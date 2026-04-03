package org.mycarcompanion.app.platform

actual suspend fun getCurrentPosition(): GeoPosition? {
    // iOS CoreLocation integration will be implemented in Phase 8 (iOS launch)
    return null
}
