package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import org.junit.Assert.*
import org.junit.Test

class DiscoveryV101Test {
    private val families = DefaultCatalog.discoveryFamiliesV101()
    private fun radio(mac: String = "00:11:22:33:44:55", company: Int? = null, hex: String = "") = Sighting(
        key = "BLE:$mac", kind = RadioKind.BLE, mac = mac, name = "",
        rssi = -60, rssiMin = -60, rssiMax = -60, channel = 0, frequencyMhz = 0,
        vendor = null, randomized = false, hiddenSsid = false, serviceUuids = emptyList(),
        manufacturerId = company, manufacturerDataHex = hex, rawHex = "", extras = "",
        firstSeen = 1L, lastSeen = 2L, hitCount = 1, fleetIds = emptySet(),
        rssiHistory = emptyList(), presence = emptyList())
    private fun hits(d: Sighting) = SignatureEngine().match(listOf(d), families).getValue(d.key)

    @Test fun capturedAppleMessagesMatchProtocolWithoutClaimingHardware() {
        for (hex in listOf("13085A920A6FBE46B700", "13085A76B0F792E9C900", "13080A14C3B28A559F00")) {
            assertEquals(setOf("fleet-apple-continuity-13"), hits(radio(company = 0x004C, hex = hex)))
        }
        assertEquals(setOf("fleet-airprint-radio"), hits(radio(company = 0x004C,
            hex = "03161100000277C0A801130000000000000000000000000B")))
        assertEquals(SignatureClass.OTHER, families[0].kind)
        assertEquals(SignatureClass.OTHER, families[1].kind)
    }

    @Test fun malformedRecordsAndCompanyOnlyCannotClaimAppleProtocols() {
        for (hex in listOf("13", "13085A", "13075A920A6FBE4600", "13085A920A6FBE46B700FF",
            "031611", "030100", "0215000000000000000000000000000000000000000000")) {
            assertTrue(hex, hits(radio(company = 0x004C, hex = hex)).isEmpty())
        }
        val valid = radio(company = 0x004C, hex = "13085A920A6FBE46B700")
        assertTrue(hits(valid.copy(manufacturerId = 0x004D)).isEmpty())
        assertTrue(hits(valid.copy(kind = RadioKind.WIFI)).isEmpty())
        assertTrue(hits(radio(company = 0x004C)).isEmpty())
    }

    @Test fun huaweiCompanyOrPublicOuiIdentifiesOnlyVendor() {
        val public = radio("5C:D8:9E:2F:A2:5B").copy(facts = RadioFacts(addressType = "Public"))
        assertEquals(setOf("fleet-huawei-ble-vendor"), hits(public))
        assertTrue(hits(public.copy(randomized = true)).isEmpty())
        assertTrue(hits(public.copy(facts = RadioFacts(addressType = "Random"))).isEmpty())
        assertTrue(hits(public.copy(kind = RadioKind.WIFI)).isEmpty())
        val company = radio("F1:8D:ED:DD:6B:24", 0x027D,
            "201997C454C36BAB5A121902CDAFC7F2BF1E2EEBFCA8428B55CFAA")
            .copy(facts = RadioFacts(addressType = "Random"))
        assertEquals(setOf("fleet-huawei-ble-vendor"), hits(company))
        assertTrue(hits(radio().copy(vendor = "Huawei Device Co., Ltd.")).isEmpty())
        assertEquals(SignatureClass.OTHER, families.last().kind)
    }

    @Test fun upgradePreservesCustomEditsAndDisabledRows() {
        val edited = families.first().copy(name = "Custom name", notes = "Custom notes", enabled = false)
        val custom = Fleet(id = "custom", name = "Custom", builtIn = false)
        val result = ConfigStore.appendCatalogV101(listOf(edited, custom))
        assertEquals(edited, result[0])
        assertEquals(custom, result[1])
        assertEquals(families.size + 1, result.size)
        assertEquals(result, ConfigStore.appendCatalogV101(result))
        assertEquals(101, ConfigStore.CATALOG_VERSION)
    }
}
