package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceReportLocalizationTest {
    private val translate: (String) -> String = { source ->
        when (source) {
            "Fieldwatch device detail\n" -> "Fieldwatch 设备详情\n"
            "Identity" -> "身份信息"
            "Signal" -> "信号"
            "Notes" -> "备注"
            "Advertised name" -> "广播名称"
            "channel %1\$s" -> "信道 %1\$s"
            "%1\$s to %2\$s dBm" -> "%1\$s 至 %2\$s dBm"
            "## Collection context" -> "## 采集背景"
            "- Subject: %1\$s titled “%2\$s”." -> "- 对象：%1\$s，名称为“%2\$s”。"
            "Operator path" -> "观测者路径"
            "Fieldwatch %1\$s export" -> "Fieldwatch %1\$s 导出"
            "Observer: %1\$s" -> "观测备注：%1\$s"
            "Heard at this phone. Not a radio fix." -> "在手机位置收到信号，并非设备定位。"
            else -> source
        }
    }

    @Test
    fun detailReportTranslatesTemplatesAndRetainsObservedAndOperatorValues() {
        val device = sighting().copy(name = "Identity", channel = 6, frequencyMhz = 2437)
        val english = DeviceDetailText.build(device, emptyList(), now = 2L, signatureNotes = listOf("Identity" to "Signal"))
        val localized = DeviceDetailText.build(device, emptyList(), now = 2L, signatureNotes = listOf("Identity" to "Signal"), translate = translate)
        assertTrue(english.startsWith("Fieldwatch device detail\n"))
        assertTrue(localized.startsWith("Fieldwatch 设备详情\n"))
        assertTrue(localized.contains("## 身份信息"))
        assertTrue(localized.contains("广播名称: Identity"))
        assertTrue(localized.contains("Identity: Signal"))
        assertTrue(localized.contains("MAC: 00:11:22:33:44:55"))
        assertTrue(localized.contains("信道 6"))
        assertTrue(localized.contains("-80 至 -35 dBm"))
        assertTrue(localized.contains("2437 MHz"))
        assertFalse(english.contains("身份信息"))
    }

    @Test
    fun analystPromptForwardsTranslationToTheEmbeddedDeviceDump() {
        val device = sighting().copy(name = "Identity")
        val prompt = DeviceDetailPrompt.build(device, emptyList(), AppSettings(), now = 2L, translate = translate)
        assertTrue(prompt.contains("## 采集背景"))
        assertTrue(prompt.contains("名称为“Identity”"))
        assertTrue(prompt.contains("Fieldwatch 设备详情"))
        assertTrue(prompt.contains("MAC: 00:11:22:33:44:55"))
    }

    @Test
    fun translatedSignatureListDoesNotChangeRawSignatureClassification() {
        val device = sighting().copy(fleetIds = setOf("apple"))
        val names = listOf("Apple Device")
        val displayNames = listOf("Apple 设备")
        val raw = DeviceDetailText.build(device, names, now = 2L)
        val localized = DeviceDetailText.build(device, names, now = 2L,
            translate = translate, displaySignatureNames = displayNames)
        assertTrue(raw.contains("Matched signatures: Apple Device"))
        assertTrue(localized.contains("Matched signatures: Apple 设备"))
        assertTrue(localized.contains("Most likely Apple device (type unconfirmed)"))
        assertTrue(localized.contains("Matched signature Apple Device."))
        val prompt = DeviceDetailPrompt.build(device, names, AppSettings(), now = 2L,
            translate = translate, displaySignatureNames = displayNames)
        assertTrue(prompt.contains("Matched signatures: Apple 设备"))
        assertTrue(prompt.contains("Matched signature Apple Device."))
    }

    @Test
    fun geoDescriptionsTranslateWithoutChangingXmlCoordinatesOrUserNotes() {
        val radio = logRadio()
        val xml = GeoExport.render(
            GeoExport.Format.KML, listOf(radio), emptyMap(), "1.2.3", "",
            customNames = mapOf(radio.key to "Identity"),
            observerNotes = mapOf(radio.key to "Signal <operator>"),
            track = listOf(GpsSample(1L, 37.0, -122.0), GpsSample(2L, 38.0, -123.0)),
            translate = translate,
        )
        assertTrue(xml.contains("<name>Fieldwatch 1.2.3 导出</name>"))
        assertTrue(xml.contains("<name>Identity</name>"))
        assertTrue(xml.contains("<name>观测者路径</name>"))
        assertTrue(xml.contains("观测备注：Signal &lt;operator&gt;"))
        assertTrue(xml.contains("<coordinates>-122.143000,37.441900</coordinates>"))
        assertTrue(xml.contains("WIFI AA:BB:CC:DD:EE:01"))
        assertTrue(xml.contains("在手机位置收到信号，并非设备定位。"))
    }

    @Test
    fun wigleMachineSchemaAndValuesNeverUseTheHumanTranslator() {
        val args = listOf(logRadio())
        val english = GeoExport.render(GeoExport.Format.WIGLE, args, emptyMap(), "1.2.3", "")
        val localized = GeoExport.render(GeoExport.Format.WIGLE, args, emptyMap(), "1.2.3", "", translate = { error("WiGLE schema must remain unchanged: $it") })
        assertEquals(english, localized)
        assertTrue(localized.contains("MAC,SSID,AuthMode,FirstSeen,Channel,RSSI"))
    }

    private fun logRadio() = LogRadio(
        kind = RadioKind.WIFI, mac = "AA:BB:CC:DD:EE:01", name = "Identity", vendor = "Signal",
        manufacturerId = null, manufacturerDataHex = "", serviceUuids = emptyList(), vendorIeOuis = emptyList(),
        randomized = false, hiddenSsid = false, rssi = -60, firstSeen = 1L, lastSeen = 2L, hits = 3,
        channel = 6, frequencyMhz = 2437, latitude = 37.4419, longitude = -122.1430,
    )

    private fun sighting() = Sighting(
        key = "ble:00:11:22:33:44:55", kind = RadioKind.BLE, mac = "00:11:22:33:44:55",
        name = "", rssi = -50, rssiMin = -80, rssiMax = -35, channel = 0, frequencyMhz = 0,
        vendor = null, randomized = false, hiddenSsid = false, serviceUuids = emptyList(),
        manufacturerId = null, manufacturerDataHex = "", rawHex = "", extras = "",
        firstSeen = 1L, lastSeen = 2L, hitCount = 1, fleetIds = emptySet(),
        rssiHistory = emptyList(), presence = emptyList(),
    )
}
