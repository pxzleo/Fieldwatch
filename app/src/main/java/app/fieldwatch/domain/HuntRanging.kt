package app.fieldwatch.domain

import kotlinx.serialization.json.*

enum class HuntRangeStatus {
    CHECKING, OLD_ANDROID, PERMISSION, PHONE_UNAVAILABLE, PEER_UNVERIFIED, CONNECTING,
    PAIRING, NO_SERVICE, STARTING, ACTIVE, LOW_QUALITY, NO_DATA, FAILED, STOPPED, CONFIG_INVALID,
}
enum class HuntRangeTechnology { CS, UWB }
data class HuntRangeState(val status: HuntRangeStatus = HuntRangeStatus.CHECKING,
    val technology: HuntRangeTechnology? = null, val distance: Double? = null, val at: Long = 0,
    val csAvailability: Int? = null, val uwbAvailability: Int? = null, val reason: Int? = null,
    val rasAvailable: Boolean? = null) {
    fun freshDistance(now: Long): Double? = distance?.takeIf {
        status == HuntRangeStatus.ACTIVE && now - at in 0..3_000L && it.isFinite() && it >= 0 }
}

enum class HuntRangeBadge { CS_VERIFIED, UWB_VERIFIED, CS_SERVICE }

fun Sighting.rangingBadge(): HuntRangeBadge? {
    if (kind != RadioKind.BLE) return null
    return when (rangingVerified) {
        HuntRangeTechnology.CS -> HuntRangeBadge.CS_VERIFIED
        HuntRangeTechnology.UWB -> HuntRangeBadge.UWB_VERIFIED
        null -> if (rangingServiceSeen || (serviceUuids + facts.serviceData.map { it.uuid })
            .any { "185B" in uuidAliases(it) }) HuntRangeBadge.CS_SERVICE else null
    }
}

class UwbConfigException(message: String) : IllegalArgumentException(message)
/** Peer-negotiated, ephemeral parameters. Never logged or stored in settings. */
class HuntUwbConfig(val targetMac: String, val sessionId: Int, val configId: Int,
    val localAddress: ByteArray, val peerAddress: ByteArray, val channel: Int,
    val preambleIndex: Int, val sessionKey: ByteArray, val slotDuration: Int) {
    companion object {
        fun parse(text: String, targetMac: String): HuntUwbConfig {
            val obj = try { Json.parseToJsonElement(text).jsonObject }
                catch (e: kotlinx.serialization.SerializationException) { throw UwbConfigException("Invalid JSON") }
                catch (e: IllegalArgumentException) { throw UwbConfigException("JSON object required") }
            fun string(key: String) = (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw UwbConfigException("Missing string: $key")
            fun int(key: String) = (obj[key] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
                ?: throw UwbConfigException("Missing integer: $key")
            fun bytes(key: String): ByteArray {
                val hex = string(key)
                if (hex.length % 2 != 0 || !hex.matches(Regex("[0-9a-fA-F]+")))
                    throw UwbConfigException("Invalid hex: $key")
                return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            }
            val mac = MacUtil.normalize(string("targetMac"))
            if (mac != MacUtil.normalize(targetMac)) throw UwbConfigException("targetMac mismatch")
            val config = int("configId")
            if (config !in listOf(1, 3, 6)) throw UwbConfigException("Unicast configId required")
            val local = bytes("localAddress"); val peer = bytes("peerAddress")
            if (local.size !in listOf(2, 8) || peer.size != local.size || local.contentEquals(peer))
                throw UwbConfigException("Invalid localAddress or peerAddress")
            val key = bytes("sessionKey")
            if ((config == 1 && key.size != 8) || (config != 1 && key.size !in listOf(16, 32)))
                throw UwbConfigException("Invalid sessionKey length")
            val channel = int("channel"); val preamble = int("preambleIndex")
            if (channel !in listOf(5, 9) || preamble !in 9..32) throw UwbConfigException("Invalid channel or preambleIndex")
            val slot = if ("slotDuration" in obj) int("slotDuration") else 2
            if (slot !in 1..2) throw UwbConfigException("Invalid slotDuration")
            return HuntUwbConfig(mac, int("sessionId"), config, local, peer, channel, preamble, key, slot)
        }
    }
}
