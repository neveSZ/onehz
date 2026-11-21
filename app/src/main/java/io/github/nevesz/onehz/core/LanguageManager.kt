package io.github.nevesz.onehz.core

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** In-app language selector backed by AppCompat's per-app locales. */
object LanguageManager {
    const val SYSTEM = "system"
    const val EN = "en"
    const val PT = "pt-BR"
    const val ES = "es"

    val options = listOf(SYSTEM, PT, EN, ES)

    fun current(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return SYSTEM
        val tag = locales[0]?.toLanguageTag() ?: return SYSTEM
        return when {
            tag.startsWith("pt") -> PT
            tag.startsWith("es") -> ES
            tag.startsWith("en") -> EN
            else -> SYSTEM
        }
    }

    fun set(tag: String) {
        if (tag == SYSTEM) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }
}
