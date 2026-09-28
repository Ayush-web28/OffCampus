package com.offcampus.app.ui.lobby

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.offcampus.app.data.PlaceSuggestion

/**
 * A free-text field that also shows live place suggestions underneath, the way Uber's pickup/
 * destination fields do (see CLAUDE.md Fix 16). [suggestions] is supplied by the caller's
 * ViewModel, which owns the debounce + [com.offcampus.app.data.PlaceSearchService] call — this
 * composable only renders whatever list it's given.
 *
 * Deliberately stays a plain text field underneath: tapping a suggestion fills it in, but typing
 * a place Photon doesn't know about and never selecting anything still works exactly like the
 * old field did, so this can't make posting a trip any less reliable than before.
 *
 * Uses a plain [DropdownMenu] anchored inside a [Box], not the Exposed-dropdown variant — that
 * one is built for a fixed set of choices tied to focus/click state, not a list that changes on
 * every keystroke, and would've fought this field's own show/hide logic more than helped it.
 *
 * A hand-rolled [androidx.compose.ui.window.Popup] was tried here instead of [DropdownMenu], to
 * force the suggestion list to always open below the field (see Fix 21) — but a plain [Popup],
 * unlike [DropdownMenu], turned out to dismiss the soft keyboard the moment it appeared, since
 * it doesn't carry the IME-safe window flags Material3's own [DropdownMenu] sets up internally.
 * Reverted back to [DropdownMenu] for that reason; the "opens on top of the field" problem it
 * flips into once the keyboard eats the lower half of the screen is instead solved by keeping
 * [DropdownMenu]'s own max height small enough to always fit in whatever room is actually left
 * below the field, so its built-in flip-to-avoid-offscreen logic never has a reason to trigger.
 */
@Composable
fun LocationAutocompleteField(
    value: String,
    suggestions: List<PlaceSuggestion>,
    isLoading: Boolean,
    onValueChange: (String) -> Unit,
    onSuggestionSelected: (PlaceSuggestion) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    // Dismissed manually (suggestion tapped, or the user taps elsewhere) rather than tied to
    // focus — losing focus by tapping a suggestion row would otherwise close the menu before
    // the click on it even registers.
    var dismissed by remember { mutableStateOf(false) }
    val showMenu = suggestions.isNotEmpty() && !dismissed

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                dismissed = false
                onValueChange(it)
            },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            // Loading takes priority — while a search is in flight there's nothing useful yet
            // to clear the field back to anyway, and swapping straight to the × the instant a
            // result lands is expected, not jarring.
            trailingIcon = if (isLoading) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else clearableTrailingIcon(value) { onValueChange("") },
            modifier = Modifier.fillMaxWidth()
        )
        // Styled like the rest of the app's cards (ticket-stub shape, themed surface/border)
        // instead of Material3's plain default dropdown, which reads as generic/off-brand here.
        // Row layout itself is closer to Uber's pickup/drop-off suggestions (bold place name,
        // muted single-line address, a thin divider between rows, no big icon background) than
        // Material3's own denser default DropdownMenuItem look.
        //
        // A first attempt sized this dynamically from WindowInsets.ime + the field's own window
        // position, to always fit whatever room is actually left below the field. It didn't work
        // in practice: this app has no edge-to-edge/IME-inset setup (no windowSoftInputMode or
        // WindowCompat.setDecorFitsSystemWindows call anywhere), so the keyboard's real height
        // never reaches WindowInsets.ime here, and LocalView.current.height isn't itself an
        // observable Compose value that recomposes when the window resizes around the keyboard —
        // together that meant the calculation quietly used the full un-shrunk screen height and
        // never actually constrained anything. Replaced with a fixed cap instead, chosen the same
        // way Fix 17's network timeout was: measured directly on a real device with the keyboard
        // up, against Destination (the lower, tighter-space field — Gate sits higher and has more
        // real room below it), rather than guessed. 110dp was the first value confirmed to open
        // below/above without ever covering the field; raised back to 150dp at request for a
        // taller, easier-to-read list — still small enough on Destination to open above the field
        // rather than over it, same as at 110dp, just showing more rows once it does.
        val maxDropdownHeight = 150.dp

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { dismissed = true },
            properties = PopupProperties(focusable = false),
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.heightIn(max = maxDropdownHeight)
        ) {
            suggestions.forEachIndexed { index, suggestion ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                suggestion.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (suggestion.subtitle.isNotBlank()) {
                                Text(
                                    suggestion.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        dismissed = true
                        onSuggestionSelected(suggestion)
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                )
                if (index != suggestions.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                }
            }
        }
    }
}
