package com.offcampus.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.Rider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel : ViewModel() {
    private val uid: String? get() = Firebase.auth.currentUser?.uid

    private val _rider = MutableStateFlow<Rider?>(null)
    val rider: StateFlow<Rider?> = _rider.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private var listener: ListenerRegistration? = null

    init {
        val id = uid
        if (id != null) {
            // A live listener, not a one-shot get() — avatar/rating changes show up immediately,
            // the same real-time pattern Phase 3's lobby list reuses for live seat counts.
            listener = FirebaseRefs.riders.document(id).addSnapshotListener { snapshot, _ ->
                _rider.value = snapshot?.toObject(Rider::class.java)?.copy(id = snapshot.id)
            }
        }
    }

    fun updateAvatar(avatarId: String, onComplete: () -> Unit = {}) {
        val id = uid ?: return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                FirebaseRefs.riders.document(id).update("avatarId", avatarId).await()
            } finally {
                _isSaving.value = false
            }
            onComplete()
        }
    }

    override fun onCleared() {
        listener?.remove()
    }
}
