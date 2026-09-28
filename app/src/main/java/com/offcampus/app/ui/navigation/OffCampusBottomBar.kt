package com.offcampus.app.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    BottomTab(Routes.LOBBIES, "Lobbies", Icons.Default.Home),
    BottomTab(Routes.FRIENDS, "Friends", Icons.Default.Face),
    BottomTab(Routes.PROFILE, "Profile", Icons.Default.Person)
)

/** The three top-level tabs a signed-in rider switches between; auth/avatar-picker/post-trip/
 * detail screens are all reached by pushing on top of one of these, not by adding more tabs here.
 *
 * A custom bar rather than Material3's `NavigationBar` — that composable's own selection
 * indicator fades in/out per item independently, it doesn't share one indicator that visibly
 * slides between tabs the way this app's Phase 0 brief's spring motion already does elsewhere
 * (lobby cards, chat bubbles). Each tab gets a fixed, equal-width slot via [BoxWithConstraints],
 * so the shared pill's target x-offset is a simple index*width calculation rather than needing
 * live per-frame position tracking. */
@Composable
fun OffCampusBottomBar(currentRoute: String?, hasFriendsAlert: Boolean, onSelect: (String) -> Unit) {
    val selectedIndex = TABS.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
    Surface(tonalElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(80.dp)) {
            val tabWidth = maxWidth / TABS.size
            val pillWidth = tabWidth - 32.dp
            // Same spring spec Fix 20 already uses for a lobby card's own entrance — reused here
            // rather than a new value, so every spring-based motion in the app settles the same way.
            val pillOffsetX by animateDpAsState(
                targetValue = tabWidth * selectedIndex + 16.dp,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                label = "navPillOffset"
            )
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .offset(x = pillOffsetX)
                    .width(pillWidth)
                    .height(32.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            )
            Row(modifier = Modifier.fillMaxSize()) {
                TABS.forEachIndexed { index, tab ->
                    BottomTabItem(
                        tab = tab,
                        selected = index == selectedIndex,
                        showAlertDot = tab.route == Routes.FRIENDS && hasFriendsAlert,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(tab.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomTabItem(
    tab: BottomTab,
    selected: Boolean,
    showAlertDot: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Springs past 1f and settles back on selection — the same overshoot feel Fix 20's lobby-card
    // entrance uses, just applied to a tap instead of an item appearing in a list.
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "navIconScale"
    )
    val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer(scaleX = iconScale, scaleY = iconScale)
        ) {
            if (showAlertDot) {
                // A plain presence dot (Badge with no content), not a count — this covers two
                // things that don't share a unit (a pending friend request, an unread message
                // in any chat), so a single combined number wouldn't mean anything specific.
                BadgedBox(badge = { Badge() }) {
                    Icon(tab.icon, contentDescription = null, tint = tint)
                }
            } else {
                Icon(tab.icon, contentDescription = null, tint = tint)
            }
        }
        Text(
            tab.label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
