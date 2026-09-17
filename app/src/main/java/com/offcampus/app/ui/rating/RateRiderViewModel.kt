package com.offcampus.app.ui.rating

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.Rating
import com.offcampus.app.data.model.Rider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class RateRiderFormState(
    val ratedUserName: String = "",
    val ratedUserAvatarId: String = "avatar_1",
    val selectedStars: Int = 0,
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Rates a co-rider from one specific trip. The rating doc's id is deterministic (lobbyId +
 * rater + ratedUser), so [submit] always starts by reading whatever rating already sits at that
 * id — if this rider already rated this person for this trip, submitting again *corrects* the
 * average by the delta between the old and new stars, instead of adding a second vote on top.
 *
 * ratingAverage/ratingCount on the rider doc are a running rollup kept in sync here rather than
 * computed by querying every rating doc on each profile view — same tradeoff Phase 9 already
 * made for payment acks: correctness lives in this client-side transaction (read rating + read
 * rider + write both atomically), not in the security rule, which can only sanity-check the
 * result lands in a plausible range. See firestore.rules' isRatingUpdate() for the honest limit
 * of what that rule can actually verify.
 */
class RateRiderViewModel(
    private val lobbyId: String,
    private val ratedUserId: String
) : ViewModel() {
    private val uid: String? get() = Firebase.auth.currentUser?.uid
    private val ratingDocId: String? get() = uid?.let { "${lobbyId}_${it}_$ratedUserId" }

    private val _formState = MutableStateFlow(RateRiderFormState())
    val formState: StateFlow<RateRiderFormState> = _formState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val rider = FirebaseRefs.riders.document(ratedUserId).get().await()
                    .toObject(Rider::class.java)
                _formState.update {
                    it.copy(
                        ratedUserName = rider?.name ?: "Rider",
                        ratedUserAvatarId = rider?.avatarId ?: "avatar_1",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, errorMessage = "Couldn't load rider details.") }
            }
        }
    }

    fun onStarsSelect(stars: Int) = _formState.update { it.copy(selectedStars = stars, errorMessage = null) }

    fun submit() {
        val myId = uid ?: return
        val docId = ratingDocId ?: return
        val stars = _formState.value.selectedStars
        if (stars !in 1..5) {
            _formState.update { it.copy(errorMessage = "Tap a star to rate this rider.") }
            return
        }

        _formState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val ratingRef = FirebaseRefs.ratings.document(docId)
                val riderRef = FirebaseRefs.riders.document(ratedUserId)

                Firebase.firestore.runTransaction { transaction ->
                    val previousStars = transaction.get(ratingRef).getLong("stars")?.toInt()
                    val rider = transaction.get(riderRef).toObject(Rider::class.java)
                        ?: return@runTransaction

                    // A brand-new rating grows the count by one; correcting an existing one
                    // leaves the count alone and just swaps that rater's old stars for the new.
                    val newCount: Int
                    val newAverage: Double
                    if (previousStars == null) {
                        newCount = rider.ratingCount + 1
                        newAverage = (rider.ratingAverage * rider.ratingCount + stars) / newCount
                    } else {
                        newCount = rider.ratingCount
                        val total = rider.ratingAverage * rider.ratingCount - previousStars + stars
                        newAverage = if (newCount > 0) total / newCount else 0.0
                    }

                    transaction.set(
                        ratingRef,
                        Rating(
                            lobbyId = lobbyId,
                            raterUserId = myId,
                            ratedUserId = ratedUserId,
                            stars = stars,
                            timestamp = Timestamp.now()
                        )
                    )
                    transaction.update(riderRef, mapOf("ratingAverage" to newAverage, "ratingCount" to newCount))
                }.await()

                _formState.update { it.copy(isSubmitting = false, isSubmitted = true) }
            } catch (e: Exception) {
                _formState.update {
                    it.copy(isSubmitting = false, errorMessage = e.localizedMessage ?: "Couldn't submit the rating. Try again.")
                }
            }
        }
    }
}
