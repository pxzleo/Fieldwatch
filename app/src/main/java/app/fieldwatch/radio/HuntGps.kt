package app.fieldwatch.radio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import app.fieldwatch.data.HuntGpsState
import app.fieldwatch.domain.HuntFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class HuntGpsUpdate(val fix: HuntFix?, val status: HuntGpsState)

/** Separate foreground subscription: does not change the user's normal location-tagging setting. */
fun huntGps(context: Context) = callbackFlow<HuntGpsUpdate> {
    val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val age = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
            val fix = HuntFix(location.latitude, location.longitude,
                if (location.hasAccuracy()) location.accuracy.toDouble() else Double.POSITIVE_INFINITY,
                System.currentTimeMillis() - age)
            trySend(HuntGpsUpdate(fix.takeIf { age in 0..3_000 && it.usable() },
                if (age in 0..3_000 && fix.usable()) HuntGpsState.READY else HuntGpsState.POOR))
        }
        override fun onProviderDisabled(provider: String) { trySend(HuntGpsUpdate(null, HuntGpsState.DISABLED)) }
        override fun onProviderEnabled(provider: String) { trySend(HuntGpsUpdate(null, HuntGpsState.WAITING)) }
        @Deprecated("Legacy Android callback")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        trySend(HuntGpsUpdate(null, HuntGpsState.DENIED))
    } else {
        try {
            trySend(HuntGpsUpdate(null, if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                HuntGpsState.WAITING else HuntGpsState.DISABLED))
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("FieldwatchHuntGps", "Location permission unavailable", e)
            trySend(HuntGpsUpdate(null, HuntGpsState.DENIED))
        } catch (e: IllegalArgumentException) {
            Log.e("FieldwatchHuntGps", "GPS provider unavailable", e)
            trySend(HuntGpsUpdate(null, HuntGpsState.ERROR))
        }
    }
    awaitClose {
        try { manager.removeUpdates(listener) }
        catch (e: SecurityException) { Log.e("FieldwatchHuntGps", "Location permission revoked during cleanup", e) }
    }
}
