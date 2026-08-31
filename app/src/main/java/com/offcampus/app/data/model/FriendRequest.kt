package com.offcampus.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

enum class FriendRequestStatus { PENDING, ACCEPTED }

/** Mirrors a document in the top-level "friendRequests" collection. */
data class FriendRequest(
    @get:Exclude val id: String = "",
    val fromUserId: String = "",
    val toUserId: String = "",
    val status: FriendRequestStatus = FriendRequestStatus.PENDING,
    val createdAt: Timestamp = Timestamp.now()
)
