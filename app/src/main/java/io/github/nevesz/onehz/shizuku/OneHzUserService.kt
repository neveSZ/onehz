package io.github.nevesz.onehz.shizuku

import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import io.github.nevesz.onehz.core.DozeState
import io.github.nevesz.onehz.core.VideoConfig
import io.github.nevesz.onehz.core.VideoSignals
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.exitProcess

/**
 * Runs inside the Shizuku UserService (UID 2000 shell or UID 0 root).
 *
 * Touch monitoring is intentionally per-device: this firmware's `getevent` accepts a single
 * `[device]` argument, so every accepted touchscreen gets its own process/thread. Down/up are
 * derived from `ABS_MT_TRACKING_ID` (MT-B), with `BTN_TOUCH` only as a fallback for drivers
 * that do not expose tracking ids. MOVE events are not forwarded: the engine only needs the
 * edges, and every callback costs a Binder wakeup.
 */
class OneHzUserService : IOneHzService.Stub() {

    private val commandCount = AtomicInteger(0)

    @Volatile
    private var clientBinder: IBinder? = null

    private val monitors = ConcurrentHashMap<String, Monitor>()
    private val restarts = ConcurrentHashMap<String, Int>()

    @Volatile
    private var requestedPaths: Set<String> = emptySet()

    @Volatile
    private var touchCallback: ITouchCallback? = null

    @Volatile
    private var stoppingAll = false

    private val clientDeath = IBinder.DeathRecipient {
        // The app process is gone: release everything and end this process. Keeping it alive
        // leaves orphaned UserServices behind on every app update/restart.
        stopForegroundMonitoring()
        stopTouchMonitoring()
        exitProcess(0)
    }

    override fun listInputDevices(): String =
        exec("/system/bin/getevent", "-lp", maxBytes = 65_536).output

    /**
     * Only the lines that map an input device to a display are returned. `dumpsys input` is
     * large; filtering in the shell keeps the Binder payload small.
     */
    override fun dumpInputState(): String = exec(
        "/system/bin/sh",
        "-c",
        "dumpsys input | grep -E '^  Device [0-9]+:|Viewport INTERNAL'",
        maxBytes = 32_768,
        timeoutMs = 5_000
    ).output

    private val foregroundCache = ForegroundCache(FOREGROUND_CACHE_MS)
    private val foregroundThread = HandlerThread("OneHzForeground").apply { start() }
    private val foregroundHandler = Handler(foregroundThread.looper)

    @Volatile
    private var foregroundCallback: IForegroundCallback? = null

    @Volatile
    private var taskStackListener: Any? = null

    @Volatile
    private var lastForegroundNotified: String? = null

    private val foregroundDeath = IBinder.DeathRecipient {
        stopForegroundMonitoring()
    }

    override fun foregroundPackage(): String = queryForeground(force = false)

    override fun foregroundPackageFresh(): String = queryForeground(force = true)

    private fun queryForeground(force: Boolean): String {
        val now = System.currentTimeMillis()
        if (!force) foregroundCache.get(now)?.let { return it }
        val pkg = parseForeground(
            exec(
                "/system/bin/sh",
                "-c",
                "dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity|mCurrentFocus|mFocusedApp'",
                maxBytes = 32_768,
                timeoutMs = 2_500
            ).output
        ) ?: ""
        foregroundCache.put(now, pkg)
        return pkg
    }

    /**
     * Registers an [android.app.ITaskStackListener] through the hidden task-manager service.
     * The shell UID has MANAGE_ACTIVITY_TASKS, but hidden-API access and the callback ABI can
     * vary by platform; returning false lets the app fall back to the touch probe.
     */
    override fun startForegroundMonitoring(callback: IForegroundCallback?): Boolean {
        if (callback == null) return false
        stopForegroundMonitoring()
        foregroundCallback = callback
        runCatching { callback.asBinder().linkToDeath(foregroundDeath, 0) }
        val registered = runCatching { registerTaskStackListener() }
            .onFailure { Log.w(TAG, "foreground listener unavailable: ${it.message}") }
            .getOrDefault(false)
        if (!registered) {
            foregroundCallback = null
            return false
        }
        scheduleForegroundQuery(immediate = true)
        return true
    }

    override fun stopForegroundMonitoring() {
        foregroundCallback?.let { callback ->
            runCatching { callback.asBinder().unlinkToDeath(foregroundDeath, 0) }
        }
        foregroundCallback = null
        lastForegroundNotified = null
        foregroundHandler.removeCallbacks(foregroundQuery)
        unregisterTaskStackListener()
    }

    private fun registerTaskStackListener(): Boolean {
        if (taskStackListener != null) return true
        val listenerInterface = Class.forName("android.app.ITaskStackListener")
        val callbackBinder = object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                // ITaskStackListener.onTaskStackChanged() is the first oneway method.
                if (code == TASK_STACK_CHANGED_TRANSACTION) {
                    scheduleForegroundQuery(immediate = false)
                    return true
                }
                return super.onTransact(code, data, reply, flags)
            }
        }
        val listener = Proxy.newProxyInstance(
            listenerInterface.classLoader,
            arrayOf(listenerInterface)
        ) { _, method, args ->
            when (method.name) {
                "asBinder" -> callbackBinder
                "hashCode" -> System.identityHashCode(callbackBinder)
                "equals" -> callbackBinder === args?.firstOrNull()
                "toString" -> "OneHzTaskStackListener"
                else -> null
            }
        }
        val taskManager = Class.forName("android.app.ActivityTaskManager")
            .getMethod("getService")
            .invoke(null) ?: return false
        Class.forName("android.app.IActivityTaskManager")
            .getMethod("registerTaskStackListener", listenerInterface)
            .invoke(taskManager, listener)
        taskStackListener = listener
        return true
    }

    private fun unregisterTaskStackListener() {
        val listener = taskStackListener ?: return
        taskStackListener = null
        runCatching {
            val listenerInterface = Class.forName("android.app.ITaskStackListener")
            val taskManager = Class.forName("android.app.ActivityTaskManager")
                .getMethod("getService")
                .invoke(null) ?: return@runCatching
            Class.forName("android.app.IActivityTaskManager")
                .getMethod("unregisterTaskStackListener", listenerInterface)
                .invoke(taskManager, listener)
        }
    }

    private val foregroundQuery = Runnable {
        val callback = foregroundCallback ?: return@Runnable
        val pkg = queryForeground(force = true)
        if (pkg.isBlank() || pkg == lastForegroundNotified) return@Runnable
        lastForegroundNotified = pkg
        runCatching { callback.onForegroundPackage(pkg) }
    }

    private fun scheduleForegroundQuery(immediate: Boolean) {
        if (foregroundCallback == null) return
        foregroundHandler.removeCallbacks(foregroundQuery)
        foregroundHandler.postDelayed(
            foregroundQuery,
            if (immediate) 0L else FOREGROUND_DEBOUNCE_MS
        )
    }

    @Volatile
    private var cachedVideoAt = 0L

    @Volatile
    private var cachedVideoPackage: String? = null

    @Volatile
    private var cachedVideoResult: String? = null

    @Volatile
    private var cachedVideoConfig: String? = null

    /**
     * Video detection for the app currently in the foreground, without an app whitelist.
     *
     * The user config selects which signals are consulted. The default rules are:
     * 1. a MediaSession that exists but is paused is authoritative: no video;
     * 2. the foreground app must hold a video codec (Codec2 ResourceManager);
     * 3. with a playing session the codec is enough; without a session, a started
     *    USAGE_MEDIA player confirms that the video is not paused.
     *
     * Packages in the forced list skip every probe and are always reported as playing.
     */
    override fun videoPlaybackWithConfig(config: String?): String {
        val cfg = VideoConfig.decode(config)
        val pkg = foregroundPackage()
        if (pkg.isBlank()) {
            return VideoSignals.Verdict(VideoSignals.Source.NONE, null, "no-foreground").encode()
        }
        if (pkg in cfg.forcedPackages) {
            return cacheVideo(
                pkg,
                cfg,
                VideoSignals.Verdict(VideoSignals.Source.PLAYING, pkg, "forced")
            )
        }
        if (!cfg.hasPositiveSignal) {
            return cacheVideo(
                pkg,
                cfg,
                VideoSignals.Verdict(VideoSignals.Source.NONE, pkg, "no-signal")
            )
        }

        val now = System.currentTimeMillis()
        cachedVideoResult?.let { cached ->
            if (cachedVideoPackage == pkg && cachedVideoConfig == cfg.encode() && now - cachedVideoAt < 800L) {
                return cached
            }
        }

        val sessions = if (cfg.useMediaSession) {
            exec(
                "/system/bin/dumpsys",
                "media_session",
                maxBytes = 131_072,
                timeoutMs = 2_500
            ).output
        } else {
            ""
        }
        val session = if (cfg.useMediaSession) VideoSignals.sessionState(sessions, pkg) else null

        // A session that exists but is not playing is authoritative when the rule is on.
        if (cfg.useMediaSession && cfg.pausedSessionBlocks &&
            session != null && !VideoSignals.isActive(session)
        ) {
            return cacheVideo(
                pkg,
                cfg,
                VideoSignals.Verdict(VideoSignals.Source.NONE, pkg, "session:$session")
            )
        }

        val pids = if (cfg.requireVideoCodec || cfg.requireStartedAudio) {
            VideoSignals.processPids(
                exec(
                    "/system/bin/ps",
                    "-A",
                    "-o",
                    "PID,NAME",
                    maxBytes = 131_072,
                    timeoutMs = 2_500
                ).output,
                pkg
            )
        } else {
            emptySet()
        }

        val codecs = if (cfg.requireVideoCodec) {
            VideoSignals.videoCodecPids(
                exec(
                    "/system/bin/sh",
                    "-c",
                    "dumpsys media.resource_manager | sed -n '/Processes:/,/Process Pid override:/p'",
                    maxBytes = 32_768,
                    timeoutMs = 2_500
                ).output
            )
        } else {
            emptyMap()
        }

        // Audio only confirms playback when there is no playing session to confirm it.
        val sessionPlaying = cfg.useMediaSession && session != null && VideoSignals.isActive(session)
        val audioStarted = if (cfg.requireStartedAudio && !sessionPlaying) {
            VideoSignals.mediaAudioStarted(
                exec(
                    "/system/bin/sh",
                    "-c",
                    "dumpsys audio | grep AudioPlaybackConfiguration",
                    maxBytes = 32_768,
                    timeoutMs = 2_500
                ).output,
                pids
            )
        } else {
            false
        }

        return cacheVideo(
            pkg,
            cfg,
            VideoSignals.decide(cfg, pkg, session, pids, codecs, audioStarted)
        )
    }

    /** Kept for an app process started by a previous version: defaults apply. */
    override fun videoPlayback(): String = videoPlaybackWithConfig(null)

    private fun cacheVideo(
        pkg: String,
        config: VideoConfig,
        verdict: VideoSignals.Verdict
    ): String {
        val encoded = verdict.encode()
        cachedVideoPackage = pkg
        cachedVideoResult = encoded
        cachedVideoConfig = config.encode()
        cachedVideoAt = System.currentTimeMillis()
        return encoded
    }

    override fun setDozeWhitelist(packageName: String?, enabled: Boolean): Boolean {
        if (packageName == null || !PACKAGE_NAME.matches(packageName)) return false
        val sign = if (enabled) "+" else "-"
        // `dumpsys deviceidle sys-whitelist` reports success without changing anything on
        // some builds, and dumpsys exits 0 even for unknown commands. Use the shell command
        // first and confirm the result by reading the list back.
        val commands = listOf(
            arrayOf("/system/bin/cmd", "deviceidle", "whitelist", "$sign$packageName"),
            arrayOf("/system/bin/dumpsys", "deviceidle", "whitelist", "$sign$packageName"),
        )
        for (command in commands) {
            if (!exec(*command).success) continue
            if (whitelistContains(packageName) == enabled) return true
        }
        return false
    }

    override fun isDozeWhitelisted(packageName: String?): Boolean {
        if (packageName == null || !PACKAGE_NAME.matches(packageName)) return false
        return whitelistContains(packageName)
    }

    private fun whitelistContains(packageName: String): Boolean {
        val output = exec("/system/bin/dumpsys", "deviceidle", "whitelist", maxBytes = 65_536).output
        return output.lineSequence().any { line ->
            line.trim().split(',').getOrNull(1) == packageName
        }
    }

    override fun dozeWhitelist(): String =
        exec("/system/bin/dumpsys", "deviceidle", "whitelist", maxBytes = 65_536).output

    /**
     * `force-idle` without an argument selects deep idle, which is the portable form across
     * Android 12+. `light` is only sent when the user asks for it.
     */
    override fun forceIdle(deep: Boolean): Boolean {
        val args = if (deep) {
            arrayOf("deviceidle", "force-idle")
        } else {
            arrayOf("deviceidle", "force-idle", "light")
        }
        // `cmd` starts the system command directly and is the faster path on Android 12+.
        // Keep `dumpsys` as a fallback for builds where `cmd deviceidle` is unavailable.
        if (exec("/system/bin/cmd", *args).success) return true
        return exec("/system/bin/dumpsys", *args).success
    }

    override fun unforceIdle(): Boolean {
        // Same order as forceIdle. The controller still verifies the real idle state before
        // clearing its persisted flag, so a false positive here is not trusted.
        val unforced = exec("/system/bin/cmd", "deviceidle", "unforce").success ||
            exec("/system/bin/dumpsys", "deviceidle", "unforce").success
        if (!unforced) return false
        // `unforce` only clears the forced flag. On One UI the state can stay IDLE while
        // the screen is off, so a guard change would not actually leave idle. The
        // documented motion command brings the device back to INACTIVE without waking it.
        if (!exec("/system/bin/cmd", "deviceidle", "motion").success) {
            exec("/system/bin/dumpsys", "deviceidle", "motion")
        }
        return true
    }

    override fun idleState(): String {
        // The state lines are at the end of a ~23 KB dump on One UI; a smaller cap
        // truncates them and makes the verification read an empty state.
        val dump = exec("/system/bin/dumpsys", "deviceidle", maxBytes = 65_536, timeoutMs = 3_000).output
        return DozeState.encode(dump)
    }

    private fun parseForeground(text: String): String? {
        if (text.isBlank()) return null
        val patterns = listOf(
            Regex("""(?:mResumedActivity|topResumedActivity)[^\n]*?([A-Za-z][A-Za-z0-9_]*\.[A-Za-z0-9_.]+)/"""),
            Regex("""mCurrentFocus[^\n]*?([A-Za-z][A-Za-z0-9_]*\.[A-Za-z0-9_.]+)/"""),
            Regex("""mFocusedApp[^\n]*?([A-Za-z][A-Za-z0-9_]*\.[A-Za-z0-9_.]+)/""")
        )
        for (pattern in patterns) {
            val pkg = pattern.find(text)?.groupValues?.get(1) ?: continue
            if (pkg == "com.android.systemui" || pkg == "android") continue
            if (PACKAGE_NAME.matches(pkg)) return pkg
        }
        return null
    }

    override fun startTouchMonitoring(devicePaths: Array<out String>?, callback: ITouchCallback?) {
        if (devicePaths == null || callback == null) return
        touchCallback = callback
        stoppingAll = false

        @Suppress("SENSELESS_COMPARISON")
        val safe = devicePaths.asSequence()
            .filter { it != null && DEVICE_PATH.matches(it) }
            .distinct()
            .take(MAX_MONITORS)
            .toList()

        requestedPaths = safe.toSet()

        safe.forEach { path ->
            restarts.remove(path)
            if (monitors.containsKey(path)) return@forEach
            val monitor = Monitor(path)
            if (monitors.putIfAbsent(path, monitor) == null) spawn(monitor)
        }

        // A device that no longer passes the filter must stop feeding the engine.
        monitors.keys.filter { it !in requestedPaths }.forEach { stopMonitor(it, notify = true) }
    }

    override fun stopTouchMonitoring() {
        requestedPaths = emptySet()
        stoppingAll = true
        monitors.keys.toList().forEach { stopMonitor(it, notify = true) }
        touchCallback = null
    }

    private fun spawn(monitor: Monitor) {
        monitor.stopping = false
        monitor.thread = Thread({
            var process: Process? = null
            try {
                process = ProcessBuilder(
                    "/system/bin/sh",
                    "-c",
                    "exec /system/bin/getevent -lt ${monitor.path}"
                ).redirectErrorStream(true).start()
                monitor.process = process
                BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                    while (!monitor.stopping && requestedPaths.contains(monitor.path)) {
                        val line = reader.readLine() ?: break
                        handleInputLine(line.trimEnd(), monitor)
                    }
                }
            } catch (_: Throwable) {
                // Best effort: restarted below while the device is still requested.
            } finally {
                process?.destroySafely()
                monitor.process = null
                if (monitor.down) {
                    monitor.down = false
                    monitor.parser.reset()
                    monitor.pointers = 0
                    emit(monitor, down = false)
                }
                monitors.remove(monitor.path, monitor)
                if (!monitor.stopping && !stoppingAll && requestedPaths.contains(monitor.path)) {
                    runCatching { touchCallback?.onTouch(monitor.path, ACTION_STOPPED, 0) }
                    scheduleRestart(monitor.path)
                }
            }
        }, "OneHzInput-${monitor.path.substringAfterLast('/')}").apply {
            isDaemon = false
            start()
        }
    }

    private fun stopMonitor(path: String, notify: Boolean) {
        val monitor = monitors.remove(path) ?: return
        monitor.stopping = true
        val process = monitor.process
        monitor.process = null
        process?.destroySafely()

        val thread = monitor.thread
        monitor.thread = null
        if (thread != null && thread !== Thread.currentThread()) {
            runCatching { thread.join(THREAD_JOIN_MS) }
        }

        if (monitor.down) {
            monitor.down = false
            monitor.parser.reset()
            monitor.pointers = 0
            if (notify) emit(monitor, down = false)
        }
    }

    private fun scheduleRestart(path: String) {
        val attempt = restarts[path] ?: 0
        restarts[path] = attempt + 1
        val delay = when (attempt) {
            0 -> 2_000L
            1 -> 5_000L
            2 -> 10_000L
            else -> 30_000L
        }
        Thread {
            runCatching { Thread.sleep(delay) }
            if (!stoppingAll && requestedPaths.contains(path) && !monitors.containsKey(path)) {
                val monitor = Monitor(path)
                if (monitors.putIfAbsent(path, monitor) == null) spawn(monitor)
            }
        }.apply {
            isDaemon = true
            name = "OneHzInputRestart"
            start()
        }
    }

    private fun handleInputLine(line: String, monitor: Monitor) {
        try {
            val transition = monitor.parser.feed(line)
            monitor.pointers = monitor.parser.activePointers
            if (transition != 0) {
                val down = transition > 0
                monitor.down = down
                emit(monitor, down)
            }
        } catch (_: RemoteException) {
            stopTouchMonitoring()
        }
    }

    private fun emit(monitor: Monitor, down: Boolean) {
        val callback = touchCallback ?: return
        runCatching {
            callback.onTouch(
                monitor.path,
                if (down) ACTION_DOWN else ACTION_UP,
                monitors.values.sumOf { it.pointers }
            )
        }
    }

    override fun writeSecureMode(key: String?, value: Int): Boolean {
        if (key == null || key !in SECURE_KEYS || value !in 0..2) return false
        return writeMode(key, value)
    }

    override fun readSecureMode(key: String?): Int =
        if (key != null && key in SECURE_KEYS) readMode(key) else -1

    override fun readRefreshRateSetting(key: String?): String {
        if (key == null || key !in RATE_KEYS) return ""
        val raw = exec("/system/bin/settings", "get", "system", key).output.trim()
        return if (raw == "null") "" else raw
    }

    override fun writeRefreshRateSetting(key: String?, value: String?): Boolean {
        if (key == null || key !in RATE_KEYS || value == null) return false
        val safe = value.toFloatOrNull()?.takeIf { it.isFinite() && it >= 0f }?.toString() ?: return false
        return exec("/system/bin/settings", "put", "system", key, safe).success
    }

    override fun deleteRefreshRateSetting(key: String?): Boolean {
        if (key == null || key !in RATE_KEYS) return false
        return exec("/system/bin/settings", "delete", "system", key).success
    }

    /**
     * HyperOS exposes its user-facing rate limit in Settings.Secure. The values are integers;
     * the two keys below are the only ones accepted, so this cannot become a generic secure
     * settings writer.
     */
    override fun readSecureSetting(key: String?): String {
        if (key == null || key !in SECURE_RATE_KEYS) return ""
        val raw = exec("/system/bin/settings", "get", "secure", key).output.trim()
        return if (raw == "null") "" else raw
    }

    override fun writeSecureSetting(key: String?, value: String?): Boolean {
        if (key == null || key !in SECURE_RATE_KEYS || value == null) return false
        val safe = value.toIntOrNull()?.takeIf { it in 1..240 }?.toString() ?: return false
        val result = exec("/system/bin/settings", "put", "secure", key, safe)
        if (!result.success) return false
        return exec("/system/bin/settings", "get", "secure", key).output.trim() == safe
    }

    override fun deleteSecureSetting(key: String?): Boolean {
        if (key == null || key !in SECURE_RATE_KEYS) return false
        return exec("/system/bin/settings", "delete", "secure", key).success
    }

    override fun writeGlobalString(key: String?, value: String?): Boolean {
        if (key == null || value == null || key !in GLOBAL_KEYS) return false
        val result = exec("/system/bin/settings", "put", "global", key, value)
        if (!result.success) return false
        return exec("/system/bin/settings", "get", "global", key).output.trim() == value
    }

    override fun readGlobalString(key: String?): String {
        if (key == null || key !in GLOBAL_KEYS) return ""
        val raw = exec("/system/bin/settings", "get", "global", key).output.trim()
        return if (raw == "null") "" else raw
    }

    /**
     * Samsung's AOD service reads this debug key when the AOD starts and uses it as the raw
     * platform brightness (0..255), overriding its own auto brightness. The value is validated
     * here so the app can never write an arbitrary setting through this method.
     */
    override fun writeAodBrightness(value: Int): Boolean {
        if (value !in 0..255) return false
        val result = exec("/system/bin/settings", "put", "system", AOD_BRIGHTNESS_KEY, value.toString())
        if (!result.success) return false
        return exec("/system/bin/settings", "get", "system", AOD_BRIGHTNESS_KEY).output.trim() == value.toString()
    }

    override fun clearAodBrightness(): Boolean =
        exec("/system/bin/settings", "delete", "system", AOD_BRIGHTNESS_KEY).success

    override fun readAodBrightness(): Int =
        exec("/system/bin/settings", "get", "system", AOD_BRIGHTNESS_KEY).output.trim().toIntOrNull() ?: -1

    /** On-demand read-back so the hot path can trust the exit code. */
    override fun verifySettings(): String {
        val mode = readMode(MODE_KEY)
        val peak = exec("/system/bin/settings", "get", "system", "peak_refresh_rate").output.trim()
        val min = exec("/system/bin/settings", "get", "system", "min_refresh_rate").output.trim()
        return "mode=$mode · peak=$peak · min=$min"
    }

    override fun serviceStats(): String =
        "commands=${commandCount.get()} · monitors=${monitors.size} · " +
            "foreground=${if (foregroundCallback != null) "listener" else "off"}"

    override fun registerClient(client: IBinder?) {
        if (client == null || client === clientBinder) return
        unregisterClient()
        runCatching { client.linkToDeath(clientDeath, 0) }
        clientBinder = client
    }

    private fun unregisterClient() {
        clientBinder?.let { runCatching { it.unlinkToDeath(clientDeath, 0) } }
        clientBinder = null
    }

    override fun destroy() {
        stopForegroundMonitoring()
        stopTouchMonitoring()
        exitProcess(0)
    }

    /**
     * Runs a privileged command with a real deadline and a bounded read.
     * The watchdog kills the child even if the read is blocked, which the old
     * readText()+waitFor() order could not guarantee.
     */
    private fun exec(vararg command: String, maxBytes: Int = 8_192, timeoutMs: Long = 3_000): CommandResult {
        var process: Process? = null
        val timedOut = java.util.concurrent.atomic.AtomicBoolean(false)
        return try {
            commandCount.incrementAndGet()
            val started = ProcessBuilder(*command).redirectErrorStream(true).start()
            process = started
            Thread {
                runCatching {
                    Thread.sleep(timeoutMs)
                    if (started.isAlive) {
                        timedOut.set(true)
                        started.destroyForcibly()
                    }
                }
            }.apply { isDaemon = true }.start()

            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4_096)
            var total = 0
            started.inputStream.use { stream ->
                while (total < maxBytes) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    total += read
                }
            }
            if (total >= maxBytes && started.isAlive) started.destroyForcibly()
            val done = started.waitFor(2, TimeUnit.SECONDS)
            val success = done && !timedOut.get() && started.exitValue() == 0
            CommandResult(success, String(output.toByteArray(), Charsets.UTF_8))
        } catch (t: Throwable) {
            CommandResult(false, "")
        } finally {
            process?.destroySafely()
        }
    }

    private fun readMode(key: String): Int =
        exec("/system/bin/settings", "get", "secure", key).output.trim().toIntOrNull() ?: -1

    private fun writeMode(key: String, value: Int): Boolean =
        exec("/system/bin/settings", "put", "secure", key, value.toString()).success

    private fun Process.destroySafely() {
        runCatching { outputStream?.close() }
        runCatching { inputStream?.close() }
        runCatching { destroy() }
        runCatching { if (!waitFor(500, TimeUnit.MILLISECONDS)) destroyForcibly() }
    }

    private class Monitor(val path: String) {
        @Volatile var process: Process? = null
        @Volatile var thread: Thread? = null
        @Volatile var stopping = false
        @Volatile var down = false
        @Volatile var pointers = 0
        val parser = MtTouchParser()
    }

    private data class CommandResult(val success: Boolean, val output: String)

    private companion object {
        const val TAG = "OneHzForeground"
        const val MODE_KEY = "refresh_rate_mode"
        const val AOD_BRIGHTNESS_KEY = "debug_aod_brightness"

        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_STOPPED = 2
        const val MAX_MONITORS = 4
        const val THREAD_JOIN_MS = 250L

        const val FOREGROUND_CACHE_MS = 250L
        const val FOREGROUND_DEBOUNCE_MS = 250L
        const val TASK_STACK_CHANGED_TRANSACTION = 1

        val DEVICE_PATH = Regex("/dev/input/event[0-9]+")
        val PACKAGE_NAME = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
        val SECURE_KEYS = setOf("refresh_rate_mode", "refresh_rate_mode_cover")
        val RATE_KEYS = setOf("min_refresh_rate", "peak_refresh_rate")
        val SECURE_RATE_KEYS = setOf("miui_refresh_rate", "user_refresh_rate")
        val GLOBAL_KEYS = setOf(
            "pms_settings_refresh_rate_enabled",
            "pms_settings_refresh_rate_cover_enabled",
            "psm_refresh_rate_tag",
            "psm_refresh_rate_cover_tag",
            "restricted_device_performance"
        )
    }
}
