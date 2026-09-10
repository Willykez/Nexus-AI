package com.nexusforge.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberInfiniteTransition
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.ToolChip
import com.nexusforge.app.ui.markdown.MarkdownText
import com.nexusforge.app.ui.theme.ErrorRed
import kotlinx.coroutines.delay

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
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(topStart = 6.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 18.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
            MarkdownText(text, isStreaming = isStreaming)
        } else if (isStreaming && toolChips.isEmpty()) {
            // Only show "thinking" when there's truly nothing else on screen yet — once tool
            // chips or text show up, those already communicate progress on their own.
            ThinkingIndicator()
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

private val THINKING_PHRASES = listOf(
    "Thinking…", "Reading the project…", "Planning the approach…", "Working through it…", "Considering the details…"
)

/**
 * "I need to make sure the user doesn't feel bored, assuming the app isn't doing things" — this
 * is the fix: rotating phrases + a typing-dots pulse instead of a single static "Thinking…" that
 * never changes and reads as stuck after a few seconds.
 */
@Composable
fun ThinkingIndicator() {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1700)
            index = (index + 1) % THINKING_PHRASES.size
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TypingDots()
        Text(THINKING_PHRASES[index], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Surface(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                shape = CircleShape,
                modifier = Modifier.size(6.dp)
            ) {}
        }
    }
}
