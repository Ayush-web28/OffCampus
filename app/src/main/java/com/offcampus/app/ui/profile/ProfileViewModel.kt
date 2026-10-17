package com.offcampus.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.PhotoService
import com.offcampus.app.data.model.Rider
import com.offcampus.app.data.observeSignedInUid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProfileViewModel : ViewModel() {
    private val uid: String? get() = Firebase.auth.currentUser?.uid

    private val _rider = MutableStateFlow<Rider?>(null)
    val rider: StateFlow<Rider?> = _rider.asStateFlow()

    private val _blockedRiders = MutableStateFlow<List<Rider>>(emptyList())
    val blockedRiders: StateFlow<List<Rider>> = _blockedRiders.asStateFlow()

    private val _isUnblocking = MutableStateFlow(false)
    val isUnblocking: StateFlow<Boolean> = _isUnblocking.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private var listener: ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null

    init {
        // See observeSignedInUid's doc — a one-shot `val id = uid` here used to keep showing the
        // previous account's profile after switching accounts mid-session.
        authListener = observeSignedInUid(::attachListener)
    }

    private fun attachListener(id: String?) {
        listener?.remove()
        _rider.value = null
        _blockedRiders.value = emptyList()
        if (id == null) return
        // A live listener, not a one-shot get() — avatar/rating changes show up immediately,
        // the same real-time pattern Phase 3's lobby list reuses for live seat counts.
        listener = FirebaseRefs.riders.document(id).addSnapshotListener { snapshot, _ ->
            val r = snapshot?.toObject(Rider::class.java)?.copy(id = snapshot.id)
            _rider.value = r
            loadBlockedRiders(r?.blockedUserIds ?: emptyList())
        }
    }

    private fun loadBlockedRiders(blockedIds: List<String>) {
        if (blockedIds.isEmpty()) {
            _blockedRiders.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val list = mutableListOf<Rider>()
                for (chunk in blockedIds.chunked(30)) {
                    val snapshot = FirebaseRefs.riders
                        .whereIn(FieldPath.documentId(), chunk)
                        .get().await()
                    list.addAll(snapshot.documents.mapNotNull { doc ->
                        doc.toObject(Rider::class.java)?.copy(id = doc.id)
                    })
                }
                _blockedRiders.value = list
            } catch (_: Exception) {
                val list = mutableListOf<Rider>()
                for (bId in blockedIds) {
                    try {
                        val doc = FirebaseRefs.riders.document(bId).get().await()
                        doc.toObject(Rider::class.java)?.copy(id = doc.id)?.let { list.add(it) }
                    } catch (_: Exception) {}
                }
                _blockedRiders.value = list
            }
        }
    }

    fun unblockUser(blockedUserId: String) {
        val id = uid ?: return
        viewModelScope.launch {
            _isUnblocking.value = true
            try {
                FirebaseRefs.riders.document(id)
                    .update("blockedUserIds", FieldValue.arrayRemove(blockedUserId)).await()
                _blockedRiders.value = _blockedRiders.value.filter { it.id != blockedUserId }
            } catch (_: Exception) {
            } finally {
                _isUnblocking.value = false
            }
        }
    }

    // Shown by the picker when an upload or save fails (no network, Worker down, ...) — a photo
    // upload is a real network call to a separate service, so unlike a plain Firestore write it
    // has genuine ways to fail that the rider needs to be told about.
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Picking a catalog avatar and uploading a photo are mutually exclusive — choosing one
    // always clears the other, rather than the two silently coexisting with AvatarView just
    // picking a winner (which is confusing: the field you didn't touch looks like it "reset").
    fun updateAvatar(avatarId: String, onComplete: () -> Unit = {}) = saveWith(onComplete) { id ->
        val hadUploadedPhoto = _rider.value?.photoUrl?.isNotBlank() == true
        FirebaseRefs.riders.document(id)
            .update(mapOf("avatarId" to avatarId, "photoUrl" to "", "photoBase64" to "")).await()
        // Free the stored file too. Best-effort — the profile is already switched over, so a
        // failed cleanup just leaves an orphaned file behind, not a broken profile.
        if (hadUploadedPhoto) runCatching { PhotoService.delete() }
    }

    fun uploadPhoto(jpeg: ByteArray, onComplete: () -> Unit = {}) = saveWith(onComplete) { id ->
        val url = PhotoService.upload(jpeg)
        FirebaseRefs.riders.document(id).update(mapOf("photoUrl" to url, "photoBase64" to "")).await()
    }

    private fun saveWith(onComplete: () -> Unit, block: suspend (String) -> Unit) {
        val id = uid ?: return
        viewModelScope.launch {
            _isSaving.value = true
            _error.value = null
            try {
                block(id)
                onComplete()
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Couldn't save that. Try again."
            } finally {
                _isSaving.value = false
            }
        }
    }

    override fun onCleared() {
        authListener?.let { Firebase.auth.removeAuthStateListener(it) }
        listener?.remove()
    }
}
