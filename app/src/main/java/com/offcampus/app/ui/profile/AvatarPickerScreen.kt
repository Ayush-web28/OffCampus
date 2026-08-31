package com.offcampus.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offcampus.app.ui.avatar.AvatarCatalog
import com.offcampus.app.ui.avatar.AvatarView

@Composable
fun AvatarPickerScreen(
    onDone: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val rider by viewModel.rider.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    var selectedId by remember(rider) { mutableStateOf(rider?.avatarId ?: AvatarCatalog.options.first().id) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Pick your avatar", style = MaterialTheme.typography.headlineMedium)
        Text(
            "No real photos — just something that feels like you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(AvatarCatalog.options) { option ->
                val isSelected = option.id == selectedId
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        )
                        .border(
                            width = if (isSelected) 3.dp else 0.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                        .clickable { selectedId = option.id }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarView(avatarId = option.id, size = 64.dp)
                }
            }
        }

        Button(
            onClick = { viewModel.updateAvatar(selectedId, onComplete = onDone) },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Save and continue")
        }
    }
}
