package com.offcampus.app.ui.lobby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offcampus.app.data.model.RideType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyBrowseScreen(
    onCreateLobby: () -> Unit,
    onOpenLobby: (String) -> Unit,
    onOpenTripHistory: () -> Unit,
    onOpenPaymentSplit: (String) -> Unit,
    unreadChatIds: Set<String> = emptySet(),
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

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateLobby) {
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
                        style = MaterialTheme.typography.headlineMedium
                    )
                    OutlinedButton(
                        onClick = onOpenTripHistory
                    ) {
                        Text("Past rides")
                    }
                }
            }

            item(key = "search") {
                OutlinedTextField(
                    value = filters.destinationQuery,
                    onValueChange = viewModel::onDestinationQueryChange,
                    label = { Text("Search destination") },
                    singleLine = true,
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
                                onClick = {
                                    viewModel.onRideTypeChange(null)
                                    rideTypeMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Auto") },
                                onClick = {
                                    viewModel.onRideTypeChange(RideType.AUTO)
                                    rideTypeMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cab") },
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
                                onClick = {
                                    viewModel.onSortChange(SortOption.NEWEST)
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Soonest") },
                                onClick = {
                                    viewModel.onSortChange(SortOption.SOONEST)
                                    sortMenuExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Most open") },
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
                        label = { Text("Friends only") }
                    )
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
                        ActiveRideCard(
                            lobby = active,
                            isMaster = active.createdBy == currentUserId,
                            hasUnreadChat = active.id in unreadChatIds,
                            onClick = { onOpenLobby(active.id) }
                        )
                    }
                }
            }

            if (lobbies.isEmpty() && activeLobby == null && pendingPayment == null) {
                item(key = "empty_state") {
                    EmptyLobbiesState(hasAnyLobbies = hasAnyLobbies)
                }
            }

            items(lobbies, key = { it.id }) { lobby ->
                val alreadyAnimated = animatedLobbyIds.containsKey(lobby.id)
                val visibleState = remember { MutableTransitionState(alreadyAnimated) }
                LaunchedEffect(Unit) {
                    visibleState.targetState = true
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
private fun EmptyLobbiesState(hasAnyLobbies: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (hasAnyLobbies) "No lobbies match your filters." else "No lobbies yet — be the first to post one.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
