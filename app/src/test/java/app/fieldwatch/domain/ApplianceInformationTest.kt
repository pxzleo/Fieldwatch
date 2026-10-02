package app.fieldwatch.domain

import org.junit.Assert.*
import org.junit.Test

class ApplianceInformationTest {
    private val serial = MfgRecord(0x06A8, "01" + "12345678AC0001".toByteArray().toHexUpper())
    private val address = MfgRecord(0x06A8, "0103003255443322110000")
    private fun radio() = Sighting(key = "BLE:00:11:22:33:44:55", kind = RadioKind.BLE,
        mac = "00:11:22:33:44:55", name = "midea", rssi = -50, rssiMin = -50, rssiMax = -50,
        channel = 0, frequencyMhz = 0, vendor = null, randomized = false, hiddenSsid = false,
        serviceUuids = emptyList(), manufacturerId = null, manufacturerDataHex = "", rawHex = "",
        extras = "", firstSeen = 1L, lastSeen = 1L, hitCount = 1, fleetIds = emptySet(),
        rssiHistory = emptyList(), presence = emptyList())

    @Test fun mideaIdentityIsStrictAndNeverInventsModelOrOperatingState() {
        val fields = AdvPayloadDecoder.decodeManufacturer(serial).associate { it.label to it.value }
        assertEquals("12345678AC0001", fields["Midea advertised short serial"])
        assertEquals("12345678", fields["Midea SN8 product code"])
        for (record in listOf(serial.copy(companyId = 76), serial.copy(dataHex = "GG"),
            serial.copy(dataHex = serial.dataHex.dropLast(2)), serial.copy(dataHex = serial.dataHex + "00"),
            serial.copy(dataHex = "02" + serial.dataHex.drop(2)), serial.copy(dataHex = "0100" + serial.dataHex.drop(4))))
            assertTrue(record.toString(), MideaAdvertisementDecoder.decode(record).isEmpty())
        assertTrue(fields.getValue("Midea readings status").contains("does not confirm a unique retail model"))
    }

    @Test fun mideaAddressRequiresScanCorroborationAndSplitRecordsSurviveMerge() {
        assertEquals("00:11:22:33:44:55", MideaAdvertisementDecoder.decodeAddress(address, radio().mac).single().value)
        assertTrue(MideaAdvertisementDecoder.decodeAddress(address, "AA:BB:CC:DD:EE:FF").isEmpty())
        assertTrue(MideaAdvertisementDecoder.decodeAddress(address.copy(dataHex = "0103003355443322110000"), radio().mac).isEmpty())
        assertTrue(MideaAdvertisementDecoder.decodeAddress(address.copy(dataHex = address.dataHex + "00"), radio().mac).isEmpty())
        assertTrue(MideaAdvertisementDecoder.decodeAddress(address.copy(companyId = 76), radio().mac).isEmpty())
        val facts = RadioFacts(mfgRecords = listOf(serial, address))
            .merge(RadioFacts(mfgRecords = listOf(address.copy(dataHex = address.dataHex + "2000"))))
        assertEquals(2, facts.mfgRecords.size)
        assertTrue(serial in facts.mfgRecords)
        val newer = serial.copy(dataHex = "01" + "12345678AC0002".toByteArray().toHexUpper())
        assertEquals(listOf(newer), facts.merge(RadioFacts(mfgRecords = listOf(newer))).mfgRecords.filter { MideaAdvertisementDecoder.decode(it).isNotEmpty() })
    }

    @Test fun sharedDetailAndReportTranslateMideaAndMissingMiReadings() {
        val translate: (String) -> String = { "译:$it" }
        val device = radio().copy(facts = RadioFacts(mfgRecords = listOf(serial, address),
            serviceData = listOf(ServiceDataRecord("FE95", "30585B050155443322110008"))))
        val detail = DeviceDetailText.build(device, emptyList(), now = 2L, translate = translate)
        val report = DebriefReport.document(listOf(device), emptyList(), AppSettings(), emptyList(), now = 2L, translate = translate).toPlainText(translate)
        for (text in listOf(detail, report)) {
            assertTrue(text.contains("译:Midea advertised short serial: 12345678AC0001"))
            assertTrue(text.contains("译:MiBeacon readings status: 译:This frame contains no measurement objects; not a zero reading."))
        }
    }

    @Test fun miDiscoveryAndEncryptedFramesExplainReadingsAvailability() {
        val discovery = MiBeaconDecoder.decode("30585B050155443322110008")
        assertTrue(discovery.validHeader)
        assertTrue(discovery.fields.any { it.value.contains("no measurement objects") })
        assertFalse(discovery.fields.any { it.label == "MiBeacon temperature" })
        val encrypted = MiBeaconDecoder.decode("4859B55501" + "112233445566778899AA")
        assertTrue(encrypted.validHeader)
        assertTrue(encrypted.fields.any { it.value.contains("bindkey required") })
        assertTrue(encrypted.fields.any { it.label == "MiBeacon registered flag (advertised)" && it.value == "yes" })
        assertFalse(encrypted.fields.any { it.label == "MiBeacon temperature" })
    }

    @Test fun miOptionalWifiSuffixAndMeshTrailerDoNotBecomeMeasurementObjects() {
        val result = MiBeaconDecoder.decode("60505B05013DABCD020004100285FF")
        assertTrue(result.validHeader)
        assertTrue(result.fields.any { it.label == "MiBeacon Wi-Fi MAC suffix (raw)" && it.value == "ABCD" })
        assertTrue(result.fields.any { it.label == "MiBeacon I/O capability" && it.value == "0x0002" })
        assertTrue(result.fields.any { it.value == "-12.3 °C" })
        assertFalse(MiBeaconDecoder.decode("60505B050118AB").validHeader)
        assertFalse(MiBeaconDecoder.decode("60505B050138ABCD02").validHeader)
        val mesh = MiBeaconDecoder.decode("C0505B050104100285FF1300")
        assertTrue(mesh.validHeader)
        assertTrue(mesh.fields.any { it.value == "-12.3 °C" })
        assertTrue(mesh.fields.any { it.label == "MiBeacon mesh provisioning transports (advertised)" && it.value == "PB-ADV / PB-GATT" })
        assertFalse(MiBeaconDecoder.decode("80505B050100").validHeader)
    }
}
