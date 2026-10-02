package app.fieldwatch.ui.screen

import app.fieldwatch.UiText
import app.fieldwatch.UiCatalogText
import app.fieldwatch.ui.uiLabel
import app.fieldwatch.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import app.fieldwatch.ui.component.DecodeGlyph
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import app.fieldwatch.ui.component.FieldwatchFilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import app.fieldwatch.ui.component.FieldwatchActionButton
import app.fieldwatch.ui.component.FieldwatchDropdownField
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import app.fieldwatch.ui.component.FieldwatchSwitch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldwatch.domain.Fleet
import app.fieldwatch.domain.MatchRule
import app.fieldwatch.domain.Palette
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.RuleKind
import app.fieldwatch.domain.SignatureClass
import app.fieldwatch.domain.SignatureListSort
import app.fieldwatch.domain.groupedByClass
import app.fieldwatch.domain.sortedForCatalog
import app.fieldwatch.ui.NestedTabInsets
import app.fieldwatch.ui.NestedTopBar
import app.fieldwatch.ui.RadioClassBadge
import app.fieldwatch.ui.FieldwatchUi
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.SectionCard
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetsScreen(
    state: FieldwatchUi,
    vm: FieldwatchViewModel,
    onCandidateDraftClosed: () -> Unit = {},
) {
    val draft = state.draftFleet
    if (draft != null) {
        val isNew = state.fleets.none { it.id == draft.id }
        var working by remember(draft.id) { mutableStateOf(draft) }
        var editingDecode by remember(draft.id) { mutableStateOf(false) }
        if (editingDecode) {
            val preview = state.devices.firstOrNull { working.id in it.fleetIds }
                ?: state.selected?.takeIf { working.id in it.fleetIds }
            DecodeFieldsScreen(
                fleet = working,
                previewDevice = preview,
                onSave = { decode ->
                    val next = working.copy(decode = decode)
                    working = next
                    vm.saveFleetKeepDraft(next)
                    editingDecode = false
                },
                onBack = { editingDecode = false },
            )
            return
        }
        FleetEditor(
            working,
            isNew = isNew,
            onSave = { fleet ->
                val bounce = vm.takeDraftFromCandidates()
                vm.upsertFleet(fleet) {
                    if (bounce) {
                        vm.startSignatureCandidates()
                        onCandidateDraftClosed()
                    }
                }
            },
            onCancel = {
                val bounce = vm.takeDraftFromCandidates()
                vm.cancelDraft()
                if (bounce) onCandidateDraftClosed()
            },
            onDelete = if (isNew) null else ({ vm.deleteFleet(draft.id) }),
            onOpenDecode = { current ->
                working = current
                editingDecode = true
            },
        )
        return
    }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = { NestedTopBar(UiText.text(R.string.ui_signatures_value, (state.fleets.size).toString())) },
        floatingActionButton = {
            FloatingActionButton(onClick = vm::beginNewFleet) {
                Icon(Icons.Outlined.Add, UiText.text(R.string.ui_new_signature))
            }
        },
    ) { pad ->
        val sort = state.settings.signatureListSort
        val openClasses by vm.catalogOpenClasses.collectAsStateWithLifecycle()
        LazyColumn(
            Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    if (sort == SignatureListSort.CLASS) {
                        UiText.text(R.string.ui_tap_a_class_to_open_its_signatures_bookmark_beep_hide_a_family_on)
                    } else {
                        UiText.text(R.string.ui_tap_to_edit_bookmark_beep_when_that_family_appears_hide_a_family_)
                    },
                    style = compactLine(12.sp, 14.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    FieldwatchFilterChip(
                        selected = sort == SignatureListSort.NAME,
                        onClick = { vm.setSignatureListSort(SignatureListSort.NAME) },
                        label = { Text(UiText.text(R.string.ui_name_a_z)) },
                    )
                    FieldwatchFilterChip(
                        selected = sort == SignatureListSort.CLASS,
                        onClick = { vm.setSignatureListSort(SignatureListSort.CLASS) },
                        label = { Text(UiText.text(R.string.ui_class_a_z)) },
                    )
                }
            }
            if (sort == SignatureListSort.CLASS) {
                state.fleets.groupedByClass().forEach { (kind, rows) ->
                    val classId = kind.name
                    val expanded = classId in openClasses
                    item(key = "class-$classId") {
                        SignatureClassHeader(
                            kind = kind,
                            count = rows.size,
                            colorIndex = rows.firstOrNull()?.colorIndex ?: 0,
                            expanded = expanded,
                            onToggle = { vm.toggleCatalogClass(classId) },
                        )
                    }
                    if (expanded) {
                        items(rows, key = { it.id }) { fleet ->
                            Box(Modifier.padding(start = 16.dp)) {
                                SignatureRow(fleet, state, vm)
                            }
                        }
                    }
                }
            } else {
                items(state.fleets.sortedForCatalog(sort), key = { it.id }) { fleet ->
                    SignatureRow(fleet, state, vm)
                }
            }
        }
    }
}

@Composable
private fun SignatureClassHeader(
    kind: SignatureClass,
    count: Int,
    colorIndex: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val accent = Color(Palette.color(colorIndex)).nightIf(LocalNightMode.current)
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioClassBadge(classKind = kind, accent = accent)
            Spacer(Modifier.width(10.dp))
            Text(
                kind.uiLabel(),
                style = compactLine(16.sp, 18.sp, FontWeight.SemiBold),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (expanded) "▾  $count" else "▸  $count",
                style = compactLine(14.sp, 16.sp, FontWeight.Bold).copy(fontFamily = FontFamily.Monospace),
                color = accent,
            )
        }
    }
}

@Composable
private fun SignatureRow(fleet: Fleet, state: FieldwatchUi, vm: FieldwatchViewModel) {
    val color = Color(Palette.color(fleet.colorIndex)).nightIf(LocalNightMode.current)
    val liveHits = state.devices.count { fleet.id in it.fleetIds && !it.gone }
    Surface(
        onClick = { vm.editFleet(fleet) },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioClassBadge(
                classKind = fleet.kind,
                accent = color,
            )
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        UiCatalogText.forFleet(fleet, fleet.name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = compactLine(16.sp, 18.sp, FontWeight.SemiBold),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (fleet.decode != null) {
                        DecodeGlyph(
                            tint = color,
                            size = 14.dp,
                        )
                    }
                }
                Text(
                    UiText.text(R.string.ui_value_value_rules_value_live_value, (fleet.kind.uiLabel()).toString(), (fleet.rules.size).toString(), (liveHits).toString(), (if (fleet.matchAny) "OR" else "AND").toString()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = compactLine(11.sp, 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { vm.toggleWatchFleet(fleet) },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    if (vm.isFleetWatched(fleet.id)) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                    UiText.text(R.string.ui_beep_when_this_signature_appears),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FleetEditor(
    initial: Fleet,
    isNew: Boolean = false,
    onSave: (Fleet) -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onOpenDecode: (Fleet) -> Unit = {},
) {
    var fleet by remember(initial.id) { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(initial.decode) {
        fleet = fleet.copy(decode = initial.decode)
    }
    Scaffold(
        contentWindowInsets = NestedTabInsets,
        topBar = {
            NestedTopBar(
                title = if (isNew) UiText.text(R.string.ui_new_signature) else UiText.text(R.string.ui_edit_signature),
                navigationIcon = { TextButton(onClick = onCancel) { Text(UiText.text(R.string.ui_cancel)) } },
                actions = { TextButton(onClick = { onSave(fleet) }) { Text(UiText.text(R.string.ui_save)) } },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard(UiText.text(R.string.ui_identity)) {
            FieldwatchOutlinedField(fleet.name, { fleet = fleet.copy(name = it) }, UiText.text(R.string.ui_name))
            FieldwatchOutlinedField(
                UiCatalogText.forFleet(fleet, fleet.notes),
                { fleet = fleet.copy(notes = it) },
                UiText.text(R.string.ui_notes),
                supportingText = UiText.text(R.string.ui_shows_on_radio_detail_for_matching_radios_and_in_share_ai_export_),
                singleLine = false,
                minLines = 2,
            )
            FieldwatchOutlinedField(
                UiCatalogText.forFleet(fleet, fleet.attentionNote),
                { fleet = fleet.copy(attentionNote = it) },
                UiText.text(R.string.ui_extra_attention),
                supportingText = UiText.text(R.string.ui_optional_if_this_is_not_empty_matching_radios_get_a_on_live_this_),
                singleLine = false,
                minLines = 3,
            )
            }

            SectionCard(UiText.text(R.string.ui_matching)) {
            var classMenu by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(classMenu, { classMenu = it }) {
                FieldwatchDropdownField(UiText.text(R.string.ui_class), fleet.kind.uiLabel(), classMenu)
                ExposedDropdownMenu(classMenu, { classMenu = false }) {
                    SignatureClass.visible.sortedBy { it.uiLabel().lowercase() }.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(kind.uiLabel()) },
                            onClick = {
                                fleet = fleet.copy(kind = kind)
                                classMenu = false
                            },
                        )
                    }
                }
            }
            Text(
                UiText.text(R.string.ui_filters_show_only_hide_these_class_sits_finder_tags_cameras_are_t),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_match_any_rule_or), Modifier.weight(1f))
                FieldwatchSwitch(fleet.matchAny, { fleet = fleet.copy(matchAny = it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_cluster_by_oui), Modifier.weight(1f))
                FieldwatchSwitch(fleet.clusterByOui, { fleet = fleet.copy(clusterByOui = it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(UiText.text(R.string.ui_sequential_macs), Modifier.weight(1f))
                FieldwatchSwitch(fleet.sequentialMac, { fleet = fleet.copy(sequentialMac = it) })
            }
            FieldwatchOutlinedField(
                fleet.minPeers.toString(),
                { fleet = fleet.copy(minPeers = it.toIntOrNull() ?: 0) },
                UiText.text(R.string.ui_min_peers_0_off),
            )
            FieldwatchOutlinedField(
                fleet.peerWindowSec.toString(),
                { fleet = fleet.copy(peerWindowSec = it.toIntOrNull() ?: 60) },
                UiText.text(R.string.ui_peer_window_seconds),
            )
            }

            SectionCard(UiText.text(R.string.ui_color)) {
            ColorPicker(fleet.colorIndex) { fleet = fleet.copy(colorIndex = it) }
            Text(
                UiText.text(R.string.ui_stock_colors_are_by_class_red_pentest_amber_cameras_alpr_purple_p),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            }

            SectionCard(UiText.text(R.string.ui_rules)) {
            Text(
                UiText.text(R.string.ui_each_rule_has_its_own_switch_off_keeps_the_rule_but_it_does_not_m) +
                    UiText.text(R.string.ui_use_that_to_mute_noisy_ouis_or_names_on_one_signature_without_del),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            fleet.rules.forEachIndexed { index, rule ->
                RuleEditor(
                    rule = rule,
                    onChange = { next ->
                        val rules = fleet.rules.toMutableList()
                        rules[index] = next
                        fleet = fleet.copy(rules = rules)
                    },
                    onDelete = {
                        fleet = fleet.copy(rules = fleet.rules.filterIndexed { i, _ -> i != index })
                    },
                )
            }
            FieldwatchActionButton(
                onClick = {
                    fleet = fleet.copy(rules = fleet.rules + MatchRule(RuleKind.OUI, text = ""))
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(UiText.text(R.string.ui_add_rule)) }
            }

            if (fleet.canHaveBleDecode()) {
                val decodeCount = fleet.decode?.fields?.size ?: 0
                SectionCard(UiText.text(R.string.ui_decode_fields)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDecode(fleet) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (decodeCount == 0) UiText.text(R.string.ui_none) else UiText.text(R.string.ui_value_fields, (decodeCount).toString()),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        "›",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    UiText.text(R.string.ui_optional_after_this_signature_matches_map_cleartext_ble_bytes_to_),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                }
            }
            if (onDelete != null) {
                FieldwatchActionButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Delete, null)
                    Spacer(Modifier.padding(4.dp))
                    Text(UiText.text(R.string.ui_delete_signature))
                }
            }
        }
    }
    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(UiText.text(R.string.ui_delete_this_signature)) },
            text = {
                Text(
                    if (initial.builtIn) {
                        UiText.text(R.string.ui_value_is_a_built_in_signature_deleting_it_removes_matching_its_bo, (fleet.name).toString())
                    } else {
                        UiText.text(R.string.ui_value_will_be_removed_matching_its_bookmark_and_filter_chips_go_w, (fleet.name).toString())
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text(UiText.text(R.string.ui_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(UiText.text(R.string.ui_cancel)) }
            },
        )
    }
}

@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Palette.fleet.forEachIndexed { index, argb ->
                val on = index == selected
                val fill = Color(argb).nightIf(LocalNightMode.current)
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(8.dp),
                    color = fill,
                    border = BorderStroke(
                        width = if (on) 2.dp else 1.dp,
                        color = if (on) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (on) {
                            Icon(
                                Icons.Outlined.Check,
                                contentDescription = UiText.text(R.string.ui_selected_color),
                                tint = if (fill.luminance() > 0.45f) {
                                    Color(0xFF12171C)
                                } else {
                                    Color.White
                                },
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditor(rule: MatchRule, onChange: (MatchRule) -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldwatchSwitch(
                rule.enabled,
                { onChange(rule.copy(enabled = it)) },
            )
            ExposedDropdownMenuBox(
                expanded,
                { expanded = it },
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 4.dp),
            ) {
                FieldwatchDropdownField(UiText.text(R.string.ui_kind), ruleKindLabel(rule.kind), expanded)
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    RuleKind.entries.forEach { kind ->
                        DropdownMenuItem(text = { Text(ruleKindLabel(kind)) }, onClick = {
                            onChange(rule.copy(kind = kind))
                            expanded = false
                        })
                    }
                }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, UiText.text(R.string.ui_delete_rule)) }
        }
        Column(
            Modifier.padding(top = 12.dp, start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        when (rule.kind) {
            RuleKind.OUI, RuleKind.MAC_PREFIX, RuleKind.NAME_CONTAINS, RuleKind.NAME_GLOB, RuleKind.SERVICE_UUID, RuleKind.VENDOR_IE_OUI -> {
                FieldwatchOutlinedField(
                    rule.text,
                    { onChange(rule.copy(text = it)) },
                    UiText.text(R.string.ui_value),
                )
            }
            RuleKind.MANUFACTURER_ID -> {
                FieldwatchOutlinedField(
                    if (rule.companyId == 0) "" else "0x%04X".format(rule.companyId),
                    {
                        val parsed = it.removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: 0
                        onChange(rule.copy(companyId = parsed))
                    },
                    UiText.text(R.string.ui_company_id_hex),
                )
            }
            RuleKind.MANUFACTURER_DATA -> {
                FieldwatchOutlinedField(
                    if (rule.companyId == 0) "" else "0x%04X".format(rule.companyId),
                    {
                        val parsed = it.removePrefix("0x").removePrefix("0X").toIntOrNull(16) ?: 0
                        onChange(rule.copy(companyId = parsed))
                    },
                    UiText.text(R.string.ui_company_id_hex),
                )
                FieldwatchOutlinedField(
                    rule.dataPrefixHex,
                    { onChange(rule.copy(dataPrefixHex = it)) },
                    UiText.text(R.string.ui_data_prefix_hex),
                )
            }
            RuleKind.SERVICE_DATA -> {
                FieldwatchOutlinedField(
                    rule.text,
                    { onChange(rule.copy(text = it)) },
                    UiText.text(R.string.ui_service_uuid_empty_any_contains),
                )
                FieldwatchOutlinedField(
                    rule.dataPrefixHex,
                    { onChange(rule.copy(dataPrefixHex = it)) },
                    if (rule.text.isBlank()) UiText.text(R.string.ui_contains_hex) else UiText.text(R.string.ui_data_prefix_hex),
                )
            }
            RuleKind.RADIO_KIND -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wi-Fi", Modifier.padding(end = 8.dp))
                    FieldwatchSwitch(rule.radio != RadioKind.BLE, { onChange(rule.copy(radio = if (it) RadioKind.WIFI else RadioKind.BLE)) })
                }
            }
            RuleKind.HIDDEN_SSID -> Text(UiText.text(R.string.ui_matches_hidden_ssids), style = MaterialTheme.typography.bodySmall)
        }
        }
    }
}

private fun ruleKindLabel(kind: RuleKind): String = when (kind) {
    RuleKind.OUI -> "OUI"
    RuleKind.MAC_PREFIX -> UiText.text(R.string.ui_mac_prefix)
    RuleKind.NAME_CONTAINS -> UiText.text(R.string.ui_name_contains)
    RuleKind.NAME_GLOB -> UiText.text(R.string.ui_name_glob)
    RuleKind.SERVICE_UUID -> UiText.text(R.string.ui_service_uuid)
    RuleKind.SERVICE_DATA -> UiText.text(R.string.ui_service_data)
    RuleKind.MANUFACTURER_ID -> UiText.text(R.string.ui_manufacturer_id)
    RuleKind.MANUFACTURER_DATA -> UiText.text(R.string.ui_manufacturer_data)
    RuleKind.RADIO_KIND -> UiText.text(R.string.ui_radio_kind)
    RuleKind.HIDDEN_SSID -> UiText.text(R.string.ui_hidden_ssid)
    RuleKind.VENDOR_IE_OUI -> UiText.text(R.string.ui_vendor_ie_oui)
}

private fun compactLine(
    size: androidx.compose.ui.unit.TextUnit,
    line: androidx.compose.ui.unit.TextUnit,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontSize = size,
    lineHeight = line,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)
