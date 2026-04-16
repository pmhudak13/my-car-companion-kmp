package org.mycarcompanion.app.platform

import androidx.compose.runtime.Composable

/**
 * Wraps a platform launcher so common code can call [launch] without
 * knowing Android / iOS specifics.
 */
class ImagePickerLauncher(private val launchFn: () -> Unit) {
    fun launch() = launchFn()
}

/**
 * Returns a remembered [ImagePickerLauncher] that opens the system photo
 * picker and delivers the selected image as a [ByteArray] (or null on cancel).
 */
@Composable
expect fun rememberImagePickerLauncher(
    onImagePicked: (ByteArray?) -> Unit,
): ImagePickerLauncher
