package app.fieldwatch.domain

import java.util.Locale

/** Passive MiBeacon v5 headers and plaintext objects; encrypted objects are never interpreted. */
object MiBeaconDecoder {
    data class Decoded(val productId: Int?, val validHeader: Boolean, val fields: List<AdvPayloadDecoder.Field>)

    fun decode(hex: String, translate: (String) -> String = { it }): Decoded {
        val fields = ArrayList<AdvPayloadDecoder.Field>()
        fun field(label: String, value: String) { fields += AdvPayloadDecoder.Field(translate(label), value) }
        fun error(message: String, pid: Int? = null): Decoded {
            field("MiBeacon parse status", translate(message))
            return Decoded(pid, false, fields)
        }
        val bytes = strictHexBytes(hex) ?: return error("Malformed MiBeacon payload")
        if (bytes.size < 5) return error("Truncated MiBeacon header")
        val control = u16(bytes, 0)
        val version = control ushr 12
        val pid = u16(bytes, 2)
        field("MiBeacon version", version.toString())
        field("MiBeacon product ID", "0x%04X".format(pid) + if (pid == 0x055B) " · LYWSD03MMC" else "")
        field("MiBeacon frame counter", u8(bytes, 4).toString())
        val encrypted = control and 0x08 != 0
        field("MiBeacon encryption", translate(if (encrypted) "Encrypted — readings unavailable" else "Plaintext"))
        var offset = 5
        if (control and 0x10 != 0) {
            if (bytes.size - offset < 6) return error("Truncated MiBeacon header", pid)
            field("MiBeacon advertised MAC", bytes.copyOfRange(offset, offset + 6).reversedArray().toHexUpper().chunked(2).joinToString(":"))
            offset += 6
        }
        if (control and 0x20 != 0) {
            if (bytes.size - offset < 1) return error("Truncated MiBeacon header", pid)
            val capability = u8(bytes, offset++)
            field("MiBeacon capability", "0x%02X".format(capability))
            if (capability and 0x20 != 0) {
                if (bytes.size - offset < 2) return error("Truncated MiBeacon header", pid)
                field("MiBeacon I/O capability", "0x%04X".format(u16(bytes, offset)))
                offset += 2
            }
        }
        if (version != 5) return error("Unsupported MiBeacon version", pid)
        if (encrypted) {
            if (bytes.size - offset < 7) return error("Truncated MiBeacon encrypted payload", pid)
            return Decoded(pid, true, fields)
        }
        if (control and 0x40 == 0) return Decoded(pid, true, fields)
        if (offset == bytes.size) return error("Truncated MiBeacon object", pid)
        while (offset < bytes.size) {
            if (bytes.size - offset < 3) return error("Truncated MiBeacon object", pid)
            val type = u16(bytes, offset)
            val length = u8(bytes, offset + 2)
            offset += 3
            if (bytes.size - offset < length) return error("Truncated MiBeacon object", pid)
            val expected = when (type) { 0x1004, 0x1006 -> 2; 0x100D -> 4; 0x100A -> 1; else -> null }
            if (expected != null && length != expected) return error("Malformed MiBeacon object", pid)
            fun temperature(at: Int) = "%.1f °C".format(Locale.US, u16(bytes, at).toShort().toDouble() / 10)
            fun humidity(at: Int) = "%.1f %%".format(Locale.US, u16(bytes, at).toDouble() / 10)
            when (type) {
                0x1004 -> field("MiBeacon temperature", temperature(offset))
                0x1006 -> field("MiBeacon humidity", humidity(offset))
                0x100D -> { field("MiBeacon temperature", temperature(offset)); field("MiBeacon humidity", humidity(offset + 2)) }
                0x100A -> field("MiBeacon battery", "${u8(bytes, offset)} %")
                else -> field("MiBeacon unknown object", "0x%04X: ".format(type) + bytes.copyOfRange(offset, offset + length).toHexUpper())
            }
            offset += length
        }
        return Decoded(pid, true, fields)
    }

    private fun u8(bytes: ByteArray, at: Int) = bytes[at].toInt() and 0xFF
    private fun u16(bytes: ByteArray, at: Int) = u8(bytes, at) or (u8(bytes, at + 1) shl 8)
}

/** Reject invalid hex rather than stripping invalid characters into a different payload. */
internal fun strictHexBytes(hex: String): ByteArray? {
    val compact = hex.filterNot { it.isWhitespace() }
    if (compact.length % 2 != 0 || compact.any { it.digitToIntOrNull(16) == null }) return null
    return ByteArray(compact.length / 2) { compact.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
