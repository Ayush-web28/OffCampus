package com.offcampus.app.ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offcampus.app.ui.avatar.AvatarView

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onChangeAvatar: () -> Unit,
    onOpenTripHistory: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val rider by viewModel.rider.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val current = rider
        if (current == null) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 64.dp))
        } else {
            AvatarView(
                avatarId = current.avatarId,
                photoUrl = current.photoUrl,
                photoBase64 = current.photoBase64,
                size = 96.dp,
                modifier = Modifier.padding(top = 32.dp)
            )
            Text(current.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            Text(
                current.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            RatingRow(
                average = current.ratingAverage,
                count = current.ratingCount,
                modifier = Modifier.padding(top = 20.dp)
            )

            Text(
                "${current.friendIds.size} friends",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            OutlinedButton(onClick = onChangeAvatar, modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                Text("Change avatar")
            }
            OutlinedButton(onClick = onOpenTripHistory, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Trip history")
            }
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Sign out")
            }
        }
    }
}

@Composable
private fun RatingRow(average: Double, count: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        val filledStars = average.toInt().coerceIn(0, 5)
        repeat(5) { index ->
            Text(
                if (index < filledStars) "★" else "☆",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.titleLarge
            )
        }
        Text(
            text = if (count > 0) "%.1f".format(average) else "No ratings yet",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
