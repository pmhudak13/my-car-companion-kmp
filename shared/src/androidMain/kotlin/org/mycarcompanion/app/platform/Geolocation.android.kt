package org.mycarcompanion.app.platform

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal var appContext: Context? = null

fun initGeolocation(context: Context) {
    appContext = context.applicationContext
}

@SuppressLint("MissingPermission")
actual suspend fun getCurrentPosition(): GeoPosition? {
    val context = appContext ?: return null
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null

    return suspendCancellableCoroutine { cont ->
        try {
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (lastKnown != null) {
                cont.resume(GeoPosition(lastKnown.latitude, lastKnown.longitude))
            } else {
                cont.resume(null)
            }
        } catch (_: Exception) {
            cont.resume(null)
        }
    }
}
