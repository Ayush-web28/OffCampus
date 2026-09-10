package com.offcampus.app.ui.payment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldPath
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.LobbyStatus
import com.offcampus.app.data.model.PaymentParticipant
import com.offcampus.app.data.model.PaymentSplit
import com.offcampus.app.data.model.Rider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class SplitMode { EQUAL, CUSTOM }

data class PostFareFormState(
    val destination: String = "",
    val otherMembers: List<Rider> = emptyList(),
    val totalFare: String = "",
    val splitMode: SplitMode = SplitMode.EQUAL,
    val customAmounts: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class PostFareViewModel(private val lobbyId: String) : ViewModel() {
    private val uid: String? get() = Firebase.auth.currentUser?.uid

    private val _formState = MutableStateFlow(PostFareFormState())
    val formState: StateFlow<PostFareFormState> = _formState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val lobby = FirebaseRefs.lobbies.document(lobbyId).get().await().toObject(Lobby::class.java)
                val myId = uid
                val otherIds = lobby?.memberIds.orEmpty().filter { it != myId }
                val others = if (otherIds.isEmpty()) {
                    emptyList()
                } else {
                    FirebaseRefs.riders.whereIn(FieldPath.documentId(), otherIds.take(30))
                        .get().await().documents.mapNotNull { doc ->
                            doc.toObject(Rider::class.java)?.copy(id = doc.id)
                        }
                }
                _formState.update {
                    it.copy(destination = lobby?.destination ?: "", otherMembers = others, isLoading = false)
                }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, errorMessage = "Couldn't load lobby members.") }
            }
        }
    }

    fun onTotalFareChange(value: String) = _formState.update { it.copy(totalFare = value, errorMessage = null) }

    fun onSplitModeChange(mode: SplitMode) {
        _formState.update { state ->
            // Switching to Custom seeds each field with the current equal share, so adjusting
            // means nudging a number that already makes sense rather than starting from blank.
            val seeded = if (mode == SplitMode.CUSTOM) {
                val share = equalShare(state)
                state.otherMembers.associate { it.id to formatAmount(share) }
            } else {
                state.customAmounts
            }
            state.copy(splitMode = mode, customAmounts = seeded)
        }
    }

    fun onCustomAmountChange(userId: String, value: String) = _formState.update {
        it.copy(customAmounts = it.customAmounts + (userId to value), errorMessage = null)
    }

    private fun equalShare(state: PostFareFormState): Double {
        val total = state.totalFare.toDoubleOrNull() ?: return 0.0
        val memberCount = state.otherMembers.size + 1 // +1 for the payer themselves
        if (memberCount == 0) return 0.0
        return roundToPaise(total / memberCount)
    }

    fun submit(onPosted: () -> Unit) {
        val myId = uid ?: return
        val state = _formState.value
        val total = state.totalFare.toDoubleOrNull()

        if (total == null || total <= 0.0) {
            _formState.update { it.copy(errorMessage = "Enter a valid fare amount.") }
            return
        }
        if (state.otherMembers.isEmpty()) {
            _formState.update { it.copy(errorMessage = "No one else is in this lobby to split with.") }
            return
        }

        val participants = state.otherMembers.map { member ->
            val amount = when (state.splitMode) {
                SplitMode.EQUAL -> equalShare(state)
                SplitMode.CUSTOM -> state.customAmounts[member.id]?.toDoubleOrNull() ?: equalShare(state)
            }
            PaymentParticipant(userId = member.id, owedAmount = amount)
        }

        _formState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val split = PaymentSplit(
                    lobbyId = lobbyId,
                    totalFare = total,
                    paidBy = myId,
                    participants = participants,
                    participantIds = participants.map { it.userId },
                    createdAt = Timestamp.now()
                )
                // The split doc's id IS the lobby id — one split per trip, same pattern as chats.
                FirebaseRefs.paymentSplits.document(lobbyId).set(split).await()
                FirebaseRefs.lobbies.document(lobbyId).update("status", LobbyStatus.COMPLETED.name).await()
                onPosted()
            } catch (e: Exception) {
                _formState.update {
                    it.copy(isSubmitting = false, errorMessage = e.localizedMessage ?: "Couldn't post the split. Try again.")
                }
            }
        }
    }
}

private fun roundToPaise(value: Double): Double = kotlin.math.round(value * 100) / 100.0
private fun formatAmount(value: Double): String = "%.2f".format(value)
