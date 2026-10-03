package app.fieldwatch.domain

/** SIG member assignments identify a service owner, never the finished product or its type. */
object BleServiceOwnership {
    fun fields(device: Sighting, translate: (String) -> String = { it }): List<AdvPayloadDecoder.Field> {
        if (device.kind != RadioKind.BLE) return emptyList()
        val advertised = (device.serviceUuids + device.facts.serviceData.map { it.uuid })
            .flatMap { uuidAliases(it) }.toSet()
        return listOf("FDEE" to "Huawei", "FCC0" to "Xiaomi", "FD2D" to "Xiaomi").mapNotNull { (uuid, owner) ->
            if (uuidAliases(uuid).none { it in advertised }) null else AdvPayloadDecoder.Field(
                translate("BLE service assignment") + " · 0x$uuid",
                translate("%1\$s-assigned service; device type and model unconfirmed.").format(translate(owner)),
            )
        }
    }
}
