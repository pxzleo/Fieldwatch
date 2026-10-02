package app.fieldwatch.domain

import org.junit.Assert.*
import org.junit.Test

class DomesticCatalogTest {
    private val families = DefaultCatalog.domesticFamilies()
    private val engine = SignatureEngine()
    private fun hits(device: Sighting, fleets: List<Fleet> = families): Set<String> =
        engine.match(listOf(device), fleets)[device.key].orEmpty()

    @Test fun domesticAdvertisedNamesMatchOnlyTheirRadioScope() {
        val cases = listOf(
            Triple("H3C_sample", RadioKind.WIFI, "fleet-h3c-wifi"),
            Triple("XiaoMi_sample", RadioKind.WIFI, "fleet-xiaomi-wifi"),
            Triple("LYWSD03MMC", RadioKind.BLE, "fleet-lywsd03mmc"),
            Triple("MI_SCALE", RadioKind.BLE, "fleet-mi-scale"),
            Triple("lumi.switch.acn018", RadioKind.BLE, "fleet-lumi-aqara-switch"),
            Triple("midea_appliance", RadioKind.WIFI, "fleet-midea-appliance"),
            Triple("midea", RadioKind.BLE, "fleet-midea-appliance"),
            Triple("LuYuan-Smart", RadioKind.BLE, "fleet-luyuan-smart"),
            Triple("AIMA-SAMPLE", RadioKind.BLE, "fleet-aima-vehicle"),
            Triple("hellobike", RadioKind.BLE, "fleet-hellobike"),
            Triple("EDIFIER BLE", RadioKind.BLE, "fleet-edifier-ble"),
            Triple("Keep_CC_sample", RadioKind.BLE, "fleet-keep-cc"),
            Triple("Daikin!sample", RadioKind.WIFI, "fleet-daikin-wifi"),
        )
        cases.forEach { (name, kind, id) ->
            assertTrue("$name / $kind", id in hits(radio(kind, name)))
            if (id != "fleet-midea-appliance") {
                val other = if (kind == RadioKind.WIFI) RadioKind.BLE else RadioKind.WIFI
                assertFalse("$name / $other", id in hits(radio(other, name)))
            }
        }
        listOf("CU_test", "CMCC_test", "ChinaNet_test", "RZ_test", "YD_test", "U-GW_test", "my H3C_sample", "nothellobike").forEach {
            assertTrue(it, hits(radio(RadioKind.WIFI, it)).isEmpty())
            assertTrue(it, hits(radio(RadioKind.BLE, it)).isEmpty())
        }
    }

    @Test fun ecosystemServicesDoNotClaimManufacturerAndNeverMatchWifi() {
        val fe95 = radio(RadioKind.BLE).copy(serviceUuids = listOf("0000fe95-0000-1000-8000-00805f9b34fb"))
        val mfg = radio(RadioKind.BLE).copy(facts = RadioFacts(mfgRecords = listOf(MfgRecord(0x038F, "00"))))
        for (device in listOf(fe95, mfg)) {
            assertTrue("fleet-mibeacon-ecosystem" in hits(device))
            assertFalse("fleet-mibeacon-ecosystem" in hits(device.copy(kind = RadioKind.WIFI)))
        }
        val guess = DeviceExplain.guess(fe95, listOf("MiBeacon ecosystem"))
        assertTrue(guess.headline.contains("MiBeacon ecosystem"))
        assertFalse(guess.headline.contains("Xiaomi"))
        assertTrue(guess.because.contains("partner manufacturers"))
    }

    @Test fun ouiFamiliesAreGenericAndEcosystemDoesNotMaskSpecificName() {
        assertTrue("fleet-h3c-wifi" in hits(radio(RadioKind.WIFI, mac = "04:D7:A5:00:00:01")))
        assertTrue("fleet-xiaomi-wifi" in hits(radio(RadioKind.WIFI, mac = "EC:41:18:00:00:01")))
        val ezviz = radio(RadioKind.BLE, mac = "94:EC:13:00:00:01")
        assertTrue("fleet-ezviz-device" in hits(ezviz))
        assertTrue("fleet-ezviz-device" in hits(ezviz.copy(kind = RadioKind.WIFI)))
        val guess = DeviceExplain.guess(ezviz, listOf("EZVIZ device"))
        assertTrue(guess.headline.contains("EZVIZ device"))
        assertFalse(guess.headline.contains("camera", true))
        assertTrue(guess.because.contains("does not prove"))
        val sensor = DeviceExplain.guess(radio(RadioKind.BLE, "LYWSD03MMC"), listOf("MiBeacon ecosystem", "LYWSD03MMC thermometer"))
        assertTrue(sensor.headline.contains("LYWSD03MMC"))
    }

    @Test fun randomAnonymousAndFallbackLocalAddressesCannotMatchOui() {
        val public = radio(RadioKind.BLE, mac = "94:EC:13:00:00:01")
        val local = public.copy(mac = "96:EC:13:00:00:01")
        val shortOui = Fleet(id = "short", name = "Short OUI", rules = listOf(MatchRule(RuleKind.OUI, text = "94:EC:13")))
        val longOui = Fleet(id = "long", name = "Long OUI", rules = listOf(MatchRule(RuleKind.OUI, text = "94:EC:13:00")))
        val all = shortOui.copy(id = "all", matchAny = false)
        val mac = Fleet(id = "mac", name = "MAC prefix", rules = listOf(MatchRule(RuleKind.MAC_PREFIX, text = "94:EC:13")))
        val longMac = Fleet(id = "long-mac", name = "Long MAC prefix", rules = listOf(MatchRule(RuleKind.MAC_PREFIX, text = "94:EC:13:00")))
        val mixed = shortOui.copy(id = "mixed", rules = shortOui.rules + mac.rules)
        val fleets = listOf(shortOui, longOui, all, mac, longMac, mixed)
        assertEquals(fleets.map { it.id }.toSet(), hits(public, fleets))
        for (device in listOf(public.copy(randomized = true), public.copy(facts = RadioFacts(addressType = "Random")),
            public.copy(facts = RadioFacts(addressType = "Anonymous")))) {
            assertEquals(setOf("mac", "long-mac", "mixed"), hits(device, fleets))
        }
        val localOui = shortOui.copy(rules = listOf(MatchRule(RuleKind.OUI, text = "96:EC:13")))
        assertTrue(hits(local, listOf(localOui)).isEmpty())
        assertTrue("short" in hits(public.copy(facts = RadioFacts(addressType = "Public")), listOf(shortOui)))
        assertTrue("short" in hits(public.copy(kind = RadioKind.WIFI, randomized = true), listOf(shortOui)))
    }

    @Test fun scaleServicesAreBrandNeutralAndFcf1IsNotFinderProof() {
        for ((uuid, expected) in listOf("181D" to "weight scale", "181B" to "body-composition scale")) {
            val device = radio(RadioKind.BLE).copy(serviceUuids = listOf(uuid))
            val guess = DeviceExplain.guess(device, emptyList())
            assertTrue(guess.headline, guess.headline.contains(expected))
            assertTrue(guess.because.contains("does not specify a brand"))
            assertTrue(DeviceExplain.uuidGloss(uuid)!!.contains("brand not specified"))
        }
        val fcf1 = radio(RadioKind.BLE).copy(serviceUuids = listOf("FCF1"))
        assertFalse(DeviceExplain.guess(fcf1, emptyList()).headline.contains("Find Hub"))
        assertTrue(DeviceExplain.uuidGloss("FCF1")!!.contains("Google vendor service"))
        val scale = DeviceExplain.guess(radio(RadioKind.BLE).copy(serviceUuids = listOf("181D")), listOf("MI_SCALE scale"))
        assertTrue(scale.headline.contains("Xiaomi ecosystem scale"))
    }

    @Test fun domesticLabelsReasonsAndGlossesUseTranslationCallback() {
        val called = mutableListOf<String>()
        val translate: (String) -> String = { called += it; "译:$it" }
        families.forEach { fleet ->
            val guess = DeviceExplain.guess(radio(RadioKind.BLE), listOf(fleet.name), translate)
            assertTrue(fleet.name, guess.headline.contains("译:"))
            assertTrue(fleet.name, guess.because.contains("译:"))
            assertFalse("raw catalog name is not a translation key: ${fleet.name}", fleet.name in called)
        }
        assertTrue(DeviceExplain.uuidGloss("181D", translate)!!.contains("译:weight-scale"))
        assertTrue(DeviceExplain.uuidGloss("FCF1", translate)!!.contains("译:Google vendor"))
    }

    private fun radio(kind: RadioKind, name: String = "", mac: String = "00:11:22:00:00:01") = Sighting(
        key = "$kind:$mac", kind = kind, mac = mac, name = name, rssi = -60, rssiMin = -60, rssiMax = -60,
        channel = 1, frequencyMhz = 2412, vendor = null, randomized = false, hiddenSsid = false,
        serviceUuids = emptyList(), manufacturerId = null, manufacturerDataHex = "", rawHex = "", extras = "",
        firstSeen = 1L, lastSeen = 1L, hitCount = 1, fleetIds = emptySet(), rssiHistory = emptyList(), presence = emptyList(),
    )
}
