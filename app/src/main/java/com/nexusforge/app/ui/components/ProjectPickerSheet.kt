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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.Project
import com.nexusforge.app.data.ProjectSource
import java.text.DateFormat
import java.util.Date

/**
 * "Select a repository"-style picker: every conversation picks the project (sandbox or attached
 * folder) it works in explicitly, instead of every chat silently sharing one global workspace.
 * Also doubles as management (rename/delete) via each row's overflow menu.
 */
@Composable
fun ProjectPickerSheet(
    projects: List<Project>,
    activeProjectId: String?,
    onSelect: (Project) -> Unit,
    onCreateSandbox: (String) -> Unit,
    onAttachFolder: () -> Unit,
    onRename: (Project, String) -> Unit,
    onDelete: (Project) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<Project?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newProjectName by remember { mutableStateOf("") }

    val filtered = if (query.isBlank()) projects else projects.filter { it.name.contains(query, ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Switch project", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search projects") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp).clickable { showCreateDialog = true },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("New sandbox project", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp))
            }
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onAttachFolder),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Attach a real folder", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp))
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))

            if (filtered.isEmpty()) {
                Text(
                    if (projects.isEmpty()) "No projects yet — create a sandbox or attach a folder above."
                    else "No projects match \"$query\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.id }) { project ->
                        ProjectRow(
                            project = project,
                            isActive = project.id == activeProjectId,
                            onClick = { onSelect(project) },
                            onRenameRequested = { renameTarget = project; renameText = project.name },
                            onDeleteRequested = { deleteTarget = project }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New sandbox project") },
            text = {
                OutlinedTextField(
                    value = newProjectName,
                    onValueChange = { newProjectName = it },
                    placeholder = { Text("Project name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onCreateSandbox(newProjectName.ifBlank { "New sandbox" })
                    newProjectName = ""
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") } }
        )
    }

    renameTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename project") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(project, renameText); renameTarget = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { project ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete \"${project.name}\"?") },
            text = {
                Text(
                    if (project.source is ProjectSource.Sandbox)
                        "This permanently deletes every file in this sandbox. This can't be undone."
                    else "This only removes it from Nexus Forge — the real folder and its files are untouched."
                )
            },
            confirmButton = { TextButton(onClick = { onDelete(project); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProjectRow(
    project: Project,
    isActive: Boolean,
    onClick: () -> Unit,
    onRenameRequested: () -> Unit,
    onDeleteRequested: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val isAttached = project.source is ProjectSource.AttachedFolder

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            if (isAttached) Icons.Default.FolderOpen else Icons.Default.Folder,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(Modifier.weight(1f)) {
            Text(project.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
            Text(
                "${if (isAttached) "Attached folder" else "Sandbox"} · used ${formatDate(project.lastUsedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Project actions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                    onClick = { menuOpen = false; onRenameRequested() }
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

private fun formatDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
