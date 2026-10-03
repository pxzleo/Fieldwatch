package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import org.junit.Assert.*
import org.junit.Test

class DiscoveryV100Test {
    private val families = DefaultCatalog.discoveryFamiliesV100()
    private fun radio(name: String, mac: String = "00:11:22:33:44:55", company: Int? = null,
        uuids: List<String> = emptyList(), kind: RadioKind = RadioKind.BLE) = Sighting(
        key = "${kind.name}:$mac", kind = kind, mac = mac, name = name,
        rssi = -60, rssiMin = -60, rssiMax = -60, channel = 0, frequencyMhz = 2412,
        vendor = null, randomized = false, hiddenSsid = false, serviceUuids = uuids,
        manufacturerId = company, manufacturerDataHex = "", rawHex = "", extras = "",
        firstSeen = 1L, lastSeen = 2L, hitCount = 1, fleetIds = emptySet(),
        rssiHistory = emptyList(), presence = emptyList())
    private fun hits(device: Sighting) = SignatureEngine().match(listOf(device), families).values.single()

    @Test fun capturedNamesAndCombinedEvidenceIdentifySixFamilies() {
        val samples = listOf(
            radio("LOCK_dd7c", "F8:AA:B3:3F:DD:7C") to "fleet-dessmann-lock",
            radio("GR-AC_11002_09_7e6d_FC", "58:0D:0D:80:7E:6D", 0x0D23) to "fleet-gree-ac-ble",
            radio("BOLOLO-b8d0", "D4:E9:F4:48:B8:D2") to "fleet-bololo-appliance",
            radio("UTRAO_KH_Ultra_N4YYxCnpCMy", "E8:6B:EA:C0:A8:BA") to "fleet-utrao-kh-ultra",
            radio("TAPE LIGHTS", "00:00:03:95:6E:12") to "fleet-tape-lights",
            radio("JoyLink", "08:04:11:00:00:64", uuids = listOf("0000FE70-0000-1000-8000-00805F9B34FB")) to "fleet-jd-joylink")
        for ((sample, expected) in samples) {
            assertEquals(sample.name, setOf(expected), hits(sample))
            assertTrue(hits(sample.copy(kind = RadioKind.WIFI)).isEmpty())
        }
    }

    @Test fun weakAndGenericEvidenceDoesNotClaimAProduct() {
        val negative = listOf(
            radio("LOCK_dd7c"), radio("Unrelated", "F8:AA:B3:3F:DD:7C"),
            radio("LOCK_dd7c", "F8:AA:B3:3F:DD:7C").copy(randomized = true),
            radio("LOCK_dd7c", "F8:AA:B3:3F:DD:7C").let { it.copy(facts = it.facts.copy(addressType = "Random")) },
            radio("GR-AC_any"), radio("Unrelated", company = 0x0D23),
            radio("JoyLink"), radio("Unrelated", uuids = listOf("FE70")),
            radio("TAPE LIGHTS unrelated"), radio("BOLOLO"), radio("UTRAO_other"),
            radio("Unknown", uuids = listOf("ABF0", "FFE1", "FFF0")),
            radio("Unknown", company = 0xD406))
        for (sample in negative) assertTrue(sample.name, hits(sample).isEmpty())
    }

    @Test fun migrationPreservesExistingEditedAndDisabledRowsAndIsIdempotent() {
        val edited = families.first().copy(name = "Operator choice", notes = "Operator note", enabled = false)
        val custom = Fleet(id = "custom", name = "Custom", builtIn = false)
        val result = ConfigStore.appendCatalogV100(listOf(edited, custom))
        assertEquals(edited, result.first())
        assertEquals(custom, result[1])
        assertEquals(families.size + 1, result.size)
        assertEquals(result, ConfigStore.appendCatalogV100(result))
    }
}
