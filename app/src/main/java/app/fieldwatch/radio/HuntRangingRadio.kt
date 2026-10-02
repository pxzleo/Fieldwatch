package app.fieldwatch.radio

import android.Manifest
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.ranging.*
import android.ranging.ble.cs.*
import android.ranging.raw.*
import android.ranging.uwb.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import app.fieldwatch.domain.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID

fun huntRanging(context: Context, mac: String, probe: Boolean, config: HuntUwbConfig?) =
    if (Build.VERSION.SDK_INT < 36) flowOf(HuntRangeState(HuntRangeStatus.OLD_ANDROID))
    else ranging36(context, mac, probe, config)

@RequiresApi(36)
private fun ranging36(context: Context, mac: String, probe: Boolean, config: HuntUwbConfig?) = callbackFlow {
    val controller = HuntRangingController(context, mac, probe, config) { trySend(it) }
    controller.start()
    awaitClose { controller.close() }
}

/** One selected peer, foreground only. Never scans, pairs, or connects to other list entries. */
@RequiresApi(36)
private class HuntRangingController(private val context: Context, private val mac: String,
    private val probe: Boolean, private val uwb: HuntUwbConfig?, private val emit: (HuntRangeState) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private val manager = context.getSystemService(RangingManager::class.java)
    private var session: RangingSession? = null
    private var gatt: BluetoothGatt? = null
    private var capabilities: RangingCapabilities? = null
    private var registered = false
    private var receiverRegistered = false
    private var closed = false
    private var attempted = false
    private var finishing = false
    private var technology: HuntRangeTechnology? = null
    private var rasAvailable: Boolean? = null
    private val timeout = Runnable { fail(HuntRangeStatus.NO_DATA) }
    private fun publish(status: HuntRangeStatus, distance: Double? = null, at: Long = 0, reason: Int? = null) {
        if (!closed) emit(HuntRangeState(status, technology, distance, at,
            capabilities?.technologyAvailability?.get(RangingManager.BLE_CS),
            capabilities?.technologyAvailability?.get(RangingManager.UWB), reason, rasAvailable))
    }
    private fun deadline(ms: Long) { handler.removeCallbacks(timeout); handler.postDelayed(timeout, ms) }
    private val capabilityCallback = RangingManager.RangingCapabilitiesCallback { caps ->
        if (!closed && !finishing) {
            capabilities = caps
            if (!attempted) { attempted = true; begin(caps) }
            else if (technology != null && caps.technologyAvailability[if (technology == HuntRangeTechnology.CS)
                    RangingManager.BLE_CS else RangingManager.UWB] != RangingCapabilities.ENABLED)
                fail(HuntRangeStatus.PHONE_UNAVAILABLE)
        }
    }
    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RANGING) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            publish(HuntRangeStatus.PERMISSION); return
        }
        if (manager == null) { publish(HuntRangeStatus.PHONE_UNAVAILABLE); return }
        try {
            publish(HuntRangeStatus.CHECKING)
            manager.registerCapabilitiesCallback(context.mainExecutor, capabilityCallback)
            registered = true
            deadline(15_000)
        } catch (e: SecurityException) { Log.e("FieldwatchRanging", "Capabilities permission denied", e); fail(HuntRangeStatus.PERMISSION) }
        catch (e: IllegalStateException) { Log.e("FieldwatchRanging", "Capabilities unavailable", e); fail(HuntRangeStatus.FAILED) }
    }
    private fun begin(caps: RangingCapabilities) {
        handler.removeCallbacks(timeout)
        if (uwb != null && caps.technologyAvailability[RangingManager.UWB] == RangingCapabilities.ENABLED) {
            val supported = caps.uwbCapabilities
            if (uwb.targetMac != MacUtil.normalize(mac) || supported == null || !supported.isDistanceMeasurementSupported ||
                uwb.channel !in supported.supportedChannels || uwb.configId !in supported.supportedConfigIds ||
                uwb.preambleIndex !in supported.supportedPreambleIndexes || uwb.slotDuration !in supported.supportedSlotDurations) {
                fail(HuntRangeStatus.CONFIG_INVALID); return
            }
            technology = HuntRangeTechnology.UWB
            guarded {
                val params = UwbRangingParams.Builder(uwb.sessionId, uwb.configId,
                    UwbAddress.fromBytes(uwb.localAddress), UwbAddress.fromBytes(uwb.peerAddress))
                    .setComplexChannel(UwbComplexChannel.Builder().setChannel(uwb.channel)
                        .setPreambleIndex(uwb.preambleIndex).build())
                    .setSessionKeyInfo(uwb.sessionKey).setSlotDuration(uwb.slotDuration)
                    .setRangingUpdateRate(RawRangingDevice.UPDATE_RATE_FREQUENT).build()
                startSession(RawRangingDevice.Builder().setUwbRangingParams(params))
            }
            return
        }
        if (caps.technologyAvailability[RangingManager.BLE_CS] != RangingCapabilities.ENABLED || caps.csCapabilities == null) {
            fail(HuntRangeStatus.PHONE_UNAVAILABLE); return
        }
        technology = HuntRangeTechnology.CS
        guarded {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            val peer = adapter?.getRemoteDevice(mac)
            if (peer == null || !adapter.isEnabled) { fail(HuntRangeStatus.PHONE_UNAVAILABLE); return@guarded }
            if (!probe && peer.bondState != BluetoothDevice.BOND_BONDED) {
                publish(HuntRangeStatus.PEER_UNVERIFIED); return@guarded
            }
            publish(HuntRangeStatus.CONNECTING)
            deadline(30_000)
            gatt = peer.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) fail(HuntRangeStatus.FAILED)
        }
    }
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(connection: BluetoothGatt, status: Int, newState: Int) {
            handler.post {
                if (closed || finishing || connection != gatt) return@post
                guarded {
                    if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED)
                        fail(HuntRangeStatus.FAILED, status)
                    else if (newState == BluetoothProfile.STATE_CONNECTED && !connection.discoverServices())
                        fail(HuntRangeStatus.FAILED)
                }
            }
        }
        override fun onServicesDiscovered(connection: BluetoothGatt, status: Int) {
            handler.post {
                if (closed || finishing || connection != gatt) return@post
                guarded {
                    if (status != BluetoothGatt.GATT_SUCCESS) { fail(HuntRangeStatus.FAILED, status); return@guarded }
                    // RAS is an interoperability clue, not proof; only a successful CS result verifies ranging.
                    rasAvailable = connection.getService(UUID.fromString("0000185b-0000-1000-8000-00805f9b34fb")) != null
                    if (rasAvailable != true) {
                        fail(HuntRangeStatus.NO_SERVICE); return@guarded
                    }
                    if (connection.device.bondState == BluetoothDevice.BOND_BONDED) startCs()
                    else if (probe) {
                        publish(HuntRangeStatus.PAIRING)
                        ContextCompat.registerReceiver(context, bondReceiver,
                            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
                        receiverRegistered = true
                        deadline(90_000)
                        if (connection.device.bondState != BluetoothDevice.BOND_BONDING && !connection.device.createBond())
                            fail(HuntRangeStatus.FAILED)
                    } else fail(HuntRangeStatus.PEER_UNVERIFIED)
                }
            }
        }
    }
    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            @Suppress("DEPRECATION") val peer = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            if (closed || finishing) return
            guarded {
                if (peer?.address != mac) return@guarded
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                    BluetoothDevice.BOND_BONDED -> startCs()
                    BluetoothDevice.BOND_NONE -> fail(HuntRangeStatus.PEER_UNVERIFIED)
                }
            }
        }
    }
    private fun startCs() {
        if (session != null) return
        val security = capabilities?.csCapabilities?.supportedSecurityLevels?.minOrNull()
            ?: run { fail(HuntRangeStatus.PHONE_UNAVAILABLE); return }
        startSession(RawRangingDevice.Builder().setCsRangingParams(BleCsRangingParams.Builder(mac)
            .setSecurityLevel(security).setRangingUpdateRate(RawRangingDevice.UPDATE_RATE_FREQUENT).build()))
    }
    private fun startSession(builder: RawRangingDevice.Builder) {
        val peer = RangingDevice.Builder().setUuid(UUID.randomUUID()).build()
        val config = RawInitiatorRangingConfig.Builder().addRawRangingDevice(builder.setRangingDevice(peer).build()).build()
        session = manager!!.createRangingSession(context.mainExecutor, object : RangingSession.Callback {
            override fun onOpened() = Unit
            override fun onStarted(device: RangingDevice, rangingTechnology: Int) { if (!closed && !finishing) publish(HuntRangeStatus.STARTING) }
            override fun onOpenFailed(reason: Int) { if (!closed && !finishing) fail(HuntRangeStatus.FAILED, reason) }
            override fun onClosed(reason: Int) { if (!closed && !finishing) fail(HuntRangeStatus.STOPPED, reason) }
            override fun onStopped(device: RangingDevice, rangingTechnology: Int) {
                if (!closed && !finishing) fail(HuntRangeStatus.STOPPED)
            }
            override fun onResults(device: RangingDevice, data: RangingData) {
                if (closed || finishing || device != peer || data.rangingTechnology !=
                    if (technology == HuntRangeTechnology.CS) RangingManager.BLE_CS else RangingManager.UWB) return
                val age = SystemClock.elapsedRealtime() - data.timestampMillis
                val measurement = data.distance ?: return
                val distance = measurement.measurement
                if (age !in 0..3_000L || !distance.isFinite() || distance < 0) return
                deadline(10_000)
                if (measurement.confidence == RangingMeasurement.CONFIDENCE_LOW) publish(HuntRangeStatus.LOW_QUALITY)
                else publish(HuntRangeStatus.ACTIVE, distance, System.currentTimeMillis() - age)
            }
        })
        publish(HuntRangeStatus.STARTING)
        deadline(30_000)
        session!!.start(RangingPreference.Builder(RangingPreference.DEVICE_ROLE_INITIATOR, config).build())
    }
    private inline fun guarded(action: () -> Unit) {
        try { action() }
        catch (e: SecurityException) { Log.e("FieldwatchRanging", "Ranging permission denied", e); fail(HuntRangeStatus.PERMISSION) }
        catch (e: IllegalArgumentException) { Log.e("FieldwatchRanging", "Invalid ranging parameters (${e.javaClass.simpleName})"); fail(HuntRangeStatus.CONFIG_INVALID) }
        catch (e: IllegalStateException) { Log.e("FieldwatchRanging", "Ranging operation failed", e); fail(HuntRangeStatus.FAILED) }
    }
    private fun fail(status: HuntRangeStatus, reason: Int? = null) {
        if (closed || finishing) return
        finishing = true
        publish(status, reason = reason)
        release()
    }
    private fun release() {
        handler.removeCallbacks(timeout)
        val oldSession = session; session = null
        try { oldSession?.close() }
        catch (e: SecurityException) { Log.e("FieldwatchRanging", "Ranging permission revoked on close", e) }
        catch (e: IllegalStateException) { Log.e("FieldwatchRanging", "Ranging close failed", e) }
        val oldGatt = gatt; gatt = null
        try { oldGatt?.close() }
        catch (e: SecurityException) { Log.e("FieldwatchRanging", "Bluetooth permission revoked on close", e) }
        if (receiverRegistered) { context.unregisterReceiver(bondReceiver); receiverRegistered = false }
        if (registered) {
            try { manager?.unregisterCapabilitiesCallback(capabilityCallback) }
            catch (e: SecurityException) { Log.e("FieldwatchRanging", "Ranging permission revoked during cleanup", e) }
            catch (e: IllegalStateException) { Log.e("FieldwatchRanging", "Capabilities cleanup failed", e) }
            registered = false
        }
    }
    fun close() {
        closed = true
        release()
    }
}
