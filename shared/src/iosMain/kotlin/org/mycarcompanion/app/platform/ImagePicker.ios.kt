package org.mycarcompanion.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): () -> Unit {
    // TODO: Wire up UIImagePickerController / PHPickerViewController for iOS
    return remember { { /* no-op on iOS for now */ } }
}
