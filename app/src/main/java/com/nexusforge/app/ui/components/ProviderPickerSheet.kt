package com.nexusforge.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.ProviderProfile
import com.nexusforge.app.data.SettingsStore

/**
 * "I have three Gemini keys and keep having to re-paste one" — this is the fix: every saved
 * credential is fully stored, switching is a tap on a name, and adding a new one only asks
 * once. Mirrors ProjectPickerSheet's shape deliberately — same mental model, different resource.
 */
@Composable
fun ProviderPickerSheet(
    profiles: List<ProviderProfile>,
    activeProfileId: String?,
    onSelect: (ProviderProfile) -> Unit,
    onAddRequested: () -> Unit,
    onEditRequested: (ProviderProfile) -> Unit,
    onDelete: (ProviderProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var deleteTarget by remember { mutableStateOf<ProviderProfile?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Switch provider", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))

            Row(
                Modifier.fillMaxWidth().clickable(onClick = onAddRequested).padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Add a saved provider", color = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            if (profiles.isEmpty()) {
                Text(
                    "No providers saved yet — add one above with a name, key, and model, and you'll never have to paste that key again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(profiles, key = { it.id }) { profile ->
                        ProviderRow(
                            profile = profile,
                            isActive = profile.id == activeProfileId,
                            onClick = { onSelect(profile) },
                            onEditRequested = { onEditRequested(profile) },
                            onDeleteRequested = { deleteTarget = profile }
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${profile.name}\"?") },
            text = { Text("This removes the saved key from this device. This can't be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onDelete(profile); deleteTarget = null }) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProviderRow(
    profile: ProviderProfile,
    isActive: Boolean,
    onClick: () -> Unit,
    onEditRequested: () -> Unit,
    onDeleteRequested: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isReady = profile.apiKey.isNotBlank() || SettingsStore.isKeylessLocal(profile.baseUrl)

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            Icons.Default.Bolt, contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(Modifier.weight(1f)) {
            Text(profile.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
            Text(
                "${SettingsStore.providerLabel(profile.baseUrl)} · ${profile.model}" + if (!isReady) " · needs a key" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (isReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Provider actions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                    onClick = { menuOpen = false; onEditRequested() }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menuOpen = false; onDeleteRequested() }
                )
            }
        }
    }
}

@Composable
private fun Box(content: @Composable () -> Unit) = androidx.compose.foundation.layout.Box(content = { content() })
