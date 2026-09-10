package com.offcampus.app.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.PaymentSplit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * In-app stand-in for the real push notification Phase 9's Cloud Function will send when a
 * payment split is created — same honest scoping as Phase 4's friend-lobby notification: this
 * only fires while the app is open, since there's no server trigger yet.
 */
class PaymentNotificationViewModel : ViewModel() {
    private val uid: String? get() = Firebase.auth.currentUser?.uid

    private val _owedNotification = MutableStateFlow<String?>(null)
    val owedNotification: StateFlow<String?> = _owedNotification.asStateFlow()

    // null until the first snapshot is processed, so we never notify about splits that already
    // existed when the listener started — only ones created afterward.
    private var seenSplitIds: MutableSet<String>? = null
    private var listener: ListenerRegistration? = null

    init {
        val id = uid
        if (id != null) {
            listener = FirebaseRefs.paymentSplits
                .whereArrayContains("participantIds", id)
                .addSnapshotListener { snapshot, _ ->
                    val splits = snapshot?.documents?.mapNotNull { doc ->
                        doc.toObject(PaymentSplit::class.java)?.copy(id = doc.id)
                    } ?: emptyList()

                    val previouslySeen = seenSplitIds
                    if (previouslySeen != null) {
                        val newSplit = splits.firstOrNull { it.id !in previouslySeen }
                        if (newSplit != null) notifyOwed(newSplit, id)
                    }
                    seenSplitIds = splits.map { it.id }.toMutableSet()
                }
        }
    }

    private fun notifyOwed(split: PaymentSplit, myId: String) {
        val myShare = split.participants.firstOrNull { it.userId == myId } ?: return
        viewModelScope.launch {
            val destination = try {
                FirebaseRefs.lobbies.document(split.lobbyId).get().await().toObject(Lobby::class.java)?.destination
            } catch (e: Exception) {
                null
            } ?: "the trip"
            _owedNotification.value = "You owe ₹%.2f for %s".format(myShare.owedAmount, destination)
        }
    }

    fun dismiss() {
        _owedNotification.value = null
    }

    override fun onCleared() {
        listener?.remove()
    }
}
