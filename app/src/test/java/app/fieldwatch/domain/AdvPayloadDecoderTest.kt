package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvPayloadDecoderTest {
    private fun audioFrame(code: String): String = "071901${code}519811000000" + "00".repeat(16)
    private fun bleSighting(mfgHex: String): Sighting = Sighting(
        key = "BLE:AA:BB:CC:DD:EE:99",
        kind = RadioKind.BLE,
        mac = "AA:BB:CC:DD:EE:99",
        name = "",
        rssi = -50,
        rssiMin = -50,
        rssiMax = -50,
        channel = 0,
        frequencyMhz = 0,
        vendor = null,
        randomized = false,
        hiddenSsid = false,
        serviceUuids = emptyList(),
        manufacturerId = 0x004C,
        manufacturerDataHex = mfgHex,
        rawHex = "",
        extras = "",
        firstSeen = 1L,
        lastSeen = 1L,
        hitCount = 1,
        fleetIds = emptySet(),
        rssiHistory = emptyList(),
        presence = emptyList(),
        facts = RadioFacts(mfgRecords = listOf(MfgRecord(0x004C, mfgHex))),
    )

    @Test
    fun appleIBeaconDecodesUuidMajorMinorTx() {
        // TLV 02/15 + 16-byte UUID + major + minor + calibrated TX.
        val mfg = "0215" + "E2C56DB5DFFB48D2B060D0F5A71096E0" + "0001" + "0002" + "C5"
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, mfg))
        val byLabel = fields.associate { it.label to it.value }
        assertEquals("0x02 · iBeacon", byLabel["Apple Continuity type"])
        assertEquals("e2c56db5-dffb-48d2-b060-d0f5a71096e0", byLabel["iBeacon UUID"])
        assertEquals("1 / 2", byLabel["iBeacon major / minor"])
        assertTrue(byLabel.getValue("iBeacon calibrated TX").startsWith("-59"))
    }

    @Test
    fun appleProximityPairingNamesAirPods() {
        // TLV 07, prefix 01, model 0F20 (AirPods 2nd gen), status 51, batt 98, case byte 11.
        val mfg = audioFrame("0F20")
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, mfg))
        val byLabel = fields.associate { it.label to it.value }
        assertEquals("AirPods (2nd generation)", byLabel["Product"])
        assertEquals("80% / 90%", byLabel["Battery (left / right)"])
        assertEquals("10%", byLabel["Case battery"])
        assertEquals("case", byLabel["Charging"])
    }

    @Test
    fun appleFindMyLabelsOfflineFinding() {
        val mfg = "12" + "0A" + "00".repeat(10)
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, mfg))
        assertTrue(fields.any { it.label == "Find My / Offline Finding" })
    }

    @Test
    fun appleSiriClassesOverrideGenericSignatureAndReachReports() {
        for ((code, label) in listOf("0002" to "iPhone", "0003" to "iPad", "0007" to "HomePod", "0009" to "Mac", "000A" to "Apple Watch")) {
            val device = bleSighting("080700004300${code}CA")
            assertEquals(label, AdvPayloadDecoder.appleDeviceHint(device)?.label)
            assertEquals(label, DeviceExplain.listLabel(device, listOf("Apple Device")))
            assertTrue(DeviceDetailText.build(device, listOf("Apple Device")).contains("Apple device type: $label"))
        }
    }

    @Test
    fun watchActivityDoesNotConfuseConnectedWatchFlagsWithSenderType() {
        val watch = bleSighting("10051A1C010203")
        assertEquals("Apple Watch", AdvPayloadDecoder.appleDeviceHint(watch)?.label)
        assertEquals("Apple Watch", DeviceExplain.listLabel(watch, listOf("Apple Device")))
        val phoneWithWatch = bleSighting("100517FC010203")
        assertEquals("Apple device (type unconfirmed)", AdvPayloadDecoder.appleDeviceHint(phoneWithWatch)?.label)
    }

    @Test
    fun appleAudioMappingsAndSecondaryManufacturerRecordsAreShared() {
        for ((code, model) in listOf("0A20" to "AirPods Max (Lightning)", "1F20" to "AirPods Max (USB-C)",
            "0620" to "Beats Solo3", "0320" to "Powerbeats 3", "0B20" to "Powerbeats Pro",
            "0C20" to "Beats Solo Pro", "0D20" to "Powerbeats 4", "1020" to "Beats Flex",
            "1120" to "Beats Studio Buds", "1220" to "Beats Fit Pro", "1720" to "Beats Studio Pro",
            "1B20" to "AirPods 4 (ANC)")) {
            val device = bleSighting(audioFrame(code)).copy(manufacturerId = 0x9999, manufacturerDataHex = "00")
            assertEquals(model, AdvPayloadDecoder.appleDeviceHint(device)?.label)
            assertEquals(model, DeviceExplain.listLabel(device, listOf("Apple audio")))
            assertEquals(model, AdvPayloadDecoder.decodeDevice(device).first { it.label == "Product" }.value)
        }
    }

    @Test
    fun unknownAndTruncatedFramesDoNotClaimAnExactAppleDevice() {
        for (hex in listOf("0806000043000002", "080700004300FFFFCA", "10011A", "0707FF0A2051981100", "0707010A2051981100", audioFrame("FFFF"))) {
            assertTrue(AdvPayloadDecoder.appleDeviceHint(bleSighting(hex))!!.weight < 9)
            assertTrue(AdvPayloadDecoder.roleHints(bleSighting(hex)).none { it.weight >= 9 })
        }
        val truncatedTail = bleSighting("0807000043000002CA10051A")
        assertEquals(null, AdvPayloadDecoder.appleDeviceHint(truncatedTail))
        assertTrue(AdvPayloadDecoder.roleHints(truncatedTail).none { it.weight >= 9 })
        assertTrue(AdvPayloadDecoder.decodeDevice(truncatedTail).none { it.value.contains("iPhone") })
        val audioTail = bleSighting(audioFrame("0A20") + "10051A")
        assertTrue(AdvPayloadDecoder.decodeDevice(audioTail).none { it.label == "Product" })
        assertTrue(!DeviceDetailText.build(audioTail, listOf("Apple audio")).contains("AirPods Max"))
    }

    @Test
    fun findMyAndIBeaconDoNotProveAirTagOrAppleHardware() {
        val findMy = bleSighting("12020000")
        assertEquals("Find My device (type unconfirmed)", AdvPayloadDecoder.appleDeviceHint(findMy)?.label)
        assertTrue(!DeviceExplain.guess(findMy, listOf("Apple AirTags")).headline.contains("AirTag"))
        val beacon = bleSighting("0215" + "00".repeat(21))
        assertEquals(null, AdvPayloadDecoder.appleDeviceHint(beacon))
    }

    @Test
    fun appleTypeAndModelUseCallerTranslationInDetailsAndReports() {
        val device = bleSighting(audioFrame("0F20"))
        val translate: (String) -> String = { when (it) {
            "Apple device type" -> "苹果设备类型"
            "AirPods (2nd generation)" -> "AirPods（第 2 代）"
            else -> it
        } }
        assertEquals("AirPods（第 2 代）", AdvPayloadDecoder.appleDeviceHint(device, translate)?.label)
        assertTrue(DeviceDetailText.build(device, listOf("Apple audio"), translate = translate).contains("苹果设备类型: AirPods（第 2 代）"))
    }

    @Test
    fun unknownCompanyYieldsNoFields() {
        assertTrue(AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x9999, "AABB")).isEmpty())
        assertTrue(AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x004C, "XYZ")).isEmpty())
    }

    @Test
    fun fastPairThreeByteModelIdIsPairing() {
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FE2C", "000006"))
        val byLabel = fields.associate { it.label to it.value }
        assertTrue(byLabel.getValue("Google Fast Pair").contains("pairing mode"))
        assertEquals("Google Pixel Buds  (0x000006)", byLabel["Model ID"])
    }

    @Test
    fun fastPairLongerPayloadIsAccountKeyBloom() {
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FE2C", "01020304050607"))
        assertTrue(fields.single().value.contains("Already paired"))
    }

    @Test
    fun eddystoneUidSplitsNamespaceAndInstance() {
        val bytes = "00" + "C5" + "00112233445566778899" + "AABBCCDDEEFF" + "0000"
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FEAA", bytes))
        val byLabel = fields.associate { it.label to it.value }
        assertEquals("00112233445566778899", byLabel["Eddystone-UID namespace"])
        assertEquals("AABBCCDDEEFF", byLabel["Eddystone-UID instance"])
    }

    @Test
    fun findHubFrameIsNotEddystone() {
        val eid = "11".repeat(20)
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FEAA", "41$eid" + "00"))
        assertEquals("separated (unwanted-tracking mode)", fields.first { it.label == "Find Hub" }.value)
        assertTrue(fields.none { it.label.startsWith("Eddystone") })
    }

    @Test
    fun eddystoneUrlExpandsSchemeAndSuffix() {
        // frame 10, tx C5, scheme 01 (https://www.), "example", 07 (.com)
        val bytes = "10" + "C5" + "01" + "example".toByteArray().toHexUpper() + "07"
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FEAA", bytes))
        assertEquals("https://www.example.com", fields.single().value)
    }

    @Test
    fun microsoftCdpNamesDeviceType() {
        val fields = AdvPayloadDecoder.decodeManufacturer(MfgRecord(0x0006, "0109"))
        assertTrue(fields.single().value.contains("Windows desktop"))
    }

    @Test
    fun roleHintsFlagIBeaconAndTeslaKey() {
        val ibeacon = "0215" + "E2C56DB5DFFB48D2B060D0F5A71096E0" + "00010002" + "C5"
        val hints = AdvPayloadDecoder.roleHints(bleSighting(ibeacon))
        assertTrue(hints.any { it.label == "an iBeacon" })

        // Tesla prefix carries the "0215" TLV header; the body still needs
        // major/minor/tx (5 bytes) to reach the declared iBeacon length.
        val tesla = bleSighting(DefaultCatalog.TESLA_IBEACON_MFG_PREFIX + "00010002C5")
        assertTrue(AdvPayloadDecoder.roleHints(tesla).any { it.label.contains("Tesla") })
    }
}
