package app.fieldwatch.ui

import app.fieldwatch.R
import app.fieldwatch.UiText
import app.fieldwatch.domain.*

fun ListSort.uiLabel(strength: StrengthSort, windowSec: Int): String = UiText.text(when (this) {
    ListSort.STRENGTH -> R.string.sort_strongest_short
    ListSort.NEWEST -> R.string.ui_newest_heard
    ListSort.NEWEST_ALERT -> R.string.ui_newest_alert
    ListSort.FIRST_SEEN -> R.string.ui_newest_arrival
    ListSort.ARRIVAL -> R.string.ui_new_at_bottom
    ListSort.NAME -> R.string.ui_name_a_z
    ListSort.SIGNATURES -> R.string.ui_signatures_first
    ListSort.WEAKEST -> if (strength == StrengthSort.AVERAGE) R.string.sort_weakest_average else R.string.sort_weakest
    ListSort.WIFI_FIRST -> R.string.sort_wifi_first
    ListSort.BLE_FIRST -> R.string.sort_ble_first
    ListSort.SIGNAL_TYPE -> R.string.sort_signal_type
    ListSort.DEVICE_TYPE -> R.string.sort_device_type
}, *if (this == ListSort.WEAKEST && strength == StrengthSort.AVERAGE) arrayOf(windowSec.coerceIn(5, 180).toString()) else emptyArray())

fun SignalType.uiLabel(): String = UiText.text(when (this) {
    SignalType.WIFI_24 -> R.string.signal_wifi_24
    SignalType.WIFI_5 -> R.string.signal_wifi_5
    SignalType.WIFI_6 -> R.string.signal_wifi_6
    SignalType.WIFI_OTHER -> R.string.signal_wifi_other
    SignalType.BLE_CONNECTABLE -> R.string.signal_ble_connectable
    SignalType.BLE_BROADCAST -> R.string.signal_ble_broadcast
    SignalType.BLE_UNKNOWN -> R.string.signal_ble_unknown
})

fun DevicePurpose.uiLabel(): String = UiText.text(when (this) {
    DevicePurpose.PHONE -> R.string.purpose_phone
    DevicePurpose.COMPUTER -> R.string.purpose_computer
    DevicePurpose.HEADPHONES -> R.string.purpose_headphones
    DevicePurpose.SPEAKER -> R.string.purpose_speaker
    DevicePurpose.WEARABLE -> R.string.purpose_wearable
    DevicePurpose.SENSOR -> R.string.purpose_sensor
    DevicePurpose.FINDER -> R.string.purpose_finder
    DevicePurpose.BEACON -> R.string.purpose_beacon
    DevicePurpose.ROUTER -> R.string.purpose_router
    DevicePurpose.VEHICLE -> R.string.purpose_vehicle
    DevicePurpose.INPUT -> R.string.purpose_input
    DevicePurpose.HOME -> R.string.purpose_home
    DevicePurpose.CAMERA -> R.string.purpose_camera
    DevicePurpose.DRONE -> R.string.purpose_drone
    DevicePurpose.HEALTH -> R.string.purpose_health
    DevicePurpose.OTHER -> R.string.purpose_other
    DevicePurpose.UNKNOWN -> R.string.purpose_unknown
})

fun ViewMode.uiLabel(): String = UiText.text(when (this) {
    ViewMode.RADAR -> R.string.label_viewmode_radar
    ViewMode.LIST -> R.string.label_viewmode_list
    ViewMode.TIMELINE -> R.string.label_viewmode_timeline
    ViewMode.HYBRID -> R.string.label_viewmode_hybrid
    ViewMode.BY_CLASS -> R.string.label_viewmode_by_class
})

fun SignatureClass.uiLabel(): String = UiText.text(when (this) {
    SignatureClass.FINDER -> R.string.label_signatureclass_finder
    SignatureClass.BEACON -> R.string.label_signatureclass_beacon
    SignatureClass.SIGNAGE -> R.string.label_signatureclass_signage
    SignatureClass.WEARABLE -> R.string.label_signatureclass_wearable
    SignatureClass.SURVEILLANCE -> R.string.label_signatureclass_surveillance
    SignatureClass.DRONE -> R.string.label_signatureclass_drone
    SignatureClass.HACKING -> R.string.label_signatureclass_hacking
    SignatureClass.BODYWORN -> R.string.label_signatureclass_bodyworn
    SignatureClass.LAW_ENFORCEMENT -> R.string.label_signatureclass_law_enforcement
    SignatureClass.VEHICLE -> R.string.label_signatureclass_vehicle
    SignatureClass.GLASSES -> R.string.label_signatureclass_glasses
    SignatureClass.AUDIO -> R.string.label_signatureclass_audio
    SignatureClass.CAMERA -> R.string.label_signatureclass_camera
    SignatureClass.THERMOSTAT -> R.string.label_signatureclass_thermostat
    SignatureClass.LOCK -> R.string.label_signatureclass_lock
    SignatureClass.HEALTH -> R.string.label_signatureclass_health
    SignatureClass.HOME -> R.string.label_signatureclass_home
    SignatureClass.ISP -> R.string.label_signatureclass_isp
    SignatureClass.MESH -> R.string.label_signatureclass_mesh
    SignatureClass.PHONE -> R.string.label_signatureclass_phone
    SignatureClass.OTHER -> R.string.label_signatureclass_other
})

fun AlertVoiceWhat.uiLabel(): String = UiText.text(when (this) {
    AlertVoiceWhat.CLASS -> R.string.label_alertvoicewhat_class
    AlertVoiceWhat.SIGNATURE -> R.string.label_alertvoicewhat_signature
    AlertVoiceWhat.BOTH -> R.string.label_alertvoicewhat_both
})

fun SignatureListSort.uiLabel(): String = UiText.text(when (this) {
    SignatureListSort.NAME -> R.string.label_signaturelistsort_name
    SignatureListSort.CLASS -> R.string.label_signaturelistsort_class
})

fun HuntCue.uiLabel(): String = UiText.text(when (this) {
    HuntCue.VERY_CLOSE -> R.string.label_huntcue_very_close
    HuntCue.CLOSER -> R.string.label_huntcue_closer
    HuntCue.FURTHER -> R.string.label_huntcue_further
    HuntCue.SAME -> R.string.label_huntcue_same
    HuntCue.WAITING -> R.string.label_huntcue_waiting
    HuntCue.QUIET -> R.string.hunt_no_signal
    HuntCue.GONE -> R.string.label_huntcue_gone
    HuntCue.UNSTABLE -> R.string.hunt_unstable
})

fun ClassSlice.uiLabel(): String = kind?.uiLabel() ?: UiText.text(R.string.ui_unmatched)

fun HuntCue.uiHint(): String = UiText.text(when (this) {
    HuntCue.VERY_CLOSE -> R.string.hint_hunt_very_close
    HuntCue.CLOSER -> R.string.hint_hunt_closer
    HuntCue.FURTHER -> R.string.hint_hunt_further
    HuntCue.SAME -> R.string.hint_hunt_same
    HuntCue.WAITING -> R.string.hint_hunt_waiting
    HuntCue.QUIET -> R.string.hunt_no_recent_packet
    HuntCue.GONE -> R.string.hint_hunt_gone
    HuntCue.UNSTABLE -> R.string.hunt_hold_phone
})

fun LogExportKind.uiLabel(): String = when (this) {
    LogExportKind.LOG_CSV -> UiText.text(R.string.log_csv_label)
    LogExportKind.LOG_JSONL -> UiText.text(R.string.log_json_label)
    else -> label
}

fun LogExportRadios.uiLabel(): String = UiText.text(when (this) {
    LogExportRadios.BOTH -> R.string.log_both_radios_label
    LogExportRadios.WIFI -> R.string.ui_wi_fi_only
    LogExportRadios.BLE -> R.string.ui_ble_only
})
