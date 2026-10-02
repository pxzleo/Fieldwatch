package app.fieldwatch.domain

import java.util.Locale

/** Xiaomi layouts require an explicit factory name or the matching catalog family. */
object MiScaleDecoder {
    fun applies(device: Sighting?): Boolean = device?.kind == RadioKind.BLE &&
        ("fleet-mi-scale" in device.fleetIds || listOf("MI_SCALE", "MI SCALE", "MIBCS", "MIBFS").any { device.name.startsWith(it, true) })

    fun decode(uuid: Int, hex: String, translate: (String) -> String = { it }): List<AdvPayloadDecoder.Field> {
        val fields = ArrayList<AdvPayloadDecoder.Field>()
        fun field(label: String, value: String) { fields += AdvPayloadDecoder.Field(translate(label), value) }
        fun error(message: String): List<AdvPayloadDecoder.Field> {
            field("Mi Scale parse status", translate(message))
            return fields
        }
        val bytes = strictHexBytes(hex) ?: return error("Malformed Mi Scale payload")
        val expected = if (uuid == 0x181D) 10 else 13
        if (bytes.size < expected) return error("Truncated Mi Scale payload")
        if (bytes.size != expected) return error("Unsupported Mi Scale payload length")
        fun u8(at: Int) = bytes[at].toInt() and 0xFF
        fun u16(at: Int) = u8(at) or (u8(at + 1) shl 8)
        val status = u8(if (uuid == 0x181D) 0 else 1)
        val empty = status and 0x80 != 0
        val stable = status and 0x20 != 0
        val unit = when {
            u8(0) and 0x01 != 0 -> "lb"
            status and (if (uuid == 0x181D) 0x10 else 0x40) != 0 -> translate("jin")
            else -> "kg"
        }
        field("Mi Scale weighing state", translate(when {
            empty -> "Empty / load removed — no current weight"
            stable -> "Stable weight"
            else -> "Unstable weight — measurement in progress"
        }))
        if (!empty) {
            val raw = u16(if (uuid == 0x181D) 1 else 11)
            val divisor = if (u8(0) and 0x01 != 0 || status and (if (uuid == 0x181D) 0x10 else 0x40) != 0) 100.0 else 200.0
            field(if (stable) "Mi Scale weight" else "Mi Scale provisional weight", "%.2f %s".format(Locale.US, raw / divisor, unit))
            if (uuid == 0x181B && status and 0x02 != 0) field("Mi Scale impedance", "${u16(9)} Ω")
        }
        return fields
    }
}
