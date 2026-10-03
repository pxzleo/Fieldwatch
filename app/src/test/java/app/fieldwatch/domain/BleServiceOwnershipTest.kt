package app.fieldwatch.domain

import org.junit.Assert.*
import org.junit.Test

class BleServiceOwnershipTest {
    private fun device(uuids: List<String> = emptyList(), data: List<ServiceDataRecord> = emptyList()) = Sighting(
        key = "BLE:test", kind = RadioKind.BLE, mac = "00:11:22:33:44:55", name = "",
        rssi = -60, rssiMin = -60, rssiMax = -60, channel = 0, frequencyMhz = 0,
        vendor = null, randomized = false, hiddenSsid = false, serviceUuids = uuids,
        manufacturerId = null, manufacturerDataHex = "", rawHex = "", extras = "",
        firstSeen = 1L, lastSeen = 2L, hitCount = 1, fleetIds = emptySet(),
        rssiHistory = emptyList(), presence = emptyList(), facts = RadioFacts(serviceData = data),
    )

    @Test fun aliasesAndServiceDataIdentifyAssignmentsOnceWithoutProductGuess() {
        val sample = device(listOf("fdee", "0000FDEE-0000-1000-8000-00805F9B34FB", "FCC0"),
            listOf(ServiceDataRecord("FDEE", "0505"), ServiceDataRecord("FD2D", "")))
        val fields = AdvPayloadDecoder.decodeDevice(sample).filter { it.label.startsWith("BLE service assignment") }
        assertEquals(3, fields.size)
        assertEquals(1, fields.count { it.value.startsWith("Huawei") })
        assertEquals(2, fields.count { it.value.startsWith("Xiaomi") })
        assertTrue(fields.all { it.value.endsWith("device type and model unconfirmed.") })
        assertEquals(DevicePurpose.UNKNOWN, sample.devicePurpose())
    }

    @Test fun unrelatedCustomUuidsSubstringsAndWifiDoNotMatch() {
        assertTrue(BleServiceOwnership.fields(device(listOf("1234FDEE-0000-1000-8000-00805F9B34FB",
            "0000FCC0-0000-1000-8000-000000000000", "4669", "FE95", "FDEEAA"))).isEmpty())
        assertTrue(BleServiceOwnership.fields(device(listOf("FDEE")).copy(kind = RadioKind.WIFI)).isEmpty())
    }

    @Test fun assignmentFieldsReachDetailAndMonitoringReportInBothLanguages() {
        val sample = device(listOf("FDEE"))
        val translate: (String) -> String = { when (it) {
            "BLE service assignment" -> "蓝牙服务归属"
            "Huawei" -> "华为"
            "%1\$s-assigned service; device type and model unconfirmed." -> "%1\$s 分配的服务；设备类型与型号未确认。"
            else -> it
        } }
        val english = DeviceDetailText.build(sample, emptyList(), now = 2L)
        assertTrue(english.contains("Huawei-assigned service"))
        val detail = DeviceDetailText.build(sample, emptyList(), now = 2L, translate = translate)
        val report = DebriefReport.document(listOf(sample), emptyList(), AppSettings(), emptyList(), now = 2L, translate = translate).toPlainText(translate)
        for (text in listOf(detail, report)) {
            assertTrue(text.contains("蓝牙服务归属 · 0xFDEE"))
            assertTrue(text.contains("华为 分配的服务；设备类型与型号未确认。"))
            assertFalse(text.contains("Huawei-assigned"))
        }
    }
}
