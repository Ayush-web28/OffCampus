package com.offcampus.app.ui.avatar

import androidx.compose.ui.graphics.Color
import com.offcampus.app.ui.theme.LightAmber
import com.offcampus.app.ui.theme.LightCoral
import com.offcampus.app.ui.theme.LightGreen
import com.offcampus.app.ui.theme.LightViolet

data class AvatarOption(val id: String, val emoji: String, val tint: Color)

/**
 * Fixed set of animal avatars — "no real photos" per the brief. Ids match what seed/seed.js
 * already wrote into Firestore ("avatar_1".."avatar_6"), so seeded riders and real signups
 * resolve through the same catalog. Tints are drawn only from the four brand accent colors
 * (never a new hue) so avatars stay inside the Phase 0 palette.
 */
object AvatarCatalog {
    val options = listOf(
        AvatarOption("avatar_1", "🦊", LightAmber),  // fox
        AvatarOption("avatar_2", "🐼", LightViolet), // panda
        AvatarOption("avatar_3", "🐧", LightGreen),  // penguin
        AvatarOption("avatar_4", "🐨", LightCoral),  // koala
        AvatarOption("avatar_5", "🦦", LightAmber),  // otter
        AvatarOption("avatar_6", "🦉", LightViolet), // owl
        AvatarOption("avatar_7", "🐢", LightGreen),  // turtle
        AvatarOption("avatar_8", "🐬", LightCoral)   // dolphin
    )

    fun byId(id: String): AvatarOption = options.find { it.id == id } ?: options.first()
}
