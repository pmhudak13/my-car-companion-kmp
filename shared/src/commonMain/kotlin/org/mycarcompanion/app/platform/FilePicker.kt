package org.mycarcompanion.app.platform

import androidx.compose.runtime.Composable

interface FilePickerLauncher {
    fun launch()
}

/** Picks a plain-text file (CSV). Delivers UTF-8 content as a String. */
@Composable
expect fun rememberTextFilePickerLauncher(
    onResult: (fileName: String, content: String) -> Unit,
): FilePickerLauncher

/**
 * Picks an image or PDF. Delivers base64-encoded content + MIME type so callers
 * can forward it directly to the AI edge function without platform IO.
 */
@Composable
expect fun rememberBinaryFilePickerLauncher(
    onResult: (fileName: String, base64: String, mimeType: String) -> Unit,
): FilePickerLauncher

/** Scans a VIN barcode with the camera and delivers the raw text. Null where there is no scanner (web). */
@Composable
expect fun rememberVinBarcodeScanner(onResult: (String) -> Unit, onError: (String) -> Unit): FilePickerLauncher?

/** Takes a photo (camera on Android, camera-or-file on web) and delivers it as base64 + MIME type. */
@Composable
expect fun rememberCameraPhotoLauncher(onResult: (base64: String, mimeType: String) -> Unit): FilePickerLauncher
