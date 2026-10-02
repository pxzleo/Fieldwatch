package app.fieldwatch.domain

import app.fieldwatch.data.ConfigStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DiscoveryV92Test {
    private val catalog = DefaultCatalog.fleets()
    private val engine = SignatureEngine()
    private fun radio(kind: RadioKind, facts: RadioFacts = RadioFacts()) = Sighting(
        key = "$kind:00:11:22:00:00:01", kind = kind, mac = "00:11:22:00:00:01", name = "",
        rssi = -60, rssiMin = -60, rssiMax = -60, channel = 0, frequencyMhz = 0, vendor = null,
        randomized = false, hiddenSsid = false, serviceUuids = emptyList(), manufacturerId = null,
        manufacturerDataHex = "", rawHex = "", extras = "", firstSeen = 1000, lastSeen = 1000,
        hitCount = 1, fleetIds = emptySet(), rssiHistory = emptyList(), presence = emptyList(), facts = facts,
    )
    private fun attr(type: Int, text: String): String {
        val bytes = text.toByteArray()
        return "%04X%04X".format(type, bytes.size) + bytes.toHexUpper()
    }
    private fun wps(manufacturer: String, model: String, tail: String = "1054000800060050F2040001") =
        VendorIeRecord("00:50:F2", 4, attr(0x1021, manufacturer) + attr(0x1023, model) + tail)
    private fun wifi(record: VendorIeRecord) = radio(RadioKind.WIFI, RadioFacts(vendorIes = listOf(record)))
    private fun hits(device: Sighting, fleets: List<Fleet> = catalog) = engine.match(listOf(device), fleets, 1000).getValue(device.key)
    private fun mi(pid: Int, control: String = "0050", tail: String = "") =
        radio(RadioKind.BLE, RadioFacts(serviceData = listOf(ServiceDataRecord("FE95", control +
            "%02X%02X".format(pid and 255, pid ushr 8) + "00" + tail))))

    @Test fun completeWpsClassifiesUnknownOuiWithoutUsingTheProtocolOuiAsBrand() {
        for ((maker, model, id) in listOf(
            Triple("Fiberhome", "Fiberhome", "fiberhome-wifi"), Triple("huaweitec", "WAP", "huawei"),
            Triple("Huawei Technology Co.,Ltd", "Wireless AP", "huawei"), Triple("xiaomi", "R3P", "xiaomi-wifi"),
            Triple("TP-Link", "Router", "tplink"), Triple("H3C", "AP", "h3c-wifi"), Triple("ZTE", "ZXHN F", "zte-wifi"),
        )) {
            val d = wifi(wps(maker, model))
            assertTrue(maker, "fleet-$id" in hits(d))
            assertFalse("fleet-wps-access-point" in hits(d))
        }
        val fiberhome = WifiWpsDecoder.identity(listOf(wps("Fiberhome", "Fiberhome")))!!
        assertEquals("Fiberhome", fiberhome.identityLabel())
        assertFalse("fleet-microsoft" in hits(wifi(wps("Unknown", "Model"))))
    }

    @Test fun chipsetDoesNotBecomeFinishedBrandAndIncompleteRecordsAreNotIdentity() {
        for (maker in listOf("Realtek Semiconductor Corp.", "Ralink Technology, Corp.")) {
            val d = wifi(wps(maker, "Wireless Access Point"))
            assertEquals(setOf("fleet-wps-access-point"), hits(d))
            assertTrue(DeviceExplain.listLabel(d)!!.contains("brand unconfirmed"))
            assertTrue(DeviceExplain.listLabel(d)!!.contains("Chipset platform"))
        }
        val complete = wps("Fiberhome", "Fiberhome")
        for (record in listOf(complete.copy(dataHex = complete.dataHex + "10"),
            complete.copy(dataHex = complete.dataHex + "104400020102"),
            complete.copy(type = 1), complete.copy(oui = "00:11:22"),
            complete.copy(dataHex = attr(0x1021, "Fiberhome")))) {
            assertFalse("fleet-fiberhome-wifi" in hits(wifi(record)))
        }
        val separate = radio(RadioKind.WIFI, RadioFacts(vendorIes = listOf(
            VendorIeRecord("00:50:F2", 4, attr(0x1021, "Fiberhome")),
            VendorIeRecord("00:50:F2", 4, attr(0x1023, "Fiberhome")),
        )))
        assertTrue(hits(separate).isEmpty())
        val disabled = catalog.map { f -> f.copy(rules = f.rules.map { it.copy(enabled = false) }) }
        assertTrue(hits(wifi(complete), disabled).isEmpty())
    }

    @Test fun validMiProductHeadersClassifyPlaintextAndEncryptedWithoutReadingCiphertext() {
        for ((pid, id) in listOf(0x055B to "lywsd03mmc", 0x2832 to "mi-thermometer", 0x4C47 to "mi-thermometer",
            0x55B5 to "mi-thermometer", 0x5BEA to "mi-thermometer", 0x2542 to "lywsd02mmc", 0x16E4 to "lywsd02mmc",
            0x0576 to "cgd1", 0x30D9 to "mijia-s400", 0x3BD5 to "mijia-s400", 0x48CF to "mijia-s400",
            0x0863 to "mi-water-leak", 0x098C to "mi-lock", 0x0784 to "mi-lock", 0x0E39 to "mi-lock")) {
            for (d in listOf(mi(pid), mi(pid, "4850", "04100201000000"))) {
                assertTrue(pid.toString(16), "fleet-$id" in hits(d))
                assertTrue(AdvPayloadDecoder.roleHints(d).any { MiBeaconDecoder.product(pid)!!.model in it.label })
            }
        }
        val encrypted = mi(0x055B, "4850", "04100201000000")
        assertFalse(AdvPayloadDecoder.decodeDevice(encrypted).any { it.label == "MiBeacon temperature" })
        for (d in listOf(mi(0x1234), mi(0x055B, "0040"), mi(0x055B, "1050", "AA"))) {
            assertFalse("fleet-lywsd03mmc" in hits(d))
        }
        assertFalse("fleet-lywsd03mmc" in hits(radio(RadioKind.BLE).copy(rawHex = "00505B0500")))
    }

    @Test fun standardPlaintextObjectsAreLengthCheckedAndUnknownStatesRemainRaw() {
        val hex = "40505B05000710035634121210010214100101151001001910010918100107"
        val fields = MiBeaconDecoder.decode(hex).fields.associate { it.label to it.value }
        assertEquals("1193046", fields["MiBeacon light raw value"])
        assertEquals("yes", fields["MiBeacon water detected"])
        assertEquals("no", fields["MiBeacon smoke detected"])
        assertEquals("0x09", fields["MiBeacon door/window state"])
        assertEquals("0x02", fields["MiBeacon on/off raw state"])
        assertEquals("0x07", fields["MiBeacon light raw state"])
        assertFalse(MiBeaconDecoder.decode("40505B05000710020102").validHeader)
        assertEquals("No advertisement payload captured", AdvPayloadDecoder.decodeDevice(radio(RadioKind.BLE)).single().value)
        assertEquals("Payload parser unsupported", AdvPayloadDecoder.decodeDevice(radio(RadioKind.BLE,
            RadioFacts(serviceData = listOf(ServiceDataRecord("FFFF", "01"))))).single().value)
    }

    @Test fun migrationPreservesOperatorMetadataDisabledRulesAndConjunctions() {
        val base = catalog.first { it.id == "fleet-xiaomi-wifi" }
        val off = base.copy(name = "Operator", notes = "Notes", attentionNote = "Caution", enabled = false,
            rules = listOf(MatchRule(RuleKind.WPS_MANUFACTURER, text = "Xiaomi", radio = RadioKind.WIFI, enabled = false)))
        val and = base.copy(id = "fleet-h3c-wifi", matchAny = false, rules = listOf(MatchRule(RuleKind.NAME_GLOB, text = "H3C_*")))
        val custom = base.copy(id = "fleet-fiberhome-wifi", builtIn = false)
        val result = ConfigStore.appendCatalogV92(listOf(off, and, custom))
        assertEquals(off, result[0])
        assertEquals(and, result[1])
        assertEquals(custom, result[2])
        assertEquals(result, ConfigStore.appendCatalogV92(result))
    }

    @Test fun historyReclassifiesAsACopyAndKeepsClusterPool() {
        val old = wifi(wps("Fiberhome", "Fiberhome")).copy(fleetIds = setOf("fleet-dji"))
        val current = listOf(old).reclassify(catalog)
        assertTrue("fleet-fiberhome-wifi" in current.single().fleetIds)
        assertEquals(setOf("fleet-dji"), old.fleetIds)
        val cluster = Fleet("test-cluster", "Test", minPeers = 2, clusterByOui = true)
        val peer = old.copy(key = "WIFI:00:11:22:00:00:02", mac = "00:11:22:00:00:02")
        assertTrue(listOf(old, peer).reclassify(listOf(cluster)).all { "test-cluster" in it.fleetIds })
    }

    @Test fun invalidFactsHaveExplicitErrorsRatherThanDefaultZeroValues() {
        for ((key, value) in listOf("tx_power_dbm" to "invalid", "appearance" to "1.5", "advertising_interval_ms" to "invalid", "connectable" to "yes")) {
            val sample = JSONObject().put("facts", JSONObject().put(key, value))
            val error = assertThrows(org.json.JSONException::class.java) { RadioSampleJson.readFacts(sample) }
            assertTrue(error.message.orEmpty().contains("facts.$key"))
        }
        assertThrows(org.json.JSONException::class.java) {
            RadioSampleJson.readFacts(JSONObject().put("facts", JSONObject().put("mfg_records", "invalid")))
        }
        assertEquals(RadioFacts(), RadioSampleJson.readFacts(JSONObject().put("facts", JSONObject().put("appearance", JSONObject.NULL))))
    }

    @Test fun logsPreserveAllProviderFactsAndNullableFields() {
        val first = radio(RadioKind.BLE, RadioFacts(serviceData = listOf(ServiceDataRecord("FE95", "00505B0500"), ServiceDataRecord("FFFF", "AA")),
            mfgRecords = listOf(MfgRecord(1, "AA"), MfgRecord(2, "BB")),
            vendorIes = listOf(VendorIeRecord("00:11:22", 1, "AA"), VendorIeRecord("00:11:22", 1, "BB"))))
        val second = first.copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FE95", "0050322800")),
            mfgRecords = listOf(MfgRecord(3, "CC")),
            vendorIes = listOf(VendorIeRecord("00:11:22", 1, "CC"), VendorIeRecord("00:11:22", 1, "DD"),
                VendorIeRecord("00:22:33", 2, "EE"))))
        fun row(d: Sighting) = RadioSampleJson.appendTo(JSONObject().put("kind", d.kind.name).put("mac", d.mac).put("ts", 1000), d).toString()
        val log = LogReplay.parse(row(first) + "\n" + row(second)).single()
        assertEquals(3, log.facts.mfgRecords.size)
        assertEquals(listOf(ServiceDataRecord("FE95", "0050322800"), ServiceDataRecord("FFFF", "AA")), log.facts.serviceData)
        assertEquals(second.facts.vendorIes, log.facts.vendorIes)
        val current = radio(RadioKind.BLE, log.facts)
        assertTrue("fleet-mi-thermometer" in hits(current))
        assertFalse("fleet-lywsd03mmc" in hits(current))
        assertNull(log.facts.txPowerDbm)
        assertNull(log.facts.appearance)
        assertTrue(SignatureCandidates.analyze(listOf(log), catalog).unmatchedRadios == 0)
        val wpsRecord = wps("Fiberhome", "Fiberhome", attr(0x1024, "123456") + attr(0x1011, "AP") + attr(0x1042, "Serial") + "10440001021054000800060050F2040001")
        val captured = wifi(wpsRecord)
        val replay = LogReplay.parse(row(captured)).single()
        assertEquals(captured.facts, replay.facts)
        val fields = AdvPayloadDecoder.decodeDevice(captured)
        assertTrue(fields.any { it.label == "WPS advertised serial number" })
        assertTrue(fields.any { it.label == "WPS configuration state" })
        assertTrue(fields.all { DeviceDetailText.build(captured, emptyList(), now = 1000).contains("${it.label}: ${it.value}") })
    }
}
