package com.offcampus.app.ui.lobby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offcampus.app.data.model.RideType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyBrowseScreen(
    onCreateLobby: () -> Unit,
    onOpenLobby: (String) -> Unit,
    onOpenLobbyChat: (String) -> Unit = {},
    onOpenTripHistory: () -> Unit,
    onOpenPaymentSplit: (String) -> Unit,
    unreadChatIds: Set<String> = emptySet(),
    unreadCounts: Map<String, Int> = emptyMap(),
    viewModel: LobbyBrowseViewModel = viewModel()
) {
    val lobbies by viewModel.visibleLobbies.collectAsStateWithLifecycle()
    val hasAnyLobbies by viewModel.hasAnyLobbies.collectAsStateWithLifecycle()
    val activeLobby by viewModel.activeLobby.collectAsStateWithLifecycle()
    val pendingPayment by viewModel.pendingPayment.collectAsStateWithLifecycle()
    val currentUserId = viewModel.currentUserId
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val queryLoading by viewModel.queryLoading.collectAsStateWithLifecycle()
    val friendLobbyNotification by viewModel.friendLobbyNotification.collectAsStateWithLifecycle()
    val expiredLobbyAlert by viewModel.expiredLobbyAlert.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(Unit) {
        onDispose {
            snackbarHostState.currentSnackbarData?.dismiss()
            viewModel.dismissFriendLobbyNotification()
        }
    }

    LaunchedEffect(friendLobbyNotification) {
        friendLobbyNotification?.let { notification ->
            val result = snackbarHostState.showSnackbar(
                message = notification.message,
                actionLabel = "View",
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onOpenLobby(notification.lobbyId)
            viewModel.dismissFriendLobbyNotification()
        }
    }

    var rideTypeMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    // Tracks which lobby ids have already played their enter animation at least once,
    // for the whole time this screen stays composed — deliberately NOT reset by scroll.
    val animatedLobbyIds = remember { mutableStateMapOf<String, Boolean>() }

    var showActiveLobbyAlert by remember { mutableStateOf(false) }

    val hasActiveFilters = filters.destinationQuery.isNotBlank() ||
        filters.rideType != null ||
        filters.friendsOnly ||
        filters.sort != SortOption.NEWEST

    if (showActiveLobbyAlert && activeLobby != null) {
        val active = activeLobby!!
        val isMaster = active.createdBy == currentUserId
        AlertDialog(
            onDismissRequest = { showActiveLobbyAlert = false },
            title = { Text("Active Lobby in Progress") },
            text = {
                Text(
                    "You already have an active lobby to ${active.destination} (${if (isMaster) "as Lobby Master" else "as Rider"}).\n\n" +
                    "You cannot create another lobby while one is in progress. What would you like to do?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showActiveLobbyAlert = false
                        onOpenLobby(active.id)
                    }
                ) {
                    Text("View active lobby")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showActiveLobbyAlert = false }) {
                        Text("Dismiss")
                    }
                    if (isMaster) {
                        TextButton(
                            onClick = {
                                showActiveLobbyAlert = false
                                viewModel.cancelActiveLobby(active.id) {
                                    onCreateLobby()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Cancel & post new", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        TextButton(
                            onClick = {
                                showActiveLobbyAlert = false
                                viewModel.leaveActiveLobby(active.id) {
                                    onCreateLobby()
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Leave & post new", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        )
    }

    if (expiredLobbyAlert != null) {
        val expired = expiredLobbyAlert!!
        val formattedTime = SimpleDateFormat("h:mm a", Locale.getDefault()).format(expired.departureTime.toDate())
        AlertDialog(
            onDismissRequest = { viewModel.dismissExpiredLobbyAlert(expired.id) },
            icon = {
                Icon(
                    imageVector = Icons.Default.Cancel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Lobby Cancelled", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Your lobby to ${expired.destination} scheduled for $formattedTime was automatically cancelled because it wasn't locked within 1 minute past departure time."
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissExpiredLobbyAlert(expired.id) }
                ) {
                    Text("Got it")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (activeLobby != null) {
                        showActiveLobbyAlert = true
                    } else {
                        onCreateLobby()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Post a trip")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Lobbies",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = onOpenTripHistory,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text("Past rides", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            item(key = "search") {
                OutlinedTextField(
                    value = filters.destinationQuery,
                    onValueChange = viewModel::onDestinationQueryChange,
                    label = { Text("Search destination") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = if (queryLoading) {
                        {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else clearableTrailingIcon(filters.destinationQuery) { viewModel.onDestinationQueryChange("") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )
            }

            item(key = "filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Dropdown 1: Ride Type
                    Box {
                        FilterChip(
                            selected = filters.rideType != null,
                            onClick = { rideTypeMenuExpanded = true },
                            shape = RoundedCornerShape(999.dp),
                            label = {
                                Text(
                                    when (filters.rideType) {
                                        null -> "Ride: All"
                                        RideType.AUTO -> "Ride: Auto"
                                        RideType.CAB -> "Ride: Cab"
                                    }
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Select ride type",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = rideTypeMenuExpanded,
                            onDismissRequest = { rideTypeMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("All rides") },
                                leadingIcon = if (filters.rideType == null) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                onClick = {
                                    viewModel.onRideTypeChange(null)
                                    rideTypeMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Auto") },
                                leadingIcon = if (filters.rideType == RideType.AUTO) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                onClick = {
                                    viewModel.onRideTypeChange(RideType.AUTO)
                                    rideTypeMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cab") },
                                leadingIcon = if (filters.rideType == RideType.CAB) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                onClick = {
                                    viewModel.onRideTypeChange(RideType.CAB)
                                    rideTypeMenuExpanded = false
                                }
                            )
                        }
                    }

                    // Dropdown 2: Sort
                    Box {
                        FilterChip(
                            selected = filters.sort != SortOption.NEWEST,
                            onClick = { sortMenuExpanded = true },
                            shape = RoundedCornerShape(999.dp),
                            label = {
                                Text(
                                    when (filters.sort) {
                                        SortOption.NEWEST -> "Sort: Newest"
                                        SortOption.SOONEST -> "Sort: Soonest"
                                        SortOption.MOST_OPEN -> "Sort: Most open"
                                    }
                                )
                            },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Select sort order",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Newest") },
                                leadingIcon = if (filters.sort == SortOption.NEWEST) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                onClick = {
                                    viewModel.onSortChange(SortOption.NEWEST)
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Soonest") },
                                leadingIcon = if (filters.sort == SortOption.SOONEST) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                onClick = {
                                    viewModel.onSortChange(SortOption.SOONEST)
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Most open") },
                                leadingIcon = if (filters.sort == SortOption.MOST_OPEN) { { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) } } else null,
                                onClick = {
                                    viewModel.onSortChange(SortOption.MOST_OPEN)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }

                    // Toggle chip: Friends only
                    FilterChip(
                        selected = filters.friendsOnly,
                        onClick = { viewModel.onFriendsOnlyChange(!filters.friendsOnly) },
                        shape = RoundedCornerShape(999.dp),
                        label = { Text("Friends only") }
                    )

                    // Reset filters chip (shows when any filter or search is active)
                    if (hasActiveFilters) {
                        FilterChip(
                            selected = false,
                            onClick = viewModel::resetFilters,
                            shape = RoundedCornerShape(999.dp),
                            label = { Text("Reset") },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Reset filters",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }
            }

            pendingPayment?.let { info ->
                item(key = "pending_payment_${info.lobbyId}") {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        PendingPaymentBanner(
                            info = info,
                            onClick = { onOpenPaymentSplit(info.lobbyId) }
                        )
                    }
                }
            }

            activeLobby?.let { active ->
                item(key = "active_ride_${active.id}") {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        val unreadCount = unreadCounts[active.id] ?: 0
                        ActiveRideCard(
                            lobby = active,
                            isMaster = active.createdBy == currentUserId,
                            hasUnreadChat = active.id in unreadChatIds || unreadCount > 0,
                            unreadChatCount = unreadCount,
                            onOpenChat = { onOpenLobbyChat(active.id) },
                            onClick = { onOpenLobby(active.id) }
                        )
                    }
                }
            }

            if (lobbies.isEmpty()) {
                item(key = "empty_state") {
                    EmptyLobbiesState(
                        hasActiveFilters = hasActiveFilters,
                        hasActiveLobby = activeLobby != null,
                        onResetFilters = viewModel::resetFilters
                    )
                }
            }

            items(lobbies, key = { it.id }) { lobby ->
                val alreadyAnimated = animatedLobbyIds.containsKey(lobby.id)
                val visibleState = remember(lobby.id) {
                    MutableTransitionState(alreadyAnimated).apply { targetState = true }
                }
                LaunchedEffect(lobby.id) {
                    animatedLobbyIds[lobby.id] = true
                }
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = 380f)) +
                        scaleIn(spring(dampingRatio = 0.8f, stiffness = 380f), initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.92f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    LobbyCard(
                        lobby = lobby,
                        hasUnreadChat = lobby.id in unreadChatIds,
                        onClick = { onOpenLobby(lobby.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLobbiesState(
    hasActiveFilters: Boolean,
    hasActiveLobby: Boolean,
    onResetFilters: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                hasActiveFilters -> "No lobbies match your active filters."
                hasActiveLobby -> "No other open lobbies right now."
                else -> "No open lobbies yet — be the first to post one."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hasActiveFilters) {
            OutlinedButton(
                onClick = onResetFilters,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.padding(top = 12.dp)
            ) {
                Text("Reset filters")
            }
        }
    }
}
