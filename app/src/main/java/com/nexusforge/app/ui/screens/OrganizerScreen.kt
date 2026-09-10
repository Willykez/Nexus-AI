package com.nexusforge.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.organizer.LogLine
import com.nexusforge.app.ui.components.ThinkingIndicator
import com.nexusforge.app.ui.theme.ErrorRed
import com.nexusforge.app.ui.theme.SuccessGreen
import com.nexusforge.app.viewmodel.OrganizerPhase

@Composable
fun OrganizerScreen(
    log: List<LogLine>,
    phase: OrganizerPhase,
    thinkingChars: Int,
    resultSummary: String?,
    isRunning: Boolean,
    onOrganize: (String) -> Unit,
    onViewWorkspace: () -> Unit
) {
    var dump by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Organize a project dump", style = MaterialTheme.typography.titleLarge)
        Text(
            "Paste an entire project's source as one blob — labeled or not. It'll be split back " +
                "into real files and written into your active project.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box {
            OutlinedTextField(
                value = dump,
                onValueChange = { dump = it },
                modifier = Modifier.fillMaxWidth().height(180.dp),
                placeholder = { Text("Paste your project's source here…") }
            )
            IconButton(
                onClick = { clipboard.getText()?.text?.let { dump = it } },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
            ) {
                Icon(Icons.Default.ContentPaste, contentDescription = "Paste from clipboard")
            }
        }
        if (dump.isNotEmpty()) {
            Text(
                "${dump.length} characters",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = { onOrganize(dump) },
            enabled = dump.isNotBlank() && !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isRunning) "Organizing…" else "Organize into files")
        }

        when (phase) {
            OrganizerPhase.THINKING -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThinkingIndicator()
                if (thinkingChars > 0) {
                    Text(
                        "Restructuring… ($thinkingChars characters so far)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OrganizerPhase.WRITING -> OrganizerLog(log, modifier = Modifier.weight(1f))
            OrganizerPhase.DONE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OrganizerLog(log, modifier = Modifier.weight(1f, fill = false))
                SuccessBanner(resultSummary ?: "Done.", onViewWorkspace)
            }
            OrganizerPhase.FAILED -> OrganizerLog(log, modifier = Modifier.weight(1f))
            OrganizerPhase.IDLE -> {}
        }
    }
}

@Composable
private fun OrganizerLog(log: List<LogLine>, modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(log) { line -> OrganizerLogRow(line) }
    }
}

@Composable
private fun OrganizerLogRow(line: LogLine) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (line.isError) Icons.Default.ErrorOutline else Icons.Default.Check,
            contentDescription = null,
            tint = if (line.isError) ErrorRed else SuccessGreen,
            modifier = Modifier.size(16.dp)
        )
        Text(
            line.text,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodyMedium,
            color = if (line.isError) ErrorRed else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
}

@Composable
private fun SuccessBanner(summary: String, onViewWorkspace: () -> Unit) {
    Surface(
        color = SuccessGreen.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.TaskAlt, contentDescription = null, tint = SuccessGreen)
            Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onViewWorkspace) { Text("View") }
        }
    }
}
