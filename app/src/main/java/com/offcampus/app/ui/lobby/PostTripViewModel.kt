package com.offcampus.app.ui.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.LobbyStatus
import com.offcampus.app.data.model.RideType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

// Preset "leaving in" offsets rather than a full date/time picker — this app is for a ride
// happening in the next few hours, not scheduling a trip next week, so a quick chip picks
// the moment better than a calendar widget would.
val DEPARTURE_OFFSET_MINUTES = listOf(15, 30, 60, 90, 120, 180)

// A real auto-rickshaw is licensed for 3 passengers, full stop — a cab (sedan/SUV) has real
// room to flex, so only auto gets the tighter cap. Keyed off ride type rather than one fixed
// range for every trip.
fun groupSizeOptions(rideType: RideType): IntRange = if (rideType == RideType.AUTO) 2..3 else 2..6

data class PostTripFormState(
    val checkpoint: String = "",
    val gate: String = "",
    val destination: String = "",
    val departureInMinutes: Int = 30,
    val maxSize: Int = 3,
    val rideType: RideType = RideType.AUTO,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null
)

class PostTripViewModel : ViewModel() {
    private val _formState = MutableStateFlow(PostTripFormState())
    val formState: StateFlow<PostTripFormState> = _formState.asStateFlow()

    fun onCheckpointChange(value: String) = _formState.update { it.copy(checkpoint = value, errorMessage = null) }
    fun onGateChange(value: String) = _formState.update { it.copy(gate = value, errorMessage = null) }
    fun onDestinationChange(value: String) = _formState.update { it.copy(destination = value, errorMessage = null) }
    fun onDepartureChange(minutes: Int) = _formState.update { it.copy(departureInMinutes = minutes) }
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
            _formState.update { it.copy(errorMessage = "Fill in checkpoint, gate and destination.") }
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
                val lobby = Lobby(
                    checkpoint = form.checkpoint,
                    gate = form.gate,
                    destination = form.destination,
                    departureTime = Timestamp(Date(System.currentTimeMillis() + form.departureInMinutes * 60_000L)),
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
