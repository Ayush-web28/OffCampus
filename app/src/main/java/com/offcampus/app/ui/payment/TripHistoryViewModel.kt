package com.offcampus.app.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.PaymentSplit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class TripHistoryRow(val split: PaymentSplit, val destination: String)

/** A one-shot list, not a live one — past trips don't change once settled, so there's no need
 * to pay for an always-on listener the way the live screens (lobbies, chat) do. */
class TripHistoryViewModel : ViewModel() {
    private val _rows = MutableStateFlow<List<TripHistoryRow>>(emptyList())
    val rows: StateFlow<List<TripHistoryRow>> = _rows.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        val id = Firebase.auth.currentUser?.uid
        if (id == null) {
            _isLoading.value = false
        } else {
            viewModelScope.launch {
                try {
                    val asPayer = FirebaseRefs.paymentSplits.whereEqualTo("paidBy", id).get().await()
                    val asParticipant = FirebaseRefs.paymentSplits.whereArrayContains("participantIds", id).get().await()
                    val splits = (asPayer.documents + asParticipant.documents)
                        .distinctBy { it.id }
                        .mapNotNull { doc -> doc.toObject(PaymentSplit::class.java)?.copy(id = doc.id) }
                        .sortedByDescending { it.createdAt }

                    _rows.value = splits.map { split ->
                        val destination = try {
                            FirebaseRefs.lobbies.document(split.lobbyId).get().await()
                                .toObject(Lobby::class.java)?.destination
                        } catch (e: Exception) {
                            null
                        } ?: "Unknown trip"
                        TripHistoryRow(split, destination)
                    }
                } catch (e: Exception) {
                    _rows.value = emptyList()
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }
}
