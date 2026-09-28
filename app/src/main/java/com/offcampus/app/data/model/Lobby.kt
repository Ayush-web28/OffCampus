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
    // Set only when the trip was posted by tapping a place suggestion (Fix 16) rather than
    // typing free text — null is the normal case for anything Photon didn't have or the rider
    // never searched for. Kept here (not computed later) since it's a free byproduct of the
    // suggestion the rider already picked, ready for area/proximity-based matching later.
    val gateLat: Double? = null,
    val gateLng: Double? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    val departureTime: Timestamp = Timestamp.now(),
    val rideType: RideType = RideType.AUTO,
    val maxSize: Int = 4,
    val memberIds: List<String> = emptyList(),
    val status: LobbyStatus = LobbyStatus.OPEN,
    val createdBy: String = "",
    val createdAt: Timestamp = Timestamp.now()
)
