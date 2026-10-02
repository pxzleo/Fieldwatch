package app.fieldwatch.domain

/** Identity advertisements, not the authenticated GATT operating-state protocol. */
object MideaAdvertisementDecoder {
    // midea-ble/midea-ble-go docs/protocol.md §3.1; split manufacturer AD records
    // were also checked against captured SN frames and their scanned BLE addresses.
    fun decode(record: MfgRecord, translate: (String) -> String = { it }): List<AdvPayloadDecoder.Field> {
        if (record.companyId != 0x06A8) return emptyList()
        val bytes = strictHexBytes(record.dataHex) ?: return emptyList()
        if (bytes.size != 15 || bytes[0] != 1.toByte()) return emptyList()
        val chars = bytes.drop(1).map { (it.toInt() and 0xFF).toChar() }
        if (chars.any { it !in '0'..'9' && it !in 'A'..'Z' && it !in 'a'..'z' }) return emptyList()
        val serial = chars.joinToString("")
        return listOf(
            AdvPayloadDecoder.Field(translate("Midea advertised short serial"), serial),
            AdvPayloadDecoder.Field(translate("Midea SN8 product code"), serial.take(8)),
            AdvPayloadDecoder.Field(translate("Midea readings status"), translate("Identity advertisement; no operating measurements decoded. SN8 does not confirm a unique retail model.")),
        )
    }

    fun decodeAddress(record: MfgRecord, scannedMac: String, translate: (String) -> String = { it }): List<AdvPayloadDecoder.Field> {
        if (record.companyId != 0x06A8) return emptyList()
        val bytes = strictHexBytes(record.dataHex) ?: return emptyList()
        if (bytes.size !in listOf(11, 13) || bytes[0] != 1.toByte() || bytes[3] != 0x32.toByte()) return emptyList()
        val mac = bytes.copyOfRange(4, 10).reversedArray().toHexUpper().chunked(2).joinToString(":")
        // Other metadata bytes are undocumented. Only accept the address interpretation
        // when corroborated by the actual scan address, without inferring status bits.
        if (!mac.equals(scannedMac, ignoreCase = true)) return emptyList()
        return listOf(AdvPayloadDecoder.Field(translate("Midea advertised BLE address (matches scan)"), mac))
    }
}
