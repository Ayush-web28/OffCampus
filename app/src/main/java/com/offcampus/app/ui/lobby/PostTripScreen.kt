package com.offcampus.app.ui.lobby

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offcampus.app.data.model.RideType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostTripScreen(
    onPosted: () -> Unit,
    onBack: () -> Unit,
    viewModel: PostTripViewModel = viewModel()
) {
    val form by viewModel.formState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Post a trip") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
        ) {
            OutlinedTextField(
                value = form.checkpoint,
                onValueChange = viewModel::onCheckpointChange,
                label = { Text("Checkpoint") },
                placeholder = { Text("Main Campus") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = form.gate,
                onValueChange = viewModel::onGateChange,
                label = { Text("Gate") },
                placeholder = { Text("Gate 2") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            OutlinedTextField(
                value = form.destination,
                onValueChange = viewModel::onDestinationChange,
                label = { Text("Destination") },
                placeholder = { Text("HSR Layout") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            )

            Text("Leaving in", style = MaterialTheme.typography.titleMedium)
            ChipRow(modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)) {
                DEPARTURE_OFFSET_MINUTES.forEach { minutes ->
                    FilterChip(
                        selected = form.departureInMinutes == minutes,
                        onClick = { viewModel.onDepartureChange(minutes) },
                        label = { Text(formatOffset(minutes)) }
                    )
                }
            }

            Text("Group size", style = MaterialTheme.typography.titleMedium)
            ChipRow(modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)) {
                groupSizeOptions(form.rideType).forEach { size ->
                    FilterChip(
                        selected = form.maxSize == size,
                        onClick = { viewModel.onMaxSizeChange(size) },
                        label = { Text("$size") }
                    )
                }
            }

            Text("Ride type", style = MaterialTheme.typography.titleMedium)
            ChipRow(modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)) {
                FilterChip(
                    selected = form.rideType == RideType.AUTO,
                    onClick = { viewModel.onRideTypeChange(RideType.AUTO) },
                    label = { Text("Auto") }
                )
                FilterChip(
                    selected = form.rideType == RideType.CAB,
                    onClick = { viewModel.onRideTypeChange(RideType.CAB) },
                    label = { Text("Cab") }
                )
            }

            form.errorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Button(
                onClick = { viewModel.submit(onPosted) },
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Post lobby")
                }
            }
        }
    }
}

@Composable
private fun ChipRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Horizontally scrollable: 6 "leaving in" chips don't all fit on narrower screens, and a
    // fixed Row would just silently clip the overflow instead of making it reachable.
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) { content() }
}

private fun formatOffset(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} hr"
    else -> "${minutes / 60}.5 hr"
}
