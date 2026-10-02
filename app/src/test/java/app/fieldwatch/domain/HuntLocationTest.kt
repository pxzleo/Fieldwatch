package app.fieldwatch.domain

import app.fieldwatch.data.HuntSession
import app.fieldwatch.data.HuntGpsState
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class HuntLocationTest {
    private fun fix(x: Double, y: Double, at: Long = 10_000, accuracy: Double = 3.0) =
        HuntFix(1 + y / 110_540, 103 + x / (111_320 * cos(Math.toRadians(1.0))), accuracy, at)
    private fun point(x: Double, y: Double, signal: Int, accuracy: Double = 3.0) = HuntGeoPoint(
        listOf(HuntLocatedSignal(fix(x, y, accuracy = accuracy), signal, null),
            HuntLocatedSignal(fix(x, y, 11_000, accuracy), signal, null)))
    private val locations = listOf(-40.0 to -30.0, 0.0 to -40.0, 40.0 to -20.0,
        40.0 to 30.0, 0.0 to 40.0, -40.0 to 20.0)
    private fun synthetic(power: Double, targetX: Double = 9.0, targetY: Double = 6.0) =
        locations.map { (x, y) -> point(x, y, (power - 22 * log10(hypot(x - targetX, y - targetY))).roundToInt()) }

    @Test fun unknownPowerOffsetsDoNotChangeEstimatedLocation() {
        val a = HuntLocator.estimate(synthetic(-42.0))
        val b = HuntLocator.estimate(synthetic(-62.0))
        assertNotNull(a.center); assertNotNull(b.center)
        val target = fix(9.0, 6.0)
        assertTrue(Geo.meters(a.center!!.lat, a.center.lon, target.lat, target.lon) < 15)
        assertEquals(a.center.lat, b.center!!.lat, 1e-9)
        assertEquals(a.center.lon, b.center.lon, 1e-9)
        assertTrue(a.radius >= 3.0)
        assertTrue(a.candidates.isNotEmpty())
    }
    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun cancelledSearchStopsBetweenGridColumns() {
        var columns = 0
        HuntLocator.estimate(synthetic(-42.0)) {
            if (++columns == 3) throw kotlinx.coroutines.CancellationException("Newer location request")
        }
    }
    @Test fun geometryAndContrastFailuresDoNotInventTargetCoordinates() {
        assertEquals(HuntPositionStatus.MORE_POINTS, HuntLocator.estimate(emptyList()).status)
        val straight = (0..5).map { point(it * 20.0, 0.0, -50 - it * 4) }
        assertEquals(HuntPositionStatus.SIDEWAYS, HuntLocator.estimate(straight).status)
        assertNull(HuntLocator.estimate(straight).center)
        val tiny = locations.map { (x,y) -> point(x / 20, y / 20, -60) }
        assertEquals(HuntPositionStatus.WIDER_BASELINE, HuntLocator.estimate(tiny).status)
        assertEquals(HuntPositionStatus.WEAK_CONTRAST,
            HuntLocator.estimate(locations.map { (x,y) -> point(x,y,-60) }).status)
    }
    @Test fun stationaryPacketsAndGpsJitterAreOnePointAndOldPointsExpire() {
        var points = emptyList<HuntGeoPoint>()
        repeat(300) { i -> points = HuntLocator.append(points,
            HuntLocatedSignal(fix((i % 3).toDouble(), 0.0, 10_000L + i * 100), -70, null)) }
        assertEquals(1, points.size)
        assertEquals(120, points.single().readings.size)
        assertEquals(10_000L, points.single().fix.at)
        points = HuntLocator.append(points, HuntLocatedSignal(fix(40.0, 20.0, 350_000), -60, null))
        assertEquals(1, points.size)
        assertEquals(350_000L, points.single().at)
        assertEquals(points, HuntLocator.append(points, HuntLocatedSignal(fix(50.0, 30.0, 351_000, 80.0), -50, null)))
    }
    @Test fun sessionRequiresTargetAndTimeMatchedFreshFixAndClearsOnReset() {
        val session = HuntSession()
        session.start("BLE:00:11:22:33:44:55", 10_000)
        session.updateLocation(fix(0.0, 0.0), HuntGpsState.READY)
        session.observeSignal("AA:BB:CC:DD:EE:FF", -60, 10_100)
        assertTrue(session.state.value.geoPoints.isEmpty())
        session.observeSignal("00:11:22:33:44:55", -60, 10_100)
        assertEquals(1, session.state.value.geoPoints.size)
        session.observeSignal("00:11:22:33:44:55", -60, 20_000)
        assertEquals(1, session.state.value.geoPoints.single().readings.size)
        session.start("BLE:00:11:22:33:44:55", 30_000)
        assertTrue(session.state.value.geoPoints.isEmpty())
        assertTrue(session.state.value.fixes.isEmpty())
    }
    @Test fun latePacketsCannotEraseNewerPointsAndLongStaysExpireEveryOldReading() {
        val old = point(0.0, 0.0, -70)
        val next = HuntGeoPoint(listOf(HuntLocatedSignal(fix(40.0, 20.0, 20_000), -60, null)))
        assertEquals(listOf(old, next), HuntLocator.append(listOf(old, next),
            HuntLocatedSignal(fix(0.0, 0.0, 15_000), -65, null)))
        var points = listOf(old)
        for (at in 20_000L..380_000L step 10_000) points = HuntLocator.append(points,
            HuntLocatedSignal(fix(0.0, 0.0, at), -70, null))
        assertTrue(points.single().readings.all { it.fix.at >= 80_000 })
        val partlyExpired = HuntGeoPoint(listOf(HuntLocatedSignal(fix(0.0, 0.0, 1_000), -40, null),
            HuntLocatedSignal(fix(0.0, 0.0, 201_000), -70, null)))
        val pruned = HuntLocator.recent(listOf(partlyExpired), 401_000)
        assertEquals(1, pruned.single().readings.size)
        assertEquals(-70.0, pruned.single().signal, 0.0)
    }
}
