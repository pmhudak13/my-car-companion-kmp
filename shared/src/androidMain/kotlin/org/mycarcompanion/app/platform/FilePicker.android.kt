package org.mycarcompanion.app.platform

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.ByteArrayOutputStream
import java.io.File
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

@Composable
actual fun rememberVinBarcodeScanner(onResult: (String) -> Unit, onError: (String) -> Unit): FilePickerLauncher? {
    val context = LocalContext.current
    val result = rememberUpdatedState(onResult)
    val error = rememberUpdatedState(onError)
    return remember(context) {
        // Google's scanner UI runs in Play services: no camera permission or camera code in the app
        val scanner = GmsBarcodeScanning.getClient(
            context,
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_CODE_39, Barcode.FORMAT_CODE_128, Barcode.FORMAT_DATA_MATRIX, Barcode.FORMAT_QR_CODE)
                .build(),
        )
        object : FilePickerLauncher {
            override fun launch() {
                scanner.startScan()
                    .addOnSuccessListener { code -> code.rawValue?.let { result.value(it) } }
                    .addOnFailureListener { error.value("The scanner isn't available on this phone. Type the VIN or use a photo.") }
            }
        }
    }
}

@Composable
actual fun rememberCameraPhotoLauncher(onResult: (base64: String, mimeType: String) -> Unit): FilePickerLauncher {
    val context = LocalContext.current
    val callback = rememberUpdatedState(onResult)
    val file = remember { File(context.cacheDir, "photos/vehicle.jpg").apply { parentFile?.mkdirs() } }
    val uri = remember { FileProvider.getUriForFile(context, "${context.packageName}.photos", file) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (!saved) return@rememberLauncherForActivityResult
        val bytes = downscaledJpeg(file) ?: return@rememberLauncherForActivityResult
        callback.value(Base64.encodeToString(bytes, Base64.NO_WRAP), "image/jpeg")
    }
    return remember { object : FilePickerLauncher { override fun launch() = launcher.launch(uri) } }
}

/** Full-size camera shots run 3–8 MB; the AI image limit is 5 MB, and plates read fine at 1600 px. */
private fun downscaledJpeg(file: File, maxSide: Int = 1600): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val scale = maxSide.toFloat() / maxOf(decoded.width, decoded.height)
    val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
}
