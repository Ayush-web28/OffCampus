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

/**
 * Renders one rider's avatar. If [photoBase64] is a real uploaded photo (see
 * Rider.photoBase64), that's shown instead of the catalog entry — a custom photo always wins
 * over avatarId when both are present, since picking one is meant to replace the other, not
 * layer on top of it.
 */
@Composable
fun AvatarView(avatarId: String, photoBase64: String = "", size: Dp = 56.dp, modifier: Modifier = Modifier) {
    if (photoBase64.isNotBlank()) {
        // Decoding on every recomposition would be wasteful for something as expensive as a
        // bitmap decode — remember() keyed on the string itself so it only re-runs when the
        // photo actually changes, not on every unrelated recomposition of this row.
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
            return
        }
        // Corrupt/undecodable base64 falls through to the catalog avatar below rather than
        // showing a blank circle.
    }

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
