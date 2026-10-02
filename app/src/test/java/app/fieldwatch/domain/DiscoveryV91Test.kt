package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import org.junit.Assert.*
import org.junit.Test

class DiscoveryV91Test {
    private val families = DefaultCatalog.discoveryFamiliesV91()
    private val engine = SignatureEngine()
    private fun radio(name: String, kind: RadioKind = RadioKind.BLE, mac: String = "00:11:22:00:00:01") = Sighting(
        key = "$kind:$mac", kind = kind, mac = mac, name = name, rssi = -60, rssiMin = -60, rssiMax = -60,
        channel = 0, frequencyMhz = 0, vendor = null, randomized = false, hiddenSsid = false,
        serviceUuids = emptyList(), manufacturerId = null, manufacturerDataHex = "", rawHex = "", extras = "",
        firstSeen = 1000, lastSeen = 1000, hitCount = 1, fleetIds = emptySet(), rssiHistory = emptyList(), presence = emptyList(),
    )
    private fun hits(r: Sighting, catalog: List<Fleet> = families) = engine.match(listOf(r), catalog, 1000).getValue(r.key)

    @Test fun verifiedNamesIdentifyFamiliesOnTheirOwnRadioOnly() {
        for ((name, id) in listOf(
            "BJP0102B001" to "parking-fence", "NIU Link ABCD" to "niu-link", "CFMOTO-ABCD" to "cfmoto",
            "IngeekDK-GAC" to "ingeek-key", "ZeekrVehicle123" to "zeekr-vehicle", "ROADBIT" to "roadbit",
            "NARWAL-abcdef" to "narwal", "colmo" to "colmo", "Petkit_K3" to "petkit",
            "HONOR Band 6-ABC" to "honor-band", "OPPO Watch 4 Pro A060" to "oppo-watch",
            "MJWSD05MMC" to "mi-thermometer", "MJWSD06MMC" to "mi-thermometer",
            "Mijia Scale S400 ABCD" to "mijia-s400", "xiaomi.switch.w1" to "mi-switch",
            "xiaomi.switch.w2" to "mi-switch", "xiaomi.switch.pro1" to "mi-switch", "zimi.switch.dhkg01" to "mi-switch",
            "xiaomi.light.btlm2" to "mi-light", "lemesh.light.wy0c15" to "mi-light", "careco.light.track" to "mi-light",
            "cariot.holder.wpc100" to "cariot-holder",
        )) {
            assertTrue(name, "fleet-$id" in hits(radio(name)))
            assertFalse(name, "fleet-$id" in hits(radio(name, RadioKind.WIFI)))
        }
        for ((name, id) in listOf("ZTE_5GCPE_ABCD" to "zte-wifi", "PHICOMM_ABCD" to "phicomm-wifi",
            "@PHICOMM_EX_28_5G" to "phicomm-wifi", "360WiFi-ABCD" to "qihoo-wifi")) {
            assertTrue(name, "fleet-$id" in hits(radio(name, RadioKind.WIFI)))
            assertFalse(name, "fleet-$id" in hits(radio(name)))
        }
    }

    @Test fun ambiguousNamesAndGenericServicesAreNotAssignedToTheseProducts() {
        for (name in listOf("QJLF30", "YD12345678", "LEX1234567890", "BJP00000001", "N/A", "TG",
            "U-GWH1234", "xiaomi.light.unknown", "myNARWAL-fan", "Petkit")) {
            assertTrue(name, hits(radio(name).copy(manufacturerId = 0x0201,
                serviceUuids = listOf("FEE7", "1812", "FFF0", "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"))).isEmpty())
        }
        assertTrue(hits(radio("", RadioKind.WIFI).copy(vendorIeOuis = listOf("00:0C:43", "00:50:F2"))).isEmpty())
    }

    @Test fun registeredHaierPrefixRequiresAStableBleAddress() {
        val stable = radio("U-GWH1234", mac = "44:48:FF:00:00:01")
        assertTrue("fleet-haier-radio" in hits(stable))
        assertFalse("fleet-haier-radio" in hits(stable.copy(randomized = true)))
        assertFalse("fleet-haier-radio" in hits(stable.copy(facts = RadioFacts(addressType = "Random"))))
        assertTrue("fleet-haier-radio" in hits(stable.copy(kind = RadioKind.WIFI)))
    }

    @Test fun qianNeedsBothNameAndPublicRegisteredAddressWithoutGuessingItsDeviceType() {
        val r = radio("QJLF30", mac = "24:F1:50:00:00:01")
        assertTrue("fleet-qian-radio" in hits(r))
        assertFalse("fleet-qian-radio" in hits(r.copy(name = "")))
        assertFalse("fleet-qian-radio" in hits(r.copy(mac = "00:11:22:00:00:01")))
        assertFalse("fleet-qian-radio" in hits(r.copy(randomized = true)))
        assertFalse("fleet-qian-radio" in hits(r.copy(kind = RadioKind.WIFI)))
        assertEquals(SignatureClass.OTHER, families.single { it.id == "fleet-qian-radio" }.kind)
    }

    @Test fun migrationPreservesCustomizationsAndDisabledRulesAndIsIdempotent() {
        val old = DefaultCatalog.domesticFamilies().first { it.id == "fleet-xiaomi-wifi" }
            .copy(name = "Operator label", notes = "Operator notes", enabled = false, rules = listOf(
                MatchRule(RuleKind.OUI, text = "EC:41:18", radio = RadioKind.WIFI, enabled = false)))
        val custom = old.copy(id = "fleet-narwal", builtIn = false)
        val result = ConfigStore.appendCatalogV91(listOf(old, custom))
        assertEquals(old.copy(rules = result.first().rules), result.first())
        assertFalse(result.first().rules.first().enabled)
        assertEquals(custom, result[1])
        assertEquals(1, result.count { it.id == custom.id })
        assertEquals(result, ConfigStore.appendCatalogV91(result))
        val all = DefaultCatalog.domesticFamilies().first { it.id == "fleet-h3c-wifi" }.copy(matchAny = false,
            rules = listOf(MatchRule(RuleKind.NAME_GLOB, text = "H3C_*", radio = RadioKind.WIFI),
                MatchRule(RuleKind.OUI, text = "04:D7:A5", radio = RadioKind.WIFI)))
        assertEquals(all, ConfigStore.appendCatalogV91(listOf(all)).first())
        val h3c = radio("H3C_test", RadioKind.WIFI, "04:D7:A5:00:00:01")
        assertTrue(all.id in hits(h3c, ConfigStore.appendCatalogV91(listOf(all))))
    }

    @Test fun parkingBeaconClassDoesNotBecomeAVehicle() {
        val fleet = families.single { it.id == "fleet-parking-fence" }
        assertEquals(SignatureClass.BEACON, fleet.kind)
        val guess = DeviceExplain.guess(radio("BJP0102B001"), listOf(fleet.name))
        assertTrue(guess.headline.contains("parking-area beacon"))
        assertFalse(guess.headline.contains("rider"))
        val roadbit = DeviceExplain.guess(radio("ROADBIT"), listOf("RoadBit e-bike radio"))
        assertEquals(DeviceExplain.Confidence.MEDIUM, roadbit.confidence)
    }
}
