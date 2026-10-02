package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import org.junit.Assert.*
import org.junit.Test

class DiscoveryV90Test {
    private val catalog = DefaultCatalog.fleets()
    private val engine = SignatureEngine()
    private fun radio(kind: RadioKind, name: String = "", mac: String = "00:11:22:00:00:01") = Sighting(
        key = "$kind:$mac", kind = kind, mac = mac, name = name, rssi = -60, rssiMin = -60, rssiMax = -60,
        channel = 0, frequencyMhz = 0, vendor = null, randomized = false, hiddenSsid = false,
        serviceUuids = emptyList(), manufacturerId = null, manufacturerDataHex = "", rawHex = "", extras = "",
        firstSeen = 1000, lastSeen = 1000, hitCount = 1, fleetIds = emptySet(), rssiHistory = emptyList(), presence = emptyList(),
    )
    private fun hits(r: Sighting) = engine.match(listOf(r), catalog, 1000).getValue(r.key)

    @Test fun mercuryMatchesNamesWithoutAbsorbingOtherBrandsOrBle() {
        assertTrue("fleet-mercury-wifi" in hits(radio(RadioKind.WIFI, "MERCURY_5G_ABCD")))
        val xiaomi = radio(RadioKind.WIFI, "Xiaomi_test", "EC:41:18:00:00:01")
            .copy(vendorIeOuis = listOf("00:0C:43", "00:0C:E7", "68:7A:0A"))
        assertFalse("fleet-mercury-wifi" in hits(xiaomi))
        assertFalse("fleet-mercury-wifi" in hits(radio(RadioKind.BLE, "MERCURY_ABCD")))
    }

    @Test fun romoSuppressesGenericDjiAircraftButDoesNotSuppressActualDji() {
        val romo = radio(RadioKind.BLE, "ROMO-example").copy(manufacturerId = 0x08AA, manufacturerDataHex = "3412")
        assertTrue("fleet-dji-romo" in hits(romo))
        assertFalse("fleet-dji" in hits(romo))
        assertTrue("fleet-dji" in hits(romo.copy(name = "DJI-example")))
        assertFalse("fleet-dji-romo" in hits(romo.copy(kind = RadioKind.WIFI)))
        val names = catalog.filter { it.id in hits(romo) }.map { it.name }
        assertTrue(DeviceExplain.guess(romo, names).headline.contains("ROMO"))
    }

    @Test fun mobikeMatchesEachDedicatedIdentifierAndKeepsGenericUuidsUnknown() {
        val base = radio(RadioKind.BLE)
        for (r in listOf(base.copy(name = "mobike"), base.copy(manufacturerId = 0x04B3),
            base.copy(serviceUuids = listOf("A000FAA0-0047-005A-0052-6D6F62696B65")))) {
            assertTrue("fleet-mobike" in hits(r))
            assertFalse("fleet-mobike" in hits(r.copy(kind = RadioKind.WIFI)))
        }
        assertFalse("fleet-mobike" in hits(base.copy(serviceUuids = listOf("1812", "FEE7"))))
    }

    @Test fun mercuryMigrationRetainsMetadataIsIdempotentAndLeavesOtherCustomRulesAlone() {
        val name = MatchRule(RuleKind.NAME_GLOB, text = "MERCURY*", radio = RadioKind.WIFI)
        val candidate = Fleet(id = "operator-mercury", name = "MERCURY", builtIn = false, enabled = false,
            notes = "Shared on-air ID, not a one-radio MAC.", rules = listOf(name,
                MatchRule(RuleKind.OUI, text = "EC:41:18", radio = RadioKind.WIFI)))
        val other = candidate.copy(id = "other", name = "Other")
        val migrated = ConfigStore.appendCatalogV90(listOf(candidate, other))
        assertEquals(candidate.copy(rules = listOf(name)), migrated.first())
        assertEquals(other, migrated[1])
        assertFalse(migrated.any { it.id == "fleet-mercury-wifi" })
        assertEquals(migrated, ConfigStore.appendCatalogV90(migrated))
        // The stock pack may already have raised the version before the APK is upgraded.
        assertEquals(listOf(candidate.copy(rules = listOf(name)), other),
            ConfigStore.repairMercuryCandidates(listOf(candidate, other)))
        assertEquals(migrated, ConfigStore.repairMercuryCandidates(migrated))
        assertEquals(1, ConfigStore.appendCatalogV90(emptyList()).count { it.id == "fleet-mercury-wifi" })
    }

    @Test fun nameCandidatesKeepSharedChipsetIdentifiersSeparate() {
        val probes = listOf("MERCURY_ABCD", "MERCURY_EFAB", "Xiaomi_example").mapIndexed { i, n ->
            LogRadio(RadioKind.WIFI, "00:11:22:00:00:0$i", n, "Router vendor", null, "", emptyList(),
                listOf("00:0C:43"), false, false, -60, 1000, 1000, 1)
        }
        val report = SignatureCandidates.analyze(probes, emptyList(), engine)
        val mercury = report.families.single { it.rules.any { r -> r.kind == RuleKind.NAME_GLOB && r.text == "MERCURY*" } }
        assertEquals(2, mercury.distinctRadios)
        assertTrue(mercury.rules.all { it.kind == RuleKind.NAME_GLOB })
    }

    @Test fun modelIdsRemainReadableWithoutDecodingEncryptedMeasurements() {
        for ((pid, model) in listOf("D930" to "MJTZC01YM", "3228" to "MJWSD05MMC", "B555" to "MJWSD06MMC",
            "390E" to "XMZNMS08LM", "6308" to "SJWS01LM")) {
            val decoded = MiBeaconDecoder.decode("4850${pid}001122334455667788")
            assertTrue(decoded.validHeader)
            assertTrue(decoded.fields.any { it.label == "MiBeacon product ID" && model in it.value })
            assertFalse(decoded.fields.any { it.label in listOf("MiBeacon temperature", "MiBeacon battery", "MiBeacon humidity") })
        }
        assertEquals("0x1234", MiBeaconDecoder.decode("0050341200").fields.first { it.label == "MiBeacon product ID" }.value)
    }
}
