package app.fieldwatch.domain

import org.junit.Assert.*
import org.junit.Test

class HuntHeadingTest {
    @Test fun headingsFollowMagneticCompassAndWrapAtNorth() {
        assertEquals(0, HuntHeading.fromAxes(0.0, 1.0, 100, true)!!.degrees)
        assertEquals(90, HuntHeading.fromAxes(1.0, 0.0, 100, true)!!.degrees)
        assertEquals(180, HuntHeading.fromAxes(0.0, -1.0, 100, true)!!.degrees)
        assertEquals(270, HuntHeading.fromAxes(-1.0, 0.0, 100, true)!!.degrees)
        assertEquals(0, HuntHeading.fromAxes(-0.001, 1.0, 100, true)!!.directionIndex)
        assertEquals(1, HuntHeading.fromAxes(1.0, 1.0, 100, true)!!.directionIndex)
        assertEquals(7, HuntHeading.fromAxes(-1.0, 1.0, 100, true)!!.directionIndex)
    }

    @Test fun verticalInvalidAndStaleReadingsCannotInventHeading() {
        assertNull(HuntHeading.fromAxes(0.0, 0.0, 100, true))
        assertNull(HuntHeading.fromAxes(Double.NaN, 1.0, 100, true))
        val low = HuntHeading.fromAxes(0.0, 1.0, 100, false)!!
        assertFalse(low.reliable)
        assertTrue(low.fresh(2_100))
        assertFalse(low.fresh(2_101))
        assertFalse(low.fresh(99))
    }
}
