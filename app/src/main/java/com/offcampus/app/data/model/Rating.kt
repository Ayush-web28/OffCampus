package com.offcampus.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

/**
 * Mirrors a document in the top-level "ratings" collection. Doc id is deterministic —
 * "{lobbyId}_{raterUserId}_{ratedUserId}" — so rating the same person twice for the same trip
 * corrects the existing rating instead of creating a duplicate that would double-count in the
 * rated rider's average.
 */
data class Rating(
    @get:Exclude val id: String = "",
    val lobbyId: String = "",
    val raterUserId: String = "",
    val ratedUserId: String = "",
    val stars: Int = 0,
    val timestamp: Timestamp = Timestamp.now()
)
