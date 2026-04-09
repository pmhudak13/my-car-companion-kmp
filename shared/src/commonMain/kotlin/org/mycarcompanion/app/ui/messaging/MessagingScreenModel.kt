package org.mycarcompanion.app.ui.messaging

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.Message
import org.mycarcompanion.app.data.repository.MessageRepository

data class MessagingState(
    val messages: List<Message> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val composeText: String = "",
    val isSending: Boolean = false,
    val sendError: String? = null,
)

class MessagingScreenModel(
    private val messageRepository: MessageRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(MessagingState())
    val state: StateFlow<MessagingState> = _state.asStateFlow()

    fun loadInbox() {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            messageRepository.getInbox()
                .onSuccess { messages ->
                    _state.value = _state.value.copy(messages = messages, isLoading = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to load messages", isLoading = false)
                }
        }
    }

    fun loadConversation(otherUserId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            messageRepository.getConversation(otherUserId)
                .onSuccess { messages ->
                    _state.value = _state.value.copy(messages = messages, isLoading = false)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to load conversation", isLoading = false)
                }
        }
    }

    fun onComposeChange(text: String) {
        _state.value = _state.value.copy(composeText = text, sendError = null)
    }

    fun sendMessage(recipientId: String, vehicleId: String? = null, onSent: () -> Unit = {}) {
        val text = _state.value.composeText.trim()
        if (text.isBlank()) return
        screenModelScope.launch {
            _state.value = _state.value.copy(isSending = true, sendError = null)
            messageRepository.sendMessage(recipientId, text, vehicleId)
                .onSuccess {
                    _state.value = _state.value.copy(isSending = false, composeText = "")
                    loadConversation(recipientId)
                    onSent()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isSending = false, sendError = e.message ?: "Failed to send")
                }
        }
    }
}
