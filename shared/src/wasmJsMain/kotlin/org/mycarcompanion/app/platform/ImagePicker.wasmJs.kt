package org.mycarcompanion.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): () -> Unit {
    // TODO: Wire up a web file-input element for browser image picking
    return remember { { /* no-op on wasmJs for now */ } }
}
