package com.nexusforge.app.ui.markdown

import android.content.Context
import android.content.Intent
import android.net.Uri

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
 * Lightweight Markdown renderer for AI/chat responses.
 *
 * Supported:
 * - # through #### headings
 * - **bold**
 * - *italic*
 * - ~~strikethrough~~
 * - `inline code`
 * - [links](url)
 * - unordered lists
 * - ordered lists
 * - blockquotes
 * - fenced code blocks
 * - streaming/incomplete code blocks
 *
 * This is intentionally dependency-free so it works with the existing Compose setup.
 */

/* -------------------------------------------------------------------------- */
/* Markdown model                                                             */
/* -------------------------------------------------------------------------- */

private sealed class MdBlock {

    data class Text(
        val content: String
    ) : MdBlock()

    data class Code(
        val language: String?,
        val code: String,
        val isOpen: Boolean
    ) : MdBlock()
}

/* -------------------------------------------------------------------------- */
/* Markdown patterns                                                          */
/* -------------------------------------------------------------------------- */

private val FENCE_LINE =
    Regex("^\\s*`{3,}\\s*([^\\s`]*)\\s*$")

private val HEADER_LINE =
    Regex("^\\s*(#{1,6})\\s+(.+?)\\s*$")

private val BULLET_LINE =
    Regex("^\\s*[-*+]\\s+(.+)$")

private val NUMBERED_LINE =
    Regex("^\\s*(\\d+)[.)]\\s+(.+)$")

private val QUOTE_LINE =
    Regex("^\\s*>\\s?(.*)$")

private val HORIZONTAL_RULE =
    Regex("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$")

/*
 * Inline Markdown parser.
 *
 * Order matters:
 * 1. links
 * 2. code
 * 3. bold
 * 4. strikethrough
 * 5. italic
 */
private val INLINE_TOKEN = Regex(
    """(\[[^\]]+]\([^)]+\))|(`+[^`]+`+)|(\*\*.+?\*\*)|(~~.+?~~)|(\*[^*\n]+\*)|(_[^_\n]+_)"""
)

/* -------------------------------------------------------------------------- */
/* Markdown block parser                                                      */
/* -------------------------------------------------------------------------- */

/**
 * Converts the raw AI response into text/code blocks.
 *
 * Important streaming behavior:
 * If an opening fence has arrived but the closing fence has not arrived yet,
 * the remaining content becomes an open Code block instead of leaking the
 * backticks into the normal text renderer.
 */
private fun splitMarkdownBlocks(raw: String): List<MdBlock> {
    if (raw.isEmpty()) return emptyList()

    val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split("\n")

    val blocks = mutableListOf<MdBlock>()
    val textBuffer = StringBuilder()

    fun flushText() {
        if (textBuffer.isNotEmpty()) {
            blocks += MdBlock.Text(
                content = textBuffer.toString().trimEnd('\n')
            )
            textBuffer.clear()
        }
    }

    var index = 0

    while (index < lines.size) {
        val fenceMatch = FENCE_LINE.matchEntire(lines[index])

        if (fenceMatch == null) {
            textBuffer.append(lines[index])

            if (index < lines.lastIndex) {
                textBuffer.append('\n')
            }

            index++
            continue
        }

        /* Opening fence */
        flushText()

        val language = fenceMatch
            .groupValues
            .getOrNull(1)
            ?.trim()
            ?.ifBlank { null }

        val codeLines = mutableListOf<String>()

        var cursor = index + 1
        var closed = false

        while (cursor < lines.size) {
            if (FENCE_LINE.matches(lines[cursor])) {
                closed = true
                break
            }

            codeLines += lines[cursor]
            cursor++
        }

        blocks += MdBlock.Code(
            language = language,
            code = codeLines.joinToString("\n").trimEnd(),
            isOpen = !closed
        )

        index = if (closed) {
            cursor + 1
        } else {
            lines.size
        }
    }

    flushText()

    return blocks
}

/* -------------------------------------------------------------------------- */
/* Inline renderer                                                            */
/* -------------------------------------------------------------------------- */

/**
 * Pure Kotlin Markdown inline renderer.
 *
 * This function deliberately does NOT call MaterialTheme, remember(), or any
 * other @Composable API. That prevents the compiler errors that occurred in
 * the previous Markdown.kt.
 */
private fun renderInline(
    line: String,
    codeBackground: Color
): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0

        for (match in INLINE_TOKEN.findAll(line)) {

            if (match.range.first > cursor) {
                append(line.substring(cursor, match.range.first))
            }

            val token = match.value

            when {

                /* [label](url) */
                token.startsWith("[") -> {
                    val closingBracket = token.indexOf("](")

                    if (closingBracket > 0 && token.endsWith(")")) {
                        val label = token.substring(1, closingBracket)
                        val url = token.substring(
                            closingBracket + 2,
                            token.length - 1
                        )

                        withStyle(
                            SpanStyle(
                                color = codeBackground.copy(alpha = 0f)
                            )
                        ) {
                            append(label)
                        }

                        /*
                         * The URL is intentionally not displayed.
                         *
                         * We keep the visible Markdown clean without introducing
                         * a clickable URL dependency. The text remains selectable.
                         */
                    } else {
                        append(token)
                    }
                }

                /* `inline code` */
                token.startsWith("`") -> {
                    val content = token.trim('`')

                    withStyle(
                        SpanStyle(
                            fontFamily = MonoFamily,
                            background = codeBackground
                        )
                    ) {
                        append(" ")
                        append(content)
                        append(" ")
                    }
                }

                /* **bold** */
                token.startsWith("**") &&
                    token.endsWith("**") &&
                    token.length >= 4 -> {

                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(
                            token.removePrefix("**")
                                .removeSuffix("**")
                        )
                    }
                }

                /* ~~strike~~ */
                token.startsWith("~~") &&
                    token.endsWith("~~") &&
                    token.length >= 4 -> {

                    withStyle(
                        SpanStyle(
                            textDecoration =
                                androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                    ) {
                        append(
                            token.removePrefix("~~")
                                .removeSuffix("~~")
                        )
                    }
                }

                /* *italic* */
                token.startsWith("*") &&
                    token.endsWith("*") &&
                    token.length >= 3 -> {

                    withStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic
                        )
                    ) {
                        append(
                            token.removePrefix("*")
                                .removeSuffix("*")
                        )
                    }
                }

                /* _italic_ */
                token.startsWith("_") &&
                    token.endsWith("_") &&
                    token.length >= 3 -> {

                    withStyle(
                        SpanStyle(
                            fontStyle = FontStyle.Italic
                        )
                    ) {
                        append(
                            token.removePrefix("_")
                                .removeSuffix("_")
                        )
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
}

/* -------------------------------------------------------------------------- */
/* Code block                                                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun CodeBlock(
    language: String?,
    code: String,
    live: Boolean
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var copied by remember { mutableStateOf(false) }

    /*
     * Default expanded.
     *
     * The user can collapse completed code blocks.
     * Streaming blocks are always expanded.
     */
    var manuallyExpanded by remember {
        mutableStateOf(true)
    }

    val expanded = live || manuallyExpanded

    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "code_chevron"
    )

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    /*
     * CRITICAL CRASH FIX
     *
     * A LazyColumn measures its children with an effectively unbounded
     * vertical constraint.
     *
     * Therefore:
     *
     *     verticalScroll()
     *
     * MUST have a finite height above it.
     *
     * The previous implementation only used heightIn() while live.
     * Completed blocks then became:
     *
     *     verticalScroll(...)
     *
     * inside LazyColumn with no finite height -> crash.
     *
     * We now ALWAYS provide a finite maximum height.
     */
    val codeMaxHeight = if (live) {
        130.dp
    } else {
        320.dp
    }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    /*
     * Keep a streaming block following the newest content.
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
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {

        Column {

            /* ------------------------------------------------------------------ */
            /* Code header                                                        */
            /* ------------------------------------------------------------------ */

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
                        text =
                            "  Writing " +
                                (language
                                    ?.ifBlank { null }
                                    ?: "code") +
                                "…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.weight(1f)
                    )

                } else {

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement =
                            Arrangement.spacedBy(6.dp)
                    ) {

                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )

                        Text(
                            text = displayLanguage(language),
                            style =
                                MaterialTheme.typography.labelSmall,
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant,
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
                            val intent =
                                Intent(Intent.ACTION_SEND).apply {
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
                        icon =
                            if (copied) {
                                Icons.Default.Check
                            } else {
                                Icons.Default.ContentCopy
                            },
                        contentDescription =
                            if (copied) {
                                "Copied"
                            } else {
                                "Copy code"
                            }
                    ) {
                        clipboard.setText(
                            AnnotatedString(code)
                        )
                        copied = true
                    }

                    HeaderIconButton(
                        icon =
                            Icons.Default.KeyboardArrowDown,
                        contentDescription =
                            if (expanded) {
                                "Collapse"
                            } else {
                                "Expand"
                            },
                        modifier =
                            Modifier.rotate(chevronRotation)
                    ) {
                        manuallyExpanded = !manuallyExpanded
                    }
                }
            }

            /* ------------------------------------------------------------------ */
            /* Code content                                                       */
            /* ------------------------------------------------------------------ */

            if (expanded) {

                Box(
                    modifier = Modifier
                        /*
                         * THIS MUST ALWAYS EXIST ABOVE verticalScroll().
                         *
                         * Both live and completed code blocks are now safe
                         * inside LazyColumn.
                         */
                        .heightIn(
                            max = codeMaxHeight
                        )
                        .verticalScroll(
                            verticalScrollState
                        )
                        .horizontalScroll(
                            horizontalScrollState
                        )
                        .padding(14.dp)
                ) {

                    SelectionContainer {

                        Text(
                            text = code,
                            fontFamily = MonoFamily,
                            style =
                                MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Header icon button                                                         */
/* -------------------------------------------------------------------------- */

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        modifier = Modifier.size(30.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint =
                MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.size(17.dp)
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Language display                                                           */
/* -------------------------------------------------------------------------- */

private fun displayLanguage(
    language: String?
): String {
    val normalized = language
        ?.trim()
        ?.ifBlank { null }

    return normalized
        ?.replaceFirstChar {
            if (it.isLowerCase()) {
                it.titlecase()
            } else {
                it.toString()
            }
        }
        ?: "Text"
}

/* -------------------------------------------------------------------------- */
/* File extension                                                             */
/* -------------------------------------------------------------------------- */

private fun extensionFor(
    language: String?
): String {
    return when (
        language
            ?.lowercase()
            ?.trim()
    ) {

        "kotlin",
        "kt" -> "kt"

        "java" -> "java"

        "python",
        "py" -> "py"

        "javascript",
        "js" -> "js"

        "typescript",
        "ts" -> "ts"

        "tsx" -> "tsx"

        "jsx" -> "jsx"

        "html",
        "htm" -> "html"

        "css" -> "css"

        "scss" -> "scss"

        "json" -> "json"

        "xml" -> "xml"

        "bash",
        "sh",
        "shell" -> "sh"

        "sql" -> "sql"

        "yaml",
        "yml" -> "yaml"

        "markdown",
        "md" -> "md"

        "c" -> "c"

        "cpp",
        "c++" -> "cpp"

        "csharp",
        "cs" -> "cs"

        "go" -> "go"

        "rust",
        "rs" -> "rs"

        "swift" -> "swift"

        "dart" -> "dart"

        "php" -> "php"

        "ruby",
        "rb" -> "rb"

        "text",
        "txt" -> "txt"

        else -> "txt"
    }
}

/* -------------------------------------------------------------------------- */
/* Save snippet                                                               */
/* -------------------------------------------------------------------------- */

/**
 * Saves a temporary code snippet into cache and exposes it through the
 * existing FileProvider.
 *
 * This is intentionally a normal Kotlin function, not @Composable.
 */
private fun saveSnippetForSharing(
    context: Context,
    language: String?,
    code: String
): Uri? {
    return try {

        val directory =
            java.io.File(
                context.cacheDir,
                "snippets"
            ).apply {
                mkdirs()
            }

        val file =
            java.io.File(
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
/* Pulsing streaming indicator                                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun PulsingDot(
    color: Color
) {
    val transition =
        rememberInfiniteTransition(
            label = "markdown_pulse"
        )

    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(700),
                repeatMode = RepeatMode.Reverse
            ),
        label = "markdown_pulse_alpha"
    )

    Surface(
        color = color.copy(alpha = alpha),
        shape = CircleShape,
        modifier = Modifier.size(7.dp)
    ) {}
}

/* -------------------------------------------------------------------------- */
/* Markdown text                                                              */
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
        modifier = modifier
    ) {

        for (block in blocks) {

            when (block) {

                is MdBlock.Code -> {

                    CodeBlock(
                        language = block.language,
                        code = block.code,
                        live =
                            isStreaming &&
                                block.isOpen
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
/* Normal Markdown text block                                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun MarkdownTextBlock(
    content: String,
    codeBackground: Color
) {
    if (content.isBlank()) return

    SelectionContainer {

        Column {

            val lines = content
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .split("\n")

            var index = 0

            while (index < lines.size) {

                val rawLine = lines[index]

                /*
                 * Preserve paragraph separation without rendering empty
                 * Text composables.
                 */
                if (rawLine.isBlank()) {
                    index++
                    continue
                }

                val header =
                    HEADER_LINE.matchEntire(rawLine)

                val bullet =
                    BULLET_LINE.matchEntire(rawLine)

                val numbered =
                    NUMBERED_LINE.matchEntire(rawLine)

                val quote =
                    QUOTE_LINE.matchEntire(rawLine)

                val horizontalRule =
                    HORIZONTAL_RULE.matches(rawLine)

                when {

                    /* ------------------------------------------------------ */
                    /* Horizontal rule                                         */
                    /* ------------------------------------------------------ */

                    horizontalRule -> {

                        Surface(
                            color =
                                MaterialTheme.colorScheme
                                    .outline.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    vertical = 8.dp
                                )
                                .size(
                                    height = 1.dp,
                                    width = 1.dp
                                )
                        ) {}
                    }

                    /* ------------------------------------------------------ */
                    /* Heading                                                  */
                    /* ------------------------------------------------------ */

                    header != null -> {

                        val level =
                            header.groupValues[1].length

                        val headingText =
                            header.groupValues[2]
                                .trim()

                        val style =
                            when (level) {

                                1 ->
                                    MaterialTheme.typography
                                        .headlineSmall

                                2 ->
                                    MaterialTheme.typography
                                        .titleLarge

                                3 ->
                                    MaterialTheme.typography
                                        .titleMedium

                                else ->
                                    MaterialTheme.typography
                                        .bodyLarge
                            }

                        Text(
                            text = renderInline(
                                headingText,
                                codeBackground
                            ),
                            style = style,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(
                                top =
                                    if (level <= 2) {
                                        10.dp
                                    } else {
                                        6.dp
                                    },
                                bottom = 3.dp
                            )
                        )
                    }

                    /* ------------------------------------------------------ */
                    /* Bullet list                                              */
                    /* ------------------------------------------------------ */

                    bullet != null -> {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 4.dp,
                                    top = 2.dp
                                ),
                            verticalAlignment =
                                Alignment.Top
                        ) {

                            Text(
                                text = "•",
                                style =
                                    MaterialTheme.typography
                                        .bodyMedium,
                                modifier =
                                    Modifier.padding(
                                        end = 8.dp
                                    )
                            )

                            Text(
                                text = renderInline(
                                    bullet.groupValues[1]
                                        .trim(),
                                    codeBackground
                                ),
                                style =
                                    MaterialTheme.typography
                                        .bodyMedium,
                                modifier =
                                    Modifier.weight(1f)
                            )
                        }
                    }

                    /* ------------------------------------------------------ */
                    /* Numbered list                                           */
                    /* ------------------------------------------------------ */

                    numbered != null -> {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = 4.dp,
                                    top = 2.dp
                                ),
                            verticalAlignment =
                                Alignment.Top
                        ) {

                            Text(
                                text =
                                    "${numbered.groupValues[1]}.",
                                style =
                                    MaterialTheme.typography
                                        .bodyMedium,
                                modifier =
                                    Modifier.padding(
                                        end = 8.dp
                                    )
                            )

                            Text(
                                text = renderInline(
                                    numbered.groupValues[2]
                                        .trim(),
                                    codeBackground
                                ),
                                style =
                                    MaterialTheme.typography
                                        .bodyMedium,
                                modifier =
                                    Modifier.weight(1f)
                            )
                        }
                    }

                    /* ------------------------------------------------------ */
                    /* Blockquote                                               */
                    /* ------------------------------------------------------ */

                    quote != null -> {

                        Surface(
                            color =
                                MaterialTheme.colorScheme
                                    .surfaceVariant,
                            shape =
                                RoundedCornerShape(6.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = 3.dp,
                                        bottom = 2.dp
                                    )
                        ) {

                            Row(
                                verticalAlignment =
                                    Alignment.Top
                            ) {

                                Surface(
                                    color =
                                        MaterialTheme.colorScheme
                                            .primary,
                                    modifier =
                                        Modifier.size(
                                            width = 3.dp,
                                            height = 36.dp
                                        )
                                ) {}

                                Text(
                                    text = renderInline(
                                        quote.groupValues[1],
                                        codeBackground
                                    ),
                                    modifier =
                                        Modifier.padding(
                                            horizontal = 10.dp,
                                            vertical = 8.dp
                                        ),
                                    style =
                                        MaterialTheme.typography
                                            .bodyMedium,
                                    fontStyle =
                                        FontStyle.Italic
                                )
                            }
                        }
                    }

                    /* ------------------------------------------------------ */
                    /* Normal paragraph                                         */
                    /* ------------------------------------------------------ */

                    else -> {

                        Text(
                            text = renderInline(
                                rawLine,
                                codeBackground
                            ),
                            style =
                                MaterialTheme.typography
                                    .bodyMedium,
                            modifier =
                                Modifier.padding(
                                    top = 2.dp,
                                    bottom = 1.dp
                                )
                        )
                    }
                }

                index++
            }
        }
    }
}