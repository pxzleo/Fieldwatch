package app.fieldwatch.domain

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Screen top edge projected onto the horizontal plane, relative to magnetic north. */
data class HuntHeading(val degrees: Int, val at: Long, val reliable: Boolean) {
    val directionIndex: Int get() = ((degrees + 22) / 45) % 8

    fun fresh(now: Long): Boolean = now - at in 0..2_000L

    companion object {
        fun fromAxes(east: Double, north: Double, at: Long, reliable: Boolean): HuntHeading? {
            // An upright top edge has no meaningful horizontal heading.
            if (!east.isFinite() || !north.isFinite() || hypot(east, north) < 0.1) return null
            val degrees = ((Math.toDegrees(atan2(east, north)).roundToInt() % 360) + 360) % 360
            return HuntHeading(degrees, at, reliable)
        }
    }
}
