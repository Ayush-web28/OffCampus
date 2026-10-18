package com.offcampus.app.ui.lobby

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.offcampus.app.data.model.Lobby
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
    onOpenPaymentSplit: () -> Unit,
    onOpenProfile: (String) -> Unit = {},
    hasUnreadChat: Boolean = false,
    unreadChatCount: Int = 0
) {
    val viewModel: LobbyDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { LobbyDetailViewModel(lobbyId) } }
    )
    val lobby by viewModel.lobby.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val isUpdating by viewModel.isUpdating.collectAsStateWithLifecycle()
    val conflictOwnedLobby by viewModel.conflictOwnedLobby.collectAsStateWithLifecycle()
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }

    if (conflictOwnedLobby != null) {
        val owned = conflictOwnedLobby!!
        AlertDialog(
            onDismissRequest = viewModel::dismissConflictOwnedLobby,
            icon = {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Cancel your lobby?", fontWeight = FontWeight.Bold) },
            text = {
                Text("You are currently hosting an active lobby to ${owned.destination}. Joining this lobby will cancel your own lobby and notify any riders in it. Do you want to cancel your lobby and join?")
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmCancelOwnedLobbyAndJoin,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel my lobby & join", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConflictOwnedLobby) {
                    Text("Keep my lobby")
                }
            }
        )
    }

    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.LockOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Unlock lobby?", fontWeight = FontWeight.Bold) },
            text = { Text("If you locked the lobby by mistake, unlocking will reopen it so new riders can join.") },
            confirmButton = {
                Button(
                    onClick = {
                        showUnlockDialog = false
                        viewModel.unlock()
                    }
                ) {
                    Text("Unlock lobby")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockDialog = false }) {
                    Text("Keep locked")
                }
            }
        )
    }

    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            title = { Text("Leave lobby?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to leave this lobby? You will forfeit your seat.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveDialog = false
                        viewModel.leave()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Leave lobby", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Stay")
                }
            }
        )
    }

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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isLocked = current.status == LobbyStatus.LOCKED
                        val isMaster = viewModel.currentUid == current.createdBy
                        val statusBg = if (isLocked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                        val statusColor = if (isLocked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.tertiary
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .background(statusBg, RoundedCornerShape(999.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                if (isLocked) {
                                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = statusColor)
                                }
                                Text(
                                    text = if (isLocked) "LOBBY LOCKED" else "LOBBY OPEN",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor
                                )
                            }

                            val canUnlock = isMaster && isLocked && !current.isAutoExpired()
                            if (canUnlock) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable { showUnlockDialog = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LockOpen,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "UNLOCK",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        RideTypeChip(current.rideType)
                    }

                    val detailRouteText = buildAnnotatedString {
                        append(current.gate)
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        ) {
                            append(" → ")
                        }
                        append(current.destination)
                    }
                    Text(
                        text = detailRouteText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 10.dp)
                    )

                    Text(
                        text = "Meeting point: ${current.checkpoint}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(current.departureTime.toDate()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${current.memberIds.size}/${current.maxSize} riders",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                "Riders in lobby",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
            )

            var riderToRemove by remember { mutableStateOf<Rider?>(null) }
            riderToRemove?.let { rider ->
                AlertDialog(
                    onDismissRequest = { riderToRemove = null },
                    title = { Text("Remove rider") },
                    text = { Text("Are you sure you want to remove ${rider.name} from this lobby?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.removeRider(rider.id)
                                riderToRemove = null
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Remove")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { riderToRemove = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // Shown inline, before any "Join" decision — knowing who you'd actually be riding
            // with matters most right when you're deciding whether to join a lobby you found
            // browsing, not after you're already in it.
            val isCurrentMaster = viewModel.currentUid == current.createdBy
            if (members.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    members.forEach { member ->
                        val isThisMemberMaster = member.id == current.createdBy
                        val canRemove = isCurrentMaster && !isThisMemberMaster && current.status != LobbyStatus.COMPLETED
                        MemberRow(
                            member = member,
                            isMaster = isThisMemberMaster,
                            canRemove = canRemove,
                            onOpenProfile = { onOpenProfile(member.id) },
                            onRemove = { riderToRemove = member }
                        )
                    }
                }
            }

            // Crossfades rather than snapping, so locking a lobby (a Firestore update that can
            // arrive from any member's tap, not just this device's) feels like a real transition.
            Crossfade(targetState = current.status, label = "lobbyStatus", modifier = Modifier.padding(top = 24.dp)) { status ->
                val isMaster = viewModel.currentUid == current.createdBy
                when {
                    status == LobbyStatus.CANCELLED -> CancelledTripCard(
                        isAutoExpired = current.isAutoExpired(),
                        cancelReason = current.cancelReason,
                        departureTime = current.departureTime,
                        onBack = onBack
                    )
                    status == LobbyStatus.COMPLETED -> CompletedTripCard(onViewSplit = onOpenPaymentSplit)
                    // Booking the ride and ending it (splitting the fare) are the lobby master's
                    // calls to make — everyone else just waits for that to happen, same as they
                    // can't lock the lobby in the first place.
                    status == LobbyStatus.LOCKED && isMaster -> LockedRideBookingCard(
                        lobby = current,
                        onSplitFare = onOpenPostFare,
                        onCancelLobby = { viewModel.cancelLobby(onBack) }
                    )
                    status == LobbyStatus.LOCKED -> LockedRideInProgressCard()
                    isMaster -> LobbyMasterControls(
                        canLock = current.memberIds.isNotEmpty(),
                        isUpdating = isUpdating,
                        onLock = viewModel::lock,
                        onCancel = { viewModel.cancelLobby(onBack) }
                    )
                    viewModel.currentUid in current.memberIds -> OutlinedButton(
                        onClick = { showLeaveDialog = true },
                        enabled = !isUpdating,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) { Text("Leave lobby", fontWeight = FontWeight.Bold) }
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
                OutlinedButton(
                    onClick = onOpenChat,
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Open lobby chat",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (hasUnreadChat || unreadChatCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.error
                            ) {
                                if (unreadChatCount > 0) {
                                    Text(
                                        text = if (unreadChatCount > 9) "9+" else "$unreadChatCount",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: Rider,
    isMaster: Boolean,
    canRemove: Boolean = false,
    onOpenProfile: () -> Unit = {},
    onRemove: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenProfile),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(avatarId = member.avatarId, photoUrl = member.photoUrl, photoBase64 = member.photoBase64, size = 36.dp)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    member.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (isMaster) {
                    Text(
                        "Lobby Host",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        if (canRemove) {
            TextButton(
                onClick = onRemove,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Remove", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
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
        targetValue = if (tapped) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "joinButtonColor"
    )
    Button(
        onClick = { tapped = true; onClick() },
        enabled = !isUpdating && !tapped,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            disabledContainerColor = containerColor,
            contentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        if (tapped) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                Text("Joined", fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 6.dp))
            }
        } else {
            Text("Join lobby", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
private fun LobbyMasterControls(
    canLock: Boolean,
    isUpdating: Boolean,
    onLock: () -> Unit,
    onCancel: () -> Unit
) {
    var showCancelDialog by remember { mutableStateOf(false) }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel lobby?") },
            text = { Text("Are you sure you want to cancel this lobby? This will close the lobby for all riders.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onCancel()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel lobby", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep lobby")
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "You're the lobby master — lock it once everyone's in, or cancel if plans change.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Button(
            onClick = onLock,
            enabled = canLock && !isUpdating,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) { Text("Lock lobby", fontWeight = FontWeight.Bold) }

        OutlinedButton(
            onClick = { showCancelDialog = true },
            enabled = !isUpdating,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) { Text("Cancel lobby", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun LockedRideBookingCard(
    lobby: Lobby,
    onSplitFare: () -> Unit,
    onCancelLobby: () -> Unit
) {
    val context = LocalContext.current
    var showCancelDialog by remember { mutableStateOf(false) }
    val pickupName = remember(lobby) { resolvePickupName(lobby.checkpoint, lobby.gate) }
    val links = remember(lobby) { rideBookingLinks(lobby) }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel ride & lobby?") },
            text = { Text("If this ride didn't happen or was cancelled, ending the lobby will close it for everyone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onCancelLobby()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("End & cancel lobby", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep active")
                }
            }
        )
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Lobby locked — book the ride", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Pickup set to $pickupName; destination set to ${lobby.destination}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )

            Text(
                text = "Open With",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                links.forEach { link ->
                    RideBookingLogoCard(
                        link = link,
                        onOpen = { openRideBookingLink(context, link) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Button(
                onClick = onSplitFare,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Ride's done — split the fare", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = { showCancelDialog = true },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Cancel ride / End lobby", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun CancelledTripCard(
    isAutoExpired: Boolean = false,
    cancelReason: String? = null,
    departureTime: com.google.firebase.Timestamp? = null,
    onBack: () -> Unit
) {
    val formattedTime = departureTime?.let {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(it.toDate())
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Cancel,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(44.dp)
            )
            Text(
                when {
                    isAutoExpired -> "Lobby Automatically Cancelled"
                    cancelReason == Lobby.CANCEL_REASON_HOST_LEFT -> "Lobby Cancelled by Host"
                    else -> "This lobby was cancelled"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                when {
                    isAutoExpired -> {
                        if (formattedTime != null) {
                            "This lobby was automatically cancelled because it wasn't locked within 1 minute past departure time ($formattedTime)."
                        } else {
                            "This lobby was automatically cancelled because it wasn't locked within 1 minute past departure time."
                        }
                    }
                    cancelReason == Lobby.CANCEL_REASON_HOST_LEFT -> {
                        "The lobby master cancelled this trip because they joined another ride."
                    }
                    else -> {
                        "The lobby master cancelled this trip before the ride finished."
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
            )
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Back to lobbies", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * A branded logo card/tile for ride-hailing providers (Uber, Ola, Rapido)
 * styled like credit card selection chips with brand logos and click ripples.
 */
@Composable
private fun RideBookingLogoCard(
    link: RideBookingLink,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var opening by remember { mutableStateOf(false) }
    LaunchedEffect(opening) {
        if (opening) {
            delay(2000)
            opening = false
        }
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    // Card background and border tuned for light and dark modes
    val (cardBg, borderStroke) = when (link.label) {
        "Uber" -> Pair(
            if (isDark) Color(0xFF18181B) else Color(0xFF000000),
            BorderStroke(1.dp, if (isDark) Color(0xFF27272A) else Color(0xFF1E293B))
        )
        "Ola" -> Pair(
            if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF),
            BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
        )
        else -> Pair( // Rapido
            if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF),
            BorderStroke(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
        )
    }

    Surface(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                opening = true
                onOpen()
            },
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = borderStroke
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (opening) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = if (link.label == "Uber") Color.White else MaterialTheme.colorScheme.primary
                )
            } else {
                when (link.label) {
                    "Uber" -> {
                        Text(
                            text = "Uber",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = (-0.3).sp
                        )
                    }
                    "Ola" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF00C853), // Ola signature green
                                modifier = Modifier.size(15.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isDark) Color(0xFF1E293B) else Color.White,
                                        modifier = Modifier.size(6.dp)
                                    ) {}
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "OLA",
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    else -> { // Rapido
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFBBF24), // Rapido yellow
                                modifier = Modifier.size(15.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "R",
                                        color = Color(0xFFDC2626), // Rapido red
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "rapido",
                                color = if (isDark) Color.White else Color(0xFF0F172A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = (-0.2).sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedRideInProgressCard() {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Lobby locked — ride in progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "The lobby master is booking the ride. Once it's done, they'll split the fare here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun CompletedTripCard(onViewSplit: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Trip completed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "The fare has been split — check who's settled up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
            Button(
                onClick = onViewSplit,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("View payment split", fontWeight = FontWeight.Bold)
            }
        }
    }
}

