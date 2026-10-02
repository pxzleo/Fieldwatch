package app.fieldwatch.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioFactsTest {
    @Test fun latestShortServiceFrameAndStandardUuidAliasReplaceOldWhileEmptyKeepsIt() {
        val old = RadioFacts(serviceData = listOf(ServiceDataRecord("FE95", "40505B05300410020000")))
        val fresh = ServiceDataRecord("0000FE95-0000-1000-8000-00805F9B34FB", "00505B0531")
        val merged = old.merge(RadioFacts(serviceData = listOf(fresh)))
        assertEquals(listOf(fresh), merged.serviceData)
        assertEquals(listOf(fresh), merged.merge(RadioFacts(serviceData = listOf(ServiceDataRecord("FE95", "")))).serviceData)
        val newUid = ServiceDataRecord("0000FEAA-0000-1000-8000-00805F9B34FB", "00CC")
        val eddystone = RadioFacts(serviceData = listOf(ServiceDataRecord("FEAA", "00AABB"), ServiceDataRecord("FEAA", "10AABB")))
            .merge(RadioFacts(serviceData = listOf(newUid)))
        assertEquals(2, eddystone.serviceData.size)
        assertEquals(newUid, eddystone.serviceData.first())
    }

    @Test fun mibeaconManufacturerFrameControlChangesReplaceOldState() {
        val old = RadioFacts(mfgRecords = listOf(MfgRecord(0x038F, "58585B0530")))
        val current = MfgRecord(0x038F, "00505B0531")
        assertEquals(listOf(current), old.merge(RadioFacts(mfgRecords = listOf(current))).mfgRecords)
    }
    @Test
    fun vendorIeMergeReplacesOldShortSampleAndKeepsCurrentDifferentPayloads() {
        val unrelated = VendorIeRecord("00:17:F2", 1, "AA")
        val old = RadioFacts(vendorIes = listOf(VendorIeRecord("00:50:F2", 4, "11".repeat(12)), unrelated))
        val first = VendorIeRecord("00:50:F2", 4, "11".repeat(12) + "AABB")
        val second = VendorIeRecord("00:50:F2", 4, "11".repeat(12) + "CCDD")
        val current = RadioFacts(vendorIes = listOf(first, second))
        val merged = old.merge(current)
        assertEquals(listOf(unrelated, first, second), merged.vendorIes)
        assertEquals(merged.vendorIes, merged.merge(current).vendorIes)
        val next = VendorIeRecord("00:50:F2", 4, "22")
        assertEquals(listOf(unrelated, next), merged.merge(RadioFacts(vendorIes = listOf(next))).vendorIes)
    }

    @Test
    fun manufacturerMergeKeepsAllCurrentRecordsWithoutEightRecordCapOrHistoryGrowth() {
        val unrelated = MfgRecord(99, "AA")
        val old = RadioFacts(mfgRecords = listOf(MfgRecord(76, "11"), unrelated))
        val currentRecords = (0..10).map { MfgRecord(76, "11%02X".format(it)) }
        val current = RadioFacts(mfgRecords = currentRecords)
        val merged = old.merge(current)
        assertEquals(listOf(unrelated) + currentRecords, merged.mfgRecords)
        assertEquals(merged.mfgRecords, merged.merge(current).mfgRecords)
        val next = MfgRecord(76, "11FFFF")
        assertEquals(listOf(unrelated, next), merged.merge(RadioFacts(mfgRecords = listOf(next))).mfgRecords)
    }

    @Test
    fun connectableYesSurvivesScanResponse() {
        val adv = RadioFacts(connectable = true)
        val scanRsp = RadioFacts(connectable = false)
        assertEquals(true, adv.merge(scanRsp).connectable)
        assertEquals(true, scanRsp.merge(adv).connectable)
    }

    @Test
    fun connectableStaysNoUntilAConnectableAd() {
        val first = RadioFacts(connectable = false)
        val again = RadioFacts(connectable = false)
        assertEquals(false, first.merge(again).connectable)
    }

    @Test
    fun connectableNullDoesNotClear() {
        val known = RadioFacts(connectable = false)
        assertEquals(false, known.merge(RadioFacts()).connectable)
        assertNull(RadioFacts().merge(RadioFacts()).connectable)
    }

    @Test
    fun eddystoneFramesAccumulateInsteadOfReplacing() {
        val uid = RadioFacts(
            serviceData = listOf(ServiceDataRecord("FEAA", "00AABBCCDDEEFF00112233445566778899")),
        )
        val url = RadioFacts(
            serviceData = listOf(ServiceDataRecord("FEAA", "1001676F6F676C6507")),
        )
        val tlm = RadioFacts(
            serviceData = listOf(ServiceDataRecord("0000FEAA-0000-1000-8000-00805F9B34FB", "2000ABCD")),
        )
        val merged = uid.merge(url).merge(tlm)
        assertEquals(3, merged.serviceData.size)
        assertEquals(setOf("00", "10", "20"), merged.serviceData.map { it.dataHex.take(2) }.toSet())
    }

    @Test
    fun otherServiceDataStillReplacesByUuid() {
        val first = RadioFacts(serviceData = listOf(ServiceDataRecord("FE2C", "AA")))
        val second = RadioFacts(serviceData = listOf(ServiceDataRecord("FE2C", "BBCC")))
        val merged = first.merge(second)
        assertEquals(1, merged.serviceData.size)
        assertEquals("BBCC", merged.serviceData.single().dataHex)
    }
}
