package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryV99Test {
    private fun stockFleet(id: String) = DefaultCatalog.fleets().associateBy { it.id }.getValue(id)

    private fun sighting(kind: RadioKind, mac: String, name: String = "",
                         mfg: Int? = null, mfgData: String = "", uuids: List<String> = emptyList()
    ) = Sighting(
        key = "${kind.name}:${mac}", kind = kind, mac = mac, name = name,
        rssi = -60, rssiMin = -60, rssiMax = -60, channel = 0, frequencyMhz = 2412,
        vendor = null, randomized = false, hiddenSsid = kind == RadioKind.WIFI && name.isEmpty(),
        serviceUuids = uuids, manufacturerId = mfg, manufacturerDataHex = mfgData, rawHex = "", extras = "",
        firstSeen = 1L, lastSeen = 2L, hitCount = 1, fleetIds = emptySet(),
        rssiHistory = emptyList(), presence = emptyList(),
    )

    private fun hits(device: Sighting): Set<String> =
        SignatureEngine().match(listOf(device), DefaultCatalog.fleets()).values.single()

    @Test fun catalogVersionIs99() {
        assertEquals(101, ConfigStore.CATALOG_VERSION)
    }

    @Test fun v99DropsOverBroadCompanyIdsAndDeadUuidsOnInstalledRows() {
        val nusUuid = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
        val deadYadeaUuids = listOf(
            "E7810BD2-0000-1000-8000-00805F9B34FB",
            "D20B81E7-0000-1000-8000-00805F9B34FB",
            "E7810B92-0000-1000-8000-00805F9B34FB",
            "616C6970-6179-626F-7869-656C65626F706F6C",
        ).map { MatchRule(RuleKind.SERVICE_UUID, text = it, radio = RadioKind.BLE) }

        // Rows as they sit in a pre-V99 installed catalog: stock rules plus the
        // over-broad / dead rules V99 removes.
        val dplatform = stockFleet("fleet-dplatform-key").copy(
            rules = stockFleet("fleet-dplatform-key").rules +
                MatchRule(RuleKind.SERVICE_UUID, text = nusUuid, radio = RadioKind.BLE))
        val yadea = stockFleet("fleet-yadea-vehicle").copy(
            rules = stockFleet("fleet-yadea-vehicle").rules.filterNot { it.kind == RuleKind.MANUFACTURER_DATA } + deadYadeaUuids)
        val aima = stockFleet("fleet-aima-vehicle").copy(
            rules = stockFleet("fleet-aima-vehicle").rules + MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x01A8))
        val geely = stockFleet("fleet-geely-vehicle").copy(
            rules = stockFleet("fleet-geely-vehicle").rules + MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x01FE))
        val mtc = stockFleet("fleet-mtc-vehicle").copy(
            rules = listOf(MatchRule(RuleKind.NAME_GLOB, text = "MTC???????????", radio = RadioKind.BLE)))
        // An installed EZVIZ row still carrying the pre-V96 notes / rules.
        val ezvizLegacy = stockFleet("fleet-ezviz-device").copy(
            notes = "IEEE 94:EC:13 identifies an EZVIZ device family on Wi-Fi or a public stable BLE address. It does not prove the device is a camera.",
            rules = listOf(MatchRule(RuleKind.OUI, text = "94:EC:13")))

        val migrated = ConfigStore.appendCatalogV99(listOf(dplatform, yadea, aima, geely, mtc, ezvizLegacy))
        val byId = migrated.associateBy { it.id }
        for (id in listOf("fleet-dplatform-key", "fleet-yadea-vehicle", "fleet-aima-vehicle", "fleet-geely-vehicle", "fleet-mtc-vehicle", "fleet-ezviz-device")) {
            assertEquals("rules of $id", stockFleet(id).rules.toSet(), byId.getValue(id).rules.toSet())
        }
        assertEquals(stockFleet("fleet-ezviz-device").notes, byId.getValue("fleet-ezviz-device").notes)
        // Idempotent on a catalog that is already at V99 stock.
        assertEquals(migrated, ConfigStore.appendCatalogV99(migrated))
    }

    @Test fun v99RulesMatchCapturedFramesAndRejectLookalikes() {
        // A plain nRF dev board advertising the standard Nordic UART service is not a D-platform key.
        val devBoard = sighting(RadioKind.BLE, "00:11:22:00:00:0A", "nRF52840",
            uuids = listOf("6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))
        assertFalse("fleet-dplatform-key" in hits(devBoard))
        // Platform key names still hit.
        assertTrue("fleet-dplatform-key" in hits(sighting(RadioKind.BLE, "00:11:22:00:00:0B", "N9DAB12CJ3456")))

        // Yadea iBeacon firmware generation: the UUID lives in company-004C manufacturer data.
        val yadeaIb = sighting(RadioKind.BLE, "00:11:22:00:00:0C", "",
            mfg = 0x004C, mfgData = "0215E7810BD200001000800000805F9B34FB" + "0001" + "0002")
        assertTrue("fleet-yadea-vehicle" in hits(yadeaIb))
        // The same UUID written as a service UUID (the old broken rule shape) matches nothing.
        val yadeaSvc = sighting(RadioKind.BLE, "00:11:22:00:00:0D", "YD12345678",
            uuids = listOf("E7810BD2-0000-1000-8000-00805F9B34FB"))
        assertTrue("fleet-yadea-vehicle" in hits(yadeaSvc))

        // Bare Taobao-registered company id is not an Aima identifier; the B69E payload or the name is.
        assertFalse("fleet-aima-vehicle" in hits(sighting(RadioKind.BLE, "00:11:22:00:00:0E", "", mfg = 0x01A8, mfgData = "AABBCCDD")))
        assertTrue("fleet-aima-vehicle" in hits(sighting(RadioKind.BLE, "00:11:22:00:00:0F", "", mfg = 0x01A8, mfgData = "B69E1234")))
        assertTrue("fleet-aima-vehicle" in hits(sighting(RadioKind.BLE, "00:11:22:00:00:10", "AIMA-1234")))

        // Bare Radio Systems company id is not a Geely identifier; the name is.
        assertFalse("fleet-geely-vehicle" in hits(sighting(RadioKind.BLE, "00:11:22:00:00:11", "", mfg = 0x01FE, mfgData = "AABB")))
        assertTrue("fleet-geely-vehicle" in hits(sighting(RadioKind.BLE, "00:11:22:00:00:12", "GeelyVehicleA1B2")))

        // MTC + 12-hex code from the notes matches its own sample (the old glob was one char short).
        assertTrue("fleet-mtc-vehicle" in hits(sighting(RadioKind.BLE, "00:11:22:00:00:13", "MTC71EFA72D8B1A")))
    }
}
