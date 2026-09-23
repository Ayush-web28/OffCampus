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
    // URL of the rider's uploaded photo, served by the Cloudflare Worker (worker/). Empty means
    // "no custom photo, show the avatarId catalog entry instead"; AvatarView prefers this over
    // both photoBase64 and avatarId whenever it's non-blank.
    val photoUrl: String = "",
    // Legacy: photos uploaded before the Worker existed were stored as a small base64 JPEG right
    // on this document. Still displayed if present so nobody's photo vanishes, but nothing writes
    // it anymore — the next photo change replaces it with photoUrl.
    val photoBase64: String = "",
    val ratingAverage: Double = 0.0,
    val ratingCount: Int = 0,
    val friendIds: List<String> = emptyList()
)
