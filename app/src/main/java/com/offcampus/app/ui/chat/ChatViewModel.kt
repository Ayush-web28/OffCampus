package com.offcampus.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.ChatMessage
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
            }
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

    /** Writes/refreshes the parent chat doc's participant list — not needed for messages to
     * work (Firestore subcollections don't require the parent to exist), but Phase 9's security
     * rules will need it to check "is this reader actually part of this chat". */
    fun ensureParticipants(participantIds: List<String>) {
        viewModelScope.launch {
            try {
                FirebaseRefs.chats.document(chatId)
                    .set(mapOf("participantIds" to participantIds), SetOptions.merge())
                    .await()
            } catch (e: Exception) {
                // Non-critical — messages still send/receive without this.
            }
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
