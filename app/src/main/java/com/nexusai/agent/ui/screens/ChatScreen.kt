package com.nexusai.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusai.agent.ui.UiChatMessage
import com.nexusai.agent.ui.components.MessageBubble
import com.nexusai.agent.ui.theme.NexusOutline
import com.nexusai.agent.ui.theme.NexusSecondary
import com.nexusai.agent.ui.theme.NexusSurfaceContainer
import com.nexusai.agent.ui.theme.NexusSurfaceContainerHigh

@Composable
fun ChatScreen(
    messages: List<UiChatMessage>,
    isAgentRunning: Boolean,
    statusLabel: String,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (messages.isEmpty()) {
            EmptyChatState(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(messages, key = { _, item -> item.id }) { _, message ->
                    MessageBubble(message = message)
                }
                item {
                    if (isAgentRunning) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CircularProgressIndicator(modifier = Modifier.height(12.dp), strokeWidth = 1.5.dp, color = NexusSecondary)
                            Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = NexusOutline)
                        }
                    }
                }
            }
        }

        ChatInputBar(
            draft = draft,
            onDraftChange = { draft = it },
            enabled = !isAgentRunning,
            onSend = {
                if (draft.isNotBlank()) {
                    onSend(draft.trim())
                    draft = ""
                }
            }
        )
    }
}

@Composable
private fun EmptyChatState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Nexus Agent", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "Ask for a feature, a bug fix, or a whole project — I'll write and run it live.",
                style = MaterialTheme.typography.bodySmall,
                color = NexusOutline
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    onDraftChange: (String) -> Unit,
    enabled: Boolean,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Message Nexus Agent...") },
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = NexusSurfaceContainer,
                focusedContainerColor = NexusSurfaceContainerHigh,
                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedBorderColor = NexusSecondary
            ),
            maxLines = 5
        )
        IconButton(
            onClick = onSend,
            enabled = enabled && draft.isNotBlank(),
            modifier = Modifier
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .background(
                        if (enabled && draft.isNotBlank()) NexusSecondary else NexusSurfaceContainerHigh,
                        CircleShape
                    )
                    .padding(10.dp)
            ) {
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = "Send",
                    tint = androidx.compose.ui.graphics.Color(0xFF00390D)
                )
            }
        }
    }
}
