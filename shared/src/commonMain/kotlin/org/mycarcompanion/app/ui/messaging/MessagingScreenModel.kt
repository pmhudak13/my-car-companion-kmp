package org.mycarcompanion.app.ui.messaging

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.Message
import org.mycarcompanion.app.data.repository.AuthRepository
import org.mycarcompanion.app.data.repository.MessageRepository

data class MessagingState(
    val messages: List<Message> = emptyList(),
    /** storage path -> signed URL, valid ~1h. Only populated for messages with a photo. */
    val photoUrls: Map<String, String> = emptyMap(),
    val currentUserId: String = "",
    val isLoading: Boolean = true,
    val error: String? = null,
    val composeText: String = "",
    val isSending: Boolean = false,
    val sendError: String? = null,
)

class MessagingScreenModel(
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository,
    private val supabaseClient: SupabaseClient,
) : ScreenModel {

    private val _state = MutableStateFlow(MessagingState())
    val state: StateFlow<MessagingState> = _state.asStateFlow()

    private var realtimeRecipientId: String? = null

    fun subscribeToConversation(otherUserId: String) {
        if (realtimeRecipientId == otherUserId) return
        realtimeRecipientId = otherUserId
        screenModelScope.launch {
            val channel = supabaseClient.channel("conversation_$otherUserId")
            channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "chat_messages"
            }.onEach {
                // Reload conversation when a new row arrives
                loadConversation(otherUserId)
            }.launchIn(screenModelScope)
            channel.subscribe()
        }
    }

    fun loadInbox() {
        screenModelScope.launch {
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                _state.value = _state.value.copy(isLoading = false, error = "Not signed in")
                return@launch
            }
            _state.value = _state.value.copy(isLoading = true, error = null, currentUserId = currentUserId)
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
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                _state.value = _state.value.copy(isLoading = false, error = "Not signed in")
                return@launch
            }
            _state.value = _state.value.copy(isLoading = true, error = null, currentUserId = currentUserId)
            messageRepository.getConversation(otherUserId)
                .onSuccess { messages ->
                    _state.value = _state.value.copy(messages = messages, isLoading = false)
                    signPhotos(messages)
                    messages.filter { !it.isRead && it.recipientId == currentUserId }
                        .forEach { messageRepository.markAsRead(it.id) }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(error = e.message ?: "Failed to load conversation", isLoading = false)
                }
        }
    }

    fun onComposeChange(text: String) {
        _state.value = _state.value.copy(composeText = text, sendError = null)
    }

    // Signs only paths we haven't signed yet, so a reload doesn't re-sign the whole thread.
    private suspend fun signPhotos(messages: List<Message>) {
        val known = _state.value.photoUrls
        val newUrls = messages.mapNotNull { it.imagePath }
            .filter { it !in known }
            .distinct()
            .mapNotNull { path -> messageRepository.signedPhotoUrl(path)?.let { path to it } }
        if (newUrls.isNotEmpty()) {
            _state.value = _state.value.copy(photoUrls = known + newUrls)
        }
    }

    fun sendPhoto(recipientId: String, fileName: String, base64: String, vehicleId: String? = null) {
        screenModelScope.launch {
            _state.value = _state.value.copy(isSending = true, sendError = null)
            messageRepository.sendPhoto(
                recipientId = recipientId,
                fileName = fileName,
                base64Data = base64,
                caption = _state.value.composeText,
                vehicleId = vehicleId,
            )
                .onSuccess {
                    _state.value = _state.value.copy(isSending = false, composeText = "")
                    loadConversation(recipientId)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(isSending = false, sendError = e.message ?: "Failed to send photo")
                }
        }
    }

    override fun onDispose() {
        realtimeRecipientId?.let { otherId ->
            screenModelScope.launch {
                runCatching { supabaseClient.realtime.removeChannel(supabaseClient.channel("conversation_$otherId")) }
            }
        }
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
