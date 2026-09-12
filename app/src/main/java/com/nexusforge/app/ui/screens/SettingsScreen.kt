package com.nexusforge.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.AppSettings
import com.nexusforge.app.data.Project
import com.nexusforge.app.data.ProjectSource
import com.nexusforge.app.data.ProviderProfile
import com.nexusforge.app.data.SettingsStore
import com.nexusforge.app.data.ThemeMode

@Composable
fun SettingsScreen(
    settings: AppSettings?,
    activeProject: Project?,
    providerProfiles: List<ProviderProfile>,
    activeProviderProfileId: String?,
    onSaveCapabilities: (Boolean, Boolean) -> Unit,
    onSaveGeneration: (Float, Int) -> Unit,
    onOpenProjectPicker: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSelectProvider: (ProviderProfile) -> Unit,
    onAddProvider: () -> Unit,
    onEditProvider: (ProviderProfile) -> Unit,
    onDeleteProvider: (ProviderProfile) -> Unit
) {
    if (settings == null) return

    var filesEnabled by remember(settings.capabilities) { mutableStateOf(settings.capabilities.fileReadWriteEnabled) }
    var zipEnabled by remember(settings.capabilities) { mutableStateOf(settings.capabilities.zipEnabled) }
    var temperature by remember(settings.temperature) { mutableStateOf(settings.temperature) }
    var maxTokens by remember(settings.maxOutputTokens) { mutableStateOf(settings.maxOutputTokens.toString()) }

    LazyColumn(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text("Appearance", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoiceChip("System", settings.themeMode == ThemeMode.SYSTEM) { onSetThemeMode(ThemeMode.SYSTEM) }
                ThemeChoiceChip("Light", settings.themeMode == ThemeMode.LIGHT) { onSetThemeMode(ThemeMode.LIGHT) }
                ThemeChoiceChip("Dark", settings.themeMode == ThemeMode.DARK) { onSetThemeMode(ThemeMode.DARK) }
            }
        }

        item { HorizontalDivider() }

        item {
            Text("Project", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            Text(
                "Each conversation is pointed at a project — a private sandbox to build from scratch, or a real " +
                    "folder on your device the agent edits in place. Switch or manage projects from the picker.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenProjectPicker).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(activeProject?.name ?: "No project selected", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        when (activeProject?.source) {
                            is ProjectSource.AttachedFolder -> "Attached folder"
                            is ProjectSource.Sandbox -> "Sandbox"
                            else -> "Tap to choose a project"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Default.ChevronRight, contentDescription = "Switch project", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item { HorizontalDivider() }

        item {
            Text("Providers", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            Text(
                "Every key you add is saved fully — switching between several keys for the same provider is a tap, " +
                    "never a re-paste.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        items(providerProfiles, key = { it.id }) { profile ->
            ProviderSettingsRow(
                profile = profile,
                isActive = profile.id == activeProviderProfileId,
                onClick = { onSelectProvider(profile) },
                onEdit = { onEditProvider(profile) },
                onDelete = { onDeleteProvider(profile) }
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onAddProvider).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Add a provider", color = MaterialTheme.colorScheme.primary)
            }
        }

        item { HorizontalDivider() }

        item {
            Text("Capabilities", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("File read / write")
                    Text(
                        "Turn off to make the agent chat-only — it can talk, but never touch a file.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = filesEnabled, onCheckedChange = { filesEnabled = it; onSaveCapabilities(it, zipEnabled) })
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ZIP export", Modifier.weight(1f))
                Switch(checked = zipEnabled, onCheckedChange = { zipEnabled = it; onSaveCapabilities(filesEnabled, it) })
            }
        }

        item { HorizontalDivider() }

        item {
            Text("Generation", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 4.dp))
            Text("Temperature: ${"%.2f".format(temperature)}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = temperature, onValueChange = { temperature = it },
                valueRange = 0f..1f,
                onValueChangeFinished = { onSaveGeneration(temperature, maxTokens.toIntOrNull() ?: 8192) }
            )
            OutlinedTextField(
                maxTokens, { maxTokens = it.filter(Char::isDigit) },
                label = { Text("Max output tokens") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Button(onClick = { onSaveGeneration(temperature, maxTokens.toIntOrNull() ?: 8192) }, modifier = Modifier.padding(top = 10.dp)) {
                Text("Save generation settings")
            }
        }
    }
}

@Composable
private fun ProviderSettingsRow(
    profile: ProviderProfile,
    isActive: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isReady = profile.apiKey.isNotBlank() || SettingsStore.isKeylessLocal(profile.baseUrl)

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Bolt, contentDescription = null, tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
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
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun Box(content: @Composable () -> Unit) = androidx.compose.foundation.layout.Box(content = { content() })

@Composable
private fun ThemeChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
