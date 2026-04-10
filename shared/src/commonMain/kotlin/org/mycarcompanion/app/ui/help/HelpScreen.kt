package org.mycarcompanion.app.ui.help

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow

private data class FaqItem(val question: String, val answer: String)

private val faqs = listOf(
    FaqItem(
        "How do I add a vehicle?",
        "On the home screen, tap the '+' button at the bottom right. Enter your vehicle's year, make, model, and current odometer reading.",
    ),
    FaqItem(
        "How do maintenance reminders work?",
        "My Car Companion tracks your vehicle's mileage and service dates. When a service is due (by mileage or date), you'll see a reminder in the Reminders screen. Go to Settings → Reminders to view and manage them.",
    ),
    FaqItem(
        "How do I edit or delete a vehicle?",
        "Open the vehicle from your home screen, then tap the gear icon (⚙) at the top right to open Vehicle Settings. Here you can edit any vehicle details or delete it permanently.",
    ),
    FaqItem(
        "How do I find and assign a mechanic?",
        "On your home screen, tap 'Find Mechanic' to browse the mechanic directory. Tap a mechanic to view their profile, then tap 'Assign to Vehicle' to link them to one of your vehicles.",
    ),
    FaqItem(
        "How do I message a mechanic?",
        "Open the mechanic's profile from the directory and tap 'Message'. You can also view all your conversations from Settings → Messages.",
    ),
    FaqItem(
        "How do I log a maintenance service?",
        "Open any vehicle, then tap the '+' button to add a maintenance log. Fill in the category, description, date, and mileage.",
    ),
    FaqItem(
        "How does the Mileage Tracker work?",
        "From the home screen, tap 'Mileage Tracker' to log trips. Enter your start and end odometer readings and the app calculates the distance.",
    ),
    FaqItem(
        "How do I update my profile?",
        "Go to Settings → Profile to update your first and last name.",
    ),
    FaqItem(
        "I'm a mechanic — how do I set up my profile?",
        "After signing up with the Mechanic role, you'll be prompted to complete your profile (shop name, location, specialty). You can edit it any time from the Edit Profile button in your dashboard.",
    ),
    FaqItem(
        "How does mechanic verification work?",
        "After completing your mechanic profile, an admin will review and approve your account. Once verified, you'll appear in the mechanic directory for customers to find.",
    ),
)

class HelpScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Help & FAQ") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(faqs) { faq ->
                    FaqCard(faq)
                }
            }
        }
    }
}

@Composable
private fun FaqCard(faq: FaqItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(faq.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(faq.answer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
