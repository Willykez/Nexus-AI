package com.nexusforge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.nexusforge.app.ui.components.FileTreeView
import com.nexusforge.app.viewmodel.AppUiState

@Composable
fun WorkspaceScreen(
    state: AppUiState,
    onRefresh: () -> Unit,
    onOpenFile: (String) -> Unit,
    onClosePreview: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onZip: () -> Unit
) {
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var renameText by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(state.workspaceStats?.label ?: "Workspace", style = MaterialTheme.typography.titleMedium)
                state.workspaceStats?.let {
                    Text("${it.fileCount} files · ${it.directoryCount} folders", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row {
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                IconButton(onClick = onZip) { Icon(Icons.Default.FolderZip, contentDescription = "Zip workspace") }
            }
        }
        HorizontalDivider()

        if (state.lastExportedZip != null) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Archive ready: ${java.io.File(state.lastExportedZip).name}", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = {
                        val file = java.io.File(state.lastExportedZip)
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "application/zip"
                            putExtra(android.content.Intent.EXTRA_STREAM, uri)
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share archive"))
                    }) { Icon(Icons.Default.Share, contentDescription = "Share archive") }
                }
            }
        }

        if (state.selectedFile != null) {
            FilePreview(state.selectedFile, state.selectedFileContent, onClose = onClosePreview)
        } else {
            FileTreeView(
                root = state.fileTree,
                highlightedPath = state.highlightedPath,
                onFileClick = { onOpenFile(it.path) },
                onRename = { renameTarget = it.path; renameText = it.name },
                onDelete = { deleteTarget = it.path },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    renameTarget?.let { path ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onRename(path, renameText); renameTarget = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { path ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this file?") },
            text = { Text("$path will be permanently deleted. This can't be undone.") },
            confirmButton = { TextButton(onClick = { onDelete(path); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FilePreview(path: String, content: String, onClose: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(path, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            OutlinedButton(onClick = onClose) { Text("Back to tree") }
        }
        HorizontalDivider()
        Text(
            content,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)
        )
    }
}
