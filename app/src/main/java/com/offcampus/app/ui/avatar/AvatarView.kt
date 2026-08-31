package com.offcampus.app.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Renders one rider's avatar: a low-alpha tint circle (from AvatarCatalog) with the emoji centered. */
@Composable
fun AvatarView(avatarId: String, size: Dp = 56.dp, modifier: Modifier = Modifier) {
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
