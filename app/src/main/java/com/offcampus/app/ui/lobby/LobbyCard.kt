package com.offcampus.app.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.RideType
import java.text.SimpleDateFormat
import java.util.Locale

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

@Composable
fun LobbyCard(lobby: Lobby, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${lobby.gate} → ${lobby.destination}",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                RideTypeChip(lobby.rideType)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeFormat.format(lobby.departureTime.toDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SeatDots(filled = lobby.memberIds.size, total = lobby.maxSize)
            }
        }
    }
}

@Composable
fun RideTypeChip(rideType: RideType, modifier: Modifier = Modifier) {
    Text(
        text = if (rideType == RideType.AUTO) "Auto" else "Cab",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.background,
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(999.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp)
    )
}

@Composable
private fun SeatDots(filled: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { index ->
            val isFilled = index < filled
            Column(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (isFilled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    )
            ) {}
        }
    }
}
