package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import org.junit.Assert.*
import org.junit.Test

class DiscoveryV93Test {
    private val families = DefaultCatalog.discoveryFamiliesV93()
    private val catalog = DefaultCatalog.fleets()
    private val engine = SignatureEngine()
    private fun radio(kind: RadioKind = RadioKind.BLE, name: String = "", facts: RadioFacts = RadioFacts()) = Sighting(
        key = "$kind:00:11:22:00:00:01", kind = kind, mac = "00:11:22:00:00:01", name = name,
        rssi = -60, rssiMin = -60, rssiMax = -60, channel = 0, frequencyMhz = 0, vendor = null,
        randomized = false, hiddenSsid = false, serviceUuids = emptyList(), manufacturerId = null,
        manufacturerDataHex = "", rawHex = "", extras = "", firstSeen = 1000, lastSeen = 1000,
        hitCount = 1, fleetIds = emptySet(), rssiHistory = emptyList(), presence = emptyList(), facts = facts,
    )
    private fun hits(d: Sighting, fleets: List<Fleet> = catalog) = engine.match(listOf(d), fleets, 1000).getValue(d.key)
    private fun apple(hex: String, company: Int = 0x004C, kind: RadioKind = RadioKind.BLE) =
        radio(kind, facts = RadioFacts(mfgRecords = listOf(MfgRecord(company, hex))))
    private fun fields(hex: String) = AdvPayloadDecoder.decodeDevice(apple(hex)).associate { it.label to it.value }
    private fun wps(hex: String) = VendorIeRecord("00:50:F2", 4, hex)

    @Test fun inputServiceAndNamesDoNotInventBrandsOrProductGenerations() {
        val hid = radio().copy(serviceUuids = listOf("1812"))
        assertTrue("fleet-generic-hid" in hits(hid))
        assertEquals(SignatureClass.OTHER, families.single { it.id == "fleet-generic-hid" }.kind)
        assertFalse("fleet-generic-hid" in hits(hid.copy(kind = RadioKind.WIFI)))
        assertFalse("fleet-generic-hid" in hits(radio(facts = RadioFacts(appearance = 576))))
        for ((name, id) in listOf("Mixin Remote Control" to "generic-remote", "iKF-King Pro" to "ikf-king-pro",
            "iKF-King Pro-X" to "ikf-king-pro", "Leapmotor_DigitalKey" to "leapmotor-digital-key")) {
            assertTrue(name, "fleet-$id" in hits(radio(name = name)))
            assertFalse(name, "fleet-$id" in hits(radio(RadioKind.WIFI, name)))
        }
        assertFalse("fleet-generic-remote" in hits(radio(name = "Mixin Remote Control Pro")))
        assertFalse("fleet-leapmotor-digital-key" in hits(radio(name = "Leapmotor_DigitalKey_123")))
        assertFalse("fleet-ikf-king-pro" in hits(radio(name = "iKF-King")))
        val audio = catalog.single { it.id == "fleet-ikf-king-pro" }
        assertEquals(SignatureClass.AUDIO, audio.kind)
        assertTrue(DeviceExplain.guess(radio(name = "iKF-King Pro"), listOf(audio.name)).headline.contains("headphones"))
    }

    @Test fun lexRequiresAllIndependentSignalsAndDoesNotClaimLexus() {
        val d = radio(name = "LEX123", facts = RadioFacts(mfgRecords = listOf(MfgRecord(0x0759, "00"))))
            .copy(serviceUuids = listOf("5810BBC0-B499-11E9-A2A3-2A2AE2DBCCFF"))
        assertTrue("fleet-ingeek-lex" in hits(d))
        for (bad in listOf(d.copy(name = "OTHER"), d.copy(kind = RadioKind.WIFI), d.copy(serviceUuids = emptyList()),
            d.copy(facts = RadioFacts(mfgRecords = listOf(MfgRecord(0x0758, "00")))))) {
            assertFalse("fleet-ingeek-lex" in hits(bad))
        }
        val row = families.single { it.id == "fleet-ingeek-lex" }
        assertEquals(SignatureClass.OTHER, row.kind)
        assertFalse(row.notes.contains("Lexus", true))
        assertTrue(DeviceExplain.guess(d, listOf(row.name)).headline.contains("purpose unconfirmed"))
    }

    @Test fun registeredInterfacesUseWifiOnlyAndDoNotClaimAnApOrSetTopBox() {
        val vendors = families.filter { it.rules.all { r -> r.kind == RuleKind.OUI } }
        assertEquals(6, vendors.size)
        for (f in vendors) {
            assertEquals(SignatureClass.OTHER, f.kind)
            for (r in f.rules) {
                val d = radio(RadioKind.WIFI).copy(mac = r.text + ":00:00:01")
                assertTrue(r.text, f.id in hits(d))
                assertFalse(r.text, f.id in hits(d.copy(kind = RadioKind.BLE)))
                assertTrue(DeviceExplain.guess(d, listOf(f.name)).headline.contains("type unknown"))
            }
        }
        for ((prefix, id) in listOf("14:51:7E" to "h3c-wifi", "F0:0C:51" to "zte-wifi", "54:92:6A" to "midea-appliance")) {
            val d = radio(RadioKind.WIFI).copy(mac = "$prefix:00:00:01")
            assertTrue("fleet-$id" in hits(d))
            assertFalse("fleet-$id" in hits(d.copy(kind = RadioKind.BLE)))
        }
    }

    @Test fun airPlayReadsEndpointWithinItsTlvAndPortIsBigEndian() {
        val explicit = fields("09081330C0A801FD1B581608002C5EEDB811AFEC")
        assertEquals("192.168.1.253", explicit["AirPlay advertised IPv4"])
        assertEquals("7000", explicit["AirPlay advertised port"])
        assertFalse(explicit.containsKey("AirPlay default port (not advertised)"))
        assertEquals("4660", fields("090813300A0000021234")["AirPlay advertised port"])
        val old = fields("09060330C0A801FD1608002C5EEDB811AFEC")
        assertEquals("7000", old["AirPlay default port (not advertised)"])
        assertFalse(old.containsKey("AirPlay advertised port"))
        for (hex in listOf("09061330C0A801FD1608002C5EEDB811AFEC", "09080330C0A801FD1B58", "09051330C0A8011608002C5EEDB811AFEC", "09081330C0A801FD1B")) {
            assertFalse(hex, fields(hex).containsKey("AirPlay advertised IPv4"))
            assertFalse(hex, fields(hex).containsKey("AirPlay advertised port"))
            assertFalse(hex, AdvPayloadDecoder.roleHints(apple(hex)).any { it.label == "an AirPlay target" })
        }
        val hint = AdvPayloadDecoder.roleHints(apple("09081330C0A801FD1B58")).single()
        assertEquals("an AirPlay target", hint.label)
        assertFalse(hint.label.contains("Apple TV"))
        assertTrue(AdvPayloadDecoder.hasAppleContinuityType("09081330C0A801FD1B58", 0x09))
        assertTrue(AdvPayloadDecoder.hasAppleContinuityType("09060330C0A801FD", 0x09))
        assertFalse(AdvPayloadDecoder.hasAppleContinuityType("09061330C0A801FD1608002C5EEDB811AFEC", 0x09))
        assertFalse(AdvPayloadDecoder.hasAppleContinuityType("09081330C0A801FD1B5816", 0x09))
    }

    @Test fun awdlIsProtocolOnlyAndMatchingRejectsTruncatedRecordsAndWrongLengths() {
        val valid = "1608002C5EEDB811AFEC"
        for (hex in listOf(valid, "09060330C0A801FD" + valid, valid + "09081330C0A801FD1B58")) {
            assertTrue("fleet-apple-awdl" in hits(apple(hex)))
            assertEquals("2C5EEDB811AFEC", fields(hex)["AWDL message (raw)"])
            assertFalse(fields(hex).any { it.key.contains("Nearby Info") || it.key.contains("battery", true) || it.key.contains("activity", true) })
        }
        for (hex in listOf("1607002C5EEDB811AF", "1609002C5EEDB811AFEC00", "1608002C5EED", valid + "09", valid + "090813", "001608002C5EEDB811AFEC", "1608002C5EEDB811AFEZ")) {
            assertFalse(hex, "fleet-apple-awdl" in hits(apple(hex)))
        }
        assertFalse("fleet-apple-awdl" in hits(apple(valid, 0x004D)))
        assertFalse("fleet-apple-awdl" in hits(apple(valid, kind = RadioKind.WIFI)))
        assertFalse("fleet-apple-awdl" in hits(radio().copy(rawHex = valid)))
        val off = families.single { it.id == "fleet-apple-awdl" }.let { it.copy(rules = it.rules.map { r -> r.copy(enabled = false) }) }
        assertTrue(hits(apple(valid), listOf(off)).isEmpty())
        val packed = SignatureExchange.parse(SignatureExchange.encode(SignatureExchange.pack(listOf(off), 93, "test", "")))
        assertEquals(off.rules, packed.fleets.single().rules)
    }

    @Test fun wpsStandardAttributesPreserveUnknownBitsAndRejectBadLengths() {
        val hex = "104A00011010570001011041000100103C000187100800021234"
        val decoded = WifiWpsDecoder.decode(wps(hex))!!
        assertEquals(WifiWpsDecoder.Status.COMPLETE, decoded.status)
        val f = decoded.fields.associate { it.label to it.value }
        assertEquals("1.0", f["WPS attribute version (not firmware)"])
        assertEquals("yes", f["WPS AP setup locked"])
        assertEquals("no", f["WPS selected registrar"])
        assertEquals("2.4 GHz / 5 GHz / 60 GHz / 0x87", f["WPS RF bands"])
        assertEquals("0x1234", f["WPS configuration methods (advertised mask)"])
        for (type in listOf(0x104A, 0x1057, 0x1041, 0x103C, 0x1008)) {
            val bad = "%04X0000".format(type)
            assertEquals(WifiWpsDecoder.Status.MALFORMED, WifiWpsDecoder.decode(wps(bad))!!.status)
            assertEquals(WifiWpsDecoder.Status.TRUNCATED, WifiWpsDecoder.decode(wps("%04X000201".format(type)))!!.status)
        }
        val unknown = WifiWpsDecoder.decode(wps("105700010210410001FF103C000180"))!!.fields.associate { it.label to it.value }
        assertEquals("0x02", unknown["WPS AP setup locked"])
        assertEquals("0xFF", unknown["WPS selected registrar"])
        assertEquals("0x80", unknown["WPS RF bands"])
        assertNull(WifiWpsDecoder.identity(listOf(wps(hex))))
        val completeIdentity = "10210003414243102300034D4F44"
        assertNull(WifiWpsDecoder.identity(listOf(wps(completeIdentity + "104A0000"))))
    }

    @Test fun sharedDeviceDecodeAndReportExposeSameEndpointAndWpsValues() {
        val devices = listOf(apple("09081330C0A801FD1B581608002C5EEDB811AFEC"),
            radio(RadioKind.WIFI, facts = RadioFacts(vendorIes = listOf(wps("104A00011010570001011041000100103C000103100800020080")))))
        for (d in devices) {
            val fields = AdvPayloadDecoder.decodeDevice(d)
            val report = DeviceDetailText.build(d, emptyList(), now = 1000)
            assertTrue(fields.isNotEmpty())
            assertTrue(fields.all { report.contains("${it.label}: ${it.value}") })
            val marked = AdvPayloadDecoder.decodeDevice(d) { "translated:$it" }
            assertTrue(marked.all { it.label.startsWith("translated:") })
        }
    }

    @Test fun migrationPreservesOperatorChoicesAndIsIdempotent() {
        val base = catalog.single { it.id == "fleet-h3c-wifi" }
        val old = base.copy(name = "Operator name", notes = "Operator notes", enabled = false,
            rules = base.rules.map { if (it.text == "14:51:7E") it.copy(enabled = false) else it })
        val and = catalog.single { it.id == "fleet-zte-wifi" }.copy(matchAny = false, rules = emptyList())
        val custom = catalog.single { it.id == "fleet-midea-appliance" }.copy(builtIn = false, rules = emptyList())
        val existingNew = families.single { it.id == "fleet-apple-awdl" }.copy(enabled = false, name = "My AWDL")
        val result = ConfigStore.appendCatalogV93(listOf(old, and, custom, existingNew))
        assertEquals(listOf(old, and, custom, existingNew), result.take(4))
        assertEquals(1, result.count { it.id == existingNew.id })
        assertEquals(result, ConfigStore.appendCatalogV93(result))
        val needsOui = base.copy(rules = base.rules.filterNot { it.text == "14:51:7E" })
        assertEquals(base.rules.toSet(), ConfigStore.appendCatalogV93(listOf(needsOui)).first().rules.toSet())
        for ((id, prefix) in listOf("fleet-h3c-wifi" to "14:51:7E", "fleet-zte-wifi" to "F0:0C:51", "fleet-midea-appliance" to "54:92:6A")) {
            val deletedOuis = catalog.single { it.id == id }.let { f -> f.copy(rules = f.rules.filterNot { it.kind == RuleKind.OUI }) }
            val migrated = ConfigStore.appendCatalogV93(listOf(deletedOuis)).first()
            assertEquals(id, listOf(prefix), migrated.rules.filter { it.kind == RuleKind.OUI }.map { it.text })
            assertEquals(deletedOuis.rules, migrated.rules.filterNot { it.kind == RuleKind.OUI })
            assertEquals(migrated, ConfigStore.appendCatalogV93(listOf(migrated)).first())
        }
        assertEquals(101, ConfigStore.CATALOG_VERSION)
    }
}
