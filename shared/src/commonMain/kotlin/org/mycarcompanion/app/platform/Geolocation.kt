package org.mycarcompanion.app.platform

data class GeoPosition(val latitude: Double, val longitude: Double)

expect suspend fun getCurrentPosition(): GeoPosition?
