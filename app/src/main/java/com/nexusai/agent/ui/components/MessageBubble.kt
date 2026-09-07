package com.nexusai.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nexusai.agent.data.ActionStatus
import com.nexusai.agent.ui.Role
import com.nexusai.agent.ui.ToolChip
import com.nexusai.agent.ui.UiChatMessage
import com.nexusai.agent.ui.theme.CodeInlineStyle
import com.nexusai.agent.ui.theme.NexusError
import com.nexusai.agent.ui.theme.NexusPrimary
import com.nexusai.agent.ui.theme.NexusSecondary
import com.nexusai.agent.ui.theme.NexusSurfaceContainerHigh
import com.nexusai.agent.ui.theme.NexusSurfaceContainerHighest
import com.nexusai.agent.ui.theme.NexusTertiary

private data class MessagePart(val isCode: Boolean, val language: String, val text: String)

/** Splits a raw assistant string on ```fenced code``` blocks so text and code render differently. */
private fun parseParts(raw: String): List<MessagePart> {
    val regex = Regex("```([a-zA-Z0-9]*)\n?([\\s\\S]*?)(```|$)")
    val parts = mutableListOf<MessagePart>()
    var cursor = 0
    for (match in regex.findAll(raw)) {
        if (match.range.first > cursor) {
            val plain = raw.substring(cursor, match.range.first)
            if (plain.isNotBlank()) parts.add(MessagePart(false, "", plain.trim()))
        }
        val lang = match.groupValues[1].ifBlank { "text" }
        val body = match.groupValues[2]
        parts.add(MessagePart(true, lang, body))
        cursor = match.range.last + 1
    }
    if (cursor < raw.length) {
        val plain = raw.substring(cursor)
        if (plain.isNotBlank()) parts.add(MessagePart(false, "", plain.trim()))
    }
    return parts
}

@Composable
fun MessageBubble(message: UiChatMessage, modifier: Modifier = Modifier) {
    val isUser = message.role == Role.USER

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            if (!isUser) {
                Icon(Icons.Filled.SmartToy, contentDescription = null, tint = NexusSecondary, modifier = Modifier.padding(end = 2.dp))
            }
            Text(
                if (isUser) "YOU" else "NEXUS AGENT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            if (message.isStreaming) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 2.dp), strokeWidth = 1.5.dp, color = NexusSecondary)
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .background(
                    if (isUser) NexusSurfaceContainerHighest else NexusSurfaceContainerHigh.copy(alpha = 0.55f),
                    RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (message.toolChips.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    message.toolChips.forEach { chip -> ToolChipRow(chip) }
                }
            }

            if (message.text.isBlank() && message.isStreaming) {
                TypingDots()
            } else {
                parseParts(message.text).forEach { part ->
                    if (part.isCode) {
                        StreamingCodeViewer(
                            code = part.text,
                            language = part.language,
                            isStreaming = message.isStreaming
                        )
                    } else {
                        Text(
                            part.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (message.isError) NexusError else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChipRow(chip: ToolChip) {
    val (icon, tint) = when (chip.status) {
        ActionStatus.RUNNING -> null to NexusSecondary
        ActionStatus.DONE -> Icons.Filled.CheckCircle to NexusPrimary
        ActionStatus.ERROR -> Icons.Filled.Error to NexusError
    }
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(end = 0.dp))
        } else {
            CircularProgressIndicator(modifier = Modifier.padding(2.dp), strokeWidth = 1.5.dp, color = tint)
        }
        Text(chip.toolName, style = CodeInlineStyle, color = NexusTertiary)
        if (chip.path != null) {
            Text(chip.path, style = CodeInlineStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TypingDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) {
            Box(
                Modifier
                    .padding(vertical = 6.dp)
                    .size(6.dp)
                    .background(NexusSecondary, RoundedCornerShape(50))
            )
        }
    }
}
