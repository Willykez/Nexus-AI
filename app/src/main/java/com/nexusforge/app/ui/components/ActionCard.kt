package com.nexusforge.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.ToolChip
import com.nexusforge.app.data.ToolStatus
import com.nexusforge.app.ui.theme.AccentForge
import com.nexusforge.app.ui.theme.ErrorRed
import com.nexusforge.app.ui.theme.SuccessGreen

private fun iconFor(toolName: String) = when (toolName) {
    "read_file" -> Icons.Default.Description
    "write_file" -> Icons.Default.Description
    "list_files" -> Icons.Default.ListAlt
    "zip_project" -> Icons.Default.FolderZip
    else -> Icons.Default.Description
}

private fun labelFor(toolName: String) = when (toolName) {
    "read_file" -> "Reading"
    "write_file" -> "Writing"
    "list_files" -> "Listing"
    "zip_project" -> "Zipping"
    else -> toolName
}

/**
 * "A little card pops up saying it's writing CartViewModel.kt... a checkmark appears the moment
 * it's done." — this is that card. Color/icon state alone should tell you running/done/errored
 * at a glance, per the brief's "judge it by a glance" bar.
 */
@Composable
fun ActionCard(chip: ToolChip, modifier: Modifier = Modifier) {
    val borderColor = when (chip.status) {
        ToolStatus.RUNNING -> AccentForge
        ToolStatus.DONE -> SuccessGreen
        ToolStatus.ERROR -> ErrorRed
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(color = borderColor.copy(alpha = 0.18f), shape = CircleShape) {
                Icon(iconFor(chip.toolName), contentDescription = null, tint = borderColor, modifier = Modifier.padding(6.dp).size(16.dp))
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(labelFor(chip.toolName), style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                if (chip.path != null) {
                    Text(
                        chip.path, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                    )
                }
            }
            when (chip.status) {
                ToolStatus.RUNNING -> SpinningDot(color = borderColor)
                ToolStatus.DONE -> Icon(Icons.Default.Check, contentDescription = "Done", tint = SuccessGreen, modifier = Modifier.size(18.dp))
                ToolStatus.ERROR -> Icon(Icons.Default.Close, contentDescription = "Failed", tint = ErrorRed, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SpinningDot(color: androidx.compose.ui.graphics.Color) {
    val transition = rememberInfiniteTransition(label = "spin")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "angle"
    )
    Surface(
        color = color, shape = CircleShape,
        modifier = Modifier.size(10.dp).graphicsLayer { rotationZ = angle }
    ) {}
}
