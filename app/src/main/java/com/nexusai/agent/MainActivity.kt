package com.nexusai.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusai.agent.ui.ChatViewModel
import com.nexusai.agent.ui.screens.ChatScreen
import com.nexusai.agent.ui.screens.InspectorScreen
import com.nexusai.agent.ui.screens.SettingsScreen
import com.nexusai.agent.ui.theme.NexusAiTheme
import com.nexusai.agent.ui.theme.NexusOutlineVariant
import com.nexusai.agent.ui.theme.NexusSecondary
import com.nexusai.agent.ui.theme.NexusSurfaceContainerLowest
import kotlinx.coroutines.flow.StateFlow

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexusAiTheme {
                NexusApp(viewModel)
            }
        }
    }
}

private enum class NavDestination { CHAT, INSPECTOR }

/** Bridges a StateFlow to Compose State without extra ViewModel boilerplate for this single screen tree. */
@Composable
private fun <T> StateFlow<T>.asState() = collectAsState(initial = value)

@Composable
fun NexusApp(viewModel: ChatViewModel) {
    val messages by viewModel.uiMessages.asState()
    val activityFeed by viewModel.activityFeed.asState()
    val fileTree by viewModel.fileTree.asState()
    val isRunning by viewModel.isAgentRunning.asState()
    val statusLabel by viewModel.statusLabel.asState()
    val providerConfig by viewModel.providerConfig.asState()
    val lastZip by viewModel.lastExportedZip.asState()

    var showSettings by remember { mutableStateOf(false) }
    var narrowDestination by remember { mutableStateOf(NavDestination.CHAT) }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        if (showSettings) {
            SettingsScreen(
                current = providerConfig,
                onSave = {
                    viewModel.updateProviderConfig(it)
                    showSettings = false
                },
                onBack = { showSettings = false }
            )
            return@Surface
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 720.dp

            Scaffold(
                topBar = {
                    NexusTopBar(
                        isStreaming = isRunning,
                        modelName = providerConfig.model,
                        onSettingsClick = { showSettings = true }
                    )
                },
                bottomBar = {
                    if (!isWide) {
                        NexusBottomBar(
                            destination = narrowDestination,
                            onSelect = { narrowDestination = it }
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { padding ->
                if (isWide) {
                    Row(Modifier.padding(padding).fillMaxSize()) {
                        ChatScreen(
                            messages = messages,
                            isAgentRunning = isRunning,
                            statusLabel = statusLabel,
                            onSend = viewModel::sendMessage,
                            modifier = Modifier.weight(1.15f).fillMaxHeight()
                        )
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(NexusOutlineVariant.copy(alpha = 0.4f))
                        )
                        InspectorScreen(
                            activityFeed = activityFeed,
                            fileTree = fileTree,
                            lastExportedZip = lastZip,
                            modifier = Modifier.weight(0.85f).fillMaxHeight()
                        )
                    }
                } else {
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        when (narrowDestination) {
                            NavDestination.CHAT -> ChatScreen(
                                messages = messages,
                                isAgentRunning = isRunning,
                                statusLabel = statusLabel,
                                onSend = viewModel::sendMessage,
                                modifier = Modifier.fillMaxSize()
                            )
                            NavDestination.INSPECTOR -> InspectorScreen(
                                activityFeed = activityFeed,
                                fileTree = fileTree,
                                lastExportedZip = lastZip,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NexusBottomBar(destination: NavDestination, onSelect: (NavDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
        NavigationBarItem(
            selected = destination == NavDestination.CHAT,
            onClick = { onSelect(NavDestination.CHAT) },
            icon = { Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "Chat") },
            label = { Text("Chat") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NexusSecondary,
                selectedTextColor = NexusSecondary,
                indicatorColor = NexusSecondary.copy(alpha = 0.16f)
            )
        )
        NavigationBarItem(
            selected = destination == NavDestination.INSPECTOR,
            onClick = { onSelect(NavDestination.INSPECTOR) },
            icon = { Icon(Icons.Outlined.Dashboard, contentDescription = "Inspector") },
            label = { Text("Inspector") },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = NexusSecondary,
                selectedTextColor = NexusSecondary,
                indicatorColor = NexusSecondary.copy(alpha = 0.16f)
            )
        )
    }
}

@Composable
private fun NexusTopBar(isStreaming: Boolean, modelName: String, onSettingsClick: () -> Unit) {
    TopAppBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(NexusSecondary.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Terminal, contentDescription = null, tint = NexusSecondary, modifier = Modifier.size(16.dp))
                }
                Text("Nexus AI", style = MaterialTheme.typography.headlineSmall)
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(NexusSurfaceContainerLowest, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(if (isStreaming) NexusSecondary else NexusOutlineVariant, CircleShape)
                )
                Text(
                    modelName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isStreaming) NexusSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Tune, contentDescription = "Settings")
            }
        }
    )
}
