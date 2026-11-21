package io.github.nevesz.onehz.core.update

/**
 * Small SemVer 2.0 implementation used to compare release tags without extra dependencies.
 *
 * It is intentionally a little lenient with tags: a leading "v" is accepted, numeric
 * core parts may have leading zeros, and extra numeric parts (1.0.0.1) are compared as
 * further components. Build metadata is ignored. Anything that cannot be parsed is
 * rejected so the checker never offers a release based on a guess.
 */
data class SemanticVersion(
    val core: List<Int>,
    val prerelease: List<String>
) : Comparable<SemanticVersion> {

    val isPrerelease: Boolean get() = prerelease.isNotEmpty()

    override fun compareTo(other: SemanticVersion): Int {
        val coreSize = maxOf(core.size, other.core.size)
        for (index in 0 until coreSize) {
            val left = core.getOrElse(index) { 0 }
            val right = other.core.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }

        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
        if (prerelease.isEmpty()) return 1
        if (other.prerelease.isEmpty()) return -1

        val prereleaseSize = maxOf(prerelease.size, other.prerelease.size)
        for (index in 0 until prereleaseSize) {
            val left = prerelease.getOrNull(index) ?: return -1
            val right = other.prerelease.getOrNull(index) ?: return 1
            val leftNumeric = left.all(Char::isDigit)
            val rightNumeric = right.all(Char::isDigit)
            val result = when {
                leftNumeric && rightNumeric -> compareNumeric(left, right)
                leftNumeric -> -1
                rightNumeric -> 1
                else -> left.compareTo(right)
            }
            if (result != 0) return result
        }
        return 0
    }

    /** Compares numeric pre-release identifiers without risking Int/Long overflow. */
    private fun compareNumeric(left: String, right: String): Int {
        val a = left.trimStart('0').ifEmpty { "0" }
        val b = right.trimStart('0').ifEmpty { "0" }
        if (a.length != b.length) return a.length.compareTo(b.length)
        return a.compareTo(b)
    }

    companion object {
        private val PATTERN = Regex(
            """^[vV]?(\d+(?:\.\d+)*)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$"""
        )

        fun parse(raw: String): SemanticVersion? {
            val match = PATTERN.matchEntire(raw.trim()) ?: return null
            val core = match.groupValues[1].split('.').map {
                it.toIntOrNull() ?: return null
            }
            val prerelease = match.groupValues[2]
                .takeIf { it.isNotEmpty() }
                ?.split('.')
                ?.also { parts -> if (parts.any { it.isEmpty() }) return null }
                ?: emptyList()
            return SemanticVersion(core = core, prerelease = prerelease)
        }
    }
}
