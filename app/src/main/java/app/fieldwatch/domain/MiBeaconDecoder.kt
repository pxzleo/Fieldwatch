package app.fieldwatch.domain

import java.util.Locale

/** Passive MiBeacon v5 headers and plaintext objects; encrypted objects are never interpreted. */
object MiBeaconDecoder {
    // Bluetooth-Devices/xiaomi-ble device table, cross-checked against captured product IDs.
    // Product codes remain readable even when measurement objects are encrypted.
    private val models = mapOf(
        0x055B to "LYWSD03MMC", 0x2832 to "MJWSD05MMC", 0x4C47 to "MJWSD05MMC",
        0x55B5 to "MJWSD06MMC", 0x5BEA to "MJWSD06MMC", 0x2542 to "LYWSD02MMC",
        0x16E4 to "LYWSD02MMC", 0x30D9 to "S400 · MJTZC01YM", 0x3BD5 to "S400 · MJTZC01YM",
        0x48CF to "S400 · MJTZC01YM", 0x0863 to "SJWS01LM", 0x098C to "XMZNMST02YD",
        0x0784 to "XMZNMS04LM", 0x0E39 to "XMZNMS08LM", 0x0576 to "CGD1",
    )

    data class Product(val model: String, val type: String, val fleetId: String)

    fun product(id: Int?): Product? {
        val model = models[id ?: return null] ?: return null
        val typeAndFleet = when (id) {
            0x055B -> "temperature / humidity sensor" to "fleet-lywsd03mmc"
            0x2832, 0x4C47, 0x55B5, 0x5BEA -> "temperature / humidity sensor" to "fleet-mi-thermometer"
            0x2542, 0x16E4 -> "temperature / humidity sensor" to "fleet-lywsd02mmc"
            0x0576 -> "temperature / humidity sensor" to "fleet-cgd1"
            0x30D9, 0x3BD5, 0x48CF -> "body composition scale" to "fleet-mijia-s400"
            0x0863 -> "water leak sensor" to "fleet-mi-water-leak"
            0x098C, 0x0784, 0x0E39 -> "door lock" to "fleet-mi-lock"
            else -> return null
        }
        return Product(model, typeAndFleet.first, typeAndFleet.second)
    }

    fun productRules(fleetId: String): List<MatchRule> = models.keys.filter { product(it)?.fleetId == fleetId }
        .map { MatchRule(RuleKind.MIBEACON_PRODUCT_ID, text = "%04X".format(it), radio = RadioKind.BLE) }

    fun identities(device: Sighting): List<Decoded> {
        if (device.kind != RadioKind.BLE) return emptyList()
        val manufacturer = device.facts.mfgRecords.ifEmpty {
            device.manufacturerId?.let { listOf(MfgRecord(it, device.manufacturerDataHex)) }.orEmpty()
        }
        val records = device.facts.serviceData.filter { uuidAliases(it.uuid).any { alias -> alias in uuidAliases("FE95") } }.map { it.dataHex } +
            manufacturer.filter { it.companyId == 0x038F }.map { it.dataHex }
        return records.distinct().map { decode(it) }.filter { it.validHeader }
    }

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
        field("MiBeacon product ID", "0x%04X".format(pid) + models[pid]?.let { " · $it" }.orEmpty())
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
            if ((capability ushr 3) and 3 == 3) {
                if (bytes.size - offset < 2) return error("Truncated MiBeacon header", pid)
                field("MiBeacon Wi-Fi MAC suffix (raw)", bytes.copyOfRange(offset, offset + 2).toHexUpper())
                offset += 2
            }
            if (capability and 0x20 != 0) {
                if (bytes.size - offset < 2) return error("Truncated MiBeacon header", pid)
                field("MiBeacon I/O capability", "0x%04X".format(u16(bytes, offset)))
                offset += 2
            }
            if (version == 5) {
                field("MiBeacon connectable capability (advertised)", translate(if (capability and 1 != 0) "yes" else "no"))
                field("MiBeacon encryption capability (advertised)", translate(if (capability and 4 != 0) "yes" else "no"))
            }
        }
        if (version != 5) return error("Unsupported MiBeacon version", pid)
        field("MiBeacon registered flag (advertised)", translate(if (control and 0x100 != 0) "yes" else "no"))
        field("MiBeacon binding confirmation flag (advertised)", translate(if (control and 0x200 != 0) "yes" else "no"))
        val objectEnd = bytes.size - if (control and 0x80 != 0) 2 else 0
        if (objectEnd < offset) return error("Truncated MiBeacon mesh information", pid)
        if (control and 0x80 != 0) {
            val mesh = u8(bytes, objectEnd)
            field("MiBeacon mesh provisioning transports (advertised)", listOfNotNull(
                "PB-ADV".takeIf { mesh and 1 != 0 }, "PB-GATT".takeIf { mesh and 2 != 0 },
            ).joinToString(" / ").ifEmpty { translate("None advertised") })
            field("MiBeacon mesh state (raw)", "0x%X".format((mesh ushr 2) and 3))
            field("MiBeacon mesh version", (mesh ushr 4).toString())
        }
        if (encrypted) {
            if (objectEnd - offset < 7) return error("Truncated MiBeacon encrypted payload", pid)
            field("MiBeacon readings status", translate("Encrypted measurement payload; bindkey required to read values."))
            return Decoded(pid, true, fields)
        }
        if (control and 0x40 == 0) {
            field("MiBeacon readings status", translate("This frame contains no measurement objects; not a zero reading."))
            return Decoded(pid, true, fields)
        }
        if (offset == objectEnd) return error("Truncated MiBeacon object", pid)
        while (offset < objectEnd) {
            if (objectEnd - offset < 3) return error("Truncated MiBeacon object", pid)
            val type = u16(bytes, offset)
            val length = u8(bytes, offset + 2)
            offset += 3
            if (objectEnd - offset < length) return error("Truncated MiBeacon object", pid)
            val expected = when (type) {
                0x1004, 0x1006 -> 2
                0x100D -> 4
                0x1007 -> 3
                0x100A, 0x1012, 0x1014, 0x1015, 0x1018, 0x1019 -> 1
                else -> null
            }
            if (expected != null && length != expected) return error("Malformed MiBeacon object", pid)
            fun temperature(at: Int) = "%.1f °C".format(Locale.US, u16(bytes, at).toShort().toDouble() / 10)
            fun humidity(at: Int) = "%.1f %%".format(Locale.US, u16(bytes, at).toDouble() / 10)
            when (type) {
                0x1004 -> field("MiBeacon temperature", temperature(offset))
                0x1006 -> field("MiBeacon humidity", humidity(offset))
                0x100D -> { field("MiBeacon temperature", temperature(offset)); field("MiBeacon humidity", humidity(offset + 2)) }
                0x100A -> field("MiBeacon battery", "${u8(bytes, offset)} %")
                // Some products reinterpret 1007 as a light threshold. Keep its published raw unitless value.
                0x1007 -> field("MiBeacon light raw value", (u16(bytes, offset) or (u8(bytes, offset + 2) shl 16)).toString())
                0x1012 -> field("MiBeacon on/off raw state", "0x%02X".format(u8(bytes, offset)))
                0x1018 -> field("MiBeacon light raw state", "0x%02X".format(u8(bytes, offset)))
                0x1014 -> field("MiBeacon water detected", translate(if (u8(bytes, offset) > 0) "yes" else "no"))
                0x1015 -> field("MiBeacon smoke detected", translate(if (u8(bytes, offset) > 0) "yes" else "no"))
                0x1019 -> field("MiBeacon door/window state", when (u8(bytes, offset)) {
                    0 -> translate("Open")
                    1 -> translate("Closed")
                    else -> "0x%02X".format(u8(bytes, offset))
                })
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
