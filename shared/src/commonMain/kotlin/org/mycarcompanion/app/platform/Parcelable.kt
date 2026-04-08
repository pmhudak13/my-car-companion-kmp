package org.mycarcompanion.app.platform

// Common interface so data class Screens can implement Parcelable on Android
// while compiling on iOS and wasmJs without issue.
expect interface CommonParcelable
