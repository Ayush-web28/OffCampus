package com.offcampus.app.ui.navigation

import androidx.lifecycle.ViewModel
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.Rider
import com.offcampus.app.data.observeSignedInUid
import com.offcampus.app.ui.chat.friendChatId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks, across every chat this rider is part of (every lobby they're in + every friend chat),
 * whether any of them has a message newer than this rider's own read marker for it — feeding the
 * single alert dot on the Friends tab and the matching dot on a lobby's own card in the browse
 * list. Deliberately a presence check, not a count: Firestore has no cheap way to total up exact
 * unread messages across a changing set of chats, but "does at least one exist" only needs each
 * chat's newest message, which is a one-row query per chat.
 */
class UnreadActivityViewModel : ViewModel() {
    private val uid: String? get() = Firebase.auth.currentUser?.uid

    private val _unreadChatIds = MutableStateFlow<Set<String>>(emptySet())
    val unreadChatIds: StateFlow<Set<String>> = _unreadChatIds.asStateFlow()

    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts.asStateFlow()

    private var riderListener: ListenerRegistration? = null
    private var lobbiesListener: ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null

    // One watcher per relevant chat id, added or torn down as the relevant set changes (a friend
    // removed, a lobby left) so no listener outlives its reason to exist.
    private val chatWatchers = mutableMapOf<String, ChatWatcher>()
    private var lastFriendChatIds: List<String> = emptyList()
    private var lastLobbyChatIds: List<String> = emptyList()

    init {
        // See observeSignedInUid's doc — a one-shot `val id = uid` here used to keep this dot lit
        // (or dark) based on the previous account's chats after switching accounts mid-session.
        authListener = observeSignedInUid(::attachListeners)
    }

    private fun attachListeners(id: String?) {
        riderListener?.remove()
        lobbiesListener?.remove()
        chatWatchers.values.forEach { it.remove() }
        chatWatchers.clear()
        lastFriendChatIds = emptyList()
        lastLobbyChatIds = emptyList()
        _unreadChatIds.value = emptySet()
        _unreadCounts.value = emptyMap()
        if (id == null) return

        riderListener = FirebaseRefs.riders.document(id).addSnapshotListener { snapshot, _ ->
            val friendIds = snapshot?.toObject(Rider::class.java)?.friendIds ?: emptyList()
            syncWatchedChats(friendChatIds = friendIds.map { friendChatId(id, it) })
        }
        // The creator is always added to their own lobby's memberIds on posting (see
        // PostTripViewModel), so this one query covers both "created" and "joined".
        lobbiesListener = FirebaseRefs.lobbies
            .whereArrayContains("memberIds", id)
            .addSnapshotListener { snapshot, _ ->
                syncWatchedChats(lobbyChatIds = snapshot?.documents?.map { it.id } ?: emptyList())
            }
    }

    // Each listener above only knows its own half of the picture — this remembers the other
    // half so a lobby update, say, never wipes out the friend chats already being watched.
    private fun syncWatchedChats(
        friendChatIds: List<String> = lastFriendChatIds,
        lobbyChatIds: List<String> = lastLobbyChatIds
    ) {
        lastFriendChatIds = friendChatIds
        lastLobbyChatIds = lobbyChatIds
        val id = uid ?: return
        val relevant = (friendChatIds + lobbyChatIds).toSet()

        (chatWatchers.keys - relevant).forEach { staleId -> chatWatchers.remove(staleId)?.remove() }
        (relevant - chatWatchers.keys).forEach { newId ->
            chatWatchers[newId] = ChatWatcher(id, newId, onChange = ::recomputeUnread)
        }
        recomputeUnread()
    }

    private fun recomputeUnread() {
        val counts = mutableMapOf<String, Int>()
        val unreadIds = mutableSetOf<String>()
        for ((chatId, watcher) in chatWatchers) {
            val count = watcher.unreadCount()
            if (count > 0) {
                counts[chatId] = count
                unreadIds.add(chatId)
            }
        }
        _unreadCounts.value = counts
        _unreadChatIds.value = unreadIds
    }

    override fun onCleared() {
        authListener?.let { Firebase.auth.removeAuthStateListener(it) }
        riderListener?.remove()
        lobbiesListener?.remove()
        chatWatchers.values.forEach { it.remove() }
    }
}

/** Watches one chat's recent messages and this rider's own read marker for it, calculating
 * the exact unread message count. */
private class ChatWatcher(uid: String, chatId: String, private val onChange: () -> Unit) {
    private var messageTimestamps: List<Timestamp> = emptyList()
    private var lastReadAt: Timestamp? = null

    private val messageListener = FirebaseRefs.chats.document(chatId).collection("messages")
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .limit(20)
        .addSnapshotListener { snapshot, _ ->
            messageTimestamps = snapshot?.documents?.mapNotNull { it.getTimestamp("timestamp") } ?: emptyList()
            onChange()
        }

    private val readListener = FirebaseRefs.riders.document(uid).collection("chatReads")
        .document(chatId)
        .addSnapshotListener { snapshot, _ ->
            lastReadAt = snapshot?.getTimestamp("lastReadAt")
            onChange()
        }

    fun unreadCount(): Int {
        if (messageTimestamps.isEmpty()) return 0
        val read = lastReadAt ?: return messageTimestamps.size
        return messageTimestamps.count { it > read }
    }

    fun isUnread(): Boolean = unreadCount() > 0

    fun remove() {
        messageListener.remove()
        readListener.remove()
    }
}
