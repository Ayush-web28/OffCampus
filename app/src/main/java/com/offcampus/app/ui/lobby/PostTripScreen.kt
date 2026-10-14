package com.offcampus.app.ui.lobby

import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offcampus.app.data.model.RideType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostTripScreen(
    onPosted: () -> Unit,
    onBack: () -> Unit,
    viewModel: PostTripViewModel = viewModel()
) {
    val form by viewModel.formState.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            OutlinedTextField(
                value = form.checkpoint,
                onValueChange = viewModel::onCheckpointChange,
                label = { Text("Checkpoint") },
                placeholder = { Text("Main Campus") },
                singleLine = true,
                trailingIcon = clearableTrailingIcon(form.checkpoint) { viewModel.onCheckpointChange("") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            LocationAutocompleteField(
                value = form.gate,
                suggestions = form.gateSuggestions,
                isLoading = form.gateSearching,
                onValueChange = viewModel::onGateChange,
                onSuggestionSelected = viewModel::onGateSuggestionSelected,
                label = "Location",
                placeholder = "Gate 2 or pickup point",
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
            LocationAutocompleteField(
                value = form.destination,
                suggestions = form.destinationSuggestions,
                isLoading = form.destinationSearching,
                onValueChange = viewModel::onDestinationChange,
                onSuggestionSelected = viewModel::onDestinationSuggestionSelected,
                label = "Destination",
                placeholder = "HSR Layout",
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
            )

            Text("Departure time", style = MaterialTheme.typography.titleMedium)
            OutlinedCard(
                onClick = {
                    val cal = Calendar.getInstance().apply { time = form.departureTime }
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            viewModel.onDepartureTimeSelected(hourOfDay, minute)
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        false
                    ).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Time",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDepartureTimeDisplay(form.departureTime),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
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

private fun formatDepartureTimeDisplay(date: Date): String {
    val cal = Calendar.getInstance().apply { time = date }
    val now = Calendar.getInstance()
    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    return if (cal.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR) || cal.get(Calendar.YEAR) != now.get(Calendar.YEAR)) {
        "Tomorrow, $timeStr"
    } else {
        "Today, $timeStr"
    }
}
