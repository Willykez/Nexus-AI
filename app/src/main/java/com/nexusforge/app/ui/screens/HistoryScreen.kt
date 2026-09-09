package com.nexusforge.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexusforge.app.data.history.ChatSessionSummary
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    sessions: List<ChatSessionSummary>,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    if (sessions.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("No past conversations yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Everything you talk about here gets saved on-device, so you can pick any thread back up later.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(sessions, key = { it.id }) { session ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(session.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(session.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text(
                        "${session.providerLabel} · ${session.projectLabel} · ${formatDate(session.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDelete(session.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete conversation", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider()
        }
    }
}

private fun formatDate(millis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
