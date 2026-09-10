package com.nexusforge.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.ToolChip
import com.nexusforge.app.ui.markdown.MarkdownText
import com.nexusforge.app.ui.theme.ErrorRed

/**
 * User turns get a right-aligned rounded pill, the way most chat apps mark "this is you". The
 * assistant's reply sits directly on the background as plain flowing text — no card, no border
 * — which is the thing that actually makes a chat screen feel like Claude's rather than a stack
 * of message cards; the reply is the content, not an object floating on top of the page.
 */
@Composable
fun ChatBubble(
    role: String,
    text: String,
    toolChips: List<ToolChip>,
    isStreaming: Boolean,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    if (role == "user") {
        UserBubble(text, modifier)
    } else {
        AssistantTurn(text, toolChips, isStreaming, isError, modifier)
    }
}

@Composable
private fun UserBubble(text: String, modifier: Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(0.86f)
        ) {
            SelectionContainer {
                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun AssistantTurn(text: String, toolChips: List<ToolChip>, isStreaming: Boolean, isError: Boolean, modifier: Modifier) {
    val clipboard = LocalClipboardManager.current
    Column(modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
        if (toolChips.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                for (chip in toolChips) ActionCard(chip)
            }
        }
        if (text.isNotBlank()) {
            MarkdownText(text)
        } else if (isStreaming) {
            Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isError) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.padding(end = 4.dp).size(18.dp))
                Text("Something went wrong — see message above.", color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!isStreaming && text.isNotBlank()) {
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(text)) },
                modifier = Modifier.padding(top = 2.dp).size(32.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy reply", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
    }
}
