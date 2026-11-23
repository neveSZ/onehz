package io.github.nevesz.onehz.core

/** Parses the pinned/PiP task from the activity dump used by the privileged service. */
object PipSignals {
    private val TASK_PATTERN = Regex("""A=\d+:([A-Za-z0-9_.]+)[^}]*mode=pinned""")

    fun packageName(dump: String?): String? {
        if (dump.isNullOrBlank()) return null
        return TASK_PATTERN.find(dump)?.groupValues?.getOrNull(1)
    }
}
