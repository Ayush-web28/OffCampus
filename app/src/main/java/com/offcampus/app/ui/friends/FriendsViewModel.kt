package com.offcampus.app.ui.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.FriendRequest
import com.offcampus.app.data.model.FriendRequestStatus
import com.offcampus.app.data.model.Rider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class IncomingRequest(val request: FriendRequest, val fromRider: Rider)

data class AddFriendState(
    val email: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class FriendsViewModel : ViewModel() {
    private val uid: String? get() = Firebase.auth.currentUser?.uid

    private val _friends = MutableStateFlow<List<Rider>>(emptyList())
    val friends: StateFlow<List<Rider>> = _friends.asStateFlow()

    private val _incomingRequests = MutableStateFlow<List<IncomingRequest>>(emptyList())
    val incomingRequests: StateFlow<List<IncomingRequest>> = _incomingRequests.asStateFlow()

    private val _addFriendState = MutableStateFlow(AddFriendState())
    val addFriendState: StateFlow<AddFriendState> = _addFriendState.asStateFlow()

    private var riderListener: ListenerRegistration? = null
    private var incomingListener: ListenerRegistration? = null

    init {
        val id = uid
        if (id != null) {
            // Friends themselves are read from MY rider doc's friendIds — re-resolved into full
            // Rider objects whenever that array changes, rather than a live listener per friend.
            riderListener = FirebaseRefs.riders.document(id).addSnapshotListener { snapshot, _ ->
                val friendIds = snapshot?.toObject(Rider::class.java)?.friendIds ?: emptyList()
                loadFriends(friendIds)
            }
            incomingListener = FirebaseRefs.friendRequests
                .whereEqualTo("toUserId", id)
                .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                .addSnapshotListener { snapshot, _ ->
                    val requests = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(FriendRequest::class.java)?.copy(id = doc.id)
                    } ?: emptyList()
                    loadIncoming(requests)
                }
        }
    }

    private fun loadFriends(friendIds: List<String>) {
        if (friendIds.isEmpty()) {
            _friends.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                // whereIn caps at 30 values — plenty for a college friend list.
                val snapshot = FirebaseRefs.riders
                    .whereIn(FieldPath.documentId(), friendIds.take(30))
                    .get().await()
                _friends.value = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Rider::class.java)?.copy(id = doc.id)
                }
            } catch (e: Exception) {
                _friends.value = emptyList()
            }
        }
    }

    private fun loadIncoming(requests: List<FriendRequest>) {
        viewModelScope.launch {
            _incomingRequests.value = requests.mapNotNull { request ->
                try {
                    val riderDoc = FirebaseRefs.riders.document(request.fromUserId).get().await()
                    riderDoc.toObject(Rider::class.java)?.copy(id = riderDoc.id)
                        ?.let { IncomingRequest(request, it) }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun onEmailChange(value: String) = _addFriendState.update {
        it.copy(email = value, errorMessage = null, successMessage = null)
    }

    fun sendRequest() {
        val myId = uid ?: return
        val email = _addFriendState.value.email.trim()
        if (email.isBlank()) {
            _addFriendState.update { it.copy(errorMessage = "Enter a college email.") }
            return
        }

        _addFriendState.update { it.copy(isSubmitting = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            try {
                val targetDoc = FirebaseRefs.riders.whereEqualTo("email", email).limit(1)
                    .get().await().documents.firstOrNull()

                if (targetDoc == null) {
                    fail("No rider found with that email.")
                    return@launch
                }
                val targetId = targetDoc.id
                if (targetId == myId) {
                    fail("That's you!")
                    return@launch
                }

                val myRider = FirebaseRefs.riders.document(myId).get().await().toObject(Rider::class.java)
                if (myRider?.friendIds?.contains(targetId) == true) {
                    fail("You're already friends.")
                    return@launch
                }

                val friendRequests = FirebaseRefs.friendRequests
                val alreadyOutgoing = friendRequests
                    .whereEqualTo("fromUserId", myId).whereEqualTo("toUserId", targetId)
                    .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                    .get().await()
                val alreadyIncoming = friendRequests
                    .whereEqualTo("fromUserId", targetId).whereEqualTo("toUserId", myId)
                    .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                    .get().await()
                if (!alreadyOutgoing.isEmpty || !alreadyIncoming.isEmpty) {
                    fail("A request is already pending.")
                    return@launch
                }

                friendRequests.add(
                    FriendRequest(fromUserId = myId, toUserId = targetId, status = FriendRequestStatus.PENDING)
                ).await()
                _addFriendState.value = AddFriendState(successMessage = "Friend request sent.")
            } catch (e: Exception) {
                fail(e.localizedMessage ?: "Couldn't send that request. Try again.")
            }
        }
    }

    private fun fail(message: String) = _addFriendState.update { it.copy(isSubmitting = false, errorMessage = message) }

    fun accept(incoming: IncomingRequest) {
        val myId = uid ?: return
        viewModelScope.launch {
            try {
                FirebaseRefs.friendRequests.document(incoming.request.id)
                    .update("status", FriendRequestStatus.ACCEPTED.name).await()
                // Both sides of the friendship are written from whichever client accepts —
                // there's no Cloud Function yet (that's Phase 9), so this has to be a two-write client update.
                FirebaseRefs.riders.document(myId)
                    .update("friendIds", FieldValue.arrayUnion(incoming.request.fromUserId)).await()
                FirebaseRefs.riders.document(incoming.request.fromUserId)
                    .update("friendIds", FieldValue.arrayUnion(myId)).await()
            } catch (e: Exception) {
                // Best-effort: the incoming list is live, so a failed accept just leaves the
                // request pending and the user can retry.
            }
        }
    }

    fun decline(incoming: IncomingRequest) {
        viewModelScope.launch {
            try {
                FirebaseRefs.friendRequests.document(incoming.request.id).delete().await()
            } catch (e: Exception) {
                // Ignore — the request stays visible and the user can retry.
            }
        }
    }

    override fun onCleared() {
        riderListener?.remove()
        incomingListener?.remove()
    }
}
