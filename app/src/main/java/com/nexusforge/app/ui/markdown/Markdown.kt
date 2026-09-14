package com.nexusforge.app.ui.markdown

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusforge.app.ui.theme.MonoFamily
import kotlinx.coroutines.delay

/**
 * Nexus Forge Markdown renderer.
 *
 * Supported Markdown:
 * - # / ## / ### / #### headings
 * - paragraphs
 * - **bold**
 * - *italic*
 * - ***bold italic***
 * - `inline code`
 * - ~~strikethrough~~
 * - bullet lists
 * - numbered lists
 * - task lists
 * - blockquotes
 * - horizontal rules
 * - basic [links](url)
 * - fenced ``` code blocks
 * - streaming/incomplete code fences
 *
 * Important layout rule:
 *
 * Every vertically scrollable code block has a finite maximum height.
 * This is required because MarkdownText is normally rendered inside
 * ChatScreen's LazyColumn.
 */

private sealed class MdBlock {

    data class Code(
        val language: String?,
        val code: String,
        val isOpen: Boolean,
        val id: Int
    ) : MdBlock()

    data class Text(
        val content: String,
        val id: Int
    ) : MdBlock()
}

/* -------------------------------------------------------------------------- */
/* Markdown parsing                                                           */
/* -------------------------------------------------------------------------- */

private val FENCE_LINE = Regex(
    """^\s*(`{3,}|~{3,})\s*([^\s`]*)?.*$"""
)

private val HEADER_LINE = Regex(
    """^\s*(#{1,6})\s+(.+?)\s*$"""
)

private val BULLET_LINE = Regex(
    """^(\s*)(?:[*\-+]|\u2022)\s+(.*)$"""
)

private val NUMBERED_LINE = Regex(
    """^(\s*)(\d+)[.)]\s+(.*)$"""
)

private val QUOTE_LINE = Regex(
    """^\s*>\s?(.*)$"""
)

private val TASK_LINE = Regex(
    """^(\s*)(?:[*\-+]|\u2022)\s+([ xX])]\s+(.*)$"""
)

private val HORIZONTAL_RULE = Regex(
    """^\s*(?:\*{3,}|-{3,}|_{3,})\s*$"""
)

private fun splitMarkdownBlocks(raw: String): List<MdBlock> {
    if (raw.isEmpty()) return emptyList()

    val normalized = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    val lines = normalized.split('\n')
    val blocks = mutableListOf<MdBlock>()

    val textBuffer = StringBuilder()
    var blockId = 0

    fun flushText() {
        if (textBuffer.isNotEmpty()) {
            val content = textBuffer.toString()
                .trimEnd('\n')

            if (content.isNotBlank()) {
                blocks += MdBlock.Text(
                    content = content,
                    id = blockId++
                )
            }

            textBuffer.clear()
        }
    }

    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val fenceMatch = FENCE_LINE.matchEntire(line)

        if (fenceMatch == null) {
            textBuffer
                .append(line)
                .append('\n')

            i++
            continue
        }

        flushText()

        val fence = fenceMatch.groupValues[1]
        val language = fenceMatch.groupValues
            .getOrNull(2)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val codeLines = mutableListOf<String>()
        var j = i + 1
        var closed = false

        while (j < lines.size) {
            val closing = lines[j].trim()

            val isClosingFence =
                closing.startsWith(fence.first().toString().repeat(fence.length)) &&
                    closing.all { it == fence.first() }

            if (isClosingFence) {
                closed = true
                break
            }

            codeLines += lines[j]
            j++
        }

        blocks += MdBlock.Code(
            language = language,
            code = codeLines.joinToString("\n"),
            isOpen = !closed,
            id = blockId++
        )

        i = if (closed) {
            j + 1
        } else {
            lines.size
        }
    }

    flushText()

    return blocks
}

/* -------------------------------------------------------------------------- */
/* Inline Markdown                                                            */
/* -------------------------------------------------------------------------- */

private val INLINE_TOKEN = Regex(
    """
    (\*\*\*[^*\n]+?\*\*\*)|
    (\*\*[^*\n]+?\*\*)|
    (~~[^~\n]+?~~)|
    (`[^`\n]+`)|
    (\*[^*\n]+?\*)|
    (_[^_\n]+?_)|
    (\[[^]+][^)]+)
    """.trimIndent()
)

private fun renderInline(
    line: String,
    codeBackground: Color
): AnnotatedString = buildAnnotatedString {

    var cursor = 0

    for (match in INLINE_TOKEN.findAll(line)) {

        if (match.range.first > cursor) {
            append(
                line.substring(
                    cursor,
                    match.range.first
                )
            )
        }

        val token = match.value

        when {

            /* ***bold italic*** */
            token.startsWith("***") && token.endsWith("***") -> {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic
                    )
                ) {
                    append(
                        token.removeSurrounding("***")
                    )
                }
            }

            /* **bold** */
            token.startsWith("**") && token.endsWith("**") -> {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(
                        token.removeSurrounding("**")
                    )
                }
            }

            /* ~~strike~~ */
            token.startsWith("~~") && token.endsWith("~~") -> {
                withStyle(
                    SpanStyle(
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                    )
                ) {
                    append(
                        token.removeSurrounding("~~")
                    )
                }
            }

            /* `inline code` */
            token.startsWith("`") && token.endsWith("`") -> {
                withStyle(
                    SpanStyle(
                        fontFamily = MonoFamily,
                        background = codeBackground
                    )
                ) {
                    append(" ")
                    append(
                        token.removeSurrounding("`")
                    )
                    append(" ")
                }
            }

            /* *italic* / _italic_ */
            token.startsWith("*") ||
                (token.startsWith("_") && token.endsWith("_")) -> {

                val marker =
                    if (token.startsWith("*")) "*" else "_"

                withStyle(
                    SpanStyle(
                        fontStyle = FontStyle.Italic
                    )
                ) {
                    append(
                        token.removeSurrounding(marker)
                    )
                }
            }

            /* [text](url) */
            token.startsWith("[") -> {
                val linkMatch = Regex(
                    """^\[([^\]]+)]\(([^)]+)\)$"""
                ).find(token)

                if (linkMatch != null) {
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(linkMatch.groupValues[1])
                    }
                } else {
                    append(token)
                }
            }

            else -> append(token)
        }

        cursor = match.range.last + 1
    }

    if (cursor < line.length) {
        append(line.substring(cursor))
    }
}

/* -------------------------------------------------------------------------- */
/* Code block                                                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun CodeBlock(
    language: String?,
    code: String,
    live: Boolean,
    blockId: Int
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var copied by remember(blockId) {
        mutableStateOf(false)
    }

    var manuallyExpanded by remember(blockId) {
        mutableStateOf(true)
    }

    val expanded = live || manuallyExpanded

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "code-chevron"
    )

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    /*
     * CRITICAL FIX:
     *
     * The vertical scrolling container ALWAYS receives a finite maximum
     * height. Previously heightIn(max = 130.dp) was only applied when
     * live == true, while verticalScroll() remained active after the
     * code block finished.
     *
     * That caused:
     *
     * LazyColumn
     *   -> ChatBubble
     *      -> Markdown
     *         -> verticalScroll()
     *            -> infinite max height
     *
     * Compose rejects that measurement.
     */
    val maxCodeHeight = if (live) {
        130.dp
    } else {
        500.dp
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    /*
     * During streaming, keep the code view at the newest content.
     */
    LaunchedEffect(code, live) {
        if (live) {
            verticalScrollState.animateScrollTo(
                verticalScrollState.maxValue
            )
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(
            1.dp,
            if (live) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {

        Column {

            /* Code header */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                    .padding(
                        horizontal = 12.dp,
                        vertical = 8.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                if (live) {

                    PulsingDot(
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "  Writing ${
                            language
                                ?.takeIf { it.isNotBlank() }
                                ?: "code"
                        }…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.weight(1f)
                    )

                } else {

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {

                        Icon(
                            Icons.Default.Code,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )

                        Text(
                            text = (
                                language
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "text"
                                ).replaceFirstChar {
                                    it.uppercase()
                                },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = MonoFamily
                        )
                    }

                    HeaderIconButton(
                        icon = Icons.Default.Download,
                        contentDescription = "Save code"
                    ) {
                        val uri = saveSnippetForSharing(
                            context = context,
                            language = language,
                            code = code
                        )

                        if (uri != null) {
                            val intent = Intent(
                                Intent.ACTION_SEND
                            ).apply {
                                type = "text/plain"

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
                                    "Save code"
                                )
                            )
                        }
                    }

                    HeaderIconButton(
                        icon = if (copied) {
                            Icons.Default.Check
                        } else {
                            Icons.Default.ContentCopy
                        },
                        contentDescription = "Copy code"
                    ) {
                        clipboard.setText(
                            AnnotatedString(code)
                        )

                        copied = true
                    }

                    HeaderIconButton(
                        icon = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) {
                            "Collapse"
                        } else {
                            "Expand"
                        },
                        modifier = Modifier.rotate(
                            chevronRotation
                        )
                    ) {
                        manuallyExpanded = !manuallyExpanded
                    }
                }
            }

            /*
             * Don't create the scrolling container at all while collapsed.
             */
            AnimatedVisibility(
                visible = expanded
            ) {

                /*
                 * IMPORTANT:
                 *
                 * heightIn(max = ...)
                 * comes BEFORE verticalScroll().
                 *
                 * Therefore the vertical scrolling child can never receive
                 * an infinite maximum height from the LazyColumn.
                 */
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            min = 0.dp,
                            max = maxCodeHeight
                        )
                        .verticalScroll(
                            verticalScrollState
                        )
                        .horizontalScroll(
                            horizontalScrollState
                        )
                        .padding(14.dp)
                ) {

                    Text(
                        text = code,
                        fontFamily = MonoFamily,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Header button                                                              */
/* -------------------------------------------------------------------------- */

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(28.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(16.dp)
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Saved code snippets                                                        */
/* -------------------------------------------------------------------------- */

private fun extensionFor(
    language: String?
): String =
    when (language?.lowercase()?.trim()) {

        "kotlin", "kt" -> "kt"
        "java" -> "java"

        "python", "py" -> "py"

        "javascript", "js" -> "js"
        "typescript", "ts" -> "ts"

        "tsx" -> "tsx"
        "jsx" -> "jsx"

        "html" -> "html"
        "css" -> "css"

        "json" -> "json"
        "xml" -> "xml"

        "bash", "sh", "shell" -> "sh"

        "sql" -> "sql"

        "yaml", "yml" -> "yaml"

        "markdown", "md" -> "md"

        "c" -> "c"
        "cpp", "c++" -> "cpp"

        "go" -> "go"
        "rust", "rs" -> "rs"

        "swift" -> "swift"

        "dart" -> "dart"

        "php" -> "php"

        "ruby", "rb" -> "rb"

        "csharp", "cs", "c#" -> "cs"

        else -> "txt"
    }

private fun saveSnippetForSharing(
    context: Context,
    language: String?,
    code: String
): Uri? {
    return try {

        val directory = java.io.File(
            context.cacheDir,
            "snippets"
        ).apply {
            mkdirs()
        }

        val file = java.io.File(
            directory,
            "snippet_${System.currentTimeMillis()}.${
                extensionFor(language)
            }"
        )

        file.writeText(code)

        androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

    } catch (_: Exception) {
        null
    }
}

/* -------------------------------------------------------------------------- */
/* Streaming indicator                                                       */
/* -------------------------------------------------------------------------- */

@Composable
private fun PulsingDot(
    color: Color
) {
    val transition = rememberInfiniteTransition(
        label = "code-pulse"
    )

    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "code-alpha"
    )

    Surface(
        color = color.copy(alpha = alpha),
        shape = CircleShape,
        modifier = Modifier.size(7.dp)
    ) {}
}

/* -------------------------------------------------------------------------- */
/* Public Markdown composable                                                */
/* -------------------------------------------------------------------------- */

@Composable
fun MarkdownText(
    raw: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val blocks = remember(raw) {
        splitMarkdownBlocks(raw)
    }

    val codeBackground =
        MaterialTheme.colorScheme.onSurface.copy(
            alpha = 0.10f
        )

    Column(
        modifier = modifier.fillMaxWidth()
    ) {

        blocks.forEach { block ->

            when (block) {

                is MdBlock.Code -> {
                    CodeBlock(
                        language = block.language,
                        code = block.code,
                        live = isStreaming && block.isOpen,
                        blockId = block.id
                    )
                }

                is MdBlock.Text -> {
                    MarkdownTextBlock(
                        content = block.content,
                        codeBackground = codeBackground
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Text Markdown block                                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun MarkdownTextBlock(
    content: String,
    codeBackground: Color
) {
    val lines = content
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')

    SelectionContainer {

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {

            var paragraphBuffer = StringBuilder()

            fun flushParagraph() {
                if (paragraphBuffer.isEmpty()) return

                val paragraph = paragraphBuffer
                    .toString()
                    .trim()

                if (paragraph.isNotEmpty()) {
                    Text(
                        text = renderInline(
                            paragraph,
                            codeBackground
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(
                            top = 3.dp,
                            bottom = 3.dp
                        )
                    )
                }

                paragraphBuffer = StringBuilder()
            }

            lines.forEach { rawLine ->

                val line = rawLine.trimEnd()

                val header = HEADER_LINE.matchEntire(line)
                val task = TASK_LINE.matchEntire(line)
                val bullet = BULLET_LINE.matchEntire(line)
                val numbered = NUMBERED_LINE.matchEntire(line)
                val quote = QUOTE_LINE.matchEntire(line)

                when {

                    /* Blank line = paragraph boundary */
                    line.isBlank() -> {
                        flushParagraph()
                    }

                    /* Horizontal rule */
                    HORIZONTAL_RULE.matches(line) -> {
                        flushParagraph()

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical = 8.dp
                                ),
                            color = MaterialTheme.colorScheme.outline.copy(
                                alpha = 0.35f
                            )
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .size(height = 1.dp, width = 1.dp)
                            )
                        }
                    }

                    /* Heading */
                    header != null -> {
                        flushParagraph()

                        val level =
                            header.groupValues[1].length

                        val style =
                            when (level) {
                                1 -> MaterialTheme.typography.headlineSmall
                                2 -> MaterialTheme.typography.titleLarge
                                3 -> MaterialTheme.typography.titleMedium
                                else -> MaterialTheme.typography.titleSmall
                            }

                        Text(
                            text = renderInline(
                                header.groupValues[2],
                                codeBackground
                            ),
                            style = style,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                top = if (level <= 2) 10.dp else 6.dp,
                                bottom = 4.dp
                            )
                        )
                    }

                    /* Task list */
                    task != null -> {
                        flushParagraph()

                        val checked =
                            task.groupValues[2]
                                .equals("x", ignoreCase = true)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = 3.dp,
                                    bottom = 3.dp
                                ),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement =
                                Arrangement.spacedBy(8.dp)
                        ) {

                            Text(
                                text = if (checked) {
                                    "☑"
                                } else {
                                    "☐"
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Text(
                                text = renderInline(
                                    task.groupValues[3],
                                    codeBackground
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    /* Bullet list */
                    bullet != null -> {
                        flushParagraph()

                        val indent =
                            (bullet.groupValues[1].length * 4)
                                .coerceAtMost(48)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = indent.dp,
                                    top = 2.dp,
                                    bottom = 2.dp
                                ),
                            verticalAlignment = Alignment.Top
                        ) {

                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(
                                    end = 8.dp
                                )
                            )

                            Text(
                                text = renderInline(
                                    bullet.groupValues[2],
                                    codeBackground
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    /* Numbered list */
                    numbered != null -> {
                        flushParagraph()

                        val indent =
                            (numbered.groupValues[1].length * 4)
                                .coerceAtMost(48)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = indent.dp,
                                    top = 2.dp,
                                    bottom = 2.dp
                                ),
                            verticalAlignment = Alignment.Top
                        ) {

                            Text(
                                text = "${numbered.groupValues[2]}.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(
                                    end = 8.dp
                                )
                            )

                            Text(
                                text = renderInline(
                                    numbered.groupValues[3],
                                    codeBackground
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    /* Blockquote */
                    quote != null -> {
                        flushParagraph()

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(5.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    top = 3.dp,
                                    bottom = 3.dp
                                )
                        ) {

                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {

                                Box(
                                    Modifier
                                        .size(
                                            width = 3.dp,
                                            height = 1.dp
                                        )
                                        .align(Alignment.CenterVertically)
                                )

                                Text(
                                    text = renderInline(
                                        quote.groupValues[1],
                                        codeBackground
                                    ),
                                    modifier = Modifier.padding(
                                        10.dp
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }

                    /*
                     * Normal line.
                     *
                     * Consecutive normal lines become one paragraph instead
                     * of creating artificial line spacing.
                     */
                    else -> {
                        if (paragraphBuffer.isNotEmpty()) {
                            paragraphBuffer.append(' ')
                        }

                        paragraphBuffer.append(line.trim())
                    }
                }
            }

            flushParagraph()
        }
    }
}