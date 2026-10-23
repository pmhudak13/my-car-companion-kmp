package org.mycarcompanion.app.platform

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberCsvFileSaver(): FileSaver {
    val context = LocalContext.current
    val pendingContent = remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val content = pendingContent.value
        pendingContent.value = null
        if (uri == null || content == null) return@rememberLauncherForActivityResult
        val saved = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } != null
        }.getOrDefault(false)
        Toast.makeText(context, if (saved) "Report saved" else "Couldn't save the report", Toast.LENGTH_SHORT).show()
    }
    return remember {
        object : FileSaver {
            override fun save(fileName: String, content: String) {
                pendingContent.value = content
                launcher.launch(fileName)
            }
        }
    }
}
