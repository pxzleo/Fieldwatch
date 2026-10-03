package app.fieldwatch.domain

/**
 * Relative-loudness hunt for one BLE advertiser. RSSI is not distance
 * and not a bearing. Quiet / gone is as important as closer / further.
 */
enum class HuntCue {
    VERY_CLOSE,
    CLOSER,
    FURTHER,
    SAME,
    WAITING,
    QUIET,
    GONE,
    UNSTABLE,
}

object Hunt {
    /** Measurements stay tied to received packets; a UI clock tick is not a new sample. */
    fun recentSignal(samples: List<RssiSample>, now: Long): List<RssiSample> {
        val last = samples.lastOrNull { it.at <= now && Rssi.measured(it.rssi) } ?: return emptyList()
        if (now - last.at > QUIET_MS) return emptyList()
        return samples.filter { it.at in (last.at - RECENT_MS)..last.at && Rssi.measured(it.rssi) }.takeLast(5)
    }
    fun accepts(targetKey: String?, observation: Observation): Boolean = targetKey == null ||
        (observation.kind == RadioKind.BLE && targetKey == "BLE:${MacUtil.normalize(observation.mac)}")
    const val RECENT_MS = 2_000L
    const val EARLIER_FROM_MS = 8_000L
    const val EARLIER_TO_MS = 3_500L
    const val QUIET_MS = RECENT_MS
    /** Geiger tick: last-heard RSSI mapped to interval. Loud end is faster than Very Close. */
    const val TICK_LOUD_DBM = -40
    const val TICK_QUIET_DBM = -90
    const val TICK_FAST_MS = 90L
    const val TICK_SLOW_MS = 1_400L

    fun cue(
        samples: List<RssiSample>,
        now: Long,
        lastSeen: Long?,
        missing: Boolean,
    ): HuntCue {
        if (missing) return HuntCue.GONE
        if (lastSeen == null) return HuntCue.WAITING
        if (now - lastSeen > QUIET_MS) return HuntCue.QUIET
        val usable = samples.filter { it.at <= now && Rssi.measured(it.rssi) }
        val anchor = usable.lastOrNull()?.at ?: return HuntCue.WAITING
        val window = recentWindowMs(usable)
        val gap = (window / 4).coerceIn(500L, 1_500L)
        val recent = usable.filter { it.at > anchor - window }
        val earlier = usable.filter { it.at in (anchor - window - maxOf(6_000L, window + gap))..(anchor - window - gap) }
        if (!enough(recent) || !enough(earlier)) return HuntCue.WAITING
        val noise = maxOf(spread(recent), spread(earlier))
        if (noise > 5.0) return HuntCue.UNSTABLE
        val delta = median(recent)!! - median(earlier)!!
        val threshold = maxOf(4.0, noise * 2)
        // Require both halves of the current window to support the same trend.
        val halves = recent.partition { it.at <= anchor - window / 2 }
        val changes = listOf(halves.first, halves.second).mapNotNull { median(it)?.minus(median(earlier)!!) }
        return when {
            changes.size < 2 -> HuntCue.WAITING
            delta >= threshold && changes.all { it >= threshold } -> HuntCue.CLOSER
            delta <= -threshold && changes.all { it <= -threshold } -> HuntCue.FURTHER
            else -> HuntCue.SAME
        }
    }

    /** Sparse advertisers need a longer comparison window, not invented intermediate packets. */
    fun recentWindowMs(samples: List<RssiSample>): Long {
        val gaps = samples.takeLast(12).zipWithNext { a, b -> b.at - a.at }.filter { it > 0 }.sorted()
        return if (gaps.isEmpty()) RECENT_MS else (gaps[gaps.size / 2] * 2 + 500).coerceIn(1_200L, 6_000L)
    }

    fun median(samples: List<RssiSample>): Double? {
        val values = samples.filter { Rssi.measured(it.rssi) }.map { it.rssi.toDouble() }.sorted()
        if (values.isEmpty()) return null
        return (values[(values.size - 1) / 2] + values[values.size / 2]) / 2
    }

    /** Robust median absolute deviation; one anomalous packet cannot set the scale. */
    fun spread(samples: List<RssiSample>): Double {
        val center = median(samples) ?: return 0.0
        val values = samples.filter { Rssi.measured(it.rssi) }.map { kotlin.math.abs(it.rssi - center) }.sorted()
        return if (values.isEmpty()) 0.0 else (values[(values.size - 1) / 2] + values[values.size / 2]) / 2
    }

    fun enough(samples: List<RssiSample>): Boolean = samples.size >= 2 &&
        samples.maxOf { it.at } - samples.minOf { it.at } >= 700L

    fun label(cue: HuntCue): String = when (cue) {
        HuntCue.VERY_CLOSE -> "Strong signal"
        HuntCue.CLOSER -> "Closer"
        HuntCue.FURTHER -> "Further"
        HuntCue.SAME -> "About the same"
        HuntCue.WAITING -> "Listening…"
        HuntCue.QUIET -> "Quiet"
        HuntCue.GONE -> "Gone"
        HuntCue.UNSTABLE -> "Signal unstable"
    }

    fun hint(cue: HuntCue): String = when (cue) {
        HuntCue.VERY_CLOSE -> "Strong received signal; distance is unconfirmed."
        HuntCue.CLOSER -> "Louder than a few seconds ago. Keep walking that way."
        HuntCue.FURTHER -> "Quieter than a few seconds ago. Turn or back up."
        HuntCue.SAME -> "No clear change yet. Slow down; hold the phone still."
        HuntCue.WAITING -> "Need a few seconds of packets to compare."
        HuntCue.QUIET -> "No packet for a few seconds. Silent, or behind a wall."
        HuntCue.GONE -> "Left the live set. Randomized BLE often vanishes mid-hunt."
        HuntCue.UNSTABLE -> "Hold the phone the same way and sample again."
    }

    /**
     * Interval between Hunt ticks, or null to stay silent.
     * Quiet / Gone (and no live RSSI) do not tick. Waiting still ticks if a packet is on the screen.
     */
    fun tickIntervalMs(rssi: Int?, cue: HuntCue): Long? {
        if (cue == HuntCue.QUIET || cue == HuntCue.GONE || cue == HuntCue.UNSTABLE) return null
        val r = rssi ?: return null
        if (!Rssi.measured(r)) return null
        val span = (TICK_LOUD_DBM - TICK_QUIET_DBM).toDouble()
        val t = ((r - TICK_QUIET_DBM) / span).coerceIn(0.0, 1.0)
        return (TICK_SLOW_MS + (TICK_FAST_MS - TICK_SLOW_MS) * t).toLong()
    }
}
