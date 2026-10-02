package app.fieldwatch.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveSortTest {
    private fun radio(id: String, kind: RadioKind = RadioKind.BLE, rssi: Int = -60, frequency: Int = 0,
        connectable: Boolean? = null): Sighting = Sighting(
        key = id, kind = kind, mac = id, name = "", rssi = rssi, rssiMin = rssi, rssiMax = rssi,
        channel = 0, frequencyMhz = frequency, vendor = null, randomized = false, hiddenSsid = false,
        serviceUuids = emptyList(), manufacturerId = null, manufacturerDataHex = "", rawHex = "", extras = "",
        firstSeen = 1L, lastSeen = 100_000L, hitCount = 1, fleetIds = emptySet(), rssiHistory = emptyList(),
        presence = emptyList(), facts = RadioFacts(connectable = connectable),
    )

    @Test
    fun strengthDirectionsUseRealValuesAndKeepUnavailableLast() {
        val devices = listOf(radio("missing", rssi = 127), radio("weak", rssi = -90), radio("strong", rssi = -40))
        val settings = AppSettings(strengthSort = StrengthSort.INSTANT)
        assertEquals(listOf("strong", "weak", "missing"), devices.sortedWith(LiveSort.strength(settings, 100_000L)).map { it.key })
        assertEquals(listOf("weak", "strong", "missing"), devices.sortedWith(LiveSort.strength(settings, 100_000L, true)).map { it.key })
    }

    @Test
    fun averageSortUsesSelectedWindowAndDoesNotInventMissingStrength() {
        val devices = listOf(
            radio("a", rssi = -40).copy(rssiHistory = listOf(RssiSample(99_000L, -90))),
            radio("b", rssi = -80).copy(rssiHistory = listOf(RssiSample(99_000L, -45))),
            radio("expired", rssi = 127).copy(rssiHistory = listOf(RssiSample(1L, -30))),
        )
        val settings = AppSettings(strengthSort = StrengthSort.AVERAGE, averageWindowSec = 30)
        assertEquals(listOf("b", "a", "expired"), devices.sortedWith(LiveSort.strength(settings, 100_000L)).map { it.key })
    }

    @Test
    fun subtypesUseFrequencyAndTriStateConnectability() {
        assertEquals(SignalType.WIFI_24, radio("w", RadioKind.WIFI, frequency = 2412).signalType())
        assertEquals(SignalType.WIFI_5, radio("w", RadioKind.WIFI, frequency = 5180).signalType())
        assertEquals(SignalType.WIFI_6, radio("w", RadioKind.WIFI, frequency = 5955).signalType())
        assertEquals(SignalType.WIFI_OTHER, radio("w", RadioKind.WIFI).signalType())
        assertEquals(SignalType.BLE_CONNECTABLE, radio("b", connectable = true).signalType())
        assertEquals(SignalType.BLE_BROADCAST, radio("b", connectable = false).signalType())
        assertEquals(SignalType.BLE_UNKNOWN, radio("b").signalType())
    }

    @Test
    fun radioPriorityAndSubtypeKeepStrengthOrderWithinEachGroup() {
        val devices = listOf(radio("ble", rssi = -20), radio("w5", RadioKind.WIFI, -40, 5180),
            radio("w24weak", RadioKind.WIFI, -80, 2412), radio("w24strong", RadioKind.WIFI, -30, 2412))
        val settings = AppSettings(strengthSort = StrengthSort.INSTANT)
        fun sorted(sort: ListSort) = devices.sortedWith(LiveSort.types(devices, settings.copy(listSort = sort), 100_000L, emptyMap())).map { it.key }
        assertEquals(listOf("w24strong", "w5", "w24weak", "ble"), sorted(ListSort.WIFI_FIRST))
        assertEquals(listOf("ble", "w24strong", "w5", "w24weak"), sorted(ListSort.BLE_FIRST))
        assertEquals(listOf("w24strong", "w24weak", "w5", "ble"), sorted(ListSort.SIGNAL_TYPE))
    }

    @Test
    fun purposeUsesExistingDecodedEvidenceAndKeepsGenericAppleUnknown() {
        fun siri(code: String) = radio(code).copy(manufacturerId = 76, manufacturerDataHex = "080700004300${code}CA")
        assertEquals(DevicePurpose.PHONE, siri("0002").devicePurpose(listOf("Apple Device")))
        assertEquals(DevicePurpose.COMPUTER, siri("0009").devicePurpose(listOf("Apple Device")))
        assertEquals(DevicePurpose.SPEAKER, siri("0007").devicePurpose(listOf("Apple Device")))
        assertEquals(DevicePurpose.WEARABLE, siri("000A").devicePurpose(listOf("Apple Device")))
        assertEquals(DevicePurpose.SENSOR, radio("sensor").copy(serviceUuids = listOf("181A")).devicePurpose())
        assertEquals(DevicePurpose.UNKNOWN, radio("unknown").devicePurpose())
        assertEquals(DevicePurpose.UNKNOWN, radio("apple").devicePurpose(listOf("Apple Device")))
        for (frame in listOf("0B0100", "0C0100", "0F0100", "100517FC010203")) {
            assertEquals(DevicePurpose.UNKNOWN, radio("apple").copy(manufacturerId = 76, manufacturerDataHex = frame).devicePurpose())
        }
        assertEquals(DevicePurpose.CAMERA, radio("wifi", RadioKind.WIFI).devicePurpose(listOf("Hanwha Vision")))
    }

    @Test
    fun purposeSortGroupsTypesAndUnknownLast() {
        val devices = listOf(radio("unknown", rssi = -10), radio("sensor", rssi = -40).copy(serviceUuids = listOf("181A")),
            radio("earbuds", rssi = -90).copy(serviceUuids = listOf("184E")))
        val settings = AppSettings(listSort = ListSort.DEVICE_TYPE)
        assertEquals(listOf("earbuds", "sensor", "unknown"),
            devices.sortedWith(LiveSort.types(devices, settings, 100_000L, emptyMap())).map { it.key })
    }

    @Test
    fun quickSortSwitchesGroupedViewsToGlobalListAndPersistsNewModes() {
        val old = Json.decodeFromString<AppSettings>("{}")
        assertEquals(ListSort.STRENGTH, old.listSort)
        val global = old.withLiveSort(ListSort.BLE_FIRST)
        assertEquals(ViewMode.LIST, global.viewMode)
        assertEquals(ListSort.BLE_FIRST, Json.decodeFromString<AppSettings>(Json.encodeToString(global)).listSort)
        assertEquals(ViewMode.HYBRID, old.copy(viewMode = ViewMode.HYBRID).withLiveSort(ListSort.DEVICE_TYPE).viewMode)
        assertEquals(StrengthSort.INSTANT, old.withLiveSort(ListSort.STRENGTH, StrengthSort.INSTANT).strengthSort)
    }
}
