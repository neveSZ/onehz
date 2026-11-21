package io.github.nevesz.onehz.core

/** How the app colors are chosen. AMOLED keeps surfaces at true black. */
enum class ThemeMode(val stored: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    AMOLED("amoled");

    val isDark: Boolean
        get() = this == DARK || this == AMOLED

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK, AMOLED -> true
    }

    companion object {
        fun fromStored(value: String?): ThemeMode =
            entries.firstOrNull { it.stored == value } ?: SYSTEM
    }
}
