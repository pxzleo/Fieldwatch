package app.fieldwatch.domain

/**
 * Plain-language decode of advertised identity. Guesses are what the radio
 * is broadcasting, not a visual identification.
 */
object DeviceExplain {
    private val v94ByName by lazy { DefaultCatalog.discoveryFamiliesV94().associateBy { it.name.lowercase() } }
    private val v93ByName by lazy { DefaultCatalog.discoveryFamiliesV93().associateBy { it.name.lowercase() } }
    data class Guess(
        val headline: String,
        val because: String,
        val confidence: Confidence,
        val bucket: String? = null,
    )

    enum class Confidence { HIGH, MEDIUM, LOW }

    fun guess(device: Sighting, signatureNames: List<String>, translate: (String) -> String = { it }): Guess {
        val hints = ArrayList<Hint>(8)
        val appearance = device.facts.appearance?.let { RadioDb.appearance(it) }
        appearanceHint(appearance, translate)?.let { hints += it }
        CodDecoder.decodeOrNull(device.facts.deviceClass)?.let { codHint(it, translate)?.let { h -> hints += h } }
        hints += uuidHints(device.serviceUuids + device.facts.serviceData.map { it.uuid })
        hints += AdvPayloadDecoder.roleHints(device, translate).map {
            Hint(it.bucket, it.label, it.reason, it.weight)
        }
        hints += signatureHints(signatureNames, translate)
        if (device.kind == RadioKind.WIFI) hints += wifiHints(device, signatureNames, translate)

        if (hints.isEmpty()) {
            return Guess(
                headline = if (device.kind == RadioKind.WIFI) {
                    translate("Wi-Fi access point")
                } else {
                    translate("Bluetooth LE advertiser")
                },
                because = translate("It is on the air, but it did not advertise a product class ") +
                    translate("(no Appearance, Class of Device, or well-known service that names a type)."),
                confidence = Confidence.LOW,
            )
        }
        val grouped = LinkedHashMap<String, Hint>()
        for (hint in hints.sortedByDescending { it.weight }) {
            val key = hint.bucket
            val prev = grouped[key]
            if (prev == null || hint.weight > prev.weight) grouped[key] = hint
        }
        val best = grouped.values.maxBy { it.weight }
        val support = grouped.values
            .filter { it.bucket == best.bucket || it.weight >= 3 }
            .map { translate(it.reason) }
            .distinct()
        val confidence = when {
            best.weight >= 6 -> Confidence.HIGH
            best.weight >= 3 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        val hedge = when (confidence) {
            Confidence.HIGH -> "Most likely"
            Confidence.MEDIUM -> "Probably"
            Confidence.LOW -> "Could be"
        }
        return Guess(
            headline = "${translate(hedge)} ${if (best.bucket == "named" || best.bucket == "other") best.label else translate(best.label)}",
            because = support.joinToString(" ") +
                translate(" This is what the device is advertising, not a visual ID."),
            confidence = confidence,
            bucket = if (best.bucket == "apple-unknown") null else best.bucket,
        )
    }

    /**
     * Compact Live-row title from the same guess as detail. Null if we only
     * know it is an unnamed advertiser — caller may fall back to vendor.
     */
    fun listLabel(device: Sighting, signatureNames: List<String> = emptyList(), translate: (String) -> String = { it }): String? {
        AdvPayloadDecoder.appleDeviceHint(device, translate)?.takeIf { it.weight >= 9 }?.let { return it.label }
        if (device.kind == RadioKind.WIFI) {
            WifiWpsDecoder.identity(device.facts.vendorIes)?.let {
                return if (it.chipsetOnly) translate("Wi-Fi access point (brand unconfirmed)") + " · " +
                    translate("Chipset platform: %1\$s").format(it.identityLabel()) else it.identityLabel()
            }
        }
        val guess = guess(device, signatureNames, translate)
        val generic = guess.headline.contains(translate("Bluetooth LE advertiser"), ignoreCase = true) ||
            guess.headline.contains(translate("Wi-Fi access point"), ignoreCase = true)
        val core = if (generic) null else tidyHeadline(guess.headline, translate)
        val vendor = device.vendor?.trim()?.takeIf { it.isNotBlank() && it.length <= 24 }
        if (core != null) {
            return if (vendor != null && !core.contains(vendor, ignoreCase = true)) {
                "$vendor · $core"
            } else {
                core
            }
        }
        if (vendor != null) return translate("%1\$s device").format(vendor)
        return null
    }

    private fun tidyHeadline(headline: String, translate: (String) -> String = { it }): String {
        var s = headline
            .removePrefix(translate("Most likely") + " ")
            .removePrefix(translate("Probably") + " ")
            .removePrefix(translate("Could be") + " ")
            .trim()
        s = s.replace(Regex("""\s*\([^)]*\)"""), "").trim()
        s = s.removePrefix("an ").removePrefix("a ").trim()
        if (s.isEmpty()) return headline
        return s.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun flagsExplain(flags: Int, translate: (String) -> String = { it }): String = buildList {
        if (flags and 0x01 != 0) {
            add(translate("Limited-discoverable: briefly looking for a nearby connection."))
        }
        if (flags and 0x02 != 0) {
            add(translate("Discoverable: other BLE devices can find it."))
        }
        if (flags and 0x04 != 0) {
            add(translate("BLE-only: no classic Bluetooth (headsets/file-send radio)."))
        } else {
            add(translate("May also do classic Bluetooth (BR/EDR) as well as BLE."))
        }
        if (flags and 0x08 != 0 || flags and 0x10 != 0) {
            add(translate("Dual-mode chip: BLE and classic can run together."))
        }
    }.joinToString(" ")

    fun phyExplain(label: String, translate: (String) -> String = { it }): String = when {
        label.contains("Coded") -> translate("%1\$s — long-range BLE (slower, farther)").format(label)
        label.contains("2M") -> translate("%1\$s — faster BLE (Bluetooth 5)").format(label)
        label.contains("1M") -> translate("%1\$s — standard BLE radio").format(label)
        else -> label
    }

    fun addressExplain(device: Sighting, translate: (String) -> String = { it }): String {
        val type = device.facts.addressType
        return when {
            device.kind == RadioKind.WIFI && device.randomized ->
                translate("Locally administered BSSID. Vehicle, mesh, and guest APs often keep this address. Not a rotating phone MAC.")
            type.equals("Public", true) && !device.randomized ->
                translate("Public factory address (stable, IEEE-assigned).")
            type.equals("Random", true) || device.randomized ->
                translate("Random / privacy address. The MAC can change, so this is not a lasting identity.")
            type.equals("Anonymous", true) ->
                translate("Anonymous: the stack hid the address.")
            else ->
                listOfNotNull(type, translate("Universal IEEE address (stable OUI).")).joinToString(" · ")
        }
    }

    fun rssiBand(rssi: Int, translate: (String) -> String = { it }): String = when {
        !Rssi.measured(rssi) -> translate("not available")
        rssi >= -45 -> translate("very strong")
        rssi >= -60 -> translate("strong")
        rssi >= -75 -> translate("medium")
        rssi >= -88 -> translate("weak")
        else -> translate("very weak")
    }

    fun rssiExplain(rssi: Int, translate: (String) -> String = { it }): String =
        if (!Rssi.measured(rssi)) translate("Not available")
        else "%d dBm · %s".format(rssi, rssiBand(rssi, translate))

    fun wifiSecurityExplain(raw: String, translate: (String) -> String = { it }): String {
        val bits = ArrayList<String>(4)
        val u = raw.uppercase()
        when {
            "SAE" in u || "WPA3" in u -> bits += translate("WPA3 password (SAE handshake)")
            "OWE" in u -> bits += translate("Enhanced Open (encrypted, no password)")
            "PSK" in u && "WPA2" in u -> bits += translate("WPA2 password (PSK)")
            "PSK" in u || "WPA" in u -> bits += translate("Wi-Fi password (WPA/PSK)")
            "802.1X" in u || "EAP" in u -> bits += translate("Enterprise login (802.1X)")
            "WEP" in u -> bits += translate("WEP (old, weak)")
            "ESS" in u && bits.isEmpty() -> bits += translate("Open or encryption not parsed")
        }
        when {
            "CCMP" in u || "GCMP" in u -> bits += translate("AES encryption")
            "TKIP" in u -> bits += translate("TKIP (older, weaker cipher)")
        }
        if ("WPS" in u) bits += translate("WPS setup is enabled")
        if ("MESH" in u) bits += translate("mesh node")
        if ("IBSS" in u) bits += translate("ad-hoc network")
        if ("ESS" in u) bits += translate("infrastructure access point")
        return if (bits.isEmpty()) raw else bits.distinct().joinToString(". ") + "."
    }

    fun uuidGloss(uuid: String, translate: (String) -> String = { it }): String? {
        val name = RadioDb.serviceUuid(uuid)
        val short = uuid16(uuid) ?: return name?.let(translate)
        val extra = when (short) {
            0x1800 -> "connection basics"
            0x1801 -> "attribute protocol"
            0x180A -> "model / serial / firmware"
            0x180F -> "battery level"
            0x1812 -> "keyboard, mouse, or gamepad"
            0x180D -> "heart-rate sensor"
            0x1810 -> "blood-pressure sensor"
            0x181A -> "temperature / humidity style sensor"
            0x181D -> "weight-scale measurements (brand not specified)"
            0x181B -> "body-composition measurements (brand not specified)"
            0xFCF1 -> "Google vendor service (not proof of a Find Hub tag)"
            0x1844, 0x1845, 0x1846 -> "LE cycling power/speed"
            0x1850, 0x184E, 0x184F -> "LE Audio"
            0xFE2C -> "Google Fast Pair (often buds/speakers)"
            0xFD5A -> "Samsung SmartTag"
            0xFD44 -> "Apple Find My related"
            0xFEED, 0xFEDD -> "Tile tracker"
            0xFD50 -> "Tuya IoT"
            0xFEBE, 0xFE21 -> "Bose"
            0xFE78 -> "HP printer"
            0xFE07 -> "Sonos speaker"
            0xFEAF, 0xFEB0 -> "Nest Weave"
            0xFCBF -> "ASSA ABLOY Opening Solutions"
            0xFE24 -> "August Home lock"
            0xFCF4 -> "Allegion / Schlage"
            0xFCB2 -> "Apple (not ASSA ABLOY)"
            else -> null
        }
        return when {
            name != null && extra != null -> "${translate(name)} — ${translate(extra)}"
            name != null -> translate(name)
            extra != null -> translate(extra)
            else -> null
        }
    }

    private data class Hint(
        val bucket: String,
        val label: String,
        val reason: String,
        val weight: Int,
    )

    private fun appearanceHint(name: String?, translate: (String) -> String = { it }): Hint? {
        if (name.isNullOrBlank() || name.equals("Unknown", true)) return null
        val n = name.lowercase()
        val (bucket, label, w) = when {
            "ear" in n || "headphone" in n || "headset" in n || "hearable" in n || "hearing" in n ->
                Triple("audio-personal", "earbuds or headphones", 7)
            "speaker" in n || "loudspeaker" in n || "hifi" in n ->
                Triple("audio-speaker", "a speaker", 7)
            "mouse" in n -> Triple("mouse", "a mouse", 8)
            "keyboard" in n -> Triple("keyboard", "a keyboard", 8)
            "gamepad" in n || "joystick" in n -> Triple("gamepad", "a game controller", 7)
            "watch" in n -> Triple("watch", "a watch or wrist wearable", 7)
            "phone" in n -> Triple("phone", "a phone", 6)
            "laptop" in n || "computer" in n || "desktop" in n || "tablet" in n ->
                Triple("computer", "a computer or tablet", 6)
            "tag" in n || "keyring" in n -> Triple("tag", "a finder tag / tracker", 6)
            "remote" in n -> Triple("remote", "a remote control", 6)
            "hid" in n -> Triple("hid", "an input device (keyboard, mouse, or similar)", 4)
            "heart" in n -> Triple("health", "a heart-rate monitor", 7)
            "glucose" in n || "oximeter" in n || "blood pressure" in n || "thermometer" in n ->
                Triple("health", "a health sensor", 6)
            "display" in n || "monitor" in n -> Triple("display", "a display or TV stick", 4)
            "clock" in n -> Triple("clock", "a clock", 5)
            "glasses" in n -> Triple("glasses", "smart glasses", 6)
            else -> Triple("other", translate(name), 3)
        }
        return Hint(bucket, label, translate("It advertises Appearance as %1\$s.").format(translate(name)), w)
    }

    private fun codHint(cod: CodDecoder.Decoded, translate: (String) -> String = { it }): Hint? {
        val minor = cod.minor.lowercase()
        val major = cod.major.lowercase()
        val (bucket, label, w) = when {
            "headphone" in minor || "headset" in minor || "hands-free" in minor ->
                Triple("audio-personal", "earbuds or a headset", 6)
            "loudspeaker" in minor || "portable audio" in minor || "hifi" in minor || "car audio" in minor ->
                Triple("audio-speaker", "a speaker", 6)
            "pointing" in minor || minor == "mouse" -> Triple("mouse", "a mouse", 7)
            "keyboard" in minor -> Triple("keyboard", "a keyboard", 7)
            "gamepad" in minor || "joystick" in minor -> Triple("gamepad", "a game controller", 6)
            "smartphone" in minor || (major == "phone" && "uncategorized" !in minor) ->
                Triple("phone", "a phone", 5)
            "laptop" in minor || "tablet" in minor || "desktop" in minor ->
                Triple("computer", "a computer", 5)
            "wristwatch" in minor -> Triple("watch", "a watch", 6)
            "heart" in minor || "pulse" in minor || "glucose" in minor || "oximeter" in minor ->
                Triple("health", "a health sensor", 6)
            "audio" in major -> Triple("audio-personal", "an audio device", 3)
            "peripheral" in major -> Triple("hid", "an input accessory", 3)
            "uncategorized" in major || "miscellaneous" in major -> return null
            else -> return null
        }
        val localized = CodDecoder.decode(cod.raw, translate)
        val shown = if (cod.minor.isNotBlank() && cod.minor != "Uncategorized") {
            "${localized.major} / ${localized.minor}"
        } else {
            localized.major
        }
        return Hint(bucket, label, translate("Class of Device says %1\$s.").format(shown), w)
    }

    private fun uuidHints(uuids: List<String>): List<Hint> {
        val out = ArrayList<Hint>(4)
        for (uuid in uuids) {
            val id = uuid16(uuid) ?: continue
            when (id) {
                0x1812 -> out += Hint("hid", "a keyboard, mouse, or gamepad", "It offers the HID (human-interface) service.", 5)
                0x1108, 0x1112, 0x111E, 0x110B, 0x110A, 0x1131, 0x1203 ->
                    out += Hint("audio-personal", "headphones, a headset, or a speaker", "It offers a classic audio / headset service.", 5)
                0x184E, 0x184F, 0x1850, 0x1851 ->
                    out += Hint("audio-personal", "LE Audio earbuds or a speaker", "It offers Bluetooth LE Audio services.", 6)
                0x180D -> out += Hint("health", "a heart-rate monitor", "It offers the Heart Rate service.", 6)
                0x1810 -> out += Hint("health", "a blood-pressure monitor", "It offers the Blood Pressure service.", 6)
                0x181A -> out += Hint("sensor", "an environmental sensor", "It offers Environmental Sensing.", 4)
                0x181D -> out += Hint("health", "a weight scale", "It offers the Weight Scale service; the service does not specify a brand or a person.", 6)
                0x181B -> out += Hint("health", "a body-composition scale", "It offers the Body Composition service; the service does not specify a brand or a person.", 6)
                0xFE2C -> out += Hint("audio-personal", "earbuds or a speaker", "Google Fast Pair is present (common on buds and speakers).", 4)
                0xFD5A -> out += Hint("tag", "a Samsung SmartTag", "SmartTag service UUID.", 7)
                0xFD44 -> out += Hint("tag", "an Apple Find My accessory", "Find My related UUID.", 6)
                0xFEED, 0xFEDD -> out += Hint("tag", "a Tile tracker", "Tile service UUID.", 7)
            }
        }
        return out
    }

    private fun signatureHints(names: List<String>, translate: (String) -> String = { it }): List<Hint> {
        return names.mapNotNull { raw ->
            if (isGenericSignatureName(raw)) return@mapNotNull null
            val n = raw.lowercase()
            v94ByName[n]?.let { family ->
                return@mapNotNull Hint(if (family.kind == SignatureClass.ISP) "router" else "other", translate(family.name), translate(family.notes), 5)
            }
            val v93 = v93ByName[n]
            if (v93 != null) {
                val (bucket, label) = when (v93.id) {
                    "fleet-generic-hid" -> "hid" to "a HID input device (brand unconfirmed)"
                    "fleet-generic-remote" -> "remote" to "a possible remote control (brand unconfirmed)"
                    "fleet-ikf-king-pro" -> "audio-personal" to "possible iKF-King Pro headphones"
                    "fleet-leapmotor-digital-key" -> "vehicle" to "a possible Leapmotor digital-key radio"
                    "fleet-ingeek-lex" -> "other" to "an InGeek ecosystem radio (purpose unconfirmed)"
                    "fleet-apple-awdl" -> "other" to "an AWDL protocol advertiser (product unconfirmed)"
                    else -> "other" to v93.name
                }
                return@mapNotNull Hint(bucket, translate(label), translate(v93.notes), 5)
            }
            val domestic = when (n) {
                "qi'an / qingju radio (type unknown)" -> Hint("qian-radio", "a Qi'an / Qingju radio of unknown device type", "The advertised name and public registered prefix identify the operator family; lock versus parking infrastructure is unverified.", 6)
                "bicycle parking beacon" -> Hint("beacon", "a bicycle parking-area beacon", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "niu link vehicle" -> Hint("vehicle", "a NIU vehicle radio", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "cfmoto vehicle" -> Hint("vehicle", "a CFMOTO vehicle radio or instrument", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "ingeek digital vehicle key" -> Hint("vehicle", "an InGeek vehicle digital-key radio", "Matched a device-family fingerprint; the exact model and operating state are not established.", 5)
                "zeekr vehicle radio" -> Hint("vehicle", "a ZEEKR vehicle radio", "Matched a device-family fingerprint; the exact model and operating state are not established.", 5)
                "roadbit e-bike radio" -> Hint("vehicle", "a possible RoadBit e-bike radio", "Matched a device-family fingerprint; the exact model and operating state are not established.", 5)
                "narwal robot vacuum" -> Hint("home", "a Narwal robot vacuum", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "colmo appliance" -> Hint("home", "a COLMO home appliance", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "petkit pet appliance" -> Hint("home", "a PETKIT pet appliance", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "honor band wearable" -> Hint("wearable", "an Honor fitness band", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "oppo watch wearable" -> Hint("wearable", "an OPPO smartwatch", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "mjwsd thermometer" -> Hint("sensor", "a Xiaomi ecosystem temperature/humidity monitor", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "mijia scale s400" -> Hint("health", "a Mijia S400 body-composition scale", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "mi ecosystem smart switch" -> Hint("home", "a Mi ecosystem smart switch", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "mi ecosystem smart light" -> Hint("home", "a Mi ecosystem smart light", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "cariot phone holder" -> Hint("vehicle", "a Cariot phone holder", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "haier appliance radio" -> Hint("home", "a Haier appliance radio", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "zte wi-fi radio" -> Hint("ap", "a ZTE Wi-Fi gateway, access point or hotspot", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "phicomm wi-fi" -> Hint("ap", "a Phicomm Wi-Fi access point or router", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "360 wi-fi" -> Hint("ap", "a 360 Wi-Fi router or hotspot", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)

                "h3c wi-fi" -> Hint("ap", "an H3C Wi-Fi access point or router", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "xiaomi wi-fi" -> Hint("ap", "a Xiaomi Wi-Fi access point or hotspot", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "dji romo robot vacuum" -> Hint("home", "a DJI ROMO robot vacuum", "The ROMO name identifies a robot vacuum family, not a flying aircraft.", 8)
                "mobike bicycle lock" -> Hint("vehicle", "a Mobike bicycle lock", "Mobike uses a dedicated BLE lock service and company identifier.", 7)
                "mercury wi-fi" -> Hint("network", "a Mercury Wi-Fi access point", "A MERCURY name identifies a network family; it does not identify an exact model.", 6)
                "mibeacon ecosystem" -> Hint("home", "a MiBeacon ecosystem device", "MiBeacon is an ecosystem advertisement; partner manufacturers also use it.", 4)
                "lywsd03mmc thermometer" -> Hint("sensor", "an LYWSD03MMC temperature / humidity sensor", "The advertised LYWSD03MMC name identifies a sensor family; encrypted frames do not expose readings.", 8)
                "mi_scale scale" -> Hint("health", "a Xiaomi ecosystem scale", "The MI_SCALE name identifies a scale family; it does not identify a person.", 8)
                "lumi / aqara switch" -> Hint("home", "a Lumi / Aqara switch", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "midea appliance" -> Hint("home", "a Midea appliance", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "luyuan-smart vehicle" -> Hint("vehicle", "a Luyuan electric two-wheeler radio", "Matched a vehicle-family name; this does not identify a rider or show motion.", 7)
                "aima vehicle" -> Hint("vehicle", "an Aima electric two-wheeler radio", "Matched a vehicle-family name; this does not identify a rider or show motion.", 7)
                "hellobike bicycle" -> Hint("vehicle", "a Hello bicycle or lock radio", "Matched a vehicle-family name; this does not identify a rider or show motion.", 7)
                "edifier ble audio" -> Hint("audio-personal", "an Edifier audio radio", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "keep_cc fitness equipment" -> Hint("health", "Keep fitness equipment", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                "ezviz device" -> Hint("home", "an EZVIZ device", "The address prefix identifies a device family; it does not prove the device is a camera.", 6)
                "daikin wi-fi appliance" -> Hint("home", "a Daikin appliance or controller", "Matched a device-family fingerprint; the exact model and operating state are not established.", 7)
                else -> null
            }
            if (domestic != null) return@mapNotNull domestic.copy(reason =
                translate("Matched signature %1\$s.").format(raw) + " " + translate(domestic.reason))
            when {
                "airtag" in n || n == "find my" || "find hub" in n || "dult" in n ->
                    Hint(
                        "tag",
                        when {
                            "dult" in n -> "a DULT finder tag"
                            "find hub" in n -> "a Google Find Hub tag"
                            else -> "Find My device (type unconfirmed)"
                        },
                        translate("Matched signature %1\$s.").format(raw),
                        8,
                    )
                "apple device" in n ->
                    Hint("apple-unknown", "Apple device (type unconfirmed)", translate("Matched signature %1\$s.").format(raw), 7)
                "apple audio" in n ->
                    Hint("audio-personal", "AirPods, Beats, or AirPlay", translate("Matched signature %1\$s.").format(raw), 7)
                "microsoft" in n ->
                    Hint("computer", "a Windows / Surface / Xbox radio", translate("Matched signature %1\$s.").format(raw), 6)
                n == "tesla tstpms" ->
                    Hint("vehicle", "a Tesla BLE tire sensor", translate("Matched signature %1\$s.").format(raw), 7)
                "tpms" in n || n == "tirecheck" || n == "sytpms" ->
                    Hint("vehicle", "a BLE tire-pressure sensor", translate("Matched signature %1\$s.").format(raw), 7)
                n == "vuzix" ->
                    Hint("glasses", "Vuzix smart glasses", translate("Matched signature %1\$s.").format(raw), 7)
                n == "tesla" ->
                    Hint("vehicle", "a Tesla vehicle (including Cybertruck) or phone-as-key", translate("Matched signature %1\$s.").format(raw), 7)
                n == "google" ->
                    Hint("phone", "a Pixel or other Google radio", translate("Matched signature %1\$s.").format(raw), 6)
                n == "sony" ->
                    Hint("audio-personal", "Sony headphones, a TV, or a camera", translate("Matched signature %1\$s.").format(raw), 6)
                n == "bose" ->
                    Hint("audio-personal", "Bose headphones or a speaker", translate("Matched signature %1\$s.").format(raw), 7)
                n == "garmin" ->
                    Hint("watch", "a Garmin watch or inReach", translate("Matched signature %1\$s.").format(raw), 7)
                n == "amazon" ->
                    Hint("speaker", "an Echo, Fire, or other Amazon radio", translate("Matched signature %1\$s.").format(raw), 6)
                n == "fitbit" ->
                    Hint("watch", "a Fitbit", translate("Matched signature %1\$s.").format(raw), 7)
                n == "oura" ->
                    Hint("wearable", "an Oura ring", translate("Matched signature %1\$s.").format(raw), 7)
                n == "logitech" ->
                    Hint("hid", "a Logitech mouse, keyboard, or webcam", translate("Matched signature %1\$s.").format(raw), 6)
                "jbl" in n || n == "harman" ->
                    Hint("audio-personal", "JBL or Harman audio", translate("Matched signature %1\$s.").format(raw), 6)
                n == "sonos" ->
                    Hint("audio-speaker", "a Sonos speaker", translate("Matched signature %1\$s.").format(raw), 7)
                n == "gopro" ->
                    Hint("camera", "a GoPro", translate("Matched signature %1\$s.").format(raw), 7)
                n == "osmo" ->
                    Hint("camera", "a DJI Osmo action camera", translate("Matched signature %1\$s.").format(raw), 7)
                n == "insta360" ->
                    Hint("camera", "an Insta360 camera", translate("Matched signature %1\$s.").format(raw), 7)
                n == "dji" ->
                    Hint("drone", "a DJI drone or controller", translate("Matched signature %1\$s.").format(raw), 7)
                n == "remote id" ->
                    Hint("drone", "a drone broadcasting ASTM Remote ID", translate("Matched signature %1\$s.").format(raw), 8)
                n == "skydio" ->
                    Hint("drone", "a Skydio drone", translate("Matched signature %1\$s.").format(raw), 7)
                n == "autel" ->
                    Hint("drone", "an Autel drone", translate("Matched signature %1\$s.").format(raw), 7)
                n == "parrot" ->
                    Hint("drone", "a Parrot ANAFI or Bebop drone", translate("Matched signature %1\$s.").format(raw), 7)
                n == "hoverair" ->
                    Hint("drone", "a HOVERAir flying camera", translate("Matched signature %1\$s.").format(raw), 7)
                n == "netgear" || n == "orbi" ->
                    Hint("ap", "a NETGEAR or Orbi access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "tp-link" ->
                    Hint("ap", "a TP-Link access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "asus" ->
                    Hint("ap", "an ASUS access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "linksys" ->
                    Hint("ap", "a Linksys or Velop access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "eero" ->
                    Hint("ap", "an Eero mesh node", translate("Matched signature %1\$s.").format(raw), 6)
                n == "google wifi" ->
                    Hint("ap", "a Google Wifi or Nest Wifi point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "d-link" ->
                    Hint("ap", "a D-Link access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "belkin" ->
                    Hint("ap", "a Belkin access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "xfinity" ->
                    Hint("ap", "an Xfinity gateway or hotspot", translate("Matched signature %1\$s.").format(raw), 6)
                n == "spectrum" ->
                    Hint("ap", "a Spectrum gateway or Spectrum Mobile hotspot", translate("Matched signature %1\$s.").format(raw), 6)
                n == "at&t" ->
                    Hint("ap", "an AT&T gateway or attwifi hotspot", translate("Matched signature %1\$s.").format(raw), 6)
                n == "verizon" ->
                    Hint("ap", "a Verizon or Fios gateway", translate("Matched signature %1\$s.").format(raw), 6)
                n == "starlink" ->
                    Hint("ap", "a Starlink router", translate("Matched signature %1\$s.").format(raw), 7)
                n == "meraki" ->
                    Hint("ap", "a Cisco Meraki access point", translate("Matched signature %1\$s.").format(raw), 7)
                n == "cisco" ->
                    Hint("ap", "a Cisco Aironet, Catalyst, Business, RV, or SPVTG access point", translate("Matched signature %1\$s.").format(raw), 7)
                n == "mist" ->
                    Hint("ap", "a Juniper Mist access point", translate("Matched signature %1\$s.").format(raw), 7)
                n == "t-mobile" ->
                    Hint("ap", "a T-Mobile Home Internet gateway or hotspot", translate("Matched signature %1\$s.").format(raw), 6)
                n == "humax" ->
                    Hint("ap", "a HUMAX gateway (often T-Mobile Home Internet)", translate("Matched signature %1\$s.").format(raw), 6)
                n == "sagemcom" ->
                    Hint("ap", "a Sagemcom ISP gateway", translate("Matched signature %1\$s.").format(raw), 6)
                n == "arcadyan" ->
                    Hint("ap", "an Arcadyan ISP gateway", translate("Matched signature %1\$s.").format(raw), 6)
                n == "askey" ->
                    Hint("ap", "an Askey ISP / 5G gateway", translate("Matched signature %1\$s.").format(raw), 6)
                n == "calix" ->
                    Hint("ap", "a Calix fiber gateway", translate("Matched signature %1\$s.").format(raw), 6)
                n == "nokia" ->
                    Hint("ap", "a Nokia Solutions and Networks gateway", translate("Matched signature %1\$s.").format(raw), 6)
                n == "airties" ->
                    Hint("ap", "an AirTies ISP mesh node", translate("Matched signature %1\$s.").format(raw), 6)
                n == "tenda" ->
                    Hint("ap", "a Tenda access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "ruijie" ->
                    Hint("ap", "a Ruijie or Reyee access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "dwnet" ->
                    Hint("ap", "a DWnet access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "wavlink" ->
                    Hint("ap", "a WAVLINK access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "sercomm" ->
                    Hint("ap", "a Sercomm ISP gateway", translate("Matched signature %1\$s.").format(raw), 6)
                n == "luxul" ->
                    Hint("ap", "a Luxul access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "sophos" ->
                    Hint("ap", "a Sophos firewall or access point", translate("Matched signature %1\$s.").format(raw), 7)
                n == "aumovio" ->
                    Hint("hotspot", "an AUMOVIO / Continental vehicle Wi-Fi radio", translate("Matched signature %1\$s.").format(raw), 6)
                n == "centurylink" ->
                    Hint("ap", "a CenturyLink gateway", translate("Matched signature %1\$s.").format(raw), 6)
                n == "gm hotspot" ->
                    Hint("hotspot", "a GM in-car hotspot (Cadillac / GMC / Buick / Chevrolet)", translate("Matched signature %1\$s.").format(raw), 6)
                n == "audi mmi" ->
                    Hint("hotspot", "an Audi MMI in-car hotspot", translate("Matched signature %1\$s.").format(raw), 6)
                n == "extreme" ->
                    Hint("ap", "an Extreme Networks access point", translate("Matched signature %1\$s.").format(raw), 7)
                n == "adtran" ->
                    Hint("ap", "an Adtran fiber gateway (often CenturyLink / Quantum Fiber OEM)", translate("Matched signature %1\$s.").format(raw), 6)
                n == "cambium" ->
                    Hint("ap", "a Cambium or IgniteNet access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "trendnet" ->
                    Hint("ap", "a TRENDnet access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "cudy" ->
                    Hint("ap", "a Cudy travel or home router", translate("Matched signature %1\$s.").format(raw), 6)
                n == "snapav" ->
                    Hint("ap", "a SnapAV / Control4 / Wattbox access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "arlo" ->
                    Hint("camera", "an Arlo camera or VMB base station", translate("Matched signature %1\$s.").format(raw), 6)
                n == "vantiva" ->
                    Hint("ap", "a Vantiva or Technicolor ISP gateway", translate("Matched signature %1\$s.").format(raw), 6)
                n == "hitron" ->
                    Hint("ap", "a Hitron cable gateway (often Xfinity OEM)", translate("Matched signature %1\$s.").format(raw), 6)
                n == "actiontec" ->
                    Hint("ap", "an Actiontec FiOS or Frontier gateway", translate("Matched signature %1\$s.").format(raw), 6)
                n == "buffalo" ->
                    Hint("ap", "a Buffalo AirStation or router", translate("Matched signature %1\$s.").format(raw), 6)
                n == "grandstream" ->
                    Hint("ap", "a Grandstream GWN access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "edgecore" ->
                    Hint("ap", "an Edgecore access point", translate("Matched signature %1\$s.").format(raw), 7)
                n == "watchguard ap" ->
                    Hint("ap", "a WatchGuard firewall or access point", translate("Matched signature %1\$s.").format(raw), 7)
                n == "mojo" ->
                    Hint("ap", "a Mojo Networks / Arista Cognitive Wi-Fi access point", translate("Matched signature %1\$s.").format(raw), 7)
                n == "winegard" ->
                    Hint("hotspot", "a Winegard RV or marine Wi-Fi radio", translate("Matched signature %1\$s.").format(raw), 6)
                n == "inseego" ->
                    Hint("ap", "an Inseego 5G or MiFi hotspot", translate("Matched signature %1\$s.").format(raw), 6)
                n == "franklin" ->
                    Hint("ap", "a Franklin Technology 5G home-internet gateway (RG3100 class)", translate("Matched signature %1\$s.").format(raw), 6)
                n == "synology" ->
                    Hint("ap", "a Synology NAS or router access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "aruba" ->
                    Hint("ap", "an HPE Aruba Instant or Instant On access point", translate("Matched signature %1\$s.").format(raw), 7)
                n == "ruckus" ->
                    Hint("ap", "a RUCKUS access point", translate("Matched signature %1\$s.").format(raw), 7)
                n == "fortinet" ->
                    Hint("ap", "a Fortinet FortiAP or FortiWiFi", translate("Matched signature %1\$s.").format(raw), 7)
                n == "mikrotik" ->
                    Hint("ap", "a MikroTik router or access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "engenius" ->
                    Hint("ap", "an EnGenius access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "zyxel" ->
                    Hint("ap", "a Zyxel gateway or access point", translate("Matched signature %1\$s.").format(raw), 6)
                n == "peplink" ->
                    Hint("ap", "a Peplink or Pepwave router", translate("Matched signature %1\$s.").format(raw), 6)
                n == "openwrt" ->
                    Hint("ap", "an OpenWrt router", translate("Matched signature %1\$s.").format(raw), 6)
                n == "arris" ->
                    Hint("ap", "an Arris or SURFboard cable gateway", translate("Matched signature %1\$s.").format(raw), 6)
                n == "unifi ap" ->
                    Hint("ap", "a Ubiquiti UniFi access point", translate("Matched signature %1\$s.").format(raw), 7)
                n == "unifi protect" ->
                    Hint("camera", "a UniFi Protect Instant camera", translate("Matched signature %1\$s.").format(raw), 7)
                n == "unifi" ->
                    Hint("ap", "a UniFi / Ubiquiti name", translate("Matched signature %1\$s.").format(raw), 5)
                n == "ecobee" ->
                    Hint("thermostat", "an ecobee thermostat", translate("Matched signature %1\$s.").format(raw), 7)
                n == "sensi" ->
                    Hint("thermostat", "a Sensi thermostat", translate("Matched signature %1\$s.").format(raw), 6)
                n == "honeywell home" ->
                    Hint("thermostat", "a Honeywell Home or Lyric thermostat", translate("Matched signature %1\$s.").format(raw), 6)
                "honeywell xenon" in n ->
                    Hint("health", "a Honeywell Xenon healthcare barcode scanner", translate("Matched signature %1\$s.").format(raw), 7)
                n == "omron" ->
                    Hint("health", "an Omron blood-pressure cuff or scale", translate("Matched signature %1\$s.").format(raw), 7)
                n == "withings" ->
                    Hint("health", "a Withings scale or blood-pressure monitor", translate("Matched signature %1\$s.").format(raw), 7)
                n == "dexcom" ->
                    Hint("health", "a Dexcom glucose sensor", translate("Matched signature %1\$s.").format(raw), 7)
                n == "nest thermostat" ->
                    Hint("thermostat", "a Nest thermostat or Nest Labs BLE sensor", translate("Matched signature %1\$s.").format(raw), 6)
                n == "nest weave" ->
                    Hint("sensor", "a Nest Protect, camera, or other Weave BLE device", translate("Matched signature %1\$s.").format(raw), 7)
                n == "haiku fan" || n == "haiku" ->
                    Hint("fan", "a Haiku or Mammoth ceiling fan", translate("Matched signature %1\$s.").format(raw), 7)
                n == "tuya" ->
                    Hint("iot", "a Tuya BLE gadget (plug, light, camera, sensor)", translate("Matched signature %1\$s.").format(raw), 6)
                n == "seos" || n == "assa abloy" ->
                    Hint("access", "an ASSA ABLOY lock, Yale lock, HID reader, or Seos credential", translate("Matched signature %1\$s.").format(raw), 7)
                n == "august" ->
                    Hint("lock", "an August smart lock", translate("Matched signature %1\$s.").format(raw), 7)
                n == "schlage" ->
                    Hint("lock", "a Schlage or Allegion lock", translate("Matched signature %1\$s.").format(raw), 7)
                n == "nuki" ->
                    Hint("lock", "a Nuki lock or opener", translate("Matched signature %1\$s.").format(raw), 7)
                n == "salto" ->
                    Hint("access", "a SALTO access reader or lock", translate("Matched signature %1\$s.").format(raw), 7)
                n == "dormakaba" ->
                    Hint("access", "a dormakaba, Saflok, or Oracode lock", translate("Matched signature %1\$s.").format(raw), 7)
                n == "lockly" ->
                    Hint("lock", "a Lockly smart lock", translate("Matched signature %1\$s.").format(raw), 6)
                n == "kevo" ->
                    Hint("lock", "a Kwikset Kevo or Unikey lock", translate("Matched signature %1\$s.").format(raw), 7)
                n == "master lock" ->
                    Hint("lock", "a Master Lock padlock", translate("Matched signature %1\$s.").format(raw), 7)
                n == "igloohome" ->
                    Hint("lock", "an igloohome lock or keybox", translate("Matched signature %1\$s.").format(raw), 7)
                n == "tedee" ->
                    Hint("lock", "a Tedee smart lock", translate("Matched signature %1\$s.").format(raw), 7)
                n == "paxton" ->
                    Hint("access", "a Paxton reader or Net2 access point", translate("Matched signature %1\$s.").format(raw), 7)
                n == "kwikset" ->
                    Hint("lock", "a Kwikset lock", translate("Matched signature %1\$s.").format(raw), 6)
                n == "myq" ->
                    Hint("garage", "a Chamberlain myQ garage hub", translate("Matched signature %1\$s.").format(raw), 7)
                n == "chevrolet hotspot" ->
                    Hint("hotspot", "a Chevrolet in-car Wi-Fi hotspot", translate("Matched signature %1\$s.").format(raw), 7)
                n == "rivian" ->
                    Hint("vehicle", "a Rivian vehicle, phone key, or sensor", translate("Matched signature %1\$s.").format(raw), 7)
                n == "ford" ->
                    Hint("vehicle", "a Ford or Lincoln vehicle or phone-as-key", translate("Matched signature %1\$s.").format(raw), 7)
                n == "honda" ->
                    Hint("vehicle", "a Honda or Acura vehicle or phone-as-key", translate("Matched signature %1\$s.").format(raw), 7)
                n == "hyundai" ->
                    Hint("vehicle", "a Hyundai or Genesis vehicle or phone-as-key", translate("Matched signature %1\$s.").format(raw), 7)
                n == "toyota" ->
                    Hint("vehicle", "a Toyota or Lexus vehicle or phone-as-key", translate("Matched signature %1\$s.").format(raw), 7)
                n == "nissan" ->
                    Hint("vehicle", "a Nissan or Infiniti vehicle or phone-as-key", translate("Matched signature %1\$s.").format(raw), 7)
                n == "subaru" ->
                    Hint("vehicle", "a Subaru vehicle or phone-as-key", translate("Matched signature %1\$s.").format(raw), 7)
                n == "bmw" ->
                    Hint("vehicle", "a BMW vehicle, phone-as-key, or factory hotspot", translate("Matched signature %1\$s.").format(raw), 7)
                n == "volkswagen" ->
                    Hint("vehicle", "a Volkswagen vehicle or phone-as-key", translate("Matched signature %1\$s.").format(raw), 7)
                n == "porsche" ->
                    Hint("vehicle", "a Porsche vehicle or phone-as-key", translate("Matched signature %1\$s.").format(raw), 7)
                n == "jaguar land rover" ->
                    Hint("vehicle", "a Jaguar, Land Rover, or Range Rover", translate("Matched signature %1\$s.").format(raw), 7)
                n == "byd" ->
                    Hint("vehicle", "a BYD vehicle or phone-as-key", translate("Matched signature %1\$s.").format(raw), 7)
                n == "govee" ->
                    Hint("light", "a Govee light or sensor", translate("Matched signature %1\$s.").format(raw), 6)
                n == "hp" ->
                    Hint("printer", "an HP printer", translate("Matched signature %1\$s.").format(raw), 6)
                n == "epson" ->
                    Hint("printer", "an Epson EcoTank or WorkForce printer", translate("Matched signature %1\$s.").format(raw), 6)
                n == "lg webos tv" ->
                    Hint("tv", "an LG webOS TV", translate("Matched signature %1\$s.").format(raw), 7)
                n == "roku" ->
                    Hint("tv", "a Roku streaming stick or Roku TV (often a hidden Wi-Fi Direct remote AP)", translate("Matched signature %1\$s.").format(raw), 7)
                n == "samsung appliance" ->
                    Hint("iot", "a Samsung fridge, range, oven, or cooktop (setup AP)", translate("Matched signature %1\$s.").format(raw), 6)
                n == "ecowater" ->
                    Hint("iot", "an EcoWater water softener (setup AP)", translate("Matched signature %1\$s.").format(raw), 6)
                n == "nespresso" ->
                    Hint("iot", "a Nespresso coffee machine", translate("Matched signature %1\$s.").format(raw), 7)
                n == "radiacode" ->
                    Hint("sensor", "a RadiaCode radiation detector", translate("Matched signature %1\$s.").format(raw), 7)
                n == "shokz" ->
                    Hint("audio-personal", "Shokz OpenRun or OpenFit headphones", translate("Matched signature %1\$s.").format(raw), 7)
                n == "mercedes mbux" ->
                    Hint("hotspot", "a Mercedes MBUX in-car hotspot", translate("Matched signature %1\$s.").format(raw), 7)
                n == "motive" ->
                    Hint("hotspot", "a Motive / KeepTruckin fleet ELD hotspot", translate("Matched signature %1\$s.").format(raw), 6)
                n == "peoplenet" ->
                    Hint("hotspot", "a PeopleNet fleet ELD hotspot", translate("Matched signature %1\$s.").format(raw), 6)
                n == "uconnect" ->
                    Hint("hotspot", "a Uconnect in-car hotspot", translate("Matched signature %1\$s.").format(raw), 6)
                n == "carplay" ->
                    Hint("hotspot", "a CarPlay in-car hotspot", translate("Matched signature %1\$s.").format(raw), 6)
                n == "cradlepoint" ->
                    Hint("hotspot", "a Cradlepoint vehicle router (often public-safety / fleet)", translate("Matched signature %1\$s.").format(raw), 7)
                n == "airlink" ->
                    Hint("hotspot", "a Sierra Wireless AirLink vehicle gateway", translate("Matched signature %1\$s.").format(raw), 7)
                n == "compex" ->
                    Hint("hotspot", "a Compex access point (sometimes public-safety / fleet)", translate("Matched signature %1\$s.").format(raw), 6)
                n == "novatel wireless" ->
                    Hint("hotspot", "a Novatel Wireless / Inseego vehicle radio", translate("Matched signature %1\$s.").format(raw), 6)
                n == "utility inc" ->
                    Hint("hotspot", "a Utility, Inc vehicle or public-safety radio", translate("Matched signature %1\$s.").format(raw), 6)
                "gl.inet" in n || n == "glinet" ->
                    Hint("ap", "a GL.iNet travel router", translate("Matched signature %1\$s.").format(raw), 6)
                "smarttag" in n ->
                    Hint("tag", "a Samsung SmartTag", translate("Matched signature %1\$s.").format(raw), 8)
                "tile" in n ->
                    Hint("tag", "a Tile tracker", translate("Matched signature %1\$s.").format(raw), 8)
                n == "ibeacon" ->
                    Hint("beacon", "an iBeacon", translate("Matched signature %1\$s.").format(raw), 7)
                "target atrius" in n ->
                    Hint("beacon", "a Target Atrius basket tag", translate("Matched signature %1\$s.").format(raw), 8)
                n == "minew" ->
                    Hint("beacon", "a Minew BLE beacon or sensor", translate("Matched signature %1\$s.").format(raw), 7)
                n == "estimote" ->
                    Hint("beacon", "an Estimote beacon", translate("Matched signature %1\$s.").format(raw), 7)
                n == "kontakt.io" || n == "kontakt" ->
                    Hint("beacon", "a Kontakt.io beacon", translate("Matched signature %1\$s.").format(raw), 7)
                "bluetoad" in n ->
                    Hint(
                        "roadside",
                        "an Iteris BlueTOAD / Vantage Velocity roadside Bluetooth travel-time reader",
                        translate("Matched signature %1\$s.").format(raw),
                        7,
                    )
                "bliptrack" in n ->
                    Hint(
                        "roadside",
                        "a BLIP Systems BlipTrack roadside travel-time sensor",
                        translate("Matched signature %1\$s.").format(raw),
                        7,
                    )
                "raven" in n || "shotspotter" in n || "soundthinking" in n ->
                    Hint(
                        "acoustic",
                        "a Flock Raven or ShotSpotter acoustic gunshot sensor",
                        translate("Matched signature %1\$s.").format(raw),
                        8,
                    )
                "digital ally" in n ->
                    Hint("camera", "a Digital Ally body-worn or in-car camera", translate("Matched signature %1\$s.").format(raw), 8)
                "reveal media" in n || "bodyworn" in n ->
                    Hint("camera", "a Reveal Media body-worn camera", translate("Matched signature %1\$s.").format(raw), 8)
                n == "wolfcom" ->
                    Hint("camera", "a Wolfcom body-worn or in-car camera", translate("Matched signature %1\$s.").format(raw), 8)
                "i-pro" in n || "arbitrator" in n ->
                    Hint("camera", "a Panasonic i-PRO camera or Arbitrator in-car system", translate("Matched signature %1\$s.").format(raw), 8)
                "limitless" in n ->
                    Hint("wearable", "a Limitless Pendant conversation recorder", translate("Matched signature %1\$s.").format(raw), 8)
                n == "bee pendant" || "bee pioneer" in n ->
                    Hint("wearable", "a Bee Pioneer wearable recorder", translate("Matched signature %1\$s.").format(raw), 8)
                n == "omi" || "openglass" in n ->
                    Hint("wearable", "an Omi pendant or OpenGlass camera glasses", translate("Matched signature %1\$s.").format(raw), 8)
                "friend pendant" in n ->
                    Hint("wearable", "a Friend Pendant necklace", translate("Matched signature %1\$s.").format(raw), 8)
                "brilliant frame" in n ->
                    Hint("glasses", "Brilliant Labs Frame AR glasses", translate("Matched signature %1\$s.").format(raw), 8)
                n == "even g1" ->
                    Hint("glasses", "Even Realities G1 glasses", translate("Matched signature %1\$s.").format(raw), 8)
                "hayden" in n ->
                    Hint("camera", "a Hayden AI bus- or vehicle-mounted camera", translate("Matched signature %1\$s.").format(raw), 8)
                "miovision" in n ->
                    Hint("camera", "a Miovision intersection traffic camera", translate("Matched signature %1\$s.").format(raw), 8)
                n == "tattile" ->
                    Hint("camera", "a Tattile plate reader", translate("Matched signature %1\$s.").format(raw), 8)
                "lvt" in n || "liveview" in n ->
                    Hint("camera", "an LVT / LiveView solar surveillance trailer", translate("Matched signature %1\$s.").format(raw), 8)
                "hanwha" in n || "wisenet" in n ->
                    Hint("camera", "a Hanwha Vision / Wisenet camera", translate("Matched signature %1\$s.").format(raw), 7)
                n == "uniview" ->
                    Hint("camera", "a Uniview / UNV camera", translate("Matched signature %1\$s.").format(raw), 7)
                n == "rhombus" ->
                    Hint("camera", "a Rhombus cloud camera", translate("Matched signature %1\$s.").format(raw), 7)
                n == "meshcore" ->
                    Hint("mesh", "a MeshCore LoRa companion radio", translate("Matched signature %1\$s.").format(raw), 7)
                "gotenna" in n ->
                    Hint("mesh", "a goTenna Mesh or Pro radio", translate("Matched signature %1\$s.").format(raw), 7)
                n == "sensecap" ->
                    Hint("mesh", "a SenseCAP LoRaWAN / Helium gateway", translate("Matched signature %1\$s.").format(raw), 7)
                "wisgate" in n || n == "rak wisgate" ->
                    Hint("mesh", "a RAK WisGate LoRaWAN gateway", translate("Matched signature %1\$s.").format(raw), 7)
                n == "ghostesp" ->
                    Hint("pentest", "a GhostESP ESP32 audit board", translate("Matched signature %1\$s.").format(raw), 7)
                n == "bruce" ->
                    Hint("pentest", "a Bruce ESP32 pentest board", translate("Matched signature %1\$s.").format(raw), 7)
                n == "liteon camera radio" ->
                    Hint(
                        "module",
                        "a camera-module radio (LiteOn or similar)",
                        translate("Matched signature %1\$s.").format(raw),
                        3,
                    )
                "chipolo" in n || "pebblebee" in n || "moto tag" in n ->
                    Hint("tag", "a finder tag", translate("Matched signature %1\$s.").format(raw), 7)
                "airpods" in n ->
                    Hint("audio-personal", "AirPods", translate("Matched signature %1\$s.").format(raw), 8)
                else -> Hint("named", raw, translate("Matched signature %1\$s.").format(raw), 7)
            }
        }
    }

    private fun isGenericSignatureName(name: String): Boolean {
        val n = name.trim()
        return n.equals("Unknown Signature", ignoreCase = true) ||
            n.equals("Unknown Fleet", ignoreCase = true)
    }

    private fun wifiHints(device: Sighting, signatureNames: List<String>, translate: (String) -> String = { it }): List<Hint> {
        val name = device.name
        val caps = (device.facts.capabilities ?: "").uppercase()
        val specific = signatureNames.any { !isGenericSignatureName(it) }
        val out = ArrayList<Hint>(2)
        device.facts.vendorIes.mapNotNull { WifiWpsDecoder.decode(it) }.forEach { wps ->
            if (wps.status == WifiWpsDecoder.Status.COMPLETE && !wps.manufacturer.isNullOrBlank() && !wps.model.isNullOrBlank()) {
                out += Hint("named", if (wps.chipsetOnly) translate("Wi-Fi access point (brand unconfirmed)") + " · " +
                    translate("Chipset platform: %1\$s").format(wps.identityLabel()) else wps.identityLabel(),
                    translate("WPS advertises manufacturer %1\$s and model %2\$s. These are broadcast fields, not a visual identification.")
                        .format(wps.manufacturer, wps.model), 10)
            }
        }
        when {
            name.startsWith("DIRECT-", true) ->
                out += if (specific) {
                    Hint("wifi-direct", "a Wi-Fi Direct access point", "SSID starts with DIRECT-.", 4)
                } else {
                    Hint("wifi-direct", "a phone or TV using Wi-Fi Direct", "SSID starts with DIRECT-.", 6)
                }
            name.startsWith("ANDROID-", true) || name.contains("hotspot", true) ->
                if (!specific) {
                    out += Hint("hotspot", "a phone hotspot", "SSID looks like a phone hotspot.", 6)
                }
            "MESH" in caps ->
                out += Hint("mesh", "a mesh Wi-Fi node", "Capability list includes mesh.", 5)
            device.hiddenSsid ->
                out += Hint("ap", "a hidden Wi-Fi access point", "SSID is hidden; the radio is still beaconing.", 4)
            else ->
                if (!specific) {
                    out += Hint("ap", "a Wi-Fi access point", "Stock Android only reports beaconing APs.", 3)
                }
        }
        return out
    }

    private fun uuid16(uuid: String): Int? {
        val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
        return when {
            hex.length == 4 -> hex.toIntOrNull(16)
            hex.length == 32 && hex.startsWith("0000") && hex.endsWith("00001000800000805F9B34FB") ->
                hex.substring(4, 8).toIntOrNull(16)
            hex.length == 8 -> hex.takeLast(4).toIntOrNull(16)
            else -> null
        }
    }
}
