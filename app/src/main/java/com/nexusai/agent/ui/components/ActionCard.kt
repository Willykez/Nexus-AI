package com.nexusai.agent.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nexusai.agent.data.ActionStatus
import com.nexusai.agent.data.ActivityEvent
import com.nexusai.agent.data.ToolRegistry
import com.nexusai.agent.ui.theme.CodeInlineStyle
import com.nexusai.agent.ui.theme.LabelXsStyle
import com.nexusai.agent.ui.theme.NexusOutline
import com.nexusai.agent.ui.theme.NexusSurfaceContainerLow
import com.nexusai.agent.ui.theme.NexusSurfaceContainerLowest
import com.nexusai.agent.ui.theme.StatusDone
import com.nexusai.agent.ui.theme.StatusError
import com.nexusai.agent.ui.theme.StatusRunning

private fun iconForTool(name: String) = when (name) {
    ToolRegistry.TOOL_WRITE_FILE -> Icons.Filled.Description
    ToolRegistry.TOOL_READ_FILE -> Icons.Filled.Description
    ToolRegistry.TOOL_LIST_FILES -> Icons.Filled.ListAlt
    ToolRegistry.TOOL_ZIP_PROJECT -> Icons.Filled.FolderZip
    else -> Icons.Filled.Terminal
}

private fun statusColor(status: ActionStatus) = when (status) {
    ActionStatus.RUNNING -> StatusRunning
    ActionStatus.DONE -> StatusDone
    ActionStatus.ERROR -> StatusError
}

/** Mirrors the "Real-Time Function Invocation Action Card" from the Stitch live-agent reference. */
@Composable
fun ActionCard(event: ActivityEvent, modifier: Modifier = Modifier) {
    val accent = statusColor(event.status)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NexusSurfaceContainerLowest, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(status = event.status)
                Icon(iconForTool(event.toolName), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
                Text(event.toolName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            when (event.status) {
                ActionStatus.DONE -> Icon(Icons.Filled.CheckCircle, null, tint = StatusDone, modifier = Modifier.size(16.dp))
                ActionStatus.ERROR -> Icon(Icons.Filled.Error, null, tint = StatusError, modifier = Modifier.size(16.dp))
                ActionStatus.RUNNING -> {}
            }
        }

        event.targetPath?.let { path ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NexusSurfaceContainerLow, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Description, null, tint = NexusOutline, modifier = Modifier.size(13.dp))
                Text(path, style = CodeInlineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }

        ActionProgressBar(status = event.status, progress = event.progress)

        if (event.detail.isNotBlank()) {
            Text(
                event.detail.take(160),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusPill(status: ActionStatus) {
    val (label, color) = when (status) {
        ActionStatus.RUNNING -> "RUNNING" to StatusRunning
        ActionStatus.DONE -> "DONE" to StatusDone
        ActionStatus.ERROR -> "ERROR" to StatusError
    }
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(50)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(Modifier.size(6.dp).background(color, CircleShape))
            Text(label, style = LabelXsStyle, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionProgressBar(status: ActionStatus, progress: Float) {
    val color = statusColor(status)
    val infiniteTransition = rememberInfiniteTransition(label = "progress-glow")
    val animatedWidth by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "indeterminate-width"
    )
    val fraction = if (status == ActionStatus.RUNNING && progress <= 0f) animatedWidth else progress.coerceIn(0.04f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(50))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(5.dp)
                .background(color, RoundedCornerShape(50))
        )
    }
}
