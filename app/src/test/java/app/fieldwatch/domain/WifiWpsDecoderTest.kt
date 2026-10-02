package app.fieldwatch.domain

import org.junit.Assert.*
import org.junit.Test

class WifiWpsDecoderTest {
    private val sample = "102100067869616F6D69102300035233501024000430303032" +
        "1042000831323334353637381011000C5869616F4D69526F75746572"
    private fun wps(hex: String = sample) = VendorIeRecord("00:50:F2", 4, hex)

    @Test fun r3pBigEndianAttributesPreserveBroadcastText() {
        val decoded = WifiWpsDecoder.decode(wps())!!
        assertEquals(WifiWpsDecoder.Status.COMPLETE, decoded.status)
        assertEquals("xiaomi", decoded.manufacturer)
        assertEquals("R3P", decoded.model)
        assertEquals(listOf("xiaomi", "R3P", "0002", "12345678", "XiaoMiRouter"), decoded.fields.map { it.value })
        assertEquals("WPS advertised serial number", decoded.fields[3].label)
    }

    @Test fun unrelatedOuiTypeAndUnknownAttributesDoNotInventIdentity() {
        assertNull(WifiWpsDecoder.decode(VendorIeRecord("00:11:22", 4, sample)))
        assertNull(WifiWpsDecoder.decode(VendorIeRecord("00:50:F2", 1, sample)))
        assertNull(WifiWpsDecoder.decode(VendorIeRecord("00:50:F2", 2, sample)))
        assertEquals("WPA", WifiWpsDecoder.protocolName(VendorIeRecord("00:50:F2", 1, "")))
        assertEquals("WMM", WifiWpsDecoder.protocolName(VendorIeRecord("00:50:F2", 2, "")))
        assertTrue(WifiWpsDecoder.decode(wps("FFFF0002ABCD"))!!.fields.isEmpty())
        assertNull(WifiWpsDecoder.identity(listOf(wps("102100067869616F6D69"), wps("10230003523350"))))
        assertEquals("R3P", WifiWpsDecoder.identity(listOf(VendorIeRecord("00:50:F2", 2, "00"), wps()))!!.model)
    }

    @Test fun truncationAndMalformedValuesAreExplicit() {
        for (hex in listOf("1021", "102100067869")) {
            val decoded = WifiWpsDecoder.decode(wps(hex))!!
            assertEquals(WifiWpsDecoder.Status.TRUNCATED, decoded.status)
            assertEquals("Truncated WPS attributes", decoded.fields.last().value)
            assertNull(decoded.manufacturer)
        }
        for (hex in listOf("0", "GG", "10210002C328", "104400020102", "1054000100")) {
            assertEquals(hex, WifiWpsDecoder.Status.MALFORMED, WifiWpsDecoder.decode(wps(hex))!!.status)
        }
        assertEquals(WifiWpsDecoder.Status.EMPTY, WifiWpsDecoder.decode(wps(""))!!.status)
        assertNull(WifiWpsDecoder.identity(listOf(wps("1021000010230003202020"))))
        assertTrue(WifiWpsDecoder.decode(wps("1021000010230003202020"))!!.fields.isEmpty())
    }

    @Test fun translateOnlyLabelsReasonsAndStates() {
        val called = mutableListOf<String>()
        val translate: (String) -> String = { called += it; "译:$it" }
        val decoded = WifiWpsDecoder.decode(wps(sample + "10440001021054000800060050F2040001"), translate)!!
        assertEquals("译:WPS manufacturer", decoded.fields.first().label)
        assertEquals("xiaomi", decoded.fields.first().value)
        assertEquals("译:WPS configured", decoded.fields[5].value)
        assertEquals("译:WPS access point", decoded.fields[6].value)
        val device = wifi()
        val guess = DeviceExplain.guess(device, emptyList(), translate)
        assertTrue(guess.headline.contains("xiaomi R3P"))
        assertTrue(guess.because.contains("译:WPS advertises manufacturer xiaomi and model R3P."))
        assertFalse(called.contains("xiaomi"))
        assertFalse(called.contains("R3P"))
        val report = DeviceDetailText.build(device, emptyList(), now = 2L, translate = translate)
        assertTrue(report.contains("译:WPS model: R3P"))
        assertTrue(report.contains("译:WPS advertised serial number: 12345678"))
        assertTrue(report.contains("WPS译: — Wi-Fi protocol tag"))
        assertFalse(report.contains("Microsoft"))
    }

    @Test fun listKeepsCustomAndAdvertisedNamesAndAddsIdentityOnce() {
        val device = wifi()
        assertTrue(device.listLineText(ListLine.NAME_AND_TYPE).contains("Home · xiaomi R3P"))
        assertEquals("Home", device.listLineText(ListLine.ADVERTISED_NAME))
        assertEquals("My router", device.listLineText(ListLine.NAME_AND_TYPE, watchName = "My router"))
        assertEquals("Home R3P", device.copy(name = "Home R3P").listLineText(ListLine.NAME_AND_TYPE))
        assertEquals("Home", device.copy(facts = RadioFacts()).listLineText(ListLine.NAME_AND_TYPE))
        assertTrue(DeviceExplain.guess(device, listOf("Generic router")).headline.contains("xiaomi R3P"))
    }

    @Test fun debriefDocumentIncludesBroadcastIdentityWithRawValues() {
        val translated: (String) -> String = { "译:$it" }
        val doc = DebriefReport.document(listOf(wifi()), emptyList(), AppSettings(), emptyList(), now = 2L, translate = translated)
        val text = doc.toPlainText(translated)
        assertTrue(text.contains("WPS: xiaomi R3P"))
        assertFalse(text.contains("译:xiaomi"))
        val noWps = DebriefReport.document(listOf(wifi().copy(facts = RadioFacts())), emptyList(), AppSettings(), emptyList(), now = 2L)
        assertFalse(noWps.toPlainText().contains("WPS:"))
        val existing = DebriefReport.document(listOf(wifi().copy(name = "Home R3P")), emptyList(), AppSettings(), emptyList(), now = 2L)
        assertFalse(existing.toPlainText().contains("WPS:"))
    }

    private fun wifi() = Sighting(
        key = "WIFI:00:11:22:33:44:55", kind = RadioKind.WIFI, mac = "00:11:22:33:44:55",
        name = "Home", rssi = -50, rssiMin = -60, rssiMax = -50, channel = 1, frequencyMhz = 2412,
        vendor = null, randomized = false, hiddenSsid = false, serviceUuids = emptyList(), manufacturerId = null,
        manufacturerDataHex = "", rawHex = "", extras = "", firstSeen = 1L, lastSeen = 1L, hitCount = 1,
        fleetIds = emptySet(), rssiHistory = emptyList(), presence = emptyList(),
        facts = RadioFacts(vendorIes = listOf(wps())),
    )
}
