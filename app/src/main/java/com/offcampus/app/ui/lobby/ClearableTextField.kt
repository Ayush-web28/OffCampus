package com.offcampus.app.ui.lobby

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A small "×" `trailingIcon` for a text field, shown only once there's something to clear —
 * so a rider can clear Checkpoint, Gate, Destination or the lobby search bar in one tap instead
 * of holding backspace. Deliberately not used in chat's message input, which was never part of
 * this request and works differently (send-on-submit, not a filter/value to clear).
 *
 * Always returns a composable (never null) so the same [AnimatedVisibility] instance stays in
 * the tree across recompositions and can animate its own show/hide — returning null outright
 * when [value] was empty (the original version) meant the icon's appearance/disappearance was an
 * instant structural swap with nothing to animate. A near-full-size fragment briefly looked
 * broken on the emulator during testing — turned out to be the loading spinner (queryLoading, see
 * LobbyBrowseScreen/LocationAutocompleteField) caught mid-rotation in the same trailing-icon slot,
 * not this icon at all; once the query actually finished resolving the × rendered correctly. The
 * same overshoot spring used elsewhere in the app on appear; a plain scale-down on exit, since a
 * bouncy exit would read as the icon lingering rather than leaving. */
fun clearableTrailingIcon(value: String, onClear: () -> Unit): @Composable (() -> Unit) = {
    AnimatedVisibility(
        visible = value.isNotEmpty(),
        enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 380f)) + fadeIn(),
        exit = scaleOut(targetScale = 0.6f) + fadeOut()
    ) {
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
        }
    }
}
