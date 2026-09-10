package com.offcampus.app.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.PaymentParticipant
import com.offcampus.app.data.model.PaymentSplit
import com.offcampus.app.data.model.Rider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Views one trip's fare split and drives the two-way acknowledgement. The two ack flags live
 * as fields inside one element of the `participants` array on a single document — Firestore has
 * no way to patch just one array element directly, so every ack goes through [acknowledge], which
 * reads the whole document, rewrites the one participant, and writes the whole array back inside
 * a transaction. That's what keeps two near-simultaneous acks (e.g. the payer confirming receipt
 * at the same moment a different participant marks their own payment sent) from racing each other
 * and silently dropping one of the two writes — a transaction retries automatically on conflict,
 * a plain read-then-set would not.
 */
class PaymentSplitViewModel(private val lobbyId: String) : ViewModel() {
    val currentUid: String? get() = Firebase.auth.currentUser?.uid

    private val _split = MutableStateFlow<PaymentSplit?>(null)
    val split: StateFlow<PaymentSplit?> = _split.asStateFlow()

    private val nameCache = mutableMapOf<String, String>()
    private val _riderNames = MutableStateFlow<Map<String, String>>(emptyMap())
    val riderNames: StateFlow<Map<String, String>> = _riderNames.asStateFlow()

    private var listener: ListenerRegistration? = null

    init {
        listener = FirebaseRefs.paymentSplits.document(lobbyId).addSnapshotListener { snapshot, _ ->
            val loaded = snapshot?.toObject(PaymentSplit::class.java)?.copy(id = snapshot.id)
            _split.value = loaded
            loaded?.let { resolveNames(it) }
        }
    }

    private fun resolveNames(split: PaymentSplit) {
        val unknown = (split.participants.map { it.userId } + split.paidBy)
            .distinct()
            .filter { it !in nameCache }
        if (unknown.isEmpty()) return
        viewModelScope.launch {
            for (id in unknown) {
                nameCache[id] = try {
                    FirebaseRefs.riders.document(id).get().await().toObject(Rider::class.java)?.name
                } catch (e: Exception) {
                    null
                } ?: "Rider"
            }
            _riderNames.value = nameCache.toMap()
        }
    }

    fun markSent(participantUserId: String) = acknowledge(participantUserId) { it.copy(senderAck = true) }
    fun confirmReceived(participantUserId: String) = acknowledge(participantUserId) { it.copy(receiverAck = true) }

    private fun acknowledge(participantUserId: String, applyAck: (PaymentParticipant) -> PaymentParticipant) {
        viewModelScope.launch {
            try {
                val ref = FirebaseRefs.paymentSplits.document(lobbyId)
                Firebase.firestore.runTransaction { transaction ->
                    val current = transaction.get(ref).toObject(PaymentSplit::class.java) ?: return@runTransaction
                    val updated = current.participants.map { participant ->
                        if (participant.userId == participantUserId) {
                            val withAck = applyAck(participant)
                            withAck.copy(settled = withAck.senderAck && withAck.receiverAck)
                        } else {
                            participant
                        }
                    }
                    transaction.update(ref, "participants", updated)
                }.await()
            } catch (e: Exception) {
                // The listener still shows the last-committed state, so the user can just retry.
            }
        }
    }

    override fun onCleared() {
        listener?.remove()
    }
}
