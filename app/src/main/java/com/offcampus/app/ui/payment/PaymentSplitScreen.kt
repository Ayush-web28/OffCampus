package com.offcampus.app.ui.payment

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.offcampus.app.data.model.PaymentParticipant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentSplitScreen(
    lobbyId: String,
    onBack: () -> Unit,
    onReport: (reportedUserId: String) -> Unit,
    onRate: (ratedUserId: String) -> Unit
) {
    val viewModel: PaymentSplitViewModel = viewModel(
        factory = viewModelFactory { initializer { PaymentSplitViewModel(lobbyId) } }
    )
    val split by viewModel.split.collectAsStateWithLifecycle()
    val names by viewModel.riderNames.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment split", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        val current = split
        if (current == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(top = 64.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "₹%.2f total".format(current.totalFare),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "Paid by ${names[current.paidBy] ?: "…"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        // Only a participant (not the payer looking at their own split) gets this
                        if (viewModel.currentUid != current.paidBy) {
                            Text(
                                text = "Rate",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .clickable { onRate(current.paidBy) }
                            )
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(current.participants, key = { it.userId }) { participant ->
                    ParticipantRow(
                        participant = participant,
                        name = names[participant.userId] ?: "…",
                        currentUid = viewModel.currentUid,
                        isPayer = viewModel.currentUid == current.paidBy,
                        onMarkSent = { viewModel.markSent(participant.userId) },
                        onConfirmReceived = { viewModel.confirmReceived(participant.userId) },
                        onReport = { onReport(participant.userId) },
                        onRate = { onRate(participant.userId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantRow(
    participant: PaymentParticipant,
    name: String,
    currentUid: String?,
    isPayer: Boolean,
    onMarkSent: () -> Unit,
    onConfirmReceived: () -> Unit,
    onReport: () -> Unit,
    onRate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "₹%.2f".format(participant.owedAmount),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Crossfades between pending/sent/settled states
                Crossfade(
                    targetState = Triple(participant.senderAck, participant.receiverAck, participant.settled),
                    label = "ackStatus"
                ) { (senderAck, receiverAck, settled) ->
                    when {
                        settled -> StatusChip("Settled", MaterialTheme.colorScheme.tertiary, showCheckmark = true)
                        senderAck && isPayer -> Button(
                            onClick = onConfirmReceived,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) { Text("Confirm received", fontWeight = FontWeight.Bold) }
                        senderAck -> StatusChip("Sent — waiting", MaterialTheme.colorScheme.secondary)
                        participant.userId == currentUid -> Button(
                            onClick = onMarkSent,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            )
                        ) { Text("Mark as sent", fontWeight = FontWeight.Bold) }
                        else -> StatusChip("Pending", MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Can't act on yourself
            if (participant.userId != currentUid) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "Rate",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onRate)
                    )
                    Text(
                        text = "Report",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable(onClick = onReport)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color, showCheckmark: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (showCheckmark) {
            val checkmarkVisible = remember { MutableTransitionState(false) }
            LaunchedEffect(Unit) { checkmarkVisible.targetState = true }
            AnimatedVisibility(
                visibleState = checkmarkVisible,
                enter = scaleIn(spring(dampingRatio = 0.8f, stiffness = 380f)) + fadeIn()
            ) {
                Text(
                    text = "✓ ",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
