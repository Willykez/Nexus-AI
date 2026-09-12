package com.nexusforge.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.ProjectSource
import com.nexusforge.app.data.SettingsStore
import com.nexusforge.app.ui.components.ChatBubble
import com.nexusforge.app.ui.components.ChatInputBar
import com.nexusforge.app.ui.components.MentionPopup
import com.nexusforge.app.ui.components.ProviderBadge
import com.nexusforge.app.viewmodel.AppUiState
import kotlinx.coroutines.launch

private data class HeroSuggestion(val icon: ImageVector, val title: String, val subtitle: String, val prompt: String)

private val HERO_SUGGESTIONS = listOf(
    HeroSuggestion(Icons.Default.Search, "Explain the codebase", "Map structure & patterns", "Explain the architecture of this project and suggest improvements"),
    HeroSuggestion(Icons.Default.AutoAwesome, "Write a function", "Clean, tested code", "Write a well-typed, production-ready function that parses nested JSON safely"),
    HeroSuggestion(Icons.Default.BugReport, "Fix a bug", "Root-cause analysis", "Find and fix the bug in this code, and explain the root cause"),
    HeroSuggestion(Icons.Default.Edit, "Draft an issue", "Well-structured & actionable", "Draft a well-structured issue for a bug I need to describe")
)

private data class QuickChip(val label: String, val prompt: String)

private val QUICK_CHIPS = listOf(
    QuickChip("🐞 Debug", "Debug the following code. Find the root cause and give a minimal fix: "),
    QuickChip("🤖 Agent", "Act as an autonomous coding agent. Break this task into steps and execute: "),
    QuickChip("📝 Write code", "Write clean, production-ready code for: "),
    QuickChip("⑂ Explain", "Explain how this works, step by step: ")
)

/** Where an active "@" mention starts and what's been typed after it, or null if none is active. */
private data class MentionState(val atIndex: Int, val query: String)

private fun findActiveMention(value: TextFieldValue): MentionState? {
    val cursor = value.selection.end
    if (cursor <= 0) return null
    val upto = value.text.substring(0, cursor)
    val at = upto.lastIndexOf('@')
    if (at < 0) return null
    if (at > 0 && !upto[at - 1].isWhitespace()) return null
    val query = upto.substring(at + 1)
    if (query.contains(' ') || query.contains('\n')) return null
    return MentionState(at, query)
}

@Composable
fun ChatScreen(
    state: AppUiState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onOpenProjectPicker: () -> Unit,
    onOpenProviderPicker: () -> Unit,
    onOpenFileTree: () -> Unit
) {
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text) {
        if (state.messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(state.messages.size - 1) }
        }
    }

    val mention = remember(draft.text, draft.selection) { findActiveMention(draft) }
    val mentionResults = remember(mention, state.projectFilePaths) {
        if (mention == null) emptyList()
        else state.projectFilePaths.filter { it.contains(mention.query, ignoreCase = true) }.take(8)
    }

    fun pickMention(path: String) {
        val m = mention ?: return
        val newText = draft.text.substring(0, m.atIndex) + "@" + path + " " + draft.text.substring(draft.selection.end)
        val newCursor = m.atIndex + path.length + 2
        draft = TextFieldValue(newText, TextRange(newCursor))
    }

    Column(Modifier.fillMaxSize()) {
        if (state.messages.isEmpty()) {
            EmptyChatHint(modifier = Modifier.weight(1f), onSuggestionSelected = { draft = TextFieldValue(it, TextRange(it.length)) })
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
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

        val profile = state.activeProviderProfile
        val providerLabel = profile?.let { SettingsStore.providerLabel(it.baseUrl) } ?: "No provider"
        val isReady = profile != null && (profile.apiKey.isNotBlank() || SettingsStore.isKeylessLocal(profile.baseUrl))

        if (mention != null) {
            MentionPopup(query = mention.query, results = mentionResults, onPick = ::pickMention)
        } else if (state.messages.isEmpty() && !state.isAgentRunning) {
            QuickChipsRow(onPick = { draft = TextFieldValue(it, TextRange(it.length)) })
        }

        ChatInputBar(
            value = draft,
            onValueChange = { draft = it },
            onSend = { onSend(draft.text); draft = TextFieldValue("") },
            onStop = onStop,
            isRunning = state.isAgentRunning,
            providerBadge = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.clickable(onClick = onOpenProviderPicker)) {
                        ProviderBadge(providerLabel, profile?.model ?: "add a provider", isReady)
                    }
                    ProjectChip(
                        name = state.activeProject?.name ?: "Pick a project",
                        isAttached = state.activeProject?.source is ProjectSource.AttachedFolder,
                        onClick = onOpenProjectPicker
                    )
                    IconButton(onClick = onOpenFileTree, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.AccountTree, contentDescription = "Project files", modifier = Modifier.size(18.dp))
                    }
                }
            }
        )
    }
}

@Composable
private fun QuickChipsRow(onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (chip in QUICK_CHIPS) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.clickable { onPick(chip.prompt) }
            ) {
                Text(
                    chip.label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ProjectChip(name: String, isAttached: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                if (isAttached) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(13.dp)
            )
            Text(name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ExpandMore, contentDescription = "Switch project", modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun StatusRow(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(2.dp).size(14.dp), strokeWidth = 2.dp)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyChatHint(modifier: Modifier = Modifier, onSuggestionSelected: (String) -> Unit) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Talk to your project", style = MaterialTheme.typography.titleLarge)
        Text(
            "Ask in plain language, type @ to reference a file, or start from one of these.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(HERO_SUGGESTIONS) { suggestion ->
                HeroCard(suggestion) { onSuggestionSelected(suggestion.prompt) }
            }
        }
    }
}

@Composable
private fun HeroCard(suggestion: HeroSuggestion, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp)) {
            Icon(suggestion.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            Text(suggestion.title, style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(
                suggestion.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
