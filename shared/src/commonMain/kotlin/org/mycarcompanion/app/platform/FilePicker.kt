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
