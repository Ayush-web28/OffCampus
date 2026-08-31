package com.offcampus.app.data.model

import com.google.firebase.firestore.Exclude

/**
 * Mirrors a document in the top-level "riders" collection.
 * Every field has a default value so Firestore's automatic `toObject()` deserialization
 * works (it needs a no-arg constructor, which Kotlin only generates when all params have defaults).
 */
data class Rider(
    @get:Exclude val id: String = "", // the document's own key is the id, so it's never written as a field
    val name: String = "",
    val email: String = "",
    val avatarId: String = "avatar_1",
    val ratingAverage: Double = 0.0,
    val ratingCount: Int = 0,
    val friendIds: List<String> = emptyList()
)
