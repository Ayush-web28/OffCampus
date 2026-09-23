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
import com.offcampus.app.ui.avatar.compressImageToJpeg
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
    val error by viewModel.error.collectAsStateWithLifecycle()
    var selectedId by remember(rider) { mutableStateOf(rider?.avatarId ?: AvatarCatalog.options.first().id) }
    // A freshly picked photo, held locally until "Save and continue" — null means "use
    // selectedId instead", not "no photo at all" (the rider's existing photo, if any, is what's
    // previewed until a new one is picked or a catalog avatar is tapped).
    var pendingPhoto by remember { mutableStateOf<ByteArray?>(null) }
    // AvatarView's inline-photo path doubles as the preview for bytes that aren't uploaded yet.
    val previewBase64 = remember(pendingPhoto) {
        pendingPhoto?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
    }
    var presetChosen by remember { mutableStateOf(false) } // tapped a catalog avatar this visit
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
            val jpeg = withContext(Dispatchers.IO) { compressImageToJpeg(context, uri) }
            isCompressing = false
            if (jpeg != null) {
                pendingPhoto = jpeg
                presetChosen = false
            }
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
                // The rider's saved photo only counts as the preview until they pick something else.
                photoUrl = if (pendingPhoto == null && !presetChosen) rider?.photoUrl.orEmpty() else "",
                photoBase64 = previewBase64 ?: if (presetChosen) "" else rider?.photoBase64.orEmpty(),
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
                val hasSavedPhoto = rider?.photoUrl?.isNotBlank() == true || rider?.photoBase64?.isNotBlank() == true
                val isSelected = option.id == selectedId && pendingPhoto == null && (presetChosen || !hasSavedPhoto)
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
                            presetChosen = true
                        }
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AvatarView(avatarId = option.id, size = 64.dp)
                }
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        }
        Button(
            onClick = {
                val photo = pendingPhoto
                if (photo != null) viewModel.uploadPhoto(photo, onComplete = onDone)
                else viewModel.updateAvatar(selectedId, onComplete = onDone)
            },
            enabled = !isSaving && !isCompressing,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Save and continue")
        }
    }
}
