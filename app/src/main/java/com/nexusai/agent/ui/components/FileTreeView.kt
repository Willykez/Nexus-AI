package com.nexusai.agent.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexusai.agent.data.FileNode
import com.nexusai.agent.ui.theme.CodeInlineStyle
import com.nexusai.agent.ui.theme.NexusOutline
import com.nexusai.agent.ui.theme.NexusPrimary
import com.nexusai.agent.ui.theme.NexusSecondary

/** Live-updating recursive file tree for the Workspace / File Inspector panel. */
@Composable
fun FileTreeView(root: FileNode, modifier: Modifier = Modifier, recentlyWrittenPath: String? = null) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (root.children.isEmpty()) {
            Text(
                "Workspace is empty. Ask the agent to create a file.",
                style = MaterialTheme.typography.bodySmall,
                color = NexusOutline,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            root.children.forEach { child ->
                FileTreeNodeRow(node = child, depth = 0, recentlyWrittenPath = recentlyWrittenPath)
            }
        }
    }
}

@Composable
private fun FileTreeNodeRow(node: FileNode, depth: Int, recentlyWrittenPath: String?) {
    var expanded by remember(node.path) { mutableStateOf(depth < 1) }
    val isRecent = node.path.isNotBlank() && node.path == recentlyWrittenPath

    Column(modifier = Modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = node.isDirectory) { expanded = !expanded }
                .background(
                    if (isRecent) NexusSecondary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(6.dp)
                )
                .padding(start = (12 + depth * 14).dp, top = 6.dp, bottom = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (node.isDirectory) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = NexusOutline,
                    modifier = Modifier.size(14.dp)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                    contentDescription = null,
                    tint = NexusPrimary,
                    modifier = Modifier.size(15.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = if (isRecent) NexusSecondary else NexusOutline,
                    modifier = Modifier.size(14.dp).padding(start = 20.dp)
                )
            }
            Text(
                node.name,
                style = CodeInlineStyle,
                color = if (isRecent) NexusSecondary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isRecent) FontWeight.Bold else FontWeight.Normal
            )
            if (!node.isDirectory && node.sizeBytes > 0) {
                Text(
                    formatBytes(node.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = NexusOutline
                )
            }
        }

        if (node.isDirectory && expanded) {
            node.children.forEach { child ->
                FileTreeNodeRow(node = child, depth = depth + 1, recentlyWrittenPath = recentlyWrittenPath)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "${bytes}B"
    bytes < 1024 * 1024 -> "${bytes / 1024}KB"
    else -> "${bytes / (1024 * 1024)}MB"
}
