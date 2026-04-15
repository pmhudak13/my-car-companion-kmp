package org.mycarcompanion.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import org.mycarcompanion.app.platform.rememberImagePickerLauncher

class ProfileScreen : Screen {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: ProfileScreenModel = koinScreenModel()
        val state by model.state.collectAsState()

        val launchImagePicker = rememberImagePickerLauncher { bytes ->
            if (bytes != null) model.onAvatarPicked(bytes)
        }

        LaunchedEffect(state.saved) {
            if (state.saved) navigator.pop()
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Profile") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { paddingValues ->
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Avatar
                    AvatarSection(
                        avatarUrl = state.avatarUrl,
                        pendingBytes = state.pendingAvatarBytes,
                        uploadingPhoto = state.uploadingPhoto,
                        onPickPhoto = launchImagePicker,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    state.error?.let { error ->
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    Text("First Name", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = state.firstName,
                        onValueChange = { model.updateFirstName(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("First name") },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Last Name", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = state.lastName,
                        onValueChange = { model.updateLastName(it) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Last name") },
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Email", style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.email.ifEmpty { "—" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { model.save() },
                        enabled = !state.saving && !state.uploadingPhoto,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.saving || state.uploadingPhoto) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.height(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarSection(
    avatarUrl: String?,
    pendingBytes: ByteArray?,
    uploadingPhoto: Boolean,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                pendingBytes != null -> {
                    // Show a placeholder with spinner while pending
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✓", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                !avatarUrl.isNullOrBlank() -> {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Profile photo",
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (uploadingPhoto) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onPickPhoto) {
            Text(if (pendingBytes != null || !avatarUrl.isNullOrBlank()) "Change Photo" else "Add Photo")
        }
    }
}
