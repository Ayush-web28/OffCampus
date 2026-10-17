package com.offcampus.app.ui.lobby

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offcampus.app.data.model.Lobby
import com.offcampus.app.data.model.LobbyStatus
import com.offcampus.app.data.model.RideType
import java.text.SimpleDateFormat
import java.util.Locale

private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

/**
 * Stitch Neo-Transit styled Lobby card for the browse list.
 */
@Composable
fun LobbyCard(lobby: Lobby, hasUnreadChat: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lobby.gate,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = " → ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = lobby.destination,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (hasUnreadChat) {
                        Box(
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(8.dp)
                                .background(color = MaterialTheme.colorScheme.error, shape = CircleShape)
                        )
                    }
                }
                RideTypeChip(lobby.rideType, modifier = Modifier.padding(start = 8.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = timeFormat.format(lobby.departureTime.toDate()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• ${lobby.memberIds.size}/${lobby.maxSize} riders",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                SeatDots(filled = lobby.memberIds.size, total = lobby.maxSize)
            }
        }
    }
}

/**
 * Stitch Neo-Transit RideType badge chip.
 */
@Composable
fun RideTypeChip(rideType: RideType, modifier: Modifier = Modifier) {
    val isAuto = rideType == RideType.AUTO
    val bgColor = if (isAuto) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f)
    val textColor = if (isAuto) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary

    Text(
        text = if (isAuto) "Auto" else "Cab",
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = textColor,
        modifier = modifier
            .background(bgColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun SeatDots(filled: Int, total: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { index ->
            val isFilled = index < filled
            val color by animateColorAsState(
                targetValue = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "seatDotFill"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = color, shape = CircleShape)
            )
        }
    }
}

/**
 * Stitch Neo-Transit Master Active Ride Card pinned at top of the browse list.
 */
@Composable
fun ActiveRideCard(
    lobby: Lobby,
    isMaster: Boolean,
    onClick: () -> Unit,
    hasUnreadChat: Boolean = false,
    unreadChatCount: Int = 0,
    onOpenChat: () -> Unit = onClick,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            // Indigo accent indicator bar across the top of the card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Text(
                            text = if (isMaster) "YOUR ACTIVE LOBBY (MASTER)" else "YOUR ACTIVE LOBBY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        if (hasUnreadChat) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(color = MaterialTheme.colorScheme.error, CircleShape)
                            )
                        }
                    }

                    // Status Pill
                    val isLocked = lobby.status == LobbyStatus.LOCKED
                    val statusBg = if (isLocked) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
                    }
                    val statusColor = if (isLocked) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(statusBg, RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        if (isLocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = statusColor
                            )
                        }
                        Text(
                            text = if (isLocked) "LOCKED" else "OPEN",
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Route Text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        text = lobby.gate,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = " → ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = lobby.destination,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Details Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        RideTypeChip(lobby.rideType)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = timeFormat.format(lobby.departureTime.toDate()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "• ${lobby.memberIds.size}/${lobby.maxSize} riders",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        onClick = onOpenChat,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            BadgedBox(
                                badge = {
                                    if (hasUnreadChat || unreadChatCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ) {
                                            if (unreadChatCount > 0) {
                                                Text(
                                                    text = if (unreadChatCount > 9) "9+" else "$unreadChatCount",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Forum,
                                    contentDescription = "Open lobby chat",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stitch Neo-Transit Pending Payment Banner.
 */
@Composable
fun PendingPaymentBanner(
    info: PendingPaymentInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.12f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (info.isPayer) "PAYMENT AWAITING CONFIRMATION" else "PENDING PAYMENT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (info.isPayer) {
                        "₹${"%.0f".format(info.amount)} pending for trip to ${info.destination}"
                    } else {
                        "You owe ₹${"%.0f".format(info.amount)} for trip to ${info.destination}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (info.isPayer) "View →" else "Settle →",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
