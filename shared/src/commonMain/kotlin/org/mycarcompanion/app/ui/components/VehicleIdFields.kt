package org.mycarcompanion.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.mycarcompanion.app.data.repository.DecodedVin
import org.mycarcompanion.app.data.repository.VehicleLookupRepository
import org.mycarcompanion.app.data.repository.normalizeVin
import org.mycarcompanion.app.data.repository.vinCheckDigitOk
import org.mycarcompanion.app.platform.rememberCameraPhotoLauncher
import org.mycarcompanion.app.platform.rememberVinBarcodeScanner

/**
 * License plate + VIN fields with the shortcuts Bryan asked for: scan the VIN barcode (Android),
 * read the plate from a photo (AI), and decode the VIN into year/make/model (NHTSA).
 */
@Composable
fun VehicleIdFields(
    vin: String,
    plate: String,
    onVinChange: (String) -> Unit,
    onPlateChange: (String) -> Unit,
    onDecoded: (DecodedVin) -> Unit,
    /** Photo reading costs an AI call per shot, so only mechanics get it (the server enforces this too). */
    allowPhoto: Boolean = true,
) {
    val lookup: VehicleLookupRepository = koinInject()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    // photo results belong under the plate field, scan/decode results under the VIN field
    var messageForPlate by remember { mutableStateOf(false) }

    fun decode(raw: String) {
        scope.launch {
            busy = "Decoding VIN…"
            messageForPlate = false
            lookup.decodeVin(raw)
                .onSuccess { d ->
                    onDecoded(d)
                    message = "Filled in ${listOfNotNull(d.year?.toString(), d.make, d.model.ifBlank { null }).joinToString(" ")}"
                    isError = false
                }
                .onFailure { message = it.message; isError = true }
            busy = null
        }
    }

    val scanner = rememberVinBarcodeScanner(
        onResult = { raw ->
            val clean = normalizeVin(raw)
            messageForPlate = false
            if (clean == null) {
                message = "That barcode isn't a VIN. Try the one on the door jamb or windshield."
                isError = true
            } else if (!vinCheckDigitOk(clean)) {
                // a scratched or angled label can misread one character; don't fill in a wrong VIN
                message = "The barcode didn't scan cleanly. Hold steady and scan again, or type the VIN."
                isError = true
            } else {
                onVinChange(clean)
                decode(clean)
            }
        },
        onError = { message = it; isError = true },
    )

    val camera = rememberCameraPhotoLauncher { base64, mime ->
        scope.launch {
            busy = "Reading plate…"
            messageForPlate = true
            lookup.readPlatePhoto(base64, mime)
                .onSuccess { plate ->
                    onPlateChange(plate)
                    message = "Read plate $plate. Double-check it."
                    isError = false
                }
                .onFailure { message = it.message; isError = true }
            busy = null
        }
    }

    Column {
        OutlinedTextField(
            value = plate,
            onValueChange = onPlateChange,
            label = { Text("License Plate") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            trailingIcon = if (!allowPhoto) null else {
                {
                    IconButton(onClick = camera::launch, enabled = busy == null) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Read plate from a photo")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (messageForPlate) StatusLine(busy, message, isError)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = vin,
            onValueChange = onVinChange,
            label = { Text("VIN") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            trailingIcon = scanner?.let { s ->
                {
                    IconButton(onClick = s::launch, enabled = busy == null) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan VIN barcode")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { decode(vin) }, enabled = busy == null && vin.isNotBlank()) {
            Text("Decode VIN")
        }
        if (!messageForPlate) StatusLine(busy, message, isError)
    }
}

@Composable
private fun StatusLine(busy: String?, message: String?, isError: Boolean) {
    busy?.let {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
    message?.takeIf { busy == null }?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
