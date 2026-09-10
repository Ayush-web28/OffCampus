package com.offcampus.app.ui.payment

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onReport: (reportedUserId: String) -> Unit
) {
    val viewModel: PaymentSplitViewModel = viewModel(
        factory = viewModelFactory { initializer { PaymentSplitViewModel(lobbyId) } }
    )
    val split by viewModel.split.collectAsStateWithLifecycle()
    val names by viewModel.riderNames.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment split") },
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
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 64.dp))
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("₹%.2f total".format(current.totalFare), style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Paid by ${names[current.paidBy] ?: "…"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(current.participants, key = { it.userId }) { participant ->
                    ParticipantRow(
                        participant = participant,
                        name = names[participant.userId] ?: "…",
                        currentUid = viewModel.currentUid,
                        isPayer = viewModel.currentUid == current.paidBy,
                        onMarkSent = { viewModel.markSent(participant.userId) },
                        onConfirmReceived = { viewModel.confirmReceived(participant.userId) },
                        onReport = { onReport(participant.userId) }
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
    onReport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "₹%.2f".format(participant.owedAmount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Crossfades between pending/sent/settled states so a status flip (which can arrive
            // from the other person's device, not just this one) reads as a small reward, not a snap.
            Crossfade(
                targetState = Triple(participant.senderAck, participant.receiverAck, participant.settled),
                label = "ackStatus"
            ) { (senderAck, receiverAck, settled) ->
                when {
                    settled -> StatusChip("Settled", MaterialTheme.colorScheme.tertiary)
                    senderAck && isPayer -> Button(onClick = onConfirmReceived) { Text("Confirm received") }
                    senderAck -> StatusChip("Sent — waiting", MaterialTheme.colorScheme.secondary)
                    participant.userId == currentUid -> Button(onClick = onMarkSent) { Text("Mark as sent") }
                    else -> StatusChip("Pending", MaterialTheme.colorScheme.error)
                }
            }
        }

        // Can't report yourself, so this row's own participant is skipped — everyone else in
        // the split (whichever side of the ack they're on) can be flagged from right here.
        if (participant.userId != currentUid) {
            Text(
                "Report",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp)
                    .clickable(onClick = onReport)
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
