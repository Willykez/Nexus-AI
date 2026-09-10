package com.nexusforge.app

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.documentfile.provider.DocumentFile
import com.nexusforge.app.data.AppTab
import com.nexusforge.app.data.ThemeMode
import com.nexusforge.app.ui.components.ProjectPickerSheet
import com.nexusforge.app.ui.screens.ChatScreen
import com.nexusforge.app.ui.screens.HistoryScreen
import com.nexusforge.app.ui.screens.OrganizerScreen
import com.nexusforge.app.ui.screens.SettingsScreen
import com.nexusforge.app.ui.screens.WorkspaceScreen
import com.nexusforge.app.ui.theme.NexusForgeTheme
import com.nexusforge.app.viewmodel.AppViewModel
import kotlinx.coroutines.launch

/** Width, in dp, past which we treat the device as wide enough for a side-by-side layout. */
private const val WIDE_LAYOUT_BREAKPOINT_DP = 840

/** Two back-presses within this window on the Chat tab exits the app. */
private const val EXIT_CONFIRM_WINDOW_MS = 2000L

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // True edge-to-edge: content (and TopAppBar/NavigationBar's own background) draws under
        // the system bars instead of the window auto-resizing around a separate black status-bar
        // strip. This is also what makes Modifier.imePadding() below do real work.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val state by viewModel.state.collectAsState()
            val useDarkTheme = when (state.settings?.themeMode ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
            NexusForgeTheme(themeMode = state.settings?.themeMode ?: ThemeMode.SYSTEM) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    NexusForgeApp(viewModel)
                }
            }
        }
    }
}

@Composable
private fun NexusForgeApp(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isWide = LocalConfiguration.current.screenWidthDp >= WIDE_LAYOUT_BREAKPOINT_DP
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { message -> if (message.isNotBlank()) snackbarHostState.showSnackbar(message) }
    }

    // Back behavior: any non-Chat tab goes back to Chat first; Chat itself needs a second press
    // within EXIT_CONFIRM_WINDOW_MS to actually exit, so a stray back tap never kills the app.
    if (state.currentTab != AppTab.CHAT) {
        BackHandler { viewModel.selectTab(AppTab.CHAT) }
    } else {
        var lastBackPressAt by remember { mutableLongStateOf(0L) }
        val activity = context as? Activity
        BackHandler {
            val now = System.currentTimeMillis()
            if (now - lastBackPressAt < EXIT_CONFIRM_WINDOW_MS) {
                activity?.finish()
            } else {
                lastBackPressAt = now
                scope.launch { snackbarHostState.showSnackbar("Press back again to exit") }
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "Attached folder"
            viewModel.attachFolderAsProject(uri, name)
        }
    }

    val tabs = listOf(
        AppTab.CHAT to Icons.Default.Chat,
        AppTab.WORKSPACE to Icons.Default.Folder,
        AppTab.ORGANIZER to Icons.Default.MergeType,
        AppTab.HISTORY to Icons.Default.History,
        AppTab.SETTINGS to Icons.Default.Settings
    )

    Scaffold(
        modifier = Modifier.imePadding(), // keeps the chat input bar (and now the message list) above the keyboard
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            // containerColor matches the screen background exactly (not the default tonal
            // "surface" container M3 would otherwise use), so the status bar and the app content
            // read as one continuous flat color instead of a visible seam.
            TopAppBar(
                title = { Text("Nexus Forge") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    if (state.currentTab == AppTab.CHAT) {
                        IconButton(onClick = { viewModel.newChat() }) {
                            Icon(Icons.Default.Add, contentDescription = "New chat")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (!isWide) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                    tabs.forEach { (tab, icon) ->
                        NavigationBarItem(
                            selected = state.currentTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            icon = { Icon(icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (isWide) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                    tabs.forEach { (tab, icon) ->
                        NavigationRailItem(
                            selected = state.currentTab == tab,
                            onClick = { viewModel.selectTab(tab) },
                            icon = { Icon(icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }

            if (isWide && state.currentTab == AppTab.CHAT) {
                // Tablet: chat and the live file tree side by side, so writes are visible as they happen.
                Row(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(0.58f)) {
                        ChatScreen(
                            state = state, onSend = viewModel::sendMessage, onStop = viewModel::stopAgent,
                            onOpenProjectPicker = viewModel::openProjectPicker
                        )
                    }
                    Row(Modifier.weight(0.42f)) {
                        WorkspaceScreen(
                            state = state,
                            onRefresh = viewModel::refreshWorkspace,
                            onOpenFile = viewModel::openFile,
                            onClosePreview = viewModel::closeFilePreview,
                            onRename = viewModel::renameFile,
                            onDelete = viewModel::deleteFile,
                            onZip = viewModel::zipWorkspace
                        )
                    }
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    when (state.currentTab) {
                        AppTab.CHAT -> ChatScreen(
                            state = state, onSend = viewModel::sendMessage, onStop = viewModel::stopAgent,
                            onOpenProjectPicker = viewModel::openProjectPicker
                        )
                        AppTab.WORKSPACE -> WorkspaceScreen(
                            state = state,
                            onRefresh = viewModel::refreshWorkspace,
                            onOpenFile = viewModel::openFile,
                            onClosePreview = viewModel::closeFilePreview,
                            onRename = viewModel::renameFile,
                            onDelete = viewModel::deleteFile,
                            onZip = viewModel::zipWorkspace
                        )
                        AppTab.ORGANIZER -> OrganizerScreen(
                            log = state.organizerLog, phase = state.organizerPhase,
                            thinkingChars = state.organizerThinkingChars, resultSummary = state.organizerResultSummary,
                            isRunning = state.organizerRunning,
                            onOrganize = viewModel::organizeDump, onViewWorkspace = { viewModel.selectTab(AppTab.WORKSPACE) }
                        )
                        AppTab.HISTORY -> HistoryScreen(
                            sessions = state.sessionSummaries, onOpen = viewModel::loadSession, onDelete = viewModel::deleteSession
                        )
                        AppTab.SETTINGS -> SettingsScreen(
                            settings = state.settings,
                            activeProject = state.activeProject,
                            onSaveProvider = viewModel::saveProvider,
                            onSaveCapabilities = viewModel::saveCapabilities,
                            onSaveGeneration = viewModel::saveGenerationParams,
                            onOpenProjectPicker = viewModel::openProjectPicker,
                            onSetThemeMode = viewModel::setThemeMode
                        )
                    }
                }
            }
        }
    }

    if (state.showProjectPicker) {
        ProjectPickerSheet(
            projects = state.projects,
            activeProjectId = state.activeProject?.id,
            onSelect = { project -> viewModel.selectProject(project) },
            onCreateSandbox = viewModel::createSandboxProject,
            onAttachFolder = { folderPicker.launch(null) },
            onRename = viewModel::renameProject,
            onDelete = viewModel::deleteProject,
            onDismiss = viewModel::dismissProjectPicker
        )
    }
}
