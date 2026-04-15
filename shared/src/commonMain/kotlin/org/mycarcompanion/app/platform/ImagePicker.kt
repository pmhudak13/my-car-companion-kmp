package org.mycarcompanion.app.platform

import androidx.compose.runtime.Composable

/**
 * Returns a lambda that, when invoked, launches the platform image picker.
 * The [onResult] callback receives the selected image as a raw ByteArray,
 * or null if the user cancelled.
 */
@Composable
expect fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): () -> Unit
