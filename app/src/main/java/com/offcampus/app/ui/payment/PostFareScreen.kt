package com.offcampus.app.ui.payment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.offcampus.app.ui.avatar.AvatarView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostFareScreen(
    lobbyId: String,
    onPosted: () -> Unit,
    onBack: () -> Unit
) {
    val viewModel: PostFareViewModel = viewModel(
        factory = viewModelFactory { initializer { PostFareViewModel(lobbyId) } }
    )
    val form by viewModel.formState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split the fare") },
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

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp)) {
            Text(
                "Trip to ${form.destination}",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OutlinedTextField(
                value = form.totalFare,
                onValueChange = viewModel::onTotalFareChange,
                label = { Text("Total fare (₹)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                FilterChip(
                    selected = form.splitMode == SplitMode.EQUAL,
                    onClick = { viewModel.onSplitModeChange(SplitMode.EQUAL) },
                    label = { Text("Split equally") }
                )
                FilterChip(
                    selected = form.splitMode == SplitMode.CUSTOM,
                    onClick = { viewModel.onSplitModeChange(SplitMode.CUSTOM) },
                    label = { Text("Custom amounts") }
                )
            }

            val total = form.totalFare.toDoubleOrNull()
            val memberCount = form.otherMembers.size + 1
            val equalShare = if (total != null && memberCount > 0) total / memberCount else 0.0

            form.otherMembers.forEach { member ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AvatarView(avatarId = member.avatarId, photoUrl = member.photoUrl, photoBase64 = member.photoBase64, size = 36.dp)
                    Text(
                        member.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                    )
                    if (form.splitMode == SplitMode.EQUAL) {
                        Text(
                            "₹%.2f".format(equalShare),
                            style = MaterialTheme.typography.labelLarge
                        )
                    } else {
                        OutlinedTextField(
                            value = form.customAmounts[member.id] ?: "",
                            onValueChange = { viewModel.onCustomAmountChange(member.id, it) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(110.dp)
                        )
                    }
                }
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
                onClick = { viewModel.submit(onPosted) },
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Confirm split")
                }
            }
        }
    }
}
