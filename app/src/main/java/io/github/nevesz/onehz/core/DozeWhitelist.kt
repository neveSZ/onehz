package io.github.nevesz.onehz.core

/** Package names listed by `dumpsys deviceidle whitelist`. */
object DozeWhitelist {

    private val ENTRY = Regex(
        """(?:^|\s)(?:system|user|temp),([A-Za-z][A-Za-z0-9_.]*)""",
        RegexOption.MULTILINE,
    )

    fun parse(dump: String?): Set<String> =
        dump?.let { text -> ENTRY.findAll(text).mapTo(mutableSetOf()) { it.groupValues[1] } }
            .orEmpty()
}
