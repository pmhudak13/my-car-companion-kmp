package org.mycarcompanion.app.platform

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberTextFilePickerLauncher(
    onResult: (fileName: String, content: String) -> Unit,
): FilePickerLauncher {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "import.csv"
        val content = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.readText() ?: return@rememberLauncherForActivityResult
        callback.value(fileName, content)
    }
    return remember {
        object : FilePickerLauncher {
            override fun launch() = launcher.launch(
                arrayOf("text/csv", "text/plain", "application/csv", "application/vnd.ms-excel"),
            )
        }
    }
}

@Composable
actual fun rememberBinaryFilePickerLauncher(
    onResult: (fileName: String, base64: String, mimeType: String) -> Unit,
): FilePickerLauncher {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "invoice"
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.readBytes()
            ?: return@rememberLauncherForActivityResult
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        callback.value(fileName, base64, mimeType)
    }
    return remember {
        object : FilePickerLauncher {
            override fun launch() = launcher.launch(
                arrayOf("image/jpeg", "image/png", "image/webp", "image/gif", "application/pdf"),
            )
        }
    }
}
