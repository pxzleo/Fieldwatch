package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportLanguageTest {
    @Test
    fun hiddenPlaceholderTranslatesWithoutChangingAdvertisedOrCustomNames() {
        val hidden = radio().copy(name = "", hiddenSsid = true)
        val translate: (String) -> String = { if (it == "<hidden>") "<隐藏>" else it }
        assertEquals("<隐藏>", hidden.reportName(emptyMap(), translate))
        assertEquals("<hidden>", hidden.copy(name = "<hidden>").reportName(emptyMap(), translate))
        assertEquals("<hidden>", hidden.reportName(mapOf(hidden.key to "<hidden>"), translate))
    }

    @Test
    fun trackerLabelsPreserveWholeCustomNamesAndTranslateBySourceId() {
        val device = radio().copy(kind = RadioKind.BLE, fleetIds = setOf("custom", "stock"))
        val names = mapOf("custom" to "Tile + Phone", "stock" to "Tile Trackers")
        assertEquals("Tile + Phone + 寻物标签", TrackerMatch.label(
            device, names, mapOf("stock" to "寻物标签"), { "unexpected translation" },
        ))
        assertEquals(TrackerMatch.Kind.FINDER, TrackerMatch.kind(device, names))
    }

    @Test
    fun translatedTemplateReordersArgumentsWithoutReinterpretingTheirContents() {
        val raw = "Device {1} %1\$s AA:BB:CC:DD:EE:FF"
        val result = ReportText.format("Name {0}; notes {1}", { "备注 {1}；名称 {0}" }, raw, "Keep {0}")
        assertEquals("备注 Keep {0}；名称 $raw", result)
    }

    @Test
    fun debriefAndAiPromptTranslateAuthorTextButKeepNamesAndObserverNotes() {
        val device = radio()
        val name = "Extra attention {0} %1\$s"
        val note = "Observer notes {1} 100%"
        val translatedInputs = mutableListOf<String>()
        val translate: (String) -> String = { source ->
            translatedInputs += source
            when (source) {
                "FIELDWATCH FIELD DEBRIEF" -> "FIELDWATCH 监测简报"
                "Observer notes" -> "观察备注"
                "DISCLAIMER" -> "免责声明"
                "Takeaway: {0}" -> "要点：{0}"
                "## Collection context" -> "## 采集背景"
                else -> source
            }
        }
        val report = DebriefReport.build(
            listOf(device), emptyList(), AppSettings(), emptyList(), now = 100_000L,
            customNames = mapOf(device.key to name), observerNotes = mapOf(device.key to note),
            translate = translate,
        )
        assertTrue(report.contains("FIELDWATCH 监测简报"))
        assertTrue(report.contains("观察备注"))
        assertTrue(report.contains("免责声明"))
        assertTrue(report.contains("要点："))
        assertTrue(report.contains(name))
        assertTrue(report.contains(note))
        assertTrue(report.contains(device.mac))
        val ai = DebriefPrompt.build(
            listOf(device), emptyList(), AppSettings(), now = 100_000L,
            customNames = mapOf(device.key to name), observerNotes = mapOf(device.key to note),
            translate = translate,
        )
        assertTrue(ai.contains("## 采集背景"))
        assertTrue(ai.contains("FIELDWATCH 监测简报"))
        assertTrue(ai.contains(name))
        assertTrue(ai.contains(note))
        assertFalse(translatedInputs.contains(name))
        assertFalse(translatedInputs.contains(note))
        assertFalse(translatedInputs.contains(device.name))
        assertFalse(translatedInputs.contains(device.mac))
    }

    @Test
    fun displaySignatureNamesDoNotChangeFinderClassification() {
        val fleet = Fleet(id = "tag", name = "Tile Trackers", kind = SignatureClass.FINDER)
        val device = radio().copy(kind = RadioKind.BLE, fleetIds = setOf(fleet.id))
        val prompt = DebriefPrompt.build(
            listOf(device), listOf(fleet), AppSettings(), now = 100_000L,
            displaySignatureNames = mapOf(fleet.id to "查找标签"),
        )
        assertTrue(prompt.contains("查找标签"))
        val finderRows = prompt.substringAfter(
            "Finder-tag-like radios (for stress-test of onboard tracking; not a tail list):",
        )
        assertTrue(finderRows.contains(device.mac))
        assertEquals("Tile Trackers", fleet.name)
    }

    @Test
    fun comparePropagatesTranslationToPlainTextAndPrivacyFooter() {
        val first = SitDiff.Side("This sit {0}", false, emptyList())
        val second = SitDiff.Side("Second sit %1\$s", false, emptyList())
        val report = SitDiff.report(first, second, demoMode = true, translate = { source ->
            when (source) {
                "DISCLAIMER" -> "免责声明"
                "Privacy" -> "隐私"
                "Takeaway: {0}" -> "要点：{0}"
                else -> source
            }
        })
        assertTrue(report.contains("免责声明"))
        assertTrue(report.contains("隐私"))
        assertTrue(report.contains("要点："))
        assertTrue(report.contains(first.name))
        assertTrue(report.contains(second.name))
    }

    private fun radio() = Sighting(
        key = "WIFI:AA:BB:CC:DD:EE:FF", kind = RadioKind.WIFI, mac = "AA:BB:CC:DD:EE:FF",
        name = "Keep name {0} 100%", rssi = -60, rssiMin = -70, rssiMax = -50,
        channel = 1, frequencyMhz = 2412, vendor = "Vendor original", randomized = false,
        hiddenSsid = false, serviceUuids = emptyList(), manufacturerId = null,
        manufacturerDataHex = "", rawHex = "", extras = "", firstSeen = 50_000L,
        lastSeen = 100_000L, hitCount = 3, fleetIds = emptySet(), rssiHistory = emptyList(),
        presence = emptyList(),
    )
}
