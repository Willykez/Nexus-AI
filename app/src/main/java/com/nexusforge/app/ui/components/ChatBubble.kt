package com.nexusforge.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.ToolChip
import com.nexusforge.app.ui.markdown.MarkdownText
import com.nexusforge.app.ui.theme.ErrorRed

@Composable
fun ChatBubble(
    role: String,
    text: String,
    toolChips: List<ToolChip>,
    isStreaming: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val isUser = role == "user"
    val clipboard = LocalClipboardManager.current
    val bubbleColor = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 1f)
        ) {
            Column(Modifier.padding(12.dp)) {
                if (toolChips.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                        for (chip in toolChips) ActionCard(chip)
                    }
                }
                if (text.isNotBlank()) {
                    if (isUser) {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(text, style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        MarkdownText(text)
                    }
                } else if (isStreaming) {
                    Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isError) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.padding(end = 4.dp))
                        Text("Something went wrong — see message above.", color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (!isStreaming && text.isNotBlank()) {
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { clipboard.setText(AnnotatedString(text)) }, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy reply", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
