package com.nexusforge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.nexusforge.app.data.ProjectSource
import com.nexusforge.app.data.SettingsStore
import com.nexusforge.app.ui.components.ChatBubble
import com.nexusforge.app.ui.components.ChatInputBar
import com.nexusforge.app.ui.components.ProviderBadge
import com.nexusforge.app.viewmodel.AppUiState
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(state: AppUiState, onSend: (String) -> Unit, onStop: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (state.messages.isEmpty()) {
            EmptyChatHint(state)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    ChatBubble(
                        role = message.role,
                        text = message.text,
                        toolChips = message.toolChips,
                        isStreaming = message.isStreaming,
                        isError = message.isError
                    )
                }
                if (state.isAgentRunning) {
                    item {
                        Box(Modifier.padding(start = 4.dp)) {
                            StatusRow(state.statusLabel)
                        }
                    }
                }
            }
        }

        val settings = state.settings
        val providerLabel = settings?.let { SettingsStore.providerLabel(it.provider.baseUrl) } ?: "—"
        val isReady = settings != null && (settings.provider.apiKey.isNotBlank() || SettingsStore.isKeylessLocal(settings.provider.baseUrl))

        ChatInputBar(
            text = draft,
            onTextChange = { draft = it },
            onSend = { onSend(draft); draft = "" },
            onStop = onStop,
            isRunning = state.isAgentRunning,
            providerBadge = {
                ProviderBadge(providerLabel, settings?.provider?.model ?: "no model", isReady)
                Box(Modifier.padding(start = 6.dp)) {
                    val sourceLabel = when (val s = settings?.projectSource) {
                        is ProjectSource.AttachedFolder -> s.displayName
                        else -> "Sandbox"
                    }
                    Text("· $sourceLabel", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun StatusRow(label: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(2.dp).size(14.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyChatHint(state: AppUiState) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Talk to your project", style = MaterialTheme.typography.titleLarge)
        Text(
            "Ask in plain language — \"build a Task data class\", \"what's messy in here\", \"fix the off-by-one bug\". " +
                "You'll see it read and write files live, right here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun rememberCoroutineScope() = androidx.compose.runtime.rememberCoroutineScope()
