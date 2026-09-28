package com.offcampus.app.ui.lobby

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
 */
@Composable
fun LocationAutocompleteField(
    value: String,
    suggestions: List<PlaceSuggestion>,
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
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { dismissed = true },
            properties = PopupProperties(focusable = false)
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(suggestion.name, style = MaterialTheme.typography.bodyLarge)
                            if (suggestion.subtitle.isNotBlank()) {
                                Text(
                                    suggestion.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = {
                        dismissed = true
                        onSuggestionSelected(suggestion)
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}
