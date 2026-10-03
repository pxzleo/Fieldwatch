package app.fieldwatch.domain

/** Keep the raw frame that last supplied each valid field, independently of rotating advertisements. */
object BlePayloadHistory {
    fun capture(device: Sighting, previous: List<BlePayloadSnapshot> = emptyList(), logSnapshot: Boolean = false): RadioFacts {
        if (device.kind != RadioKind.BLE || device.lastSeen <= 0 || device.facts.bleHistory.isNotEmpty()) return device.facts
        val source = BlePayloadSnapshot(device.lastSeen, logSnapshot,
            device.facts.mfgRecords.ifEmpty {
                device.manufacturerId?.let { listOf(MfgRecord(it, device.manufacturerDataHex)) }.orEmpty()
            }, device.facts.serviceData, device.rawHex, name = device.name, mac = device.mac, fleetIds = device.fleetIds)
        // A repeated complete source already has its field keys; no decoder work is needed.
        val repeated = previous.firstOrNull { it.copy(observedAt = source.observedAt, logSnapshot = source.logSnapshot, labels = emptyList(), decodedLabels = emptyList()) == source }
        val labels = repeated?.decodedLabels ?: validFields(device).map { it.label }.distinct()
        return if (labels.isEmpty()) device.facts else device.facts.copy(bleHistory = listOf(source.copy(labels = labels, decodedLabels = labels)))
    }

    fun merge(old: List<BlePayloadSnapshot>, incoming: List<BlePayloadSnapshot>): List<BlePayloadSnapshot> {
        if (incoming.isEmpty()) return old
        val owners = linkedMapOf<String, BlePayloadSnapshot>()
        (old + incoming).forEach { snapshot ->
            snapshot.labels.forEach { label ->
                if (owners[label]?.observedAt?.let { it > snapshot.observedAt } != true) owners[label] = snapshot
            }
        }
        return owners.entries.groupBy({ it.value }, { it.key }).map { (snapshot, labels) -> snapshot.copy(labels = labels) }
    }

    fun fields(device: Sighting, translate: (String) -> String): List<AdvPayloadDecoder.Field> = device.facts.bleHistory.flatMap { snapshot ->
        val original = device.copy(name = snapshot.name, mac = snapshot.mac, fleetIds = snapshot.fleetIds,
            manufacturerId = null, manufacturerDataHex = "", rawHex = snapshot.rawHex,
            facts = RadioFacts(mfgRecords = snapshot.mfgRecords, serviceData = snapshot.serviceData))
        val keys = validFields(original).filter { it.label in snapshot.labels }.map { it.label }.toSet()
        val localized = validFields(original, translate).filter { it.label in keys.map(translate) }
        val time = java.time.Instant.ofEpochMilli(snapshot.observedAt).toString()
        localized.map { field -> AdvPayloadDecoder.Field(
            translate("Last valid BLE field: %1\$s").format(field.label),
            translate(if (snapshot.logSnapshot) "%1\$s · log snapshot: %2\$s (not a new reception)" else "%1\$s · observed at: %2\$s").format(field.value, time)) }
    }

    internal fun validFields(device: Sighting, translate: (String) -> String = { it }): List<AdvPayloadDecoder.Field> {
        fun accepted(fields: List<AdvPayloadDecoder.Field>): List<AdvPayloadDecoder.Field> {
            if (fields.any { it.label == translate("MiBeacon parse status") || it.label == translate("Mi Scale parse status") ||
                    it.label == translate("Find Hub parse status") }) return emptyList()
            return fields.filterNot { field ->
                val label = field.label
                listOf("MiBeacon readings status", "Midea readings status", "Apple payload", "Payload", "Google manufacturer data", "Microsoft manufacturer data", "Eddystone").any { label == translate(it) } ||
                    listOf("Unsupported", "Malformed", "Truncated", "truncated", "Encrypted or unsupported", "Payload parser unsupported", "Not supported").any { field.value.startsWith(translate(it)) } ||
                    field.value == translate("Encrypted or unsupported telemetry version; readings unavailable") ||
                    field.value == translate("Malformed telemetry length; readings unavailable") ||
                    field.value.contains(translate("unknown / not present")) ||
                    (label == translate("Eddystone-URL") && !field.value.startsWith("http")) ||
                    field.value == translate("Unsupported or malformed AirPlay target length") ||
                    field.value == translate("Unsupported or malformed AWDL message length")
            }
        }
        val mfg = device.facts.mfgRecords.ifEmpty { device.manufacturerId?.let { listOf(MfgRecord(it, device.manufacturerDataHex)) }.orEmpty() }
        return (mfg.flatMap { accepted(AdvPayloadDecoder.decodeManufacturer(it, translate) + MideaAdvertisementDecoder.decodeAddress(it, device.mac, translate)) } +
            device.facts.serviceData.flatMap { accepted(AdvPayloadDecoder.decodeService(it, device, translate)) } +
            AdvPayloadDecoder.decodeMesh(device.rawHex, translate)).distinct()
    }
}
