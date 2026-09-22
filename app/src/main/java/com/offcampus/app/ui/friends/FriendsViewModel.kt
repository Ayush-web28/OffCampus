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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class IncomingRequest(val request: FriendRequest, val fromRider: Rider)

data class AddFriendState(
    val query: String = "",
    val searchResults: List<Rider> = emptyList(),
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

// Firestore has no "contains" text search — a range on a single field only ever matches a
// prefix (name/email starting with the typed text), the same trick used elsewhere in this
// project. '' is a private-use-area character that sorts after virtually anything, so
// [prefix, prefix + '') catches every string that starts with prefix.
private fun String.asPrefixRange() = this to this + ''

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
    private var searchJob: Job? = null

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

    fun onQueryChange(value: String) {
        _addFriendState.update { it.copy(query = value, errorMessage = null, successMessage = null) }

        searchJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.length < 2) {
            _addFriendState.update { it.copy(searchResults = emptyList()) }
            return
        }
        // A short debounce so a fast typist doesn't fire a Firestore query per keystroke —
        // each new character cancels the previous pending search via searchJob.
        searchJob = viewModelScope.launch {
            delay(300)
            search(trimmed)
        }
    }

    private suspend fun search(prefix: String) {
        val myId = uid
        try {
            val (start, end) = prefix.asPrefixRange()
            // Firestore can't OR across two different fields in one query, so this is two
            // range queries — one against name, one against email — merged and de-duplicated
            // by document id below.
            val byName = FirebaseRefs.riders.orderBy("name").startAt(start).endAt(end)
                .limit(10).get().await()
            val byEmail = FirebaseRefs.riders.orderBy("email").startAt(start).endAt(end)
                .limit(10).get().await()

            val myFriendIds = _friends.value.map { it.id }.toSet()
            val results = (byName.documents + byEmail.documents)
                .distinctBy { it.id }
                .mapNotNull { doc -> doc.toObject(Rider::class.java)?.copy(id = doc.id) }
                // Nothing useful about suggesting yourself or someone you're already friends with.
                .filter { it.id != myId && it.id !in myFriendIds }
                .take(10)

            _addFriendState.update { it.copy(searchResults = results) }
        } catch (e: Exception) {
            // A failed search just leaves the list as-is — nothing to retry, the user can keep typing.
        }
    }

    fun sendRequestTo(target: Rider) {
        val myId = uid ?: return
        _addFriendState.update { it.copy(isSubmitting = true, errorMessage = null, successMessage = null) }
        viewModelScope.launch {
            try {
                val myRider = FirebaseRefs.riders.document(myId).get().await().toObject(Rider::class.java)
                if (myRider?.friendIds?.contains(target.id) == true) {
                    fail("You're already friends.")
                    return@launch
                }

                val friendRequests = FirebaseRefs.friendRequests
                val alreadyOutgoing = friendRequests
                    .whereEqualTo("fromUserId", myId).whereEqualTo("toUserId", target.id)
                    .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                    .get().await()
                val alreadyIncoming = friendRequests
                    .whereEqualTo("fromUserId", target.id).whereEqualTo("toUserId", myId)
                    .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                    .get().await()
                if (!alreadyOutgoing.isEmpty || !alreadyIncoming.isEmpty) {
                    fail("A request is already pending.")
                    return@launch
                }

                friendRequests.add(
                    FriendRequest(fromUserId = myId, toUserId = target.id, status = FriendRequestStatus.PENDING)
                ).await()
                _addFriendState.value = AddFriendState(successMessage = "Friend request sent to ${target.name}.")
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
