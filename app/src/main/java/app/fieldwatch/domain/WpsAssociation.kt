package app.fieldwatch.domain

/** Shared self-declared UUIDs are candidate links, never physical-device identities. */
object WpsAssociation {
    fun uuid(device: Sighting): String? {
        if (device.kind != RadioKind.WIFI) return null
        val ids = device.facts.vendorIes.mapNotNull { WifiWpsDecoder.decode(it)?.uuidE }.distinct()
        if (ids.size > 1) return null
        return ids.singleOrNull() ?: device.facts.wpsIdentity?.let { WifiWpsDecoder.decode(it.record)?.uuidE }
    }

    fun groups(devices: Collection<Sighting>): List<List<Sighting>> = devices
        .distinctBy { it.key }.mapNotNull { d -> uuid(d)?.let { it to d } }
        .groupBy({ it.first }, { it.second }).values.filter { it.size > 1 }

    fun fields(device: Sighting, peers: Collection<Sighting>, translate: (String) -> String = { it },
        demoMode: Boolean = false): List<AdvPayloadDecoder.Field> {
        val id = uuid(device) ?: return emptyList()
        val others = peers.filter { it.key != device.key && uuid(it) == id }.distinctBy { it.key }.sortedBy { it.mac }
        if (others.isEmpty()) return emptyList()
        fun identities(d: Sighting): List<WifiWpsDecoder.Decoded> {
            val current = d.facts.vendorIes.mapNotNull { WifiWpsDecoder.decode(it) }.filter { it.uuidE == id }
            return current.ifEmpty { d.facts.wpsIdentity?.let { WifiWpsDecoder.decode(it.record) }
                ?.takeIf { it.uuidE == id }?.let(::listOf).orEmpty() }
        }
        fun serials(d: Sighting) = identities(d).mapNotNull { decoded ->
            decoded.fields.firstOrNull { it.label == "WPS advertised serial number" }?.value }
        val own = identities(device).firstOrNull { !it.manufacturer.isNullOrBlank() && !it.model.isNullOrBlank() }
        val group = listOf(device) + others
        val identityRecords = group.flatMap(::identities)
        val conflicts = identityRecords.mapNotNull { it.manufacturer?.lowercase(java.util.Locale.ROOT) }.distinct().size > 1 ||
            identityRecords.mapNotNull { it.model?.lowercase(java.util.Locale.ROOT) }.distinct().size > 1 ||
            group.flatMap(::serials).distinct().size > 1
        val evidence = buildList {
            add(translate("Exact nonzero WPS UUID-E match"))
            if (!conflicts && own != null && others.all { d -> identities(d).any { it.manufacturer.equals(own.manufacturer, true) &&
                    it.model.equals(own.model, true) } }) add(translate("Manufacturer and model agree"))
            val ownSerial = serials(device).distinct().singleOrNull()
            if (!conflicts && ownSerial != null && others.all { serials(it).distinct().singleOrNull() == ownSerial })
                add(translate("Advertised serial numbers agree"))
            if (others.all { it.firstSeen <= device.lastSeen && device.firstSeen <= it.lastSeen })
                add(translate("Observation periods overlap"))
        }
        return listOf(
            AdvPayloadDecoder.Field(translate("Possible shared Wi-Fi device"), others.joinToString("\n") {
                MacUtil.redactMacIn("${it.mac} · ${it.name}", it.mac, demoMode)
            }),
            AdvPayloadDecoder.Field("WPS UUID-E", if (demoMode) translate("Hidden in privacy mode") else id),
            AdvPayloadDecoder.Field(translate("WPS association evidence"), evidence.joinToString("; ")),
            AdvPayloadDecoder.Field(translate("WPS association limits"), translate(if (conflicts)
                "Shared UUID with conflicting identity; relationship unconfirmed. Records remain separate."
                else "May be multiple interfaces of one device. UUIDs can be copied; records remain separate.")),
        )
    }
}
