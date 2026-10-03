package app.fieldwatch.domain

import org.junit.Assert.*
import org.junit.Test

class WpsAssociationTest {
    private val id = "0102030405060708090A0B0C0D0E0F10"
    private fun attr(type: String, hex: String) = type + "%04X".format(hex.length / 2) + hex
    private fun text(type: String, value: String) = attr(type, value.toByteArray().toHexUpper())
    private fun device(mac: String, uuid: String = id, model: String = "R7000") = Sighting(
        "WIFI:$mac", RadioKind.WIFI, mac, "Network", -60, -60, -60, 1, 2412, null, false,
        false, emptyList(), null, "", "", "", 1000, 2000, 1, emptySet(), emptyList(), emptyList(),
        facts = RadioFacts(vendorIes = listOf(VendorIeRecord("0050F2", 4,
            text("1021", "NETGEAR") + text("1023", model) + text("1042", "SERIAL") + attr("1047", uuid)))))

    @Test fun exactUuidProducesSymmetricCandidatesAndCorroborationWithoutMerging() {
        val a = device("00:11:22:33:44:55")
        val b = device("00:11:22:33:44:66").copy(name = "Other", frequencyMhz = 5180)
        val devices = listOf(a, b, b)
        assertEquals(1, WpsAssociation.groups(devices).size)
        val fields = WpsAssociation.fields(a, devices)
        assertTrue(fields.first().value.contains(b.mac))
        assertFalse(fields.first().value.contains(a.mac))
        assertTrue(fields.any { it.value.contains("Manufacturer and model agree") })
        assertTrue(fields.any { it.value.contains("serial numbers agree") })
        assertTrue(WpsAssociation.fields(b, devices).first().value.contains(a.mac))
        assertEquals(3, devices.size)
    }

    @Test fun zeroMalformedTruncatedConflictingAndDifferentUuidDoNotInventLinks() {
        val a = device("00:11:22:33:44:55")
        assertNull(WpsAssociation.uuid(device("zero", "0".repeat(32))))
        assertNull(WpsAssociation.uuid(device("short", "01")))
        assertNull(WpsAssociation.uuid(a.copy(facts = RadioFacts(vendorIes = listOf(
            a.facts.vendorIes.single().copy(dataHex = a.facts.vendorIes.single().dataHex.dropLast(2)))))))
        assertTrue(WpsAssociation.fields(a, listOf(device("other", "11".repeat(16)))).isEmpty())
        val fields = WpsAssociation.fields(a, listOf(device("conflict", model = "R8000")))
        assertTrue(fields.last().value.contains("conflicting identity"))
        assertFalse(fields.any { it.value.contains("Manufacturer and model agree") })
        assertTrue(WpsAssociation.fields(a.copy(kind = RadioKind.BLE), listOf(a)).isEmpty())
    }

    @Test fun hintsReachDetailAndReportAndPrivacyMasksPeerAddressAndUuid() {
        val a = device("00:11:22:33:44:55")
        val b = device("00:11:22:33:44:66")
        val peers = listOf(a, b)
        val translate: (String) -> String = { when (it) {
            "Possible shared Wi-Fi device" -> "疑似同源 Wi-Fi 设备"
            else -> it
        } }
        assertTrue(DeviceDetailText.build(a, emptyList(), peerDevices = peers, translate = translate).contains("疑似同源 Wi-Fi 设备"))
        assertTrue(DebriefReport.build(peers, emptyList(), AppSettings(), emptyList(), now = 2000, translate = translate).contains("疑似同源 Wi-Fi 设备"))
        val private = DeviceDetailText.build(a, emptyList(), peerDevices = peers, demoMode = true)
        assertFalse(private.contains(b.mac))
        assertFalse(private.contains(id))
        assertFalse(private.contains(id.hexSpaced()))
        assertFalse(DeviceDetailPrompt.build(a, emptyList(), AppSettings(demoMode = true), peerDevices = peers).contains(id.hexSpaced()))
        val privateReport = DebriefReport.build(peers, emptyList(), AppSettings(demoMode = true), emptyList(), now = 2000)
        assertFalse(privateReport.contains(id))
    }

    @Test fun uuidOnlyGroupLeaderStillDetectsConflictsBetweenOtherMembers() {
        val a = device("00:11:22:33:44:55").copy(facts = RadioFacts(vendorIes = listOf(
            VendorIeRecord("0050F2", 4, attr("1047", id)))))
        val b = device("00:11:22:33:44:66")
        val c = device("00:11:22:33:44:77", model = "R8000")
        val devices = listOf(a, b, c)
        assertTrue(WpsAssociation.fields(a, devices).last().value.contains("conflicting identity"))
        assertTrue(DebriefReport.build(devices, emptyList(), AppSettings(), emptyList(), now = 2000).contains("conflicting identity"))
    }

    @Test fun identityEvidenceMustComeFromTheRecordCarryingTheAssociatedUuid() {
        val matching = device("00:11:22:33:44:55", model = "RT")
        val unrelated = VendorIeRecord("0050F2", 4, text("1021", "Other") + text("1023", "R7000"))
        val a = matching.copy(facts = RadioFacts(vendorIes = listOf(unrelated) + matching.facts.vendorIes))
        val b = device("00:11:22:33:44:66", model = "RT")
        val fields = WpsAssociation.fields(a, listOf(a, b))
        assertFalse(fields.last().value.contains("conflicting identity"))
        assertTrue(fields.any { it.value.contains("Manufacturer and model agree") })
    }
}
