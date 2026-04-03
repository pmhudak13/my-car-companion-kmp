package org.mycarcompanion.app.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

private fun geolocationAvailable(): Boolean = js("typeof navigator !== 'undefined' && navigator.geolocation != null")

private fun requestPosition(
    onSuccess: (Double, Double) -> Unit,
    onError: () -> Unit,
) {
    js("""
        navigator.geolocation.getCurrentPosition(
            function(pos) { onSuccess(pos.coords.latitude, pos.coords.longitude); },
            function() { onError(); },
            { enableHighAccuracy: true, timeout: 10000 }
        )
    """)
}

actual suspend fun getCurrentPosition(): GeoPosition? {
    if (!geolocationAvailable()) return null
    return suspendCancellableCoroutine { cont ->
        requestPosition(
            onSuccess = { lat, lng -> cont.resume(GeoPosition(lat, lng)) },
            onError = { cont.resume(null) },
        )
    }
}
