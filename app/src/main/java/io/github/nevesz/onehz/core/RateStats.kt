package io.github.nevesz.onehz.core

import android.content.Context
import androidx.core.content.edit
import io.github.nevesz.onehz.core.prefs.PreferencesStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONObject

enum class RateMode { ON, AOD, OFF }

data class RateSample(val mode: RateMode, val hz: Int, val profileId: String?)

data class RateBucket(val hz: Int, val durationMs: Long)
data class ProfileBucket(val profileId: String, val durationMs: Long)

data class RateStatsSummary(
    val rates: List<RateBucket>,
    val profiles: List<ProfileBucket>,
    val aodMs: Long,
    val offMs: Long
) {
    val controlledMs: Long get() = rates.sumOf { it.durationMs }
}

/**
 * Event-driven time accounting for the applied rate. It only consumes transitions reported
 * by the engine, so it never polls and never holds a wake lock.
 */
class RateStatsAccumulator {

    private class DayBucket {
        val on = mutableMapOf<Int, Long>()
        val aod = mutableMapOf<Int, Long>()
        val profiles = mutableMapOf<String, Long>()
        var offMs = 0L
    }

    private val days = sortedMapOf<String, DayBucket>()
    private var lastAt = 0L
    private var last: RateSample? = null

    fun note(now: Long, sample: RateSample) {
        if (last != null && lastAt > 0 && now > lastAt) {
            addSpan(lastAt, now, last!!)
        }
        lastAt = now
        last = sample
    }

    /** Includes the open span without changing the current sample. */
    fun flushTo(now: Long) {
        val sample = last ?: return
        if (lastAt > 0 && now > lastAt) {
            addSpan(lastAt, now, sample)
            lastAt = now
        }
    }

    fun summary(now: Long, daysBack: Int): RateStatsSummary {
        flushTo(now)
        val start = startOfDay(now - (daysBack - 1).coerceAtLeast(0) * DAY_MS)
        val rates = mutableMapOf<Int, Long>()
        val aod = mutableMapOf<Int, Long>()
        val profiles = mutableMapOf<String, Long>()
        var off = 0L
        days.forEach { (key, bucket) ->
            val dayStart = parseDay(key) ?: return@forEach
            if (dayStart + DAY_MS <= start) return@forEach
            bucket.on.forEach { (hz, ms) -> rates[hz] = (rates[hz] ?: 0L) + ms }
            bucket.aod.forEach { (hz, ms) -> aod[hz] = (aod[hz] ?: 0L) + ms }
            bucket.profiles.forEach { (id, ms) -> profiles[id] = (profiles[id] ?: 0L) + ms }
            off += bucket.offMs
        }
        return RateStatsSummary(
            rates = rates.entries.map { RateBucket(it.key, it.value) }
                .sortedBy { it.hz },
            profiles = profiles.map { ProfileBucket(it.key, it.value) }
                .sortedByDescending { it.durationMs },
            aodMs = aod.values.sum(),
            offMs = off
        )
    }

    fun prune(now: Long, keepDays: Int = 30) {
        val cutoff = startOfDay(now - keepDays * DAY_MS)
        days.keys.removeAll { key -> (parseDay(key) ?: 0L) < cutoff }
    }

    fun encode(): String {
        val root = JSONObject()
        val dayObject = JSONObject()
        days.forEach { (key, bucket) ->
            val objectDay = JSONObject()
            objectDay.put("on", JSONObject().apply {
                bucket.on.forEach { (hz, ms) -> put(hz.toString(), ms) }
            })
            objectDay.put("aod", JSONObject().apply {
                bucket.aod.forEach { (hz, ms) -> put(hz.toString(), ms) }
            })
            objectDay.put("profiles", JSONObject().apply {
                bucket.profiles.forEach { (id, ms) -> put(id, ms) }
            })
            objectDay.put("off", bucket.offMs)
            dayObject.put(key, objectDay)
        }
        root.put("days", dayObject)
        return root.toString()
    }

    fun decode(raw: String?) {
        days.clear()
        if (raw.isNullOrBlank()) return
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val dayObject = root.optJSONObject("days") ?: return
        dayObject.keys().forEach { key ->
            val source = dayObject.optJSONObject(key) ?: return@forEach
            val bucket = DayBucket()
            source.optJSONObject("on")?.let { objectOn ->
                objectOn.keys().forEach { hz ->
                    hz.toIntOrNull()?.let { bucket.on[it] = objectOn.optLong(hz) }
                }
            }
            source.optJSONObject("aod")?.let { objectAod ->
                objectAod.keys().forEach { hz ->
                    hz.toIntOrNull()?.let { bucket.aod[it] = objectAod.optLong(hz) }
                }
            }
            source.optJSONObject("profiles")?.let { objectProfiles ->
                objectProfiles.keys().forEach { id ->
                    bucket.profiles[id] = objectProfiles.optLong(id)
                }
            }
            bucket.offMs = source.optLong("off")
            days[key] = bucket
        }
    }

    private fun addSpan(start: Long, end: Long, sample: RateSample) {
        var cursor = start
        while (cursor < end) {
            val dayEnd = startOfDay(cursor) + DAY_MS
            val chunkEnd = minOf(end, dayEnd)
            add(cursor, chunkEnd - cursor, sample)
            cursor = chunkEnd
        }
    }

    private fun add(at: Long, durationMs: Long, sample: RateSample) {
        if (durationMs <= 0) return
        val bucket = days.getOrPut(dayKey(at)) { DayBucket() }
        when (sample.mode) {
            RateMode.ON -> bucket.on[sample.hz] = (bucket.on[sample.hz] ?: 0L) + durationMs
            RateMode.AOD -> bucket.aod[sample.hz] = (bucket.aod[sample.hz] ?: 0L) + durationMs
            RateMode.OFF -> bucket.offMs += durationMs
        }
        if (sample.mode != RateMode.OFF && sample.profileId != null) {
            bucket.profiles[sample.profileId] =
                (bucket.profiles[sample.profileId] ?: 0L) + durationMs
        }
    }

    companion object {
        private const val DAY_MS = 24L * 60L * 60L * 1_000L
        private val DAY_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun startOfDay(at: Long): Long = Calendar.getInstance().apply {
            timeInMillis = at
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        private fun dayKey(at: Long): String = DAY_FORMAT.format(Date(at))

        private fun parseDay(key: String): Long? =
            runCatching { DAY_FORMAT.parse(key)?.time }.getOrNull()
    }
}

/** Process-wide owner of the accumulator and its debounced persistence. */
object RateStats {
    private const val KEY = "rate_stats_days"
    private const val SAVE_INTERVAL_MS = 30_000L

    private var loaded = false
    private var lastSavedAt = 0L
    private var accumulator = RateStatsAccumulator()

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        accumulator.decode(
            PreferencesStore.get(context).getString(KEY, null)
        )
    }

    fun note(context: Context, hz: Int, profileId: String?, mode: RateMode) {
        load(context)
        accumulator.note(System.currentTimeMillis(), RateSample(mode, hz, profileId))
        val now = System.currentTimeMillis()
        if (now - lastSavedAt >= SAVE_INTERVAL_MS) save(context, now)
    }

    fun summary(context: Context, days: Int): RateStatsSummary {
        load(context)
        val now = System.currentTimeMillis()
        accumulator.prune(now)
        return accumulator.summary(now, days)
    }

    fun saveNow(context: Context) {
        load(context)
        save(context, System.currentTimeMillis())
    }

    private fun save(context: Context, now: Long) {
        accumulator.prune(now)
        PreferencesStore.get(context).edit {
            putString(KEY, accumulator.encode())
        }
        lastSavedAt = now
    }
}
