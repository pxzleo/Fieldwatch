package app.fieldwatch.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.devicePurpose
import app.fieldwatch.domain.signalType
import app.fieldwatch.ui.uiLabel

@Composable
fun SignalTypeLabel(device: Sighting, signatureNames: List<String>) {
    val purpose = remember(device.kind, device.facts, device.manufacturerId, device.manufacturerDataHex,
        device.serviceUuids, device.name, signatureNames) { device.devicePurpose(signatureNames) }
    Text("${device.signalType().uiLabel()} · ${purpose.uiLabel()}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
