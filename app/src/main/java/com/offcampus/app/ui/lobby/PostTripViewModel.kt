package com.offcampus.app.ui.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.PlaceSearchService
import com.offcampus.app.data.PlaceSuggestion
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.LobbyStatus
import com.offcampus.app.data.model.PaymentSplit
import com.offcampus.app.data.model.RideType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

private fun defaultDepartureTime(): Date = Calendar.getInstance().apply {
    add(Calendar.MINUTE, 30)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.time

// A real auto-rickshaw is licensed for 3 passengers, full stop — a cab (sedan/SUV) has real
// room to flex, so only auto gets the tighter cap. Keyed off ride type rather than one fixed
// range for every trip.
fun groupSizeOptions(rideType: RideType): IntRange = if (rideType == RideType.AUTO) 2..3 else 2..6

data class PostTripFormState(
    val checkpoint: String = "",
    val gate: String = "",
    val destination: String = "",
    // Set only when the rider taps a suggestion, cleared the moment they type again — a
    // half-edited field shouldn't silently keep posting a previous suggestion's coordinates.
    val gateLat: Double? = null,
    val gateLng: Double? = null,
    val destinationLat: Double? = null,
    val destinationLng: Double? = null,
    val gateSuggestions: List<PlaceSuggestion> = emptyList(),
    val destinationSuggestions: List<PlaceSuggestion> = emptyList(),
    // True only while a Photon request for that field is actually in flight (after the 300ms
    // debounce, not during it) — lets the field show a spinner instead of just sitting there
    // looking unresponsive for however long the request takes.
    val gateSearching: Boolean = false,
    val destinationSearching: Boolean = false,
    val departureTime: Date = defaultDepartureTime(),
    val maxSize: Int = 3,
    val rideType: RideType = RideType.AUTO,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class PostTripViewModel : ViewModel() {
    private val _formState = MutableStateFlow(PostTripFormState())
    val formState: StateFlow<PostTripFormState> = _formState.asStateFlow()

    private var gateSearchJob: Job? = null
    private var destinationSearchJob: Job? = null

    init {
        // Fired the instant this screen opens, while the rider is still on Checkpoint — pays
        // Photon's cold-connection cost in the background so it's (hopefully) already paid by
        // the time they reach Gate or Destination. See PlaceSearchService.warmUp()'s own doc.
        viewModelScope.launch { PlaceSearchService.warmUp() }
        viewModelScope.launch {
            val uid = Firebase.auth.currentUser?.uid ?: return@launch
            try {
                val pendingSplits = FirebaseRefs.paymentSplits
                    .whereArrayContains("participantIds", uid)
                    .get().await()
                    .documents
                    .mapNotNull { doc -> doc.toObject(PaymentSplit::class.java) }
                    .filter { split -> split.participants.any { it.userId == uid && !it.settled } }

                if (pendingSplits.isNotEmpty()) {
                    _formState.update {
                        it.copy(
                            errorMessage = "You have an unsettled payment for a previous ride. Please clear it before creating a new lobby."
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun onCheckpointChange(value: String) = _formState.update { it.copy(checkpoint = value, errorMessage = null) }

    fun onGateChange(value: String) {
        _formState.update { it.copy(gate = value, gateLat = null, gateLng = null, gateSearching = false, errorMessage = null) }
        gateSearchJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            _formState.update { it.copy(gateSuggestions = emptyList()) }
            return
        }
        // Immediately display matching campus/local presets without waiting for network
        val immediateLocal = PlaceSearchService.searchLocalPresets(trimmed)
        if (immediateLocal.isNotEmpty()) {
            _formState.update { it.copy(gateSuggestions = immediateLocal) }
        }
        gateSearchJob = viewModelScope.launch {
            delay(300)
            _formState.update { it.copy(gateSearching = true) }
            val results = PlaceSearchService.search(trimmed)
            _formState.update { it.copy(gateSuggestions = results, gateSearching = false) }
        }
    }

    fun onDestinationChange(value: String) {
        _formState.update {
            it.copy(destination = value, destinationLat = null, destinationLng = null, destinationSearching = false, errorMessage = null)
        }
        destinationSearchJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            _formState.update { it.copy(destinationSuggestions = emptyList()) }
            return
        }
        // Immediately display matching campus/transit presets without waiting for network
        val immediateLocal = PlaceSearchService.searchLocalPresets(trimmed)
        if (immediateLocal.isNotEmpty()) {
            _formState.update { it.copy(destinationSuggestions = immediateLocal) }
        }
        destinationSearchJob = viewModelScope.launch {
            delay(300)
            _formState.update { it.copy(destinationSearching = true) }
            val results = PlaceSearchService.search(trimmed)
            _formState.update { it.copy(destinationSuggestions = results, destinationSearching = false) }
        }
    }

    fun onGateSuggestionSelected(suggestion: PlaceSuggestion) {
        gateSearchJob?.cancel()
        _formState.update {
            it.copy(gate = suggestion.name, gateLat = suggestion.lat, gateLng = suggestion.lng, gateSuggestions = emptyList())
        }
    }

    fun onDestinationSuggestionSelected(suggestion: PlaceSuggestion) {
        destinationSearchJob?.cancel()
        _formState.update {
            it.copy(
                destination = suggestion.name,
                destinationLat = suggestion.lat,
                destinationLng = suggestion.lng,
                destinationSuggestions = emptyList()
            )
        }
    }

    fun onDepartureTimeSelected(hourOfDay: Int, minute: Int) {
        val now = Calendar.getInstance()
        val target = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hourOfDay)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // If the selected time has already passed today (with a 1-minute buffer), assume tomorrow
            if (timeInMillis < now.timeInMillis - 60_000L) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        _formState.update { it.copy(departureTime = target.time) }
    }

    fun onMaxSizeChange(size: Int) = _formState.update { it.copy(maxSize = size) }

    // Switching to Auto while a larger Cab-only group size is selected clamps it back down to
    // 3 instead of leaving the form in a state the UI's own chips no longer offer.
    fun onRideTypeChange(type: RideType) = _formState.update {
        val range = groupSizeOptions(type)
        it.copy(rideType = type, maxSize = it.maxSize.coerceIn(range.first, range.last))
    }

    fun submit(onPosted: () -> Unit) {
        val form = _formState.value
        val uid = Firebase.auth.currentUser?.uid ?: return

        if (form.checkpoint.isBlank() || form.gate.isBlank() || form.destination.isBlank()) {
            _formState.update { it.copy(errorMessage = "Fill in checkpoint, location and destination.") }
            return
        }
        // Belt-and-suspenders: the chips never offer an out-of-range value, but this guards
        // the actual write in case that ever changes without this check being updated too.
        if (form.maxSize !in groupSizeOptions(form.rideType)) {
            _formState.update { it.copy(errorMessage = "An auto can't seat more than 3 — pick a smaller group or switch to Cab.") }
            return
        }

        _formState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                // Guard: Prevent posting if user has an unsettled payment for a previous ride
                val pendingSplits = FirebaseRefs.paymentSplits
                    .whereArrayContains("participantIds", uid)
                    .get().await()
                    .documents
                    .mapNotNull { doc -> doc.toObject(PaymentSplit::class.java) }
                    .filter { split -> split.participants.any { it.userId == uid && !it.settled } }

                if (pendingSplits.isNotEmpty()) {
                    _formState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "You have an unsettled payment for a previous ride. Please clear it before creating a new lobby."
                        )
                    }
                    return@launch
                }

                // Guard: Prevent posting if user already has an active lobby (OPEN or LOCKED)
                val activeLobbies = FirebaseRefs.lobbies
                    .whereArrayContains("memberIds", uid)
                    .get().await()
                    .documents
                    .mapNotNull { doc -> doc.toObject(Lobby::class.java)?.copy(id = doc.id) }
                    .filter { it.status == LobbyStatus.OPEN || it.status == LobbyStatus.LOCKED }

                if (activeLobbies.isNotEmpty()) {
                    _formState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = "You already have an active lobby. Please cancel or end it before posting a new one."
                        )
                    }
                    return@launch
                }

                val resolvedDest = if (form.destinationLat == null) {
                    PlaceSearchService.searchLocalPresets(form.destination).firstOrNull()
                } else null
                val resolvedGate = if (form.gateLat == null) {
                    PlaceSearchService.searchLocalPresets(form.gate).firstOrNull()
                } else null

                val finalGateLat = form.gateLat ?: resolvedGate?.lat ?: 19.1076
                val finalGateLng = form.gateLng ?: resolvedGate?.lng ?: 72.8263
                val finalDestLat = form.destinationLat ?: resolvedDest?.lat
                val finalDestLng = form.destinationLng ?: resolvedDest?.lng

                val lobby = Lobby(
                    checkpoint = form.checkpoint,
                    gate = form.gate,
                    destination = form.destination,
                    gateLat = finalGateLat,
                    gateLng = finalGateLng,
                    destinationLat = finalDestLat,
                    destinationLng = finalDestLng,
                    departureTime = Timestamp(form.departureTime),
                    rideType = form.rideType,
                    maxSize = form.maxSize,
                    memberIds = listOf(uid),
                    status = LobbyStatus.OPEN,
                    createdBy = uid,
                    createdAt = Timestamp.now()
                )
                FirebaseRefs.lobbies.add(lobby).await()
                onPosted()
            } catch (e: Exception) {
                _formState.update {
                    it.copy(isSubmitting = false, errorMessage = e.localizedMessage ?: "Couldn't post the lobby. Try again.")
                }
            }
        }
    }
}
