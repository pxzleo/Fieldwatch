package app.fieldwatch.ui.screen

import app.fieldwatch.UiText
import app.fieldwatch.ui.uiLabel

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchFilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchSlider
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.fieldwatch.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.fieldwatch.domain.AlertVoiceWhat
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.ScanIntensity
import app.fieldwatch.domain.TakDefaults
import app.fieldwatch.domain.TakFeedStatus
import app.fieldwatch.domain.TakPublish
import app.fieldwatch.domain.TakUdpPreset
import app.fieldwatch.radio.WifiRadio
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.FieldwatchFilterChip
import app.fieldwatch.ui.component.StableCaption
import app.fieldwatch.ui.component.StickyHeight
import java.net.Inet4Address
import java.net.NetworkInterface

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onRadioBookmarks: () -> Unit,
    onShowLiveTour: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = state.settings
    val saveSignatures = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::saveSignaturesToUri) }
    val importSignatures = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importSignaturesFromUri) }
    val saveSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(vm::saveSettingsToUri) }
    val importSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(vm::importSettingsFromUri) }
    var confirmRestore by remember { mutableStateOf(false) }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar(UiText.text(R.string.ui_settings)) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(app.fieldwatch.UiText.text(R.string.language)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.fieldwatch.AppLanguage.entries.forEach { language ->
                        FieldwatchFilterChip(
                            selected = settings.language == language,
                            onClick = { vm.updateSettings { it.copy(language = language) } },
                            label = {
                                Text(app.fieldwatch.UiText.text(when (language) {
                                    app.fieldwatch.AppLanguage.SYSTEM -> R.string.language_system
                                    app.fieldwatch.AppLanguage.ENGLISH -> R.string.language_english
                                    app.fieldwatch.AppLanguage.SIMPLIFIED_CHINESE -> R.string.language_chinese
                                }))
                            },
                        )
                    }
                }
            }
            SectionCard(UiText.text(R.string.ui_appearance)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_night_mode), Modifier.weight(1f))
                FieldwatchSwitch(settings.nightMode, { on -> vm.updateSettings { it.copy(nightMode = on) } })
            }
            Text(
                UiText.text(R.string.ui_off_by_default_red_on_black_field_display_so_chips_text_and_signa) +
                    UiText.text(R.string.ui_do_not_dump_green_or_blue_into_a_dark_sit_background_stays_dark) +
                    UiText.text(R.string.ui_phone_brightness_is_unchanged),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_keep_screen_on), Modifier.weight(1f))
                FieldwatchSwitch(settings.keepScreenOn, { on -> vm.updateSettings { it.copy(keepScreenOn = on) } })
            }
            Text(
                UiText.text(R.string.ui_on_by_default_stops_the_display_from_sleeping_while_fieldwatch_is),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_privacy_mode), Modifier.weight(1f))
                FieldwatchSwitch(settings.demoMode, { on -> vm.updateSettings { it.copy(demoMode = on) } })
            }
            Text(
                UiText.text(R.string.ui_hides_the_last_three_octets_of_every_mac_on_live_radar_timeline_d),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(UiText.text(R.string.ui_scanning)) {
            val label = when (settings.intensity) {
                ScanIntensity.SAVER -> UiText.text(R.string.ui_battery_saver)
                ScanIntensity.BALANCED -> UiText.text(R.string.ui_balanced)
                ScanIntensity.PERFORMANCE -> UiText.text(R.string.ui_high_performance)
            }
            Text(UiText.text(R.string.ui_scan_intensity_value, (label).toString()))
            FieldwatchSlider(
                value = settings.intensity.ordinal.toFloat(),
                onValueChange = { v ->
                    val next = ScanIntensity.entries[v.toInt().coerceIn(0, 2)]
                    vm.updateSettings { it.copy(intensity = next) }
                },
                valueRange = 0f..2f,
                steps = 1,
            )
            Text(
                UiText.text(R.string.ui_wi_fi_is_a_batch_radio_the_phone_grabs_every_ap_at_once_then_must),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StableCaption(
                state.throttleHint.ifBlank { " " },
                UiText.text(R.string.ui_wi_fi_waiting_on_os),
                UiText.text(R.string.ui_wi_fi_scanning),
                UiText.text(R.string.ui_wi_fi_next_99s),
                " ",
            )

            val lifecycleOwner = LocalLifecycleOwner.current
            var osThrottled by remember { mutableStateOf(WifiRadio.osScanThrottled(context)) }
            var backgroundAllowed by remember { mutableStateOf(isBackgroundUsageAllowed(context)) }
            var unrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
            var needDevOptions by remember { mutableStateOf(false) }
            var batteryGate by remember { mutableStateOf<BatteryAndroidGate?>(null) }
            DisposableEffect(lifecycleOwner) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        osThrottled = WifiRadio.osScanThrottled(context)
                        backgroundAllowed = isBackgroundUsageAllowed(context)
                        unrestricted = isIgnoringBatteryOptimizations(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }
            val fastActive = settings.wifiFastScan && !osThrottled
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_faster_wi_fi_ap_scans), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = settings.wifiFastScan,
                    onCheckedChange = { on ->
                        if (!on) {
                            vm.updateSettings { it.copy(wifiFastScan = false) }
                        } else if (!osThrottled) {
                            vm.updateSettings { it.copy(wifiFastScan = true) }
                        } else {
                            needDevOptions = true
                        }
                    },
                )
            }
            StableCaption(
                when {
                    Build.VERSION.SDK_INT < 30 ->
                        UiText.text(R.string.ui_needs_android_11_so_fieldwatch_can_read_whether_the_os_is_still_t)
                    fastActive ->
                        UiText.text(R.string.ui_on_fieldwatch_asks_for_a_new_ap_list_about_every_8_seconds_uses_m)
                    settings.wifiFastScan && osThrottled ->
                        UiText.text(R.string.ui_saved_on_but_not_in_effect_android_wi_fi_scan_throttling_is_still)
                    else ->
                        UiText.text(R.string.ui_stock_android_allows_about_four_ap_scans_per_two_minutes_faster_s)
                },
                UiText.text(R.string.ui_needs_android_11_so_fieldwatch_can_read_whether_the_os_is_still_t),
                UiText.text(R.string.ui_on_fieldwatch_asks_for_a_new_ap_list_about_every_8_seconds_uses_m),
                UiText.text(R.string.ui_saved_on_but_not_in_effect_android_wi_fi_scan_throttling_is_still),
                UiText.text(R.string.ui_stock_android_allows_about_four_ap_scans_per_two_minutes_faster_s),
            )
            if (needDevOptions) {
                AlertDialog(
                    onDismissRequest = { needDevOptions = false },
                    title = { Text(UiText.text(R.string.ui_developer_options_required)) },
                    text = {
                        Text(
                            if (Build.VERSION.SDK_INT < 30) {
                                UiText.text(R.string.ui_this_phone_is_older_than_android_11_so_fieldwatch_cannot_read_the)
                            } else {
                                UiText.text(R.string.ui_android_is_still_throttling_wi_fi_scans_about_four_per_two_minute) +
                                    UiText.text(R.string.ui_enable_developer_options_tap_build_number_seven_times_in_about_ph)
                            },
                        )
                    },
                    confirmButton = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            TextButton(
                                onClick = {
                                    needDevOptions = false
                                    runCatching {
                                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                                    }
                                },
                            ) { Text(UiText.text(R.string.ui_open_developer_options)) }
                        } else {
                            TextButton(onClick = { needDevOptions = false }) { Text(UiText.text(R.string.ui_ok)) }
                        }
                    },
                    dismissButton = {
                        if (Build.VERSION.SDK_INT >= 30) {
                            TextButton(onClick = { needDevOptions = false }) { Text(UiText.text(R.string.ui_not_now)) }
                        }
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_allow_background_usage), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = backgroundAllowed,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.BACKGROUND },
                )
            }
            Text(
                UiText.text(R.string.ui_mirrors_android_allow_background_usage_tap_to_open_fieldwatch_s_b) +
                    UiText.text(R.string.ui_use_that_switch_fieldwatch_updates_when_you_return_off_the_os_can) +
                    UiText.text(R.string.ui_as_soon_as_you_leave_not_keep_screen_on),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_unrestricted_battery), Modifier.weight(1f))
                FieldwatchSwitch(
                    checked = unrestricted,
                    onCheckedChange = { batteryGate = BatteryAndroidGate.UNRESTRICTED },
                )
            }
            Text(
                UiText.text(R.string.ui_mirrors_android_unrestricted_not_optimized_some_phones_samsung_am) +
                    UiText.text(R.string.ui_open_onto_that_choice_if_you_only_see_allow_background_usage_tap_) +
                    UiText.text(R.string.ui_click_through_and_select_unrestricted_fieldwatch_updates_when_you),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (batteryGate != null) {
                val background = batteryGate == BatteryAndroidGate.BACKGROUND
                AlertDialog(
                    onDismissRequest = { batteryGate = null },
                    title = {
                        Text(if (background) UiText.text(R.string.ui_allow_background_usage) else UiText.text(R.string.ui_unrestricted_battery))
                    },
                    text = {
                        Text(
                            if (background) {
                                UiText.text(R.string.ui_the_next_screen_is_fieldwatch_s_battery_page_use_the_allow_backgr) +
                                    UiText.text(R.string.ui_fieldwatch_will_match_that_setting_when_you_return)
                            } else {
                                UiText.text(R.string.ui_some_phones_samsung_among_them_do_not_open_onto_unrestricted) +
                                    UiText.text(R.string.ui_optimized_restricted_if_you_only_see_allow_background_usage) +
                                    UiText.text(R.string.ui_tap_that_row_the_words_not_the_blue_switch_to_click_through) +
                                    UiText.text(R.string.ui_then_select_unrestricted_fieldwatch_will_match_that_when_you_retu)
                            },
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val gate = batteryGate
                                batteryGate = null
                                openAppBatteryPage(
                                    context,
                                    highlightBackground = gate == BatteryAndroidGate.BACKGROUND,
                                )
                            },
                        ) { Text(UiText.text(R.string.ui_open_android_settings)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { batteryGate = null }) { Text(UiText.text(R.string.ui_not_now)) }
                    },
                )
            }
            }

            SectionCard(UiText.text(R.string.ui_watchlist)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_watchlist_alerts), Modifier.weight(1f))
                FieldwatchSwitch(settings.alertsEnabled, { on -> vm.updateSettings { it.copy(alertsEnabled = on) } })
            }
            Text(
                UiText.text(R.string.ui_on_by_default_master_switch_for_bookmarked_signatures_and_devices),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val radioWatchN = state.watchlist.count { it.deviceKey != null }
            FieldwatchActionButton(
                onClick = onRadioBookmarks,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(UiText.text(R.string.ui_named_radios_value, (radioWatchN).toString())) }
            Text(
                UiText.text(R.string.ui_custom_names_for_one_mac_alert_is_optional_filters_named_radios_o),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_beep_on_watched_signature), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertBeep,
                    { on -> vm.updateSettings { it.copy(alertBeep = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                UiText.text(R.string.ui_the_double_pip_on_media_volume_when_a_bookmarked_signature_or_dev),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_voice_on_watched_signature), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertVoice,
                    { on -> vm.updateSettings { it.copy(alertVoice = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                UiText.text(R.string.ui_on_by_default_speaks_on_the_same_media_volume_as_the_pip_independ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(UiText.text(R.string.ui_what_to_say), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertVoiceWhat.entries.forEach { item ->
                    FieldwatchFilterChip(
                        selected = settings.alertVoiceWhat == item,
                        onClick = { vm.updateSettings { it.copy(alertVoiceWhat = item) } },
                        enabled = settings.alertsEnabled && settings.alertVoice,
                        label = { Text(item.uiLabel()) },
                    )
                }
            }
            Text(
                UiText.text(R.string.ui_for_signature_watches_class_is_the_live_glyph_bucket_finder_tags_),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::testWatchBeep,
                modifier = Modifier.fillMaxWidth(),
                enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
            ) { Text(UiText.text(R.string.ui_test_alert)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_jump_to_new_watched_detection), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.snapToBeep,
                    { on -> vm.updateSettings { it.copy(snapToBeep = on) } },
                    enabled = settings.alertsEnabled && (settings.alertBeep || settings.alertVoice),
                )
            }
            Text(
                UiText.text(R.string.ui_when_a_new_watched_signature_or_device_appears_live_scrolls_to_th),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_system_notification), Modifier.weight(1f))
                FieldwatchSwitch(
                    settings.alertShade,
                    { on -> vm.updateSettings { it.copy(alertShade = on) } },
                    enabled = settings.alertsEnabled,
                )
            }
            Text(
                UiText.text(R.string.ui_optional_posts_a_silent_shade_card_when_a_watched_radio_appears_o),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(UiText.text(R.string.ui_location)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_tag_detections_with_gps), Modifier.weight(1f))
                FieldwatchSwitch(settings.tagLocation, { on -> vm.updateSettings { it.copy(tagLocation = on) } })
            }
            Text(
                UiText.text(R.string.ui_on_by_default_requests_live_gps_network_updates_and_stamps_each_h) +
                    UiText.text(R.string.ui_debrief_and_lat_lon_on_new_log_rows_last_known_only_is_ignored_if) +
                    UiText.text(R.string.ui_that_is_your_gps_at_hear_time_not_an_independent_fix_on_the_other) +
                    UiText.text(R.string.ui_use_high_accuracy_location_or_the_path_stays_0_turn_off_if_you_do) +
                    UiText.text(R.string.ui_heard_here_tak_pins_also_need_this_advertised_payload_coordinates),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_online_place_names_and_maps), Modifier.weight(1f))
                FieldwatchSwitch(settings.onlineLookup, { on -> vm.updateSettings { it.copy(onlineLookup = on) } })
            }
            Text(
                UiText.text(R.string.ui_on_by_default_when_the_phone_has_internet_debrief_ai_export_rever) +
                    UiText.text(R.string.ui_to_street_city_and_reports_path_loads_openstreetmap_tiles_under_t) +
                    UiText.text(R.string.ui_no_fieldwatch_cloud_no_api_key_offline_or_no_geocoder_debrief_use) +
                    UiText.text(R.string.ui_turn_off_to_keep_streets_and_map_tiles_out_of_reports_and_path) +
                    UiText.text(R.string.ui_debrief_sit_export_log_export_and_reset_clear_log_are_on_the_repo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard("TAK / CoT") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_tak_cot_feed), Modifier.weight(1f))
                FieldwatchSwitch(settings.takEnabled, { on -> vm.updateSettings { it.copy(takEnabled = on) } })
            }
            Text(
                UiText.text(R.string.ui_off_by_default_sends_cursor_on_target_udp_markers_to_atak_wintak_) +
                    UiText.text(R.string.ui_this_phone_value_value_is_atak_civ_on_this_handset, (TakDefaults.LOOPBACK).toString(), (TakDefaults.PORT).toString()) +
                    UiText.text(R.string.ui_lan_multicast_is_value_value, (TakDefaults.SA_HOST).toString(), (TakDefaults.SA_PORT).toString()) +
                    UiText.text(R.string.ui_custom_is_a_unicast_ipv4_or_hostname_udp_only_a_tak_server_s_tcp_) +
                    UiText.text(R.string.ui_heard_here_pins_sit_at_this_phone_s_gps_at_the_loudest_hear_close) +
                    UiText.text(R.string.ui_walking_away_does_not_drag_the_pin_a_louder_hear_moves_it_keep_al) +
                    UiText.text(R.string.ui_advertised_lat_lon_stock_remote_id_sit_on_the_aircraft_the_same_r) +
                    UiText.text(R.string.ui_keeps_one_marker_that_moves_uas_id_not_the_rotating_ble_mac) +
                    UiText.text(R.string.ui_a_decoded_pilot_location_is_a_second_pin_gone_radios_are_dropped_) +
                    UiText.text(R.string.ui_tap_a_marker_in_atak_for_remarks_name_mac_rssi_signatures) +
                    UiText.text(R.string.ui_not_direction_finding_not_a_remote_id_plugin_privacy_mode_pauses_),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (settings.takEnabled && settings.demoMode) {
                Text(
                    UiText.text(R.string.ui_privacy_mode_is_on_the_feed_is_paused_so_full_macs_and_coordinate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (settings.takEnabled) {
                TakFeedSettings(settings, vm, state.takStatus)
            }
            }

            SectionCard(UiText.text(R.string.ui_logging)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_write_detections_to_disk), Modifier.weight(1f))
                FieldwatchSwitch(settings.loggingEnabled, { on -> vm.updateSettings { it.copy(loggingEnabled = on) } })
            }
            StableCaption(
                if (settings.loggingEnabled) {
                    UiText.text(R.string.ui_logging_is_on_new_detections_are_appended_to_the_rotating_file)
                } else {
                    UiText.text(R.string.ui_logging_is_off_scanning_still_runs_nothing_new_is_written_until_y)
                },
                UiText.text(R.string.ui_logging_is_on_new_detections_are_appended_to_the_rotating_file),
                UiText.text(R.string.ui_logging_is_off_scanning_still_runs_nothing_new_is_written_until_y),
            )
            Text(
                UiText.text(R.string.ui_the_rotating_file_is_json_lines_one_hear_per_line_reports_log_for),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var rotateDrag by remember { mutableIntStateOf(settings.logRotateKb) }
            var rotateDragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.logRotateKb) {
                if (!rotateDragging) rotateDrag = settings.logRotateKb
            }
            Text(UiText.text(R.string.ui_rotate_at_value_kb, (rotateDrag).toString()))
            FieldwatchSlider(
                value = rotateDrag.toFloat(),
                onValueChange = {
                    rotateDragging = true
                    rotateDrag = it.toInt().coerceIn(128, 4096)
                },
                onValueChangeFinished = {
                    vm.updateSettings { s -> s.copy(logRotateKb = rotateDrag) }
                    rotateDragging = false
                },
                valueRange = 128f..4096f,
            )
            var staleDrag by remember { mutableIntStateOf(settings.staleSec) }
            var staleDragging by remember { mutableStateOf(false) }
            LaunchedEffect(settings.staleSec) {
                if (!staleDragging) staleDrag = settings.staleSec
            }
            Text(UiText.text(R.string.ui_stale_after_values, (staleDrag).toString()))
            FieldwatchSlider(
                value = staleDrag.toFloat(),
                onValueChange = {
                    staleDragging = true
                    staleDrag = it.toInt().coerceIn(15, 180)
                },
                onValueChangeFinished = {
                    vm.updateSettings { s -> s.copy(staleSec = staleDrag) }
                    staleDragging = false
                },
                valueRange = 15f..180f,
            )
            StickyHeight("log-stats") {
                Text(
                    UiText.text(R.string.ui_value_lines_this_session_value_kb_on_disk_value, (state.logLines).toString(), (vm.logBytes() / 1024).toString()) +
                        UiText.text(R.string.ui_share_save_and_reset_clear_log_are_on_the_reports_tab),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            }

            SectionCard(UiText.text(R.string.ui_signatures)) {
            Text(
                UiText.text(R.string.ui_export_the_catalog_stock_plus_any_you_added_or_edited_to_share_wi),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSignatureShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(UiText.text(R.string.ui_export_signatures)) }
            FieldwatchActionButton(
                onClick = { saveSignatures.launch(vm.suggestedSignaturesName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(UiText.text(R.string.ui_save_signatures_to_sd_card_storage)) }
            FieldwatchActionButton(
                onClick = {
                    importSignatures.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(UiText.text(R.string.ui_import_signatures)) }
            FieldwatchActionButton(
                onClick = vm::updateStockCatalogFromGitHub,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(UiText.text(R.string.ui_update_stock_catalog_from_github)) }

            FieldwatchActionButton(
                onClick = { confirmRestore = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(UiText.text(R.string.ui_restore_default_signatures_presets))
            }
            }

            SectionCard(UiText.text(R.string.ui_settings_backup)) {
            Text(
                UiText.text(R.string.ui_settings_switches_the_current_filter_filter_presets_named_radios_) +
                    UiText.text(R.string.ui_not_the_catalog_that_is_export_signatures_not_logs_or_gps) +
                    UiText.text(R.string.ui_import_replaces_those_on_this_phone_the_catalog_stays) +
                    UiText.text(R.string.ui_use_this_after_a_factory_reset_or_on_a_new_phone),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FieldwatchActionButton(
                onClick = vm::startSettingsShare,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(UiText.text(R.string.ui_export_settings)) }
            FieldwatchActionButton(
                onClick = { saveSettings.launch(vm.suggestedSettingsName()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(UiText.text(R.string.ui_save_settings_to_sd_card_storage)) }
            FieldwatchActionButton(
                onClick = {
                    importSettings.launch(arrayOf("application/json", "text/plain", "*/*"))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(UiText.text(R.string.ui_import_settings)) }
            }

            FieldwatchActionButton(
                onClick = onShowLiveTour,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(UiText.text(R.string.ui_show_live_tour)) }
            Text(
                UiText.text(R.string.ui_chrome_overlay_on_live_tune_is_display_radar_list_by_class_pause_),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                UiText.text(R.string.ui_fieldwatch_value_catalog_value, (app.fieldwatch.BuildConfig.VERSION_NAME).toString(), (state.catalogVersion).toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                UiText.text(R.string.ui_passive_wi_fi_ble_only) +
                    UiText.text(R.string.ui_stock_android_cannot_promiscuously_capture_wi_fi_stations_access_),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val footerLifecycle = LocalLifecycleOwner.current
            var ipv4 by remember { mutableStateOf(localIpv4Addresses()) }
            DisposableEffect(footerLifecycle) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) ipv4 = localIpv4Addresses()
                }
                footerLifecycle.lifecycle.addObserver(obs)
                onDispose { footerLifecycle.lifecycle.removeObserver(obs) }
            }
            Text(
                if (ipv4.isEmpty()) {
                    UiText.text(R.string.ui_this_phone_s_ipv4_none)
                } else {
                    UiText.text(R.string.ui_this_phone_s_ipv4_value, (ipv4.joinToString("  ·  ")).toString())
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
            CreditFooter()
        }
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text(UiText.text(R.string.ui_restore_defaults)) },
            text = {
                Text(
                    UiText.text(R.string.ui_rewrites_the_catalog_stock_rows_class_colors_decode_fields_stock_) +
                        UiText.text(R.string.ui_stock_filter_chips_and_default_settings_switches_custom_signature) +
                        UiText.text(R.string.ui_saved_are_wiped_export_signatures_and_export_settings_first_if_yo) +
                        UiText.text(R.string.ui_this_cannot_be_undone),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        vm.restoreDefaults()
                    },
                ) { Text(UiText.text(R.string.ui_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text(UiText.text(R.string.ui_cancel)) }
            },
        )
    }
}

@Composable
private fun CreditFooter() {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Copyright (c) 2026 Off Grid Pete LLC. All rights reserved.",
            style = MaterialTheme.typography.labelSmall,
            color = muted,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SocialChip(
                icon = R.drawable.ic_instagram,
                label = "@OffGridPete",
                tint = muted,
                onClick = { openUrl(context, "https://instagram.com/OffGridPete") },
            )
            SocialChip(
                icon = R.drawable.ic_x,
                label = "@OGridPete",
                tint = muted,
                onClick = { openUrl(context, "https://x.com/OGridPete") },
            )
        }
    }
}

@Composable
private fun SocialChip(
    icon: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TakFeedSettings(settings: AppSettings, vm: FieldwatchViewModel, status: TakFeedStatus) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var hostText by remember { mutableStateOf(settings.takHost) }
    var portText by remember { mutableStateOf(settings.takPort.toString()) }
    LaunchedEffect(settings.takHost) { hostText = settings.takHost }
    LaunchedEffect(settings.takPort) { portText = settings.takPort.toString() }
    val preset = TakPublish.udpPreset(settings.takHost, settings.takPort)
    Text(UiText.text(R.string.ui_destination), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.THIS_PHONE,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.THIS_PHONE)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text(UiText.text(R.string.ui_this_phone)) },
        )
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.LAN_MULTICAST,
            onClick = {
                val (host, port) = TakPublish.applyPreset(TakUdpPreset.LAN_MULTICAST)
                vm.updateSettings { it.copy(takHost = host, takPort = port) }
            },
            enabled = !settings.demoMode,
            label = { Text(UiText.text(R.string.ui_lan_multicast)) },
        )
        FieldwatchFilterChip(
            selected = preset == TakUdpPreset.CUSTOM,
            onClick = {
                if (preset != TakUdpPreset.CUSTOM) {
                    val (host, port) = TakPublish.applyPreset(TakUdpPreset.CUSTOM)
                    vm.updateSettings { it.copy(takHost = host, takPort = port) }
                }
            },
            enabled = !settings.demoMode,
            label = { Text(UiText.text(R.string.ui_custom)) },
        )
    }
    Text(
        UiText.text(R.string.ui_this_phone_value_value_atak_civ_on_this_handset, (TakDefaults.LOOPBACK).toString(), (TakDefaults.PORT).toString()) +
            UiText.text(R.string.ui_lan_multicast_value_value_other_ataks_on_this_wi_fi, (TakDefaults.SA_HOST).toString(), (TakDefaults.SA_PORT).toString()) +
            UiText.text(R.string.ui_custom_type_a_unicast_ipv4_or_hostname_udp_only_a_tak_server_s_tc) +
            UiText.text(R.string.ui_if_this_phone_does_not_plot_use_custom_with_this_phone_s_wi_fi_ip, (TakDefaults.PORT).toString()),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
    FieldwatchOutlinedField(
        value = hostText,
        onValueChange = { value ->
            hostText = value
            val trimmed = value.trim()
            if (trimmed.isNotEmpty()) {
                vm.updateSettings { it.copy(takHost = trimmed) }
            }
        },
        label = UiText.text(R.string.ui_host),
        placeholder = TakDefaults.HOST,
        enabled = !settings.demoMode,
    )
    FieldwatchOutlinedField(
        value = portText,
        onValueChange = { value ->
            val filtered = value.filter { it.isDigit() }.take(5)
            portText = filtered
            filtered.toIntOrNull()?.let { n ->
                if (n in 1..65_535) {
                    vm.updateSettings { it.copy(takPort = n) }
                }
            }
        },
        label = UiText.text(R.string.ui_port),
        placeholder = TakDefaults.PORT.toString(),
        supportingText = UiText.text(R.string.ui_udp_atak_civ_value_sa_multicast_value_not_tcp_8087, (TakDefaults.PORT).toString(), (TakDefaults.SA_PORT).toString()),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        enabled = !settings.demoMode,
    )
    Text(takStatusLine(status), style = MaterialTheme.typography.bodySmall, color = muted)
    Text(UiText.text(R.string.ui_what_to_send), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldwatchFilterChip(
            selected = settings.takAttention,
            onClick = { vm.updateSettings { it.copy(takAttention = !it.takAttention) } },
            enabled = !settings.demoMode,
            label = { Text(UiText.text(R.string.ui_extra_attention)) },
        )
        FieldwatchFilterChip(
            selected = settings.takPayloadFix,
            onClick = { vm.updateSettings { it.copy(takPayloadFix = !it.takPayloadFix) } },
            enabled = !settings.demoMode,
            label = { Text(UiText.text(R.string.ui_payload_location)) },
        )
        FieldwatchFilterChip(
            selected = settings.takWatchlist,
            onClick = { vm.updateSettings { it.copy(takWatchlist = !it.takWatchlist) } },
            enabled = !settings.demoMode,
            label = { Text(UiText.text(R.string.ui_watchlist)) },
        )
        FieldwatchFilterChip(
            selected = settings.takAllSignatures,
            onClick = { vm.updateSettings { it.copy(takAllSignatures = !it.takAllSignatures) } },
            enabled = !settings.demoMode,
            label = { Text(UiText.text(R.string.ui_all_signatures)) },
        )
    }
    Text(
        UiText.text(R.string.ui_independent_chips_extra_attention_on_body_cam_glasses_recording_w) +
            UiText.text(R.string.ui_payload_location_on_advertised_lat_lon_from_a_decode_map_required) +
            UiText.text(R.string.ui_watchlist_off_bookmarked_signatures_and_named_radios_with_alert_o) +
            UiText.text(R.string.ui_all_signatures_off_every_labeled_radio_noisy_in_a_plaza_unmatched) +
            UiText.text(R.string.ui_a_pin_still_needs_coordinates_advertised_payload_or_gps_tagging_w) +
            UiText.text(R.string.ui_heard_here_holds_the_loudest_hear_not_the_last_and_callsigns_end_) +
            UiText.text(R.string.ui_remote_id_keeps_one_aircraft_marker_uas_id_plus_a_pilot_pin_when_),
        style = MaterialTheme.typography.bodySmall,
        color = muted,
    )
}

private fun takStatusLine(status: TakFeedStatus): String {
    if (status.paused) return UiText.text(R.string.ui_feed_status_paused_privacy_mode)
    if (status.error != null) {
        val whenAt = takStatusWhen(status.at)
        val error = if (status.error.startsWith("Host ") && status.error.endsWith(" did not resolve")) {
            UiText.text(R.string.detail_tak_host_unresolved,
                status.error.removePrefix("Host ").removeSuffix(" did not resolve"))
        } else status.error
        return UiText.text(R.string.ui_feed_status_error_value, error) + if (whenAt.isNotEmpty()) "  ·  $whenAt" else ""
    }
    if (status.at <= 0L) {
        return UiText.text(R.string.ui_feed_status_no_send_yet_this_session)
    }
    val bits = ArrayList<String>(5)
    bits += UiText.text(R.string.ui_on_the_feed_value, (status.onFeed).toString())
    bits += UiText.text(R.string.ui_sent_value, (status.sent).toString())
    if (status.gone > 0) {
        bits += if (status.gone == 1) UiText.text(R.string.ui_1_gone) else UiText.text(R.string.ui_value_gone, (status.gone).toString())
    }
    if (status.dest.isNotBlank()) bits += status.dest
    val whenAt = takStatusWhen(status.at)
    if (whenAt.isNotEmpty()) bits += whenAt
    val head = UiText.text(R.string.ui_feed_status_value, (bits.joinToString("  ·  ")).toString())
    return if (status.detail.isNotBlank() && status.sent == 0 && status.gone == 0) {
        "$head  ·  ${UiText.explanation(status.detail)}"
    } else {
        head
    }
}

private fun takStatusWhen(at: Long): String {
    if (at <= 0L) return ""
    return java.time.Instant.ofEpochMilli(at)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
}

private fun localIpv4Addresses(): List<String> {
    val found = LinkedHashSet<String>()
    val nifs = runCatching {
        java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
    }.getOrDefault(emptyList())
    for (nif in nifs) {
        if (!nif.isUp || nif.isLoopback) continue
        for (addr in java.util.Collections.list(nif.inetAddresses)) {
            if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                addr.hostAddress?.let { found += it }
            }
        }
    }
    return found.toList()
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true

private fun isBackgroundUsageAllowed(context: Context): Boolean =
    context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted != true

private enum class BatteryAndroidGate { BACKGROUND, UNRESTRICTED }

/**
 * Fieldwatch’s per-app Battery page. Samsung keeps Allow background usage and
 * Unrestricted on this same screen. [highlightBackground] asks Settings to
 * focus the background-usage switch when the OEM supports it.
 */
private fun openAppBatteryPage(context: Context, highlightBackground: Boolean) {
    val pkgUri = Uri.fromParts("package", context.packageName, null)
    val attempts = listOf(
        Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL").apply {
            data = pkgUri
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("request_ignore_background_restriction", highlightBackground)
            if (!highlightBackground) {
                putExtra(":settings:fragment_args_key", "unrestricted_pref")
            }
        },
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = pkgUri
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        },
    )
    for (intent in attempts) {
        if (intent.resolveActivity(context.packageManager) == null) continue
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
