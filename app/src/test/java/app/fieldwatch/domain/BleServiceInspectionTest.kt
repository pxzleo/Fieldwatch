package app.fieldwatch.domain

import org.junit.Assert.*
import org.junit.Test

class BleServiceInspectionTest {
    private fun device(mac: String, uuid: String = "4669", hex: String = "6235" + "00".repeat(25)) = Sighting(
        "BLE:$mac", RadioKind.BLE, mac, "", -60, -60, -60, 0, 0, null, false, false,
        listOf(uuid), null, "", "", "", 1000, 2000, 1, emptySet(), emptyList(), emptyList(),
        facts = RadioFacts(serviceData = listOf(ServiceDataRecord(uuid, hex))))

    @Test fun unknownServicesExposeOnlyRawStructureAndExplicitMeaningLimits() {
        val fields = BleServiceInspection.fields(ServiceDataRecord("0000fdee-0000-1000-8000-00805f9b34fb", "0605" + "AA".repeat(22)))
        assertTrue(fields.any { it.value == "24 bytes; raw prefix 0605" })
        assertTrue(fields.any { it.value.contains("does not apply") })
        assertFalse(fields.any { it.label.contains("battery", true) || it.label.contains("counter", true) || it.label.contains("version", true) })
        assertTrue(BleServiceInspection.fields(ServiceDataRecord("1234FDEE-0000-1000-8000-00805F9B34FB", "0605")).isEmpty())
        assertTrue(BleServiceInspection.fields(ServiceDataRecord("FDEE", "GG")).isEmpty())
        assertTrue(BleServiceInspection.fields(ServiceDataRecord("4669", "")).isEmpty())
    }

    @Test fun sharedContentCountsUniqueBleAddressesWithoutIdentityInference() {
        val a = device("00:11:22:33:44:55")
        val b = device("00:11:22:33:44:66", "00004669-0000-1000-8000-00805F9B34FB")
        val other = device("different", hex = "6235" + "01".repeat(25))
        val fields = BleServiceInspection.sharedFields(a, listOf(a, b, b, other, b.copy(kind = RadioKind.WIFI)))
        assertEquals(1, fields.size)
        assertTrue(fields.single().value.startsWith("2 address records"))
        assertTrue(fields.single().value.endsWith("physical-device count is unconfirmed."))
        assertTrue(BleServiceInspection.sharedFields(a, listOf(a)).isEmpty())
    }

    @Test fun structureAndSharedContentReachLocalizedDetailAndReportWithoutNewClassification() {
        val a = device("00:11:22:33:44:55")
        val b = device("00:11:22:33:44:66")
        val zh: (String) -> String = { when (it) {
            "Service %1\$s payload structure" -> "服务 %1\$s 的载荷结构"
            "%1\$d bytes; raw prefix %2\$s" -> "%1\$d 字节；原始前缀 %2\$s"
            "Service %1\$s shared payload" -> "服务 %1\$s 的相同载荷"
            else -> it
        } }
        for (translate in listOf<(String) -> String>({ it }, zh)) {
            val detail = DeviceDetailText.build(a, emptyList(), peerDevices = listOf(a, b), translate = translate)
            val report = DebriefReport.build(listOf(a, b), emptyList(), AppSettings(), emptyList(), now = 2000, translate = translate)
            for (text in listOf(detail, report)) {
                assertTrue(text.contains(translate("Service %1\$s payload structure").format("4669")))
                assertTrue(text.contains(translate("%1\$d bytes; raw prefix %2\$s").format(27, "6235")))
                assertTrue(text.contains(translate("Service %1\$s shared payload").format("4669")))
            }
        }
        assertEquals(DevicePurpose.UNKNOWN, a.devicePurpose())
    }
}
