package com.offcampus.app.ui.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.offcampus.app.ui.avatar.AvatarView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    lobbyId: String,
    reportedUserId: String,
    onBack: () -> Unit
) {
    val viewModel: ReportViewModel = viewModel(
        factory = viewModelFactory { initializer { ReportViewModel(lobbyId, reportedUserId) } }
    )
    val form by viewModel.formState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report rider") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (form.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 64.dp))
            }
            return@Scaffold
        }

        // Swaps the whole form out for a confirmation rather than popping back immediately —
        // the reporter gets to see it actually went through before leaving the screen.
        if (form.isSubmitted) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Report submitted", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "We've logged this against ${form.reportedUserName}. Thanks for flagging it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Text("Done")
                }
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                AvatarView(avatarId = form.reportedUserAvatarId, size = 48.dp)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(form.reportedUserName, style = MaterialTheme.typography.titleLarge)
                    if (form.disputedAmount > 0.0) {
                        Text(
                            "₹%.2f still owed on this trip".format(form.disputedAmount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Text(
                "What happened?",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Four fixed reasons laid out two-per-row — a plain Row wrap, not a LazyRow, since
            // the list never changes and never needs scrolling or item recycling.
            ReportReason.entries.chunked(2).forEach { rowReasons ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    rowReasons.forEach { reason ->
                        FilterChip(
                            selected = form.selectedReason == reason,
                            onClick = { viewModel.onReasonSelect(reason) },
                            label = { Text(reason.label) }
                        )
                    }
                }
            }

            if (form.selectedReason == ReportReason.OTHER) {
                OutlinedTextField(
                    value = form.customReason,
                    onValueChange = viewModel::onCustomReasonChange,
                    label = { Text("Describe what happened") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            form.errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            Button(
                onClick = viewModel::submit,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Submit report")
                }
            }
        }
    }
}
