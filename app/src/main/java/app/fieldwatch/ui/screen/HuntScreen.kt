package app.fieldwatch.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.fieldwatch.domain.MacUtil
import app.fieldwatch.ui.FieldwatchViewModel
import app.fieldwatch.ui.component.FieldwatchSwitch
import app.fieldwatch.ui.component.Sparkline
import app.fieldwatch.ui.uiHint
import app.fieldwatch.ui.uiLabel
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HuntScreen(vm: FieldwatchViewModel, onBack: () -> Unit, demoMode: Boolean = false,
    huntBeep: Boolean = false, huntVibrate: Boolean = false) {
    val hunt by vm.hunt.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var testSent by remember { mutableStateOf(false) }
    LaunchedEffect(testSent) { if (testSent) { delay(2_000); testSent = false } }
    LaunchedEffect(hunt.active, huntBeep, huntVibrate, lifecycle) {
        if (!hunt.active || (!huntBeep && !huntVibrate)) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var lastTick = 0L
            while (true) {
                val live = vm.hunt.value
                val freshSignal = live.signal?.takeIf { System.currentTimeMillis() - live.lastSeen <= live.windowMs }
                val interval = Hunt.tickIntervalMs(freshSignal?.roundToInt(), live.cue)
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
            actions = { TextButton(onClick = vm::resetHunt) { Text(UiText.text(R.string.hunt_restart)) } })
    }, bottomBar = {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(UiText.text(R.string.ui_beep), Modifier.weight(1f))
                FieldwatchSwitch(huntBeep, { vm.updateSettings { s -> s.copy(huntBeep = it) } })
                Text(UiText.text(R.string.ui_vibrate), Modifier.weight(1f))
                FieldwatchSwitch(huntVibrate, { vm.updateSettings { s -> s.copy(huntVibrate = it) } })
            }
            TextButton(onClick = { vm.huntTick(true, true); testSent = true }, modifier = Modifier.fillMaxWidth()) {
                Text(UiText.text(if (testSent) R.string.hunt_test_sent else R.string.hunt_test_feedback))
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(hunt.device?.let { MacUtil.redactMacIn(hunt.title, it.mac, demoMode) } ?: hunt.title,
                style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(UiText.text(R.string.hunt_relative_only), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            Box(Modifier.fillMaxWidth().height(148.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(136.dp)) {
                    val stroke = 8.dp.toPx()
                    drawArc(scheme.surfaceVariant, 135f, 270f, false, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    if (hunt.signal != null) drawArc(accent, 135f, 270f * strength, false, style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(hunt.signal?.roundToInt()?.toString() ?: "—", fontSize = 44.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text(UiText.text(R.string.hunt_smoothed_signal), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                }
            }
            AnimatedContent(hunt.cue, label = "huntTrend") { cue ->
                Text(cue.uiLabel(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accent)
            }
            Text(if (hunt.lastSeen > 0 && hunt.count == 0) UiText.text(R.string.hunt_no_recent_packet) else hunt.cue.uiHint(),
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = scheme.onSurfaceVariant)
            Text(UiText.text(R.string.hunt_sample_quality, hunt.count, hunt.noise.roundToInt(),
                if (hunt.lastSeen > 0) ((hunt.now - hunt.lastSeen) / 1000).coerceAtLeast(0).toString() else "—"),
                style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Sparkline(hunt.samples, accent, Modifier.fillMaxWidth().height(42.dp))
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

@Composable
private fun HuntPointValue(label: String, point: HuntPoint?, modifier: Modifier) {
    Column(modifier) {
        Text(UiText.text(R.string.hunt_position, label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(point?.signal?.let { "%.1f dBm".format(Locale.US, it) } ?: "—", style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
        Text(UiText.text(when { point == null -> R.string.hunt_not_sampled; point.sufficient -> R.string.hunt_sample_saved; else -> R.string.hunt_sample_retry }),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
