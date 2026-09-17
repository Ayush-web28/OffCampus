package com.offcampus.app.ui.rating

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.offcampus.app.ui.avatar.AvatarView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateRiderScreen(
    lobbyId: String,
    ratedUserId: String,
    onBack: () -> Unit
) {
    val viewModel: RateRiderViewModel = viewModel(
        factory = viewModelFactory { initializer { RateRiderViewModel(lobbyId, ratedUserId) } }
    )
    val form by viewModel.formState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rate rider") },
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

        if (form.isSubmitted) {
            Column(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Rating submitted", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Thanks for rating ${form.ratedUserName}.",
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 28.dp)) {
                AvatarView(avatarId = form.ratedUserAvatarId, size = 48.dp)
                Text(
                    form.ratedUserName,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Text(
                "How was travelling with ${form.ratedUserName}?",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Plain tappable glyphs rather than a slider or a Material rating widget (Compose
            // has no built-in star rating component) — matches the "★"/"☆" glyphs already used
            // to display ratings read-only on ProfileScreen, just interactive here.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { star ->
                    Text(
                        if (star <= form.selectedStars) "★" else "☆",
                        fontSize = 40.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.clickable { viewModel.onStarsSelect(star) }
                    )
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
                onClick = viewModel::submit,
                enabled = !form.isSubmitting,
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
            ) {
                if (form.isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Submit rating")
                }
            }
        }
    }
}
