package app.fieldwatch.ui.screen

import app.fieldwatch.UiText
import app.fieldwatch.ui.uiLabel
import app.fieldwatch.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Scaffold
import app.fieldwatch.ui.component.FieldwatchSlider
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.FilterLogic
import app.fieldwatch.domain.FilterPreset
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.ui.ClassGlyphs
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.SectionCard
import app.fieldwatch.ui.component.FieldwatchFilterChip
import app.fieldwatch.ui.component.spectreSectionFill
import app.fieldwatch.ui.component.spectreTileEdge
import app.fieldwatch.ui.component.spectreTileFill
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.PhosphorActive
import app.fieldwatch.ui.theme.nightIf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FiltersScreen(state: FieldwatchUi, vm: FieldwatchViewModel) {
    var presetName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<FilterPreset?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val filter = state.filter
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar(UiText.text(R.string.ui_filters)) },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(UiText.text(R.string.ui_presets)) {
            Text(
                UiText.text(R.string.ui_tap_to_replace_the_whole_filter_long_press_a_chip_to_delete_it) +
                    UiText.text(R.string.ui_a_short_stock_set_ships_save_current_as_adds_your_own_cameras_pla) +
                    UiText.text(R.string.ui_stock_chips_you_delete_come_back_with_settings_restore_default_si),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.presets.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            row.forEach { preset ->
                                PresetChip(
                                    name = preset.name,
                                    selected = preset.filter == filter,
                                    onApply = { vm.applyPreset(preset) },
                                    onLongPress = { pendingDelete = preset },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FieldwatchOutlinedField(
                    presetName,
                    { presetName = it },
                    UiText.text(R.string.ui_save_current_as),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    if (presetName.isNotBlank()) {
                        vm.savePreset(presetName.trim())
                        presetName = ""
                    }
                }) { Text(UiText.text(R.string.ui_save)) }
            }
            }

            SectionCard(UiText.text(R.string.ui_radios)) {
            Text(
                if (filter.movingWithYou) {
                    UiText.text(R.string.ui_moving_with_you_is_ble_only_both_and_wi_fi_only_stay_off_until_yo)
                } else {
                    UiText.text(R.string.ui_these_are_include_switches_turn_both_on_to_see_wi_fi_and_ble_toge)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = !filter.movingWithYou && filter.showWifi && filter.showBle,
                    onClick = { vm.updateFilter { it.copy(showWifi = true, showBle = true) } },
                    enabled = !filter.movingWithYou,
                    label = { Text(UiText.text(R.string.ui_both)) },
                )
                FieldwatchFilterChip(
                    selected = !filter.movingWithYou && filter.showWifi && !filter.showBle,
                    onClick = { vm.updateFilter { it.copy(showWifi = true, showBle = false) } },
                    enabled = !filter.movingWithYou,
                    label = { Text(UiText.text(R.string.ui_wi_fi_only)) },
                )
                FieldwatchFilterChip(
                    selected = filter.movingWithYou || (filter.showBle && !filter.showWifi),
                    onClick = { vm.updateFilter { it.copy(showWifi = false, showBle = true) } },
                    label = { Text(UiText.text(R.string.ui_ble_only)) },
                )
            }
            }

            SectionCard(UiText.text(R.string.ui_moving_with_you)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_moving_with_you), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.movingWithYou,
                    { on ->
                        vm.updateFilter { current ->
                            if (!on) current.copy(movingWithYou = false)
                            else {
                                // Follow test is BLE. Leftover Trackers / Show only hides
                                // unmatched rows; AirTags rotate, so Live looks empty.
                                val hiding = current.useClassFilter && current.excludeClasses
                                current.copy(
                                    movingWithYou = true,
                                    showWifi = false,
                                    showBle = true,
                                    namedOnly = false,
                                    customNamesOnly = false,
                                    watchedOnly = false,
                                    useClassFilter = hiding,
                                    excludeClasses = hiding,
                                    classes = if (hiding) current.classes else emptySet(),
                                    includeSignatures = false,
                                )
                            }
                        }
                    },
                )
            }
            Text(
                when {
                    !state.settings.tagLocation ->
                        UiText.text(R.string.ui_turn_on_settings_tag_detections_with_gps_then_walk_or_drive) +
                            UiText.text(R.string.ui_only_loud_ble_advertisers_that_stay_with_you_along_the_path) +
                            UiText.text(R.string.ui_wi_fi_access_points_stay_off_a_loud_ap_you_drive_past_paints_your) +
                            UiText.text(R.string.ui_the_switch_starts_a_ble_follow_test_clears_signatures_only_show_o) +
                            UiText.text(R.string.ui_or_tap_the_moving_with_you_preset_at_the_top)
                    state.operatorSpanM < 45.0 ->
                        UiText.text(R.string.ui_gps_path_so_far_value_m_keep_moving_50_m, (state.operatorSpanM.toInt()).toString()) +
                            UiText.text(R.string.ui_if_this_stays_0_while_you_drive_location_is_not_giving_a_live_fix) +
                            UiText.text(R.string.ui_set_location_to_high_accuracy_last_known_only_is_not_enough) +
                            UiText.text(R.string.ui_a_second_iphone_usually_will_not_match_ble_mac_rotation_starts_a_) +
                            when {
                                filter.customNamesOnly ->
                                    UiText.text(R.string.ui_named_radios_only_is_also_on_unlabeled_radios_stay_hidden)
                                filter.watchedOnly ->
                                    UiText.text(R.string.ui_watched_only_is_also_on_unwatched_radios_stay_hidden)
                                filter.namedOnly || filter.namedOnlyImplied() ->
                                    UiText.text(R.string.ui_signatures_only_show_only_is_also_on_unmatched_radios_stay_hidden)
                                else -> ""
                            }
                    filter.customNamesOnly || filter.watchedOnly || filter.namedOnly || filter.namedOnlyImplied() ->
                        UiText.text(R.string.ui_gps_path_value_m_signatures_only_class_show_only, (state.operatorSpanM.toInt()).toString()) +
                            UiText.text(R.string.ui_named_radios_only_or_watched_only_is_also_on_so_only_those_radios) +
                            UiText.text(R.string.ui_tap_the_moving_with_you_preset_to_test_ble_a_tag_in_your_bag_or_c)
                    else ->
                        UiText.text(R.string.ui_gps_path_value_m_loud_ble_heard_along_that, (state.operatorSpanM.toInt()).toString()) +
                            UiText.text(R.string.ui_path_at_a_fairly_steady_level_not_ones_that_only_appear_when_you) +
                            UiText.text(R.string.ui_arrive_a_tag_in_your_bag_or_car_will_match_wi_fi_access_points_st) +
                            UiText.text(R.string.ui_range_looks_like_co_travel_a_phone_s_rotating_ble_address_will_no) +
                            UiText.text(R.string.ui_live_start_over_clears_the_path_and_trails_so_you_can_test_again)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(UiText.text(R.string.ui_new_detections)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.arrivalsLearning) UiText.text(R.string.ui_new_detections_only_learning) else UiText.text(R.string.ui_new_detections_only),
                    Modifier.weight(1f),
                )
                FieldwatchSwitch(
                    filter.arrivalsOnly,
                    { on -> vm.updateFilter { it.copy(arrivalsOnly = on) } },
                )
            }
            Text(
                if (filter.arrivalsOnly) {
                    UiText.text(R.string.ui_mark_seen_and_reset_seen_sit_on_live_above_the_tabs) +
                        when {
                            state.arrivalsLearning ->
                                UiText.text(R.string.ui_learning_sitting_wi_fi_into_already_seen)
                            state.hiddenKnown > 0 ->
                                UiText.text(R.string.ui_value_already_seen_are_hidden, (state.hiddenKnown).toString())
                            else ->
                                UiText.text(R.string.ui_already_seen_is_0)
                        }
                } else {
                    UiText.text(R.string.ui_hide_radios_already_here_so_only_new_ones_show_on_live) +
                        UiText.text(R.string.ui_mark_seen_reset_seen_appear_above_the_tabs_on_live_while_this_is_) +
                        UiText.text(R.string.ui_brief_hold_still_sets_how_long_a_new_radio_stays_after_the_last_p) +
                        UiText.text(R.string.ui_randomized_ble_addresses_look_new)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(UiText.text(R.string.ui_who_stays)) {
            val namedImplied = filter.namedOnlyImplied()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    UiText.text(R.string.ui_signatures_only_hide_unmatched),
                    Modifier.weight(1f),
                    color = if (namedImplied) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                FieldwatchSwitch(
                    checked = filter.namedOnly || namedImplied,
                    onCheckedChange = { on ->
                        if (!namedImplied) vm.updateFilter { it.copy(namedOnly = on) }
                    },
                    enabled = !namedImplied,
                )
            }
            if (namedImplied) {
                Text(
                    UiText.text(R.string.ui_show_only_already_hides_unmatched_radios_turn_show_only_class_or_),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_watched_only), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.watchedOnly,
                    { on -> vm.updateFilter { it.copy(watchedOnly = on) } },
                )
            }
            Text(
                UiText.text(R.string.ui_only_radios_that_match_a_bookmarked_signature_or_a_named_radio_wi) +
                    UiText.text(R.string.ui_hide_these_still_applies_watched_only_hide_surveillance_drops_boo) +
                    UiText.text(R.string.ui_label_only_names_stay_on_named_radios_only_bookmark_on_signatures),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_named_radios_only), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.customNamesOnly,
                    { on -> vm.updateFilter { it.copy(customNamesOnly = on) } },
                )
            }
            Text(
                UiText.text(R.string.ui_only_radios_you_gave_a_custom_name_alert_can_still_be_off_setting) +
                    UiText.text(R.string.ui_a_random_privacy_mac_will_not_follow_a_rotation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_hide_fast_pair_account_key), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.hideFastPairAccountKey,
                    { on -> vm.updateFilter { it.copy(hideFastPairAccountKey = on) } },
                )
            }
            Text(
                UiText.text(R.string.ui_plaza_noise_already_paired_fast_pair_chips_with_no_other_signatur) +
                    UiText.text(R.string.ui_keeps_pairing_mode_tap_to_pair_model_id_hide_selected_fast_pair_s),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(UiText.text(R.string.ui_signature_classes)) {
            Text(
                UiText.text(R.string.ui_live_only_signatures_still_label_log_and_can_beep) +
                    UiText.text(R.string.ui_cameras_drones_finder_tags_and_the_rest_are_these_chips_show_only) +
                    UiText.text(R.string.ui_show_only_with_no_class_picked_leaves_live_unchanged),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = filter.useClassFilter && !filter.excludeClasses,
                    onClick = {
                        vm.updateFilter {
                            val on = !(it.useClassFilter && !it.excludeClasses)
                            it.copy(useClassFilter = on, excludeClasses = false)
                        }
                    },
                    label = { Text(UiText.text(R.string.ui_show_only)) },
                )
                FieldwatchFilterChip(
                    selected = filter.useClassFilter && filter.excludeClasses,
                    onClick = {
                        vm.updateFilter {
                            val on = !(it.useClassFilter && it.excludeClasses)
                            it.copy(useClassFilter = on, excludeClasses = on)
                        }
                    },
                    label = { Text(UiText.text(R.string.ui_hide_these)) },
                )
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SignatureClass.visible.sortedBy { it.uiLabel().lowercase() }.chunked(2).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            row.forEach { kind ->
                                val on = kind in filter.classes
                                FieldwatchFilterChip(
                                    selected = on,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(max = 32.dp),
                                    onClick = {
                                        vm.updateFilter { current ->
                                            val next = current.classes.toMutableSet()
                                            if (on) next.remove(kind) else next.add(kind)
                                            current.copy(classes = next)
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            ClassGlyphs.of(kind),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            kind.uiLabel(),
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            }

            SectionCard(UiText.text(R.string.ui_selected_signatures)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_show_only_selected_signatures), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.includeSignatures,
                    { on -> vm.updateFilter { it.copy(includeSignatures = on) } },
                )
            }
            if (filter.includeSignatures) {
                SignaturePickList(
                    fleets = state.fleets,
                    selected = filter.includeFleetIds,
                    help = UiText.text(R.string.ui_tap_a_class_to_open_its_signatures_only_radios_matching_a_signatu) +
                        UiText.text(R.string.ui_empty_list_no_extra_include_live_unchanged_picks_stay_if_you_turn),
                    onToggle = { id, checked ->
                        vm.updateFilter { current ->
                            val next = current.includeFleetIds.toMutableSet()
                            if (checked) next.add(id) else next.remove(id)
                            current.copy(includeFleetIds = next)
                        }
                    },
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_hide_selected_signatures), Modifier.weight(1f))
                FieldwatchSwitch(
                    filter.excludeSignatures,
                    { on -> vm.updateFilter { it.copy(excludeSignatures = on) } },
                )
            }
            if (filter.excludeSignatures) {
                SignaturePickList(
                    fleets = state.fleets,
                    selected = filter.fleetIds,
                    help = UiText.text(R.string.ui_tap_a_class_to_open_its_signatures_devices_matching_a_signature_y) +
                        UiText.text(R.string.ui_your_picks_stay_if_you_turn_this_off_and_on_again),
                    onToggle = { id, checked ->
                        vm.updateFilter { current ->
                            val next = current.fleetIds.toMutableSet()
                            if (checked) next.add(id) else next.remove(id)
                            current.copy(fleetIds = next)
                        }
                    },
                )
            }
            }

            SectionCard(UiText.text(R.string.ui_fine_filter)) {
            var rssiDrag by remember { mutableIntStateOf(filter.rssiMin) }
            var rssiDragging by remember { mutableStateOf(false) }
            LaunchedEffect(filter.rssiMin) {
                if (!rssiDragging) rssiDrag = filter.rssiMin
            }
            Text(UiText.text(R.string.ui_minimum_rssi_value_dbm, (rssiDrag).toString()), style = MaterialTheme.typography.labelLarge)
            FieldwatchSlider(
                value = rssiDrag.toFloat(),
                onValueChange = { v ->
                    rssiDragging = true
                    rssiDrag = v.toInt()
                },
                onValueChangeFinished = {
                    vm.updateFilter { it.copy(rssiMin = rssiDrag) }
                    rssiDragging = false
                },
                valueRange = -100f..-30f,
            )

            FieldwatchOutlinedField(
                filter.nameQuery,
                { value -> vm.updateFilter { it.copy(nameQuery = value) } },
                UiText.text(R.string.ui_name_mac_contains),
            )
            FieldwatchOutlinedField(
                filter.ouiQuery,
                { value -> vm.updateFilter { it.copy(ouiQuery = value) } },
                UiText.text(R.string.ui_oui_vendor_contains),
            )

            Text(UiText.text(R.string.ui_extra_filter_logic), style = MaterialTheme.typography.labelLarge)
            Text(
                UiText.text(R.string.ui_and_or_applies_to_name_oui_rssi_and_class_include_not_to_radios_n),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldwatchFilterChip(
                    selected = filter.logic == FilterLogic.AND,
                    onClick = { vm.updateFilter { it.copy(logic = FilterLogic.AND) } },
                    label = { Text(UiText.text(R.string.ui_and_407)) },
                )
                FieldwatchFilterChip(
                    selected = filter.logic == FilterLogic.OR,
                    onClick = { vm.updateFilter { it.copy(logic = FilterLogic.OR) } },
                    label = { Text(UiText.text(R.string.ui_or)) },
                )
            }

            FieldwatchActionButton(onClick = { confirmReset = true }) {
                Text(UiText.text(R.string.ui_reset_filter))
            }
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(UiText.text(R.string.ui_reset_filter_410)) },
            text = {
                Text(
                    UiText.text(R.string.ui_clears_every_switch_and_pick_on_this_tab_radios_classes_selected_) +
                        UiText.text(R.string.ui_presets_you_saved_stay_the_live_display_goes_back_to_the_unfilter),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        vm.updateFilter { app.fieldwatch.domain.FilterState() }
                    },
                ) { Text(UiText.text(R.string.ui_reset)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(UiText.text(R.string.ui_cancel)) }
            },
        )
    }
    pendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(UiText.text(R.string.ui_delete_preset)) },
            text = {
                Text(
                    if (preset.isBuiltIn()) {
                        UiText.text(R.string.ui_remove_stock_chip_value_from_this_list_catalog_updates_will_not_p, (preset.name).toString())
                    } else {
                        UiText.text(R.string.ui_delete_preset_value_this_cannot_be_undone_the_filter_on_live_does, (preset.name).toString())
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePreset(preset.id)
                    pendingDelete = null
                }) { Text(UiText.text(R.string.ui_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(UiText.text(R.string.ui_cancel)) }
            },
        )
    }
}

@Composable
private fun SignaturePickList(
    fleets: List<Fleet>,
    selected: Set<String>,
    help: String,
    onToggle: (id: String, checked: Boolean) -> Unit,
) {
    val groups = remember(fleets) {
        fleets.groupBy { it.kind.folded() }
            .toList()
            .sortedBy { it.first.uiLabel().lowercase() }
            .map { (kind, rows) -> kind to rows.sortedBy { it.name.lowercase() } }
    }
    var open by remember {
        mutableStateOf(
            groups.filter { (_, rows) -> rows.any { it.id in selected } }
                .map { it.first.name }
                .toSet(),
        )
    }
    Column(
        modifier = Modifier.padding(start = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        groups.forEach { (kind, rows) ->
            val classId = kind.name
            val expanded = classId in open
            val picked = rows.count { it.id in selected }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        open = if (expanded) open - classId else open + classId
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    ClassGlyphs.of(kind),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    kind.uiLabel(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(if (expanded) "▾  " else "▸  ")
                        if (picked > 0) append("$picked/")
                        append(rows.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (picked > 0) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (expanded) {
                rows.forEach { fleet ->
                    val on = fleet.id in selected
                    Row(
                        modifier = Modifier.padding(start = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(app.fieldwatch.UiCatalogText.forFleet(fleet, fleet.name), Modifier.weight(1f))
                        FieldwatchSwitch(on, { checked -> onToggle(fleet.id, checked) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(
    name: String,
    selected: Boolean = false,
    onApply: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = FilterChipDefaults.shape,
        color = if (selected) spectreSectionFill() else spectreTileFill(),
        border = BorderStroke(
            1.dp,
            if (selected) PhosphorActive.nightIf(LocalNightMode.current) else spectreTileEdge(),
        ),
        modifier = modifier
            .heightIn(max = 32.dp)
            .combinedClickable(
                onClick = onApply,
                onLongClick = onLongPress,
            ),
    ) {
        Text(
            name,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
