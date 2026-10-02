package app.fieldwatch.domain

import kotlin.math.*

data class HuntFix(val lat: Double, val lon: Double, val accuracy: Double, val at: Long) {
    fun usable() = lat.isFinite() && lon.isFinite() && lat in -85.0..85.0 && lon in -180.0..180.0 &&
        accuracy.isFinite() && accuracy in 0.0..25.0
}
data class HuntLocatedSignal(val fix: HuntFix, val rssi: Int, val heading: HuntHeading?)
data class HuntGeoPoint(val readings: List<HuntLocatedSignal>) {
    private val samples = readings.map { RssiSample(it.fix.at, it.rssi) }
    val fix = readings.first().fix.copy(accuracy = readings.maxOf {
        maxOf(it.fix.accuracy, Geo.meters(readings.first().fix.lat, readings.first().fix.lon, it.fix.lat, it.fix.lon)) })
    val signal = Hunt.median(samples)!!
    val noise = Hunt.spread(samples)
    val heading get() = readings.first().heading
    val at get() = readings.last().fix.at
}
enum class HuntPositionStatus { MORE_POINTS, WIDER_BASELINE, SIDEWAYS, WEAK_CONTRAST, INCONSISTENT, EDGE, ESTIMATED }
data class HuntCandidate(val lat: Double, val lon: Double)
data class HuntPosition(val status: HuntPositionStatus, val center: HuntCandidate? = null,
    val candidates: List<HuntCandidate> = emptyList(), val radius: Double = 0.0, val residual: Double = 0.0,
    val cellLatStep: Double = 0.0, val cellLonStep: Double = 0.0)

/** Passive, stationary-source hypothesis. Unknown reference power is fitted, never guessed from Tx dBm. */
object HuntLocator {
    fun recent(points: List<HuntGeoPoint>, now: Long): List<HuntGeoPoint> = points.mapNotNull { point ->
        val kept = point.readings.filter { now - it.fix.at in 0..300_000L }
        if (kept.isEmpty()) null else if (kept.size == point.readings.size) point else HuntGeoPoint(kept)
    }
    fun append(points: List<HuntGeoPoint>, reading: HuntLocatedSignal): List<HuntGeoPoint> {
        if (!reading.fix.usable() || !Rssi.measured(reading.rssi)) return points
        // Late BLE callbacks still belong in the signal graph, but must not rewind the location window.
        if (points.lastOrNull()?.let { reading.fix.at <= it.at } == true) return points
        val recent = recent(points, reading.fix.at)
        val last = recent.lastOrNull()
        val together = last != null && Geo.meters(last.fix.lat, last.fix.lon, reading.fix.lat, reading.fix.lon) <
            maxOf(8.0, last.fix.accuracy, reading.fix.accuracy)
        return if (together) recent.dropLast(1) + HuntGeoPoint(listOf(last!!.readings.first()) +
            (last.readings.drop(1) + reading).takeLast(119))
            else (recent + HuntGeoPoint(listOf(reading))).takeLast(60)
    }

    fun estimate(input: List<HuntGeoPoint>, checkpoint: () -> Unit = {}): HuntPosition {
        val points = input.filter { it.readings.size >= 2 }
        if (points.size < 4) return HuntPosition(HuntPositionStatus.MORE_POINTS)
        val lat = points.map { it.fix.lat }.average()
        val lon = points.map { it.fix.lon }.average()
        val lonScale = 111_320.0 * cos(Math.toRadians(lat))
        val xs = points.map { (it.fix.lon - lon) * lonScale }
        val ys = points.map { (it.fix.lat - lat) * 110_540.0 }
        val span = hypot(xs.max() - xs.min(), ys.max() - ys.min())
        val gps = points.map { it.fix.accuracy }.average()
        if (span < maxOf(20.0, gps * 2)) return HuntPosition(HuntPositionStatus.WIDER_BASELINE)
        val xx = xs.sumOf { it * it }; val yy = ys.sumOf { it * it }
        val xy = xs.indices.sumOf { xs[it] * ys[it] }
        if ((xx * yy - xy * xy) / (xx + yy).pow(2) < 0.025)
            return HuntPosition(HuntPositionStatus.SIDEWAYS)
        if (points.maxOf { it.signal } - points.minOf { it.signal } < 5)
            return HuntPosition(HuntPositionStatus.WEAK_CONTRAST)
        val padding = maxOf(30.0, span)
        val left = xs.min() - padding; val bottom = ys.min() - padding
        val width = xs.max() - xs.min() + 2 * padding
        val height = ys.max() - ys.min() + 2 * padding
        data class Cell(val x: Double, val y: Double, val score: Double, val edge: Boolean)
        val cells = ArrayList<Cell>(1681)
        for (gx in 0..40) {
            checkpoint()
            for (gy in 0..40) {
                val x = left + gx * width / 40; val y = bottom + gy * height / 40
                val distances = xs.indices.map { hypot(x - xs[it], y - ys[it]).coerceAtLeast(2.0) }
                // Noise, uncertain receiver position, and dense packet bursts must not dominate the fit.
                val weights = points.indices.map { i -> 1.0 / (4.0 + points[i].noise.pow(2) +
                    (10 * points[i].fix.accuracy / distances[i]).pow(2)) }
                var best = Double.POSITIVE_INFINITY
                for (step in 0..12) {
                    val n = 1.5 + step * 0.25
                    val powers = points.indices.map { points[it].signal + 10 * n * log10(distances[it]) }
                    val reference = powers.indices.sumOf { powers[it] * weights[it] } / weights.sum()
                    val cost = powers.indices.sumOf {
                        val error = abs(powers[it] - reference)
                        weights[it] * if (error <= 6) error * error else 12 * error - 36
                    } / weights.sum()
                    best = minOf(best, cost)
                }
                cells += Cell(x, y, best, gx == 0 || gy == 0 || gx == 40 || gy == 40)
            }
        }
        val best = cells.minBy { it.score }
        if (best.score > 64) return HuntPosition(HuntPositionStatus.INCONSISTENT, residual = sqrt(best.score))
        fun coord(x: Double, y: Double) = HuntCandidate(lat + y / 110_540.0, lon + x / lonScale)
        val plausible = cells.filter { it.score <= best.score + 9 }
        val radius = plausible.maxOf { hypot(it.x - best.x, it.y - best.y) } + gps + hypot(width, height) / 40
        return HuntPosition(if (best.edge || plausible.any { it.edge }) HuntPositionStatus.EDGE else HuntPositionStatus.ESTIMATED,
            coord(best.x, best.y), plausible.map { coord(it.x, it.y) }, radius, sqrt(best.score),
            height / 40 / 110_540, width / 40 / lonScale)
    }
}
