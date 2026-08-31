package com.offcampus.app.ui.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.LobbyStatus
import com.offcampus.app.data.model.RideType
import com.offcampus.app.data.model.Rider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

enum class SortOption { SOONEST, MOST_OPEN }

data class LobbyFilters(
    val destinationQuery: String = "",
    val rideType: RideType? = null, // null means "all ride types"
    val friendsOnly: Boolean = false,
    val sort: SortOption = SortOption.SOONEST
)

class LobbyBrowseViewModel : ViewModel() {
    private val uid: String? get() = Firebase.auth.currentUser?.uid

    // Raw, unfiltered lobbies straight from Firestore.
    private val _lobbies = MutableStateFlow<List<Lobby>>(emptyList())

    private val _filters = MutableStateFlow(LobbyFilters())
    val filters: StateFlow<LobbyFilters> = _filters.asStateFlow()

    private val _friendIds = MutableStateFlow<Set<String>>(emptySet())

    // In-app stand-in for the real push notification Phase 9's Cloud Function will send —
    // this only fires while the screen is open, since there's no server trigger yet.
    private val _friendLobbyNotification = MutableStateFlow<String?>(null)
    val friendLobbyNotification: StateFlow<String?> = _friendLobbyNotification.asStateFlow()

    // null until the first snapshot is processed, so we never "notify" about lobbies that
    // already existed when the screen opened — only ones that arrive afterward.
    private var seenLobbyIds: MutableSet<String>? = null

    private var lobbyListener: ListenerRegistration? = null
    private var riderListener: ListenerRegistration? = null

    init {
        val id = uid
        if (id != null) {
            riderListener = FirebaseRefs.riders.document(id).addSnapshotListener { snapshot, _ ->
                _friendIds.value = snapshot?.toObject(Rider::class.java)?.friendIds?.toSet() ?: emptySet()
            }
        }

        // Only OPEN lobbies are joinable, so that's the only thing browse needs to listen to.
        // This is a live listener, not a one-shot get() — a lobby filling up on someone else's
        // phone updates here with no manual refresh, which is the Phase 3 "real-time" checklist item.
        lobbyListener = FirebaseRefs.lobbies
            .whereEqualTo("status", LobbyStatus.OPEN.name)
            .addSnapshotListener { snapshot, _ ->
                val lobbies = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Lobby::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                val previouslySeen = seenLobbyIds
                if (previouslySeen != null) {
                    val newFriendLobby = lobbies.firstOrNull {
                        it.id !in previouslySeen && it.createdBy in _friendIds.value
                    }
                    if (newFriendLobby != null) notifyFriendLobby(newFriendLobby)
                }
                seenLobbyIds = lobbies.map { it.id }.toMutableSet()

                _lobbies.value = lobbies
            }
    }

    private fun notifyFriendLobby(lobby: Lobby) {
        viewModelScope.launch {
            val name = try {
                FirebaseRefs.riders.document(lobby.createdBy).get().await()
                    .toObject(Rider::class.java)?.name
            } catch (e: Exception) {
                null
            } ?: "A friend"
            _friendLobbyNotification.value = "$name posted a trip to ${lobby.destination}"
        }
    }

    fun dismissFriendLobbyNotification() {
        _friendLobbyNotification.value = null
    }

    // Filtering/sorting is derived state: it recomputes automatically whenever the raw list,
    // the friend graph, or the filter settings change, instead of us re-running it in every setter.
    val visibleLobbies: StateFlow<List<Lobby>> = combine(_lobbies, _filters, _friendIds) { lobbies, filters, friendIds ->
        lobbies
            .filter { lobby ->
                (filters.rideType == null || lobby.rideType == filters.rideType) &&
                    (!filters.friendsOnly || lobby.createdBy in friendIds) &&
                    (filters.destinationQuery.isBlank() ||
                        lobby.destination.contains(filters.destinationQuery, ignoreCase = true))
            }
            .let { filtered ->
                when (filters.sort) {
                    SortOption.SOONEST -> filtered.sortedBy { it.departureTime }
                    SortOption.MOST_OPEN -> filtered.sortedByDescending { it.maxSize - it.memberIds.size }
                }
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasAnyLobbies: StateFlow<Boolean> = _lobbies
        .combine(_filters) { lobbies, _ -> lobbies.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onDestinationQueryChange(query: String) = _filters.update { it.copy(destinationQuery = query) }
    fun onRideTypeChange(rideType: RideType?) = _filters.update { it.copy(rideType = rideType) }
    fun onFriendsOnlyChange(friendsOnly: Boolean) = _filters.update { it.copy(friendsOnly = friendsOnly) }
    fun onSortChange(sort: SortOption) = _filters.update { it.copy(sort = sort) }

    override fun onCleared() {
        lobbyListener?.remove()
        riderListener?.remove()
    }
}
