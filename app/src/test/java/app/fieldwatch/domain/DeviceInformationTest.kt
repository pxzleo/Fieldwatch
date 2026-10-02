package app.fieldwatch.domain

import org.junit.Assert.*
import org.junit.Test

class DeviceInformationTest {
    private fun radio(kind: RadioKind = RadioKind.BLE) = Sighting(
        key = "${kind}:00:11:22:33:44:55", kind = kind, mac = "00:11:22:33:44:55", name = "Sample",
        rssi = -60, rssiMin = -60, rssiMax = -60, channel = 0, frequencyMhz = 0, vendor = null,
        randomized = false, hiddenSsid = false, serviceUuids = emptyList(), manufacturerId = null,
        manufacturerDataHex = "", rawHex = "", extras = "", firstSeen = 1, lastSeen = 1, hitCount = 1,
        fleetIds = emptySet(), rssiHistory = emptyList(), presence = emptyList(),
    )
    private fun wmm(hex: String) = VendorIeRecord("00:50:F2", 2, hex)
    private fun tlm(hex: String) = AdvPayloadDecoder.decodeService(ServiceDataRecord("FEAA", hex))
        .associate { it.label to it.value }

    @Test fun wmmParameterAndInformationDecodeApPowerSavingWithoutPerformanceClaims() {
        for (hex in listOf("000180", "01018000" + "00000000".repeat(4))) {
            val fields = WifiWmmDecoder.decode(wmm(hex))
            assertEquals(2, fields.size)
            assertTrue(fields[0].value.contains("does not measure throughput"))
            assertTrue(fields[1].value.startsWith("Supported;"))
        }
        assertEquals("Not advertised in this element", WifiWmmDecoder.decode(wmm("000100"))[1].value)
        for (hex in listOf("", "GG", "0001", "00018000", "01018000", "000280",
            "01018001" + "00000000".repeat(4))) {
            val fields = WifiWmmDecoder.decode(wmm(hex))
            assertEquals(hex, "WMM parse status", fields.single().label)
        }
        assertEquals("Unsupported WMM subtype", WifiWmmDecoder.decode(wmm("0201"))[0].value)
        assertTrue(WifiWmmDecoder.decode(VendorIeRecord("00:11:22", 2, "000180")).isEmpty())
        assertTrue(WifiWmmDecoder.decode(VendorIeRecord("00:50:F2", 4, "000180")).isEmpty())
    }

    @Test fun setupMethodsKeepRawMaskAndDoNotClaimPairingIsCurrentlyOpen() {
        val decoded = WifiWpsDecoder.decode(VendorIeRecord("00:50:F2", 4, "10080002068C"))!!
        val fields = decoded.fields.associate { it.label to it.value }
        assertEquals("0x068C", fields["WPS configuration methods (advertised mask)"])
        val methods = fields.getValue("WPS setup methods (advertised)")
        for (name in listOf("Printed PIN label", "Displayed PIN", "Push button", "Virtual push button", "Physical push button"))
            assertTrue(name, methods.contains(name))
        assertTrue(methods.contains("not proof that setup is currently allowed"))
        val unknown = WifiWpsDecoder.decode(VendorIeRecord("00:50:F2", 4, "100800028000"))!!
        assertTrue(unknown.fields.last().value.startsWith("No known setup method"))
        for ((hex, expected) in listOf("00060050F2040001" to "WPS access point", "00040050F2040004" to "Camera",
            "00050050F2040001" to "Storage device", "000C0050F2040001" to "000C0050F2040001",
            "0004001122040004" to "0004001122040004")) {
            assertTrue(WifiWpsDecoder.decode(VendorIeRecord("00:50:F2", 4, "10540008$hex"))!!.fields.single().value.startsWith(expected))
        }
    }

    @Test fun meshFlagsSeparateKeyRefreshPhase2FromIvUpdateWithoutAuthenticationClaims() {
        for (flags in 0..3) {
            val device = radio().copy(rawHex = "172B01%02X".format(flags) + "00".repeat(20))
            val fields = AdvPayloadDecoder.decodeDevice(device).associate { it.label to it.value }
            assertEquals(if ((flags and 1) != 0) "Key refresh phase 2" else "Not in key refresh phase 2",
                fields["Mesh key refresh (advertised, unverified)"])
            assertEquals(if ((flags and 2) != 0) "IV update in progress" else "Normal IV operation",
                fields["Mesh IV update (advertised, unverified)"])
        }
    }

    @Test fun plainTlmDecodesSignedTemperatureUnsignedCountersAndTenthsOfSeconds() {
        val fields = tlm("20000BB8FF800000000AFFFFFFFF")
        assertEquals("3000 mV", fields["Beacon battery voltage"])
        assertEquals("-0.50 °C", fields["Beacon temperature"])
        assertEquals("10", fields["Beacon transmitted advertisement count"])
        assertEquals("429496729.5 s", fields["Beacon time since power-on or reboot"])
        val absent = tlm("200000008000FFFFFFFF00000001")
        assertEquals("Not supported", absent["Beacon battery voltage"])
        assertEquals("Not supported", absent["Beacon temperature"])
        assertEquals("4294967295", absent["Beacon transmitted advertisement count"])
        assertEquals("0.1 s", absent["Beacon time since power-on or reboot"])
        for (hex in listOf("20", "2001" + "00".repeat(16), "2000", "2000" + "00".repeat(13))) {
            val invalid = tlm(hex)
            assertFalse(hex, invalid.containsKey("Beacon battery voltage"))
            assertTrue(hex, invalid.values.single().contains("readings unavailable"))
        }
    }

    @Test fun sharedDetailsAndReportUseSameTranslatedFieldsWithoutTranslatingMeasurements() {
        val device = radio(RadioKind.WIFI).copy(facts = RadioFacts(vendorIes = listOf(wmm("000180"),
            VendorIeRecord("00:50:F2", 4, "100800020080"))))
        val translate: (String) -> String = { "译:$it" }
        val fields = AdvPayloadDecoder.decodeDevice(device, translate)
        assertTrue(fields.any { it.label == "译:WMM client power saving (U-APSD, advertised)" })
        assertTrue(fields.any { it.label == "译:WPS setup methods (advertised)" && it.value.contains("译:Push button") })
        val details = DeviceDetailText.build(device, emptyList(), now = 2, translate = translate)
        assertTrue(fields.all { details.contains("${it.label}: ${it.value}") })
        val report = DebriefReport.document(listOf(device), emptyList(), AppSettings(), emptyList(), now = 2, translate = translate)
            .toPlainText(translate)
        assertTrue(fields.all { report.contains("${it.label}: ${it.value}") })
        assertFalse(AdvPayloadDecoder.decodeDevice(device.copy(kind = RadioKind.BLE), translate).any { it.label.contains("WMM") })
    }
}
