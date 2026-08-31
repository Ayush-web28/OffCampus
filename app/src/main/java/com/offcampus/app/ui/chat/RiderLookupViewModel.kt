package com.offcampus.app.ui.chat

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.Rider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Watches a single rider by id — used for the friend-chat header (name + avatar), where we
 * only have the other person's uid from navigation, not their profile. */
class RiderLookupViewModel(riderId: String) : ViewModel() {
    private val _rider = MutableStateFlow<Rider?>(null)
    val rider: StateFlow<Rider?> = _rider.asStateFlow()

    private val listener: ListenerRegistration = FirebaseRefs.riders.document(riderId)
        .addSnapshotListener { snapshot, _ ->
            _rider.value = snapshot?.toObject(Rider::class.java)?.copy(id = snapshot.id)
        }

    override fun onCleared() {
        listener.remove()
    }
}
