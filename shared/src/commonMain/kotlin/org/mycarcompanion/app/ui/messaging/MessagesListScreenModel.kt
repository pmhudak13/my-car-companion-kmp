package org.mycarcompanion.app.ui.messaging

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.Message
import org.mycarcompanion.app.data.repository.AuthRepository
import org.mycarcompanion.app.data.repository.MessageRepository

data class ConversationThread(
    val otherUserId: String,
    val latestMessage: Message,
    val unreadCount: Int,
)

data class MessagesListState(
    val threads: List<ConversationThread> = emptyList(),
    val currentUserId: String = "",
    val loading: Boolean = true,
    val error: String? = null,
)

class MessagesListScreenModel(
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(MessagesListState())
    val state: StateFlow<MessagesListState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        screenModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                _state.value = _state.value.copy(loading = false, error = "Not signed in")
                return@launch
            }
            messageRepository.getAllMessages()
                .onSuccess { messages ->
                    // Group messages by the other participant
                    val threadMap = mutableMapOf<String, MutableList<Message>>()
                    for (msg in messages) {
                        val otherId = if (msg.senderId == currentUserId) msg.recipientId else msg.senderId
                        threadMap.getOrPut(otherId) { mutableListOf() }.add(msg)
                    }
                    val threads = threadMap.entries.map { (otherId, msgs) ->
                        val sorted = msgs.sortedByDescending { it.createdAt }
                        ConversationThread(
                            otherUserId = otherId,
                            latestMessage = sorted.first(),
                            unreadCount = sorted.count { !it.isRead && it.recipientId == currentUserId },
                        )
                    }.sortedByDescending { it.latestMessage.createdAt }
                    _state.value = _state.value.copy(
                        loading = false,
                        threads = threads,
                        currentUserId = currentUserId,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        error = e.message ?: "Failed to load messages",
                    )
                }
        }
    }
}
