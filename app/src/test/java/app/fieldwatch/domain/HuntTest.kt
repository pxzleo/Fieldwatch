package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuntTest {
    @Test fun clockTicksCannotChangeSignalOrTrendWithoutNewPackets() {
        val samples = (0..17).map { RssiSample(10_000L + it * 200, if (it < 6) -75 else -60) }
        val last = samples.last().at
        assertEquals(HuntCue.CLOSER, Hunt.cue(samples, last, last, false))
        assertEquals(Hunt.cue(samples, last, last, false), Hunt.cue(samples, last + 1_500, last, false))
        assertEquals(Hunt.recentSignal(samples, last), Hunt.recentSignal(samples, last + 1_500))
        assertEquals(5, Hunt.recentSignal(samples, last).size)
        assertTrue(Hunt.recentSignal(samples, last + Hunt.QUIET_MS + 1).isEmpty())
        assertEquals(HuntCue.QUIET, Hunt.cue(samples, last + Hunt.QUIET_MS + 1, last, false))
    }

    @Test fun denseBroadcastsConfirmApproachOrRetreatWithinThreeSeconds() {
        fun run(before: Int, after: Int): HuntCue {
            val samples = (0..15).map { RssiSample(10_000L + it * 200, if (it < 6) before else after) }
            return Hunt.cue(samples, 13_000, 13_000, false)
        }
        assertEquals(HuntCue.CLOSER, run(-75, -60))
        assertEquals(HuntCue.FURTHER, run(-60, -75))
    }

    @Test fun smoothingRejectsOneSpikeAndExcludesFutureOrUnavailableReadings() {
        val samples = listOf(RssiSample(10_000, -70), RssiSample(10_500, -71),
            RssiSample(11_000, -20), RssiSample(11_100, 127), RssiSample(20_000, -30))
        assertEquals(-70.0, Hunt.median(Hunt.recentSignal(samples, 11_200))!!, 0.01)
        assertTrue(Hunt.recentSignal(samples, 14_000).isEmpty())
    }

    private val now = 100_000L

    @Test
    fun strongSignalDoesNotOverrideApproachTrend() {
        val samples = listOf(
            RssiSample(now - 6_000, -70),
            RssiSample(now - 5_000, -68),
            RssiSample(now - 4_000, -69),
            RssiSample(now - 1_800, -38),
            RssiSample(now - 400, -36),
            RssiSample(now - 100, -37),
        )
        assertEquals(HuntCue.CLOSER, Hunt.cue(samples, now, now - 400, missing = false))
    }

    @Test
    fun belowVeryCloseStillUsesRelativeCue() {
        val samples = listOf(
            RssiSample(now - 6_000, -72),
            RssiSample(now - 5_000, -70),
            RssiSample(now - 4_000, -71),
            RssiSample(now - 1_800, -60),
            RssiSample(now - 400, -58),
            RssiSample(now - 100, -59),
        )
        assertEquals(HuntCue.CLOSER, Hunt.cue(samples, now, now - 400, missing = false))
    }

    @Test
    fun quietWinsOverALoudLastPacket() {
        val samples = listOf(RssiSample(now - 9_000, -30))
        assertEquals(HuntCue.QUIET, Hunt.cue(samples, now, now - 9_000, missing = false))
    }

    @Test
    fun goneWins() {
        assertEquals(HuntCue.GONE, Hunt.cue(emptyList(), now, now, missing = true))
    }

    @Test
    fun tickSilentWhenQuietGoneOrNoRssi() {
        assertEquals(null, Hunt.tickIntervalMs(-40, HuntCue.QUIET))
        assertEquals(null, Hunt.tickIntervalMs(-40, HuntCue.GONE))
        assertEquals(null, Hunt.tickIntervalMs(null, HuntCue.CLOSER))
    }

    @Test
    fun tickFasterWhenLouder() {
        val slow = Hunt.tickIntervalMs(-90, HuntCue.SAME)!!
        val mid = Hunt.tickIntervalMs(-65, HuntCue.CLOSER)!!
        val fast = Hunt.tickIntervalMs(-40, HuntCue.VERY_CLOSE)!!
        assertEquals(Hunt.TICK_SLOW_MS, slow)
        assertEquals(Hunt.TICK_FAST_MS, fast)
        assertTrue(mid in (fast + 1) until slow)
        val veryClose = Hunt.tickIntervalMs(-45, HuntCue.VERY_CLOSE)!!
        assertTrue(veryClose < mid)
        assertTrue(veryClose > fast)
    }
}
