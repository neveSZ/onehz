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
import android.os.IBinder
import android.os.PowerManager
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
import io.github.nevesz.onehz.ui.profile.labelRes
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Optional foreground service. It does not hold a wake lock: the device still enters Doze.
 * Screen transitions are handled by the SCREEN_OFF/ON/USER_PRESENT broadcasts; refresh-rate
 * changes must never be treated as screen transitions.
 */
class OneHzKeepAliveService : Service() {

    private var receiver: ScreenStateReceiver? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val eligible = DisplayGate.evaluate(this).eligible
        createChannel()
        startInForeground()
        if (!eligible) {
            stopSelf()
            return
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
                Diagnostics.activeLayer
            ) { profileId, hz, idle, layer -> listOf(profileId, hz, idle, layer) }
                .distinctUntilChanged()
                .collect { refreshNotification() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
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
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
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
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            type
        )
    }

    private fun refreshNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): android.app.Notification {
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
        val pausedUntil = OneHzPreferences.masterPausedUntil(this)
        val paused = OneHzPreferences.isPaused(this)
        val toggle = PendingIntent.getService(
            this,
            if (paused) 3 else 2,
            Intent(this, OneHzKeepAliveService::class.java)
                .setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val activeProfile = ProfileStore.profile(this, Diagnostics.activeProfileId.value)
        val profileName = activeProfile?.let {
            it.name ?: getString(it.kind.labelRes())
        }
        val hz = Diagnostics.appliedHz.value
        val text = when {
            paused -> getString(
                R.string.notification_paused,
                DateFormat.getTimeFormat(this).format(Date(pausedUntil))
            )
            hz > 0 && profileName != null -> getString(R.string.notification_active, profileName, hz)
            else -> getString(R.string.notification_idle)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_onehz)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                getString(if (paused) R.string.home_resume_action else R.string.home_pause_action),
                toggle
            )
            .addAction(0, getString(R.string.notification_stop), stop)
            .build()
    }

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

    companion object {
        private const val CHANNEL_ID = "onehz_service"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "io.github.nevesz.onehz.STOP"
        const val ACTION_PAUSE = "io.github.nevesz.onehz.PAUSE"
        const val ACTION_RESUME = "io.github.nevesz.onehz.RESUME"

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
