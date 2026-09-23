package com.offcampus.app.data

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.offcampus.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the Cloudflare Worker in worker/ that stores profile photos. Every call carries the
 * signed-in rider's Firebase ID token; the Worker verifies it and works out WHICH rider it is from
 * the token itself, so there's no way to ask it to touch someone else's photo.
 */
object PhotoService {
    /** Uploads [jpeg] as the current rider's photo and returns the URL to store on their profile. */
    suspend fun upload(jpeg: ByteArray): String = withContext(Dispatchers.IO) {
        val connection = open("/photo", "POST")
        connection.setRequestProperty("Content-Type", "image/jpeg")
        connection.doOutput = true
        connection.outputStream.use { it.write(jpeg) }
        val body = readBody(connection)
        JSONObject(body).getString("url")
    }

    /** Removes the current rider's stored photo. Best-effort: callers shouldn't fail over this. */
    suspend fun delete() = withContext(Dispatchers.IO) {
        readBody(open("/photo", "DELETE"))
        Unit
    }

    private suspend fun open(path: String, method: String): HttpURLConnection {
        val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw IllegalStateException("You need to be signed in to change your photo.")
        return (URL(BuildConfig.PHOTO_WORKER_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $token")
        }
    }

    private fun readBody(connection: HttpURLConnection): String {
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val message = connection.errorStream?.bufferedReader()?.readText()
                    ?.let { runCatching { JSONObject(it).getString("error") }.getOrNull() }
                throw java.io.IOException(message ?: "Photo upload failed ($code).")
            }
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
