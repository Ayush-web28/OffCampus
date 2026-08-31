package com.offcampus.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

/**
 * Mirrors a document inside a chat's "messages" subcollection. The parent "chats" document
 * (either a lobby's chat or a persistent friend-to-friend thread) is created in Phase 5, once
 * there's an actual chat list screen that needs its metadata — Phase 1 only needs the shape
 * of a single message, since that's what the data model brief calls out explicitly.
 */
data class ChatMessage(
    @get:Exclude val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now()
)
