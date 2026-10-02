package app.fieldwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import app.fieldwatch.alert.Alerter
import app.fieldwatch.radio.RadioPermissions
import app.fieldwatch.ui.FieldwatchRoot
import app.fieldwatch.ui.FieldwatchViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: android.content.Context) {
        val language = (base.applicationContext as FieldwatchApp).config.settings.language
        super.attachBaseContext(
            if (android.os.Build.VERSION.SDK_INT >= 33 || language == AppLanguage.SYSTEM) base
            else AppLanguages.localizedContext(base, language),
        )
    }
    private val vm: FieldwatchViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        vm.refreshPermissions()
        if (RadioPermissions.granted(this)) {
            (application as FieldwatchApp).refreshFix()
            vm.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT < 33) {
            val initial = (application as FieldwatchApp).config.settings.language
            lifecycleScope.launch {
                (application as FieldwatchApp).config.config
                    .map { it.settings.language }.distinctUntilChanged().collect { language ->
                        if (language != initial) recreate()
                    }
            }
        }
        setContent {
            FieldwatchRoot(vm) {
                permissionLauncher.launch(RadioPermissions.required())
            }
        }
        if (RadioPermissions.granted(this)) {
            lifecycleScope.launch { vm.startScan() }
        }
        intent?.getStringExtra(Alerter.EXTRA_DEVICE_KEY)?.let { vm.select(it) }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermissions()
        if (RadioPermissions.granted(this) && !(application as FieldwatchApp).devices.stats.value.scanning) {
            vm.startScan()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(Alerter.EXTRA_DEVICE_KEY)?.let { vm.select(it) }
    }
}
