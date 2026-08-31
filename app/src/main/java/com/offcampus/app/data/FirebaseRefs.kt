package com.offcampus.app.data

import com.google.firebase.Firebase
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.firestore

/**
 * Centralizes Firestore collection references so screens never hardcode collection-name
 * strings themselves — a typo in a hardcoded "lobies" would otherwise be a silent runtime bug
 * (an empty query) rather than a compile error.
 */
object FirebaseRefs {
    private val db get() = Firebase.firestore

    val riders: CollectionReference get() = db.collection("riders")
    val lobbies: CollectionReference get() = db.collection("lobbies")
    val friendRequests: CollectionReference get() = db.collection("friendRequests")
    val chats: CollectionReference get() = db.collection("chats")
    val paymentSplits: CollectionReference get() = db.collection("paymentSplits")
    val reports: CollectionReference get() = db.collection("reports")
}
