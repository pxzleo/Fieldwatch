package app.fieldwatch.domain

import org.junit.Assert.*
import org.junit.Test

class WifiWpsDecoderTest {
    private val sample = "102100067869616F6D69102300035233501024000430303032" +
        "1042000831323334353637381011000C5869616F4D69526F75746572"
    private fun wps(hex: String = sample) = VendorIeRecord("00:50:F2", 4, hex)

    @Test fun historySurvivesIncompleteFramesAndKeepsOnlyIdentityFields() {
        val full = RadioFacts(vendorIes = listOf(wps(sample + "1044000102"))).captureWpsIdentity(1_000L)
        val partial = RadioFacts(vendorIes = listOf(wps("104A000110"))).captureWpsIdentity(2_000L)
        val facts = full.merge(partial)
        assertEquals(1_000L, facts.wpsIdentity!!.observedAt)
        assertEquals(partial.vendorIes, facts.vendorIes)
        val fields = AdvPayloadDecoder.decodeDevice(wifi().copy(facts = facts))
        assertTrue(fields.any { it.label == "Last valid WPS identity: WPS model" && it.value == "R3P" })
        assertTrue(fields.any { it.label == "Last valid WPS identity: WPS advertised serial number" && it.value == "12345678" })
        assertFalse(fields.any { it.label.contains("configuration state") })
        assertTrue(fields.any { it.label == "Last valid WPS identity observed at" && it.value == "1970-01-01T00:00:01Z" })
        assertEquals(null, WifiWpsDecoder.identity(facts.vendorIes))
    }

    @Test fun completeNewIdentityReplacesOldSnapshotButOlderTimestampsDoNot() {
        val first = RadioFacts(vendorIes = listOf(wps())).captureWpsIdentity(1_000L)
        val changed = RadioFacts(vendorIes = listOf(wps(sample.replace("523350", "523358")))).captureWpsIdentity(2_000L)
        val latest = first.merge(changed).merge(first)
        assertEquals(changed.wpsIdentity, latest.wpsIdentity)
        val fields = WifiWpsDecoder.historicalFields(latest)
        assertTrue(fields.any { it.value == "R3X" })
        assertFalse(fields.any { it.value == "R3P" })
        assertEquals(6, WifiWpsDecoder.historicalFields(changed).size)
        val currentFields = AdvPayloadDecoder.decodeDevice(wifi().copy(facts = changed))
        assertEquals(1, currentFields.count { it.value == "R3X" })
        assertFalse(currentFields.any { it.label == "WPS model" })
    }

    @Test fun malformedAndUntimedIdentityCannotCreateOrRefreshHistory() {
        val valid = RadioFacts(vendorIes = listOf(wps())).captureWpsIdentity(1_000L)
        for (record in listOf(wps(sample + "FF"), wps("102100067869"), wps(sample).copy(type = 2))) {
            val invalid = RadioFacts(vendorIes = listOf(record)).captureWpsIdentity(2_000L)
            assertEquals(null, invalid.wpsIdentity)
            assertEquals(valid.wpsIdentity, valid.merge(invalid).wpsIdentity)
        }
        assertEquals(null, RadioFacts(vendorIes = listOf(wps())).captureWpsIdentity(0L).wpsIdentity)
    }

    @Test fun historyRoundTripsJsonlAndOldLogsRecoverIt() {
        val first = wifi().copy(facts = wifi().facts.captureWpsIdentity(1_000L))
        val latest = first.copy(lastSeen = 2_000L, facts = first.facts.merge(RadioFacts(vendorIes = listOf(wps("104A000110")))))
        fun json(device: Sighting, at: Long) = RadioSampleJson.appendTo(org.json.JSONObject()
            .put("kind", "WIFI").put("mac", device.mac).put("ts", at), device)
        val encoded = json(latest, 2_000L)
        assertEquals(latest.facts.wpsIdentity, RadioSampleJson.readFacts(encoded).wpsIdentity)
        val persisted = kotlinx.serialization.json.Json.encodeToString(RadioFacts.serializer(), latest.facts)
        assertEquals(latest.facts.wpsIdentity,
            kotlinx.serialization.json.Json.decodeFromString(RadioFacts.serializer(), persisted).wpsIdentity)
        assertEquals(null, kotlinx.serialization.json.Json.decodeFromString(RadioFacts.serializer(), "{}").wpsIdentity)
        assertEquals(latest.facts.wpsIdentity, LogReplay.parse(encoded.toString()).single().facts.wpsIdentity)
        val oldFirst = json(first, 1_000L).also { it.getJSONObject("facts").remove("wps_identity") }
        val oldLast = json(latest, 2_000L).also { it.getJSONObject("facts").remove("wps_identity") }
        val recovered = LogReplay.parse("$oldFirst\n$oldLast").single().facts
        assertEquals(first.facts.wpsIdentity!!.copy(logSnapshot = true), recovered.wpsIdentity)
        assertEquals(latest.facts.vendorIes, recovered.vendorIes)
        val bad = json(latest, 2_000L).also { it.getJSONObject("facts").getJSONObject("wps_identity").put("observed_at", -1) }
        assertThrows(org.json.JSONException::class.java) { RadioSampleJson.readFacts(bad) }
    }

    @Test fun historicalIdentityAndTimestampReachDetailsAndDebriefWithTranslation() {
        val facts = wifi().facts.captureWpsIdentity(1_000L).merge(RadioFacts(vendorIes = listOf(wps("104A000110"))))
        val device = wifi().copy(facts = facts)
        val translated: (String) -> String = { "译:$it" }
        val detail = DeviceDetailText.build(device, emptyList(), now = 2_000L, translate = translated)
        val report = DebriefReport.document(listOf(device), emptyList(), AppSettings(), emptyList(), now = 2_000L, translate = translated).toPlainText(translated)
        for (text in listOf(detail, report)) {
            assertTrue(text.contains("译:Last valid WPS identity: 译:WPS model: R3P"))
            assertTrue(text.contains("1970-01-01T00:00:01Z"))
        }
    }

    @Test fun oldCumulativeLogsClearlyLabelSnapshotTimeInsteadOfClaimingFreshReception() {
        val device = wifi()
        fun row(at: Long) = RadioSampleJson.appendTo(org.json.JSONObject()
            .put("kind", "WIFI").put("mac", device.mac).put("ts", at), device)
        val replayed = LogReplay.parse("${row(1_000L)}\n${row(2_000L)}").single()
        assertTrue(replayed.facts.wpsIdentity!!.logSnapshot)
        val fields = AdvPayloadDecoder.decodeDevice(device.copy(facts = replayed.facts))
        assertTrue(fields.any { it.label == "WPS identity log snapshot time (not a new reception)" &&
            it.value == "1970-01-01T00:00:02Z" })
        assertFalse(fields.any { it.label == "Last valid WPS identity observed at" })
        val saved = RadioSampleJson.appendTo(org.json.JSONObject(), device.copy(facts = replayed.facts))
        assertTrue(RadioSampleJson.readFacts(saved).wpsIdentity!!.logSnapshot)
    }

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
