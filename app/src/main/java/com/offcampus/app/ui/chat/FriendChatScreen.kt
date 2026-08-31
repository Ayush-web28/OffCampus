package com.offcampus.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

/**
 * Persistent 1:1 thread with a friend, reachable "usable anytime" (not tied to any lobby) — the
 * chat id is the sorted pair of uids from [friendChatId], so both people land on the same document.
 */
@Composable
fun FriendChatScreen(friendId: String, onBack: () -> Unit) {
    val riderLookup: RiderLookupViewModel = viewModel(
        factory = viewModelFactory { initializer { RiderLookupViewModel(friendId) } }
    )
    val friend by riderLookup.rider.collectAsStateWithLifecycle()

    val myUid = Firebase.auth.currentUser?.uid
    val chatId = remember(friendId, myUid) { myUid?.let { friendChatId(it, friendId) } ?: friendId }

    val chatViewModel: ChatViewModel = viewModel(
        factory = viewModelFactory { initializer { ChatViewModel(chatId) } }
    )
    val messages by chatViewModel.messages.collectAsStateWithLifecycle()

    LaunchedEffect(myUid) {
        if (myUid != null) chatViewModel.ensureParticipants(listOf(myUid, friendId))
    }

    ChatScreen(
        title = friend?.name ?: "Chat",
        headerAvatarId = friend?.avatarId,
        messages = messages,
        currentUid = chatViewModel.currentUid,
        senderNames = emptyMap(),
        showSenderNames = false, // 1:1 — which side a bubble is on already says who sent it
        onSend = chatViewModel::send,
        onBack = onBack
    )
}
