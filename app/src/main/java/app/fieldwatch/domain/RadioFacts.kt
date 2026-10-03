package app.fieldwatch.domain

import kotlinx.serialization.Serializable

@Serializable
data class MfgRecord(
    val companyId: Int,
    val dataHex: String,
)

@Serializable
data class VendorIeRecord(
    val oui: String,
    val type: Int,
    val dataHex: String,
)

@Serializable
data class ServiceDataRecord(
    val uuid: String,
    val dataHex: String,
)

/** Last complete, self-declared WPS identity, separate from current protocol/status bytes. */
@Serializable
data class WpsIdentitySnapshot(val record: VendorIeRecord, val observedAt: Long, val logSnapshot: Boolean = false)

/** Raw source of the most recent successfully decoded BLE fields; labels are untranslated keys. */
@Serializable
data class BlePayloadSnapshot(
    val observedAt: Long,
    val logSnapshot: Boolean = false,
    val mfgRecords: List<MfgRecord> = emptyList(),
    val serviceData: List<ServiceDataRecord> = emptyList(),
    val rawHex: String = "",
    val labels: List<String> = emptyList(),
    val decodedLabels: List<String> = labels,
    val name: String = "",
    val mac: String = "",
    val fleetIds: Set<String> = emptySet(),
)

@Serializable
data class RadioFacts(
    val txPowerDbm: Int? = null,
    val advFlags: Int? = null,
    val appearance: Int? = null,
    val addressType: String? = null,
    val advertisingIntervalMs: Double? = null,
    val periodicIntervalMs: Double? = null,
    val connectable: Boolean? = null,
    val primaryPhy: String? = null,
    val secondaryPhy: String? = null,
    val deviceClass: Int? = null,
    val wifiStandard: String? = null,
    val channelWidth: String? = null,
    val centerFreq0: Int? = null,
    val centerFreq1: Int? = null,
    val capabilities: String? = null,
    val supportedRates: String? = null,
    val security: String? = null,
    val mfgRecords: List<MfgRecord> = emptyList(),
    val vendorIes: List<VendorIeRecord> = emptyList(),
    val serviceData: List<ServiceDataRecord> = emptyList(),
    val bleHistory: List<BlePayloadSnapshot> = emptyList(),
    val wpsIdentity: WpsIdentitySnapshot? = null,
) {
    fun captureWpsIdentity(at: Long, logSnapshot: Boolean = false): RadioFacts {
        if (wpsIdentity != null || at <= 0) return this
        val record = vendorIes.firstOrNull { WifiWpsDecoder.identity(listOf(it)) != null } ?: return this
        return copy(wpsIdentity = WpsIdentitySnapshot(record, at, logSnapshot))
    }

    fun merge(newer: RadioFacts): RadioFacts = copy(
        txPowerDbm = newer.txPowerDbm ?: txPowerDbm,
        advFlags = newer.advFlags ?: advFlags,
        appearance = newer.appearance ?: appearance,
        addressType = newer.addressType ?: addressType,
        advertisingIntervalMs = newer.advertisingIntervalMs ?: advertisingIntervalMs,
        periodicIntervalMs = newer.periodicIntervalMs ?: periodicIntervalMs,
        // Scan responses report not connectable; keep Yes once any ad was.
        connectable = when {
            connectable == true || newer.connectable == true -> true
            else -> newer.connectable ?: connectable
        },
        primaryPhy = newer.primaryPhy ?: primaryPhy,
        secondaryPhy = newer.secondaryPhy ?: secondaryPhy,
        deviceClass = newer.deviceClass ?: deviceClass,
        wifiStandard = newer.wifiStandard ?: wifiStandard,
        channelWidth = newer.channelWidth ?: channelWidth,
        centerFreq0 = newer.centerFreq0 ?: centerFreq0,
        centerFreq1 = newer.centerFreq1 ?: centerFreq1,
        capabilities = newer.capabilities?.ifBlank { null } ?: capabilities,
        supportedRates = newer.supportedRates?.ifBlank { null } ?: supportedRates,
        security = newer.security?.ifBlank { null } ?: security,
        mfgRecords = mergeMfg(mfgRecords, newer.mfgRecords),
        vendorIes = mergeVendorIes(vendorIes, newer.vendorIes),
        serviceData = mergeServiceData(serviceData, newer.serviceData),
        bleHistory = BlePayloadHistory.merge(bleHistory, newer.bleHistory),
        wpsIdentity = if (newer.wpsIdentity != null &&
            (wpsIdentity == null || newer.wpsIdentity.observedAt >= wpsIdentity.observedAt)) newer.wpsIdentity else wpsIdentity,
    )

    companion object {
        val Empty = RadioFacts()
    }
}

private fun mergeMfg(old: List<MfgRecord>, extra: List<MfgRecord>): List<MfgRecord> {
    if (extra.isEmpty()) return old
    fun source(record: MfgRecord) = record.companyId to
        when {
            record.companyId == 0x038F -> ""
            record.companyId == 0x06A8 && MideaAdvertisementDecoder.decode(record).isNotEmpty() -> "SN"
            else -> record.dataHex.take(2).uppercase()
        }
    val currentSources = extra.map(::source).toSet()
    // Keep every distinct record in this frame; replace prior samples of those sources.
    return old.filterNot { source(it) in currentSources } + extra.distinct()
}

private fun mergeVendorIes(old: List<VendorIeRecord>, extra: List<VendorIeRecord>): List<VendorIeRecord> {
    if (extra.isEmpty()) return old
    val currentSources = extra.map { it.oui to it.type }.toSet()
    return old.filterNot { (it.oui to it.type) in currentSources } + extra.distinct()
}

private fun mergeServiceData(old: List<ServiceDataRecord>, extra: List<ServiceDataRecord>): List<ServiceDataRecord> {
    if (extra.isEmpty()) return old
    val by = LinkedHashMap<String, ServiceDataRecord>()
    old.forEach { by[serviceDataMergeKey(it)] = it }
    extra.forEach { rec ->
        if (rec.dataHex.isBlank()) return@forEach
        val key = serviceDataMergeKey(rec)
        by[key] = rec
    }
    return by.values.toList()
}

/** Eddystone FEAA rotates UID / URL / TLM; keep one slot per frame type. */
private fun serviceDataMergeKey(rec: ServiceDataRecord): String {
    val uuidHex = rec.uuid.filter { it.isLetterOrDigit() }.uppercase()
    val short = when {
        uuidHex.length == 4 -> uuidHex
        uuidHex.length == 32 && uuidHex.startsWith("0000") && uuidHex.endsWith("00001000800000805F9B34FB") -> uuidHex.substring(4, 8)
        else -> uuidHex
    }
    if (short == "FEAA") {
        val frame = rec.dataHex.filter { it.isLetterOrDigit() }.uppercase().take(2)
        if (frame.length == 2) return "FEAA:$frame"
    }
    return short.ifBlank { rec.uuid }
}

fun ByteArray.toHexUpper(): String = joinToString("") { "%02X".format(it) }

fun String.hexSpaced(): String = chunked(2).joinToString(" ")
