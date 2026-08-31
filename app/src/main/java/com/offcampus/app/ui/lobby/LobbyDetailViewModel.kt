package com.offcampus.app.ui.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.LobbyStatus
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

    private val _isUpdating = MutableStateFlow(false)
    val isUpdating: StateFlow<Boolean> = _isUpdating.asStateFlow()

    private var listener: ListenerRegistration? = null

    init {
        listener = FirebaseRefs.lobbies.document(lobbyId).addSnapshotListener { snapshot, _ ->
            _lobby.value = snapshot?.toObject(Lobby::class.java)?.copy(id = snapshot.id)
        }
    }

    fun join() = updateMembers { FieldValue.arrayUnion(it) }
    fun leave() = updateMembers { FieldValue.arrayRemove(it) }

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

    override fun onCleared() {
        listener?.remove()
    }
}
