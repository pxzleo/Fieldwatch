package app.fieldwatch.domain

/** Objective structure for captured unknown families; byte similarity is not a product decoder. */
object BleServiceInspection {
    private val services = listOf("4669", "FDEE", "FF01", "FF02",
        "5810BBC0-B499-11E9-A2A3-2A2AE2DBCCFF", "5810BBC0-B499-11E9-A2A3-2A2AE2DBCCE4")

    private fun family(uuid: String) = services.firstOrNull { expected ->
        uuidAliases(uuid).any { it in uuidAliases(expected) }
    }

    fun fields(record: ServiceDataRecord, translate: (String) -> String = { it }): List<AdvPayloadDecoder.Field> {
        val service = family(record.uuid) ?: return emptyList()
        val bytes = strictHexBytes(record.dataHex) ?: return emptyList()
        if (bytes.isEmpty()) return emptyList()
        val prefix = bytes.take(2).toByteArray().toHexUpper()
        val fields = mutableListOf(
            AdvPayloadDecoder.Field(translate("Service %1\$s payload structure").format(service),
                translate("%1\$d bytes; raw prefix %2\$s").format(bytes.size, prefix)),
            AdvPayloadDecoder.Field(translate("Service %1\$s payload interpretation").format(service),
                translate("Device type, model and private state fields are unconfirmed.")),
        )
        if (service == "FDEE") fields += AdvPayloadDecoder.Field(translate("FDEE public-layout compatibility"),
            translate(if (bytes[0].toInt() and 255 != 4)
                "Public SoftBus v4 layout does not apply to this header."
                else "Header starts with 0x04; the remaining SoftBus layout has not been validated."))
        return fields
    }

    fun sharedFields(device: Sighting, peers: Collection<Sighting>, translate: (String) -> String = { it }): List<AdvPayloadDecoder.Field> {
        if (device.kind != RadioKind.BLE) return emptyList()
        return device.facts.serviceData.mapNotNull { record ->
            val service = family(record.uuid) ?: return@mapNotNull null
            val bytes = strictHexBytes(record.dataHex)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val count = (peers + device).filter { it.kind == RadioKind.BLE }.distinctBy { it.key }.count { peer ->
                peer.facts.serviceData.any { other -> family(other.uuid) == service &&
                    strictHexBytes(other.dataHex)?.contentEquals(bytes) == true }
            }
            if (count < 2) null else AdvPayloadDecoder.Field(translate("Service %1\$s shared payload").format(service),
                translate("%1\$d address records share identical captured content; physical-device count is unconfirmed.").format(count))
        }.distinct()
    }
}
