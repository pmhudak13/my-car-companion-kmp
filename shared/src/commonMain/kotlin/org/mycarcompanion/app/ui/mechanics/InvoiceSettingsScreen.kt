package org.mycarcompanion.app.ui.mechanics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import org.mycarcompanion.app.platform.rememberBinaryFilePickerLauncher
import org.mycarcompanion.app.platform.scaffoldContentWindowInsets
import org.mycarcompanion.app.platform.topBarWindowInsets

/** Logo, contact details, payment methods and terms that print on every invoice. */
@OptIn(ExperimentalMaterial3Api::class)
class InvoiceSettingsScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: InvoiceSettingsScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val snackbar = remember { SnackbarHostState() }
        val logoPicker = rememberBinaryFilePickerLauncher { _, base64, mimeType -> model.uploadLogo(base64, mimeType) }

        LaunchedEffect(Unit) { model.load() }
        LaunchedEffect(state.message) {
            state.message?.let {
                snackbar.showSnackbar(it)
                model.clearMessage()
            }
        }

        Scaffold(
            contentWindowInsets = scaffoldContentWindowInsets(),
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = { Text("Invoices & Payments") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    windowInsets = topBarWindowInsets(),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                if (state.isLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    return@Column
                }
                val f = state.form

                Section("Shop logo")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    state.logoUrl?.let {
                        AsyncImage(model = it, contentDescription = "Shop logo", modifier = Modifier.size(64.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    if (state.isUploadingLogo) CircularProgressIndicator(Modifier.size(24.dp))
                    else OutlinedButton(onClick = { logoPicker.launch() }) {
                        Text(if (state.logoUrl == null) "Upload logo" else "Change logo")
                    }
                }
                Hint("PNG or JPG under 2 MB. Shows at the top of your invoices.")

                Section("Shop contact (invoice header)")
                Field("Phone", f.phone, KeyboardType.Phone) { model.update(f.copy(phone = it)) }
                Field("Street address", f.address) { model.update(f.copy(address = it)) }
                Row {
                    Field("City", f.city, modifier = Modifier.weight(1f)) { model.update(f.copy(city = it)) }
                    Spacer(Modifier.width(8.dp))
                    Field("State", f.state, modifier = Modifier.weight(0.6f)) { model.update(f.copy(state = it)) }
                    Spacer(Modifier.width(8.dp))
                    Field("ZIP", f.zipCode, KeyboardType.Number, Modifier.weight(0.8f)) { model.update(f.copy(zipCode = it)) }
                }

                Section("How customers can pay you")
                Hint("Fill in the ones you take. Each shows on the invoice as a pay button or instructions.")
                Field("Zelle (email or phone)", f.payZelle) { model.update(f.copy(payZelle = it)) }
                Field("Venmo username", f.payVenmo) { model.update(f.copy(payVenmo = it)) }
                Field("Cash App \$cashtag", f.payCashapp) { model.update(f.copy(payCashapp = it)) }
                Field("PayPal.me name", f.payPaypal) { model.update(f.copy(payPaypal = it)) }
                Field("Card payment link (Square, Stripe, etc.)", f.payCardLink, KeyboardType.Uri) { model.update(f.copy(payCardLink = it)) }
                Field("Other payment instructions", f.paymentNotes, singleLine = false) { model.update(f.copy(paymentNotes = it)) }

                Section("Invoice terms")
                Field("Terms (warranty, returns, due date)", f.invoiceTerms, singleLine = false) { model.update(f.copy(invoiceTerms = it)) }
                Hint("For example: \"Payment due on receipt. Parts and labor warranted 12 months or 12,000 miles.\"")

                Spacer(Modifier.height(16.dp))
                Button(onClick = model::save, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                    if (state.isSaving) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    else Text("Save")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Field(
    label: String,
    value: String?,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier.fillMaxWidth(),
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.padding(bottom = 8.dp),
    )
}
