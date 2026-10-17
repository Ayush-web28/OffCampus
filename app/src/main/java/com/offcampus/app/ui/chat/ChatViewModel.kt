package com.offcampus.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.NotificationService
import com.offcampus.app.data.model.ChatMessage
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.Rider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Deterministic chat id for a friend pair, so either side can compute the same document
 * without a lookup — sorted so it doesn't matter who opens the chat first. */
fun friendChatId(uidA: String, uidB: String): String = listOf(uidA, uidB).sorted().joinToString("_")

/**
 * Drives one chat thread — either a lobby's group chat (chatId == the lobby's id) or a
 * friend-to-friend thread (chatId from [friendChatId]). Both are just documents in the same
 * top-level "chats" collection with a "messages" subcollection, per the Phase 1 data model.
 */
class ChatViewModel(private val chatId: String) : ViewModel() {
    private val uid: String? get() = Firebase.auth.currentUser?.uid

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // senderId -> display name, resolved lazily as new senders show up in the message list —
    // needed for lobby chats, which are group chats where "who said this" isn't implied by side.
    private val nameCache = mutableMapOf<String, String>()
    private val _senderNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val senderNames: StateFlow<Map<String, String>> = _senderNames.asStateFlow()

    val currentUid: String? get() = uid

    private var listener: ListenerRegistration? = null

    init {
        listener = FirebaseRefs.chats.document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                val loaded = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                _messages.value = loaded
                resolveSenderNames(loaded)
                markRead(loaded)
            }
    }

    // Having this chat's listener open at all means the rider is looking at it right now — on
    // first open, or on any later message arriving while they stay on the screen. One write
    // covers both cases, so there's no separate "mark as read" action to remember to call.
    // Feeds UnreadActivityViewModel's alert dot, which compares this against each chat's newest
    // message timestamp.
    private fun markRead(loaded: List<ChatMessage>) {
        val myUid = uid ?: return
        if (loaded.isEmpty()) return
        val latestTimestamp = loaded.map { it.timestamp }.maxOrNull() ?: Timestamp.now()
        FirebaseRefs.riders.document(myUid).collection("chatReads").document(chatId)
            .set(mapOf("lastReadAt" to latestTimestamp))
    }

    private fun resolveSenderNames(loaded: List<ChatMessage>) {
        val unknown = loaded.map { it.senderId }.distinct().filter { it !in nameCache }
        if (unknown.isEmpty()) return
        viewModelScope.launch {
            for (senderId in unknown) {
                nameCache[senderId] = try {
                    FirebaseRefs.riders.document(senderId).get().await()
                        .toObject(Rider::class.java)?.name
                } catch (e: Exception) {
                    null
                } ?: "Rider"
            }
            _senderNames.value = nameCache.toMap()
        }
    }

    fun send(text: String) {
        val id = uid ?: return
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                FirebaseRefs.chats.document(chatId).collection("messages")
                    .add(ChatMessage(senderId = id, text = trimmed, timestamp = Timestamp.now()))
                    .await()

                val senderName = nameCache[id] ?: try {
                    FirebaseRefs.riders.document(id).get().await()
                        .toObject(Rider::class.java)?.name?.ifBlank { null }
                } catch (_: Exception) { null } ?: "Rider"

                if (chatId.contains("_")) {
                    val uids = chatId.split("_")
                    val recipientId = uids.firstOrNull { it != id } ?: ""
                    if (recipientId.isNotBlank()) {
                        NotificationService.notifyFriendChat(
                            recipientId = recipientId,
                            senderName = senderName,
                            messageText = trimmed,
                            chatId = chatId
                        )
                    }
                } else {
                    try {
                        val lobbyDoc = FirebaseRefs.lobbies.document(chatId).get().await()
                        val lobby = lobbyDoc.toObject(Lobby::class.java)
                        if (lobby != null) {
                            val destination = lobby.destination.ifBlank { "Lobby" }
                            NotificationService.notifyLobbyChat(
                                memberIds = lobby.memberIds,
                                senderId = id,
                                senderName = senderName,
                                destination = destination,
                                messageText = trimmed,
                                lobbyId = chatId
                            )
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                // The message just won't appear — the input keeps whatever the caller does with
                // it (ChatScreen clears the field optimistically since retry-by-retyping is fine here).
            }
        }
    }

    override fun onCleared() {
        listener?.remove()
    }
}
