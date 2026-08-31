package com.offcampus.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** The three top-level tabs a signed-in rider switches between; auth/avatar-picker/post-trip/
 * detail screens are all reached by pushing on top of one of these, not by adding more tabs here. */
@Composable
fun OffCampusBottomBar(currentRoute: String?, pendingFriendRequests: Int, onSelect: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.LOBBIES,
            onClick = { onSelect(Routes.LOBBIES) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Lobbies") }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.FRIENDS,
            onClick = { onSelect(Routes.FRIENDS) },
            icon = {
                BadgedBox(badge = {
                    if (pendingFriendRequests > 0) Badge { Text("$pendingFriendRequests") }
                }) {
                    Icon(Icons.Default.Face, contentDescription = null)
                }
            },
            label = { Text("Friends") }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.PROFILE,
            onClick = { onSelect(Routes.PROFILE) },
            icon = { Icon(Icons.Default.Person, contentDescription = null) },
            label = { Text("Profile") }
        )
    }
}
