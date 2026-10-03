package app.fieldwatch.ui.screen

import app.fieldwatch.UiText
import app.fieldwatch.UiCatalogText
import app.fieldwatch.UiDetailText
import app.fieldwatch.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import app.fieldwatch.ui.component.DecodeGlyph
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import app.fieldwatch.ui.component.FieldwatchActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import app.fieldwatch.ui.component.FieldwatchOutlinedField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.fieldwatch.domain.CodDecoder
import app.fieldwatch.domain.FamilyVerdict
import app.fieldwatch.domain.Geo
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.domain.DeviceExplain
import app.fieldwatch.domain.Palette
import app.fieldwatch.domain.RadioDb
import app.fieldwatch.domain.RadioBookmarks
import app.fieldwatch.domain.RadioKind
import app.fieldwatch.domain.Rssi
import app.fieldwatch.domain.ServiceDataRecord
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.SignatureFamilyHint
import app.fieldwatch.domain.SignatureFieldDecoder
import app.fieldwatch.domain.hexSpaced
import app.fieldwatch.domain.label
import app.fieldwatch.ui.RadioKindMark
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.theme.Amber
import app.fieldwatch.ui.theme.Cyan
import app.fieldwatch.ui.theme.LocalNightMode
import app.fieldwatch.ui.theme.nightIf
import app.fieldwatch.ui.component.PresenceTrack
import app.fieldwatch.ui.component.Sparkline
import app.fieldwatch.ui.component.StickyHeight
import app.fieldwatch.ui.component.rssiColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    device: Sighting,
    vm: FieldwatchViewModel,
    watched: Boolean,
    onBack: () -> Unit,
    onCreateFleet: () -> Unit,
    onHunt: () -> Unit,
    demoMode: Boolean = false,
) {
    val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    val accent = (device.fleetIds.firstOrNull()
        ?.let { Color(Palette.color(vm.fleetColor(it))) }
        ?: rssiColor(device.rssi))
        .nightIf(LocalNightMode.current)
    val facts = device.facts
    val peerContext by vm.ui.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val associationKey = Triple(device.key, demoMode, locale)
    val associatedResult by produceState(associationKey to emptyList<app.fieldwatch.domain.AdvPayloadDecoder.Field>(), device, peerContext.devices, demoMode, locale) {
        value = associationKey to withContext(Dispatchers.Default) {
            app.fieldwatch.domain.WpsAssociation.fields(device, peerContext.devices, UiText::explanation, demoMode) +
                app.fieldwatch.domain.BleServiceInspection.sharedFields(device, peerContext.devices, UiText::explanation)
        }
    }
    val associatedFields = associatedResult.second.takeIf { associatedResult.first == associationKey }.orEmpty()
    val familyHint by vm.familyHint.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val custom = vm.watchLabelFor(device.key)
                    val title = custom?.takeIf { it.isNotBlank() }
                        ?: device.listTitle(device.fleetIds.map { vm.fleetName(it) }, UiText::explanation)
                    Text(MacUtil.redactMacIn(title, device.mac, demoMode), maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, UiText.text(R.string.ui_back)) }
                },
                actions = {
                    IconButton(onClick = { vm.toggleWatchDevice(device) }) {
                        Icon(if (watched) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder, UiText.text(R.string.ui_watch))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(MacUtil.screenMac(device.mac, demoMode), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleMedium)
            app.fieldwatch.ui.component.RangingBadge(device)
            if (device.gone) {
                Text(
                    UiText.text(R.string.ui_not_on_the_air_this_is_the_last_detail_we_heard),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            var nameDraft by remember(device.key) {
                mutableStateOf(vm.watchLabelFor(device.key).orEmpty())
            }
            var lastSaved by remember(device.key) {
                mutableStateOf(vm.watchLabelFor(device.key).orEmpty())
            }
            var editingName by remember(device.key) { mutableStateOf(false) }
            val draftLabel = RadioBookmarks.clip(nameDraft)
            val nameIsSaved = lastSaved.isNotBlank() && draftLabel == lastSaved
            val canName = RadioBookmarks.canSetCustomName(device)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    if (lastSaved.isNotBlank()) {
                        Text(
                            UiText.text(R.string.ui_custom_name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(lastSaved, style = MaterialTheme.typography.titleLarge)
                        Text(
                            UiText.text(R.string.ui_advertised),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            device.name.ifBlank { UiText.text(R.string.ui_no_advertised_name) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (device.name.isNotBlank()) {
                        Text(
                            UiText.text(R.string.ui_advertised_name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(device.name, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            UiText.text(R.string.ui_name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            UiText.text(R.string.ui_no_advertised_name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (canName) {
                    IconButton(onClick = { editingName = !editingName }) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editingName) UiText.text(R.string.ui_hide_custom_name) else UiText.text(R.string.ui_custom_name),
                        )
                    }
                }
            }
            if (editingName && canName) {
                FieldwatchOutlinedField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it.take(RadioBookmarks.MAX_NAME) },
                    label = UiText.text(R.string.ui_custom_name),
                    supportingText = RadioBookmarks.customNameHint(device, UiText::explanation),
                )
                FieldwatchActionButton(
                    onClick = {
                        vm.saveRadioName(device, nameDraft)
                        nameDraft = draftLabel
                        lastSaved = draftLabel
                        scope.launch {
                            snackbarHostState.showSnackbar(UiText.text(R.string.ui_saved_as_value, (draftLabel).toString()))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nameDraft.isNotBlank() && !nameIsSaved,
                ) {
                    if (nameIsSaved) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(4.dp))
                        Text(UiText.text(R.string.ui_saved))
                    } else {
                        Text(UiText.text(R.string.ui_save_name))
                    }
                }
            }

            var notesDraft by remember(device.key) {
                mutableStateOf(vm.watchObserverNoteFor(device.key).orEmpty())
            }
            var lastSavedNotes by remember(device.key) {
                mutableStateOf(vm.watchObserverNoteFor(device.key).orEmpty())
            }
            var editingNotes by remember(device.key) { mutableStateOf(false) }
            val draftNotes = RadioBookmarks.clipNotes(notesDraft)
            val notesIsSaved = draftNotes == lastSavedNotes
            if (canName || lastSavedNotes.isNotBlank()) {
                ObserverNotesCard(
                    notes = lastSavedNotes,
                    canEdit = canName,
                    editing = editingNotes && canName,
                    draft = notesDraft,
                    onToggleEdit = { editingNotes = !editingNotes },
                    onDraftChange = { notesDraft = it.take(RadioBookmarks.MAX_NOTES) },
                    onSave = {
                        vm.saveRadioNotes(device, notesDraft)
                        notesDraft = draftNotes
                        lastSavedNotes = draftNotes
                        if (lastSaved.isBlank() && draftNotes.isNotBlank()) {
                            val suggest = RadioBookmarks.suggestLabel(
                                device,
                                device.fleetIds.map { vm.fleetName(it) },
                            )
                            lastSaved = suggest
                            nameDraft = suggest
                        }
                        editingNotes = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (draftNotes.isBlank()) UiText.text(R.string.ui_observer_notes_cleared) else UiText.text(R.string.ui_observer_notes_saved),
                            )
                        }
                    },
                    saveEnabled = !notesIsSaved,
                    saved = notesIsSaved && lastSavedNotes.isNotBlank(),
                )
            }

            val guess = DeviceExplain.guess(device, device.fleetIds.map { vm.fleetName(it) }, UiText::explanation)
            StickyHeight(device.key to "guess") { GuessCard(guess) }
            val attention = vm.uiSignatureNotesFor(device, attention = true)
            if (attention.isNotEmpty()) {
                StickyHeight(device.key to "attention") { ExtraAttentionCard(attention) }
            }
            val notes = vm.uiSignatureNotesFor(device)
            if (notes.isNotEmpty()) {
                StickyHeight(device.key to "notes") { SignatureNotesCard(notes) }
            }

            StickyHeight(device.key to "identity") {
                Section(UiText.text(R.string.ui_identity))
                Meta(
                    UiText.text(R.string.ui_radio),
                    if (device.kind == RadioKind.WIFI) {
                        UiText.text(R.string.ui_wi_fi_access_point_beaconing_a_network)
                    } else {
                        UiText.text(R.string.ui_bluetooth_low_energy_advertiser)
                    },
                )
                Meta(UiText.text(R.string.ui_address), DeviceExplain.addressExplain(device, UiText::explanation))
                vendorLine(device)?.let { Meta(UiText.text(R.string.ui_who_made_it), it) }
                    ?: Meta(UiText.text(R.string.ui_oui_vendor_prefix), UiText.text(R.string.ui_value_no_ieee_match_randomized_addresses_usually_have_none, (device.oui).toString()))
                if (device.hiddenSsid) {
                    Meta(UiText.text(R.string.ui_network_name_ssid), UiText.text(R.string.ui_hidden_the_ap_is_beaconing_but_not_publishing_a_name))
                }
            }

            StickyHeight(device.key to "signal") {
                Section(UiText.text(R.string.ui_signal))
                if (device.gone) {
                    Meta(UiText.text(R.string.ui_how_loud_here_rssi), UiText.text(R.string.ui_not_available))
                    Meta(
                        UiText.text(R.string.ui_last_heard),
                        buildString {
                            append(fmt.format(Date(device.lastSeen)))
                            Rssi.lastMeasured(device.rssi, device.rssiHistory)?.let {
                                append(UiText.text(R.string.ui_at_value_dbm, (it).toString()))
                            }
                        },
                    )
                } else {
                    Meta(UiText.text(R.string.ui_how_loud_here_rssi), DeviceExplain.rssiExplain(device.rssi, UiText::explanation))
                    Text(
                        UiText.text(R.string.ui_closer_to_0_dbm_is_louder_here_not_a_distance),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Meta(
                    UiText.text(R.string.ui_heard_range_this_session),
                    Rssi.sessionRange(device.rssiMin, device.rssiMax, device.rssiHistory, UiText::explanation),
                )
                facts.txPowerDbm?.let {
                    Meta(UiText.text(R.string.ui_claimed_transmit_power), UiText.text(R.string.ui_value_dbm_how_loud_it_says_it_transmits_not_a_distance, (it).toString()))
                }
                if (device.channel != 0 || device.frequencyMhz != 0) {
                    Meta(
                        UiText.text(R.string.ui_channel_frequency),
                        buildString {
                            if (device.channel != 0) append(UiText.text(R.string.ui_channel_value, (device.channel).toString()))
                            if (device.frequencyMhz != 0) {
                                if (isNotEmpty()) append("  ·  ")
                                append("${device.frequencyMhz} MHz")
                            }
                            facts.channelWidth?.let { append(UiText.text(R.string.ui_value_wide, (it).toString())) }
                        },
                    )
                }
                facts.wifiStandard?.let { Meta(UiText.text(R.string.ui_wi_fi_generation), it) }
                if (facts.centerFreq0 != null || facts.centerFreq1 != null) {
                    Meta(
                        UiText.text(R.string.ui_center_frequencies),
                        listOfNotNull(
                            facts.centerFreq0?.let { "$it MHz" },
                            facts.centerFreq1?.let { "$it MHz" },
                        ).joinToString("  ·  "),
                    )
                }
            }

            if (device.kind == RadioKind.BLE) {
                StickyHeight(device.key to "ble") {
                Section(UiText.text(R.string.ui_bluetooth_advertisement))
                facts.primaryPhy?.let {
                    val phys = listOfNotNull(it, facts.secondaryPhy).distinct()
                    Meta(UiText.text(R.string.ui_radio_phy), phys.joinToString(" / ") { phy -> DeviceExplain.phyExplain(phy, UiText::explanation) })
                }
                facts.connectable?.let {
                    Meta(
                        UiText.text(R.string.ui_connectable),
                        if (it) UiText.text(R.string.ui_yes_a_phone_could_open_a_ble_connection)
                        else UiText.text(R.string.ui_no_broadcast_only_you_can_hear_it_not_join_it_from_this_scan),
                    )
                }
                facts.advertisingIntervalMs?.let {
                    Meta(
                        UiText.text(R.string.ui_how_often_it_advertises),
                        UiText.text(R.string.ui_0f_ms_between_bursts_smaller_chattier_on_the_air).format(it),
                    )
                }
                facts.periodicIntervalMs?.let {
                    Meta(UiText.text(R.string.ui_periodic_advertising), UiText.text(R.string.ui_0f_ms).format(it))
                }
                facts.advFlags?.let { flags ->
                    Meta(UiText.text(R.string.ui_discoverability), DeviceExplain.flagsExplain(flags, UiText::explanation))
                    Meta(UiText.text(R.string.ui_flags_raw), "0x%02X".format(flags), mono = true)
                }
                facts.appearance?.let { value ->
                    val name = RadioDb.appearance(value)?.let(UiText::explanation)
                    Meta(
                        UiText.text(R.string.ui_what_it_says_it_is_appearance),
                        name?.let { UiText.text(R.string.ui_value_nthe_device_publishes_this_gap_appearance_code_to_describe_, (it).toString()) }
                            ?: UiText.text(R.string.ui_unlisted_appearance_0x_04x).format(value),
                    )
                    Meta(UiText.text(R.string.ui_appearance_code), "0x%04X".format(value), mono = true)
                }
                CodDecoder.decodeOrNull(facts.deviceClass, UiText::explanation)?.let { cod ->
                    Meta(
                        UiText.text(R.string.ui_classic_bluetooth_class),
                        buildString {
                            append(cod.major)
                            if (cod.minor.isNotBlank()) append(" / ").append(cod.minor)
                            append(UiText.text(R.string.ui_nthis_is_the_class_of_device_bitfield_used_by_classic_bluetooth))
                            if (cod.services.isNotEmpty()) {
                                append(UiText.text(R.string.ui_nalso_offers))
                                append(cod.services.joinToString(", "))
                            }
                        },
                    )
                }
                }
            }

            if (device.kind == RadioKind.WIFI) {
                StickyHeight(device.key to "wifi") {
                    Section(UiText.text(R.string.ui_wi_fi_access_point))
                    facts.security?.let {
                        Meta(UiText.text(R.string.ui_encryption_login), DeviceExplain.wifiSecurityExplain(it, UiText::explanation))
                        if (it.isNotBlank()) Meta(UiText.text(R.string.ui_security_string), it, mono = true)
                    }
                    facts.supportedRates?.let {
                        Meta(UiText.text(R.string.ui_supported_rates), UiText.text(R.string.ui_value_mbps_required_basic_rate, (it).toString()))
                    }
                    facts.capabilities?.takeIf { it.isNotBlank() && it != facts.security }?.let {
                        Meta(UiText.text(R.string.ui_capability_string), it, mono = true)
                    }
                }
            }

            app.fieldwatch.domain.AdvPayloadDecoder.decodeDevice(device, UiText::explanation).forEach { field ->
                Meta(field.label, field.value)
            }
            associatedFields.forEach { field -> Meta(field.label, field.value) }
            if (device.fleetIds.isEmpty()) Meta(UiText.text(R.string.ui_matched_signatures),
                UiText.explanation("No catalog signature matched; parsed identity fields may still be available."))

            if (device.serviceUuids.isNotEmpty() || facts.serviceData.isNotEmpty()) {
                StickyHeight(device.key to "services") {
                    if (device.serviceUuids.isNotEmpty()) {
                        Section(UiText.text(R.string.ui_services_it_offers))
                        Meta(
                            UiText.text(R.string.ui_service_ids),
                            device.serviceUuids.joinToString("\n") { uuid ->
                                DeviceExplain.uuidGloss(uuid, UiText::explanation)?.let { "$uuid  ·  $it" } ?: uuid
                            },
                            mono = true,
                        )
                    }
                    facts.serviceData.forEach { sd ->

                        Meta(
                            serviceDataHeading(sd),
                            sd.dataHex.hexSpaced().ifBlank { UiText.text(R.string.placeholder_empty) },
                            mono = true,
                        )
                    }
                }
            }

            if (device.kind == RadioKind.BLE || device.kind == RadioKind.WIFI) {
                val fleets = vm.ui.value.fleets
                val decoded = remember(device.key, device.facts, device.fleetIds, fleets) {
                    SignatureFieldDecoder.decodeSighting(device, fleets)
                }
                val mapped = device.fleetIds.mapNotNull { id -> fleets.find { it.id == id && it.decode != null } }
                val hasPayload = device.facts.mfgRecords.isNotEmpty() ||
                    device.manufacturerDataHex.isNotBlank() ||
                    device.facts.serviceData.isNotEmpty()
                if (decoded.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        DecodeGlyph(
                            tint = MaterialTheme.colorScheme.primary,
                            size = 16.dp,
                        )
                        Text(
                            UiText.text(R.string.ui_decoded_fields),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    val multi = decoded.map { it.fleetId }.distinct().size > 1
                    decoded.forEach { row ->
                        val fleet = fleets.firstOrNull { it.id == row.fleetId }
                        val label = UiCatalogText.forFleet(fleet, row.label)
                        Meta(if (multi) "${UiCatalogText.forFleet(fleet, row.fleetName)} · $label" else label,
                            UiCatalogText.decoded(fleet, row))
                        if (row.note.isNotBlank()) {
                            Text(
                                UiCatalogText.forFleet(fleet, row.note),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (mapped.isNotEmpty()) {
                    val govee = mapped.any { it.id == "fleet-govee" }
                    Text(
                        when {
                            hasPayload && govee ->
                                UiText.text(R.string.ui_decode_fields_did_not_apply_to_this_advertisement_short_payload_a)
                            hasPayload ->
                                UiText.text(R.string.ui_decode_fields_did_not_apply_to_this_advertisement_short_payload_a_299)
                            govee ->
                                UiText.text(R.string.ui_this_signature_has_a_decode_map_but_this_advertisement_has_no_man)
                            else ->
                                UiText.text(R.string.ui_this_signature_has_a_decode_map_but_this_advertisement_has_no_man_301)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val mfg = facts.mfgRecords.ifEmpty {
                device.manufacturerId?.let {
                    listOf(app.fieldwatch.domain.MfgRecord(it, device.manufacturerDataHex))
                } ?: emptyList()
            }
            if (mfg.isNotEmpty()) {
                StickyHeight(device.key to "mfg") {
                    Section(UiText.text(R.string.ui_maker_data_inside_the_ad))
                    mfg.forEach { rec ->
                        val knownCompany = RadioDb.company(rec.companyId)
                        val company = knownCompany ?: UiText.text(R.string.ui_not_in_the_bluetooth_company_list)
                        Meta(
                            UiText.text(R.string.ui_bluetooth_company_0x_04x).format(rec.companyId),
                            if (knownCompany != null) UiText.text(R.string.ui_value_nthis_id_is_assigned_by_the_bluetooth_sig_and_is_carried_in, company)
                            else "$company\n" + UiText.explanation("Company identifier carried in manufacturer-specific data; the current list cannot confirm the assigning organization."),
                        )

                        if (rec.dataHex.isNotBlank()) {
                            Meta(UiText.text(R.string.ui_raw_payload_value_bytes, (rec.dataHex.length / 2).toString()), rec.dataHex.hexSpaced(), mono = true)
                        }
                    }
                }
            }

            if (facts.vendorIes.isNotEmpty() || device.vendorIeOuis.isNotEmpty()) {
                StickyHeight(device.key to "ies") {
                    Section(UiText.text(R.string.ui_wi_fi_vendor_tags))
                    val rows = facts.vendorIes.ifEmpty {
                        device.vendorIeOuis.map { app.fieldwatch.domain.VendorIeRecord(it, -1, "") }
                    }
                    rows.forEach { ie ->
                        val protocol = app.fieldwatch.domain.WifiWpsDecoder.protocolName(ie)
                        val org = protocol ?: RadioDb.vendorForOui24(ie.oui)
                        val type = if (ie.type >= 0) UiText.text(R.string.ui_type_d).format(ie.type) else ""
                        Meta(
                            UiText.text(R.string.ui_vendor_oui_valuevalue, (ie.oui).toString(), (type).toString()),
                            buildString {
                                append(org ?: UiText.text(R.string.ui_unknown_ieee_oui))
                                append(if (protocol != null) UiText.explanation(" — Wi-Fi protocol tag, not the AP manufacturer.")
                                    else UiText.text(R.string.ui_extra_ap_information_element_not_the_ssid))
                                if (ie.dataHex.isNotBlank()) {
                                    append("\n")
                                    append(ie.dataHex.hexSpaced())
                                }
                            },
                        )

                    }
                }
            }

            StickyHeight(device.key to "session") {
                Section(UiText.text(R.string.ui_session))
                Meta(UiText.text(R.string.ui_first_seen), fmt.format(Date(device.firstSeen)))
                Meta(UiText.text(R.string.ui_last_seen), fmt.format(Date(device.lastSeen)))
                Meta(UiText.text(R.string.ui_hits), device.hitCount.toString())
                Geo.screenCoord(device.latitude, device.longitude, demoMode)?.let { Meta(UiText.text(R.string.ui_last_fix), it) }
                if (device.fleetIds.isNotEmpty()) {
                    Meta(
                        UiText.text(R.string.ui_matched_signatures),
                        device.fleetIds.joinToString("\n") { id ->
                            val name = vm.fleetUiName(id)
                            if (vm.fleetHasDecode(id)) "$name  ⬡" else name
                        },
                    )
                }
                if (device.rawHex.isNotBlank() && device.kind == RadioKind.BLE) {
                    Meta(UiText.text(R.string.ui_raw_advertisement), device.rawHex.hexSpaced())
                }
            }

            Text(UiText.text(R.string.ui_signal_trend), style = MaterialTheme.typography.titleSmall)
            Sparkline(device.rssiHistory, accent, modifier = Modifier.fillMaxWidth().height(56.dp))
            Text(UiText.text(R.string.ui_presence_15_min), style = MaterialTheme.typography.titleSmall)
            PresenceTrack(device, System.currentTimeMillis(), 15 * 60 * 1000L, accent)
            if (device.kind == RadioKind.BLE) {
                FieldwatchActionButton(
                    onClick = onHunt,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.NearMe, null)
                    Spacer(Modifier.padding(4.dp))
                    Text(UiText.text(R.string.ui_hunt))
                }
            } else {
                Text(
                    UiText.text(R.string.ui_hunt_is_ble_only_wi_fi_access_points_update_too_slowly_on_stock_a),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            familyHint?.let { hint ->
                StickyHeight(device.key to "family") { FamilyCard(UiDetailText.family(hint)) }
            }
            FieldwatchActionButton(
                onClick = onCreateFleet,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.GroupAdd, null)
                Spacer(Modifier.padding(4.dp))
                Text(UiText.text(R.string.ui_create_signature_from_device))
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailShare(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.Share, null)
                Spacer(Modifier.padding(4.dp))
                Text(UiText.text(R.string.ui_share_as_text))
            }
            FieldwatchActionButton(
                onClick = { vm.startDeviceDetailAiExport(device) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.AutoAwesome, null)
                Spacer(Modifier.padding(4.dp))
                Text(UiText.text(R.string.ui_ai_export_15))
            }
            Text(
                UiText.text(R.string.ui_opens_a_paste_ready_prompt_for_a_chat_decode_this_radio_look_up_o),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FamilyCard(hint: SignatureFamilyHint) {
    val scheme = MaterialTheme.colorScheme
    val container = when (hint.verdict) {
        FamilyVerdict.STRONG -> scheme.primaryContainer
        FamilyVerdict.POSSIBLE -> Amber.nightIf(LocalNightMode.current).copy(alpha = 0.22f)
        FamilyVerdict.SINGLE, FamilyVerdict.TAGGED -> scheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val onContainer = when (hint.verdict) {
        FamilyVerdict.STRONG -> scheme.onPrimaryContainer
        FamilyVerdict.POSSIBLE, FamilyVerdict.SINGLE, FamilyVerdict.TAGGED -> scheme.onSurface
    }
    val muted = onContainer.copy(alpha = 0.78f)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = container,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        UiText.text(R.string.ui_signature_family),
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                    )
                    Text(hint.title, style = MaterialTheme.typography.titleMedium, color = onContainer)
                }
                if (hint.displayCount > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            hint.displayCount.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = onContainer,
                        )
                        RadioKindMark(hint.radioKind, size = 13.dp)
                    }
                }
            }
            hint.ruleLabel?.let { rule ->
                Text(
                    rule,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = scheme.primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(hint.body, style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}

@Composable
private fun SignatureNotesCard(notes: List<Pair<String, String>>) {
    if (notes.isEmpty()) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                UiText.text(R.string.ui_notes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            notes.forEach { (name, note) ->
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ObserverNotesCard(
    notes: String,
    canEdit: Boolean,
    editing: Boolean,
    draft: String,
    onToggleEdit: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean,
    saved: Boolean,
) {
    val ink = Cyan.nightIf(LocalNightMode.current)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ink.copy(alpha = 0.18f),
        border = BorderStroke(1.5.dp, ink),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    UiText.text(R.string.ui_observer_notes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ink,
                    modifier = Modifier.weight(1f),
                )
                if (canEdit) {
                    IconButton(onClick = onToggleEdit) {
                        Icon(
                            Icons.Outlined.Edit,
                            if (editing) UiText.text(R.string.ui_hide_observer_notes) else UiText.text(R.string.ui_observer_notes),
                        )
                    }
                }
            }
            if (!editing) {
                if (notes.isNotBlank()) {
                    Text(notes, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        UiText.text(R.string.ui_no_observer_notes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                FieldwatchOutlinedField(
                    value = draft,
                    onValueChange = onDraftChange,
                    label = UiText.text(R.string.ui_observer_notes),
                    singleLine = false,
                    minLines = 3,
                    supportingText = "${draft.trim().length}/${RadioBookmarks.MAX_NOTES}. ${UiText.text(R.string.observer_notes_hint)}",
                )
                FieldwatchActionButton(
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = saveEnabled,
                ) {
                    if (saved) {
                        Icon(Icons.Outlined.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.padding(4.dp))
                        Text(UiText.text(R.string.ui_saved))
                    } else {
                        Text(UiText.text(R.string.ui_save_notes))
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtraAttentionCard(notes: List<Pair<String, String>>) {
    if (notes.isEmpty()) return
    val warn = Amber.nightIf(LocalNightMode.current)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = warn.copy(alpha = 0.28f),
        border = BorderStroke(1.5.dp, warn),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = warn,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    UiText.text(R.string.ui_extra_attention),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = warn,
                )
            }
            notes.forEach { (name, note) ->
                Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                UiText.text(R.string.ui_pattern_match_not_identity_not_a_safety_finding),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuessCard(guess: DeviceExplain.Guess) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                UiText.text(R.string.ui_what_this_looks_like),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(guess.headline, style = MaterialTheme.typography.titleMedium)
            Text(guess.because, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Meta(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun vendorLine(device: Sighting): String? {
    val parts = ArrayList<String>(3)
    device.vendor?.let {
        parts += UiText.text(R.string.ui_ieee_board_chip_vendor_value_value_this_is_who_owns_the_mac_prefi, (it).toString(), (device.oui).toString())
    }
    val mfgId = device.facts.mfgRecords.firstOrNull()?.companyId ?: device.manufacturerId
    if (mfgId != null) {
        val company = RadioDb.company(mfgId)
        parts += UiText.text(R.string.ui_bluetooth_company_in_the_ad_value_0x_04x, (company ?: UiText.text(R.string.label_unlisted)).toString(), mfgId)
    }
    return parts.joinToString("\n").ifBlank { null }
}

private fun uuidShort(uuid: String): String {
    val hex = uuid.filter { it.isLetterOrDigit() }.uppercase()
    return if (hex.length >= 8 && hex.startsWith("0000")) hex.substring(4, 8) else uuid.take(8)
}

private fun serviceDataHeading(sd: ServiceDataRecord): String {
    val named = RadioDb.serviceUuid(sd.uuid)?.let { " (${UiText.explanation(it)})" } ?: ""
    val frame = eddystoneFrameTag(sd)?.let { " · $it" } ?: ""
    return UiText.text(R.string.ui_service_data_valuevaluevalue, (uuidShort(sd.uuid)).toString(), (named).toString(), (frame).toString())
}

private fun eddystoneFrameTag(sd: ServiceDataRecord): String? {
    val hex = sd.uuid.filter { it.isLetterOrDigit() }.uppercase()
    val short = when {
        hex.length == 4 -> hex
        hex.length >= 8 && hex.startsWith("0000") -> hex.substring(4, 8)
        else -> return null
    }
    if (short != "FEAA") return null
    return when (sd.dataHex.filter { it.isLetterOrDigit() }.uppercase().take(2)) {
        "00" -> "UID"
        "10" -> "URL"
        "20" -> "TLM"
        "30" -> "EID"
        else -> null
    }
}
