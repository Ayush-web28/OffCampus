package com.offcampus.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.offcampus.app.ui.lobby.LobbyDetailViewModel

/** The chat id is just the lobby's own id — one chat per lobby, matching the data model note
 * that "chats" holds both lobby chats and friend chats as siblings in the same collection. */
@Composable
fun LobbyChatScreen(lobbyId: String, onBack: () -> Unit) {
    val lobbyViewModel: LobbyDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { LobbyDetailViewModel(lobbyId) } }
    )
    val chatViewModel: ChatViewModel = viewModel(
        factory = viewModelFactory { initializer { ChatViewModel(lobbyId) } }
    )

    val lobby by lobbyViewModel.lobby.collectAsStateWithLifecycle()
    val messages by chatViewModel.messages.collectAsStateWithLifecycle()
    val senderNames by chatViewModel.senderNames.collectAsStateWithLifecycle()

    LaunchedEffect(lobby?.memberIds, lobby?.createdBy) {
        lobby?.let { chatViewModel.ensureParticipants(it.memberIds + it.createdBy) }
    }

    ChatScreen(
        title = lobby?.let { "${it.gate} → ${it.destination}" } ?: "Lobby chat",
        headerAvatarId = null,
        messages = messages,
        currentUid = chatViewModel.currentUid,
        senderNames = senderNames,
        showSenderNames = true, // it's a group chat — always show who sent each message
        onSend = chatViewModel::send,
        onBack = onBack
    )
}
