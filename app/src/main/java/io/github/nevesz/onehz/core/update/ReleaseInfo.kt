package io.github.nevesz.onehz.core.update

/** A GitHub release that can be offered to the user. */
data class ReleaseInfo(
    val tag: String,
    val url: String,
    val name: String? = null,
    /** GitHub's own pre-release flag. Tags with "-" are also treated as pre-releases. */
    val prereleaseFlag: Boolean = false
) {
    val isPrerelease: Boolean
        get() = prereleaseFlag || tag.substringBefore('+').contains('-')

    /** Version without the conventional leading "v", for display only. */
    val displayVersion: String
        get() = tag.trim().removePrefix("v").removePrefix("V")
}
