package io.github.nevesz.onehz.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.nevesz.onehz.BuildConfig
import io.github.nevesz.onehz.core.VideoSignals
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.diagnostic.InputMonitorState
import io.github.nevesz.onehz.engine.OneHzEngine
import kotlinx.coroutines.flow.MutableStateFlow
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

enum class ShizukuState { NOT_RUNNING, PERMISSION_REQUIRED, READY, SERVICE_UNAVAILABLE }

/**
 * Single entry point to Shizuku. The privileged UserService is the only place that performs
 * elevated operations; if Shizuku is lost the app fails closed.
 */
object ShizukuGateway {

    const val PERMISSION_REQUEST_CODE = 42_002
    private const val TAG = "Shizuku"
    private const val SERVICE_TAG = "onehz_privileged_service"
    private const val PROCESS_SUFFIX = "onehz_core"
    private const val API_REVISION = 11
    private const val TOUCH_ACTION_DOWN = 0
    private const val TOUCH_ACTION_UP = 1
    private const val TOUCH_ACTION_STOPPED = 2
    private const val REDISCOVERY_FIRST_MS = 5_000L
    private const val REDISCOVERY_SECOND_MS = 15_000L
    private const val REDISCOVERY_REST_MS = 30_000L
    private const val BIND_COOLDOWN_MS = 5_000L

    val state = MutableStateFlow(ShizukuState.NOT_RUNNING)

    @Volatile
    var service: IOneHzService? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "OneHzShizuku").apply { isDaemon = true } }
    private var initialized = false
    private var destroyed = false
    private var binding = false
    private var lastBindAttempt = 0L
    private var rebindAttempt = 0
    private var rebindPending = false
    private val clientBinder = android.os.Binder()

    /** Devices that passed [TouchDeviceSelector] and are being monitored. */
    @Volatile
    private var cachedDevices: List<TouchDevice> = emptyList()

    /** Paths currently reporting at least one finger down; refreshed from Binder threads. */
    private val downDevices: MutableSet<String> =
        java.util.Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile
    private var monitoringRequested = false

    private var rediscoveryPending = false

    @Volatile
    private var rediscoveryAttempt = 0

    private var onReady: (() -> Unit)? = null
    private var onUnavailable: (() -> Unit)? = null

    private val touchCallback = object : ITouchCallback.Stub() {
        override fun onTouch(devicePath: String?, action: Int, activePointers: Int) {
            val path = devicePath ?: return
            if (action == TOUCH_ACTION_STOPPED) {
                // The remote getevent died (device removed/renamed). Re-discover instead of
                // trusting the cached path forever.
                if (monitoringRequested) {
                    cachedDevices = emptyList()
                    Diagnostics.log("touch", "monitor stopped ($path); rediscovering")
                    scheduleRediscovery()
                }
                return
            }
            Diagnostics.lastTouchAt.value = timeNow()
            val (wasTouching, touching) = synchronized(downDevices) {
                val before = downDevices.isNotEmpty()
                if (action == TOUCH_ACTION_DOWN) downDevices.add(path) else downDevices.remove(path)
                before to downDevices.isNotEmpty()
            }
            if (touching != wasTouching) {
                Diagnostics.log(
                    "touch",
                    if (touching) "DOWN ($path · $activePointers pointer(s))" else "UP ($path)"
                )
                OneHzEngine.onTouchState(touching)
            }
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, OneHzUserService::class.java.name)
        )
            .tag(SERVICE_TAG)
            .daemon(false)
            .processNameSuffix(PROCESS_SUFFIX)
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE * 100 + API_REVISION)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (destroyed) {
                runCatching { Shizuku.unbindUserService(userServiceArgs, this, true) }
                return
            }
            service = IOneHzService.Stub.asInterface(binder)
            binding = false
            rebindAttempt = 0
            rebindPending = false
            publishState(ShizukuState.READY)
            executor.execute {
                // Keeps the death recipient alive even when no touch monitor is running, so
                // this UserService exits with the app instead of leaking a process.
                runCatching { service?.registerClient(clientBinder) }
                Diagnostics.log(
                    TAG,
                    "UserService connected (version ${BuildConfig.VERSION_CODE * 100 + API_REVISION})"
                )
                onReady?.invoke()
                OneHzEngine.onServiceAvailable()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) = lost("disconnected")
        override fun onBindingDied(name: ComponentName?) = lost("binding died")
        override fun onNullBinding(name: ComponentName?) = lost("null binding")
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener {
        Diagnostics.log(TAG, "binder received")
        refresh()
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        publishState(ShizukuState.NOT_RUNNING)
        Diagnostics.log(TAG, "binder died")
        onUnavailable?.invoke()
        scheduleRebind()
    }

    private val permissionResult = Shizuku.OnRequestPermissionResultListener { code, result ->
        if (code != PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener
        val granted = result == PackageManager.PERMISSION_GRANTED
        Diagnostics.log(TAG, "Shizuku permission: $granted")
        if (granted) refresh() else publishState(ShizukuState.PERMISSION_REQUIRED)
    }

    fun init(
        context: Context,
        onReady: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        if (initialized) return
        initialized = true
        this.onReady = onReady
        this.onUnavailable = onUnavailable
        context.applicationContext
        runCatching {
            Shizuku.addBinderReceivedListener(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        }
        refresh()
    }

    fun refresh() {
        executor.execute {
            val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
            if (!running) {
                service = null
                publishState(ShizukuState.NOT_RUNNING)
                onUnavailable?.invoke()
                scheduleRebind()
                return@execute
            }
            val granted = runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            if (!granted) {
                publishState(ShizukuState.PERMISSION_REQUIRED)
                return@execute
            }
            bind()
        }
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(PERMISSION_REQUEST_CODE) }
    }

    /** One binder call per interaction; no polling. */
    fun foregroundPackage(): String? =
        runCatching { service?.foregroundPackage() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun setDozeWhitelisted(packageName: String, enabled: Boolean): Boolean =
        runCatching { service?.setDozeWhitelist(packageName, enabled) == true }.getOrDefault(false)

    fun isDozeWhitelisted(packageName: String): Boolean =
        runCatching { service?.isDozeWhitelisted(packageName) == true }.getOrDefault(false)

    fun readSystemRate(key: String): String? =
        runCatching { service?.readRefreshRateSetting(key) }.getOrNull()?.takeIf { it.isNotBlank() }

    fun writeSystemRate(key: String, value: Float): Boolean =
        runCatching { service?.writeRefreshRateSetting(key, value.toString()) == true }.getOrDefault(false)

    fun deleteSystemRate(key: String): Boolean =
        runCatching { service?.deleteRefreshRateSetting(key) == true }.getOrDefault(false)

    fun writeMode(key: String, mode: Int): Boolean =
        runCatching { service?.writeSecureMode(key, mode) == true }.getOrDefault(false)

    fun readGlobalString(key: String): String =
        runCatching { service?.readGlobalString(key) }.getOrNull().orEmpty()

    fun writeGlobalString(key: String, value: String): Boolean =
        runCatching { service?.writeGlobalString(key, value) == true }.getOrDefault(false)

    fun readMode(key: String): Int =
        runCatching { service?.readSecureMode(key) }.getOrNull() ?: -1

    fun verifySettings(): String =
        runCatching { service?.verifySettings() }.getOrNull() ?: "service unavailable"

    fun serviceStats(): String =
        runCatching { service?.serviceStats() }.getOrNull() ?: "—"

    /** One binder call that resolves the foreground and checks the two video signals. */
    fun videoPlayback(): VideoSignals.Verdict =
        VideoSignals.Verdict.decode(runCatching { service?.videoPlayback() }.getOrNull())

    fun writeAodBrightness(value: Int): Boolean =
        runCatching { service?.writeAodBrightness(value) == true }.getOrDefault(false)

    fun clearAodBrightness(): Boolean =
        runCatching { service?.clearAodBrightness() == true }.getOrDefault(false)

    fun readAodBrightness(): Int =
        runCatching { service?.readAodBrightness() }.getOrNull() ?: -1

    /** Stops every raw input monitor so the device can idle during AOD. */
    fun stopTouchMonitoring() {
        monitoringRequested = false
        synchronized(downDevices) { downDevices.clear() }
        executor.execute {
            runCatching { service?.stopTouchMonitoring() }
            Diagnostics.inputState.value = InputMonitorState.STOPPED
            Diagnostics.log(TAG, "touch monitor stopped")
        }
    }

    /** Drops the cached device list and re-runs discovery + monitoring. */
    fun restartInputMonitoring() {
        monitoringRequested = true
        executor.execute {
            cachedDevices = emptyList()
            synchronized(downDevices) { downDevices.clear() }
            rediscoveryAttempt = 0
            val s = service ?: run {
                Diagnostics.inputState.value = InputMonitorState.NO_SERVICE
                return@execute
            }
            runCatching { s.stopTouchMonitoring() }
            startInputMonitoring()
        }
    }

    /** Starts monitoring only; safe to call on every resume (the service ignores duplicates). */
    fun ensureInputMonitoring() {
        monitoringRequested = true
        executor.execute { startInputMonitoring() }
    }

    private fun bind() {
        val now = System.currentTimeMillis()
        if (destroyed || binding || service != null || now - lastBindAttempt < BIND_COOLDOWN_MS) return
        lastBindAttempt = now
        binding = true
        publishState(ShizukuState.SERVICE_UNAVAILABLE)
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
            .onFailure {
                binding = false
                Diagnostics.log(TAG, "bind falhou: ${it.message}")
                scheduleRebind()
            }
    }

    private fun lost(reason: String) {
        service = null
        binding = false
        publishState(ShizukuState.SERVICE_UNAVAILABLE)
        Diagnostics.log(TAG, "UserService $reason")
        onUnavailable?.invoke()
        scheduleRebind()
    }

    private fun scheduleRebind() {
        if (destroyed || rebindPending) return
        rebindPending = true
        val delay = when (rebindAttempt) {
            0 -> 5_000L
            1 -> 15_000L
            2 -> 30_000L
            else -> 60_000L
        }
        rebindAttempt++
        mainHandler.postDelayed({
            rebindPending = false
            refresh()
        }, delay)
    }

    private fun startInputMonitoring() {
        val s = service ?: run {
            Diagnostics.inputState.value = InputMonitorState.NO_SERVICE
            return
        }

        // Reuse the accepted devices; getevent -lp only runs again after a failure or reset.
        if (cachedDevices.isNotEmpty() && startMonitoring(s, cachedDevices)) return
        cachedDevices = emptyList()

        val listing = runCatching { s.listInputDevices() }.getOrElse {
            Diagnostics.inputState.value = InputMonitorState.LIST_FAILED
            Diagnostics.log(TAG, "listInputDevices failed: ${it.message}")
            scheduleRediscovery()
            return
        }.orEmpty()

        if (listing.isBlank()) {
            Diagnostics.inputState.value = InputMonitorState.EMPTY_LIST
            Diagnostics.log(TAG, "empty device list")
            scheduleRediscovery()
            return
        }

        // Optional enrichment: displayId per device. Discovery still works without it.
        val dump = runCatching { s.dumpInputState() }.getOrDefault("")
        val displayByName = TouchDeviceSelector.parseDisplayIds(dump)

        val candidates = TouchDeviceSelector.parse(listing, displayByName)
        Diagnostics.log(TAG, "candidates: " + TouchDeviceSelector.describe(candidates))
        Diagnostics.inputCandidates.value = candidates.map { candidate ->
            "${if (candidate.accepted) "OK" else "REJ"} ${candidate.device.path} · " +
                "${candidate.device.name} · ${candidate.reason}"
        }

        val selected = candidates.filter { it.accepted }.map { it.device }
        if (selected.isEmpty()) {
            Diagnostics.inputState.value = InputMonitorState.NO_TOUCHSCREEN
            Diagnostics.log(TAG, "no touchscreen accepted; fail-closed")
            scheduleRediscovery()
            return
        }

        cachedDevices = selected
        startMonitoring(s, selected)
    }

    private fun startMonitoring(s: IOneHzService, devices: List<TouchDevice>): Boolean =
        runCatching {
            s.startTouchMonitoring(devices.map { it.path }.toTypedArray(), touchCallback)
        }
            .onSuccess {
                Diagnostics.inputDevice.value = devices.joinToString("\n") { it.label }
                Diagnostics.inputState.value = InputMonitorState.ACTIVE
                rediscoveryAttempt = 0
                Diagnostics.log(
                    TAG,
                    "monitoring " + devices.joinToString { "${it.path} (${it.name})" }
                )
            }
            .onFailure {
                Diagnostics.inputState.value = InputMonitorState.START_FAILED
                Diagnostics.log(TAG, "startTouchMonitoring failed: ${it.message}")
                scheduleRediscovery()
            }
            .isSuccess

    /**
     * Discovery failed or returned nothing: retry with backoff while monitoring is still
     * wanted. A screen-off stop clears [monitoringRequested], so retries never keep the device
     * awake during AOD.
     */
    private fun scheduleRediscovery() {
        synchronized(this) {
            if (destroyed || rediscoveryPending || !monitoringRequested) return
            rediscoveryPending = true
        }
        val delay = when (rediscoveryAttempt) {
            0 -> REDISCOVERY_FIRST_MS
            1 -> REDISCOVERY_SECOND_MS
            else -> REDISCOVERY_REST_MS
        }
        rediscoveryAttempt++
        mainHandler.postDelayed({
            synchronized(this) { rediscoveryPending = false }
            if (!destroyed && monitoringRequested) executor.execute { startInputMonitoring() }
        }, delay)
    }

    private fun timeNow(): String =
        java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
            .format(java.util.Date())

    private fun publishState(newState: ShizukuState) {
        if (state.value != newState) {
            state.value = newState
            Diagnostics.shizukuState.value = when (newState) {
                ShizukuState.NOT_RUNNING -> "Shizuku not running"
                ShizukuState.PERMISSION_REQUIRED -> "Shizuku permission required"
                ShizukuState.READY -> "Ready"
                ShizukuState.SERVICE_UNAVAILABLE -> "Privileged service unavailable"
            }
            Diagnostics.log(TAG, "state=${newState.name}")
        }
    }

    fun destroy() {
        destroyed = true
        monitoringRequested = false
        synchronized(downDevices) { downDevices.clear() }
        cachedDevices = emptyList()
        service?.let { runCatching { it.destroy() } }
        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceived)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permissionResult)
        }
        service = null
    }
}
