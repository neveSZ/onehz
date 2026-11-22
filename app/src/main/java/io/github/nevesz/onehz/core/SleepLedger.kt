package io.github.nevesz.onehz.core

/**
 * Local, bounded history of screen-off sessions used by the Sleep card.
 *
 * Only screen-off and device-idle spans are stored. Nothing is uploaded and every value is
 * derived from them, so a clock change or a gap never produces a negative duration.
 */
object SleepLedger {

    const val WINDOW_MS = 24 * 60 * 60 * 1000L
    const val MIN_REST_MS = 30 * 60 * 1000L
    const val MERGE_GAP_MS = 2 * 60 * 1000L
    const val MIN_RATE_MS = 15 * 60 * 1000L
    const val MAX_RECORDS = 20

    fun start(now: Long, level: Int, deep: Boolean): SleepRecord =
        SleepRecord(
            startAt = now,
            endAt = 0L,
            levelStart = level,
            levelEnd = -1,
            deep = deep,
            idle = emptyList(),
            maintenance = 0,
        )

    fun close(record: SleepRecord?, now: Long, level: Int): SleepRecord? =
        record?.let {
            closeIdle(it, now).copy(
                endAt = now.coerceAtLeast(it.startAt),
                levelEnd = if (level >= 0) level else -1,
            )
        }

    fun enterIdle(record: SleepRecord?, now: Long): SleepRecord? =
        record?.let {
            if (it.idle.any { span -> span.endAt == 0L }) return it
            it.copy(
                idle = it.idle + IdleSpan(now, 0L),
                // The first idle entry of the session is the forced one; later ones are
                // maintenance windows.
                maintenance = it.maintenance + if (it.idle.isEmpty()) 0 else 1,
            )
        }

    fun leaveIdle(record: SleepRecord?, now: Long): SleepRecord? =
        record?.let { closeIdle(it, now) }

    fun usage(records: List<SleepRecord>, now: Long, windowMs: Long = WINDOW_MS): SleepUsage {
        val since = now - windowMs
        val ordered = records.sortedBy { it.startAt }
        val screenOff = ordered.sumOf { overlap(it.startAt, it.endOr(now), since, now) }
        val idle = ordered.sumOf { record ->
            record.idle.sumOf { overlap(it.startAt, it.endOr(now), since, now) }
        }
        return SleepUsage(
            screenOffMs = screenOff,
            idleMs = idle,
            sessions = ordered.size,
            // The ledger cannot describe the part of the window before its first record.
            partial = ordered.isEmpty() || ordered.first().startAt > since,
            hasData = ordered.isNotEmpty(),
        )
    }

    /**
     * Rest periods merge screen-on gaps shorter than [mergeGapMs], so glancing at the clock
     * does not split a night in two. Only sessions with at least [minDurationMs] are
     * returned, oldest first.
     */
    fun restSessions(
        records: List<SleepRecord>,
        now: Long,
        minDurationMs: Long = MIN_REST_MS,
        mergeGapMs: Long = MERGE_GAP_MS,
    ): List<RestSession> {
        val ordered = records
            .filter { it.endOr(now) >= it.startAt }
            .sortedBy { it.startAt }
        val merged = mutableListOf<RestSession>()
        for (record in ordered) {
            val start = record.startAt
            val end = record.endOr(now)
            val last = merged.lastOrNull()
            if (last != null && start - last.endAt <= mergeGapMs) {
                merged[merged.lastIndex] = merge(last, record, end)
            } else {
                merged += RestSession(
                    startAt = start,
                    endAt = end,
                    levelStart = record.levelStart,
                    levelEnd = record.levelEnd,
                    deep = record.deep,
                    idleMs = idleWithin(record, start, end),
                    maintenance = record.maintenance,
                    open = record.open,
                )
            }
        }
        return merged.filter { it.durationMs >= minDurationMs }
    }

    fun lastRest(
        records: List<SleepRecord>,
        now: Long,
        minDurationMs: Long = MIN_REST_MS,
    ): RestSession? = restSessions(records, now, minDurationMs).lastOrNull()

    fun encode(records: List<SleepRecord>): String = records
        .sortedBy { it.startAt }
        .takeLast(MAX_RECORDS)
        .joinToString(";") { record ->
            val spans = record.idle.joinToString(",") { "${it.startAt}-${it.endAt}" }
            listOf(
                record.startAt,
                record.endAt,
                record.levelStart,
                record.levelEnd,
                if (record.deep) 1 else 0,
                record.maintenance,
                spans,
            ).joinToString("|")
        }

    fun decode(raw: String?): List<SleepRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(';')
            .mapNotNull(::decodeRecord)
            .sortedBy { it.startAt }
            .takeLast(MAX_RECORDS)
    }

    private fun decodeRecord(line: String): SleepRecord? {
        val parts = line.split('|')
        if (parts.size < 7) return null
        val start = parts[0].toLongOrNull() ?: return null
        val end = parts[1].toLongOrNull() ?: return null
        if (start <= 0L) return null
        if (end != 0L && end < start) return null
        val levelStart = parts[2].toIntOrNull()?.coerceIn(0, 100) ?: return null
        val levelEnd = parts[3].toIntOrNull()?.coerceIn(-1, 100) ?: -1
        val maintenance = parts[5].toIntOrNull()?.coerceAtLeast(0) ?: 0
        val spans = parts[6].split(',')
            .mapNotNull(::decodeSpan)
            .sortedBy { it.startAt }
        return SleepRecord(
            startAt = start,
            endAt = end,
            levelStart = levelStart,
            levelEnd = levelEnd,
            deep = parts[4] == "1",
            idle = spans,
            maintenance = maintenance,
        )
    }

    private fun decodeSpan(text: String): IdleSpan? {
        val parts = text.split('-')
        if (parts.size != 2) return null
        val start = parts[0].toLongOrNull() ?: return null
        val end = parts[1].toLongOrNull() ?: return null
        if (start <= 0L) return null
        if (end != 0L && end < start) return null
        return IdleSpan(start, end)
    }

    private fun merge(last: RestSession, record: SleepRecord, end: Long): RestSession {
        val sessionEnd = maxOf(last.endAt, end)
        return last.copy(
            endAt = sessionEnd,
            levelEnd = if (record.levelEnd >= 0) record.levelEnd else last.levelEnd,
            // A session that used light idle is reported as light, even if a later record
            // was forced deep.
            deep = last.deep && record.deep,
            idleMs = last.idleMs + idleWithin(record, record.startAt, end),
            maintenance = last.maintenance + record.maintenance,
            open = record.open,
        )
    }

    private fun idleWithin(record: SleepRecord, start: Long, end: Long): Long =
        record.idle.sumOf { overlap(it.startAt, it.endOr(end), start, end) }

    private fun overlap(start: Long, end: Long, since: Long, now: Long): Long {
        val from = maxOf(start, since)
        val to = minOf(end, now)
        return (to - from).coerceAtLeast(0L)
    }

    private fun closeIdle(record: SleepRecord, now: Long): SleepRecord = record.copy(
        idle = record.idle.map { span ->
            if (span.endAt == 0L) span.copy(endAt = now.coerceAtLeast(span.startAt)) else span
        },
    )
}

data class IdleSpan(val startAt: Long, val endAt: Long) {
    fun endOr(now: Long): Long = if (endAt == 0L) now else endAt
}

data class SleepRecord(
    val startAt: Long,
    val endAt: Long,
    val levelStart: Int,
    val levelEnd: Int,
    val deep: Boolean,
    val idle: List<IdleSpan>,
    val maintenance: Int,
) {
    val open: Boolean get() = endAt == 0L

    fun endOr(now: Long): Long = if (open) now else endAt
}

data class SleepUsage(
    val screenOffMs: Long,
    val idleMs: Long,
    val sessions: Int,
    val partial: Boolean,
    val hasData: Boolean,
)

data class RestSession(
    val startAt: Long,
    val endAt: Long,
    val levelStart: Int,
    val levelEnd: Int,
    val deep: Boolean,
    val idleMs: Long,
    val maintenance: Int,
    val open: Boolean,
) {
    val durationMs: Long get() = (endAt - startAt).coerceAtLeast(0L)

    /**
     * Percent per hour for the rest period. Null when the sample is too short, when the
     * level is unknown, or when it increased (which means the device was charging).
     */
    val perHour: Double?
        get() {
            if (durationMs < SleepLedger.MIN_RATE_MS) return null
            if (levelStart <= 0 || levelEnd < 0 || levelEnd > levelStart) return null
            return (levelStart - levelEnd) * 3_600_000.0 / durationMs
        }
}
