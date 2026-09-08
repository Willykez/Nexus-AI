package com.example.aicoder

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.aicoder.ui.theme.MyApplicationTheme
import java.io.File
import java.text.DecimalFormat

private val Bg = Color(0xFF0B1016)
private val Panel = Color(0xFF171D25)
private val Panel2 = Color(0xFF1C232C)
private val CodeBg = Color(0xFF080D13)
private val Accent = Color(0xFF8FC7FF)
private val Green = Color(0xFF57E389)
private val Purple = Color(0xFFC7A4FF)
private val Muted = Color(0xFF8D98A6)

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(dynamicColor = false) {
                AICoderRoot(viewModel)
            }
        }
    }
}

@Composable
private fun AICoderRoot(viewModel: ChatViewModel) {

    val state by viewModel.uiState.collectAsState()

    val snackbarHostState = remember {
        SnackbarHostState()
    }

    val context = LocalContext.current
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.importProject(uri)
        }
    }

    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var pendingDeleteSession by remember { mutableStateOf<StoredSession?>(null) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        containerColor = Bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        bottomBar = {
            BottomNavBar(
                selected = state.currentTab,
                onSelect = viewModel::selectTab
            )
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Bg)
                .padding(
                    bottom = paddingValues.calculateBottomPadding()
                )
        ) {

            AppHeader(
                state = state,
                onClear = viewModel::clearChat,
                onHistory = { historyOpen = true },
                onNewConversation = viewModel::newConversation
            )

            if (!state.providerReady && state.currentTab == AppTab.CHAT) {
                SetupNudge(onOpenProvider = { viewModel.selectTab(AppTab.PROVIDER) })
            }

            BoxWithConstraints(Modifier.fillMaxSize()) {
                when (state.currentTab) {
                    AppTab.CHAT -> {
                        if (maxWidth >= 700.dp) {
                            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1.25f).fillMaxSize()) {
                                    ChatStreamScreen(
                                        state = state,
                                        vm = viewModel,
                                        onImportProject = { folderLauncher.launch(null) },
                                        onOpenProvider = { viewModel.selectTab(AppTab.PROVIDER) }
                                    )
                                }
                                Box(Modifier.weight(0.85f).fillMaxSize()) {
                                    WorkspaceScreen(
                                        state = state,
                                        vm = viewModel,
                                        context = context,
                                        onImportProject = { folderLauncher.launch(null) }
                                    )
                                }
                            }
                        } else {
                            ChatStreamScreen(
                                state = state,
                                vm = viewModel,
                                onImportProject = { folderLauncher.launch(null) },
                                onOpenProvider = { viewModel.selectTab(AppTab.PROVIDER) }
                            )
                        }
                    }

                AppTab.DIFF -> {
                    DiffScreen(state)
                }

                AppTab.WORKSPACE -> {
                    WorkspaceScreen(
                        state = state,
                        vm = viewModel,
                        context = context,
                        onImportProject = { folderLauncher.launch(null) }
                    )
                }


                AppTab.PROVIDER -> {
                    ProviderScreen(
                        state = state,
                        vm = viewModel
                    )
                }
            }
        }
    }

    pendingDeleteSession?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingDeleteSession = null },
            title = { Text("Delete conversation?") },
            text = { Text("\"${session.title}\" will be removed from local history. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteConversation(session.id); pendingDeleteSession = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteSession = null }) { Text("Cancel") } }
        )
    }

    if (historyOpen) {
        AlertDialog(
            onDismissRequest = { historyOpen = false },
            title = { Text("Conversation history") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (state.sessions.isEmpty()) {
                        Text("No saved conversations yet. Send a message and it will appear here.", color = Muted)
                    } else {
                        state.sessions.forEach { session ->
                            Surface(
                                color = if (session.id == state.sessionId) Color(0xFF203149) else Panel2,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().clickable {
                                    viewModel.loadConversation(session)
                                    historyOpen = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(session.title, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${session.providerName} • ${session.modelName}", color = Muted, fontSize = 10.sp)
                                        Text(formatTimestamp(session.updatedAt), color = Muted, fontSize = 9.sp)
                                    }
                                    IconButton(onClick = { pendingDeleteSession = session }) {
                                        Icon(Icons.Default.Warning, contentDescription = "Delete conversation", tint = Muted)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.newConversation()
                    historyOpen = false
                }) { Text("New conversation") }
            },
            dismissButton = {
                TextButton(onClick = { historyOpen = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun AppHeader(
    state: UiState,
    onClear: () -> Unit,
    onHistory: () -> Unit,
    onNewConversation: () -> Unit
) {

    var menu by remember {
        mutableStateOf(false)
    }

    Surface(
        color = Color(0xFF0E141B),
        tonalElevation = 0.dp
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 14.dp,
                    vertical = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Panel),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = ">_",
                    color = Accent,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .align(Alignment.TopEnd)
                        .offset(
                            x = (-5).dp,
                            y = 5.dp
                        )
                        .clip(CircleShape)
                        .background(Purple)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = "Nexus AI",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Green)
                    )
                }

                Text(
                    text = state.currentTab.label.uppercase(),
                    color = Muted,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            StatusPill(
                text = "Streaming",
                active = state.isTyping
            )

            Spacer(Modifier.width(8.dp))

            Box {

                IconButton(
                    onClick = {
                        menu = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = Color.LightGray
                    )
                }

                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = {
                        menu = false
                    }
                ) {

                    DropdownMenuItem(
                        text = { Text("Conversation history") },
                        onClick = {
                            onHistory()
                            menu = false
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("New conversation") },
                        onClick = {
                            onNewConversation()
                            menu = false
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text("Clear session")
                        },
                        onClick = {
                            onClear()
                            menu = false
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text("About")
                        },
                        onClick = {
                            menu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    active: Boolean = true
) {

    Surface(
        color = if (active) {
            Color(0xFF10261B)
        } else {
            Color(0xFF20262D)
        },
        shape = RoundedCornerShape(50)
    ) {

        Row(
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 6.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) Green else Muted
                    )
            )

            Spacer(Modifier.width(6.dp))

            Text(
                text = text,
                color = if (active) Green else Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SetupNudge(onOpenProvider: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2417)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFD28A))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Provider setup needed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Choose a model gateway and add a key if it requires one.", color = Muted, fontSize = 10.sp)
            }
            TextButton(onClick = onOpenProvider) { Text("SET UP") }
        }
    }
}

@Composable
private fun ChatStreamScreen(
    state: UiState,
    vm: ChatViewModel,
    onImportProject: () -> Unit,
    onOpenProvider: () -> Unit
) {

    val listState = rememberLazyListState()

    var input by rememberSaveable {
        mutableStateOf("")
    }

    LaunchedEffect(
        state.messages.size,
        state.streamingText,
        state.streamingCode,
        state.activity.size
    ) {

        val lastIndex = listState.layoutInfo.totalItemsCount - 1

        if (lastIndex >= 0) {
            listState.animateScrollToItem(lastIndex)
        }
    }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            if (state.messages.isEmpty()) {

                item {

                    EmptyHero(
                        needsWorkspace = state.fileTreeNodes.isEmpty(),
                        needsProvider = !state.providerReady,
                        onImportProject = onImportProject,
                        onOpenProvider = onOpenProvider,
                        onExample = {
                            input = "Inspect the workspace and create a production-ready Android module."
                        }
                    )
                }
            }

            items(
                items = state.messages
            ) { message ->

                MessageCard(message)
            }

            if (state.activity.isNotEmpty()) {

                item {
                    ThoughtCard(state)
                }
            }

            if (
                state.activeTool != null ||
                state.streamingCode.isNotBlank()
            ) {

                item {
                    LiveWriteCard(state)
                }
            }

            if (state.streamingText.isNotBlank()) {

                item {
                    StreamingAssistantCard(
                        state.streamingText
                    )
                }
            }

            if (state.error != null) {

                item {
                    ErrorCard(state.error)
                }
            }
        }

        Composer(
            value = input,
            onValueChange = {
                input = it
            },
            running = state.isTyping,
            provider = state.providerName,
            model = state.modelName,
            onSend = {
                if (input.isNotBlank()) {
                    vm.sendMessage(input)
                    input = ""
                }
            },
            onStop = vm::stopAgent
        )
    }
}

@Composable
private fun SessionStrip(
    state: UiState
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 14.dp,
                vertical = 7.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = "● ACTIVE SESSION",
            color = Green,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "  •  Run ${state.agentGenerationCount}",
            color = Color.LightGray,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.weight(1f))

        Text(
            text = if (state.isTyping) "LIVE" else "READY",
            color = if (state.isTyping) Green else Muted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun EmptyHero(
    needsWorkspace: Boolean,
    needsProvider: Boolean,
    onImportProject: () -> Unit,
    onOpenProvider: () -> Unit,
    onExample: () -> Unit
) {

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Panel
        ),
        shape = RoundedCornerShape(20.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF202B38)),
                    contentAlignment = Alignment.Center
                ) {

                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {

                    Text(
                        text = "Build with an agent, not a textbox",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Files, tools and streaming stay visible while it works.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (needsWorkspace || needsProvider) {
                Text("Get started", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (needsWorkspace) {
                    OutlinedButton(onClick = onImportProject, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pick a project folder")
                    }
                    Spacer(Modifier.height(6.dp))
                }
                if (needsProvider) {
                    OutlinedButton(onClick = onOpenProvider, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                        Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Connect an AI provider")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = "Try a workspace task",
                color = Color.LightGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onExample,
                border = BorderStroke(
                    1.dp,
                    Color(0xFF34414F)
                )
            ) {

                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(Modifier.width(6.dp))

                Text("Generate a module")
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: ChatUiMessage
) {
    val user = message.role == "user"
    val clipboard = LocalClipboardManager.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (user) 0.90f else 1f),
            colors = CardDefaults.cardColors(containerColor = if (user) Color(0xFF263B57) else Panel),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(13.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (user) "YOU" else "NEXUS AGENT",
                        color = if (user) Color(0xFFB8D8FF) else Color.White,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(message.timeLabel, color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(7.dp))
                SelectionContainer {
                    if (user) {
                        Text(
                            text = message.content,
                            color = Color(0xFFE8EDF2),
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        MarkdownContent(message.content)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(message.content)) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy whole message", tint = Muted, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThoughtCard(
    state: UiState
) {

    val events = state.activity
        .take(3)
        .reversed()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF20242D)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {

        Column(
            modifier = Modifier.padding(13.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = "◉",
                    color = Purple,
                    fontSize = 17.sp
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "Agent activity",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Spacer(Modifier.weight(1f))

                Surface(
                    color = Color(0xFF3A2F52),
                    shape = RoundedCornerShape(6.dp)
                ) {

                    Text(
                        text = "${state.activity.size} events",
                        color = Purple,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(
                            horizontal = 7.dp,
                            vertical = 4.dp
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            events.forEach { event ->

                Row(
                    modifier = Modifier.padding(
                        vertical = 4.dp
                    ),
                    verticalAlignment = Alignment.Top
                ) {

                    Text(
                        text =
                            if (event.success == true) {
                                "✓"
                            } else {
                                "•"
                            },
                        color =
                            if (event.success == true) {
                                Green
                            } else {
                                Purple
                            },
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.width(8.dp))

                    Column {

                        Text(
                            text = event.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = event.detail,
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveWriteCard(
    state: UiState
) {

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Panel
        ),
        shape = RoundedCornerShape(16.dp)
    ) {

        Column(
            modifier = Modifier.padding(10.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Surface(
                    color = Color(0xFF123D25),
                    shape = RoundedCornerShape(50)
                ) {

                    Text(
                        text = "● RUNNING",
                        color = Green,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 5.dp
                        )
                    )
                }

                Spacer(Modifier.width(7.dp))

                Text(
                    text = state.activeTool ?: "workspace",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )

                Spacer(Modifier.weight(1f))

                Text(
                    text =
                        if (state.streamingCode.isNotBlank()) {
                            "LIVE BUFFER"
                        } else {
                            "WORKING"
                        },
                    color = Accent,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(8.dp))

            if (state.selectedFile != null) {

                Text(
                    text = "▣  ${state.selectedFile}",
                    color = Color(0xFFB7C1CD),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(7.dp))
            }

            if (state.streamingCode.isNotBlank()) {

                CodeSurface(
                    code = state.streamingCode,
                    maxHeight = 250
                )
            }
        }
    }
}

@Composable
private fun StreamingAssistantCard(
    text: String
) {

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Panel2
        ),
        shape = RoundedCornerShape(16.dp)
    ) {

        Column(
            modifier = Modifier.padding(14.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Green)
                )

                Spacer(Modifier.width(7.dp))

                Text(
                    text = "Nexus AI",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "  streaming live",
                    color = Muted,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(9.dp))

            SelectionContainer {
                MarkdownContent(text)
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String
) {

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF3A1D22)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {

        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF8D8D)
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = message,
                color = Color(0xFFFFC6C6),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    running: Boolean,
    provider: String,
    model: String,
    onSend: () -> Unit,
    onStop: () -> Unit
) {

    Surface(
        color = Color(0xFF10161D),
        shadowElevation = 8.dp
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(10.dp)
        ) {

            Row(
                modifier = Modifier.horizontalScroll(
                    rememberScrollState()
                ),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {

                Chip(
                    text = "@Workspace",
                    color = Color(0xFF1D2A38)
                )

                Chip(
                    text = provider,
                    color = Color(0xFF1E2A33)
                )

                Chip(
                    text = state.providerName,
                    color = if (state.providerReady) Color(0xFF123D25) else Color(0xFF3A2F24)
                )

                Chip(
                    text = "</> /refactor",
                    color = Color(0xFF252032)
                )

                Chip(
                    text = model,
                    color = Color(0xFF22282F)
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.Bottom
            ) {

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF171D24))
                        .padding(
                            horizontal = 12.dp,
                            vertical = 9.dp
                        )
                ) {

                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 24.dp, max = 132.dp)
                            .verticalScroll(rememberScrollState()),
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        ),
                        minLines = 1,
                        maxLines = Int.MAX_VALUE,
                        decorationBox = { innerTextField ->

                            if (value.isBlank()) {

                                Text(
                                    text = "Ask Nexus to generate, debug, or run…",
                                    color = Muted,
                                    fontSize = 13.sp
                                )
                            }

                            innerTextField()
                        }
                    )
                }

                Spacer(Modifier.width(8.dp))

                if (running) {

                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF9D1D2A)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {

                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(Modifier.width(4.dp))

                        Text("STOP")
                    }

                } else {

                    IconButton(
                        onClick = onSend,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Accent)
                    ) {

                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = Color(0xFF07111B)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(
    text: String,
    color: Color
) {

    Surface(
        color = color,
        shape = RoundedCornerShape(50)
    ) {

        Text(
            text = text,
            color = Color(0xFFD5DDE6),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            modifier = Modifier.padding(
                horizontal = 9.dp,
                vertical = 6.dp
            )
        )
    }
}

@Composable
private fun CodeSurface(
    code: String,
    maxHeight: Int = 420
) {

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    Surface(
        color = CodeBg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            Color(0xFF242D36)
        )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight.dp)
                .verticalScroll(verticalScrollState)
                .horizontalScroll(horizontalScrollState)
                .padding(10.dp)
        ) {

            val lines = code.lines()

            Column {

                lines.forEachIndexed { index, line ->

                    Row {

                        Text(
                            text = "${index + 1}".padStart(3, ' '),
                            color = Color(0xFF53606E),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            modifier = Modifier.width(30.dp)
                        )

                        Text(
                            text = highlightLine(line),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = Color(0xFFD7E0EA),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

private val CODE_KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "interface", "if", "else", "for", "while",
    "return", "import", "package", "private", "public", "protected", "internal",
    "override", "when", "true", "false", "null", "in", "is", "as", "suspend",
    "companion", "data", "enum", "try", "catch", "finally", "throw", "this", "super",
    "const", "def", "function", "let", "const", "export", "async", "await"
)

private val CODE_TOKEN_REGEX = Regex("\"[^\"]*\"|\\b\\d+(\\.\\d+)?\\b|[A-Za-z_][A-Za-z0-9_]*|\\s+|[^\\sA-Za-z0-9_]")

private fun highlightLine(line: String): AnnotatedString = buildAnnotatedString {
    val commentIndex = line.indexOf("//")
    val codePart = if (commentIndex >= 0) line.substring(0, commentIndex) else line
    val commentPart = if (commentIndex >= 0) line.substring(commentIndex) else null

    for (match in CODE_TOKEN_REGEX.findAll(codePart)) {
        val token = match.value
        when {
            token.startsWith('"') -> withStyle(SpanStyle(color = Color(0xFF9FE6A0))) { append(token) }
            token.firstOrNull()?.isDigit() == true -> withStyle(SpanStyle(color = Color(0xFFF0C67C))) { append(token) }
            token in CODE_KEYWORDS -> withStyle(SpanStyle(color = Color(0xFFC7A4FF), fontWeight = FontWeight.SemiBold)) { append(token) }
            else -> append(token)
        }
    }

    if (commentPart != null) {
        withStyle(SpanStyle(color = Color(0xFF6B7684))) { append(commentPart) }
    }
}

@Composable
private fun DiffScreen(
    state: UiState
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        SectionTitle(
            title = "Code Diff",
            trailing = "${state.diffs.size} workspace changes",
            icon = Icons.Default.Code
        )

        if (state.diffs.isEmpty()) {

            EmptyPanel(
                title = "No generated changes yet",
                body = "When the agent writes files, they appear here with line counts and previews."
            )

        } else {

            state.diffs.forEach { diff ->

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Panel
                    ),
                    shape = RoundedCornerShape(15.dp)
                ) {

                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Surface(
                                color =
                                    if (diff.status == "NEW") {
                                        Color(0xFF123D25)
                                    } else {
                                        Color(0xFF3A2F52)
                                    },
                                shape = RoundedCornerShape(5.dp)
                            ) {

                                Text(
                                    text = diff.status,
                                    color =
                                        if (diff.status == "NEW") {
                                            Green
                                        } else {
                                            Purple
                                        },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp,
                                        vertical = 4.dp
                                    )
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            Text(
                                text = diff.path,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(Modifier.weight(1f))

                            Text(
                                text = "+${diff.addedLines} / -${diff.removedLines}",
                                color = Green,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        CodeSurface(
                            code = diff.preview,
                            maxHeight = 180
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceScreen(
    state: UiState,
    vm: ChatViewModel,
    context: Context,
    onImportProject: () -> Unit
) {

    val files = state.fileTreeNodes

    val bytes = files.sumOf {
        it.sizeBytes
    }

    var pendingDelete by remember { mutableStateOf<FileNode?>(null) }
    var pendingRename by remember { mutableStateOf<FileNode?>(null) }
    var renameText by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            shape = RoundedCornerShape(18.dp)
        ) {

            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(32.dp)
                    )

                    Spacer(Modifier.width(10.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = "AI-Coder Workspace",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Text(
                            text = "Private app sandbox",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }

                    StatusPill(
                        text = "SYNCED",
                        active = true
                    )
                }

                Spacer(Modifier.height(12.dp))

                Surface(
                    color = CodeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {

                    Text(
                        text = state.workspacePath.ifBlank { "Resolving workspace path…" },
                        color = Color(0xFFADB7C2),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(10.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {

                    StatBox(
                        label = "STORAGE",
                        value = formatBytes(bytes)
                    )

                    StatBox(
                        label = "SCOPE",
                        value = "${files.size} nodes"
                    )

                    StatBox(
                        label = "AGENT GEN",
                        value = "${state.agentGenerationCount} runs"
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            OutlinedButton(
                onClick = onImportProject,
                shape = RoundedCornerShape(50)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Import Project")
            }

            Button(
                onClick = {
                    vm.zipWorkspace()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent
                ),
                shape = RoundedCornerShape(50)
            ) {

                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color(0xFF07111B)
                )

                Spacer(Modifier.width(5.dp))

                Text(
                    text = "Zip Workspace",
                    color = Color(0xFF07111B)
                )
            }

            OutlinedButton(
                onClick = vm::refreshWorkspace,
                shape = RoundedCornerShape(50)
            ) {

                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null
                )

                Spacer(Modifier.width(5.dp))

                Text("Refresh")
            }
        }

        SectionTitle(
            title = "File Hierarchy",
            trailing = "${files.count { !it.isDirectory }} files",
            icon = Icons.Default.GridView
        )

        if (files.isEmpty()) {

            EmptyPanel(
                title = "Workspace is empty",
                body = "Ask the agent to create files and the tree will update live."
            )

        } else {

            files.forEach { node ->

                FileRow(
                    node = node,
                    onClick = {
                        if (!node.isDirectory) {
                            vm.openFile(node.path)
                        }
                    },
                    currentTouchedPath = state.touchedFile,
                    onRename = {
                        pendingRename = node
                        renameText = node.name
                    },
                    onDelete = {
                        pendingDelete = node
                    }
                )
            }
        }

        if (state.selectedFile != null) {

            SectionTitle(
                title = "Inspector",
                trailing = state.selectedFile,
                icon = Icons.Default.Description
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Panel
                ),
                shape = RoundedCornerShape(15.dp)
            ) {

                Column(
                    modifier = Modifier.padding(10.dp)
                ) {

                    Text(
                        text = state.selectedFile,
                        color = Accent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    CodeSurface(
                        code = state.selectedFileContent
                    )
                }
            }
        }

        if (state.currentZipPath != null) {

            ArchiveCard(
                path = state.currentZipPath,
                context = context
            )
        }
    }

    val deleteTarget = pendingDelete
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${deleteTarget.name}?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFile(deleteTarget.path)
                    pendingDelete = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    val renameTarget = pendingRename
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Rename ${renameTarget.name}") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.renameFile(renameTarget.path, renameText)
                        pendingRename = null
                    },
                    enabled = renameText.isNotBlank()
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun RowScope.StatBox(
    label: String,
    value: String
) {
    Surface(
        color = Color(0xFF1B222A),
        shape = RoundedCornerShape(9.dp),
        modifier = Modifier.weight(1f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp)
        ) {
            Text(
                text = label,
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = value,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun FileRow(
    node: FileNode,
    currentTouchedPath: String?,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {

    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (node.path == currentTouchedPath) Color(0xFF183426) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(
                start = (8 + node.depth * 14).dp,
                end = 4.dp,
                top = 9.dp,
                bottom = 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector =
                if (node.isDirectory) {
                    Icons.Default.Folder
                } else {
                    Icons.Default.InsertDriveFile
                },
            contentDescription = null,
            tint =
                if (node.isDirectory) {
                    Accent
                } else {
                    Color(0xFF9FAAB5)
                },
            modifier = Modifier.size(19.dp)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = node.name,
            color = Color(0xFFE5E9EE),
            fontFamily =
                if (node.isDirectory) {
                    FontFamily.Default
                } else {
                    FontFamily.Monospace
                },
            fontSize = 12.sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (!node.isDirectory) {

            Text(
                text = formatBytes(node.sizeBytes),
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }

        Box {

            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(28.dp)
            ) {

                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "File options",
                    tint = Muted,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {

                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun ArchiveCard(
    path: String,
    context: Context
) {

    val file = File(path)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Panel
        ),
        shape = RoundedCornerShape(17.dp)
    ) {

        Column(
            modifier = Modifier.padding(14.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF123D35)),
                    contentAlignment = Alignment.Center
                ) {

                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = Green
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "Archive Generated",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = file.name,
                        color = Accent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }

                StatusPill(
                    text = "ZIP READY",
                    active = true
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "${formatBytes(file.length())} • Native java.util.zip",
                color = Muted,
                fontSize = 11.sp
            )

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    shareZip(
                        context = context,
                        file = file
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent
                ),
                shape = RoundedCornerShape(10.dp)
            ) {

                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = null,
                    tint = Color(0xFF07111B)
                )

                Spacer(Modifier.width(7.dp))

                Text(
                    text = "Share ZIP",
                    color = Color(0xFF07111B)
                )
            }
        }
    }
}

private fun shareZip(
    context: Context,
    file: File
) {

    if (!file.exists()) {
        return
    }

    val uri = FileProvider.getUriForFile(
        context,
        "com.example.aicoder.fileprovider",
        file
    )

    val intent = Intent(Intent.ACTION_SEND).apply {

        type = "application/zip"

        putExtra(
            Intent.EXTRA_STREAM,
            uri
        )

        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    context.startActivity(
        Intent.createChooser(
            intent,
            "Share workspace ZIP"
        )
    )
}


@Composable
private fun OrganizeScreen(state: UiState, vm: ChatViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Organize Project", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Paste a whole source dump and turn it into a real file tree.", color = Muted, fontSize = 11.sp)
            }
            StatusPill(if (state.isOrganizing) "LIVE" else "READY", state.providerReady)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(17.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Source dump", color = Color.White, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = state.organizeInput,
                    onValueChange = vm::setOrganizeInput,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 520.dp),
                    placeholder = { Text("Paste the complete project source here…") },
                    minLines = 12,
                    maxLines = 24
                )
                Text("${state.organizeInput.length} characters", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                if (state.organizeInput.length > 60_000) {
                    Text("Large paste: the selected model may have context limits. Splitting the project can improve completeness.", color = Color(0xFFFFD28A), fontSize = 10.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = vm::organizePastedProject,
                        enabled = state.organizeInput.isNotBlank() && !state.isOrganizing && !state.isTyping && state.providerReady,
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Analyze & Organize", color = Color(0xFF07111B)) }
                    OutlinedButton(onClick = vm::clearOrganize, enabled = !state.isOrganizing) { Text("Clear") }
                }
            }
        }
        if (state.organizeStreaming.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel2), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Live model output", color = Accent, fontWeight = FontWeight.Bold)
                    SelectionContainer { Text(state.organizeStreaming, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp) }
                }
            }
        }
        if (state.organizeProjectName != null && state.organizeFiles.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Review before writing", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(state.organizeProjectName, color = Accent, fontFamily = FontFamily.Monospace)
                    Text("${state.organizeFiles.size} files", color = Muted, fontSize = 10.sp)
                    state.organizeFiles.take(120).forEach { file ->
                        Surface(color = CodeBg, shape = RoundedCornerShape(7.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(file.path, color = Color(0xFFDDE5ED), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Button(onClick = vm::writeOrganizedProject, enabled = !state.isOrganizing, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                        Text("Confirm & Write to Workspace", color = Color(0xFF07111B))
                    }
                }
            }
        }
        if (state.error != null) ErrorCard(state.error)
    }
}

@Composable
private fun ProviderScreen(
    state: UiState,
    vm: ChatViewModel
) {

    var baseUrl by remember(state.baseUrl) {
        mutableStateOf(state.baseUrl)
    }

    var key by remember {
        mutableStateOf("")
    }

    var model by remember(state.modelName) {
        mutableStateOf(state.modelName)
    }

    var showKey by remember {
        mutableStateOf(false)
    }

    var temperature by remember(state.temperature) {
        mutableStateOf(state.temperature)
    }

    var maxTokens by remember(state.maxOutputTokens) {
        mutableStateOf(
            state.maxOutputTokens.toFloat()
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Nexus AI",
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "PROVIDER & AGENT ROUTING",
                    color = Muted,
                    fontSize = 10.sp,
                    letterSpacing = 1.4.sp
                )
            }

            StatusPill(
                text =
                    if (state.providerReady) {
                        "READY"
                    } else {
                        "NEEDS KEY"
                    },
                active = state.providerReady
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            shape = RoundedCornerShape(17.dp)
        ) {

            Column(
                modifier = Modifier.padding(14.dp)
            ) {

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = null,
                        tint = Green
                    )

                    Spacer(Modifier.width(9.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {

                        Text(
                            text = "Agent Routing Engine",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )

                        Text(
                            text = "Low-latency OpenAI-compatible gateway",
                            color = Green,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Text(
                        text = if (state.providerReady) "READY" else "NEEDS KEY",
                        color = if (state.providerReady) Green else Muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp
                    )
                }
            }
        }

        SectionTitle(
            title = "LLM Gateway Provider",
            trailing = "OpenAI-compatible",
            icon = Icons.Default.Hub
        )

        Row(
            modifier = Modifier.horizontalScroll(
                rememberScrollState()
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            ProviderChip(
                name = "OpenAI",
                subtitle = "GPT-4o / o3-mini",
                selected = state.providerName == "OpenAI",
                onClick = {
                    baseUrl = "https://api.openai.com/v1"
                    model = "gpt-4o-mini"
                }
            )

            ProviderChip(
                name = "Gemini",
                subtitle = "OpenAI shim",
                selected = state.providerName == "Gemini",
                onClick = {
                    baseUrl = "https://generativelanguage.googleapis.com/v1beta/openai"
                    model = "gemini-1.5-flash"
                }
            )

            ProviderChip(
                name = "DeepSeek",
                subtitle = "DeepSeek-V3",
                selected = state.providerName == "DeepSeek",
                onClick = {
                    baseUrl = "https://api.deepseek.com/v1"
                    model = "deepseek-chat"
                }
            )

            ProviderChip(
                name = "Ollama",
                subtitle = "Local 11434",
                selected = state.providerName == "Ollama",
                onClick = {
                    baseUrl = "http://10.0.2.2:11434/v1"
                    model = "llama3.1"
                }
            )

            ProviderChip(
                name = "Qwen",
                subtitle = "DashScope",
                selected = state.providerName == "Qwen",
                onClick = {
                    baseUrl = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
                    model = "qwen-plus"
                }
            )

            ProviderChip(
                name = "Groq",
                subtitle = "Fast hosted",
                selected = state.providerName == "Groq",
                onClick = {
                    baseUrl = "https://api.groq.com/openai/v1"
                    model = "llama-3.3-70b-versatile"
                }
            )

            ProviderChip(
                name = "Mistral",
                subtitle = "Codestral / Mistral",
                selected = state.providerName == "Mistral",
                onClick = {
                    baseUrl = "https://api.mistral.ai/v1"
                    model = "mistral-large-latest"
                }
            )
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            shape = RoundedCornerShape(17.dp)
        ) {

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                SectionTitle(
                    title = "Connection Protocols",
                    trailing = "HTTPS / local HTTP",
                    icon = Icons.Default.Link
                )

                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                    },
                    label = {
                        Text("Gateway Base URL")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = key,
                    onValueChange = {
                        key = it
                    },
                    label = {
                        Text(
                            if (
                                state.hasApiKey &&
                                key.isBlank()
                            ) {
                                "API key saved • enter new key to replace"
                            } else {
                                "Authentication token"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation =
                        if (showKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    trailingIcon = {

                        IconButton(
                            onClick = {
                                showKey = !showKey
                            }
                        ) {

                            Icon(
                                imageVector =
                                    if (showKey) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                contentDescription = null
                            )
                        }
                    },
                    leadingIcon = {

                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null
                        )
                    }
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = {
                        model = it
                    },
                    label = {
                        Text("Model")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Text(
                    text = "Temperature  ${
                        String.format(
                            java.util.Locale.US,
                            "%.1f",
                            temperature
                        )
                    }",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                Slider(
                    value = temperature,
                    onValueChange = {
                        temperature = it
                        vm.updateGenerationConfig(
                            it,
                            maxTokens.toInt()
                        )
                    },
                    valueRange = 0f..1f
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "Max output budget  ${maxTokens.toInt()} tok",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )

                Slider(
                    value = maxTokens,
                    onValueChange = {
                        maxTokens = it
                        vm.updateGenerationConfig(
                            temperature,
                            it.toInt()
                        )
                    },
                    valueRange = 1024f..16384f,
                    steps = 14
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    QuickUrl(
                        text = "api.openai.com",
                        onClick = {
                            baseUrl =
                                "https://api.openai.com/v1"
                        }
                    )

                    QuickUrl(
                        text = "deepseek.com",
                        onClick = {
                            baseUrl =
                                "https://api.deepseek.com/v1"
                        }
                    )

                    QuickUrl(
                        text = "localhost:11434",
                        onClick = {
                            baseUrl =
                                "http://10.0.2.2:11434/v1"
                        }
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            shape = RoundedCornerShape(17.dp)
        ) {

            Column(
                modifier = Modifier.padding(14.dp)
            ) {

                SectionTitle(
                    title = "Agent Capabilities & Sandboxing",
                    trailing = "R/W ${if (state.filesToolEnabled) "on" else "off"} • ZIP ${if (state.zipToolEnabled) "on" else "off"}",
                    icon = Icons.Default.Security
                )

                CapabilityRow(
                    title = "File System Read / Write",
                    subtitle = "Private context.filesDir workspace",
                    enabled = state.filesToolEnabled,
                    onChange = {
                        vm.setFilesToolEnabled(it)
                    }
                )

                CapabilityRow(
                    title = "Stream Tokens",
                    subtitle = "Live SSE token-by-token UI",
                    enabled = true,
                    onChange = {}
                )

                CapabilityRow(
                    title = "Auto-Package ZIP",
                    subtitle = "Native java.util.zip exporter",
                    enabled = state.zipToolEnabled,
                    onChange = {
                        vm.setZipToolEnabled(it)
                    }
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = Panel
            ),
            shape = RoundedCornerShape(17.dp)
        ) {

            Column(
                modifier = Modifier.padding(14.dp)
            ) {

                SectionTitle(
                    title = "Health Diagnostics",
                    trailing = "${state.providerName} • ${
                        if (state.providerReady) {
                            "Configured"
                        } else {
                            "Needs key"
                        }
                    }",
                    icon = Icons.Default.CheckCircle
                )

                Surface(
                    color = CodeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {

                    Column(
                        modifier = Modifier.padding(10.dp)
                    ) {

                        Text(
                            text = "POST /chat/completions",
                            color = Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        )

                        Text(
                            text = "streamable: true  •  tool calling: enabled",
                            color = Muted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        vm.saveSettings(
                            baseUrl,
                            key,
                            model
                        )
                        if (baseUrl.isNotBlank() && model.isNotBlank()) {
                            key = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {

                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = Color(0xFF07111B)
                    )

                    Spacer(Modifier.width(7.dp))

                    Text(
                        text = "Save & Activate",
                        color = Color(0xFF07111B)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderChip(
    name: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Surface(
        color =
            if (selected) {
                Color(0xFF29313B)
            } else {
                Color(0xFF171D24)
            },
        shape = RoundedCornerShape(11.dp),
        border =
            if (selected) {
                BorderStroke(1.dp, Accent)
            } else {
                null
            },
        modifier = Modifier.clickable(onClick = onClick)
    ) {

        Column(
            modifier = Modifier
                .padding(11.dp)
                .width(115.dp)
        ) {

            Text(
                text = name,
                color =
                    if (selected) {
                        Accent
                    } else {
                        Color.White
                    },
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            Text(
                text = subtitle,
                color = Muted,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun QuickUrl(
    text: String,
    onClick: () -> Unit
) {

    Surface(
        color = Color(0xFF222932),
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier.clickable(
            onClick = onClick
        )
    ) {

        Text(
            text = text,
            color = Accent,
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            modifier = Modifier.padding(7.dp)
        )
    }
}

@Composable
private fun CapabilityRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF1D252E)),
            contentAlignment = Alignment.Center
        ) {

            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(19.dp)
            )
        }

        Spacer(Modifier.width(9.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = subtitle,
                color = Muted,
                fontSize = 9.sp
            )
        }

        Switch(
            checked = enabled,
            onCheckedChange = onChange
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
    trailing: String,
    icon: ImageVector
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier.size(19.dp)
        )

        Spacer(Modifier.width(7.dp))

        Text(
            text = title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = trailing,
            color = Muted,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EmptyPanel(
    title: String,
    body: String
) {

    Card(
        colors = CardDefaults.cardColors(
            containerColor = Panel
        ),
        shape = RoundedCornerShape(15.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier.size(28.dp)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = body,
                color = Muted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BottomNavBar(
    selected: AppTab,
    onSelect: (AppTab) -> Unit
) {

    Surface(
        color = Color(0xFF0E141B),
        tonalElevation = 8.dp
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(
                    horizontal = 7.dp,
                    vertical = 6.dp
                ),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {

            NavItem(
                tab = AppTab.CHAT,
                selected = selected,
                icon = Icons.Outlined.Chat,
                onSelect = onSelect
            )

            NavItem(
                tab = AppTab.DIFF,
                selected = selected,
                icon = Icons.Outlined.Code,
                onSelect = onSelect
            )

            NavItem(
                tab = AppTab.WORKSPACE,
                selected = selected,
                icon = Icons.Outlined.Folder,
                onSelect = onSelect
            )

            NavItem(
                tab = AppTab.PROVIDER,
                selected = selected,
                icon = Icons.Default.Hub,
                onSelect = onSelect
            )
        }
    }
}

@Composable
private fun NavItem(
    tab: AppTab,
    selected: AppTab,
    icon: ImageVector,
    onSelect: (AppTab) -> Unit
) {

    val active = tab == selected

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                onSelect(tab)
            }
            .padding(
                horizontal = 12.dp,
                vertical = 5.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint =
                if (active) {
                    Accent
                } else {
                    Color(0xFF9DA7B2)
                },
            modifier = Modifier.size(20.dp)
        )

        Text(
            text = tab.label,
            color =
                if (active) {
                    Accent
                } else {
                    Color(0xFF9DA7B2)
                },
            fontSize = 8.sp,
            fontWeight =
                if (active) {
                    FontWeight.Bold
                } else {
                    FontWeight.Normal
                }
        )
    }
}

private fun formatTimestamp(timestamp: Long): String =
    java.text.SimpleDateFormat("EEE, MMM d • HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

private fun formatBytes(
    bytes: Long
): String {

    if (bytes < 1024) {
        return "$bytes B"
    }

    val kb = bytes / 1024.0

    if (kb < 1024) {
        return "${DecimalFormat("0.0").format(kb)} KB"
    }

    return "${DecimalFormat("0.0").format(kb / 1024.0)} MB"
}
