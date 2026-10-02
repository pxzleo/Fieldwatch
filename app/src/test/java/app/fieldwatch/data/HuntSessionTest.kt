package app.fieldwatch.data

import app.fieldwatch.domain.*
import org.junit.Assert.*
import org.junit.Test

class HuntSessionTest {
    private val key = "BLE:00:11:22:33:44:55"
    private fun observation(at: Long, rssi: Int = -70) = Observation(RadioKind.BLE,
        "00:11:22:33:44:55", "Sample", rssi, 0, 2402, false, emptyList(), null, "", "", "", at)
    private fun samples(start: Long, values: List<Int>) = values.mapIndexed { i, rssi -> RssiSample(start + i * 800L, rssi) }

    @Test fun targetFeedIgnoresOtherDevicesOldAndUnavailableSamplesWithoutSeedingOldRssi() {
        val session = HuntSession()
        session.start(key, 10_000)
        assertTrue(session.state.value.samples.isEmpty())
        session.observe(observation(9_999))
        session.observeSignal("00:11:22:33:44:55", -70, 9_999)
        session.observe(observation(10_000, 127))
        session.observe(observation(10_100).copy(mac = "AA:BB:CC:DD:EE:FF"))
        session.observe(observation(10_100).copy(kind = RadioKind.WIFI))
        assertTrue(session.state.value.samples.isEmpty())
        session.observe(observation(10_500))
        session.observe(observation(10_200))
        assertEquals(listOf(10_200L, 10_500L), session.state.value.samples.map { it.at })
        session.stop()
        session.observe(observation(11_000))
        assertTrue(session.state.value.samples.isEmpty())
    }

    @Test fun isolatedSpikeAndSmallChangeDoNotInventApproachAndNoiseSuppressesTrend() {
        val prior = samples(92_000, listOf(-70,-71,-69,-70))
        val steady = samples(98_000, listOf(-70,-70,-30))
        assertEquals(HuntCue.SAME, Hunt.cue(prior + steady, 100_000, 99_600, false))
        assertEquals(-70.0, Hunt.median(steady)!!, 0.01)
        val noisy = samples(98_000, listOf(-80,-40,-60))
        assertEquals(HuntCue.UNSTABLE, Hunt.cue(prior + noisy, 100_000, 99_600, false))
        val small = samples(98_000, listOf(-68,-67,-68))
        assertEquals(HuntCue.SAME, Hunt.cue(prior + small, 100_000, 99_600, false))
        assertNull(Hunt.tickIntervalMs(-50, HuntCue.UNSTABLE))
        assertNull(Hunt.tickIntervalMs(127, HuntCue.SAME))
    }

    @Test fun trendNeedsTemporalCoverageBothHalvesAndMeasuredValues() {
        val prior = samples(92_000, listOf(-70,-71,-69,-70))
        assertEquals(HuntCue.CLOSER, Hunt.cue(prior + samples(98_000, listOf(-60,-59,-60)), 100_000, 99_600, false))
        assertEquals(HuntCue.FURTHER, Hunt.cue(prior + samples(98_000, listOf(-80,-79,-80)), 100_000, 99_600, false))
        val burst = (0..6).map { RssiSample(99_990L + it, -40) }
        assertEquals(HuntCue.WAITING, Hunt.cue(prior + burst, 100_000, 99_996, false))
        assertEquals(HuntCue.QUIET, Hunt.cue(prior, 110_000, 99_600, false))
    }

    @Test fun indoorComparisonWorksWithNoGpsAndKeepsStrongerBaseline() {
        val session = HuntSession()
        session.start(key, 10_000)
        assertTrue(session.beginPoint(10_000))
        assertFalse(session.beginPoint(10_500))
        (0..4).forEach { session.observe(observation(10_000 + it * 800L, -70)) }
        session.finishPoint(14_000)
        assertTrue(session.state.value.pointA!!.sufficient)
        assertTrue(session.beginPoint(15_000))
        (0..4).forEach { session.observe(observation(15_000 + it * 800L, -60)) }
        session.finishPoint(19_000)
        assertEquals(10.0, session.state.value.difference!!, 0.01)
        session.keepB()
        assertEquals(-60.0, session.state.value.pointA!!.signal!!, 0.01)
        assertNull(session.state.value.pointB)
        session.start(key, 20_000)
        assertNull(session.state.value.pointA)
    }

    @Test fun missingAndBurstSamplesCannotBecomeValidPositionAndStopCancelsCapture() {
        val session = HuntSession()
        session.start(key, 10_000)
        session.beginPoint(10_000)
        session.finishPoint(14_000)
        assertFalse(session.state.value.pointA!!.sufficient)
        assertNull(session.state.value.difference)
        session.beginPoint(15_000)
        (0..9).forEach { session.observe(observation(15_000L + it, -30)) }
        session.finishPoint(19_000)
        assertFalse(session.state.value.pointA!!.sufficient)
        session.beginPoint(20_000)
        session.stop()
        session.finishPoint(24_000)
        assertNull(session.state.value.pointA)
        assertEquals(0L, session.state.value.captureStartedAt)
    }

    @Test fun earlyPacketsFollowedBySilenceAreNotACompletedPositionSample() {
        val session = HuntSession()
        session.start(key, 10_000)
        session.beginPoint(10_000)
        (0..3).forEach { session.observe(observation(10_000 + it * 500L)) }
        session.finishPoint(14_000)
        assertFalse(session.state.value.pointA!!.sufficient)
    }

    @Test fun sparseAdvertiserCanShowTrendButStopsGuidanceBetweenPackets() {
        val samples = (0..5).map { i -> RssiSample(84_900L + i * 3_000L, if (i < 4) -70 else -60) }
        assertEquals(6_000L, Hunt.recentWindowMs(samples))
        assertEquals(HuntCue.CLOSER, Hunt.cue(samples, 100_000, 99_900, false))
        assertEquals(HuntCue.QUIET, Hunt.cue(samples, 102_001, 99_900, false))
        assertNull(Hunt.tickIntervalMs(-60, HuntCue.QUIET))
        val session = HuntSession()
        session.start(key, 80_000)
        samples.forEach { session.observe(observation(it.at, it.rssi)) }
        session.beginPoint(100_000)
        assertEquals(12_000L, session.state.value.captureDurationMs)
    }

    @Test fun singleTargetModeRejectsOtherRadioProcessingAndNormalModeRestoresIt() {
        val target = observation(10_000)
        val other = target.copy(mac = "AA:BB:CC:DD:EE:FF")
        val wifi = target.copy(kind = RadioKind.WIFI)
        assertTrue(Hunt.accepts(key, target))
        assertFalse(Hunt.accepts(key, other))
        assertFalse(Hunt.accepts(key, wifi))
        assertTrue(Hunt.accepts(null, other))
        assertTrue(Hunt.accepts(null, wifi))
    }

    @Test fun eachPointRecordsStartHeadingAndKeepsItWhenLivePhoneTurnsOrBaselineChanges() {
        val session = HuntSession()
        session.start(key, 10_000)
        val startA = HuntHeading(359, 10_000, true)
        session.updateHeading(startA)
        session.beginPoint(10_000)
        session.updateHeading(HuntHeading(90, 11_000, true))
        (0..4).forEach { session.observe(observation(10_000 + it * 800L, -70)) }
        session.finishPoint(14_000)
        assertEquals(startA, session.state.value.pointA!!.heading)
        assertNull(session.state.value.captureHeading)
        val startB = HuntHeading(2, 15_000, false)
        session.updateHeading(startB)
        session.beginPoint(15_000)
        (0..4).forEach { session.observe(observation(15_000 + it * 800L, -60)) }
        session.finishPoint(19_000)
        assertEquals(startB, session.state.value.pointB!!.heading)
        session.keepB()
        assertEquals(startB, session.state.value.pointA!!.heading)
        session.start(key, 20_000)
        assertNull(session.state.value.heading)
        assertNull(session.state.value.pointA)
    }

    @Test fun unavailableOrOldHeadingDoesNotBlockSignalSamplingOrBecomeRecordedDirection() {
        val session = HuntSession()
        session.updateHeading(HuntHeading(90, 1_000, true))
        assertNull(session.state.value.heading)
        session.start(key, 10_000)
        session.updateHeading(HuntHeading(90, 7_000, true))
        session.beginPoint(10_000)
        (0..4).forEach { session.observe(observation(10_000 + it * 800L)) }
        session.finishPoint(14_000)
        assertTrue(session.state.value.pointA!!.sufficient)
        assertNull(session.state.value.pointA!!.heading)
        session.updateHeading(null)
        assertNull(session.state.value.heading)
    }
}
