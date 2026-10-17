package com.offcampus.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.NotificationService
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

enum class UserRelationshipStatus {
    SELF,
    FRIENDS,
    REQUEST_SENT,
    NOT_FRIENDS,
    BLOCKED
}

data class UserProfileUiState(
    val isLoading: Boolean = true,
    val targetRider: Rider? = null,
    val relationshipStatus: UserRelationshipStatus = UserRelationshipStatus.NOT_FRIENDS,
    val isActionInProgress: Boolean = false,
    val feedbackMessage: String? = null,
    val errorMessage: String? = null
)

class UserProfileViewModel(private val targetUserId: String) : ViewModel() {
    private val myUid: String? get() = Firebase.auth.currentUser?.uid

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    private var messageDismissJob: Job? = null

    init {
        loadProfile()
    }

    fun loadProfile() {
        val uid = myUid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val targetDoc = FirebaseRefs.riders.document(targetUserId).get().await()
                val targetRider = targetDoc.toObject(Rider::class.java)?.copy(id = targetDoc.id)

                if (targetRider == null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "User not found") }
                    return@launch
                }

                if (uid == targetUserId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            targetRider = targetRider,
                            relationshipStatus = UserRelationshipStatus.SELF
                        )
                    }
                    return@launch
                }

                val myDoc = FirebaseRefs.riders.document(uid).get().await()
                val myRider = myDoc.toObject(Rider::class.java) ?: Rider()

                val isBlocked = myRider.blockedUserIds.contains(targetUserId)
                val isFriend = myRider.friendIds.contains(targetUserId)

                val relationship = when {
                    isBlocked -> UserRelationshipStatus.BLOCKED
                    isFriend -> UserRelationshipStatus.FRIENDS
                    else -> {
                        val outgoingReq = FirebaseRefs.friendRequests
                            .whereEqualTo("fromUserId", uid)
                            .whereEqualTo("toUserId", targetUserId)
                            .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                            .limit(1)
                            .get().await()

                        if (!outgoingReq.isEmpty) {
                            UserRelationshipStatus.REQUEST_SENT
                        } else {
                            UserRelationshipStatus.NOT_FRIENDS
                        }
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        targetRider = targetRider,
                        relationshipStatus = relationship
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.localizedMessage ?: "Failed to load profile") }
            }
        }
    }

    fun sendFriendRequest() {
        val uid = myUid ?: return
        val target = _uiState.value.targetRider ?: return
        _uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val existing = FirebaseRefs.friendRequests
                    .whereEqualTo("fromUserId", uid)
                    .whereEqualTo("toUserId", target.id)
                    .whereEqualTo("status", FriendRequestStatus.PENDING.name)
                    .get().await()

                if (existing.isEmpty) {
                    FirebaseRefs.friendRequests.add(
                        FriendRequest(fromUserId = uid, toUserId = target.id, status = FriendRequestStatus.PENDING)
                    ).await()
                    try {
                        val myDoc = FirebaseRefs.riders.document(uid).get().await()
                        val myName = myDoc.toObject(Rider::class.java)?.name?.ifBlank { null } ?: "Someone"
                        NotificationService.notifyFriendRequest(target.id, myName)
                    } catch (_: Exception) {}
                }

                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        relationshipStatus = UserRelationshipStatus.REQUEST_SENT,
                        feedbackMessage = "Friend request sent to ${target.name}."
                    )
                }
                scheduleMessageDismiss()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isActionInProgress = false, errorMessage = e.localizedMessage ?: "Could not send friend request.")
                }
                scheduleMessageDismiss()
            }
        }
    }

    fun unfriend() {
        val uid = myUid ?: return
        val target = _uiState.value.targetRider ?: return
        _uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                FirebaseRefs.riders.document(uid)
                    .update("friendIds", FieldValue.arrayRemove(target.id)).await()

                try {
                    FirebaseRefs.riders.document(target.id)
                        .update("friendIds", FieldValue.arrayRemove(uid)).await()
                } catch (_: Exception) {}

                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        relationshipStatus = UserRelationshipStatus.NOT_FRIENDS,
                        feedbackMessage = "Removed ${target.name} from friends."
                    )
                }
                scheduleMessageDismiss()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isActionInProgress = false, errorMessage = e.localizedMessage ?: "Could not unfriend.")
                }
                scheduleMessageDismiss()
            }
        }
    }

    fun blockUser() {
        val uid = myUid ?: return
        val target = _uiState.value.targetRider ?: return
        _uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                FirebaseRefs.riders.document(uid)
                    .update("blockedUserIds", FieldValue.arrayUnion(target.id)).await()

                try {
                    FirebaseRefs.riders.document(uid)
                        .update("friendIds", FieldValue.arrayRemove(target.id)).await()
                    FirebaseRefs.riders.document(target.id)
                        .update("friendIds", FieldValue.arrayRemove(uid)).await()
                } catch (_: Exception) {}

                try {
                    val outgoing = FirebaseRefs.friendRequests
                        .whereEqualTo("fromUserId", uid).whereEqualTo("toUserId", target.id)
                        .get().await()
                    for (doc in outgoing.documents) {
                        doc.reference.delete().await()
                    }
                    val incoming = FirebaseRefs.friendRequests
                        .whereEqualTo("fromUserId", target.id).whereEqualTo("toUserId", uid)
                        .get().await()
                    for (doc in incoming.documents) {
                        doc.reference.delete().await()
                    }
                } catch (_: Exception) {}

                try {
                    val myDoc = FirebaseRefs.riders.document(uid).get().await()
                    val myName = myDoc.toObject(Rider::class.java)?.name?.ifBlank { null } ?: "Someone"
                    NotificationService.notifyUserBlocked(target.id, myName)
                } catch (_: Exception) {}

                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        relationshipStatus = UserRelationshipStatus.BLOCKED,
                        feedbackMessage = "${target.name} has been blocked."
                    )
                }
                scheduleMessageDismiss()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isActionInProgress = false, errorMessage = e.localizedMessage ?: "Could not block user.")
                }
                scheduleMessageDismiss()
            }
        }
    }

    fun unblockUser() {
        val uid = myUid ?: return
        val target = _uiState.value.targetRider ?: return
        _uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                FirebaseRefs.riders.document(uid)
                    .update("blockedUserIds", FieldValue.arrayRemove(target.id)).await()

                _uiState.update {
                    it.copy(
                        isActionInProgress = false,
                        relationshipStatus = UserRelationshipStatus.NOT_FRIENDS,
                        feedbackMessage = "${target.name} has been unblocked."
                    )
                }
                scheduleMessageDismiss()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isActionInProgress = false, errorMessage = e.localizedMessage ?: "Could not unblock user.")
                }
                scheduleMessageDismiss()
            }
        }
    }

    private fun scheduleMessageDismiss() {
        messageDismissJob?.cancel()
        messageDismissJob = viewModelScope.launch {
            delay(2000L)
            _uiState.update { it.copy(feedbackMessage = null, errorMessage = null) }
        }
    }

    override fun onCleared() {
        messageDismissJob?.cancel()
    }
}
