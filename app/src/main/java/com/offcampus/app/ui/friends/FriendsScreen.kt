package com.offcampus.app.ui.friends

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offcampus.app.data.model.Rider
import com.offcampus.app.ui.avatar.AvatarView

@Composable
fun FriendsScreen(
    onOpenChat: (String) -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                OutlinedTextField(
                    value = addFriendState.email,
                    onValueChange = viewModel::onEmailChange,
                    label = { Text("College email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = viewModel::sendRequest,
                    enabled = !addFriendState.isSubmitting,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    if (addFriendState.isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Add")
                    }
                }
            }
            addFriendState.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            addFriendState.successMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodyMedium)
            }

            if (incoming.isNotEmpty()) {
                Text(
                    "Requests",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 28.dp, bottom = 8.dp)
                )
            }
        }

        items(incoming, key = { it.request.id }) { incomingRequest ->
            IncomingRequestRow(
                incomingRequest = incomingRequest,
                onAccept = { viewModel.accept(incomingRequest) },
                onDecline = { viewModel.decline(incomingRequest) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                "Your friends",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            if (friends.isEmpty()) {
                Text(
                    "No friends yet — add one above by their college email.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(friends, key = { it.id }) { friend ->
            FriendRow(friend = friend, onOpenChat = { onOpenChat(friend.id) })
        }
    }
}

@Composable
private fun IncomingRequestRow(
    incomingRequest: IncomingRequest,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AvatarView(avatarId = incomingRequest.fromRider.avatarId, size = 44.dp)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(incomingRequest.fromRider.name, style = MaterialTheme.typography.titleMedium)
            Text(
                "wants to be friends",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onDecline) { Text("Decline") }
        Button(onClick = onAccept, modifier = Modifier.padding(start = 8.dp)) { Text("Accept") }
    }
}

@Composable
private fun FriendRow(friend: Rider, onOpenChat: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarView(avatarId = friend.avatarId, size = 44.dp)
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(friend.name, style = MaterialTheme.typography.titleMedium)
            Text(
                friend.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(onClick = onOpenChat) { Text("Chat") }
    }
}
