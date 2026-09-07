package com.nexusai.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusai.agent.data.ActivityEvent
import com.nexusai.agent.data.FileNode
import com.nexusai.agent.ui.components.ActionCard
import com.nexusai.agent.ui.components.FileTreeView
import com.nexusai.agent.ui.theme.NexusOutline
import com.nexusai.agent.ui.theme.NexusPrimary
import com.nexusai.agent.ui.theme.NexusSecondary
import com.nexusai.agent.ui.theme.NexusSurfaceContainerLow

@Composable
fun InspectorScreen(
    activityFeed: List<ActivityEvent>,
    fileTree: FileNode,
    lastExportedZip: String?,
    modifier: Modifier = Modifier
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    val titles = listOf("Activity", "Files")

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = NexusSecondary
        ) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }
        HorizontalDivider(color = NexusSurfaceContainerLow)

        when (tabIndex) {
            0 -> ActivityFeedList(activityFeed, lastExportedZip)
            1 -> Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Filled.Inventory2, contentDescription = null, tint = NexusPrimary, modifier = Modifier)
                    Text("Workspace", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                }
                FileTreeView(root = fileTree, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ActivityFeedList(events: List<ActivityEvent>, lastExportedZip: String?) {
    val listState = rememberLazyListState()
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) listState.animateScrollToItem(events.size - 1)
    }

    if (events.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.History, contentDescription = null, tint = NexusOutline)
                Text(
                    "Tool activity will stream here as the agent works.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NexusOutline
                )
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (lastExportedZip != null) {
            item(key = "zip-banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NexusPrimary.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.FolderZip, contentDescription = null, tint = NexusPrimary)
                    Column {
                        Text("Archive ready", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(lastExportedZip, style = MaterialTheme.typography.labelSmall, color = NexusOutline, maxLines = 1)
                    }
                }
            }
        }
        items(events.reversed(), key = { it.id }) { event ->
            ActionCard(event = event)
        }
    }
}
