package com.offcampus.app.ui.lobby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    viewModel: LobbyBrowseViewModel = viewModel()
) {
    val lobbies by viewModel.visibleLobbies.collectAsStateWithLifecycle()
    val hasAnyLobbies by viewModel.hasAnyLobbies.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val friendLobbyNotification by viewModel.friendLobbyNotification.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(friendLobbyNotification) {
        friendLobbyNotification?.let { notification ->
            val result = snackbarHostState.showSnackbar(notification.message, actionLabel = "View")
            if (result == SnackbarResult.ActionPerformed) onOpenLobby(notification.lobbyId)
            viewModel.dismissFriendLobbyNotification()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateLobby) {
                Icon(Icons.Default.Add, contentDescription = "Post a trip")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Text(
                "Lobbies",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )

            OutlinedTextField(
                value = filters.destinationQuery,
                onValueChange = viewModel::onDestinationQueryChange,
                label = { Text("Search destination") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filters.rideType == null,
                    onClick = { viewModel.onRideTypeChange(null) },
                    label = { Text("All rides") }
                )
                FilterChip(
                    selected = filters.rideType == RideType.AUTO,
                    onClick = { viewModel.onRideTypeChange(RideType.AUTO) },
                    label = { Text("Auto") }
                )
                FilterChip(
                    selected = filters.rideType == RideType.CAB,
                    onClick = { viewModel.onRideTypeChange(RideType.CAB) },
                    label = { Text("Cab") }
                )
                FilterChip(
                    selected = filters.friendsOnly,
                    onClick = { viewModel.onFriendsOnlyChange(!filters.friendsOnly) },
                    label = { Text("Friends only") }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filters.sort == SortOption.SOONEST,
                    onClick = { viewModel.onSortChange(SortOption.SOONEST) },
                    label = { Text("Soonest") }
                )
                FilterChip(
                    selected = filters.sort == SortOption.MOST_OPEN,
                    onClick = { viewModel.onSortChange(SortOption.MOST_OPEN) },
                    label = { Text("Most open") }
                )
            }

            AnimatedVisibility(visible = lobbies.isEmpty()) {
                EmptyLobbiesState(hasAnyLobbies = hasAnyLobbies)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().animateContentSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(lobbies, key = { it.id }) { lobby ->
                    // Starts false and flips true right after first composition, purely so
                    // AnimatedVisibility has a false->true edge to animate across — a lobby
                    // that's freshly posted (by anyone, live via the snapshot listener) or
                    // newly matching a filter fades and scales in instead of just popping in.
                    val visibleState = remember { MutableTransitionState(false) }
                    LaunchedEffect(Unit) { visibleState.targetState = true }
                    AnimatedVisibility(
                        visibleState = visibleState,
                        enter = fadeIn(spring(dampingRatio = 0.8f, stiffness = 380f)) +
                            scaleIn(spring(dampingRatio = 0.8f, stiffness = 380f), initialScale = 0.92f),
                        exit = fadeOut() + scaleOut(targetScale = 0.92f)
                    ) {
                        LobbyCard(lobby = lobby, onClick = { onOpenLobby(lobby.id) })
                    }
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
