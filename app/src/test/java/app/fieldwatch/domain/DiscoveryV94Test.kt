package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import org.junit.Assert.*
import org.junit.Test

class DiscoveryV94Test {
    private val families = DefaultCatalog.discoveryFamiliesV94()
    private val engine = SignatureEngine()
    private val beacon = "172B01030102030405060708010203041112131415161718"
    private fun radio(kind: RadioKind = RadioKind.BLE, name: String = "", mac: String = "00:11:22:00:00:01") = Sighting(
        key = "$kind:$mac", kind = kind, mac = mac, name = name, rssi = -60, rssiMin = -60, rssiMax = -60,
        channel = 0, frequencyMhz = 0, vendor = null, randomized = false, hiddenSsid = false,
        serviceUuids = emptyList(), manufacturerId = null, manufacturerDataHex = "", rawHex = "", extras = "",
        firstSeen = 1000, lastSeen = 1000, hitCount = 1, fleetIds = emptySet(), rssiHistory = emptyList(), presence = emptyList(),
    )
    private fun hits(d: Sighting, fleets: List<Fleet> = families) = engine.match(listOf(d), fleets, 1000).getValue(d.key)

    @Test fun ecosystemAndSdkEvidenceStaysWithinBleAndPurposeBoundaries() {
        assertEquals(14, families.size)
        for (id in listOf(0x0F1F, 0x424E, 0x434E)) {
            val d = radio().copy(manufacturerId = id)
            assertTrue("fleet-ninebot-ecosystem" in hits(d))
            assertFalse("fleet-ninebot-ecosystem" in hits(d.copy(kind = RadioKind.WIFI)))
        }
        val sdk = radio().copy(manufacturerId = 0x05D6, manufacturerDataHex = "08004A4C414953444B00", serviceUuids = listOf("AF30"))
        assertTrue("fleet-jieli-sdk" in hits(sdk))
        for (d in listOf(sdk.copy(manufacturerId = 0x05D5), sdk.copy(serviceUuids = emptyList()), sdk.copy(manufacturerDataHex = "004A4C414953444B"), sdk.copy(kind = RadioKind.WIFI)))
            assertFalse("fleet-jieli-sdk" in hits(d))
        assertTrue("fleet-ble-joy-r" in hits(radio(name = "BLE_Joy_R")))
        assertFalse("fleet-ble-joy-r" in hits(radio(name = "BLE_Joy_R Pro")))
        assertFalse("fleet-ble-joy-r" in hits(radio(RadioKind.WIFI, "BLE_Joy_R")))
        val google = radio().copy(serviceUuids = listOf("FCF1"))
        assertEquals(setOf("fleet-google-fcf1"), hits(google))
        assertFalse("fleet-google-fcf1" in hits(google.copy(kind = RadioKind.WIFI)))
        assertEquals(SignatureClass.OTHER, families.single { it.id == "fleet-google-fcf1" }.kind)
    }

    @Test fun accessPointNamesAndPlatformIesAreScopedPassiveClaims() {
        for ((name, id) in listOf("CMCC-A1B2-5G" to "cmcc-ap-name", "CU_Test" to "unicom-ap-name", "ChinaUnicom-MESH-Test" to "unicom-ap-name", "DIRECT-abXMSv1" to "wifi-direct-name")) {
            assertTrue(name, "fleet-$id" in hits(radio(RadioKind.WIFI, name)))
            assertFalse(name, "fleet-$id" in hits(radio(name = name)))
        }
        assertFalse("fleet-cmcc-ap-name" in hits(radio(RadioKind.WIFI, "CMCC-ABC-5G")))
        assertFalse("fleet-wifi-direct-name" in hits(radio(RadioKind.WIFI, "DIRECT-a")))
        for ((oui, id) in listOf("88:12:4E" to "qualcomm-platform-ie", "8C:FD:F0" to "qualcomm-platform-ie", "00:E0:4C" to "realtek-platform-ie")) {
            val d = radio(RadioKind.WIFI).copy(vendorIeOuis = listOf(oui))
            assertEquals(setOf("fleet-$id"), hits(d))
            assertTrue(hits(d.copy(kind = RadioKind.BLE)).isEmpty())
            assertTrue(hits(radio(RadioKind.WIFI, mac = "$oui:00:00:01")).isEmpty())
            assertTrue(families.single { it.id == "fleet-$id" }.notes.contains("does not establish"))
        }
    }

    @Test fun strictSupplierMacPrefixesDoNotRecoverLocalBitsInFastOrSlowPaths() {
        for ((prefix, id) in listOf("00:25:5C" to "nec-interface", "88:6E:DD" to "micronet-interface", "E4:67:1E" to "nuoxin-interface", "3C:CB:01" to "lingji-interface")) {
            val stock = families.single { it.id == "fleet-$id" }
            val exact = radio(RadioKind.WIFI, mac = "$prefix:00:00:01")
            val local = exact.copy(mac = "%02X".format(prefix.take(2).toInt(16) or 2) + exact.mac.drop(2))
            for (family in listOf(stock, stock.copy(matchAny = false))) {
                assertTrue(id, family.id in hits(exact, listOf(family)))
                assertTrue(id, hits(local, listOf(family)).isEmpty())
                assertTrue(id, hits(exact.copy(kind = RadioKind.BLE), listOf(family)).isEmpty())
            }
            assertTrue(stock.rules.all { it.kind == RuleKind.MAC_PREFIX && it.radio == RadioKind.WIFI })
            assertTrue(stock.notes.contains("model are unconfirmed"))
        }
        val local = radio(RadioKind.WIFI, mac = "8A:6E:DD:00:00:01").copy(vendorIeOuis = listOf("00:E0:4C"))
        assertEquals(setOf("fleet-realtek-platform-ie"), hits(local))
        val oldOui = Fleet(id = "old", name = "old", rules = listOf(MatchRule(RuleKind.OUI, text = "88:6E:DD", radio = RadioKind.WIFI)))
        assertEquals(setOf("old"), hits(local, listOf(oldOui)))
    }

    @Test fun meshRequiresCompleteAdChainAndLegalSecureBeacon() {
        val valid = radio().copy(rawHex = "020106" + beacon + "000000")
        assertTrue("fleet-bluetooth-mesh-beacon" in hits(valid))
        assertTrue(hits(valid.copy(kind = RadioKind.WIFI)).isEmpty())
        for (raw in listOf(beacon.dropLast(2), beacon + "02FF", beacon + "00FF", beacon + "GG", "2B" + beacon.drop(4), beacon.replace("172B01", "172B00"), beacon.replace("0103", "0104"), "182B" + beacon.drop(4), "16FF" + beacon.drop(4)))
            assertTrue(raw, AdvPayloadDecoder.meshSecureBeacons(raw).isEmpty())
        val fake = radio().copy(manufacturerId = 1, manufacturerDataHex = beacon)
        assertTrue(hits(fake).isEmpty())
        val fields = AdvPayloadDecoder.decodeDevice(valid).associate { it.label to it.value }
        assertEquals("0102030405060708", fields["Mesh Network ID"])
        assertEquals("16909060", fields["Mesh IV Index"])
        assertEquals("03", fields["Mesh flags (raw)"])
        assertEquals("1112131415161718", fields["Mesh authentication (raw, unverified)"])
        val report = DeviceDetailText.build(valid, emptyList(), now = 1000)
        assertTrue(fields.all { report.contains("${it.key}: ${it.value}") })
        val rule = MatchRule(RuleKind.BLE_MESH_BEACON, text = "00")
        assertTrue(hits(valid, listOf(Fleet("bad", "bad", rules = listOf(rule)))).isEmpty())
        val family = families.single { it.id == "fleet-bluetooth-mesh-beacon" }
        val roundtrip = SignatureExchange.parse(SignatureExchange.encode(SignatureExchange.pack(listOf(family), 94, "test", "")))
        assertEquals(family.rules, roundtrip.fleets.single().rules)
    }

    @Test fun replayPreservesActualFullRawThroughJsonCsvAndMerge() {
        val json = """{"kind":"BLE","mac":"00:11:22:00:00:01","ts":1,"raw":"FFFF","raw_hex":"$beacon"}"""
        val csv = LogReplay.jsonRowToCsv(json)!!
        val converted = LogReplay.csvRowToJson(csv)!!
        assertEquals(beacon, LogReplay.parse(converted).single().rawHex)
        assertEquals(beacon, LogReplay.parse(LogReplay.DEFAULT_CSV_HEADER.joinToString(",") + "\n" + csv).single().rawHex)
        val blank = json.replace(beacon, "")
        assertEquals(beacon, LogReplay.parse(json + "\n" + blank).single().rawHex)
        assertEquals("", LogReplay.parse(json.replace("\"raw_hex\":\"$beacon\"", "\"unused\":\"\"")).single().rawHex)
    }

    @Test fun migrationAddsOnlyV94AndPreservesDisabledCustomAndDeletedOldRules() {
        assertEquals(100, ConfigStore.CATALOG_VERSION)
        val stock = DefaultCatalog.fleets().single { it.id == "fleet-mercury-wifi" }
        val disabled = stock.copy(enabled = false, rules = stock.rules.map { it.copy(enabled = false) })
        val and = stock.copy(id = "and", matchAny = false, rules = emptyList())
        val custom = stock.copy(id = "custom", builtIn = false, rules = emptyList())
        val edited = families.first().copy(enabled = false, notes = "Operator notes", rules = emptyList())
        val initial = listOf(disabled, and, custom, edited)
        val migrated = ConfigStore.appendCatalogV94(initial)
        assertEquals(initial, migrated.take(4))
        assertEquals(migrated, ConfigStore.appendCatalogV94(migrated))
        for (row in listOf(stock.copy(matchAny = false, rules = emptyList()), stock.copy(builtIn = false, rules = emptyList())))
            assertEquals(row, ConfigStore.appendCatalogV94(listOf(row)).first())
        val deleted = stock.copy(rules = emptyList(), name = "Operator name", enabled = false)
        val added = ConfigStore.appendCatalogV94(listOf(deleted)).first()
        assertEquals(listOf(MatchRule(RuleKind.MAC_PREFIX, text = "4C:77:66", radio = RadioKind.WIFI)), added.rules)
        assertEquals(deleted.name, added.name)
        assertFalse(added.enabled)
        assertEquals(added, ConfigStore.patchBuiltInRules(listOf(added), DefaultCatalog.fleets().associateBy { it.id }).single())
        assertTrue(hits(radio(RadioKind.WIFI, mac = "4C:77:66:00:00:01"), listOf(stock)).contains(stock.id))
        assertTrue(hits(radio(RadioKind.WIFI, mac = "4E:77:66:00:00:01"), listOf(stock)).isEmpty())
    }
}
