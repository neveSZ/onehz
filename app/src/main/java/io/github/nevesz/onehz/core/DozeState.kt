package io.github.nevesz.onehz.core

/**
 * Device-idle state as printed by `dumpsys deviceidle`.
 *
 * The shell command exits with 0 even when it does not recognise a subcommand, so the
 * printed state is the only reliable confirmation that the device actually entered idle.
 */
object DozeState {

    private val STATE = Regex("""mState=([A-Z_]+)""")
    private val LIGHT_STATE = Regex("""mLightState=([A-Z_]+)""")

    fun parse(dump: String?): String =
        dump?.let { STATE.find(it)?.groupValues?.get(1) }.orEmpty()

    fun parseLight(dump: String?): String =
        dump?.let { LIGHT_STATE.find(it)?.groupValues?.get(1) }.orEmpty()

    /** Deep and light states in one value, for a single Binder round trip. */
    fun encode(dump: String?): String = "${parse(dump)}|${parseLight(dump)}"

    /** True for deep or light idle, including the short maintenance window. */
    fun isIdle(state: String?): Boolean =
        state == "IDLE" || state == "IDLE_MAINTENANCE"

    /** True when either the deep or the light state is idle. */
    fun isIdleEncoded(encoded: String?): Boolean =
        encoded?.split('|')?.any { isIdle(it) } == true
}
