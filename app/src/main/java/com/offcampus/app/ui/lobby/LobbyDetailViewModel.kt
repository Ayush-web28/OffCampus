package com.offcampus.app.ui.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.LobbyStatus
import com.offcampus.app.data.model.Rider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** One lobby, watched live so joins/leaves/locks from other members show up immediately. */
class LobbyDetailViewModel(private val lobbyId: String) : ViewModel() {
    val currentUid: String? get() = Firebase.auth.currentUser?.uid

    private val _lobby = MutableStateFlow<Lobby?>(null)
    val lobby: StateFlow<Lobby?> = _lobby.asStateFlow()

    // Resolved from lobby.memberIds so the detail screen can show who's actually in the lobby
    // (name + avatar), not just a bare count — the safety point of seeing who you'd be riding
    // with before you join.
    private val _members = MutableStateFlow<List<Rider>>(emptyList())
    val members: StateFlow<List<Rider>> = _members.asStateFlow()
    private var lastLoadedMemberIds: List<String>? = null

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private var listener: ListenerRegistration? = null

    init {
        listener = FirebaseRefs.lobbies.document(lobbyId).addSnapshotListener { snapshot, _ ->
            val lobby = snapshot?.toObject(Lobby::class.java)?.copy(id = snapshot.id)
            _lobby.value = lobby
            if (lobby != null && lobby.status == LobbyStatus.OPEN && lobby.isAutoExpired()) {
                autoCancelLobby()
            }
            // Avoids re-fetching all member profiles on every snapshot (e.g. a lock/status
            // change) when the member list itself hasn't actually changed.
            val memberIds = lobby?.memberIds ?: emptyList()
            if (memberIds != lastLoadedMemberIds) {
                lastLoadedMemberIds = memberIds
                loadMembers(memberIds)
            }
        }

        viewModelScope.launch {
            while (true) {
                delay(5_000)
                val current = _lobby.value
                if (current != null && current.status == LobbyStatus.OPEN && current.isAutoExpired()) {
                    autoCancelLobby()
                }
            }
        }
    }

    private fun autoCancelLobby() {
        viewModelScope.launch {
            try {
                FirebaseRefs.lobbies.document(lobbyId).update(
                    mapOf(
                        "status" to LobbyStatus.CANCELLED.name,
                        "cancelReason" to Lobby.CANCEL_REASON_AUTO_EXPIRED
                    )
                ).await()
            } catch (_: Exception) {}
        }
    }

    private fun loadMembers(memberIds: List<String>) {
        if (memberIds.isEmpty()) {
            _members.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                // whereIn caps at 30 values — comfortably above this app's 6-rider lobby cap.
                val snapshot = FirebaseRefs.riders
                    .whereIn(FieldPath.documentId(), memberIds.take(30))
                    .get().await()
                _members.value = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Rider::class.java)?.copy(id = doc.id)
                }
            } catch (e: Exception) {
                _members.value = emptyList()
            }
        }
    }

    fun join() = updateMembers { FieldValue.arrayUnion(it) }
    fun leave() = updateMembers { FieldValue.arrayRemove(it) }

    /** Master can remove a member from the lobby (cannot remove themselves). */
    fun removeRider(riderId: String) {
        val uid = currentUid ?: return
        val currentLobby = _lobby.value ?: return
        if (currentLobby.createdBy != uid || riderId == currentLobby.createdBy) return
        viewModelScope.launch {
            _isUpdating.value = true
            try {
                FirebaseRefs.lobbies.document(lobbyId).update("memberIds", FieldValue.arrayRemove(riderId)).await()
            } catch (e: Exception) {
                // Safe catch prevents crash on unexpected network/permission issues
            } finally {
                _isUpdating.value = false
            }
        }
    }

    private fun updateMembers(fieldValue: (String) -> FieldValue) {
        val id = currentUid ?: return
        viewModelScope.launch {
            _isUpdating.value = true
            try {
                FirebaseRefs.lobbies.document(lobbyId).update("memberIds", fieldValue(id)).await()
            } finally {
                _isUpdating.value = false
            }
        }
    }

    /** Only the lobby master (createdBy) can call this — enforced again by the UI, which hides
     * the button for everyone else, and will be enforced server-side by Phase 9's security rules. */
    fun lock() {
        viewModelScope.launch {
            _isUpdating.value = true
            try {
                FirebaseRefs.lobbies.document(lobbyId).update("status", LobbyStatus.LOCKED.name).await()
            } finally {
                _isUpdating.value = false
            }
        }
    }

    /** Master can cancel the lobby before the ride is finished (while OPEN or LOCKED). */
    fun cancelLobby(onSuccess: () -> Unit = {}) {
        val uid = currentUid ?: return
        val currentLobby = _lobby.value ?: return
        if (currentLobby.createdBy != uid) return
        viewModelScope.launch {
            _isUpdating.value = true
            try {
                FirebaseRefs.lobbies.document(lobbyId).update("status", LobbyStatus.CANCELLED.name).await()
                onSuccess()
            } catch (e: Exception) {
                // Safe catch prevents crash
            } finally {
                _isUpdating.value = false
            }
        }
    }

    override fun onCleared() {
        listener?.remove()
    }
}
