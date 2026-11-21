package io.github.nevesz.onehz.core.update

import android.content.Context
import io.github.nevesz.onehz.BuildConfig
import io.github.nevesz.onehz.core.OneHzPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Owns the opt-in update check lifecycle.
 *
 * There is intentionally no WorkManager, service, alarm, or periodic task here. The only
 * automatic trigger is [maybeCheckOnOpen], called when the activity starts. The interval
 * is checked against the persisted last attempt, so reopening the app does not hammer
 * GitHub.
 */
object UpdateCenter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(UpdateState())

    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Loads stored configuration and cache. Never performs a network request. */
    fun init(context: Context) {
        refreshConfig(context.applicationContext, resetStatus = true)
    }

    fun maybeCheckOnOpen(context: Context) {
        val app = context.applicationContext
        if (!OneHzPreferences.updatesEnabled(app)) return

        val lastAttempt = OneHzPreferences.updateLastAttempt(app)
        val interval = OneHzPreferences.updateInterval(app)
        if (!UpdateSchedule.isDue(System.currentTimeMillis(), lastAttempt, interval)) {
            refreshConfig(app, resetStatus = false)
            return
        }
        check(app)
    }

    fun checkNow(context: Context) {
        val app = context.applicationContext
        if (!OneHzPreferences.updatesEnabled(app)) return
        check(app)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        OneHzPreferences.setUpdatesEnabled(app, enabled)
        OneHzPreferences.setUpdateLastStatus(app, UpdateStatus.IDLE)
        refreshConfig(app, resetStatus = true)
        if (enabled) {
            check(app)
        } else {
            _state.update { it.copy(checking = false, available = null, status = UpdateStatus.IDLE) }
        }
    }

    fun setInterval(context: Context, interval: UpdateInterval) {
        val app = context.applicationContext
        OneHzPreferences.setUpdateInterval(app, interval)
        refreshConfig(app, resetStatus = false)
    }

    fun setChannel(context: Context, channel: UpdateChannel) {
        val app = context.applicationContext
        if (OneHzPreferences.updateChannel(app) == channel) return
        OneHzPreferences.setUpdateChannel(app, channel)
        OneHzPreferences.setUpdateLastStatus(app, UpdateStatus.IDLE)
        refreshConfig(app, resetStatus = true)
        if (OneHzPreferences.updatesEnabled(app)) check(app)
    }

    fun dismiss(context: Context, tag: String) {
        val app = context.applicationContext
        OneHzPreferences.setUpdateDismissedVersion(app, tag)
        _state.update { it.copy(dismissedVersion = tag) }
    }

    private fun check(context: Context) {
        scope.launch {
            if (!mutex.tryLock()) return@launch

            var rerun = false
            try {
                if (!OneHzPreferences.updatesEnabled(context)) return@launch

                val startedChannel = OneHzPreferences.updateChannel(context)
                val now = System.currentTimeMillis()
                OneHzPreferences.setUpdateLastAttempt(context, now)
                _state.update {
                    it.copy(checking = true, lastAttemptAt = now, httpCode = null)
                }

                val result = UpdateChecker.check(
                    localVersionName = BuildConfig.VERSION_NAME,
                    channel = startedChannel,
                    etag = OneHzPreferences.updateEtag(context)
                )

                // The user may have changed the channel while the request was running.
                // Never store a stable-only answer under the pre-release channel.
                if (OneHzPreferences.updateChannel(context) != startedChannel) {
                    rerun = OneHzPreferences.updatesEnabled(context)
                    return@launch
                }
                if (!OneHzPreferences.updatesEnabled(context)) {
                    OneHzPreferences.setUpdateLastStatus(context, UpdateStatus.IDLE)
                    _state.update {
                        it.copy(checking = false, available = null, status = UpdateStatus.IDLE)
                    }
                    return@launch
                }

                val cached = OneHzPreferences.updateCachedRelease(context)
                val cachedNewer = cached != null && isNewer(cached, startedChannel)

                when (result) {
                    UpdateCheckResult.NotModified -> {
                        val status = if (cachedNewer) UpdateStatus.AVAILABLE else UpdateStatus.UP_TO_DATE
                        OneHzPreferences.setUpdateLastStatus(context, status)
                        _state.update {
                            it.copy(
                                checking = false,
                                available = cached.takeIf { cachedNewer },
                                status = status,
                                httpCode = null
                            )
                        }
                    }

                    UpdateCheckResult.NoReleases -> {
                        OneHzPreferences.clearUpdateCache(context)
                        OneHzPreferences.setUpdateLastStatus(context, UpdateStatus.NO_RELEASES)
                        _state.update {
                            it.copy(
                                checking = false,
                                available = null,
                                status = UpdateStatus.NO_RELEASES,
                                httpCode = null
                            )
                        }
                    }

                    UpdateCheckResult.UpToDate -> {
                        OneHzPreferences.clearUpdateCache(context)
                        OneHzPreferences.setUpdateLastStatus(context, UpdateStatus.UP_TO_DATE)
                        _state.update {
                            it.copy(
                                checking = false,
                                available = null,
                                status = UpdateStatus.UP_TO_DATE,
                                httpCode = null
                            )
                        }
                    }

                    is UpdateCheckResult.NewRelease -> {
                        OneHzPreferences.setUpdateEtag(context, result.etag)
                        OneHzPreferences.setUpdateCachedRelease(context, result.release)
                        OneHzPreferences.setUpdateLastStatus(context, UpdateStatus.AVAILABLE)
                        _state.update {
                            it.copy(
                                checking = false,
                                available = result.release,
                                status = UpdateStatus.AVAILABLE,
                                httpCode = null
                            )
                        }
                    }

                    is UpdateCheckResult.Failed -> {
                        val status = result.reason.toStatus()
                        OneHzPreferences.setUpdateLastStatus(context, status)
                        _state.update {
                            it.copy(
                                checking = false,
                                available = cached.takeIf { cachedNewer },
                                status = status,
                                httpCode = result.httpCode
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                OneHzPreferences.setUpdateLastStatus(context, UpdateStatus.NETWORK_ERROR)
                _state.update {
                    it.copy(checking = false, status = UpdateStatus.NETWORK_ERROR, httpCode = null)
                }
            } finally {
                _state.update { it.copy(checking = false) }
                mutex.unlock()
                if (rerun) check(context)
            }
        }
    }

    private fun refreshConfig(context: Context, resetStatus: Boolean) {
        val enabled = OneHzPreferences.updatesEnabled(context)
        val channel = OneHzPreferences.updateChannel(context)
        val cached = OneHzPreferences.updateCachedRelease(context)
        val available = cached?.takeIf { enabled && isNewer(it, channel) }
        val restored = OneHzPreferences.updateLastStatus(context)
        val resetValue = when {
            available != null -> UpdateStatus.AVAILABLE
            restored == UpdateStatus.AVAILABLE -> UpdateStatus.IDLE
            else -> restored
        }
        _state.update {
            it.copy(
                enabled = enabled,
                interval = OneHzPreferences.updateInterval(context),
                channel = channel,
                available = available,
                lastAttemptAt = OneHzPreferences.updateLastAttempt(context),
                dismissedVersion = OneHzPreferences.updateDismissedVersion(context),
                status = if (resetStatus) resetValue else it.status
            )
        }
    }

    private fun isNewer(release: ReleaseInfo, channel: UpdateChannel): Boolean {
        if (channel == UpdateChannel.STABLE && release.isPrerelease) return false
        val local = SemanticVersion.parse(BuildConfig.VERSION_NAME) ?: return false
        val remote = SemanticVersion.parse(release.tag) ?: return false
        return remote > local
    }

    private fun UpdateFailure.toStatus(): UpdateStatus = when (this) {
        UpdateFailure.NETWORK -> UpdateStatus.NETWORK_ERROR
        UpdateFailure.RATE_LIMIT -> UpdateStatus.RATE_LIMIT
        UpdateFailure.HTTP -> UpdateStatus.HTTP_ERROR
        UpdateFailure.INVALID_RESPONSE -> UpdateStatus.INVALID_RESPONSE
        UpdateFailure.INVALID_VERSION -> UpdateStatus.INVALID_VERSION
    }
}
