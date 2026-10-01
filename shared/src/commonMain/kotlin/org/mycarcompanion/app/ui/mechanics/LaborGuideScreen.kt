package org.mycarcompanion.app.ui.mechanics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.mycarcompanion.app.data.models.LaborGuideResult
import org.mycarcompanion.app.data.repository.JobLineItemRepository
import org.mycarcompanion.app.data.repository.ProfileRepository
import org.mycarcompanion.app.platform.scaffoldContentWindowInsets
import org.mycarcompanion.app.platform.topBarWindowInsets
import org.mycarcompanion.app.ui.formatMoney

/** Labor time lookup without a job: quoting over the phone, checking a time before writing it up. */
// ponytail: plain remember state, no ScreenModel; one call and no state worth surviving rotation
class LaborGuideScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val repository: JobLineItemRepository = koinInject()
        val profileRepository: ProfileRepository = koinInject()
        val scope = rememberCoroutineScope()

        var year by remember { mutableStateOf("") }
        var make by remember { mutableStateOf("") }
        var model by remember { mutableStateOf("") }
        var repair by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<LaborGuideResult?>(null) }
        var rate by remember { mutableStateOf<Double?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var loading by remember { mutableStateOf(false) }

        fun lookUp() {
            val y = year.trim().toIntOrNull()
            if (y == null || make.isBlank() || model.isBlank() || repair.isBlank()) {
                error = "Year, make, model, and repair are required"
                return
            }
            scope.launch {
                loading = true; error = null; result = null
                rate = profileRepository.getMyMechanicProfile().getOrNull()?.hourlyRate
                repository.lookupLaborTime(y, make.trim(), model.trim(), repair.trim())
                    .onSuccess { result = it }
                    .onFailure { error = it.message ?: "Labor lookup failed" }
                loading = false
            }
        }

        Scaffold(
            contentWindowInsets = scaffoldContentWindowInsets(),
            topBar = {
                TopAppBar(
                    title = { Text("Labor Guide") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    windowInsets = topBarWindowInsets(),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = year, onValueChange = { year = it.filter(Char::isDigit).take(4) },
                        label = { Text("Year") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = make, onValueChange = { make = it }, label = { Text("Make") },
                        singleLine = true, modifier = Modifier.weight(1.5f),
                    )
                }
                OutlinedTextField(
                    value = model, onValueChange = { model = it }, label = { Text("Model") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = repair, onValueChange = { repair = it },
                    label = { Text("Repair, e.g. Front brake pads and rotors") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                if (loading) CircularProgressIndicator()
                else Button(onClick = ::lookUp, modifier = Modifier.fillMaxWidth()) { Text("Look up labor time") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                result?.let { r ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${formatQty(r.hours)} hrs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            rate?.takeIf { it > 0 }?.let {
                                Text("About $${formatMoney(r.hours * it)} labor at your $${formatMoney(it)}/hr")
                            }
                            r.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            Text(r.sourceLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
