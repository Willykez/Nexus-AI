package com.nexusforge.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.FileNode
import kotlinx.coroutines.launch

/**
 * "Remove the page screen and replace it with a bottom sheet... codes open by tap to expand and
 * contract." This is that: same tree, but it floats over Chat/Organizer instead of navigating
 * away, and tapping a file expands its content right there in place instead of pushing a new
 * screen.
 */
@Composable
fun FileTreeBottomSheet(root: FileNode, onReadFile: suspend (String) -> String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("Project files", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            if (root.children.isEmpty()) {
                Text(
                    "No files yet — ask the agent to create something first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(root.children) { child -> ExpandableNode(child, depth = 0, onReadFile = onReadFile) }
                }
            }
        }
    }
}

@Composable
private fun ExpandableNode(node: FileNode, depth: Int, onReadFile: suspend (String) -> String) {
    var expanded by remember(node.path) { mutableStateOf(false) }
    var content by remember(node.path) { mutableStateOf<String?>(null) }
    var loading by remember(node.path) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    expanded = !expanded
                    if (!node.isDirectory && expanded && content == null) {
                        loading = true
                        scope.launch { content = onReadFile(node.path); loading = false }
                    }
                }
                .padding(start = (12 + depth * 16).dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (node.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (node.isDirectory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(node.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (!node.isDirectory) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)
                )
            }
        }
        if (expanded) {
            if (node.isDirectory) {
                for (child in node.children) ExpandableNode(child, depth + 1, onReadFile)
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxWidth().padding(start = (12 + depth * 16).dp, end = 12.dp, bottom = 8.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            content.orEmpty(),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
