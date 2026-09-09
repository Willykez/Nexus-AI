package com.nexusforge.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.FileNode

@Composable
fun FileTreeView(
    root: FileNode,
    highlightedPath: String?,
    onFileClick: (FileNode) -> Unit,
    onRename: (FileNode) -> Unit,
    onDelete: (FileNode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        for (child in root.children) {
            TreeRow(child, depth = 0, highlightedPath = highlightedPath, onFileClick = onFileClick, onRename = onRename, onDelete = onDelete)
        }
        if (root.children.isEmpty()) {
            Text(
                "No files yet — ask the agent to create something, or paste a project into Organize.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun TreeRow(
    node: FileNode,
    depth: Int,
    highlightedPath: String?,
    onFileClick: (FileNode) -> Unit,
    onRename: (FileNode) -> Unit,
    onDelete: (FileNode) -> Unit
) {
    var expanded by remember(node.path) { mutableStateOf(depth < 1) }
    var menuOpen by remember { mutableStateOf(false) }
    val isHighlighted = node.path == highlightedPath
    val bg by animateColorAsState(
        targetValue = if (isHighlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(600), label = "highlight"
    )

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg)
                .clickable { if (node.isDirectory) expanded = !expanded else onFileClick(node) }
                .padding(start = (12 + depth * 16).dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (node.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (node.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(node.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (!node.isDirectory) {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "File actions", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                            onClick = { menuOpen = false; onRename(node) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { menuOpen = false; onDelete(node) }
                        )
                    }
                }
            }
        }
        if (node.isDirectory && expanded) {
            for (child in node.children) {
                TreeRow(child, depth + 1, highlightedPath, onFileClick, onRename, onDelete)
            }
        }
    }
}

// small local Box helper to avoid importing foundation.layout.Box collision noise above
@Composable
private fun Box(content: @Composable () -> Unit) = androidx.compose.foundation.layout.Box(content = content)
