package com.offcampus.app.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.PaymentSplit
import com.offcampus.app.data.model.Report
import com.offcampus.app.data.model.ReportStatus
import com.offcampus.app.data.model.Rider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * A small fixed set of reasons, shown as chips so most reports are a single tap. OTHER swaps
 * in a free-text field for anything the fixed set can't describe.
 */
enum class ReportReason(val label: String) {
    NO_SHOW("No-show"),
    DIDNT_PAY("Didn't pay"),
    INAPPROPRIATE("Inappropriate behavior"),
    OTHER("Other")
}

data class ReportFormState(
    val reportedUserName: String = "",
    val reportedUserAvatarId: String = "avatar_1",
    val disputedAmount: Double = 0.0,
    val selectedReason: ReportReason? = null,
    val customReason: String = "",
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Loads just enough context to show who's being reported and for how much, then writes one
 * `reports` document. There's no moderation screen anywhere in this app — [ReportStatus] starts
 * at OPEN and stays there from the app's point of view; REVIEWED only exists as a field a real
 * backend team could set directly in Firestore, which is out of scope here.
 */
class ReportViewModel(
    private val lobbyId: String,
    private val reportedUserId: String
) : ViewModel() {
    private val uid: String? get() = Firebase.auth.currentUser?.uid

    private val _formState = MutableStateFlow(ReportFormState())
    val formState: StateFlow<ReportFormState> = _formState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val rider = FirebaseRefs.riders.document(reportedUserId).get().await()
                    .toObject(Rider::class.java)

                // The disputed amount pre-fills from this trip's split rather than being a field
                // the reporter can type into — otherwise anyone could inflate a dispute past what
                // the split actually says this person owes.
                val split = FirebaseRefs.paymentSplits.document(lobbyId).get().await()
                    .toObject(PaymentSplit::class.java)
                val owed = split?.participants?.firstOrNull { it.userId == reportedUserId }?.owedAmount ?: 0.0

                _formState.update {
                    it.copy(
                        reportedUserName = rider?.name ?: "Rider",
                        reportedUserAvatarId = rider?.avatarId ?: "avatar_1",
                        disputedAmount = owed,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, errorMessage = "Couldn't load rider details.") }
            }
        }
    }

    fun onReasonSelect(reason: ReportReason) = _formState.update {
        it.copy(selectedReason = reason, errorMessage = null)
    }

    fun onCustomReasonChange(value: String) = _formState.update {
        it.copy(customReason = value, errorMessage = null)
    }

    fun submit() {
        val myId = uid ?: return
        val state = _formState.value
        val reason = state.selectedReason
        if (reason == null) {
            _formState.update { it.copy(errorMessage = "Pick a reason for this report.") }
            return
        }
        val finalReason = if (reason == ReportReason.OTHER) state.customReason.trim() else reason.label
        if (finalReason.isEmpty()) {
            _formState.update { it.copy(errorMessage = "Describe what happened.") }
            return
        }

        _formState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val report = Report(
                    reportedBy = myId,
                    reportedUser = reportedUserId,
                    lobbyId = lobbyId,
                    amount = state.disputedAmount,
                    reason = finalReason,
                    timestamp = Timestamp.now(),
                    status = ReportStatus.OPEN
                )
                // add() rather than a fixed doc id: unlike paymentSplits (one per trip) or chats
                // (one per lobby/friend pair), the same rider could be reported more than once on
                // the same trip, so each report needs its own auto-generated id.
                FirebaseRefs.reports.add(report).await()
                _formState.update { it.copy(isSubmitting = false, isSubmitted = true) }
            } catch (e: Exception) {
                _formState.update {
                    it.copy(isSubmitting = false, errorMessage = e.localizedMessage ?: "Couldn't submit the report. Try again.")
                }
            }
        }
    }
}
