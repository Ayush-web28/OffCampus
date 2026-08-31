package com.offcampus.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

enum class RideType { AUTO, CAB }

enum class LobbyStatus { OPEN, LOCKED, COMPLETED }

/** Mirrors a document in the top-level "lobbies" collection. */
data class Lobby(
    @get:Exclude val id: String = "",
    val checkpoint: String = "",
    val gate: String = "",
    val destination: String = "",
    val departureTime: Timestamp = Timestamp.now(),
    val rideType: RideType = RideType.AUTO,
    val maxSize: Int = 4,
    val memberIds: List<String> = emptyList(),
    val status: LobbyStatus = LobbyStatus.OPEN,
    val createdBy: String = "",
    val createdAt: Timestamp = Timestamp.now()
)
