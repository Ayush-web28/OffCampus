package com.offcampus.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Exclude

/**
 * One rider's share of a trip's fare, and the two-way acknowledgement that settles it:
 * the ower sets [senderAck] ("payment sent"), the payer sets [receiverAck] ("payment received").
 * [settled] is only ever written as `senderAck && receiverAck` — recomputed client-side inside a
 * Firestore transaction each time either flag changes (see PaymentSplitViewModel.acknowledge),
 * since Phase 9 doesn't have Cloud Functions to do it server-side yet.
 */
data class PaymentParticipant(
    val userId: String = "",
    val owedAmount: Double = 0.0,
    val senderAck: Boolean = false,
    val receiverAck: Boolean = false,
    val settled: Boolean = false
)

/**
 * Mirrors a document in the top-level "paymentSplits" collection, keyed by lobbyId (one split
 * per trip). [participantIds] duplicates the userIds already inside [participants] as a flat
 * list — Firestore can't query "does any element of this array-of-maps have userId == X", so
 * this flat copy is what a "trip history" screen's whereArrayContains query actually runs against.
 */
data class PaymentSplit(
    @get:Exclude val id: String = "",
    val lobbyId: String = "",
    val totalFare: Double = 0.0,
    val paidBy: String = "",
    val participants: List<PaymentParticipant> = emptyList(),
    val participantIds: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now()
)
