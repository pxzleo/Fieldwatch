package app.fieldwatch.domain

/** AP WMM Information/Parameter IE, excluding the OUI and type already held by VendorIeRecord. */
object WifiWmmDecoder {
    fun decode(record: VendorIeRecord, translate: (String) -> String = { it }): List<AdvPayloadDecoder.Field> {
        if (WifiWpsDecoder.protocolName(record) != "WMM") return emptyList()
        val bytes = strictHexBytes(record.dataHex)
        val subtype = bytes?.firstOrNull()?.toInt()?.and(255)
        if (subtype != null && subtype !in 0..1) return listOf(
            AdvPayloadDecoder.Field(translate("WMM parse status"), translate("Unsupported WMM subtype")))
        val expected = if (subtype == 0) 3 else 20
        if (bytes == null || bytes.size != expected || bytes[1] != 1.toByte() ||
            (subtype == 1 && bytes[3] != 0.toByte())) return listOf(
            AdvPayloadDecoder.Field(translate("WMM parse status"), translate("Malformed or unsupported WMM element")))
        return listOf(
            AdvPayloadDecoder.Field(translate("WMM traffic prioritization (advertised)"),
                translate("Access point advertises WMM; this does not measure throughput or latency.")),
            AdvPayloadDecoder.Field(translate("WMM client power saving (U-APSD, advertised)"), translate(
                if ((bytes[2].toInt() and 0x80) != 0) "Supported; client support and negotiation are required"
                else "Not advertised in this element")),
        )
    }
}
