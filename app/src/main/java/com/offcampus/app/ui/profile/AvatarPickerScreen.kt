package com.offcampus.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.offcampus.app.ui.avatar.AvatarCatalog
import com.offcampus.app.ui.avatar.AvatarView
import com.offcampus.app.ui.avatar.compressImageToBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AvatarPickerScreen(
    onDone: () -> Unit,
    viewModel: ProfileViewModel = viewModel()
) {
    val rider by viewModel.rider.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    var selectedId by remember(rider) { mutableStateOf(rider?.avatarId ?: AvatarCatalog.options.first().id) }
    // A freshly picked photo, held locally until "Save and continue" — null means "use
    // selectedId instead", not "no photo at all" (the rider's existing photoBase64, if any,
    // is what's previewed until a new one is picked or a catalog avatar is tapped).
    var pendingPhoto by remember { mutableStateOf<String?>(null) }
    var isCompressing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isCompressing = true
        scope.launch {
            // Reading the file and decoding/resizing the bitmap are blocking I/O and CPU work —
            // Dispatchers.IO keeps that off the UI thread the same way every Firestore .await()
            // call elsewhere in this app already does implicitly via the Firebase SDK's own
            // background dispatch.
            val base64 = withContext(Dispatchers.IO) { compressImageToBase64(context, uri) }
            isCompressing = false
            if (base64 != null) pendingPhoto = base64
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Pick your avatar", style = MaterialTheme.typography.headlineMedium)
        Text(
            "A preset avatar, or your own photo if you'd rather.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
        )

        Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
            AvatarView(
                avatarId = selectedId,
                photoBase64 = pendingPhoto ?: rider?.photoBase64.orEmpty(),
                size = 88.dp
            )
        }
        if (isCompressing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp))
        }
        OutlinedButton(
            onClick = { pickPhoto.launch("image/*") },
            enabled = !isCompressing,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp)
        ) {
            Text("Upload a photo")
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(AvatarCatalog.options) { option ->
                // A pending photo and a catalog pick are mutually exclusive in the UI too, not
                // just on save — tapping a preset here always clears whatever photo was picked,
                // same as picking a photo above always overrides whichever preset was selected.
                val isSelected = option.id == selectedId && pendingPhoto == null
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
                        .clickable {
                            selectedId = option.id
                            pendingPhoto = null
                        }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarView(avatarId = option.id, size = 64.dp)
                }
            }
        }

        Button(
            onClick = {
                val photo = pendingPhoto
                if (photo != null) viewModel.updatePhoto(photo, onComplete = onDone)
                else viewModel.updateAvatar(selectedId, onComplete = onDone)
            },
            enabled = !isSaving && !isCompressing,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Save and continue")
        }
    }
}
