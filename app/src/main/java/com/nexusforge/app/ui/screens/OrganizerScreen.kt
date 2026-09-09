package com.nexusforge.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.organizer.LogLine
import com.nexusforge.app.ui.theme.ErrorRed

@Composable
fun OrganizerScreen(
    log: List<LogLine>,
    isRunning: Boolean,
    onOrganize: (String) -> Unit
) {
    var dump by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Organize a project dump", style = MaterialTheme.typography.titleLarge)
        Text(
            "Paste an entire project's source as one blob — labeled or not. The agent will split it back " +
                "into real files and write them into your active project (the same Sandbox or attached folder Chat uses).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = dump,
            onValueChange = { dump = it },
            modifier = Modifier.fillMaxWidth().height(180.dp),
            placeholder = { Text("Paste your project's source here…") }
        )

        Button(
            onClick = { onOrganize(dump) },
            enabled = dump.isNotBlank() && !isRunning,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isRunning) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).height(16.dp), strokeWidth = 2.dp)
            }
            Text(if (isRunning) "Organizing…" else "Organize into files")
        }

        if (log.isNotEmpty()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(log) { line ->
                    Text(
                        line.text,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (line.isError) ErrorRed else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
