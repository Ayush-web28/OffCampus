package com.offcampus.app.ui.lobby

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.offcampus.app.data.model.LobbyStatus
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

            // Crossfades rather than snapping, so locking a lobby (a Firestore update that can
            // arrive from any member's tap, not just this device's) feels like a real transition.
            Crossfade(targetState = current.status, label = "lobbyStatus", modifier = Modifier.padding(top = 24.dp)) { status ->
                when {
                    status == LobbyStatus.COMPLETED -> CompletedTripCard(onViewSplit = onOpenPaymentSplit)
                    status == LobbyStatus.LOCKED -> LockedRideBookingCard(
                        destination = current.destination,
                        onSplitFare = onOpenPostFare
                    )
                    viewModel.currentUid == current.createdBy -> LobbyMasterControls(
                        canLock = current.memberIds.isNotEmpty(),
                        isUpdating = isUpdating,
                        onLock = viewModel::lock
                    )
                    viewModel.currentUid in current.memberIds -> OutlinedButton(
                        onClick = viewModel::leave,
                        enabled = !isUpdating,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Leave lobby") }
                    current.memberIds.size < current.maxSize -> Button(
                        onClick = viewModel::join,
                        enabled = !isUpdating,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Join lobby") }
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
                Button(
                    onClick = { openRideBookingLink(context, link) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) { Text("Open in ${link.label}") }
            }
            OutlinedButton(onClick = onSplitFare, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Ride's done — split the fare")
            }
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
