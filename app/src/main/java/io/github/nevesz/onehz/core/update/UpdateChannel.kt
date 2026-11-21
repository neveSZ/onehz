package io.github.nevesz.onehz.core.update

/** Which GitHub releases the update checker is allowed to offer. */
enum class UpdateChannel(val stored: String) {
    /** Stable releases only. This is the default and ignores betas/RCs. */
    STABLE("stable"),

    /** Stable releases plus pre-releases (betas and release candidates). */
    STABLE_AND_PRERELEASE("stable_prerelease");

    companion object {
        val DEFAULT = STABLE

        fun fromStored(value: String?): UpdateChannel =
            entries.firstOrNull { it.stored == value } ?: DEFAULT
    }
}
