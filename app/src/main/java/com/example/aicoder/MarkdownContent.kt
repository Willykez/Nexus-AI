package com.example.aicoder

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Bullet(val text: String, val indent: Int) : MarkdownBlock
    data class Numbered(val number: String, val text: String, val indent: Int) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val language: String, val text: String) : MarkdownBlock
}

private val Fence = Regex("^\\s*```(.*)$")
private val BulletRx = Regex("^(\\s*)[-*+]\\s+(.*)$")
private val NumberRx = Regex("^(\\s*)(\\d+)[.)]\\s+(.*)$")
private val InlineRx = Regex("\\*\\*([^*]+)\\*\\*|__([^_]+)__|`([^`]+)`|(?<!\\*)\\*([^*]+)\\*(?!\\*)")

private fun parseMarkdown(raw: String): List<MarkdownBlock> {
    val lines = raw.replace("\r\n", "\n").split('\n')
    val result = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotEmpty()) {
            result += MarkdownBlock.Paragraph(paragraph.toString())
            paragraph.clear()
        }
    }
    var inCode = false
    var language = ""
    val code = StringBuilder()
    for (line in lines) {
        val fence = Fence.matchEntire(line)
        if (fence != null) {
            if (inCode) {
                result += MarkdownBlock.Code(language, code.toString().removeSuffix("\n"))
                code.clear()
                language = ""
                inCode = false
            } else {
                flush()
                language = fence.groupValues[1].trim()
                inCode = true
            }
            continue
        }
        if (inCode) {
            code.append(line).append('\n')
            continue
        }
        val trimmed = line.trimEnd()
        when {
            trimmed.isBlank() -> flush()
            trimmed.startsWith("### ") -> { flush(); result += MarkdownBlock.Heading(3, trimmed.removePrefix("### ")) }
            trimmed.startsWith("## ") -> { flush(); result += MarkdownBlock.Heading(2, trimmed.removePrefix("## ")) }
            trimmed.startsWith("# ") -> { flush(); result += MarkdownBlock.Heading(1, trimmed.removePrefix("# ")) }
            trimmed.trimStart().startsWith(">") -> { flush(); result += MarkdownBlock.Quote(trimmed.trimStart().removePrefix(">").trim()) }
            BulletRx.matches(trimmed) -> { flush(); val m = BulletRx.matchEntire(trimmed)!!; result += MarkdownBlock.Bullet(m.groupValues[2], m.groupValues[1].length) }
            NumberRx.matches(trimmed) -> { flush(); val m = NumberRx.matchEntire(trimmed)!!; result += MarkdownBlock.Numbered(m.groupValues[2], m.groupValues[3], m.groupValues[1].length) }
            else -> { if (paragraph.isNotEmpty()) paragraph.append(' '); paragraph.append(trimmed.trim()) }
        }
    }
    if (inCode) result += MarkdownBlock.Code(language, code.toString().removeSuffix("\n")) else flush()
    return result
}

private fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (m in InlineRx.findAll(text)) {
        if (m.range.first > cursor) append(text.substring(cursor, m.range.first))
        when {
            m.groupValues[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            m.groupValues[2].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[2]) }
            m.groupValues[3].isNotEmpty() -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Color(0xFF9FCBFF))) { append(m.groupValues[3]) }
            else -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[4]) }
        }
        cursor = m.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

@Composable
fun MarkdownContent(text: String) {
    val clipboard = LocalClipboardManager.current
    val parsed = remember(text) { parseMarkdown(text) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        parsed.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(inline(block.text), color = Color(0xFFE8EDF2), fontSize = when (block.level) { 1 -> 19.sp; 2 -> 16.sp; else -> 14.sp }, fontWeight = FontWeight.Bold)
                is MarkdownBlock.Paragraph -> Text(inline(block.text), color = Color(0xFFE8EDF2), fontSize = 14.sp, lineHeight = 22.sp)
                is MarkdownBlock.Bullet -> Row(Modifier.padding(start = (4 + block.indent * 4).dp)) { Text("•  ", color = Color(0xFF8FC7FF)); Text(inline(block.text), color = Color(0xFFE8EDF2), fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f)) }
                is MarkdownBlock.Numbered -> Row(Modifier.padding(start = (4 + block.indent * 4).dp)) { Text("${block.number}.  ", color = Color(0xFF8FC7FF)); Text(inline(block.text), color = Color(0xFFE8EDF2), fontSize = 14.sp, lineHeight = 22.sp, modifier = Modifier.weight(1f)) }
                is MarkdownBlock.Quote -> Row(Modifier.padding(start = 6.dp)) { Box(Modifier.width(3.dp).height(20.dp).background(MaterialTheme.colorScheme.outline)); Text(inline(block.text), color = Color(0xFFB9C3CE), fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 8.dp)) }
                is MarkdownBlock.Code -> Surface(color = Color(0xFF080D13), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 10.dp, end = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(block.language.ifBlank { "code" }, color = Color(0xFF8D98A6), fontFamily = FontFamily.Monospace, fontSize = 9.sp, modifier = Modifier.padding(top = 8.dp))
                            IconButton(onClick = { clipboard.setText(AnnotatedString(block.text)) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy code block", tint = Color(0xFF8FC7FF)) }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)) { Text(block.text, color = Color(0xFFD7E0EA), fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}
