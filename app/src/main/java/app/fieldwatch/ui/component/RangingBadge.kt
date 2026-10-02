package app.fieldwatch.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import app.fieldwatch.R
import app.fieldwatch.UiText
import app.fieldwatch.domain.HuntRangeBadge
import app.fieldwatch.domain.Sighting
import app.fieldwatch.domain.rangingBadge
import app.fieldwatch.ui.theme.Phosphor

@Composable
fun RangingBadge(device: Sighting) {
    val label = when (device.rangingBadge() ?: return) {
        HuntRangeBadge.CS_VERIFIED -> R.string.ranging_badge_cs_verified
        HuntRangeBadge.UWB_VERIFIED -> R.string.ranging_badge_uwb_verified
        HuntRangeBadge.CS_SERVICE -> R.string.ranging_badge_cs_service
    }
    Text(UiText.text(label), style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = if (MaterialTheme.colorScheme.surface.luminance() > .5f) Color(0xFF0B7A48) else Phosphor)
}
