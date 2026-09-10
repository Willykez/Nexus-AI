package com.nexusforge.app.ui.markdown

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusforge.app.ui.theme.MonoFamily
import kotlinx.coroutines.delay

/**
 * A hand-rolled Markdown subset: headers, bold/italic/inline code, bullet/numbered lists,
 * blockquotes, and fenced code blocks with a one-tap copy button. Not full CommonMark, but it
 * covers everything model replies actually use — which is what determines whether the reply
 * reads like a person wrote it, or like raw asterisks and backticks sitting on screen.
 */
private sealed class MdBlock {
    /** [isOpen] means the closing fence hasn't arrived yet — this block is still being written. */
    data class Code(val language: String?, val code: String, val isOpen: Boolean = false) : MdBlock()
    data class Text(val content: String) : MdBlock()
}

private val BULLET_LINE = Regex("^(\\s*)[*\\-•]\\s+(.*)$")
private val NUMBERED_LINE = Regex("^(\\s*)(\\d+)\\.\\s+(.*)$")
private val HEADER_LINE = Regex("^(#{1,4})\\s+(.*)$")
private val QUOTE_LINE = Regex("^>\\s?(.*)$")
private val INLINE_TOKEN = Regex("(\\*\\*[^*]+\\*\\*)|(`[^`]+`)|(\\*[^*]+\\*)")
private val FENCE_LINE = Regex("^\\s*`{3,}(\\S*)\\s*$")

/**
 * Splits on fence lines only — line-by-line, not substring search — so a fence is always
 * consumed as a delimiter, even a half-typed one while a reply is still streaming in, and a
 * trailing closing fence never leaks through as literal backticks.
 */
private fun splitMarkdownBlocks(raw: String): List<MdBlock> {
    val lines = raw.split("\n")
    val blocks = mutableListOf<MdBlock>()
    val buffer = StringBuilder()

    fun flush() {
        if (buffer.isNotEmpty()) {
            blocks.add(MdBlock.Text(buffer.toString()))
            buffer.clear()
        }
    }

    var i = 0
    while (i < lines.size) {
        val fence = FENCE_LINE.find(lines[i])
        if (fence == null) {
            buffer.append(lines[i]).append('\n')
            i++
            continue
        }
        flush()
        val lang = fence.groupValues[1].ifBlank { null }
        val codeLines = mutableListOf<String>()
        var j = i + 1
        var closed = false
        while (j < lines.size) {
            if (FENCE_LINE.matches(lines[j])) { closed = true; break }
            codeLines.add(lines[j])
            j++
        }
        blocks.add(MdBlock.Code(lang, codeLines.joinToString("\n"), isOpen = !closed))
        i = if (closed) j + 1 else lines.size
    }
    flush()
    return blocks
}

private fun renderInline(line: String, codeBackground: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (match in INLINE_TOKEN.findAll(line)) {
        if (match.range.first > cursor) append(line.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(token.removeSurrounding("**")) }
            token.startsWith("`") -> withStyle(SpanStyle(fontFamily = MonoFamily, background = codeBackground)) {
                append(" ${token.removeSurrounding("`")} ")
            }
            token.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.removeSurrounding("*")) }
        }
        cursor = match.range.last + 1
    }
    if (cursor < line.length) append(line.substring(cursor))
}

@Composable
private fun CodeBlock(language: String?, code: String, live: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1600); copied = false } }

    // While still being written, cap the height and auto-scroll to the bottom — a live "tail -f"
    // view of the last few lines rather than an ever-growing wall of text scrolling the whole
    // screen down. Once the fence closes (or the message finishes), this becomes a normal,
    // fully-visible code block on the next recomposition.
    val scrollState = rememberScrollState()
    LaunchedEffect(code, live) {
        if (live) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, if (live) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column {
            // macOS-style traffic-light header, matching the reference design's code-block chrome.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TrafficDot(Color(0xFFFF5F57))
                    TrafficDot(Color(0xFFFEBC2E))
                    TrafficDot(Color(0xFF28C840))
                }
                if (live) {
                    Row(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingDot(MaterialTheme.colorScheme.primary)
                        Text(
                            "  Writing " + (language?.ifBlank { null } ?: "code") + "…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    }
                } else {
                    Text(
                        text = (language?.ifBlank { null } ?: "code").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString(code))
                            copied = true
                        }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy code",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                if (copied) "Copied" else "Copy",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .let { if (live) it.heightIn(max = 130.dp) else it }
                    .verticalScroll(if (live) scrollState else rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(text = code, fontFamily = MonoFamily, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun TrafficDot(color: Color) {
    Surface(color = color, shape = androidx.compose.foundation.shape.CircleShape, modifier = Modifier.size(9.dp)) {}
}

@Composable
private fun PulsingDot(color: Color) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha"
    )
    Surface(
        color = color.copy(alpha = alpha),
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier = Modifier.size(7.dp)
    ) {}
}

@Composable
fun MarkdownText(raw: String, modifier: Modifier = Modifier, isStreaming: Boolean = false) {
    val blocks = remember(raw) { splitMarkdownBlocks(raw) }
    val codeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Column(modifier = modifier) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Code -> CodeBlock(block.language, block.code, live = isStreaming && block.isOpen)
                is MdBlock.Text -> {
                    SelectionContainer {
                        Column {
                            for (rawLine in block.content.split("\n")) {
                                if (rawLine.isBlank()) continue
                                val header = HEADER_LINE.find(rawLine)
                                val bullet = BULLET_LINE.find(rawLine)
                                val numbered = NUMBERED_LINE.find(rawLine)
                                val quote = QUOTE_LINE.find(rawLine)
                                when {
                                    header != null -> {
                                        val level = header.groupValues[1].length
                                        Text(
                                            text = renderInline(header.groupValues[2], codeBackground),
                                            style = if (level <= 2) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                        )
                                    }
                                    bullet != null -> Row(Modifier.padding(start = (bullet.groupValues[1].length * 4).dp, top = 2.dp)) {
                                        Text("•  ", style = MaterialTheme.typography.bodyMedium)
                                        Text(renderInline(bullet.groupValues[2], codeBackground), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    numbered != null -> Row(Modifier.padding(start = (numbered.groupValues[1].length * 4).dp, top = 2.dp)) {
                                        Text("${numbered.groupValues[2]}.  ", style = MaterialTheme.typography.bodyMedium)
                                        Text(renderInline(numbered.groupValues[3], codeBackground), style = MaterialTheme.typography.bodyMedium)
                                    }
                                    quote != null -> Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                    ) {
                                        Text(
                                            renderInline(quote.groupValues[1], codeBackground),
                                            modifier = Modifier.padding(8.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontStyle = FontStyle.Italic
                                        )
                                    }
                                    else -> Text(
                                        renderInline(rawLine, codeBackground),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
