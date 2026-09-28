package com.offcampus.app.ui.lobby

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
 * this request and works differently (send-on-submit, not a filter/value to clear). */
fun clearableTrailingIcon(value: String, onClear: () -> Unit): @Composable (() -> Unit)? {
    if (value.isEmpty()) return null
    return {
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(20.dp))
        }
    }
}
