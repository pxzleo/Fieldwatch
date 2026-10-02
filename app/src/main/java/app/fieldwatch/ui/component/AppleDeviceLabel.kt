package app.fieldwatch.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.fieldwatch.UiText
import app.fieldwatch.domain.AdvPayloadDecoder
import app.fieldwatch.domain.Sighting

@Composable
fun AppleDeviceLabel(device: Sighting) {
    val hint = AdvPayloadDecoder.appleDeviceHint(device, UiText::explanation) ?: return
    Text("${UiText.explanation("Apple device type")}: ${hint.label}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
