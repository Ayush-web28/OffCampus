package com.offcampus.app.ui.lobby

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.offcampus.app.data.model.LobbyStatus
import com.offcampus.app.data.model.Rider
import com.offcampus.app.ui.avatar.AvatarView
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyDetailScreen(
    lobbyId: String,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenPostFare: () -> Unit,
    onOpenPaymentSplit: () -> Unit
) {
    val viewModel: LobbyDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { LobbyDetailViewModel(lobbyId) } }
    )
    val lobby by viewModel.lobby.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val isUpdating by viewModel.isUpdating.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lobby") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        val current = lobby
        if (current == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 64.dp))
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp)) {
            Text("${current.gate} → ${current.destination}", style = MaterialTheme.typography.headlineMedium)
            Text(
                current.checkpoint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(current.departureTime.toDate()),
                    style = MaterialTheme.typography.labelLarge
                )
                RideTypeChip(current.rideType)
            }

            Text(
                "${current.memberIds.size} / ${current.maxSize} riders",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            // Shown inline, before any "Join" decision — knowing who you'd actually be riding
            // with matters most right when you're deciding whether to join a lobby you found
            // browsing, not after you're already in it.
            if (members.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    members.forEach { member -> MemberRow(member = member, isMaster = member.id == current.createdBy) }
                }
            }

            // Crossfades rather than snapping, so locking a lobby (a Firestore update that can
            // arrive from any member's tap, not just this device's) feels like a real transition.
            Crossfade(targetState = current.status, label = "lobbyStatus", modifier = Modifier.padding(top = 24.dp)) { status ->
                val isMaster = viewModel.currentUid == current.createdBy
                when {
                    status == LobbyStatus.COMPLETED -> CompletedTripCard(onViewSplit = onOpenPaymentSplit)
                    // Booking the ride and ending it (splitting the fare) are the lobby master's
                    // calls to make — everyone else just waits for that to happen, same as they
                    // can't lock the lobby in the first place.
                    status == LobbyStatus.LOCKED && isMaster -> LockedRideBookingCard(
                        destination = current.destination,
                        onSplitFare = onOpenPostFare
                    )
                    status == LobbyStatus.LOCKED -> LockedRideInProgressCard()
                    isMaster -> LobbyMasterControls(
                        canLock = current.memberIds.isNotEmpty(),
                        isUpdating = isUpdating,
                        onLock = viewModel::lock
                    )
                    viewModel.currentUid in current.memberIds -> OutlinedButton(
                        onClick = viewModel::leave,
                        enabled = !isUpdating,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Leave lobby") }
                    current.memberIds.size < current.maxSize -> JoinLobbyButton(
                        isUpdating = isUpdating,
                        onClick = viewModel::join
                    )
                    else -> Text(
                        "This lobby is full.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Chat stays available whether the lobby is OPEN or LOCKED — coordinating exact
            // pickup time/spot matters most right after locking, not before.
            val isParticipant = viewModel.currentUid == current.createdBy || viewModel.currentUid in current.memberIds
            if (isParticipant) {
                OutlinedButton(onClick = onOpenChat, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Text("Open chat")
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: Rider, isMaster: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(avatarId = member.avatarId, photoUrl = member.photoUrl, photoBase64 = member.photoBase64, size = 32.dp)
        Text(
            member.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 10.dp)
        )
        if (isMaster) {
            Text(
                "Lobby master",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

/** Optimistically morphs "Join lobby" into a checkmark the instant it's tapped, rather than just
 * disabling while [isUpdating] — the actual "Leave lobby" state (once Firestore's snapshot
 * confirms the join) replaces this whole composable an instant later via the caller's own
 * `when` branch, which happens fast enough in practice that this morph is what a rider sees
 * bridge the gap, not a bare disabled button. [tapped] resets itself if the write fails (isUpdating
 * drops back to false while this branch is still showing at all, meaning the join didn't land) so
 * a failed tap doesn't leave a stuck, misleading checkmark. */
@Composable
private fun JoinLobbyButton(isUpdating: Boolean, onClick: () -> Unit) {
    var tapped by remember { mutableStateOf(false) }
    LaunchedEffect(isUpdating) {
        if (!isUpdating && tapped) tapped = false
    }
    val containerColor by animateColorAsState(
        targetValue = if (tapped) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "joinButtonColor"
    )
    Button(
        onClick = { tapped = true; onClick() },
        enabled = !isUpdating && !tapped,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            disabledContainerColor = containerColor
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        AnimatedContent(targetState = tapped, label = "joinButtonContent") { joined ->
            if (joined) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Joined", modifier = Modifier.padding(start = 6.dp))
                }
            } else {
                Text("Join lobby")
            }
        }
    }
}

@Composable
private fun LobbyMasterControls(canLock: Boolean, isUpdating: Boolean, onLock: () -> Unit) {
    Column {
        Text(
            "You're the lobby master — lock it once everyone's in.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Button(
            onClick = onLock,
            enabled = canLock && !isUpdating,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Lock lobby") }
    }
}

@Composable
private fun LockedRideBookingCard(destination: String, onSplitFare: () -> Unit) {
    val context = LocalContext.current
    val backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
    Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Lobby locked — book the ride", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pickup uses your current location in each app; drop-off is passed as a text hint, not an exact pin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            rideBookingLinks(destination).forEach { link ->
                RideBookingButton(link = link, onOpen = { openRideBookingLink(context, link) })
            }
            OutlinedButton(onClick = onSplitFare, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Ride's done — split the fare")
            }
        }
    }
}

/** A brief "Opening Uber…" spinner state instead of the button just sitting there while Android
 * hands off to the other app — `openRideBookingLink` itself returns almost instantly (it only
 * *requests* the activity start), the real handoff delay happens at the OS level a beat later, so
 * without this a tap looked like nothing happened for a moment. The 2s timeout is a safety net,
 * not the expected case — it only matters if a rider backs out of Uber/the Play Store fast enough
 * to see this screen resume while [opening] is still true, so it doesn't get stuck reading
 * "Opening…" forever after a real launch already happened. */
@Composable
private fun RideBookingButton(link: RideBookingLink, onOpen: () -> Unit) {
    var opening by remember { mutableStateOf(false) }
    LaunchedEffect(opening) {
        if (opening) {
            delay(2000)
            opening = false
        }
    }
    Button(
        onClick = { opening = true; onOpen() },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        if (opening) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Text("Opening ${link.label}…", modifier = Modifier.padding(start = 8.dp))
        } else {
            Text("Open in ${link.label}")
        }
    }
}

@Composable
private fun LockedRideInProgressCard() {
    val backgroundColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f)
    Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Lobby locked — ride in progress", style = MaterialTheme.typography.titleLarge)
            Text(
                "The lobby master's booking the ride. Once it's done, they'll split the fare here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CompletedTripCard(onViewSplit: () -> Unit) {
    val backgroundColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
    Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = backgroundColor)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Trip completed", style = MaterialTheme.typography.titleLarge)
            Text(
                "The fare's been split — check who's settled up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            Button(onClick = onViewSplit, modifier = Modifier.fillMaxWidth()) {
                Text("View payment split")
            }
        }
    }
}
