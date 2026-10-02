package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailLocalizationTest {
    private val translations = mapOf(
        "Phone" to "电话",
        "Smartphone" to "智能手机",
        "Peripheral" to "外设",
        "Joystick" to "操纵杆",
        "keyboard" to "键盘",
        "Imaging" to "成像设备",
        "Display" to "显示器",
        "Camera" to "相机",
        "Uncategorized" to "未分类",
        "Computer" to "计算机",
        "Major 0x%02X" to "主类别 0x%02X",
        "format %1\$s" to "格式 %1\$s",
        "Not available" to "不可用",
        "%1\$s to %2\$s dBm" to "%1\$s 至 %2\$s dBm",
        "Most likely" to "很可能是",
        "Probably" to "可能是",
        "a phone" to "电话设备",
        "Class of Device says %1\$s." to "设备类别声明为%1\$s。",
        "Possible family" to "可能的设备家族",
        "name glob" to "名称通配模式",
        "APs" to "接入点",
        "%1\$s %2\$s on the air now" to "当前广播的%1\$s个%2\$s",
        "Same %1\$s on %2\$s. Thin sample — a possible catalog family." to "%2\$s共享%1\$s，可能属于一个家族。",
        "Already tagged" to "已标记",
        "a catalog signature" to "目录中的特征",
        "Matched %1\$s. A second signature can still dual-label this radio (store UUID, product OUI)." to "已匹配%1\$s，仍可增加另一特征。",
    )
    private val translate: (String) -> String = { translations[it] ?: it }

    @Test
    fun codKeepsRawBitsAndEnglishDefaultsWhileLocalizingComposedMinor() {
        val raw = (0x05 shl 8) or (0x11 shl 2)
        assertEquals("Peripheral / Joystick + keyboard", CodDecoder.decode(raw).summary())
        val localized = CodDecoder.decode(raw, translate)
        assertEquals(raw, localized.raw)
        assertEquals("外设 / 操纵杆 + 键盘", localized.summary())
        val imaging = CodDecoder.decode((0x06 shl 8) or (0x0C shl 2), translate)
        assertEquals("成像设备 / 显示器 + 相机", imaging.summary())
        assertEquals("计算机", CodDecoder.decode(0x0100, translate).summary())
        assertEquals("LAN / Network AP / Fully available", CodDecoder.decode(0x0300).summary())
    }

    @Test
    fun unknownCodAndNonzeroFormatLocalizeOnlyTheirLabels() {
        assertEquals("主类别 0x0A", CodDecoder.decode(0x0A00, translate).major)
        assertEquals("格式 1", CodDecoder.decode(0x020D, translate).minor)
        assertEquals("format 1", CodDecoder.decode(0x020D).minor)
    }

    @Test
    fun rssiRetainsNumbersAndUnavailableSentinelSemantics() {
        assertEquals("Not available", Rssi.sessionRange(127, 127))
        assertEquals("不可用", Rssi.sessionRange(127, 127, translate = translate))
        assertEquals("-80 to -35 dBm", Rssi.sessionRange(-80, -35))
        assertEquals("-80 至 -35 dBm", Rssi.sessionRange(-80, -35, translate = translate))
        assertEquals("-35 dBm", Rssi.sessionRange(127, -35, translate = translate))
    }

    @Test
    fun codGuessClassifiesFromRawEnglishButLocalizesNestedDescription() {
        val device = sighting().copy(facts = RadioFacts(deviceClass = 0x020C))
        val english = DeviceExplain.guess(device, emptyList())
        val chinese = DeviceExplain.guess(device, emptyList(), translate)
        assertEquals(english.confidence, chinese.confidence)
        assertEquals("可能是 电话设备", chinese.headline)
        assertTrue(chinese.because.contains("设备类别声明为电话 / 智能手机。"))
        assertFalse(chinese.because.contains("Smartphone"))
    }

    @Test
    fun familyLocalizesCountsAndTemplatesWithoutChangingNameGlob() {
        val first = sighting().copy(kind = RadioKind.WIFI, name = "H2O-000000000001")
        val second = first.copy(key = "wifi:00:11:22:33:44:66", mac = "00:11:22:33:44:66", name = "H2O-000000000002")
        val english = SignatureCandidates.assessFamily(first, listOf(first, second), emptyList(), emptyList())
        val chinese = SignatureCandidates.assessFamily(first, listOf(first, second), emptyList(), emptyList(), translate)
        assertEquals(english.verdict, chinese.verdict)
        assertEquals(english.ruleLabel, chinese.ruleLabel)
        assertEquals("H2O-????????????", chinese.ruleLabel)
        assertEquals("可能的设备家族", chinese.title)
        assertTrue(chinese.body.contains("当前广播的2个接入点"))
        assertTrue(chinese.body.contains("名称通配模式"))
        assertEquals(english, english.translated { it })
    }

    @Test
    fun matchedCatalogNamesRemainUserData() {
        val fleet = Fleet(id = "operator", name = "Phone / My Custom 名称")
        val device = sighting().copy(fleetIds = setOf(fleet.id))
        val hint = SignatureCandidates.assessFamily(device, emptyList(), emptyList(), listOf(fleet), translate)
        assertEquals("已标记", hint.title)
        assertEquals("已匹配Phone / My Custom 名称，仍可增加另一特征。", hint.body)
        val reservedName = fleet.copy(name = "a catalog signature")
        assertEquals("已匹配a catalog signature，仍可增加另一特征。", SignatureCandidates.assessFamily(device, emptyList(), emptyList(), listOf(reservedName), translate).body)
    }

    private fun sighting() = Sighting(
        key = "ble:00:11:22:33:44:55", kind = RadioKind.BLE, mac = "00:11:22:33:44:55",
        name = "", rssi = -50, rssiMin = -50, rssiMax = -50, channel = 0, frequencyMhz = 0,
        vendor = null, randomized = false, hiddenSsid = false, serviceUuids = emptyList(),
        manufacturerId = null, manufacturerDataHex = "", rawHex = "", extras = "",
        firstSeen = 1L, lastSeen = 2L, hitCount = 1, fleetIds = emptySet(),
        rssiHistory = emptyList(), presence = emptyList(),
    )
}
