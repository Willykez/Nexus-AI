package com.nexusforge.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * "It should never do something weird like swallowing the send button off the bottom of the
 * screen because I pasted three paragraphs. It should just... handle it." — the text field caps
 * its own growth (heightIn max) and scrolls internally past that; the send button never moves.
 */
@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isRunning: Boolean,
    providerBadge: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        androidx.compose.foundation.layout.Column(Modifier.padding(10.dp)) {
            Row(Modifier.padding(bottom = 6.dp, start = 2.dp)) { providerBadge() }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 180.dp), // internal scroll kicks in past this, button stays put
                    placeholder = { Text("Ask for anything — build, fix, rename, explain…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    maxLines = 8
                )
                if (isRunning) {
                    FilledIconButton(onClick = onStop) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop")
                    }
                } else {
                    FilledIconButton(onClick = onSend, enabled = text.isNotBlank()) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Send")
                    }
                }
            }
        }
    }
}
