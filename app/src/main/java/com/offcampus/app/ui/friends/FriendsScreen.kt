package com.offcampus.app.ui.friends

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.offcampus.app.data.model.Rider
import com.offcampus.app.ui.avatar.AvatarView
import com.offcampus.app.ui.chat.friendChatId

@Composable
fun FriendsScreen(
    onOpenChat: (String) -> Unit,
    onOpenProfile: (String) -> Unit,
    unreadChatIds: Set<String> = emptySet(),
    unreadCounts: Map<String, Int> = emptyMap(),
    viewModel: FriendsViewModel = viewModel()
) {
    val friends by viewModel.friends.collectAsStateWithLifecycle()
    val incoming by viewModel.incomingRequests.collectAsStateWithLifecycle()
    val addFriendState by viewModel.addFriendState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp)
    ) {
        item {
            Text("Friends", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 20.dp))

            Text("Add a friend", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = addFriendState.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Search by name or college email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)
            )
            if (addFriendState.isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(vertical = 4.dp), strokeWidth = 2.dp)
            }
            AnimatedVisibility(
                visible = addFriendState.errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                addFriendState.errorMessage?.let { error ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = addFriendState.successMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                addFriendState.successMessage?.let { success ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = success,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // Tapping avatar/name opens public profile; tapping "Add" sends friend request directly.
        items(addFriendState.searchResults, key = { "search_${it.id}" }) { result ->
            SearchResultRow(
                rider = result,
                onOpenProfile = { onOpenProfile(result.id) },
                onAdd = { viewModel.sendRequestTo(result) }
            )
        }

        item {
            if (incoming.isNotEmpty()) {
                Text(
                    "Requests",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 28.dp, bottom = 8.dp)
                )
            }
        }

        items(incoming, key = { it.request.id }) { incomingRequest ->
            Column(modifier = Modifier.animateItem()) {
                IncomingRequestRow(
                    incomingRequest = incomingRequest,
                    onOpenProfile = { onOpenProfile(incomingRequest.fromRider.id) },
                    onAccept = { viewModel.accept(incomingRequest) },
                    onDecline = { viewModel.decline(incomingRequest) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }

        item {
            Text(
                "Friends (${friends.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            if (friends.isEmpty()) {
                Text(
                    "No friends yet — search a name or college email above and tap them to add.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val myUid = Firebase.auth.currentUser?.uid ?: ""
        items(friends, key = { it.id }) { friend ->
            val chatId = if (myUid.isNotBlank()) friendChatId(myUid, friend.id) else ""
            val unreadCount = unreadCounts[chatId] ?: 0
            val hasUnread = unreadCount > 0 || (chatId in unreadChatIds)
            FriendRow(
                friend = friend,
                hasUnreadChat = hasUnread,
                unreadChatCount = unreadCount,
                onOpenProfile = { onOpenProfile(friend.id) },
                onOpenChat = { onOpenChat(friend.id) }
            )
        }
    }
}

@Composable
private fun SearchResultRow(
    rider: Rider,
    onOpenProfile: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenProfile),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(avatarId = rider.avatarId, photoUrl = rider.photoUrl, photoBase64 = rider.photoBase64, size = 40.dp)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(rider.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    rider.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        TextButton(onClick = onAdd) {
            Text(
                "Add",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun IncomingRequestRow(
    incomingRequest: IncomingRequest,
    onOpenProfile: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenProfile),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(
                avatarId = incomingRequest.fromRider.avatarId,
                photoUrl = incomingRequest.fromRider.photoUrl,
                photoBase64 = incomingRequest.fromRider.photoBase64,
                size = 44.dp
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(incomingRequest.fromRider.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "wants to be friends",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedButton(onClick = onDecline) { Text("Decline") }
        Button(onClick = onAccept, modifier = Modifier.padding(start = 8.dp)) { Text("Accept") }
    }
}

@Composable
private fun FriendRow(
    friend: Rider,
    hasUnreadChat: Boolean = false,
    unreadChatCount: Int = 0,
    onOpenProfile: () -> Unit,
    onOpenChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenProfile),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarView(avatarId = friend.avatarId, photoUrl = friend.photoUrl, photoBase64 = friend.photoBase64, size = 44.dp)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(friend.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    friend.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        OutlinedButton(
            onClick = onOpenChat,
            shape = RoundedCornerShape(999.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) {
            BadgedBox(
                badge = {
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
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Forum,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Chat", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
