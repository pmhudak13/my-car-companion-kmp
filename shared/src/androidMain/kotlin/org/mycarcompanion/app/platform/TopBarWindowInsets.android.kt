package org.mycarcompanion.app.platform

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun topBarWindowInsets(): WindowInsets = TopAppBarDefaults.windowInsets
