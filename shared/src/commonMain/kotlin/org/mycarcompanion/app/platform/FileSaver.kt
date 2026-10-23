package org.mycarcompanion.app.platform

import androidx.compose.runtime.Composable

interface FileSaver {
    fun save(fileName: String, content: String)
}

/** Saves UTF-8 CSV text: Android asks where to save it, web downloads it. */
@Composable
expect fun rememberCsvFileSaver(): FileSaver
