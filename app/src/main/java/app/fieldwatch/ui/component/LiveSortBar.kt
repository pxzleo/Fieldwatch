package app.fieldwatch.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fieldwatch.R
import app.fieldwatch.UiText
import app.fieldwatch.domain.AppSettings
import app.fieldwatch.domain.ListSort
import app.fieldwatch.domain.StrengthSort
import app.fieldwatch.domain.ViewMode
import app.fieldwatch.ui.uiLabel

@Composable
fun LiveSortBar(settings: AppSettings, onSort: (ListSort, StrengthSort?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Box {
            TextButton(onClick = { expanded = true }) {
                val scope = if (settings.viewMode == ViewMode.BY_CLASS) R.string.sort_within_groups else R.string.ui_sort
                Text("${UiText.text(scope)} · ${settings.listSort.uiLabel(settings.strengthSort, settings.averageWindowSec)} ▾",
                    style = MaterialTheme.typography.labelLarge)
            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                listOf(StrengthSort.INSTANT, StrengthSort.AVERAGE).forEach { strength ->
                    DropdownMenuItem(text = { Text(ListSort.STRENGTH.uiLabel(strength, settings.averageWindowSec)) },
                        onClick = { onSort(ListSort.STRENGTH, strength); expanded = false })
                }
                listOf(ListSort.WEAKEST, ListSort.WIFI_FIRST, ListSort.BLE_FIRST, ListSort.SIGNAL_TYPE, ListSort.DEVICE_TYPE,
                    ListSort.NEWEST, ListSort.NAME, ListSort.SIGNATURES).forEach { sort ->
                    DropdownMenuItem(text = { Text(sort.uiLabel(settings.strengthSort, settings.averageWindowSec)) },
                        onClick = { onSort(sort, null); expanded = false })
                }
            }
        }
    }
}
