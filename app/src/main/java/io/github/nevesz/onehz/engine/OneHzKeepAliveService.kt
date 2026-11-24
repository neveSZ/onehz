package io.github.nevesz.onehz.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.nevesz.onehz.R
import io.github.nevesz.onehz.core.DisplayGate
import io.github.nevesz.onehz.core.MasterPause
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.profile.ProfileStore
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.ui.MainActivity
import io.github.nevesz.onehz.ui.components.NotificationAccess
import io.github.nevesz.onehz.ui.profile.labelRes
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Optional foreground service. It does not hold a wake lock: the device still enters Doze.
 * Screen transitions are handled by the SCREEN_OFF/ON/USER_PRESENT broadcasts; refresh-rate
 * changes must never be treated as screen transitions.
 *
 * The notification is rebuilt from [NotificationContent] and coalesced to at most one post
 * per [POST_WINDOW_MS]. State transitions (pause, idle, AOD, profile) bypass the window, and
 * identical content is never posted again. When the user disables OneHZ notifications the
 * service keeps running but does not restore the pin or rebuild the notification.
 */
class OneHzKeepAliveService : Service() {

    private var receiver: ScreenStateReceiver? = null
    private var notificationDismissed = false
    private var notificationPosted = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val postHandler = Handler(Looper.getMainLooper())
    private var lastPostAt = 0L
    private var lastNotifyAt = 0L
    private var lastSignature: String? = null
    private var lastKind: NotificationKind? = null
    private var pendingPost = false

    private val postRunnable = Runnable { postNow() }

    private var cachedProfileId: String? = null
    private var cachedProfileName: String? = null

    override fun onCreate() {
        super.onCreate()
        val eligible = DisplayGate.evaluate(this).eligible
        createChannel()
        startInForeground()
        notificationPosted = NotificationAccess.isEnabled(this)
        running = true
        if (!eligible) {
            running = false
            stopSelf()
            return
        }
        if (!NotificationAccess.isEnabled(this) && OneHzPreferences.notificationPinned(this)) {
            OneHzPreferences.setNotificationPinned(this, false)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        receiver = ScreenStateReceiver().also {
            ContextCompat.registerReceiver(this, it, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }

        OneHzEngine.start(this)
        AodBrightnessController.start(this)
        DozeController.start(this)
        OneHzEngine.refreshSystemState()
        observeState()
    }

    private fun observeState() {
        scope.launch {
            combine(
                Diagnostics.activeProfileId,
                Diagnostics.appliedHz,
                Diagnostics.idleReason,
                Diagnostics.activeTierKey
            ) { profileId, hz, idle, tier ->
                NotificationTrigger(profileId, hz, idle, tier)
            }
                .distinctUntilChanged()
                .collect { requestPost(immediate = false) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_STOP && !OneHzPreferences.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                OneHzPreferences.setEnabled(this, false)
                OneHzEngine.stop()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_PAUSE -> {
                OneHzPreferences.setMasterPausedUntil(
                    this,
                    System.currentTimeMillis() + MasterPause.ONE_HOUR_MS
                )
                OneHzEngine.pauseFor(MasterPause.ONE_HOUR_MS)
                refreshNotification()
            }

            ACTION_RESUME -> {
                OneHzPreferences.setMasterPausedUntil(this, 0L)
                OneHzEngine.resumeNow()
                refreshNotification()
            }

            ACTION_DISMISS -> {
                val pinned = OneHzPreferences.notificationPinned(this)
                Diagnostics.log(TAG, "notification dismissed (pinned=$pinned)")
                if (NotificationPinPolicy.restoresAfterDismiss(pinned)) {
                    scope.launch {
                        delay(RESTORE_DELAY_MS)
                        if (OneHzPreferences.notificationPinned(this@OneHzKeepAliveService)) {
                            refreshNotification()
                        }
                    }
                } else {
                    notificationDismissed = true
                }
                return START_STICKY
            }

            ACTION_REFRESH -> {
                notificationDismissed = false
                cachedProfileId = null
                refreshNotification()
                return START_STICKY
            }

            ACTION_PIN_CHANGED -> {
                if (OneHzPreferences.notificationPinned(this)) {
                    notificationDismissed = false
                    refreshNotification()
                } else if (!notificationDismissed) {
                    refreshNotification()
                }
                return START_STICKY
            }

            ACTION_CONTENT_CHANGED -> {
                refreshNotification()
                return START_STICKY
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        postHandler.removeCallbacksAndMessages(null)
        pendingPost = false
        receiver?.let { runCatching { unregisterReceiver(it) } }
        receiver = null
        AodBrightnessController.stop()
        DozeController.stop()
        OneHzEngine.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.cancel(NOTIFICATION_ID)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        val kind = currentKind()
        val spec = buildSpec(kind, pinned = OneHzPreferences.notificationPinned(this))
        lastSignature = spec.signature
        lastKind = kind
        lastPostAt = SystemClock.elapsedRealtime()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(spec),
            type
        )
        lastNotifyAt = SystemClock.elapsedRealtime()
    }

    /** Immediate path used by actions and state changes. */
    private fun refreshNotification() {
        requestPost(immediate = true)
    }

    /**
     * One post per window for rate changes inside the same state. A state change (pause,
     * idle, AOD, profile) is always immediate, so the user never sees a stale state.
     */
    private fun requestPost(immediate: Boolean) {
        val kind = currentKind()
        if (immediate || kind != lastKind) {
            postHandler.removeCallbacks(postRunnable)
            pendingPost = false
            postNow()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (lastPostAt == 0L) {
            postNow()
            return
        }
        val wait = POST_WINDOW_MS - (now - lastPostAt)
        if (wait <= 0L) {
            postHandler.removeCallbacks(postRunnable)
            pendingPost = false
            postNow()
            return
        }
        if (!pendingPost) {
            pendingPost = true
            postHandler.postDelayed(postRunnable, wait)
        }
    }

    private fun postNow() {
        pendingPost = false
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!OneHzPreferences.isEnabled(this)) {
            manager.cancel(NOTIFICATION_ID)
            return
        }
        if (!NotificationAccess.isEnabled(this)) {
            // The foreground service keeps running, but there is nothing to show or restore.
            // A hidden notification must not be mistaken for a dismissed one when the user
            // enables notifications again.
            notificationPosted = false
            if (OneHzPreferences.notificationPinned(this)) {
                OneHzPreferences.setNotificationPinned(this, false)
            }
            return
        }
        lastPostAt = SystemClock.elapsedRealtime()
        val pinned = OneHzPreferences.notificationPinned(this)
        if (!pinned && notificationPosted &&
            SystemClock.elapsedRealtime() - lastNotifyAt > NOTIFICATION_GRACE_MS
        ) {
            val active = manager.activeNotifications.any { it.id == NOTIFICATION_ID }
            if (!active) notificationDismissed = true
        }
        if (!NotificationPinPolicy.canPost(pinned, notificationDismissed)) {
            Diagnostics.log(TAG, "notification refresh suppressed")
            return
        }
        val kind = currentKind()
        val spec = buildSpec(kind, pinned)
        lastKind = kind
        if (spec.signature == lastSignature) return
        manager.notify(NOTIFICATION_ID, buildNotification(spec))
        notificationPosted = true
        lastNotifyAt = SystemClock.elapsedRealtime()
        lastSignature = spec.signature
    }

    private fun currentKind(): NotificationKind = NotificationContent.kind(
        paused = OneHzPreferences.isPaused(this),
        idleReason = Diagnostics.idleReason.value,
        tierKey = Diagnostics.activeTierKey.value,
        appliedHz = Diagnostics.appliedHz.value
    )

    private fun buildSpec(kind: NotificationKind, pinned: Boolean): NotificationSpec {
        val paused = OneHzPreferences.isPaused(this)
        val pausedUntil = OneHzPreferences.masterPausedUntil(this)
        val showRate = OneHzPreferences.notificationDetails(this)
        val hz = Diagnostics.appliedHz.value
        val profileName = if (kind == NotificationKind.ACTIVE) {
            profileName(Diagnostics.activeProfileId.value)
        } else {
            null
        }
        val text = NotificationContent.text(
            kind = kind,
            profileName = profileName,
            hz = hz,
            showRate = showRate,
            formatActive = { label, value ->
                getString(R.string.notification_active, label, value)
            },
            formatAod = { value -> getString(R.string.notification_aod, value) },
            activeLabel = getString(R.string.notification_active_simple),
            aodLabel = getString(R.string.notification_aod_simple),
            pausedLabel = if (paused) {
                getString(
                    R.string.notification_paused,
                    DateFormat.getTimeFormat(this).format(Date(pausedUntil))
                )
            } else {
                null
            },
            idleLabel = getString(R.string.notification_idle)
        )
        return NotificationSpec(text = text, paused = paused, pinned = pinned)
    }

    /**
     * Decodes the profile only when the id changes. Renaming a custom profile clears the
     * cache through [ACTION_REFRESH], which the activity sends on resume.
     */
    private fun profileName(profileId: String?): String? {
        if (profileId == cachedProfileId) return cachedProfileName
        val profile = profileId?.let { ProfileStore.profile(this, it) }
        cachedProfileId = profileId
        cachedProfileName = profile?.let { it.name ?: getString(it.kind.labelRes()) }
        return cachedProfileName
    }

    private fun buildNotification(spec: NotificationSpec): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, OneHzKeepAliveService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggle = PendingIntent.getService(
            this,
            if (spec.paused) 3 else 2,
            Intent(this, OneHzKeepAliveService::class.java)
                .setAction(if (spec.paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_onehz)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(spec.text)
            .setContentIntent(open)
            .setOngoing(spec.pinned)
            .setDeleteIntent(dismissIntent())
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                getString(if (spec.paused) R.string.home_resume_action else R.string.home_pause_action),
                toggle
            )
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
    }

    private fun dismissIntent(): PendingIntent = PendingIntent.getService(
        this,
        4,
        Intent(this, OneHzKeepAliveService::class.java).setAction(ACTION_DISMISS),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private data class NotificationTrigger(
        val profileId: String?,
        val hz: Int,
        val idleReason: IdleReason?,
        val tierKey: String
    )

    private data class NotificationSpec(
        val text: String,
        val paused: Boolean,
        val pinned: Boolean
    ) {
        val signature: String get() = "$text|$paused|$pinned"
    }

    companion object {
        private const val TAG = "service"
        internal const val CHANNEL_ID = "onehz_service"
        private const val NOTIFICATION_ID = 1001
        private const val RESTORE_DELAY_MS = 300L
        private const val POST_WINDOW_MS = 1_000L
        private const val NOTIFICATION_GRACE_MS = 3_000L
        const val ACTION_STOP = "io.github.nevesz.onehz.STOP"
        const val ACTION_PAUSE = "io.github.nevesz.onehz.PAUSE"
        const val ACTION_RESUME = "io.github.nevesz.onehz.RESUME"
        const val ACTION_DISMISS = "io.github.nevesz.onehz.DISMISS"
        const val ACTION_REFRESH = "io.github.nevesz.onehz.REFRESH"
        const val ACTION_PIN_CHANGED = "io.github.nevesz.onehz.PIN_CHANGED"
        const val ACTION_CONTENT_CHANGED = "io.github.nevesz.onehz.CONTENT_CHANGED"

        @Volatile
        private var running = false

        /** Re-posts the notification after the user opens the app again. */
        fun refreshIfRunning(context: Context) {
            if (!running || !OneHzPreferences.isEnabled(context)) return
            if (!NotificationAccess.isEnabled(context)) return
            runCatching {
                context.startService(
                    Intent(context, OneHzKeepAliveService::class.java).setAction(ACTION_REFRESH)
                )
            }
        }

        /** Applies a new pin state without starting a stopped service. */
        fun pinChanged(context: Context) {
            if (!running || !OneHzPreferences.isEnabled(context)) return
            if (!NotificationAccess.isEnabled(context)) return
            runCatching {
                context.startService(
                    Intent(context, OneHzKeepAliveService::class.java).setAction(ACTION_PIN_CHANGED)
                )
            }
        }

        /** Rebuilds the notification after the content preference changed. */
        fun contentChanged(context: Context) {
            if (!running || !OneHzPreferences.isEnabled(context)) return
            runCatching {
                context.startService(
                    Intent(context, OneHzKeepAliveService::class.java)
                        .setAction(ACTION_CONTENT_CHANGED)
                )
            }
        }

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, OneHzKeepAliveService::class.java)
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, OneHzKeepAliveService::class.java)) }
        }
    }
}
