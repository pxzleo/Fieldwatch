package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryV96Test {
    private val families = DefaultCatalog.discoveryFamiliesV96()

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

    @Test fun catalogVersionIsCurrent() {
        assertEquals(98, ConfigStore.CATALOG_VERSION)
        assertTrue(families.isNotEmpty())
        families.forEach { fleet -> assertTrue(fleet.builtIn) }
    }

    @Test fun migrationAddsOnlyV96FamiliesAndIsIdempotent() {
        val custom = Fleet(id = "operator", name = "Operator row", builtIn = false)
        val result = ConfigStore.appendCatalogV96(listOf(custom))
        assertEquals(1, result.count { it.id == custom.id })
        assertEquals(families.map { it.id }.toSet(), result.drop(1).map { it.id }.toSet())
        assertEquals(result, ConfigStore.appendCatalogV96(result))
    }

    @Test fun v96SyncsStockRulesOntoSkippedAndExtendedRows() {
        val stock = DefaultCatalog.fleets().associateBy { it.id }
        val mercury = stock.getValue("fleet-mercury-wifi").copy(
            rules = listOf(MatchRule(RuleKind.NAME_GLOB, text = "MERCURY*", radio = RadioKind.WIFI)))
        val ezviz = stock.getValue("fleet-ezviz-device").copy(rules = emptyList())
        val haier = stock.getValue("fleet-haier-radio").copy(rules = emptyList())
        val miLock = stock.getValue("fleet-mi-lock").copy(rules = emptyList())
        val migrated = ConfigStore.appendCatalogV96(listOf(mercury, ezviz, haier, miLock))
        val byId = migrated.associateBy { it.id }
        assertEquals(stock.getValue("fleet-mercury-wifi").rules.toSet(), byId.getValue("fleet-mercury-wifi").rules.toSet())
        assertEquals(stock.getValue("fleet-ezviz-device").rules.toSet(), byId.getValue("fleet-ezviz-device").rules.toSet())
        assertEquals(stock.getValue("fleet-haier-radio").rules.toSet(), byId.getValue("fleet-haier-radio").rules.toSet())
        assertEquals(stock.getValue("fleet-mi-lock").rules.toSet(), byId.getValue("fleet-mi-lock").rules.toSet())
        assertEquals(migrated, ConfigStore.appendCatalogV96(migrated))
    }

    @Test fun v96RowsCarryTheObservedFingerprints() {
        val nuoxc = stockFleet("fleet-nuoxc-wifi")
        assertTrue(nuoxc.rules.any { it.kind == RuleKind.OUI && it.text == "68:89:75" && it.radio == RadioKind.WIFI })
        assertTrue(nuoxc.rules.any { it.kind == RuleKind.OUI && it.text == "6A:89:75" && it.radio == RadioKind.WIFI })
        val mercury = stockFleet("fleet-mercury-wifi")
        assertTrue(mercury.rules.any { it.kind == RuleKind.MAC_PREFIX && it.text == "BC:54:FC" && it.radio == RadioKind.WIFI })
        val ezviz = stockFleet("fleet-ezviz-device")
        assertTrue(ezviz.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x2B18 })
        assertTrue(ezviz.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x2B19 })
        assertFalse(ezviz.rules.any { it.kind == RuleKind.MANUFACTURER_ID && it.companyId == 0x182B })
        val haier = stockFleet("fleet-haier-radio")
        assertTrue(haier.rules.any { it.kind == RuleKind.OUI && it.text == "04:39:CB" })
        val miLock = stockFleet("fleet-mi-lock")
        assertTrue(miLock.rules.any { it.kind == RuleKind.MIBEACON_PRODUCT_ID && it.text == "1B01" })
        assertTrue(miLock.rules.any { it.kind == RuleKind.NAME_CONTAINS && it.text == "Mi Automatic Smart Door Lock" })
        val huawei = stockFleet("fleet-huawei-superdevice")
        assertTrue(huawei.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text == "FDEE" && it.radio == RadioKind.BLE })
        val xiaomi = stockFleet("fleet-xiaomi-find-beacon")
        assertTrue(xiaomi.rules.any { it.kind == RuleKind.SERVICE_UUID && it.text == "FCC0" && it.radio == RadioKind.BLE })
    }

    @Test fun v96FamiliesMatchTheCapturedFrames() {
        val engine = SignatureEngine()
        val all = DefaultCatalog.fleets()
        fun hits(device: Sighting): Set<String> = engine.match(listOf(device), all).values.single()

        // NuoXc hidden dual-radio AP (one of the pair, the other prefix is symmetric).
        val nuoxc = sighting(RadioKind.WIFI, "68:89:75:0F:39:9A")
        assertTrue("fleet-nuoxc-wifi" in hits(nuoxc))
        val nuoxcAlt = sighting(RadioKind.WIFI, "6A:89:75:0C:39:9A")
        assertTrue("fleet-nuoxc-wifi" in hits(nuoxcAlt))
        // Mercury router renamed to a phone number.
        assertTrue("fleet-mercury-wifi" in hits(sighting(RadioKind.WIFI, "4C:77:66:22:8D:7A", "133411864452")))
        assertTrue("fleet-mercury-wifi" in hits(sighting(RadioKind.WIFI, "BC:54:FC:AF:DF:1A", "133411864452")))
        // EZVIZ BLE radio: primary company 2B19 plus the 2B18 block with the ASCII EZVIZ marker.
        val ezviz = sighting(RadioKind.BLE, "7A:76:30:DD:C6:34", "BD2260002",
            mfg = 0x2B19, mfgData = "0106626C3730326C")
        assertTrue("fleet-ezviz-device" in hits(ezviz))
        // Haier air-conditioner unit: registered 04:39:CB prefix on a stable address.
        val ac = sighting(RadioKind.BLE, "04:39:CB:49:E5:03", "U-ACGE502", uuids = listOf("FF01", "FF02"))
        assertTrue("fleet-haier-radio" in hits(ac))
        // The same name on an unregistered address is not assigned to Haier.
        assertTrue(!("fleet-haier-radio" in hits(sighting(RadioKind.BLE, "00:11:22:00:00:01", "U-ACGE502"))))
        // Mi Automatic Smart Door Lock: classic company-004C v3 MiBeacon, product 1B01.
        val lock = sighting(RadioKind.BLE, "ED:A3:84:1A:05:0E", "Mi Automatic Smart Door Lock",
            mfg = 0x004C, mfgData = "0631011BD242E6BC4A0600010001024C0DD969")
        assertTrue("fleet-mi-lock" in hits(lock))
        // Huawei Super Device beacon (SIG-assigned FDEE) and Xiaomi phone beacon (SIG-assigned FCC0).
        assertTrue("fleet-huawei-superdevice" in hits(
            sighting(RadioKind.BLE, "58:7C:92:B5:F8:D0", mfg = 0x027D, mfgData = "3FE800000513FFE9CE08080000E10283E9EF",
                uuids = listOf("FDEE"))))
        assertTrue("fleet-xiaomi-find-beacon" in hits(
            sighting(RadioKind.BLE, "7A:39:AC:EF:EA:19", uuids = listOf("FCC0"))))
        // Name-based home rows.
        assertTrue("fleet-eg-ac-ble" in hits(sighting(RadioKind.BLE, "04:F4:D8:1B:88:2E", "eg_ac_hanging", mfg = 0x3838)))
        assertTrue("fleet-cmcc-cpe" in hits(sighting(RadioKind.WIFI, "F8:4E:33:38:89:20", "CMCC-Xiao-Apple")))
        assertTrue("fleet-smart-tv-ap" in hits(sighting(RadioKind.WIFI, "04:3D:98:E7:64:CC", "SmartTVAP")))
        assertTrue("fleet-ziroom-iot" in hits(sighting(RadioKind.WIFI, "30:AE:7B:E7:CA:17", "ziroom421")))
        assertTrue("fleet-ziroom-iot" in hits(sighting(RadioKind.WIFI, "32:AE:7B:E7:CA:17")))
        // Cellular IoT radio named by SIM ICCID.
        assertTrue("fleet-cellular-iccid-iot" in hits(sighting(RadioKind.BLE, "EC:B1:AC:01:15:A4", "BT_866374068393406",
            mfg = 0xB1EC, mfgData = "AC0115A4")))
        // Bike rows.
        assertTrue("fleet-jida-bike" in hits(sighting(RadioKind.BLE, "2B:81:1E:3A:CD:A0", "jida_bike",
            mfg = 0x1010, mfgData = "5501062B811E3ACDA0550200000000000000", uuids = listOf("FFF0"))))
        assertTrue("fleet-xd-ebike" in hits(sighting(RadioKind.BLE, "22:34:50:19:58:ED", "xcdcJ28000032190",
            mfg = 0x4458, mfgData = "2234501958ED", uuids = listOf("FFE0"))))
        // A Huawei FDEE radio must not double-label as a Xiaomi beacon or vice versa.
        assertTrue("fleet-huawei-superdevice" !in hits(
            sighting(RadioKind.BLE, "7A:39:AC:EF:EA:19", uuids = listOf("FCC0"))))
        assertTrue("fleet-xiaomi-find-beacon" !in hits(
            sighting(RadioKind.BLE, "58:7C:92:B5:F8:D0", mfg = 0x027D, uuids = listOf("FDEE"))))
    }
}
