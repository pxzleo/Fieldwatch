package app.fieldwatch.domain

/**
 * Decode well-known BLE advertisement payloads: Apple Continuity / iBeacon,
 * Google Fast Pair, Eddystone, Microsoft CDP. After company ID / UUID the rest
 * is proprietary; only published or well-reverse-engineered layouts are named.
 */
object AdvPayloadDecoder {
    data class Field(val label: String, val value: String)

    data class RoleHint(
        val bucket: String,
        val label: String,
        val reason: String,
        val weight: Int,
    )

    fun decodeDevice(device: Sighting, translate: (String) -> String = { it }): List<Field> {
        val current = decodeCurrentDevice(device, translate)
        if (device.kind != RadioKind.BLE || device.facts.bleHistory.isEmpty()) return current
        val historicalLabels = device.facts.bleHistory.flatMap { it.labels }.map(translate).toSet()
        return current.filterNot { it.label in historicalLabels } + BlePayloadHistory.fields(device, translate)
    }

    internal fun decodeCurrentDevice(device: Sighting, translate: (String) -> String = { it }): List<Field> {
        if (device.kind == RadioKind.WIFI) return (device.facts.vendorIes.flatMap {
            WifiWpsDecoder.decode(it, translate)?.fields.orEmpty().filterNot { field ->
                device.facts.wpsIdentity != null && WifiWpsDecoder.identityLabels.any { field.label == translate(it) }
            } + WifiWmmDecoder.decode(it, translate)
        } + WifiWpsDecoder.historicalFields(device.facts, translate)).distinct()
        val mfg = device.facts.mfgRecords.ifEmpty {
            device.manufacturerId?.let { listOf(MfgRecord(it, device.manufacturerDataHex)) }.orEmpty()
        }
        val identity = appleDeviceHint(device, translate)?.let { listOf(Field(translate("Apple device type"), it.label)) }.orEmpty() +
            BleServiceOwnership.fields(device, translate)
        val decoded = identity + device.facts.serviceData.flatMap { decodeService(it, device, translate) + BleServiceInspection.fields(it, translate) } +
            mfg.flatMap { decodeManufacturer(it, translate) + MideaAdvertisementDecoder.decodeAddress(it, device.mac, translate) } + decodeMesh(device.rawHex, translate)
        if (decoded.isNotEmpty()) return decoded.distinct()
        val status = if (device.rawHex.isBlank() && mfg.none { it.dataHex.isNotBlank() } && device.facts.serviceData.none { it.dataHex.isNotBlank() })
            "No advertisement payload captured" else "Payload parser unsupported"
        return listOf(Field(translate("Payload decode status"), translate(status)))
    }

    /** Validates the entire AD chain before accepting secure network beacons (Mesh 3.10.3). */
    internal fun meshSecureBeacons(rawHex: String): List<ByteArray> {
        val bytes = strictHexBytes(rawHex) ?: return emptyList()
        val beacons = mutableListOf<ByteArray>()
        var offset = 0
        while (offset < bytes.size) {
            val length = bytes[offset].toInt() and 0xFF
            if (length == 0) return if (bytes.drop(offset).all { it == 0.toByte() }) beacons else emptyList()
            if (offset + length >= bytes.size) return emptyList()
            if ((bytes[offset + 1].toInt() and 0xFF) == 0x2B) {
                val payload = bytes.copyOfRange(offset + 2, offset + length + 1)
                if (payload.size == 22 && payload[0] == 1.toByte() && (payload[1].toInt() and 0xFF) in 0..3)
                    beacons += payload
            }
            offset += length + 1
        }
        return beacons
    }

    internal fun decodeMesh(rawHex: String, translate: (String) -> String): List<Field> =
        meshSecureBeacons(rawHex).flatMap { payload ->
            val iv = payload.sliceArray(10..13).fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 0xFF) }
            listOf(
                Field(translate("Bluetooth Mesh secure network beacon"), translate("Protocol only; product and authentication are unverified.")),
                Field(translate("Mesh flags (raw)"), "%02X".format(payload[1].toInt() and 0xFF)),
                Field(translate("Mesh key refresh (advertised, unverified)"), translate(
                    if ((payload[1].toInt() and 1) != 0) "Key refresh phase 2" else "Not in key refresh phase 2")),
                Field(translate("Mesh IV update (advertised, unverified)"), translate(
                    if ((payload[1].toInt() and 2) != 0) "IV update in progress" else "Normal IV operation")),
                Field(translate("Mesh Network ID"), payload.sliceArray(2..9).toHexUpper()),
                Field(translate("Mesh IV Index"), iv.toString()),
                Field(translate("Mesh authentication (raw, unverified)"), payload.sliceArray(14..21).toHexUpper()),
            )
        }

    fun decodeManufacturer(record: MfgRecord, translate: (String) -> String = { it }): List<Field> {
        if (record.companyId == 0x06A8) return MideaAdvertisementDecoder.decode(record, translate)
        if (record.companyId == 0x038F) return MiBeaconDecoder.decode(record.dataHex, translate).fields
        val bytes = hexToBytes(record.dataHex) ?: return emptyList()
        return when (record.companyId) {
            0x004C -> decodeApple(bytes, translate)
            0x0006 -> decodeMicrosoft(bytes, translate)
            0x0157 -> decodeAltBeacon(bytes, translate)
            0x00E0 -> listOf(Field(translate("Google manufacturer data"), translate("%1\$s bytes").format(bytes.size)))
            else -> emptyList()
        }
    }

    fun decodeService(record: ServiceDataRecord, translate: (String) -> String = { it }): List<Field> {
        return decodeService(record, null, translate)
    }

    fun decodeService(record: ServiceDataRecord, device: Sighting?, translate: (String) -> String = { it }): List<Field> {
        val short = uuid16(record.uuid) ?: return emptyList()
        if (short == 0xFE95) return MiBeaconDecoder.decode(record.dataHex, translate).fields
        if (short in listOf(0x181D, 0x181B) && MiScaleDecoder.applies(device)) return MiScaleDecoder.decode(short, record.dataHex, translate)
        val bytes = hexToBytes(record.dataHex) ?: return emptyList()
        return when (short) {
            0xFE2C -> decodeFastPair(bytes, translate)
            0xFEAA -> decodeEddystone(bytes, translate)
            else -> emptyList()
        }
    }

    fun roleHints(device: Sighting, translate: (String) -> String = { it }): List<RoleHint> {
        val out = ArrayList<RoleHint>(4)
        val appleIdentity = appleDeviceHint(device, translate)
        appleIdentity?.takeIf { it.weight >= 9 }?.let { out += it }
        val mfg = device.facts.mfgRecords.ifEmpty {
            device.manufacturerId?.let { listOf(MfgRecord(it, device.manufacturerDataHex)) } ?: emptyList()
        }
        MiBeaconDecoder.identities(device).mapNotNull { decoded ->
            MiBeaconDecoder.product(decoded.productId)?.let { decoded.productId to it }
        }.distinct().forEach { (pid, product) ->
            out += RoleHint(if (product.type == "door lock") "lock" else "sensor",
                "${product.model} · ${translate(product.type)}",
                translate("MiBeacon product ID %1\$s identifies %2\$s; encrypted objects do not expose readings.")
                    .format("0x%04X".format(pid), product.model), 8)
        }
        for (rec in mfg) {
            if (rec.companyId != 0x004C) continue
            val bytes = hexToBytes(rec.dataHex) ?: continue
            for (tlv in appleTlvs(bytes)) {
                when (tlv.type) {
                    0x02 -> if (tlv.data.size >= 20) {
                        val hex = tlv.data.toHexUpper()
                        val teslaPrefix = DefaultCatalog.TESLA_IBEACON_MFG_PREFIX
                        val targetPrefix = DefaultCatalog.TARGET_ATRIUS_IBEACON_MFG_PREFIX
                        if (hex.startsWith(teslaPrefix) || hex.startsWith(teslaPrefix.drop(4))) {
                            out += RoleHint(
                                "vehicle",
                                translate("a Tesla vehicle or phone-as-key"),
                                translate("Tesla phone-key iBeacon UUID (iOS background find)."),
                                8,
                            )
                        } else if (hex.startsWith(targetPrefix) || hex.startsWith(targetPrefix.drop(4))) {
                            out += RoleHint(
                                "beacon",
                                "a Target Atrius basket tag",
                                translate("Target / Atrius iBeacon UUID (shopping-basket asset tag)."),
                                8,
                            )
                        } else {
                            out += RoleHint("beacon", "an iBeacon", translate("Apple iBeacon payload."), 7)
                        }
                    }
                    0x05 -> out += RoleHint("apple-unknown", translate("Apple device (type unconfirmed)"), translate("Apple AirDrop advertisement."), 5)
                    0x07 -> {
                        val model = appleIdentity?.takeIf { it.weight >= 9 && it.bucket == "audio-personal" }?.label
                        out += RoleHint(
                            "audio-personal",
                            model ?: translate("AirPods or Beats headphones"),
                            if (model != null) translate("Apple Proximity Pairing: %1\$s.").format(model)
                            else translate("Apple Proximity Pairing (AirPods / Beats)."),
                            8,
                        )
                    }
                    0x08 -> out += RoleHint("apple-unknown", translate("Apple device (type unconfirmed)"), translate("Hey Siri advertisement."), 6)
                    0x09 -> if (validAirPlayTarget(tlv.data)) out += RoleHint("audio-speaker", translate("an AirPlay target"), translate("AirPlay target advertisement; product model is unconfirmed."), 5)
                    0x0B -> out += RoleHint("apple-unknown", translate("an Apple device doing Handoff"), translate("Handoff advertisement."), 4)
                    0x0C -> out += RoleHint("apple-unknown", translate("an Apple device looking for Instant Hotspot"), translate("Tethering-target advertisement."), 5)
                    0x0D, 0x0E -> out += RoleHint("hotspot", translate("an iPhone/iPad offering Instant Hotspot"), translate("Tethering-source advertisement."), 6)
                    0x0F -> out += RoleHint("apple-unknown", translate("an Apple device (Nearby Action)"), nearbyActionReason(tlv.data, translate), 4)
                    0x10 -> out += RoleHint("apple-unknown", translate("Apple device (type unconfirmed)"), nearbyInfoReason(tlv.data, translate), 5)
                    0x12 -> out += RoleHint(
                        "tag",
                        translate("a Find My network radio"),
                        translate("Apple Offline Finding — AirTag, Find My accessory, or an Apple device locating itself."),
                        4,
                    )
                }
            }
        }
        for (sd in device.facts.serviceData) {
            when (uuid16(sd.uuid)) {
                0xFE2C -> {
                    val bytes = hexToBytes(sd.dataHex) ?: continue
                    if (bytes.size == 3) {
                        val id = modelId24(bytes)
                        val name = FastPairModels.name(id)
                        out += RoleHint(
                            "audio-personal",
                            name ?: translate("a Fast Pair accessory (often earbuds or a speaker)"),
                            if (name != null) translate("Google Fast Pair model %1\$s (0x%2\$06X), in pairing mode.").format(name, id)
                            else translate("Google Fast Pair model 0x%06X, in pairing mode.").format(id),
                            if (name != null) 8 else 6,
                        )
                    } else {
                        out += RoleHint(
                            "audio-personal",
                            translate("a Fast Pair accessory already paired to someone"),
                            translate("Google Fast Pair account-key broadcast (not in pairing mode)."),
                            4,
                        )
                    }
                }
                0xFEAA -> {
                    val frame = hexToBytes(sd.dataHex)?.firstOrNull()?.toInt()?.and(0xFF)
                    when (frame) {
                        0x40, 0x41 -> out += RoleHint(
                            "tag",
                            "a Google Find Hub tag",
                            if (frame == 0x41) translate("Find Hub separated (unwanted-tracking) frame.")
                            else translate("Find Hub nearby frame."),
                            8,
                        )
                        else -> out += RoleHint("beacon", translate("an Eddystone beacon"), translate("Eddystone service data."), 6)
                    }
                }
            }
        }
        return out
    }

    private data class Tlv(val type: Int, val data: ByteArray)

    /** Product/type evidence only; activity or connected-accessory flags are not model IDs. */
    fun appleDeviceHint(device: Sighting, translate: (String) -> String = { it }): RoleHint? {
        if (device.kind != RadioKind.BLE) return null
        val records = device.facts.mfgRecords.ifEmpty {
            device.manufacturerId?.let { listOf(MfgRecord(it, device.manufacturerDataHex)) } ?: emptyList()
        }
        return records.filter { it.companyId == 0x004C }.flatMap { record ->
            val bytes = hexToBytes(record.dataHex) ?: return@flatMap emptyList()
            val tlvs = appleTlvs(bytes)
            if (tlvs.sumOf { it.data.size + 2 } != bytes.size) return@flatMap emptyList()
            tlvs.mapNotNull { appleDeviceHint(it, translate) }
        }.maxByOrNull { it.weight }
    }

    private fun appleDeviceHint(tlv: Tlv, translate: (String) -> String): RoleHint? {
        val data = tlv.data
        val model = if (tlv.type == 0x07 && data.size == 25 && data[0] == 1.toByte()) airPodsModel(data) else null
        if (model != null) return RoleHint("audio-personal", translate(model),
            translate("Apple Proximity Pairing: %1\$s.").format(translate(model)), 9)
        if (tlv.type == 0x08 && data.size == 7) {
            siriDeviceClass(data)?.let { label ->
                val bucket = when (label) { "Apple Watch" -> "watch"; "Mac" -> "computer"; "HomePod" -> "audio-speaker"; else -> "phone" }
                return RoleHint(bucket, label, translate("Hey Siri device class identifies %1\$s; the exact model is unconfirmed.").format(label), 9)
            }
        }
        if (tlv.type == 0x10 && data.size >= 5 && data[0].toInt() and 0x0F == 0x0A) {
            return RoleHint("watch", "Apple Watch", translate("Nearby Info reports a watch worn and unlocked; the exact model is unconfirmed."), 9)
        }
        return when (tlv.type) {
            0x07 -> RoleHint("audio-personal", translate("Apple audio (model unconfirmed)"), translate("Apple Proximity Pairing (AirPods / Beats)."), 4)
            0x12 -> RoleHint("tag", translate("Find My device (type unconfirmed)"), translate("Apple Offline Finding — AirTag, Find My accessory, or an Apple device locating itself."), 4)
            0x09 -> if (validAirPlayTarget(data)) RoleHint("audio-speaker", translate("AirPlay receiver (type unconfirmed)"), translate("AirPlay target advertisement; product model is unconfirmed."), 4) else null
            0x16 -> if (data.size == 8) RoleHint("other", translate("AWDL device (type unconfirmed)"), translate("Apple device type cannot be determined from this Continuity frame."), 4) else null
            0x05, 0x08, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10 -> RoleHint("phone", translate("Apple device (type unconfirmed)"), translate("Apple device type cannot be determined from this Continuity frame."), 4)
            else -> null // iBeacon/HomeKit use Apple's format without proving Apple hardware.
        }
    }

    private fun siriDeviceClass(data: ByteArray): String? = if (data.size != 7) null else when (u16be(data, 4)) {
        0x0002 -> "iPhone"
        0x0003 -> "iPad"
        0x0007 -> "HomePod"
        0x0009 -> "Mac"
        0x000A -> "Apple Watch"
        else -> null
    }

    private fun decodeApple(bytes: ByteArray, translate: (String) -> String): List<Field> {
        val tlvs = appleTlvs(bytes)
        if (tlvs.isEmpty() || tlvs.sumOf { it.data.size + 2 } != bytes.size) {
            return listOf(Field(translate("Apple payload"), translate("%1\$s bytes (unparsed)").format(bytes.size)))
        }
        val out = ArrayList<Field>(8)
        for (tlv in tlvs) {
            out += Field(translate("Apple Continuity type"), "0x%02X · %s".format(tlv.type, appleTypeName(tlv.type, translate)))
            out += when (tlv.type) {
                0x02 -> decodeIBeacon(tlv.data, translate)
                0x05 -> decodeAirDrop(tlv.data, translate)
                0x06 -> listOf(Field("HomeKit", translate("%1\$s bytes of HomeKit setup data").format(tlv.data.size)))
                0x07 -> decodeAirPods(tlv.data, translate)
                0x08 -> decodeHeySiri(tlv.data, translate)
                0x09 -> decodeAirPlayTarget(tlv.data, translate)
                0x0A -> listOf(Field("Magic Switch", translate("Apple Watch wrist / unlock related.")))
                0x0B -> decodeHandoff(tlv.data, translate)
                0x0C -> decodeHandoffOrTetherTarget(tlv.data, translate)
                0x0D, 0x0E -> decodeTetherSource(tlv.data, translate)
                0x0F -> decodeNearbyAction(tlv.data, translate)
                0x10 -> decodeNearbyInfo(tlv.data, translate)
                0x12 -> decodeFindMy(tlv.data, translate)
                0x16 -> decodeAwdl(tlv.data, translate)
                else -> listOf(Field(translate("Payload"), translate("%1\$s bytes").format(tlv.data.size)))
            }
        }
        return out
    }

    private fun appleTlvs(bytes: ByteArray): List<Tlv> {
        val out = ArrayList<Tlv>(3)
        var i = 0
        while (i + 2 <= bytes.size) {
            val type = bytes[i].toInt() and 0xFF
            val len = bytes[i + 1].toInt() and 0xFF
            if (len <= 0 || i + 2 + len > bytes.size) break
            out += Tlv(type, bytes.copyOfRange(i + 2, i + 2 + len))
            i += 2 + len
        }
        return out
    }

    /** Match only a complete manufacturer record, never a prefix or a truncated TLV chain. */
    fun hasAppleContinuityType(hex: String, type: Int): Boolean {
        val bytes = hexToBytes(hex) ?: return false
        val tlvs = appleTlvs(bytes)
        if (tlvs.sumOf { it.data.size + 2 } != bytes.size) return false
        return tlvs.any { it.type == type && when (type) {
            0x09 -> validAirPlayTarget(it.data)
            0x16 -> it.data.size == 8
            else -> false
        } }
    }

    private fun validAirPlayTarget(data: ByteArray): Boolean = data.isNotEmpty() &&
        data.size == if ((data[0].toInt() and 0x10) != 0) 8 else 6

    private fun decodeAirPlayTarget(data: ByteArray, translate: (String) -> String): List<Field> {
        if (!validAirPlayTarget(data)) return listOf(Field(translate("AirPlay target"), translate("Unsupported or malformed AirPlay target length")))
        val explicitPort = (data[0].toInt() and 0x10) != 0
        return listOf(
            Field(translate("AirPlay target"), translate("Advertised network endpoint; not location information.")),
            Field(translate("AirPlay flags (raw)"), "0x%02X".format(data[0].toInt() and 255)),
            Field(translate("AirPlay seed (raw)"), "0x%02X".format(data[1].toInt() and 255)),
            Field(translate("AirPlay advertised IPv4"), data.slice(2..5).joinToString(".") { (it.toInt() and 255).toString() }),
            Field(translate(if (explicitPort) "AirPlay advertised port" else "AirPlay default port (not advertised)"),
                if (explicitPort) u16be(data, 6).toString() else "7000"),
        )
    }

    private fun decodeAwdl(data: ByteArray, translate: (String) -> String): List<Field> {
        if (data.size != 8) return listOf(Field(translate("AWDL connection message"), translate("Unsupported or malformed AWDL message length")))
        return listOf(
            Field(translate("AWDL connection message"), translate("Undocumented connection data; battery and activity are not decoded.")),
            Field(translate("AWDL flags (raw)"), "0x%02X".format(data[0].toInt() and 255)),
            Field(translate("AWDL message (raw)"), data.copyOfRange(1, 8).toHexUpper()),
        )
    }

    private fun appleTypeName(type: Int, translate: (String) -> String): String = when (type) {
        0x02 -> "iBeacon"
        0x03 -> "AirPrint"
        0x05 -> "AirDrop"
        0x06 -> "HomeKit"
        0x07 -> translate("Proximity Pairing (AirPods / Beats)")
        0x08 -> "Hey Siri"
        0x09 -> translate("AirPlay target")
        0x0A -> translate("Magic Switch (Watch)")
        0x0B -> "Handoff"
        0x0C -> translate("Handoff or Instant Hotspot (target)")
        0x0D -> translate("Instant Hotspot (source)")
        0x0E -> translate("Instant Hotspot (source)")
        0x0F -> "Nearby Action"
        0x10 -> "Nearby Info"
        0x12 -> translate("Find My / Offline Finding")
        0x13 -> translate("Nearby Action (extended)")
        0x16 -> translate("AWDL connection message")
        else -> translate("unlisted")
    }

    private fun decodeIBeacon(data: ByteArray, translate: (String) -> String): List<Field> {
        // TLV payload is length-byte already consumed; data is 0x15 + 21 bytes OR 21 bytes.
        val body = when {
            data.size >= 22 && data[0] == 0x15.toByte() -> data.copyOfRange(1, 22)
            data.size >= 21 -> data.copyOfRange(0, 21)
            else -> return listOf(Field("iBeacon", translate("truncated (%1\$s bytes)").format(data.size)))
        }
        val uuid = uuidFromBe(body, 0)
        val major = u16be(body, 16)
        val minor = u16be(body, 18)
        val tx = body[20].toInt()
        val teslaKey = uuid.filter { it.isLetterOrDigit() }.equals(
            DefaultCatalog.TESLA_IBEACON_MFG_PREFIX.drop(4),
            ignoreCase = true,
        )
        return listOf(
            Field(
                "iBeacon UUID",
                if (teslaKey) translate("%1\$s — Tesla phone-as-key (iOS background find). Not a mall beacon.").format(uuid) else uuid,
            ),
            Field(translate("iBeacon major / minor"), "$major / $minor"),
            Field(translate("iBeacon calibrated TX"), translate("%1\$s dBm at 1 m (used to estimate range)").format(tx)),
        )
    }

    private fun decodeAirDrop(data: ByteArray, translate: (String) -> String): List<Field> {
        // 8 zeros, version, appleID hash(2), phone(2), email(2), email2(2), 0
        if (data.size < 18) return listOf(Field("AirDrop", translate("Someone nearby is offering AirDrop (%1\$s bytes).").format(data.size)))
        return listOf(
            Field("AirDrop", translate("Someone nearby has AirDrop receiving on. Hashes are truncated IDs, not names.")),
            Field(translate("Apple ID hash (2 bytes)"), data.copyOfRange(9, 11).toHexUpper()),
        )
    }

    private fun decodeAirPods(data: ByteArray, translate: (String) -> String): List<Field> {
        // prefix 0x01, model u16be, status, batt nibble, charge+case, lid, color, 0x00, enc 16
        if (data.size != 25 || data[0] != 0x01.toByte()) return listOf(Field(translate("Apple audio (model unconfirmed)"), translate("Payload parser unsupported")))
        val start = 1
        val model = ((data[start].toInt() and 0xFF) shl 8) or (data[start + 1].toInt() and 0xFF)
        val status = data[start + 2].toInt() and 0xFF
        val batt = data[start + 3].toInt() and 0xFF
        val left = batt and 0x0F
        val right = (batt shr 4) and 0x0F
        val out = ArrayList<Field>(6)
        out += Field(translate("Product"), airPodsModelName(model)?.let(translate) ?: translate("Apple audio 0x%04X").format(model))
        out += Field(translate("Pod position"), airPodsStatus(status, translate))
        out += Field(translate("Battery (left / right)"), "${nibblePct(left, translate)} / ${nibblePct(right, translate)}")
        if (data.size > start + 4) {
            val ch = data[start + 4].toInt() and 0xFF
            val caseBatt = ch and 0x0F
            val charging = buildList {
                if (ch and 0x10 != 0) add(translate("case"))
                if (ch and 0x20 != 0) add(translate("right"))
                if (ch and 0x40 != 0) add(translate("left"))
            }
            out += Field(translate("Case battery"), nibblePct(caseBatt, translate))
            if (charging.isNotEmpty()) out += Field(translate("Charging"), charging.joinToString(", "))
        }
        if (data.size > start + 6) {
            out += Field(translate("Color"), airPodsColor(data[start + 6].toInt() and 0xFF, translate))
        }
        return out
    }

    private fun airPodsModel(data: ByteArray): String? {
        if (data.size < 4) return null
        val start = if (data[0] == 0x01.toByte()) 1 else 0
        if (data.size < start + 2) return null
        val model = ((data[start].toInt() and 0xFF) shl 8) or (data[start + 1].toInt() and 0xFF)
        return airPodsModelName(model)
    }

    private fun airPodsModelName(id: Int): String? = when (id) {
        0x0220 -> "AirPods (1st generation)"
        0x0F20 -> "AirPods (2nd generation)"
        0x1320 -> "AirPods (3rd generation)"
        0x1920 -> "AirPods (4th generation)"
        0x0E20 -> "AirPods Pro"
        0x1420 -> "AirPods Pro (2nd generation)"
        0x2420 -> "AirPods Pro 2 (USB-C)"
        0x1F20 -> "AirPods Max (USB-C)"
        0x0A20 -> "AirPods Max (Lightning)"
        0x0320 -> "Powerbeats 3"
        0x0620 -> "Beats Solo3"
        0x0B20 -> "Powerbeats Pro"
        0x0C20 -> "Beats Solo Pro"
        0x0D20 -> "Powerbeats 4"
        0x1020 -> "Beats Flex"
        0x1120 -> "Beats Studio Buds"
        0x1220 -> "Beats Fit Pro"
        0x1720 -> "Beats Studio Pro"
        0x1B20 -> "AirPods 4 (ANC)"
        0x0520 -> "BeatsX"
        0x0920 -> "Beats Studio³ Wireless"
        0x1620 -> "Beats Studio Buds +"
        0x2520 -> "Beats Solo 4"
        0x2620 -> "Beats Solo Buds"
        0x2D20 -> "AirPods Max 2"
        0x3820 -> "Beats 360"
        else -> null
    }

    private fun airPodsStatus(status: Int, translate: (String) -> String): String = when (status) {
        0x01 -> translate("One or both out of the case")
        0x02 -> translate("Case open")
        0x03 -> translate("Taken out / in-ear transition")
        0x05 -> translate("One in ear")
        0x09 -> translate("Both out, not in ear")
        0x0B -> translate("In-ear activity")
        0x11, 0x13 -> translate("Both in ear")
        0x21 -> translate("One in ear (sharing?)")
        0x51 -> translate("Both in case, lid open")
        0x55 -> translate("Both in case, lid closed")
        0x75 -> translate("In case")
        else -> translate("Status 0x%02X").format(status)
    }

    private fun airPodsColor(v: Int, translate: (String) -> String): String = when (v) {
        0x00 -> translate("White")
        0x01 -> translate("Black")
        0x02 -> translate("Red")
        0x03 -> translate("Blue")
        0x04 -> translate("Pink")
        0x05 -> translate("Gray")
        0x06 -> translate("Silver")
        0x07 -> translate("Gold")
        0x08 -> translate("Rose gold")
        0x09 -> translate("Space gray")
        0x0A -> translate("Dark blue")
        0x0B -> translate("Light blue")
        0x0C -> translate("Yellow")
        else -> "0x%02X".format(v)
    }

    private fun nibblePct(n: Int, translate: (String) -> String): String = when (n) {
        in 0..9 -> "${n * 10}%"
        10, 11, 12, 13, 14 -> "100%"
        15 -> translate("unknown / not present")
        else -> "$n"
    }

    private fun decodeHeySiri(data: ByteArray, translate: (String) -> String): List<Field> {
        if (data.size != 7) return listOf(Field("Hey Siri", translate("Siri was just triggered on a nearby Apple device.")))
        val klass = u16be(data, 4)
        val device = siriDeviceClass(data) ?: translate("class 0x%04X").format(klass)
        return listOf(
            Field("Hey Siri", translate("A %1\$s just heard a Siri trigger. The packet carries a short voice hash, not the words.").format(device)),
        )
    }

    private fun decodeHandoff(data: ByteArray, translate: (String) -> String): List<Field> =
        listOf(Field("Handoff", translate("Continuity Handoff: a task can be continued on another Apple device. Payload is encrypted.")))

    private fun decodeHandoffOrTetherTarget(data: ByteArray, translate: (String) -> String): List<Field> =
        if (data.size >= 14) decodeHandoff(data, translate)
        else listOf(Field(translate("Instant Hotspot (looking)"), translate("This Apple device is searching for a paired phone’s hotspot.")))

    private fun decodeTetherSource(data: ByteArray, translate: (String) -> String): List<Field> {
        if (data.size < 6) return listOf(Field("Instant Hotspot", translate("An iPhone/iPad is offering a personal hotspot.")))
        val batt = data[2].toInt() and 0xFF
        val cell = if (data.size >= 5) u16be(data, 3) else -1
        val bars = if (data.size >= 6) data[5].toInt() and 0xFF else -1
        val cellName = when (cell) {
            0, 6 -> "4G"
            1 -> "1xRTT"
            2 -> "GPRS"
            3 -> "EDGE"
            4, 5 -> "3G"
            7 -> "LTE"
            8 -> "5G"
            else -> if (cell >= 0) translate("type %1\$s").format(cell) else null
        }
        return listOf(
            Field(
                translate("Instant Hotspot (offering)"),
                buildString {
                    append(translate("Paired iPhone/iPad hotspot"))
                    if (batt in 0..100) append(translate(" · phone battery %1\$s%%").format(batt))
                    cellName?.let { append(" · $it") }
                    if (bars in 0..5) append(translate(" · %1\$s/5 bars").format(bars))
                },
            ),
        )
    }

    private fun decodeNearbyAction(data: ByteArray, translate: (String) -> String): List<Field> {
        if (data.isEmpty()) return listOf(Field("Nearby Action", "Apple Nearby Action"))
        val action = if (data.size >= 2) data[1].toInt() and 0xFF else data[0].toInt() and 0xFF
        val name = nearbyActionName(action, translate)
        return listOf(Field("Nearby Action", name))
    }

    private fun nearbyActionReason(data: ByteArray, translate: (String) -> String): String {
        val action = if (data.size >= 2) data[1].toInt() and 0xFF else return translate("Nearby Action advertisement.")
        return translate("Nearby Action: %1\$s.").format(nearbyActionName(action, translate))
    }

    private fun nearbyActionName(action: Int, translate: (String) -> String): String = when (action) {
        0x01 -> translate("Apple TV setup")
        0x04 -> translate("Mobile backup")
        0x05 -> translate("Watch setup")
        0x06 -> translate("Apple TV pair")
        0x08 -> translate("Wi-Fi password sharing (prompting nearby iPhones)")
        0x09 -> translate("iOS setup")
        0x0A -> translate("Repair")
        0x0B -> translate("Speaker setup")
        0x0C -> "Apple Pay"
        0x0D -> translate("Whole-home audio setup")
        0x0F -> translate("Answered a call")
        0x10 -> translate("Ended a call")
        0x13 -> translate("Remote AutoFill")
        0x14 -> translate("Companion Link proximity")
        0x17 -> translate("Remote display")
        else -> translate("action 0x%02X").format(action)
    }

    private fun decodeNearbyInfo(data: ByteArray, translate: (String) -> String): List<Field> {
        if (data.isEmpty()) return listOf(Field("Nearby Info", translate("Apple device usage state.")))
        val status = data[0].toInt() and 0xFF
        val action = status and 0x0F
        val flagsHi = (status shr 4) and 0x0F
        val dataFlags = if (data.size > 1) data[1].toInt() and 0xFF else 0
        val activity = when (action) {
            0x00 -> translate("activity unknown")
            0x01 -> translate("activity reporting off")
            0x03 -> translate("idle (screen locked)")
            0x05 -> translate("audio playing, screen locked")
            0x07 -> translate("active (screen on)")
            0x09 -> translate("screen on, video playing")
            0x0A -> translate("Watch on wrist and unlocked")
            0x0B -> translate("recent interaction")
            0x0D -> translate("user is driving")
            0x0E -> translate("phone or FaceTime call")
            else -> translate("activity 0x%X").format(action)
        }
        val extras = buildList {
            if (flagsHi and 0x1 != 0) add(translate("primary iCloud device"))
            if (flagsHi and 0x4 != 0) add(translate("AirDrop receiving on"))
            if (dataFlags and 0x04 != 0) add(translate("Wi-Fi on"))
            if (dataFlags and 0x01 != 0) add(translate("AirPods connected"))
            if (dataFlags and 0x20 != 0) add(translate("Watch locked"))
        }
        return listOf(
            Field(
                translate("What the Apple device is doing"),
                buildString {
                    append(activity.replaceFirstChar { it.uppercase() })
                    if (extras.isNotEmpty()) {
                        append(". ")
                        append(extras.joinToString("; "))
                    }
                    append(".")
                },
            ),
        )
    }

    private fun nearbyInfoReason(data: ByteArray, translate: (String) -> String): String {
        if (data.isEmpty()) return translate("Nearby Info advertisement.")
        val action = data[0].toInt() and 0x0F
        return when (action) {
            0x03 -> translate("Phone is idle / locked.")
            0x05 -> translate("Audio playing with the screen locked.")
            0x07 -> translate("Screen is on — someone is using it.")
            0x0D -> translate("Device reports the user is driving.")
            0x0E -> translate("In a phone or FaceTime call.")
            else -> translate("Nearby Info advertisement.")
        }
    }

    private fun decodeFindMy(data: ByteArray, translate: (String) -> String): List<Field> {
        if (data.isEmpty()) return listOf(Field("Find My", translate("Offline Finding advertisement.")))
        val status = data[0].toInt() and 0xFF
        val maintained = status and 0x04 != 0
        val batt = (status shr 6) and 0x3
        val battName = when (batt) {
            0 -> translate("full")
            1 -> translate("medium")
            2 -> translate("low")
            else -> translate("critical")
        }
        val keyLen = (data.size - 1).coerceAtLeast(0)
        return listOf(
            Field(
                translate("Find My / Offline Finding"),
                buildString {
                    append(translate("Broadcasting a public key so the Find My network can report a location. "))
                    append(translate("Used by AirTags, Find My accessories, and Apple devices locating themselves. "))
                    if (maintained) append(translate("Owner seen recently. "))
                    else append(translate("Owner not seen in the current key window. "))
                    if (maintained || batt in 0..3) append(translate("Battery %1\$s. ").format(battName))
                    append(translate("(%1\$s-byte key fragment — not a serial number.)").format(keyLen))
                },
            ),
        )
    }

    private fun decodeFastPair(bytes: ByteArray, translate: (String) -> String): List<Field> {
        if (bytes.size == 3) {
            val id = modelId24(bytes)
            val name = FastPairModels.name(id)
            return listOf(
                Field("Google Fast Pair", translate("In pairing mode — Android will pop a tap-to-pair card.")),
                Field(
                    translate("Model ID"),
                    if (name != null) "$name  (0x%06X)".format(id) else translate("0x%06X (not in the local name list)").format(id),
                ),
            )
        }
        if (bytes.isEmpty()) return emptyList()
        val verFlags = bytes[0].toInt() and 0xFF
        val version = (verFlags shr 4) and 0x0F
        val ui = if (bytes.size > 1) {
            val lt = bytes[1].toInt() and 0xFF
            val type = lt and 0x0F
            when (type) {
                0x0 -> translate("wants to show a pairing card")
                0x2 -> translate("hiding the pairing card (e.g. buds back in the case)")
                else -> translate("filter type %1\$s").format(type)
            }
        } else translate("account-key bloom filter")
        return listOf(
            Field(
                "Google Fast Pair",
                translate("Already paired to an account (not in pairing mode). %1\$s. Version %2\$s.").format(ui, version),
            ),
        )
    }

    private fun decodeEddystone(bytes: ByteArray, translate: (String) -> String): List<Field> {
        if (bytes.isEmpty()) return emptyList()
        return when (bytes[0].toInt() and 0xFF) {
            0x00 -> {
                if (bytes.size < 18) listOf(Field("Eddystone-UID", translate("truncated")))
                else listOf(
                    Field(translate("Eddystone-UID namespace"), bytes.copyOfRange(2, 12).toHexUpper()),
                    Field(translate("Eddystone-UID instance"), bytes.copyOfRange(12, 18).toHexUpper()),
                )
            }
            0x10 -> listOf(Field("Eddystone-URL", eddystoneUrl(bytes) ?: translate("%1\$s bytes").format(bytes.size)))
            0x20 -> decodeEddystoneTlm(bytes, translate)
            0x30 -> listOf(Field("Eddystone-EID", translate("ephemeral ID (rotating)")))
            0x40, 0x41 -> {
                if (bytes.size !in listOf(21, 22, 33, 34)) return listOf(Field(translate("Find Hub parse status"),
                    translate("Malformed Find Hub payload length")))
                val mode = if (bytes[0].toInt() and 0xFF == 0x41) translate("separated (unwanted-tracking mode)") else translate("nearby / with owner")
                val eidLen = if (bytes.size >= 33) 32 else 20
                val eid = if (eidLen > 0) bytes.copyOfRange(1, 1 + eidLen).toHexUpper() else ""
                listOf(
                    Field("Find Hub", mode),
                    Field("Find Hub EID", eid.ifBlank { translate("%1\$s bytes").format(bytes.size) }),
                )
            }
            else -> listOf(Field("Eddystone", translate("frame 0x%02X").format(bytes[0])))
        }
    }

    private fun decodeEddystoneTlm(bytes: ByteArray, translate: (String) -> String): List<Field> {
        if (bytes.size < 2 || bytes[1] != 0.toByte()) return listOf(
            Field("Eddystone-TLM", translate("Encrypted or unsupported telemetry version; readings unavailable")))
        if (bytes.size != 14) return listOf(
            Field("Eddystone-TLM", translate("Malformed telemetry length; readings unavailable")))
        val voltage = u16be(bytes, 2)
        val temperature = u16be(bytes, 4)
        fun counter(offset: Int) = bytes.sliceArray(offset until offset + 4)
            .fold(0L) { value, byte -> (value shl 8) or (byte.toLong() and 255) }
        return listOf(
            Field(translate("Beacon battery voltage"), if (voltage == 0) translate("Not supported") else "$voltage mV"),
            Field(translate("Beacon temperature"), if (temperature == 0x8000) translate("Not supported")
                else java.lang.String.format(java.util.Locale.ROOT, "%.2f °C", temperature.toShort().toDouble() / 256)),
            Field(translate("Beacon transmitted advertisement count"), counter(6).toString()),
            Field(translate("Beacon time since power-on or reboot"),
                java.lang.String.format(java.util.Locale.ROOT, "%.1f s", counter(10) / 10.0)),
        )
    }

    private fun eddystoneUrl(bytes: ByteArray): String? {
        if (bytes.size < 3) return null
        val scheme = when (bytes[2].toInt() and 0xFF) {
            0 -> "http://www."
            1 -> "https://www."
            2 -> "http://"
            3 -> "https://"
            else -> return null
        }
        val expansions = arrayOf(
            ".com/", ".org/", ".edu/", ".net/", ".info/", ".biz/", ".gov/",
            ".com", ".org", ".edu", ".net", ".info", ".biz", ".gov",
        )
        val sb = StringBuilder(scheme)
        for (i in 3 until bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b < expansions.size) sb.append(expansions[b]) else if (b in 0x20..0x7E) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun decodeMicrosoft(bytes: ByteArray, translate: (String) -> String): List<Field> {
        if (bytes.isEmpty()) return emptyList()
        if (bytes[0] == 0x01.toByte() && bytes.size >= 2) {
            val type = bytes[1].toInt() and 0x1F
            val kind = when (type) {
                1 -> "Xbox"
                6 -> "iPhone"
                7 -> "iPad"
                8 -> "Android"
                9 -> translate("Windows desktop")
                11 -> translate("Windows phone")
                12 -> "Linux"
                13 -> "Windows IoT"
                14 -> "Surface Hub"
                15 -> translate("Windows laptop")
                16 -> translate("Windows tablet")
                else -> translate("type %1\$s").format(type)
            }
            return listOf(Field(translate("Microsoft Nearby Sharing / Swift Pair"), translate("A %1\$s is advertising for quick pairing or sharing.").format(kind)))
        }
        return listOf(Field(translate("Microsoft manufacturer data"), translate("%1\$s bytes").format(bytes.size)))
    }

    private fun decodeAltBeacon(bytes: ByteArray, translate: (String) -> String): List<Field> {
        if (bytes.size >= 22 && bytes[0] == 0xBE.toByte() && bytes[1] == 0xAC.toByte()) {
            return listOf(
                Field("AltBeacon UUID", uuidFromBe(bytes, 2)),
                Field(translate("AltBeacon major / minor"), "${u16be(bytes, 18)} / ${u16be(bytes, 20)}"),
            )
        }
        return emptyList()
    }

    private fun modelId24(bytes: ByteArray): Int =
        ((bytes[0].toInt() and 0xFF) shl 16) or
            ((bytes[1].toInt() and 0xFF) shl 8) or
            (bytes[2].toInt() and 0xFF)

    private fun u16be(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

    private fun uuidFromBe(data: ByteArray, offset: Int): String {
        fun h(i: Int) = "%02x".format(data[offset + i].toInt() and 0xFF)
        return "${h(0)}${h(1)}${h(2)}${h(3)}-${h(4)}${h(5)}-${h(6)}${h(7)}-${h(8)}${h(9)}-${h(10)}${h(11)}${h(12)}${h(13)}${h(14)}${h(15)}"
    }

    private fun uuid16(uuid: String): Int? {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return when {
            hex.length == 4 -> hex.toIntOrNull(16)
            hex.length == 32 && hex.startsWith("0000") && hex.endsWith("00001000800000805F9B34FB") ->
                hex.substring(4, 8).toIntOrNull(16)
            else -> null
        }
    }

    private fun hexToBytes(hex: String): ByteArray? {
        return strictHexBytes(hex)?.takeIf { it.isNotEmpty() }
    }
}
