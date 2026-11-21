package io.github.nevesz.onehz.core.update

/** Minimum time between two update checks. The user can only pick these values. */
enum class UpdateInterval(val minutes: Int) {
    HOURS_24(24 * 60),
    DAYS_3(3 * 24 * 60),
    DAYS_7(7 * 24 * 60),
    DAYS_30(30 * 24 * 60);

    val millis: Long get() = minutes * 60_000L

    companion object {
        val DEFAULT = HOURS_24

        fun fromMinutes(value: Int): UpdateInterval =
            entries.firstOrNull { it.minutes == value } ?: DEFAULT
    }
}
