package app.fieldwatch.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import app.fieldwatch.R
import app.fieldwatch.UiText
import app.fieldwatch.data.HuntPoint
import app.fieldwatch.domain.Hunt
import app.fieldwatch.domain.HuntCue
import app.fieldwatch.domain.HuntHeading
import app.fieldwatch.radio.huntHeadings
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.FieldwatchSwitch
import app.fieldwatch.ui.component.Sparkline
import app.fieldwatch.ui.uiHint
import app.fieldwatch.ui.uiLabel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.fieldwatch.data.HuntGpsState
import app.fieldwatch.domain.HuntLocator
import app.fieldwatch.domain.HuntPosition
import app.fieldwatch.domain.HuntPositionStatus
import app.fieldwatch.radio.huntGps
import app.fieldwatch.ui.component.HuntMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import app.fieldwatch.domain.HuntRangeState
import app.fieldwatch.domain.HuntRangeStatus
import app.fieldwatch.domain.HuntUwbConfig
import app.fieldwatch.domain.UwbConfigException
import app.fieldwatch.radio.huntRanging
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuntScreen(vm: FieldwatchViewModel, onBack: () -> Unit, demoMode: Boolean = false,
    huntBeep: Boolean = false, huntVibrate: Boolean = false) {
    val hunt by vm.hunt.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val epoch = hunt.session.startedAt
    val target = hunt.session.key?.removePrefix("BLE:")
    var rangeRevision by remember(epoch) { mutableIntStateOf(0) }
    var probe by remember(epoch) { mutableStateOf(false) }
    var uwbConfig by remember(epoch) { mutableStateOf<HuntUwbConfig?>(null) }
    var configError by remember(epoch) { mutableStateOf<String?>(null) }
    var importTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val rangePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { rangeRevision++ }
    val importConfig = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val requested = importTarget
        importTarget = null
        if (uri != null && requested != null) scope.launch {
            try {
                val config = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readNBytes(65_537) }
                        ?: throw java.io.IOException("Cannot open file")
                    if (bytes.size > 65_536) throw UwbConfigException("File too large")
                    HuntUwbConfig.parse(bytes.toString(Charsets.UTF_8), requested.second)
                }
                if (vm.hunt.value.session.startedAt == requested.first && vm.hunt.value.session.key == "BLE:${requested.second}") {
                    uwbConfig = config; configError = null; rangeRevision++
                }
            } catch (e: UwbConfigException) { configError = e.message }
            catch (e: java.io.IOException) { configError = e.message }
            catch (e: SecurityException) { configError = e.message }
        }
    }
    val realDistance = hunt.session.ranging.freshDistance(hunt.now)
    val rangeLabel = if (hunt.session.ranging.status == HuntRangeStatus.ACTIVE && realDistance == null)
        R.string.hunt_range_stale else hunt.session.ranging.status.label()
    LaunchedEffect(hunt.active, epoch, target, rangeRevision, uwbConfig, lifecycle) {
        if (!hunt.active || target == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try { huntRanging(context, target, probe, uwbConfig).collect { vm.updateHuntRanging("BLE:$target", epoch, it) } }
            finally { vm.updateHuntRanging("BLE:$target", epoch, HuntRangeState(HuntRangeStatus.STOPPED)) }
        }
    }
    var gpsPermissionRevision by remember { mutableIntStateOf(0) }
    var mapOpen by remember { mutableStateOf(false) }
    var mapDetails by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { gpsPermissionRevision++ }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val points = HuntLocator.recent(hunt.session.geoPoints, hunt.now)
    val estimation by produceState(0L to HuntPosition(HuntPositionStatus.MORE_POINTS),
        hunt.session.startedAt, hunt.now / 3_000L, points.size) {
        value = hunt.session.startedAt to withContext(Dispatchers.Default) { HuntLocator.estimate(points) { ensureActive() } }
    }
    val position = estimation.second.takeIf { estimation.first == hunt.session.startedAt }
        ?: HuntPosition(HuntPositionStatus.MORE_POINTS)
    val currentFix = hunt.session.fixes.lastOrNull()?.takeIf { hunt.now - it.at in 0..3_000L }
    val gpsState = if (hunt.session.locationState == HuntGpsState.READY && currentFix == null)
        HuntGpsState.WAITING else hunt.session.locationState
    LaunchedEffect(hunt.active, lifecycle, context, gpsPermissionRevision) {
        if (!hunt.active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try { huntGps(context).collect { vm.updateHuntLocation(it) } }
            finally { vm.updateHuntLocation(app.fieldwatch.radio.HuntGpsUpdate(null, HuntGpsState.WAITING)) }
        }
    }
    if (mapOpen) Dialog(onDismissRequest = { mapOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(topBar = { TopAppBar(title = { Text(UiText.text(R.string.hunt_map_title)) },
            navigationIcon = { IconButton(onClick = { mapOpen = false }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, UiText.text(R.string.ui_back)) } }) }) { mapPadding ->
            Column(Modifier.padding(mapPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(UiText.text(gpsState.label()), style = MaterialTheme.typography.bodyMedium)
                if (gpsState == HuntGpsState.DENIED) Button(onClick = {
                    permission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)
                }) { Text(UiText.text(R.string.hunt_gps_permission)) }
                Text(UiText.text(position.status.label()), style = MaterialTheme.typography.titleMedium)
                if (!demoMode) HuntMap(points, currentFix, position, ui.settings.onlineLookup,
                    heading = hunt.session.heading?.takeIf { it.fresh(hunt.now) })
                else Text(UiText.text(R.string.hunt_map_private))
                position.center?.takeUnless { demoMode }?.let {
                    Text(UiText.text(R.string.hunt_position_estimate, "%.5f, %.5f".format(Locale.US, it.lat, it.lon),
                        position.radius.roundToInt()), style = MaterialTheme.typography.bodyMedium)
                }
                TextButton(onClick = { mapDetails = !mapDetails }) { Text(UiText.text(R.string.hunt_map_details)) }
                if (mapDetails) {
                    Text(UiText.text(R.string.hunt_range_status, UiText.text(rangeLabel)),
                        style = MaterialTheme.typography.bodyMedium)
                    hunt.session.ranging.reason?.let { Text(UiText.text(R.string.hunt_range_reason, it)) }
                    if (android.os.Build.VERSION.SDK_INT >= 36) {
                        Button(onClick = {
                            probe = true
                            rangePermission.launch(arrayOf(android.Manifest.permission.RANGING, android.Manifest.permission.BLUETOOTH_CONNECT))
                        }) { Text(UiText.text(R.string.hunt_range_connect)) }
                        TextButton(onClick = {
                            target?.let { importTarget = epoch to it; importConfig.launch(arrayOf("application/json", "text/plain")) }
                        }) {
                            Text(UiText.text(R.string.hunt_uwb_import))
                        }
                        if (uwbConfig != null) TextButton(onClick = { uwbConfig = null; rangeRevision++ }) {
                            Text(UiText.text(R.string.hunt_uwb_clear))
                        }
                        configError?.let { Text(UiText.text(R.string.hunt_uwb_error, it), color = MaterialTheme.colorScheme.error) }
                        Text(UiText.text(R.string.hunt_range_help), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(UiText.text(R.string.hunt_location_method), style = MaterialTheme.typography.bodySmall)
                    Text(UiText.text(R.string.hunt_target_connectable, UiText.text(when (hunt.device?.facts?.connectable) {
                        true -> R.string.hunt_cap_yes; false -> R.string.hunt_cap_no; else -> R.string.hunt_cap_unknown
                    })), style = MaterialTheme.typography.bodySmall)
                    Text(UiText.text(R.string.hunt_phone_ranging,
                        UiText.text(availabilityLabel(hunt.session.ranging.csAvailability)),
                        UiText.text(availabilityLabel(hunt.session.ranging.uwbAvailability))), style = MaterialTheme.typography.bodySmall)
                    Text(UiText.text(R.string.hunt_location_limits), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    LaunchedEffect(hunt.active, lifecycle, context) {
        if (!hunt.active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                huntHeadings(context).collect { vm.updateHuntHeading(it) }
            } finally {
                vm.updateHuntHeading(null)
            }
        }
    }
    LaunchedEffect(hunt.active, huntBeep, huntVibrate, lifecycle) {
        if (!hunt.active || (!huntBeep && !huntVibrate)) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastTick = 0L
            while (true) {
                val live = vm.hunt.value
                val freshSignal = live.signal?.takeIf { System.currentTimeMillis() - live.lastSeen <= live.windowMs }
                val distance = live.session.ranging.freshDistance(System.currentTimeMillis())
                val interval = distance?.let { (90 + 1310 * (it / 20).coerceIn(0.0, 1.0)).toLong() }
                    ?: Hunt.tickIntervalMs(freshSignal?.roundToInt(), live.cue)
                val now = System.currentTimeMillis()
                if (interval != null && now - lastTick >= interval) {
                    vm.huntTick(huntBeep, huntVibrate)
                    lastTick = now
                }
                val remaining = interval?.minus(System.currentTimeMillis() - lastTick)
                delay(remaining?.coerceIn(1L, 80L) ?: 80L)
            }
        }
    }
    val scheme = MaterialTheme.colorScheme
    val accent by animateColorAsState(if (hunt.cue == HuntCue.FURTHER) scheme.error else scheme.primary, label = "huntAccent")
    val strength by animateFloatAsState(((hunt.signal ?: -100.0).toFloat() + 100f).div(60f).coerceIn(0f, 1f), label = "huntSignal")
    val capturing = hunt.session.captureStartedAt > 0
    val captureProgress = if (capturing) ((hunt.now - hunt.session.captureStartedAt) / hunt.session.captureDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val difference = hunt.session.difference
    val comparison = when {
        hunt.session.pointB == null -> R.string.hunt_compare_instruction
        difference == null -> R.string.hunt_compare_insufficient
        difference >= hunt.session.comparisonThreshold -> R.string.hunt_b_stronger
        difference <= -hunt.session.comparisonThreshold -> R.string.hunt_a_stronger
        else -> R.string.hunt_compare_same
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(UiText.text(R.string.ui_hunt), fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, UiText.text(R.string.ui_back)) } },
            actions = {
                TextButton(onClick = { mapOpen = true }) { Text(UiText.text(R.string.hunt_map_short, points.size)) }
                TextButton(onClick = vm::resetHunt) { Text(UiText.text(R.string.hunt_restart)) }
            })
    }, bottomBar = {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(UiText.text(R.string.ui_beep), Modifier.weight(1f))
                FieldwatchSwitch(huntBeep, { vm.updateSettings { s -> s.copy(huntBeep = it) } })
                Text(UiText.text(R.string.ui_vibrate), Modifier.weight(1f))
                FieldwatchSwitch(huntVibrate, { vm.updateSettings { s -> s.copy(huntVibrate = it) } })
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(hunt.device?.let { MacUtil.redactMacIn(hunt.title, it.mac, demoMode) } ?: hunt.title,
                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            val heading = hunt.session.heading?.takeIf { it.fresh(hunt.now) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeadingArrow(heading, Modifier.size(56.dp))
                Column {
                    Text(UiText.text(R.string.hunt_phone_heading, headingText(heading)),
                        style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                    Text(UiText.text(R.string.hunt_heading_arrow_reference),
                        style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
            Box(Modifier.fillMaxWidth().height(124.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(112.dp)) {
                    val stroke = 8.dp.toPx()
                    drawArc(scheme.surfaceVariant, 135f, 270f, false, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    if (realDistance != null || hunt.signal != null) drawArc(accent, 135f, 270f * (realDistance?.let { (1 - it / 20).coerceIn(0.0, 1.0).toFloat() } ?: strength), false, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(realDistance?.let { "%.2f".format(Locale.US, it) } ?: hunt.signal?.roundToInt()?.toString() ?: "—", fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(if (realDistance != null) UiText.text(R.string.hunt_real_distance, hunt.session.ranging.technology?.name ?: "") else UiText.text(R.string.hunt_smoothed_signal), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
            if (realDistance == null) AnimatedContent(hunt.cue, label = "huntTrend") { cue ->
                Text(cue.uiLabel(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
            }
            Text(if (realDistance != null) UiText.text(R.string.hunt_range_live) else if (hunt.lastSeen > 0 && hunt.count == 0) UiText.text(R.string.hunt_no_recent_packet) else hunt.cue.uiHint(),
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = scheme.onSurfaceVariant)
            Text(UiText.text(R.string.hunt_sample_quality, hunt.count, hunt.noise.roundToInt(),
                if (hunt.lastSeen > 0) ((hunt.now - hunt.lastSeen) / 1000).coerceAtLeast(0).toString() else "—"),
                style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Text(UiText.text(rangeLabel), style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Sparkline(hunt.samples, accent, Modifier.fillMaxWidth().height(28.dp))
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text(UiText.text(R.string.hunt_indoor_compare), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(UiText.text(R.string.hunt_indoor_instruction), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HuntPointValue("A", hunt.session.pointA, Modifier.weight(1f))
                HuntPointValue("B", hunt.session.pointB, Modifier.weight(1f))
            }
            Text(UiText.text(comparison) + (difference?.let { "  %+.1f dB".format(Locale.US, it) } ?: ""),
                Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = accent)
            if (capturing) {
                LinearProgressIndicator(progress = { captureProgress }, modifier = Modifier.fillMaxWidth())
                Text(UiText.text(R.string.hunt_capture_hold, "%.1f".format(Locale.US, hunt.session.captureDurationMs / 1000.0)), style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = vm::captureHuntPoint, enabled = hunt.active && !capturing,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(UiText.text(when {
                    capturing -> R.string.hunt_capturing
                    hunt.session.pointA?.sufficient != true -> R.string.hunt_capture_a
                    else -> R.string.hunt_capture_b
                }))
            }
            if (difference != null && difference >= hunt.session.comparisonThreshold && !capturing) {
                TextButton(onClick = vm::keepHuntB) { Text(UiText.text(R.string.hunt_keep_b)) }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

private fun HuntRangeStatus.label(): Int = when (this) {
    HuntRangeStatus.CHECKING -> R.string.hunt_range_checking
    HuntRangeStatus.OLD_ANDROID -> R.string.hunt_range_old_android
    HuntRangeStatus.PERMISSION -> R.string.hunt_range_permission
    HuntRangeStatus.PHONE_UNAVAILABLE -> R.string.hunt_range_phone_unavailable
    HuntRangeStatus.PEER_UNVERIFIED -> R.string.hunt_range_peer_unverified
    HuntRangeStatus.CONNECTING -> R.string.hunt_range_connecting
    HuntRangeStatus.PAIRING -> R.string.hunt_range_pairing
    HuntRangeStatus.NO_SERVICE -> R.string.hunt_range_no_service
    HuntRangeStatus.STARTING -> R.string.hunt_range_starting
    HuntRangeStatus.ACTIVE -> R.string.hunt_range_active
    HuntRangeStatus.LOW_QUALITY -> R.string.hunt_range_low_quality
    HuntRangeStatus.NO_DATA -> R.string.hunt_range_no_data
    HuntRangeStatus.FAILED -> R.string.hunt_range_failed
    HuntRangeStatus.STOPPED -> R.string.hunt_range_stopped
    HuntRangeStatus.CONFIG_INVALID -> R.string.hunt_range_config_invalid
}
private fun availabilityLabel(value: Int?): Int = when (value) {
    3 -> R.string.hunt_cap_yes
    null -> R.string.hunt_cap_unknown
    else -> R.string.hunt_cap_no
}

private fun HuntGpsState.label(): Int = when (this) {
    HuntGpsState.WAITING -> R.string.hunt_gps_waiting
    HuntGpsState.DENIED -> R.string.hunt_gps_denied
    HuntGpsState.DISABLED -> R.string.hunt_gps_disabled
    HuntGpsState.POOR -> R.string.hunt_gps_poor
    HuntGpsState.READY -> R.string.hunt_gps_ready
    HuntGpsState.ERROR -> R.string.hunt_gps_error
}
private fun HuntPositionStatus.label(): Int = when (this) {
    HuntPositionStatus.MORE_POINTS -> R.string.hunt_location_more
    HuntPositionStatus.WIDER_BASELINE -> R.string.hunt_location_wider
    HuntPositionStatus.SIDEWAYS -> R.string.hunt_location_sideways
    HuntPositionStatus.WEAK_CONTRAST -> R.string.hunt_location_contrast
    HuntPositionStatus.INCONSISTENT -> R.string.hunt_location_inconsistent
    HuntPositionStatus.EDGE -> R.string.hunt_location_edge
    HuntPositionStatus.ESTIMATED -> R.string.hunt_location_estimated
}

@Composable
private fun HuntPointValue(label: String, point: HuntPoint?, modifier: Modifier) {
    Column(modifier) {
        Text(UiText.text(R.string.hunt_position, label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(point?.signal?.let { "%.1f dBm".format(Locale.US, it) } ?: "—", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
        if (point != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HeadingArrow(point.heading, Modifier.size(28.dp))
            Text(UiText.text(R.string.hunt_sample_heading, headingText(point.heading)), Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(UiText.text(when { point == null -> R.string.hunt_not_sampled; point.sufficient -> R.string.hunt_sample_saved; else -> R.string.hunt_sample_retry }),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeadingArrow(heading: HuntHeading?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    var previous by remember { mutableIntStateOf(heading?.degrees ?: 0) }
    var target by remember { mutableFloatStateOf(previous.toFloat()) }
    LaunchedEffect(heading?.degrees) {
        heading?.let {
            // Cross north along the shortest arc instead of spinning almost a full turn.
            target += ((it.degrees - previous + 540) % 360 - 180)
            previous = it.degrees
        }
    }
    val rotation by animateFloatAsState(target, animationSpec = tween(180), label = "phoneHeading")
    val tint = if (heading?.reliable == true) scheme.primary else scheme.onSurfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension * .44f
            drawCircle(scheme.outlineVariant, radius, style = Stroke(1.dp.toPx()))
            repeat(8) { i ->
                val angle = Math.toRadians(i * 45.0 - 90)
                val outer = Offset(center.x + cos(angle).toFloat() * radius, center.y + sin(angle).toFloat() * radius)
                val inner = Offset(center.x + cos(angle).toFloat() * radius * .8f, center.y + sin(angle).toFloat() * radius * .8f)
                drawLine(if (i == 0) scheme.primary else scheme.outlineVariant, inner, outer, 2.dp.toPx())
            }
        }
        if (heading != null) Icon(Icons.Filled.Navigation,
            contentDescription = UiText.text(R.string.hunt_heading_arrow_description, headingText(heading)),
            tint = tint, modifier = Modifier.fillMaxSize(.62f).rotate(rotation))
        else Text("—", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

private fun headingText(heading: HuntHeading?): String {
    if (heading == null) return UiText.text(R.string.hunt_heading_unavailable)
    val directions = intArrayOf(R.string.hunt_north, R.string.hunt_northeast, R.string.hunt_east,
        R.string.hunt_southeast, R.string.hunt_south, R.string.hunt_southwest, R.string.hunt_west, R.string.hunt_northwest)
    return UiText.text(R.string.hunt_heading_value, heading.degrees, UiText.text(directions[heading.directionIndex])) +
        if (heading.reliable) "" else " · " + UiText.text(R.string.hunt_heading_low_accuracy)
}
