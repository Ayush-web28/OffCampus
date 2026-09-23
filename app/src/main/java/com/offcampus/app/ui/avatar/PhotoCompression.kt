package com.offcampus.app.ui.avatar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream

// The photo is shrunk before it ever leaves the phone: less to upload, less to store, and it
// stays well under the Worker's size ceiling. 256px is sharp enough for the largest place an
// avatar is shown (the 96dp profile picture) and typically lands around 10-40 KB.
private const val TARGET_SIZE_PX = 256
private const val JPEG_QUALITY = 80

/**
 * Downscales whatever the user picked to a small square JPEG and returns its bytes, ready to send
 * to the photo Worker. Returns null if the image couldn't be read or decoded — the caller should
 * treat that as "picking failed, try again" rather than silently clearing an existing photo.
 */
fun compressImageToJpeg(context: Context, uri: Uri): ByteArray? {
    val original = decodeBitmap(context, uri) ?: return null
    val square = centerCropToSquare(original)
    val thumbnail = Bitmap.createScaledBitmap(square, TARGET_SIZE_PX, TARGET_SIZE_PX, true)

    val output = ByteArrayOutputStream()
    thumbnail.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
    return output.toByteArray()
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
