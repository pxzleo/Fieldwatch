package app.fieldwatch.data

import app.fieldwatch.domain.Hunt
import app.fieldwatch.domain.HuntHeading
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.Observation
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Rssi
import app.fieldwatch.domain.RssiSample
import app.fieldwatch.domain.HuntFix
import app.fieldwatch.domain.HuntGeoPoint
import app.fieldwatch.domain.HuntLocatedSignal
import app.fieldwatch.domain.HuntLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HuntPoint(val signal: Double?, val noise: Double, val count: Int, val sufficient: Boolean,
    val heading: HuntHeading? = null)
data class HuntSessionState(
    val key: String? = null, val startedAt: Long = 0, val samples: List<RssiSample> = emptyList(),
    val pointA: HuntPoint? = null, val pointB: HuntPoint? = null, val captureStartedAt: Long = 0,
    val captureDurationMs: Long = 4_000,
    val heading: HuntHeading? = null, val captureHeading: HuntHeading? = null,
    val fixes: List<HuntFix> = emptyList(), val geoPoints: List<HuntGeoPoint> = emptyList(),
    val locationState: HuntGpsState = HuntGpsState.WAITING,
    val ranging: app.fieldwatch.domain.HuntRangeState = app.fieldwatch.domain.HuntRangeState(),
) {
    val difference: Double? get() {
        val a = pointA ?: return null
        val b = pointB ?: return null
        if (!a.sufficient || !b.sufficient) return null
        return b.signal!! - a.signal!!
    }
    val comparisonThreshold: Double get() = maxOf(4.0, (pointA?.noise ?: 0.0) + (pointB?.noise ?: 0.0))
}
enum class HuntGpsState { WAITING, DENIED, DISABLED, POOR, READY, ERROR }

/** A single selected advertiser, fed before list batching. No identity inference across MACs. */
class HuntSession {
    private val mutable = MutableStateFlow(HuntSessionState())
    val state = mutable.asStateFlow()
    @Synchronized fun start(key: String, now: Long) { mutable.value = HuntSessionState(key, now) }
    @Synchronized fun stop() { mutable.value = HuntSessionState() }
    @Synchronized fun updateRanging(key: String, startedAt: Long, state: app.fieldwatch.domain.HuntRangeState): Boolean {
        val current = mutable.value
        if (current.key != key || current.startedAt != startedAt) return false
        mutable.value = current.copy(ranging = state)
        return true
    }
    @Synchronized fun updateLocation(fix: HuntFix?, status: HuntGpsState) {
        val current = mutable.value
        if (current.key == null) return
        mutable.value = current.copy(locationState = status,
            fixes = if (fix == null || !fix.usable()) current.fixes else (current.fixes + fix).distinct().takeLast(60))
    }
    @Synchronized fun updateHeading(heading: HuntHeading?) {
        val current = mutable.value
        if (current.key != null) mutable.value = current.copy(heading = heading)
    }
    fun observe(observation: Observation) {
        if (observation.kind == RadioKind.BLE) observeSignal(observation.mac, observation.rssi, observation.at)
    }
    @Synchronized fun observeSignal(mac: String, rssi: Int, observedAt: Long) {
        val current = mutable.value
        if (current.key == null || current.key != "BLE:${MacUtil.normalize(mac)}" ||
            observedAt < current.startedAt || !Rssi.measured(rssi)) return
        val sample = RssiSample(observedAt, rssi)
        if (sample in current.samples) return
        // Sorting also handles delayed/batched callbacks; ignore exact duplicate observations.
        val samples = (current.samples + sample).distinct().sortedBy { it.at }
            .filter { it.at >= maxOf(observedAt, current.samples.lastOrNull()?.at ?: 0) - 45_000 }.takeLast(600)
        val fix = current.fixes.minByOrNull { kotlin.math.abs(it.at - observedAt) }
            ?.takeIf { kotlin.math.abs(it.at - observedAt) <= 3_000 }
        val geoPoints = if (fix == null) current.geoPoints else HuntLocator.append(current.geoPoints,
            HuntLocatedSignal(fix.copy(at = observedAt), rssi, current.heading?.takeIf { it.fresh(observedAt) }))
        mutable.value = current.copy(samples = samples, geoPoints = geoPoints)
    }
    @Synchronized fun beginPoint(now: Long): Boolean {
        val current = mutable.value
        if (current.key == null || current.captureStartedAt != 0L) return false
        mutable.value = current.copy(captureStartedAt = now, pointB = null,
            captureHeading = current.heading?.takeIf { it.fresh(now) },
            captureDurationMs = (Hunt.recentWindowMs(current.samples) * 2).coerceIn(4_000L, 12_000L))
        return true
    }
    @Synchronized fun finishPoint(now: Long) {
        val current = mutable.value
        if (current.captureStartedAt == 0L) return
        val samples = current.samples.filter { it.at in current.captureStartedAt..now }
        val point = HuntPoint(Hunt.median(samples), Hunt.spread(samples), samples.size,
            Hunt.enough(samples) && samples.maxOf { it.at } - samples.minOf { it.at } >= 1_500 &&
                now - samples.maxOf { it.at } <= Hunt.RECENT_MS && Hunt.spread(samples) <= 5,
            heading = current.captureHeading)
        mutable.value = if (current.pointA == null || !current.pointA.sufficient)
            current.copy(pointA = point, captureStartedAt = 0, captureHeading = null) else
            current.copy(pointB = point, captureStartedAt = 0, captureHeading = null)
    }
    @Synchronized fun keepB() {
        val current = mutable.value
        if (current.captureStartedAt == 0L && current.pointB?.sufficient == true)
            mutable.value = current.copy(pointA = current.pointB, pointB = null)
    }
}
