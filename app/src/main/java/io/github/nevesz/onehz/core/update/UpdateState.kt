package io.github.nevesz.onehz.core.update

/** Result of the last automatic or manual check, used by the Settings status line. */
enum class UpdateStatus {
    IDLE,
    UP_TO_DATE,
    AVAILABLE,
    NO_RELEASES,
    NETWORK_ERROR,
    RATE_LIMIT,
    HTTP_ERROR,
    INVALID_RESPONSE,
    INVALID_VERSION
}

/** Observable state shared by the Settings screen and the Home banner. */
data class UpdateState(
    val enabled: Boolean = false,
    val interval: UpdateInterval = UpdateInterval.DEFAULT,
    val channel: UpdateChannel = UpdateChannel.DEFAULT,
    val checking: Boolean = false,
    val available: ReleaseInfo? = null,
    val lastAttemptAt: Long = 0L,
    val status: UpdateStatus = UpdateStatus.IDLE,
    val httpCode: Int? = null,
    val dismissedVersion: String? = null
) {
    /** True when an available release should be shown, considering the dismissal. */
    val showBanner: Boolean
        get() = enabled && available != null && available.tag != dismissedVersion
}
