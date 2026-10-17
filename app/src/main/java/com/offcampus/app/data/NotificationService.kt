package com.offcampus.app.data

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.messaging.FirebaseMessaging
import com.offcampus.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object NotificationService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fetches the FCM registration token for this device and records it on the signed-in rider's document. */
    fun registerDeviceToken() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        scope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                if (!token.isNullOrBlank()) {
                    FirebaseRefs.riders.document(uid).update("fcmToken", token).await()
                }
            } catch (_: Exception) {}
        }
    }

    /** Sends a notification to specific FCM tokens via the Cloudflare Worker dispatch endpoint. */
    suspend fun send(tokens: List<String>, title: String, body: String, data: Map<String, String> = emptyMap()) {
        val validTokens = tokens.filter { it.isNotBlank() }.distinct()
        if (validTokens.isEmpty()) return

        withContext(Dispatchers.IO) {
            try {
                val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: return@withContext
                val conn = (URL("${BuildConfig.PHOTO_WORKER_URL}/notify").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }

                val payload = JSONObject().apply {
                    put("tokens", JSONArray(validTokens))
                    put("title", title)
                    put("body", body)
                    put("data", JSONObject(data))
                }

                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                conn.responseCode // trigger request
                conn.disconnect()
            } catch (_: Exception) {}
        }
    }

    private suspend fun getTokensForRiders(uids: List<String>): List<String> {
        val distinctUids = uids.filter { it.isNotBlank() }.distinct()
        if (distinctUids.isEmpty()) return emptyList()
        return try {
            val snapshot = FirebaseRefs.riders
                .whereIn(FieldPath.documentId(), distinctUids.take(30))
                .get().await()
            snapshot.documents.mapNotNull { it.getString("fcmToken") }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 1. To lobby master when a new rider joins lobby */
    fun notifyRiderJoinedLobby(masterId: String, joinerName: String, destination: String, lobbyId: String) {
        scope.launch {
            val tokens = getTokensForRiders(listOf(masterId))
            send(
                tokens = tokens,
                title = "New rider joined your lobby!",
                body = "$joinerName joined your lobby to $destination.",
                data = mapOf("type" to "LOBBY_JOIN", "lobbyId" to lobbyId)
            )
        }
    }

    /** 2. To riders and lobby master when lobby is full */
    fun notifyLobbyFull(memberIds: List<String>, destination: String, lobbyId: String) {
        scope.launch {
            val tokens = getTokensForRiders(memberIds)
            send(
                tokens = tokens,
                title = "Lobby is full! 🚗",
                body = "Your lobby to $destination is now full and ready to ride.",
                data = mapOf("type" to "LOBBY_FULL", "lobbyId" to lobbyId)
            )
        }
    }

    /** 3a. When a rider is removed from lobby */
    fun notifyRiderRemoved(riderId: String, destination: String, lobbyId: String) {
        scope.launch {
            val tokens = getTokensForRiders(listOf(riderId))
            send(
                tokens = tokens,
                title = "Removed from lobby",
                body = "You were removed from the lobby to $destination.",
                data = mapOf("type" to "REMOVED_FROM_LOBBY", "lobbyId" to lobbyId)
            )
        }
    }

    /** 3b. New friend request */
    fun notifyFriendRequest(recipientId: String, senderName: String) {
        scope.launch {
            val tokens = getTokensForRiders(listOf(recipientId))
            send(
                tokens = tokens,
                title = "New friend request",
                body = "$senderName sent you a friend request.",
                data = mapOf("type" to "FRIEND_REQUEST")
            )
        }
    }

    /** 3c. When blocked / unfriended */
    fun notifyUserBlocked(recipientId: String, blockerName: String) {
        scope.launch {
            val tokens = getTokensForRiders(listOf(recipientId))
            send(
                tokens = tokens,
                title = "OffCampus Update",
                body = "Your connection with $blockerName has been updated.",
                data = mapOf("type" to "BLOCKED")
            )
        }
    }

    /** 4a. Lobby chat message */
    fun notifyLobbyChat(memberIds: List<String>, senderId: String, senderName: String, destination: String, messageText: String, lobbyId: String) {
        scope.launch {
            val recipientIds = memberIds.filter { it != senderId }
            val tokens = getTokensForRiders(recipientIds)
            send(
                tokens = tokens,
                title = "$senderName in $destination",
                body = messageText,
                data = mapOf("type" to "LOBBY_CHAT", "lobbyId" to lobbyId)
            )
        }
    }

    /** 4b. Friend chat message */
    fun notifyFriendChat(recipientId: String, senderName: String, messageText: String, chatId: String) {
        scope.launch {
            val tokens = getTokensForRiders(listOf(recipientId))
            send(
                tokens = tokens,
                title = senderName,
                body = messageText,
                data = mapOf("type" to "FRIEND_CHAT", "chatId" to chatId)
            )
        }
    }
}
