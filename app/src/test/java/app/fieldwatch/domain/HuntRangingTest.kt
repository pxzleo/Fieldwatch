package app.fieldwatch.domain

import app.fieldwatch.data.HuntSession
import org.junit.Assert.*
import org.junit.Test

class HuntRangingTest {
    private val mac = "00:11:22:33:44:55"
    private val json = """{"targetMac":"$mac","sessionId":123,"configId":3,"localAddress":"1234","peerAddress":"5678","channel":9,"preambleIndex":10,"sessionKey":"00112233445566778899aabbccddeeff"}"""

    @Test fun onlyFreshValidActiveReadingsAreUsable() {
        val state = HuntRangeState(HuntRangeStatus.ACTIVE, HuntRangeTechnology.CS, 1.25, 10_000)
        assertEquals(1.25, state.freshDistance(13_000)!!, .001)
        assertNull(state.freshDistance(13_001))
        assertNull(state.freshDistance(9_999))
        assertNull(state.copy(status = HuntRangeStatus.LOW_QUALITY).freshDistance(10_000))
        for (value in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertNull(state.copy(distance = value).freshDistance(10_000))
    }
    @Test fun oldSessionAndWrongTargetCannotReplaceCurrentDistance() {
        val session = HuntSession()
        val key = "BLE:$mac"
        session.start(key, 100)
        val reading = HuntRangeState(HuntRangeStatus.ACTIVE, HuntRangeTechnology.UWB, 2.0, 200)
        assertTrue(session.updateRanging(key, 100, reading))
        assertEquals(reading, session.state.value.ranging)
        session.start(key, 300)
        assertFalse(session.updateRanging(key, 100, reading))
        assertFalse(session.updateRanging("BLE:11:22:33:44:55:66", 300, reading))
        assertNull(session.state.value.ranging.distance)
        session.stop()
        session.updateRanging(key, 300, reading)
        assertNull(session.state.value.ranging.distance)
    }
    @Test fun importsNegotiatedParametersWithoutInventingPeerValues() {
        val config = HuntUwbConfig.parse(json, mac)
        assertEquals(123, config.sessionId)
        assertEquals(2, config.slotDuration)
        assertArrayEquals(byteArrayOf(0x12, 0x34), config.localAddress)
        assertEquals(16, config.sessionKey.size)
    }
    @Test fun rejectsWrongTargetAndMalformedNegotiatedParameters() {
        val invalid = listOf(json.replace(mac, "AA:BB:CC:DD:EE:FF"),
            json.replace("1234", "123"), json.replace("5678", "1234"),
            json.replace("\"configId\":3", "\"configId\":2"),
            json.replace("\"channel\":9", "\"channel\":\"9\""),
            json.replace("00112233445566778899aabbccddeeff", "abcd"),
            json.dropLast(1) + ",\"slotDuration\":\"bad\"}", "[]", "bad")
        for (input in invalid) {
            try { HuntUwbConfig.parse(input, mac); fail("Accepted invalid parameters") }
            catch (expected: UwbConfigException) { assertNotNull(expected.message) }
        }
    }
}
