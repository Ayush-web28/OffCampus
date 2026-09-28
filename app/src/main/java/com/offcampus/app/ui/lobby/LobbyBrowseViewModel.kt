package com.offcampus.app.ui.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.ListenerRegistration
import com.offcampus.app.data.FirebaseRefs
import com.offcampus.app.data.PlaceSearchService
import com.offcampus.app.data.PlaceSuggestion
import com.offcampus.app.data.observeSignedInUid
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.LobbyStatus
import com.offcampus.app.data.model.RideType
import com.offcampus.app.data.model.Rider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class SortOption { NEWEST, SOONEST, MOST_OPEN }

/** Carries the lobby's own id alongside the message so the Snackbar shown for it can act as a
 * shortcut straight into that lobby, instead of just informing and leaving the user to go find
 * it themselves in the list. */
data class FriendLobbyNotification(val message: String, val lobbyId: String)

data class LobbyFilters(
    val destinationQuery: String = "",
    val rideType: RideType? = null, // null means "all ride types"
    val friendsOnly: Boolean = false,
    // Newest-first by default — a rider who just posted a trip should see it land at the top of
    // their own list immediately, not have to know to switch sort options to find it.
    val sort: SortOption = SortOption.NEWEST
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
    private val _friendLobbyNotification = MutableStateFlow<FriendLobbyNotification?>(null)
    val friendLobbyNotification: StateFlow<FriendLobbyNotification?> = _friendLobbyNotification.asStateFlow()

    // null until the first snapshot is processed, so we never "notify" about lobbies that
    // already existed when the screen opened — only ones that arrive afterward.
    private var seenLobbyIds: MutableSet<String>? = null

    private var lobbyListener: ListenerRegistration? = null
    private var riderListener: ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null

    // What the typed search text resolves to as a real place — null whenever it's too short,
    // never matched anything, or hasn't finished resolving yet. Used to widen "search destination"
    // from exact-text matching to "lobbies actually near this place" (see matchesDestination()).
    private val _queryLocation = MutableStateFlow<PlaceSuggestion?>(null)
    private val _queryLoading = MutableStateFlow(false)
    val queryLoading: StateFlow<Boolean> = _queryLoading.asStateFlow()
    private var queryLocationJob: Job? = null

    init {
        // See observeSignedInUid's doc — a one-shot `val id = uid` here used to keep the
        // "Friends only" filter working off the previous account's friend list after switching
        // accounts mid-session.
        authListener = observeSignedInUid(::attachRiderListener)

        // Same head start as PostTripViewModel's warmUp() call — Lobbies is the app's home
        // screen, so a rider can start typing in "Search destination" the instant it opens,
        // with none of Post Trip's few-seconds-on-Checkpoint runway to hide the cold connection.
        viewModelScope.launch { PlaceSearchService.warmUp() }

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

    private fun attachRiderListener(id: String?) {
        riderListener?.remove()
        _friendIds.value = emptySet()
        if (id == null) return
        riderListener = FirebaseRefs.riders.document(id).addSnapshotListener { snapshot, _ ->
            _friendIds.value = snapshot?.toObject(Rider::class.java)?.friendIds?.toSet() ?: emptySet()
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
            _friendLobbyNotification.value =
                FriendLobbyNotification("$name posted a trip to ${lobby.destination}", lobby.id)
        }
    }

    fun dismissFriendLobbyNotification() {
        _friendLobbyNotification.value = null
    }

    // Ticks once immediately, then every minute — purely to force visibleLobbies to re-evaluate
    // staleness (see STALE_AFTER_MS below) as real time passes, not just when Firestore data
    // actually changes. Without this, a lobby crossing the 24-hour mark while a rider is sitting
    // on the Lobbies screen with no new snapshot arriving would stay visible until *something*
    // else happened to trigger a recompute.
    private val nowTicker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(60_000)
        }
    }

    // Filtering/sorting is derived state: it recomputes automatically whenever the raw list,
    // the friend graph, the filter settings, the resolved query location, or the clock ticks
    // over, instead of us re-running it in every setter.
    val visibleLobbies: StateFlow<List<Lobby>> =
        combine(_lobbies, _filters, _friendIds, _queryLocation, nowTicker) { lobbies, filters, friendIds, queryLocation, now ->
            lobbies
                .filter { lobby ->
                    !isStale(lobby, now) &&
                        (filters.rideType == null || lobby.rideType == filters.rideType) &&
                        (!filters.friendsOnly || lobby.createdBy in friendIds) &&
                        matchesDestination(lobby, filters.destinationQuery, queryLocation)
                }
                .let { filtered ->
                    when (filters.sort) {
                        SortOption.NEWEST -> filtered.sortedByDescending { it.createdAt }
                        SortOption.SOONEST -> filtered.sortedBy { it.departureTime }
                        SortOption.MOST_OPEN -> filtered.sortedByDescending { it.maxSize - it.memberIds.size }
                    }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasAnyLobbies: StateFlow<Boolean> = _lobbies
        .combine(_filters) { lobbies, _ -> lobbies.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** A lobby that's been sitting OPEN for more than [STALE_AFTER_MS] since it was created is
     * treated as abandoned — its own departure window has long passed and its master never
     * locked or completed it, so it can't meaningfully still be "the same trip" a rider would
     * want to join. Purely a client-side hide from this list, not a Firestore write: this app
     * has no deployed Cloud Function to own a real cleanup job (see Fix 9/11's billing notes),
     * so there's no server-side scheduled deletion — the lobby document itself is untouched,
     * this just keeps it out of what a rider browses. */
    private fun isStale(lobby: Lobby, nowMs: Long): Boolean =
        nowMs - lobby.createdAt.toDate().time > STALE_AFTER_MS

    /** A lobby matches "Search destination" either the old way — its own destination text
     * contains what was typed — or, new here, by being within [NEARBY_RADIUS_KM] of wherever
     * that typed text actually resolves to. The second half only ever applies when both sides
     * have real coordinates: a lobby whose creator never picked a suggestion (Fix 16) has none,
     * and a query Photon can't place resolves to null — either way this just falls back to the
     * plain text match, never a hard failure. */
    private fun matchesDestination(lobby: Lobby, query: String, queryLocation: PlaceSuggestion?): Boolean {
        if (query.isBlank()) return true
        if (lobby.destination.contains(query, ignoreCase = true)) return true
        val lat = lobby.destinationLat
        val lng = lobby.destinationLng
        if (lat == null || lng == null || queryLocation == null) return false
        return haversineKm(lat, lng, queryLocation.lat, queryLocation.lng) <= NEARBY_RADIUS_KM
    }

    fun onDestinationQueryChange(query: String) {
        _filters.update { it.copy(destinationQuery = query) }
        queryLocationJob?.cancel()
        if (query.trim().length < 3) {
            _queryLocation.value = null
            _queryLoading.value = false
            return
        }
        // Same 300ms-debounce-then-search shape as PostTripViewModel's Gate/Destination
        // fields — one geocode per pause in typing. The rider never picks from a list here
        // (there's no dropdown on this field), so the top result stands in for "the place
        // they mean," the same way a rider typing without ever tapping a suggestion on Post
        // Trip just gets plain text matching instead — this is that same graceful fallback,
        // just automated instead of requiring a tap.
        queryLocationJob = viewModelScope.launch {
            delay(300)
            _queryLoading.value = true
            val results = PlaceSearchService.search(query)
            _queryLocation.value = results.firstOrNull()
            _queryLoading.value = false
        }
    }

    fun onRideTypeChange(rideType: RideType?) = _filters.update { it.copy(rideType = rideType) }
    fun onFriendsOnlyChange(friendsOnly: Boolean) = _filters.update { it.copy(friendsOnly = friendsOnly) }
    fun onSortChange(sort: SortOption) = _filters.update { it.copy(sort = sort) }

    override fun onCleared() {
        authListener?.let { Firebase.auth.removeAuthStateListener(it) }
        lobbyListener?.remove()
        riderListener?.remove()
    }
}

// How long an OPEN lobby stays browsable after being created, regardless of its own departure
// time — a lobby nobody locked within a day of posting it is realistically dead, not just late.
private const val STALE_AFTER_MS = 24 * 60 * 60 * 1000L

// "Same area" for this app's purposes — Mumbai suburbs are dense enough that a lobby's
// destination within this range of a searched place is a genuinely useful match ("close enough
// to walk, or ask the group to route past"), without the radius growing so wide it starts
// pulling in a different neighborhood entirely.
private const val NEARBY_RADIUS_KM = 3.0

// Standard great-circle (haversine) distance between two lat/lng points, in kilometers.
private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusKm * c
}
