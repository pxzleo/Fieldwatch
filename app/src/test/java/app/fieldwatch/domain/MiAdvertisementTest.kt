package app.fieldwatch.domain

import org.junit.Assert.*
import org.junit.Test

class MiAdvertisementTest {
    // Same frame shape as a v5 LYWSD03MMC broadcast; MAC and ciphertext are synthetic.
    private val encrypted = "58585B0530554433221100" + "112233445566778899AA"
    private val plain = "40505B0530" + "04100285FF061002C8010A100164"

    @Test fun encryptedHeaderNamesSensorWithoutReadingCiphertext() {
        val decoded = MiBeaconDecoder.decode(encrypted)
        assertTrue(decoded.validHeader)
        assertEquals(0x055B, decoded.productId)
        assertEquals("5", decoded.fields.first { it.label == "MiBeacon version" }.value)
        assertEquals("48", decoded.fields.first { it.label == "MiBeacon frame counter" }.value)
        assertEquals("00:11:22:33:44:55", decoded.fields.first { it.label == "MiBeacon advertised MAC" }.value)
        assertTrue(decoded.fields.any { it.value == "Encrypted — readings unavailable" })
        assertFalse(decoded.fields.any { it.label in listOf("MiBeacon temperature", "MiBeacon humidity", "MiBeacon battery") })
        assertEquals(decoded.fields, AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x038F, encrypted)))
        val device = radio("").copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FE95", encrypted))))
        assertTrue(DeviceExplain.guess(device, emptyList()).headline.contains("LYWSD03MMC"))
        val unknown = MiBeaconDecoder.decode(encrypted.replace("5B05", "3412"))
        assertTrue(unknown.fields.any { it.label == "MiBeacon product ID" && it.value == "0x1234" })
        assertFalse(unknown.fields.any { it.value.contains("LYWSD03MMC") })
    }

    @Test fun plaintextSignedTemperatureHumidityBatteryAndCompoundObjects() {
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FE95", plain))
        assertTrue(fields.any { it.value == "-12.3 °C" })
        assertTrue(fields.any { it.value == "45.6 %" })
        assertTrue(fields.any { it.value == "100 %" })
        val compound = MiBeaconDecoder.decode("40505B05300D100485FFC801")
        assertTrue(compound.fields.any { it.value == "-12.3 °C" })
        assertTrue(compound.fields.any { it.value == "45.6 %" })
        val unknown = MiBeaconDecoder.decode("40505B0530999902AABB")
        assertTrue(unknown.fields.any { it.label == "MiBeacon unknown object" && it.value == "0x9999: AABB" })
    }

    @Test fun optionalMacCapabilityAndIoLengthsUseCorrectObjectOffset() {
        val optional = "70505B0530554433221100200200" + "04100285FF"
        val decoded = MiBeaconDecoder.decode(optional)
        assertTrue(decoded.validHeader)
        assertTrue(decoded.fields.any { it.value == "-12.3 °C" })
        assertTrue(decoded.fields.any { it.label == "MiBeacon I/O capability" && it.value == "0x0002" })
        val capOnly = MiBeaconDecoder.decode("60505B05300004100285FF")
        assertTrue(capOnly.fields.any { it.value == "-12.3 °C" })
        for (bad in listOf("GG", "1", "40505B05", "50505B05305544", "60505B05302001", "40505B053004100285", "40505B053004100100", "48505B05300102")) {
            val result = MiBeaconDecoder.decode(bad)
            assertFalse(bad, result.validHeader)
            assertTrue(bad, result.fields.any { it.label == "MiBeacon parse status" })
        }
        assertTrue(MiBeaconDecoder.decode("40405B053004100285FF").fields.any { it.value == "Unsupported MiBeacon version" })
        assertTrue(AdvPayloadDecoder.decodeManufacturer(MfgRecord(76, "GG")).isEmpty())
        assertTrue(AdvPayloadDecoder.decodeService(ServiceDataRecord("FE2C", "GG")).isEmpty())
    }

    @Test fun emptyScaleDoesNotShowZeroAndUnstableValuesAreProvisional() {
        val device = radio("MI_SCALE")
        val empty = AdvPayloadDecoder.decodeService(ServiceDataRecord("181D", "920000B2080102120136"), device)
        assertEquals(listOf(AdvPayloadDecoder.Field("Mi Scale weighing state", "Empty / load removed — no current weight")), empty)
        val unstable = AdvPayloadDecoder.decodeService(ServiceDataRecord("181D", scale(0x02, 14000)), device)
        assertTrue(unstable.any { it.label == "Mi Scale provisional weight" && it.value == "70.00 kg" })
        assertFalse(unstable.any { it.label == "Mi Scale weight" })
        val removedStable = AdvPayloadDecoder.decodeService(ServiceDataRecord("181D", scale(0xA2, 14000)), device)
        assertFalse(removedStable.any { it.value.contains("kg") })
    }

    @Test fun scaleNativeUnitsAndBodyImpedanceRespectFlagsAndBrandContext() {
        val device = radio("MI SCALE sample")
        for ((status, weight) in listOf(0x22 to "70.00 kg", 0x23 to "140.00 lb", 0x32 to "140.00 jin")) {
            assertTrue(AdvPayloadDecoder.decodeService(ServiceDataRecord("181D", scale(status, 14000)), device).any { it.value == weight })
        }
        val body = ByteArray(13).apply { this[0] = 2; this[1] = 0x22; this[9] = 0xF4.toByte(); this[10] = 1; this[11] = 0xB0.toByte(); this[12] = 0x36 }
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("181B", body.toHexUpper()), radio("MIBCS"))
        assertTrue(fields.any { it.value == "70.00 kg" })
        assertTrue(fields.any { it.label == "Mi Scale impedance" && it.value == "500 Ω" })
        body[1] = 0x60
        val jin = AdvPayloadDecoder.decodeService(ServiceDataRecord("181B", body.toHexUpper()), radio("MIBFS"))
        assertTrue(jin.any { it.value == "140.00 jin" })
        assertFalse(jin.any { it.label == "Mi Scale impedance" })
        body[0] = 3; body[1] = 0x22
        assertTrue(AdvPayloadDecoder.decodeService(ServiceDataRecord("181B", body.toHexUpper()), radio("MIBFS")).any { it.value == "140.00 lb" })
        body[1] = 0xA2.toByte()
        val emptyBody = AdvPayloadDecoder.decodeService(ServiceDataRecord("181B", body.toHexUpper()), radio("MIBFS"))
        assertEquals(1, emptyBody.size)
        assertTrue(emptyBody.single().value.contains("no current weight"))
        val record = ServiceDataRecord("181D", scale(0x22, 14000))
        assertTrue(AdvPayloadDecoder.decodeService(record).isEmpty())
        assertTrue(AdvPayloadDecoder.decodeService(record, radio("Other scale")).isEmpty())
        assertTrue(AdvPayloadDecoder.decodeService(record, radio("MI_SCALE").copy(kind = RadioKind.WIFI)).isEmpty())
        assertFalse(AdvPayloadDecoder.decodeService(record, radio("").copy(fleetIds = setOf("fleet-mi-scale"))).isEmpty())
        assertTrue(AdvPayloadDecoder.decodeService(ServiceDataRecord("181D", "GG"), device).any { it.value == "Malformed Mi Scale payload" })
        assertTrue(AdvPayloadDecoder.decodeService(ServiceDataRecord("181B", "00"), device).any { it.value == "Truncated Mi Scale payload" })
        assertTrue(AdvPayloadDecoder.decodeService(ServiceDataRecord("181D", "00".repeat(11)), device).any { it.value == "Unsupported Mi Scale payload length" })
    }

    @Test fun detailAndDebriefIncludeTranslatedProtocolFieldsAlongsideSignatureDecode() {
        val translate: (String) -> String = { "译:$it" }
        val fleet = DefaultCatalog.domesticFamilies().first { it.id == "fleet-mi-scale" }.copy(decode = FleetDecode(
            source = DecodeSource.SERVICE_DATA, serviceUuid = "181D", fields = listOf(DecodeField("status", "Operator status", 0, type = DecodeType.U8))))
        val device = radio("MI_SCALE").copy(fleetIds = setOf(fleet.id), facts = RadioFacts(serviceData = listOf(ServiceDataRecord("181D", scale(0x22, 14000)))))
        val detail = DeviceDetailText.build(device, listOf(fleet.name), now = 2L, fleets = listOf(fleet), translate = translate)
        assertTrue(detail.contains("译:Mi Scale weight: 70.00 kg"))
        assertTrue(detail.contains("MI_SCALE"))
        val report = DebriefReport.document(listOf(device), listOf(fleet), AppSettings(), emptyList(), now = 2L, translate = translate).toPlainText(translate)
        assertTrue(report.contains("Operator status:"))
        assertTrue(report.contains("译:Mi Scale weight: 70.00 kg"))
        val encryptedDevice = radio("LYWSD03MMC").copy(fleetIds = setOf("fleet-lywsd03mmc"), facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FE95", encrypted))))
        val encryptedReport = DeviceDetailText.build(encryptedDevice, emptyList(), now = 2L, translate = translate)
        assertTrue(encryptedReport.contains("译:MiBeacon encryption: 译:Encrypted — readings unavailable"))
        assertTrue(encryptedReport.contains("00:11:22:33:44:55"))
        assertFalse(encryptedReport.contains("译:MiBeacon temperature:"))
    }

    @Test fun factoryNamesMatchScaleCatalogOnlyOnBle() {
        val fleet = DefaultCatalog.domesticFamilies().first { it.id == "fleet-mi-scale" }
        for (name in listOf("MI_SCALE", "MI SCALE 2", "MIBCS", "MIBFS")) {
            assertTrue(name, fleet.id in SignatureEngine().match(listOf(radio(name)), listOf(fleet)).values.single())
            assertTrue(name, SignatureEngine().match(listOf(radio(name).copy(kind = RadioKind.WIFI)), listOf(fleet)).values.single().isEmpty())
        }
    }

    @Test fun classicCompany4CV3DoorLockDecodesAndMatchesMiLockWhileAppleIbeaconDoesNot() {
        // Captured 'Mi Automatic Smart Door Lock' frame: v3 header, product 1B01, company 004C.
        val lockHex = "0631011BD242E6BC4A0600010001024C0DD969"
        val decoded = MiBeaconDecoder.decode(lockHex)
        assertTrue(decoded.validHeader)
        assertEquals(0x1B01, decoded.productId)
        assertEquals("3", decoded.fields.first { it.label == "MiBeacon version" }.value)
        assertTrue(decoded.fields.any { it.label == "MiBeacon product ID" && it.value == "0x1B01 · Mi Automatic Smart Door Lock" })
        val lock = radio("Mi Automatic Smart Door Lock").copy(manufacturerId = 0x004C, manufacturerDataHex = lockHex)
        assertTrue(MiBeaconDecoder.identities(lock).any { it.productId == 0x1B01 && it.validHeader })
        val catalog = DefaultCatalog.fleets().associateBy { it.id }
        val miLock = catalog.getValue("fleet-mi-lock")
        assertTrue(miLock.rules.any { it.kind == RuleKind.MIBEACON_PRODUCT_ID && it.text == "1B01" })
        assertTrue(miLock.rules.any { it.kind == RuleKind.NAME_CONTAINS && it.text == "Mi Automatic Smart Door Lock" })
        assertTrue("fleet-mi-lock" in SignatureEngine().match(listOf(lock), listOf(miLock)).values.single())
        // Company 004C Apple iBeacon frame (version 1) must not be read as a MiBeacon product.
        val apple = radio("").copy(manufacturerId = 0x004C, manufacturerDataHex = "121000AABBCCDDEEFF00112233445566778899" + "0001" + "0002")
        assertTrue(MiBeaconDecoder.identities(apple).isEmpty())
        val appleDecoded = MiBeaconDecoder.decode(apple.manufacturerDataHex)
        assertFalse(appleDecoded.validHeader)
        val appleMfg = apple.facts.mfgRecords.ifEmpty { listOf(MfgRecord(0x004C, apple.manufacturerDataHex)) }
        val appleFields = AdvPayloadDecoder.decodeManufacturer(appleMfg.single())
        assertTrue(appleFields.any { it.label.contains("Apple", true) })
        assertFalse(appleFields.any { it.label == "MiBeacon version" })
    }

    @Test fun appleHomeKitAndIbeaconFramesOnCompany4CStayAppleNotMiBeacon() {
        // Apple HomeKit pairing advertisement: 0504 0000 3309 00 + 8-byte pairing ID
        // (control word 0x0405, version nibble 4) — never a MiBeacon classic v3 header.
        val homekit = "05040000330900A1B2C3D4E5F60718"
        // Apple iBeacon: 1210 00 + UUID16 + major + minor (version nibble 1).
        val ibeacon = "121000E2C56DB5DFAE4826BBAB2F06C64534F700010000"
        for (hex in listOf(homekit, ibeacon)) {
            assertNull(hex, MiBeaconDecoder.classicMiBeaconVersion(hex))
            assertFalse(hex, MiBeaconDecoder.decode(hex).validHeader)
            val device = radio("").copy(manufacturerId = 0x004C, manufacturerDataHex = hex)
            assertTrue(hex, MiBeaconDecoder.identities(device).isEmpty())
            val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, hex))
            assertFalse(hex, fields.any { it.label.contains("MiBeacon", true) })
        }
        // The captured Mi lock frame keeps its classic v3 version nibble.
        assertEquals(3, MiBeaconDecoder.classicMiBeaconVersion("0631011BD242E6BC4A0600010001024C0DD969"))
    }

    private fun scale(status: Int, raw: Int): String = ByteArray(10).apply {
        this[0] = status.toByte(); this[1] = raw.toByte(); this[2] = (raw ushr 8).toByte()
    }.toHexUpper()

    private fun radio(name: String) = Sighting(key = "BLE:00:11:22:33:44:55", kind = RadioKind.BLE,
        mac = "00:11:22:33:44:55", name = name, rssi = -50, rssiMin = -50, rssiMax = -50, channel = 0, frequencyMhz = 0,
        vendor = null, randomized = false, hiddenSsid = false, serviceUuids = emptyList(), manufacturerId = null,
        manufacturerDataHex = "", rawHex = "", extras = "", firstSeen = 1L, lastSeen = 1L, hitCount = 1,
        fleetIds = emptySet(), rssiHistory = emptyList(), presence = emptyList())
}
