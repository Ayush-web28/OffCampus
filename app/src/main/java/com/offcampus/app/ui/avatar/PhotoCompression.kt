package com.offcampus.app.ui.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

// Firestore documents cap out at 1MiB and every rider list/friend list/chat header pulls this
// field along with the name it actually needs, so the photo has to stay small on purpose — a
// 128x128 JPEG at this quality is typically only a few KB, nowhere near either concern.
private const val TARGET_SIZE_PX = 128
private const val JPEG_QUALITY = 80

/**
 * Downscales whatever the user picked to a small square thumbnail and returns it as a
 * base64-encoded JPEG, ready to store directly in Rider.photoBase64. Returns null if the image
 * couldn't be read or decoded — the caller should treat that as "picking failed, try again"
 * rather than silently clearing an existing photo.
 */
fun compressImageToBase64(context: Context, uri: Uri): String? {
    val original = decodeBitmap(context, uri) ?: return null
    val square = centerCropToSquare(original)
    val thumbnail = Bitmap.createScaledBitmap(square, TARGET_SIZE_PX, TARGET_SIZE_PX, true)

    val output = ByteArrayOutputStream()
    thumbnail.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
    return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
}

private fun decodeBitmap(context: Context, uri: Uri): Bitmap? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
}.getOrNull()

private fun centerCropToSquare(bitmap: Bitmap): Bitmap {
    val side = minOf(bitmap.width, bitmap.height)
    val x = (bitmap.width - side) / 2
    val y = (bitmap.height - side) / 2
    return Bitmap.createBitmap(bitmap, x, y, side, side)
}
