package app.fieldwatch.radio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import app.fieldwatch.domain.HuntHeading
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Only collected while the Hunt screen is resumed. No GPS or location permission. */
fun huntHeadings(context: Context) = callbackFlow<HuntHeading?> {
    val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    val matrix = FloatArray(9)
    val screenMatrix = FloatArray(9)
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            @Suppress("DEPRECATION")
            val rotation = if (Build.VERSION.SDK_INT >= 30) context.display?.rotation else
                (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
            val (x, y) = when (rotation) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            }
            if (!SensorManager.remapCoordinateSystem(matrix, x, y, screenMatrix)) {
                Log.e("FieldwatchHeading", "Unable to remap phone heading")
                trySend(null)
                return
            }
            val at = System.currentTimeMillis() - ((SystemClock.elapsedRealtimeNanos() - event.timestamp) / 1_000_000L).coerceAtLeast(0L)
            trySend(HuntHeading.fromAxes(screenMatrix[1].toDouble(), screenMatrix[4].toDouble(), at,
                event.accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM))
        }
        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }
    trySend(null)
    if (sensor == null || !manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
        Log.w("FieldwatchHeading", "Phone heading sensor unavailable or registration failed")
    }
    awaitClose { manager.unregisterListener(listener) }
}.conflate()
