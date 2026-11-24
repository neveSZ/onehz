package io.github.nevesz.onehz.engine

import android.os.Handler
import android.os.HandlerThread
import io.github.nevesz.onehz.core.VideoConfig
import io.github.nevesz.onehz.core.VideoSignals
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs privileged video detection outside the engine thread.
 *
 * A probe spawns a few shell processes and can take a couple hundred milliseconds. Keeping it
 * on the engine thread delayed touches and staircase timers, so the request is handed to this
 * thread and the verdict is posted back. A single slot coalesces bursts: the latest request
 * wins and the worker runs again after the current probe.
 */
object VideoDetector {

    data class Request(
        val config: VideoConfig,
        val reason: String,
        val token: Int,
        val expectedPackage: String?,
        val onResult: (VideoSignals.Verdict, Request) -> Unit
    )

    private val thread = HandlerThread("OneHzVideo").apply { start() }
    private val handler = Handler(thread.looper)
    private val busy = AtomicBoolean(false)

    @Volatile
    private var pending: Request? = null

    fun request(request: Request) {
        pending = request
        if (busy.compareAndSet(false, true)) handler.post(runner)
    }

    private val runner: Runnable = Runnable {
        while (true) {
            val request = pending ?: break
            pending = null
            val verdict = runCatching { ShizukuGateway.videoPlayback(request.config) }
                .getOrElse {
                    VideoSignals.Verdict(VideoSignals.Source.UNKNOWN, null, "probe-error")
                }
            runCatching { request.onResult(verdict, request) }
        }
        busy.set(false)
        // A request that arrived during the last iteration still needs a worker.
        if (pending != null && busy.compareAndSet(false, true)) handler.post(runner)
    }
}
