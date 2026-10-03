package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryV97Test {
    private val families = DefaultCatalog.discoveryFamiliesV97()

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

    @Test fun catalogVersionIs97() {
        assertEquals(100, ConfigStore.CATALOG_VERSION)
        assertTrue(families.isNotEmpty())
        families.forEach { fleet -> assertTrue(fleet.builtIn) }
    }

    @Test fun migrationAddsOnlyV97FamiliesAndIsIdempotent() {
        val custom = Fleet(id = "operator", name = "Operator row", builtIn = false)
        val result = ConfigStore.appendCatalogV97(listOf(custom))
        assertEquals(1, result.count { it.id == custom.id })
        assertEquals(families.map { it.id }.toSet(), result.drop(1).map { it.id }.toSet())
        assertEquals(result, ConfigStore.appendCatalogV97(result))
    }

    @Test fun v97RepairsTheEzvizCompanyIdOnTheDomesticRow() {
        // V96 shipped the big-endian misreading 182B; V97 must drop it and add 2B18 / 2B19.
        val v96Row = Fleet(id = "fleet-ezviz-device", name = "EZVIZ device", builtIn = true,
            rules = listOf(MatchRule(RuleKind.OUI, text = "94:EC:13"),
                MatchRule(RuleKind.MANUFACTURER_ID, companyId = 0x182B)))
        val migrated = ConfigStore.appendCatalogV97(listOf(v96Row)).first()
        val stock = stockFleet("fleet-ezviz-device")
        assertEquals(stock.rules.toSet(), migrated.rules.toSet())
        assertFalse(migrated.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x182B })
        assertTrue(migrated.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x2B18 })
        assertTrue(migrated.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x2B19 })
        assertEquals(migrated, ConfigStore.appendCatalogV97(listOf(migrated)).first())
    }

    @Test fun v97FamiliesCarryTheObservedFingerprints() {
        val oppo = stockFleet("fleet-oppo-device-beacon")
        assertTrue(oppo.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x079A })
        assertTrue(oppo.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text == "686B" && it.radio == RadioKind.BLE })
        val alipay = stockFleet("fleet-alipay-ble")
        assertTrue(alipay.rules.any { it.kind == RuleKind.NAME_GLOB && it.text == "BLE_DK_*" && it.radio == RadioKind.BLE })
        assertTrue(alipay.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text == "616C6970-6179-626F-7869-62656F706F6C" })
    }

    @Test fun v97FamiliesMatchTheCapturedFrames() {
        val engine = SignatureEngine()
        val all = DefaultCatalog.fleets()
        fun hits(device: Sighting): Set<String> = engine.match(listOf(device), all).values.single()

        // OPPO phone beacons: SIG company 079A plus the observed service 686B.
        val oppoA = sighting(RadioKind.BLE, "49:44:49:EF:6B:7B",
            mfg = 0x079A, mfgData = "AF302B1488004C2640000000", uuids = listOf("686B"))
        assertTrue("fleet-oppo-device-beacon" in hits(oppoA))
        val oppoB = sighting(RadioKind.BLE, "5B:B9:F4:2D:DE:3A",
            mfg = 0x079A, mfgData = "AF302B1488004C2640000000", uuids = listOf("686B"))
        assertTrue("fleet-oppo-device-beacon" in hits(oppoB))
        // Alipay BLE_DK radio: name and the ASCII-'alipay' 128-bit service UUID, either one.
        val alipay = sighting(RadioKind.BLE, "EB:6B:03:06:11:56", "BLE_DK_EB6B03061156",
            uuids = listOf("616C6970-6179-626F-7869-62656F706F6C"))
        assertTrue("fleet-alipay-ble" in hits(alipay))
        val alipayByNameOnly = sighting(RadioKind.BLE, "EB:6B:03:06:11:56", "BLE_DK_EB6B03061156")
        assertTrue("fleet-alipay-ble" in hits(alipayByNameOnly))
        val alipayByUuidOnly = sighting(RadioKind.BLE, "EB:6B:03:06:11:56",
            uuids = listOf("616C6970-6179-626F-7869-62656F706F6C"))
        assertTrue("fleet-alipay-ble" in hits(alipayByUuidOnly))
        // Unrelated radios are not pulled into the new families.
        val other = sighting(RadioKind.BLE, "51:3C:CB:DE:6C:76")
        assertTrue(!("fleet-oppo-device-beacon" in hits(other)))
        assertTrue(!("fleet-alipay-ble" in hits(other)))
    }
}
