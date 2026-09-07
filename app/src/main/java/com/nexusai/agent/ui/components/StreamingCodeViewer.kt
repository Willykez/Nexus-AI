package com.nexusai.agent.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nexusai.agent.ui.theme.CodeBlockStyle
import com.nexusai.agent.ui.theme.NexusOutline
import com.nexusai.agent.ui.theme.NexusSecondary
import com.nexusai.agent.ui.theme.NexusSurfaceContainerLowest
import com.nexusai.agent.ui.theme.NexusTertiary

private val KOTLIN_KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "interface", "if", "else", "when", "for", "while",
    "return", "import", "package", "private", "public", "suspend", "override", "companion",
    "data", "sealed", "enum", "is", "in", "null", "true", "false", "try", "catch", "finally", "this"
)

/**
 * Renders a code block that appears to "type itself" while [isStreaming] is true — mirrors the
 * Live Code Editor / Diff reference from the Stitch designs (JetBrains Mono, dark console card).
 */
@Composable
fun StreamingCodeViewer(
    code: String,
    language: String = "kotlin",
    fileName: String? = null,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(NexusSurfaceContainerLowest, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Code, contentDescription = null, tint = NexusTertiary, modifier = Modifier.size(14.dp))
                Text(
                    fileName ?: language,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isStreaming) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Circle, contentDescription = null, tint = NexusSecondary, modifier = Modifier.size(6.dp))
                    Text("LIVE", style = MaterialTheme.typography.labelSmall, color = NexusSecondary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row {
                Text(highlightCode(code), style = CodeBlockStyle)
                if (isStreaming) BlinkingCursor()
            }
        }
    }
}

@Composable
private fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor-blink")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursor-alpha"
    )
    Text("▍", style = CodeBlockStyle, color = NexusSecondary.copy(alpha = alpha))
}

/** Very small, dependency-free keyword/string/comment highlighter — enough for a live console feel. */
private fun highlightCode(code: String) = buildAnnotatedString {
    val tokenRegex = Regex("(//[^\n]*)|(\"(?:[^\"\\\\]|\\\\.)*\")|(\\b[A-Za-z_][A-Za-z0-9_]*\\b)|([^A-Za-z0-9_]+)")
    var lastIndex = 0
    for (match in tokenRegex.findAll(code)) {
        if (match.range.first > lastIndex) append(code.substring(lastIndex, match.range.first))
        val token = match.value
        when {
            match.groups[1] != null -> withStyle(SpanStyle(color = NexusOutline)) { append(token) } // comment
            match.groups[2] != null -> withStyle(SpanStyle(color = NexusSecondary)) { append(token) } // string
            match.groups[3] != null && token in KOTLIN_KEYWORDS ->
                withStyle(SpanStyle(color = NexusTertiary, fontWeight = FontWeight.SemiBold)) { append(token) }
            else -> append(token)
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < code.length) append(code.substring(lastIndex))
}
