package com.offcampus.app.ui.lobby

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.LobbyStatus
import com.offcampus.app.data.model.RideType
import java.text.SimpleDateFormat
import java.util.Locale

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

@Composable
fun LobbyCard(lobby: Lobby, hasUnreadChat: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
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
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${lobby.gate} → ${lobby.destination}",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Same plain alert dot as the Friends tab, same meaning: an unread message —
                    // here, in this specific lobby's own chat.
                    if (hasUnreadChat) {
                        Column(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(8.dp)
                                .background(color = MaterialTheme.colorScheme.error, shape = CircleShape)
                        ) {}
                    }
                }
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
            // A dot's own fill state flips instantly whenever someone joins/leaves elsewhere
            // in the lobby (the whole list re-renders off the live Firestore listener) — this
            // animates the color swap itself so a seat filling reads as a small transition
            // rather than a hard cut.
            val color by animateColorAsState(
                targetValue = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "seatDotFill"
            )
            Column(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = color, shape = CircleShape)
            ) {}
        }
    }
}

@Composable
fun ActiveRideCard(
    lobby: Lobby,
    isMaster: Boolean,
    hasUnreadChat: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isMaster) "YOUR ACTIVE LOBBY (MASTER)" else "YOUR ACTIVE LOBBY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    if (hasUnreadChat) {
                        Column(
                            modifier = Modifier
                                .size(8.dp)
                                .background(color = MaterialTheme.colorScheme.error, shape = CircleShape)
                        ) {}
                    }
                }

                Text(
                    text = if (lobby.status == LobbyStatus.LOCKED) "LOCKED" else "OPEN",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (lobby.status == LobbyStatus.LOCKED) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(
                            (if (lobby.status == LobbyStatus.LOCKED) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary).copy(alpha = 0.15f),
                            RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Text(
                text = "${lobby.gate} → ${lobby.destination}",
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RideTypeChip(lobby.rideType)
                    Text(
                        text = timeFormat.format(lobby.departureTime.toDate()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${lobby.memberIds.size}/${lobby.maxSize} riders",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Open lobby →",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
