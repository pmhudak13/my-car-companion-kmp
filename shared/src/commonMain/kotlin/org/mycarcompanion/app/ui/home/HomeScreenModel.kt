package org.mycarcompanion.app.ui.home

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.AuthState
import org.mycarcompanion.app.data.repository.AuthRepository

class HomeScreenModel(private val authRepository: AuthRepository) : ScreenModel {

    val authState = authRepository.authState.stateIn(
        scope = screenModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AuthState.Loading
    )

    fun signOut() {
        screenModelScope.launch {
            authRepository.signOut()
        }
    }
}
