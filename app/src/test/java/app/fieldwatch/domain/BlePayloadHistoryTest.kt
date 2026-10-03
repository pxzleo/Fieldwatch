package app.fieldwatch.domain

import app.fieldwatch.data.DeviceStore
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BlePayloadHistoryTest {
    private val measurements = "40505B05000D1004FA00F4010A100164"
    private fun frame(hex: String = measurements, at: Long = 1_000L, raw: String = "") = Sighting(
        "BLE:00:11:22:33:44:55", RadioKind.BLE, "00:11:22:33:44:55", "", -60, -60, -60,
        0, 0, null, false, false, emptyList(), null, "", raw, "", 1_000L, at, 1,
        emptySet(), emptyList(), emptyList(), facts = RadioFacts(serviceData = if (hex.isEmpty()) emptyList() else listOf(ServiceDataRecord("FE95", hex))))
    private fun capture(device: Sighting) = device.copy(facts = BlePayloadHistory.capture(device))
    private fun time(facts: RadioFacts, label: String) = facts.bleHistory.single { label in it.labels }.observedAt

    @Test fun rotatingMissingEncryptedAndDamagedFramesDoNotEraseReadings() {
        val first = capture(frame())
        for (hex in listOf("00505B0500", "48505B050004100201000000", "40505B05000A100132", "GG", "40505B0500041002FA00061002FF")) {
            val next = capture(frame(hex, 2_000L))
            val merged = first.copy(facts = first.facts.merge(next.facts))
            assertEquals(1_000L, time(merged.facts, "MiBeacon temperature"))
            assertEquals(1_000L, time(merged.facts, "MiBeacon humidity"))
            assertTrue(AdvPayloadDecoder.decodeDevice(merged).any { it.label == "Last valid BLE field: MiBeacon temperature" && it.value.startsWith("25.0 °C") })
        }
        val updated = capture(frame("40505B05000A100132", 2_000L))
        val facts = first.facts.merge(updated.facts).merge(first.facts)
        assertEquals(2_000L, time(facts, "MiBeacon battery"))
        assertEquals(1_000L, time(facts, "MiBeacon humidity"))
        assertEquals(facts.bleHistory.flatMap { it.labels }.distinct().size, facts.bleHistory.sumOf { it.labels.size })
        assertFalse(facts.bleHistory.flatMap { it.labels }.any { it.endsWith("status") })
    }

    @Test fun repeatedSourceRestoresAllItsFieldsAfterAnotherFrameTakesOwnership() {
        val first = capture(frame())
        val battery = capture(frame("40505B05000A100132", 2_000L))
        val merged = first.facts.merge(battery.facts)
        val repeated = BlePayloadHistory.capture(frame(at = 3_000L), merged.bleHistory)
        val latest = merged.merge(repeated)
        assertEquals(3_000L, time(latest, "MiBeacon battery"))
        assertTrue(AdvPayloadDecoder.decodeDevice(frame().copy(facts = latest)).any {
            it.label == "Last valid BLE field: MiBeacon battery" && it.value.startsWith("100 %")
        })
    }

    @Test fun telemetryUnavailableValuesAndPureDiagnosticsNeverReplaceLastValidFields() {
        val first = capture(frame("").copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FEAA", "20000BB81900000000010000000A")))))
        val unavailable = capture(frame("", 2_000L).copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FEAA", "2000000080000000000200000014")))))
        val facts = first.facts.merge(unavailable.facts)
        assertEquals(1_000L, time(facts, "Beacon temperature"))
        assertEquals(1_000L, time(facts, "Beacon battery voltage"))
        assertEquals(2_000L, time(facts, "Beacon transmitted advertisement count"))
        assertTrue(capture(frame("", 3_000L).copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FEAA", "2001"))))).facts.bleHistory.isEmpty())
        assertTrue(capture(frame("", 3_000L).copy(serviceUuids = listOf("FE86"))).facts.bleHistory.isEmpty())
    }

    @Test fun meshRawSourceSurvivesLaterAdvertisement() {
        val mesh = "172B0103000102030405060700000001A0A1A2A3A4A5A6A7"
        val first = capture(frame("", raw = mesh))
        val second = capture(frame("", 2_000L, "020106"))
        val fields = AdvPayloadDecoder.decodeDevice(second.copy(facts = first.facts.merge(second.facts)))
        assertTrue(fields.any { it.label == "Last valid BLE field: Mesh IV Index" && it.value.startsWith("1 ·") })
        assertEquals(1_000L, time(first.facts, "Mesh Network ID"))
    }

    @Test fun incompleteFindHubIdentifiersCannotOverwriteAValidHistoricalIdentifier() {
        fun hub(hex: String, at: Long) = frame("", at).copy(facts = RadioFacts(serviceData = listOf(ServiceDataRecord("FEAA", hex))))
        val eid = "11".repeat(20)
        val first = capture(hub("40$eid" + "00", 1000))
        for (bad in listOf("40", "4011", "40" + "11".repeat(19), "40" + "11".repeat(23))) {
            val next = capture(hub(bad, 2000))
            assertTrue(next.facts.bleHistory.isEmpty())
            val facts = first.facts.merge(next.facts)
            assertEquals(1000L, time(facts, "Find Hub EID"))
            assertTrue(AdvPayloadDecoder.decodeDevice(hub(bad, 2000).copy(facts = facts)).any {
                it.label == "Last valid BLE field: Find Hub EID" && it.value.startsWith(eid)
            })
        }
        for (size in listOf(20, 32)) for (flag in listOf("", "00")) {
            assertTrue(capture(hub("40" + "11".repeat(size) + flag, 2000)).facts.bleHistory.isNotEmpty())
        }
    }

    @Test fun jsonAndSerializationPreserveSourcesAndOldLogsUseSnapshotTime() {
        val first = capture(frame())
        fun row(device: Sighting) = RadioSampleJson.appendTo(JSONObject().put("kind", "BLE").put("mac", device.mac).put("ts", device.lastSeen), device)
        val saved = row(first)
        assertEquals(first.facts, RadioSampleJson.readFacts(saved))
        assertEquals(first.facts, Json.decodeFromString(RadioFacts.serializer(), Json.encodeToString(RadioFacts.serializer(), first.facts)))
        assertTrue(Json.decodeFromString(RadioFacts.serializer(), "{}").bleHistory.isEmpty())
        assertEquals(first.facts.bleHistory, LogReplay.parse(saved.toString()).single().facts.bleHistory)
        val old = row(frame()).also { it.getJSONObject("facts").remove("ble_history") }
        val newer = row(frame(at = 2_000L)).also { it.getJSONObject("facts").remove("ble_history") }
        val recovered = LogReplay.parse("$old\n$newer").single().facts
        assertTrue(recovered.bleHistory.all { it.logSnapshot })
        val fields = AdvPayloadDecoder.decodeDevice(frame().copy(facts = recovered))
        assertTrue(fields.any { it.value.contains("log snapshot: 1970-01-01T00:00:02Z (not a new reception)") })
        val bad = row(first).also { it.getJSONObject("facts").getJSONArray("ble_history").getJSONObject(0).put("observed_at", -1) }
        assertThrows(org.json.JSONException::class.java) { RadioSampleJson.readFacts(bad) }
    }

    @Test fun historyReLocalizesDetailsAndReports() {
        val device = capture(frame())
        val zh: (String) -> String = { when (it) {
            "Last valid BLE field: %1\$s" -> "BLE 最后有效字段：%1\$s"
            "%1\$s · observed at: %2\$s" -> "%1\$s · 接收时间：%2\$s"
            "MiBeacon temperature" -> "MiBeacon 温度"
            else -> it
        } }
        for (translate in listOf<(String) -> String>({ it }, zh)) {
            val detail = DeviceDetailText.build(device, emptyList(), now = 2_000L, translate = translate)
            val report = DebriefReport.document(listOf(device), emptyList(), AppSettings(), emptyList(), now = 2_000L, translate = translate).toPlainText(translate)
            for (text in listOf(detail, report)) {
                assertTrue(text.contains(translate("Last valid BLE field: %1\$s").format(translate("MiBeacon temperature"))))
                assertTrue(text.contains("25.0 °C"))
                assertTrue(text.contains("1970-01-01T00:00:01Z"))
            }
        }
    }

    @Test fun freshStoreFramesRefreshRepeatedPayloadButCachedFramesDoNot() {
        val store = DeviceStore()
        val observation = Observation(RadioKind.BLE, frame().mac, "", -60, 0, 0, false, emptyList(), null, "", "", "", 1_000L, facts = frame().facts)
        val first = store.ingest(observation, emptyList(), 30)
        val second = store.ingest(observation.copy(at = 2_000L), emptyList(), 30)
        assertEquals(2_000L, time(second.facts, "MiBeacon temperature"))
        val cached = store.ingest(observation.copy(at = 3_000L, fresh = false), emptyList(), 30)
        assertEquals(second.facts.bleHistory, cached.facts.bleHistory)
        val older = store.ingest(observation.copy(at = 500L), emptyList(), 30)
        assertEquals(second.facts.bleHistory, older.facts.bleHistory)
        assertEquals(first.facts.bleHistory.single().decodedLabels, second.facts.bleHistory.single().decodedLabels)
    }
}
