package com.offcampus.app.ui.avatar

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

/**
 * Renders one rider's avatar. Priority, highest first:
 *  1. [photoUrl] — the photo served by the Cloudflare Worker (loaded and cached by Coil)
 *  2. [photoBase64] — a legacy photo stored inline on the rider document, from before the Worker
 *  3. the preset catalog avatar for [avatarId]
 * A custom photo always wins over the catalog avatar when present, since picking one is meant to
 * replace the other, not layer on top of it.
 */
@Composable
fun AvatarView(
    avatarId: String,
    photoUrl: String = "",
    photoBase64: String = "",
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    when {
        photoUrl.isNotBlank() -> SubcomposeAsyncImage(
            model = photoUrl,
            contentDescription = "Profile photo",
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
            // While it loads, and if it ever can't (offline, deleted), the catalog avatar is
            // shown instead of an empty circle.
            loading = { CatalogAvatar(avatarId, size) },
            error = { CatalogAvatar(avatarId, size) }
        )
        photoBase64.isNotBlank() -> LegacyBase64Avatar(avatarId, photoBase64, size, modifier)
        else -> CatalogAvatar(avatarId, size, modifier)
    }
}

@Composable
private fun LegacyBase64Avatar(avatarId: String, photoBase64: String, size: Dp, modifier: Modifier) {
    // Decoding on every recomposition would be wasteful for something as expensive as a bitmap
    // decode — remember() keyed on the string so it only re-runs when the photo actually changes.
    val bitmap = remember(photoBase64) {
        runCatching {
            val bytes = Base64.decode(photoBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Profile photo",
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape)
        )
    } else {
        CatalogAvatar(avatarId, size, modifier)
    }
}

@Composable
private fun CatalogAvatar(avatarId: String, size: Dp, modifier: Modifier = Modifier) {
    val option = AvatarCatalog.byId(avatarId)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(option.tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = option.emoji, fontSize = (size.value * 0.5f).sp)
    }
}
