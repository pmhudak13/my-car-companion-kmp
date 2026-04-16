package org.mycarcompanion.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberImagePickerLauncher(
    onImagePicked: (ByteArray?) -> Unit,
): ImagePickerLauncher = remember {
    // Web image picking not yet implemented — no-op stub
    ImagePickerLauncher { onImagePicked(null) }
}
