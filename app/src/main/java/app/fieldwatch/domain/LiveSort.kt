package app.fieldwatch.domain

/** Facts from the current scan; null connectability remains unknown. */
enum class SignalType {
    WIFI_24, WIFI_5, WIFI_6, WIFI_OTHER, BLE_CONNECTABLE, BLE_BROADCAST, BLE_UNKNOWN;
}

enum class DevicePurpose {
    PHONE, COMPUTER, HEADPHONES, SPEAKER, WEARABLE, SENSOR, FINDER, BEACON,
    ROUTER, VEHICLE, INPUT, HOME, CAMERA, DRONE, HEALTH, OTHER, UNKNOWN;
}

fun AppSettings.withLiveSort(sort: ListSort, strength: StrengthSort? = null): AppSettings = copy(
    listSort = sort,
    strengthSort = strength ?: strengthSort,
    viewMode = if (viewMode == ViewMode.LIST || viewMode == ViewMode.HYBRID) viewMode else ViewMode.LIST,
)

/** Restore only the initial ordering and layout; preserve scan and display preferences. */
fun AppSettings.withInitialLiveSort(): AppSettings {
    val initial = AppSettings()
    return copy(viewMode = initial.viewMode, listSort = initial.listSort,
        strengthSort = initial.strengthSort, averageWindowSec = initial.averageWindowSec)
}

fun Sighting.signalType(): SignalType = when (kind) {
    RadioKind.WIFI -> when (frequencyMhz) {
        in 2401..2499 -> SignalType.WIFI_24
        in 4901..5899 -> SignalType.WIFI_5
        in 5926..7124 -> SignalType.WIFI_6
        else -> SignalType.WIFI_OTHER
    }
    RadioKind.BLE -> when (facts.connectable) {
        true -> SignalType.BLE_CONNECTABLE
        false -> SignalType.BLE_BROADCAST
        null -> SignalType.BLE_UNKNOWN
    }
}

/** Reuse the same evidence ranking as details. No name/vendor-only second classifier. */
fun Sighting.devicePurpose(signatureNames: List<String> = emptyList()): DevicePurpose {
    return when (DeviceExplain.guess(this, signatureNames).bucket) {
        "phone" -> DevicePurpose.PHONE
        "hotspot" -> if (kind == RadioKind.WIFI) DevicePurpose.ROUTER else DevicePurpose.PHONE
        "computer" -> DevicePurpose.COMPUTER
        "audio-personal" -> DevicePurpose.HEADPHONES
        "audio-speaker", "speaker" -> DevicePurpose.SPEAKER
        "watch", "wearable", "glasses" -> DevicePurpose.WEARABLE
        "sensor" -> DevicePurpose.SENSOR
        "tag" -> DevicePurpose.FINDER
        "beacon" -> DevicePurpose.BEACON
        "router", "ap" -> DevicePurpose.ROUTER
        "vehicle" -> DevicePurpose.VEHICLE
        "mouse", "keyboard", "gamepad", "hid", "remote" -> DevicePurpose.INPUT
        "home", "lock", "clock", "display" -> DevicePurpose.HOME
        "camera" -> DevicePurpose.CAMERA
        "drone" -> DevicePurpose.DRONE
        "health" -> DevicePurpose.HEALTH
        "other", "named" -> if (kind == RadioKind.WIFI) DevicePurpose.ROUTER else DevicePurpose.OTHER
        else -> if (kind == RadioKind.WIFI) DevicePurpose.ROUTER else DevicePurpose.UNKNOWN
    }
}

object LiveSort {
    /** Missing strength sorts last in either direction, never as +127 dBm. */
    fun strength(settings: AppSettings, now: Long, weakestFirst: Boolean = false): Comparator<Sighting> {
        val windowMs = settings.averageWindowSec.coerceIn(10, 180) * 1000L
        // A comparator belongs to one snapshot/sort. Compute each history average once.
        val averages = java.util.IdentityHashMap<Sighting, Double?>()
        fun value(device: Sighting): Double? = when (settings.strengthSort) {
            StrengthSort.INSTANT -> device.rssi.takeIf(Rssi::measured)?.toDouble()
            StrengthSort.AVERAGE -> {
                if (!averages.containsKey(device)) {
                    averages[device] = if (Rssi.measured(device.rssi) || device.rssiHistory.any { it.at >= now - windowMs && Rssi.measured(it.rssi) })
                        device.averageRssi(windowMs, now) else null
                }
                averages[device]
            }
        }
        return Comparator<Sighting> { a, b ->
            val left = value(a)
            val right = value(b)
            when {
                left == null && right == null -> 0
                left == null -> 1
                right == null -> -1
                weakestFirst -> left.compareTo(right)
                else -> right.compareTo(left)
            }
        }.thenBy { it.key }
    }

    fun types(devices: List<Sighting>, settings: AppSettings, now: Long, names: Map<String, String>): Comparator<Sighting> {
        val purpose = if (settings.listSort == ListSort.DEVICE_TYPE)
            devices.associate { it.key to it.devicePurpose(it.fleetIds.map { id -> names[id] ?: id }) } else emptyMap()
        val grouping = when (settings.listSort) {
            ListSort.WIFI_FIRST -> compareBy<Sighting> { if (it.kind == RadioKind.WIFI) 0 else 1 }
            ListSort.BLE_FIRST -> compareBy<Sighting> { if (it.kind == RadioKind.BLE) 0 else 1 }
            ListSort.SIGNAL_TYPE -> compareBy<Sighting> { it.signalType().ordinal }
            ListSort.DEVICE_TYPE -> compareBy<Sighting> { purpose.getValue(it.key).ordinal }
            else -> error("Not a type sort: ${settings.listSort}")
        }
        return grouping.then(strength(settings, now))
    }
}
