package com.offcampus.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

enum class ReportStatus { OPEN, REVIEWED }

/** Mirrors a document in the top-level "reports" collection. */
data class Report(
    @get:Exclude val id: String = "",
    val reportedBy: String = "",
    val reportedUser: String = "",
    val lobbyId: String = "",
    val amount: Double = 0.0,
    val reason: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val status: ReportStatus = ReportStatus.OPEN
)
