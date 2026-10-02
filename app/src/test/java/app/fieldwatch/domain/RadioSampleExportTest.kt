package app.fieldwatch.domain

import app.fieldwatch.data.LogStore
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioSampleExportTest {
    private val longHex = "0123456789ABCDEF".repeat(80)
    private val sample = Sighting(
        key = "BLE:AA:BB:CC:DD:EE:FF", kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:FF", name = "原始设备名称",
        rssi = -50, rssiMin = -60, rssiMax = -40, channel = 0, frequencyMhz = 2402,
        vendor = "Vendor", randomized = true, hiddenSsid = false,
        serviceUuids = listOf("180F", "FEAA"), manufacturerId = 76,
        manufacturerDataHex = longHex, rawHex = "FF$longHex", extras = "原始附加信息",
        firstSeen = 1000, lastSeen = 2000, hitCount = 3,
        fleetIds = emptySet(), rssiHistory = emptyList(), presence = emptyList(),
        vendorIeOuis = (0..11).map { "00:11:%02X".format(it) },
        facts = RadioFacts(
            txPowerDbm = -7, advFlags = 6, appearance = 128, addressType = "Random",
            advertisingIntervalMs = 123.5, periodicIntervalMs = 160.0,
            connectable = false, primaryPhy = "LE 1M", secondaryPhy = "LE Coded", deviceClass = 0x020C,
            wifiStandard = "802.11ax", channelWidth = "80 MHz", centerFreq0 = 5210, centerFreq1 = 0,
            capabilities = "[WPA2-PSK-CCMP][ESS]", supportedRates = "6* 54", security = "WPA2",
            mfgRecords = listOf(MfgRecord(76, longHex), MfgRecord(117, "A1$longHex")),
            serviceData = listOf(ServiceDataRecord("FEAA", longHex), ServiceDataRecord("180F", "02")),
            vendorIes = listOf(VendorIeRecord("0050F2", 4, longHex), VendorIeRecord("506F9A", 9, "A2$longHex")),
        ),
    )

    @Test
    fun sitJsonlKeepsAllBytesAndRecordsWithoutChangingUserContent() {
        val obj = exported(sample)
        assertSample(obj)
        assertEquals("原始设备名称", obj.getString("name"))
        assertEquals("我的设备", obj.getString("custom_name"))
        assertEquals("我的备注", obj.getString("observer_notes"))
    }

    @Test
    fun rotatingJsonlKeepsLegacyFieldsAndAddsTheSameCompleteSample() {
        val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val obj = JSONObject(LogStore.jsonLine(sample, "原始签名", iso))
        assertSample(obj)
        assertEquals(longHex.take(160), obj.getString("raw"))
        assertEquals(76, obj.getInt("mfg"))
        assertEquals("180F,FEAA", obj.getString("uuids"))
        assertEquals("原始签名", obj.getString("fleets"))
    }

    @Test
    fun savedSitSerializationAndRestoreKeepTheCompleteSample() {
        val file = SitFile(
            summary = SitSummary("s1", "样本", 1000),
            radios = listOf(SitRadio.from(sample, emptyList())),
        )
        val restored = Json.decodeFromString<SitFile>(Json.encodeToString(file)).radios.single().toSighting()
        assertSample(exported(restored))
        assertEquals(sample.facts, restored.facts)
        assertEquals(sample.vendor, restored.vendor)
    }

    @Test
    fun oldSavedSitWithoutSampleFieldsStillLoadsWithEmptyDefaults() {
        val old = """{"summary":{"id":"s1","name":"old","startAt":1000},"radios":[{"key":"BLE:AA","kind":"BLE","mac":"AA","name":"old device","firstSeen":1000,"lastSeen":2000,"hitCount":1,"rssi":-50,"rssiMin":-50,"rssiMax":-50}]}"""
        val restored = Json.decodeFromString<SitFile>(old).radios.single().toSighting()
        val obj = exported(restored)
        assertEquals("", obj.getString("raw_hex"))
        assertEquals("", obj.getString("manufacturer_data_hex"))
        assertTrue(obj.isNull("manufacturer_id"))
        assertEquals(0, obj.getJSONArray("service_uuids").length())
        assertEquals(0, obj.getJSONArray("vendor_ie_ouis").length())
        val facts = obj.getJSONObject("facts")
        assertEquals(0, facts.getJSONArray("mfg_records").length())
        assertEquals(0, facts.getJSONArray("service_data").length())
        assertEquals(0, facts.getJSONArray("vendor_ies").length())
        assertTrue(facts.isNull("appearance"))
        assertEquals(RadioFacts.Empty, restored.facts)
    }

    @Test
    fun savedSitRetainsPreviousBytesWhenLaterScanHasNoPayload() {
        val session = SitSession.start("samples", 2000, listOf(sample), emptyList(), emptySet(), emptySet())
        session.ingest(sample.copy(lastSeen = 3000, hitCount = 4, rawHex = "", manufacturerDataHex = "",
            manufacturerId = null, serviceUuids = emptyList(), vendorIeOuis = emptyList(), facts = RadioFacts.Empty),
            emptyList(), emptySet(), emptySet())
        assertSample(exported(session.snapshot().radios.single().toSighting()))
    }

    private fun exported(device: Sighting) = JSONObject(SitExport.jsonl(
        listOf(device), LogExportRadios.BOTH,
        mapOf(device.key to "我的设备"), mapOf(device.key to "我的备注"), emptySet(),
    ))

    private fun assertSample(obj: JSONObject) {
        assertEquals(sample.rawHex, obj.getString("raw_hex"))
        assertEquals(longHex, obj.getString("manufacturer_data_hex"))
        assertEquals(76, obj.getInt("manufacturer_id"))
        assertEquals(2, obj.getJSONArray("service_uuids").length())
        assertEquals(12, obj.getJSONArray("vendor_ie_ouis").length())
        assertEquals(sample.extras, obj.getString("extras"))
        val facts = obj.getJSONObject("facts")
        assertEquals(20, facts.length())
        assertEquals(longHex, facts.getJSONArray("mfg_records").getJSONObject(0).getString("data_hex"))
        assertEquals("A1$longHex", facts.getJSONArray("mfg_records").getJSONObject(1).getString("data_hex"))
        assertEquals(longHex, facts.getJSONArray("service_data").getJSONObject(0).getString("data_hex"))
        assertEquals("02", facts.getJSONArray("service_data").getJSONObject(1).getString("data_hex"))
        assertEquals(longHex, facts.getJSONArray("vendor_ies").getJSONObject(0).getString("data_hex"))
        assertEquals("A2$longHex", facts.getJSONArray("vendor_ies").getJSONObject(1).getString("data_hex"))
        assertEquals(-7, facts.getInt("tx_power_dbm"))
        assertEquals(6, facts.getInt("adv_flags"))
        assertEquals(128, facts.getInt("appearance"))
        assertEquals("Random", facts.getString("address_type"))
        assertEquals(123.5, facts.getDouble("advertising_interval_ms"), 0.0)
        assertEquals(160.0, facts.getDouble("periodic_interval_ms"), 0.0)
        assertEquals(false, facts.getBoolean("connectable"))
        assertEquals("LE 1M", facts.getString("primary_phy"))
        assertEquals("LE Coded", facts.getString("secondary_phy"))
        assertEquals(0x020C, facts.getInt("device_class"))
        assertEquals("802.11ax", facts.getString("wifi_standard"))
        assertEquals("80 MHz", facts.getString("channel_width"))
        assertEquals(5210, facts.getInt("center_freq0"))
        assertEquals(0, facts.getInt("center_freq1"))
        assertEquals(sample.facts.capabilities, facts.getString("capabilities"))
        assertEquals("6* 54", facts.getString("supported_rates"))
        assertEquals("WPA2", facts.getString("security"))
    }
}
