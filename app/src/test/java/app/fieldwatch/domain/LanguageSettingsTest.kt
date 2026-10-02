package app.fieldwatch.domain

import app.fieldwatch.AppLanguage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageSettingsTest {
    @Test
    fun olderSettingsFollowSystem() {
        assertEquals(AppLanguage.SYSTEM, Json.decodeFromString<AppSettings>("{}").language)
    }

    @Test
    fun languageSurvivesSettingsExportImport() {
        AppLanguage.entries.forEach { language ->
            val pack = SettingsExchange.pack(
                AppSettings(language = language), FilterState(), emptyList(), emptyList(),
                emptySet(), "test", "test",
            )
            val imported = SettingsExchange.parse(SettingsExchange.encode(pack))
            val (config, _) = SettingsExchange.apply(PersistedConfig(), imported)
            assertEquals(language, config.settings.language)
        }
    }

    @Test
    fun platformTagsMapToSupportedSelections() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-US"))
        assertEquals(AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromTag("zh-Hans-CN"))
    }

    @Test
    fun explanationTranslationKeepsDynamicRadioValues() {
        val address = "not translated %s"
        val device = Sighting(
            key = "BLE:$address", kind = RadioKind.BLE, mac = address,
            name = "", rssi = -60, rssiMin = -60, rssiMax = -60,
            channel = 0, frequencyMhz = 0, vendor = null, randomized = false,
            hiddenSsid = false, serviceUuids = emptyList(), manufacturerId = null,
            manufacturerDataHex = "", rawHex = "", extras = "", firstSeen = 0, lastSeen = 0,
            hitCount = 1, fleetIds = emptySet(), rssiHistory = emptyList(), presence = emptyList(),
        )
        val guess = DeviceExplain.guess(device, listOf("Samsung SmartTag")) { token ->
            if (token == "Matched signature %1\$s.") "匹配 %1\$s。" else token
        }
        assertTrue(guess.because.contains("匹配 Samsung SmartTag。"))
        assertEquals("a speaker", device.copy(name = "a speaker").listTitle(translate = { "translated" }))
        assertEquals("a speaker", device.listLineText(ListLine.NAME_AND_TYPE, watchName = "a speaker", translate = { "translated" }))
        assertEquals("未命名 LE", device.listTitle(translate = {
            if (it == "unnamed LE") "未命名 LE" else it
        }))
    }

    @Test
    fun payloadTranslationLeavesModelIdentifierIntact() {
        val fields = AdvPayloadDecoder.decodeService(ServiceDataRecord("FE2C", "ABCDEF")) {
            if (it == "Model ID") "型号 ID" else it
        }
        val model = fields.first { it.label == "型号 ID" }
        assertTrue(model.value.contains("0xABCDEF"))
    }
}
