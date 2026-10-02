package app.fieldwatch.domain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** WPS vendor IE payload: big-endian 16-bit attribute type and byte length, then value. */
object WifiWpsDecoder {
    enum class Status { COMPLETE, EMPTY, TRUNCATED, MALFORMED }

    data class Decoded(
        val manufacturer: String?,
        val model: String?,
        val fields: List<AdvPayloadDecoder.Field>,
        val status: Status,
        val primaryDeviceType: String? = null,
    ) {
        val chipsetOnly: Boolean get() = manufacturer?.let { it.contains("Realtek", true) || it.contains("Ralink", true) } == true
        fun identityLabel(): String = listOfNotNull(manufacturer, model?.takeUnless { it.equals(manufacturer, true) }).joinToString(" ")
    }

    /** Identity requires both fields from the same advertised WPS record. */
    fun identities(records: List<VendorIeRecord>): List<Decoded> = records.mapNotNull { decode(it) }
        .filter { it.status == Status.COMPLETE && !it.manufacturer.isNullOrBlank() && !it.model.isNullOrBlank() }

    fun identity(records: List<VendorIeRecord>): Decoded? = identities(records).firstOrNull()

    fun protocolName(record: VendorIeRecord): String? {
        if (!record.oui.replace(":", "").replace("-", "").equals("0050F2", true)) return null
        return when (record.type) {
            1 -> "WPA"
            2 -> "WMM"
            4 -> "WPS"
            else -> null
        }
    }

    fun decode(record: VendorIeRecord, translate: (String) -> String = { it }): Decoded? {
        if (protocolName(record) != "WPS") return null
        val fields = ArrayList<AdvPayloadDecoder.Field>()
        var manufacturer: String? = null
        var model: String? = null
        var primaryDeviceType: String? = null
        val hex = record.dataHex
        if (hex.length % 2 != 0 || hex.any { it.digitToIntOrNull(16) == null }) {
            return Decoded(null, null, listOf(AdvPayloadDecoder.Field(translate("WPS parse status"),
                translate("Malformed WPS attributes"))), Status.MALFORMED)
        }
        val bytes = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        var status = if (bytes.isEmpty()) Status.EMPTY else Status.COMPLETE
        var offset = 0
        while (offset < bytes.size) {
            if (bytes.size - offset < 4) {
                status = Status.TRUNCATED
                break
            }
            val type = u16(bytes, offset)
            val length = u16(bytes, offset + 2)
            offset += 4
            if (length > bytes.size - offset) {
                status = Status.TRUNCATED
                break
            }
            val value = bytes.copyOfRange(offset, offset + length)
            offset += length
            val label = when (type) {
                0x1021 -> "WPS manufacturer"
                0x1023 -> "WPS model"
                0x1024 -> "WPS model number"
                0x1011 -> "WPS device name"
                0x1042 -> "WPS advertised serial number"
                else -> null
            }
            if (label != null) {
                val text = utf8(value)
                if (text == null) {
                    status = Status.MALFORMED
                    continue
                }
                if (text.isBlank()) continue
                fields += AdvPayloadDecoder.Field(translate(label), text)
                if (type == 0x1021) manufacturer = text
                if (type == 0x1023) model = text
            } else if (type in setOf(0x104A, 0x1057, 0x1041, 0x103C, 0x1008)) {
                val expected = if (type == 0x1008) 2 else 1
                if (length != expected) {
                    status = Status.MALFORMED
                    continue
                }
                val raw = value[0].toInt() and 255
                val fieldLabel = when (type) {
                    0x104A -> "WPS attribute version (not firmware)"
                    0x1057 -> "WPS AP setup locked"
                    0x1041 -> "WPS selected registrar"
                    0x103C -> "WPS RF bands"
                    else -> "WPS configuration methods (advertised mask)"
                }
                val shown = when (type) {
                    0x104A -> "${raw ushr 4}.${raw and 15}"
                    0x1057, 0x1041 -> when (raw) {
                        0 -> translate("no")
                        1 -> translate("yes")
                        else -> "0x%02X".format(raw)
                    }
                    0x103C -> buildList {
                        if ((raw and 1) != 0) add("2.4 GHz")
                        if ((raw and 2) != 0) add("5 GHz")
                        if ((raw and 4) != 0) add("60 GHz")
                        add("0x%02X".format(raw))
                    }.joinToString(" / ")
                    else -> "0x%04X".format(u16(value, 0))
                }
                fields += AdvPayloadDecoder.Field(translate(fieldLabel), shown)
                if (type == 0x1008) fields += AdvPayloadDecoder.Field(
                    translate("WPS setup methods (advertised)"), configurationMethods(u16(value, 0), translate))
            } else if (type == 0x1054) {
                if (length != 8) {
                    status = Status.MALFORMED
                    continue
                }
                primaryDeviceType = value.toHexUpper()
                fields += AdvPayloadDecoder.Field(translate("WPS primary device type"),
                    primaryType(value, translate))
            } else if (type == 0x1044) {
                if (length != 1) {
                    status = Status.MALFORMED
                    continue
                }
                val state = value[0].toInt() and 0xFF
                val shown = when (state) {
                    1 -> translate("WPS unconfigured")
                    2 -> translate("WPS configured")
                    else -> "0x%02X".format(state)
                }
                fields += AdvPayloadDecoder.Field(translate("WPS configuration state"), shown)
            }
        }
        if (status != Status.COMPLETE) {
            val message = when (status) {
                Status.EMPTY -> "No WPS attributes"
                Status.TRUNCATED -> "Truncated WPS attributes"
                else -> "Malformed WPS attributes"
            }
            fields += AdvPayloadDecoder.Field(translate("WPS parse status"), translate(message))
        }
        return Decoded(manufacturer, model, fields, status, primaryDeviceType)
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)

    private fun configurationMethods(mask: Int, translate: (String) -> String): String {
        val names = buildList {
            for ((bit, name) in listOf(1 to "USB", 2 to "Ethernet", 4 to "Printed PIN label",
                8 to "Displayed PIN", 16 to "External NFC token", 32 to "Integrated NFC token",
                64 to "NFC interface", 128 to "Push button", 256 to "PIN keypad", 4096 to "Wi-Fi Direct services")) {
                if ((mask and bit) != 0) add(translate(name))
            }
            if ((mask and 0x0280) == 0x0280) add(translate("Virtual push button"))
            if ((mask and 0x0480) == 0x0480) add(translate("Physical push button"))
            if ((mask and 0x2008) == 0x2008) add(translate("Virtual PIN display"))
            if ((mask and 0x4008) == 0x4008) add(translate("Physical PIN display"))
        }
        return (names.ifEmpty { listOf(translate("No known setup method")) }.joinToString(" / ") +
            "; " + translate("Advertised capability only; not proof that setup is currently allowed."))
    }

    private fun primaryType(value: ByteArray, translate: (String) -> String): String {
        if (!value.sliceArray(2..5).contentEquals(byteArrayOf(0, 0x50, 0xF2.toByte(), 4))) return value.toHexUpper()
        val category = u16(value, 0)
        val subcategory = u16(value, 6)
        if (category == 6 && subcategory == 1) return translate("WPS access point")
        val name = when (category) {
            1 -> "Computer"
            2 -> "Input device"
            3 -> "Printer / scanner"
            4 -> "Camera"
            5 -> "Storage device"
            6 -> "Network infrastructure"
            7 -> "Display"
            8 -> "Multimedia device"
            9 -> "Gaming device"
            10 -> "Telephone"
            11 -> "Audio device"
            else -> return value.toHexUpper()
        }
        return "${translate(name)} ($category/$subcategory; ${value.toHexUpper()})"
    }

    private fun utf8(bytes: ByteArray): String? = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: CharacterCodingException) {
        null
    }
}
