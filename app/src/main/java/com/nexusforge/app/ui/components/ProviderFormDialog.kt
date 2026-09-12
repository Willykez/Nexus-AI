package com.nexusforge.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.nexusforge.app.data.ProviderProfile
import com.nexusforge.app.data.SettingsStore

/**
 * Add-or-edit form for one saved provider profile. When [editing] is non-null the key field is
 * left blank and saving keeps the previously stored key — the same "blank means unchanged"
 * convention used everywhere else credentials are edited in this app.
 */
@Composable
fun ProviderFormDialog(
    editing: ProviderProfile?,
    onDismiss: () -> Unit,
    onSave: (name: String, baseUrl: String, apiKey: String, model: String) -> Unit
) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var baseUrl by remember { mutableStateOf(editing?.baseUrl ?: SettingsStore.PRESETS.first().second) }
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(editing?.model ?: SettingsStore.PRESETS.first().third) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Add a provider" else "Edit provider") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SettingsStore.PRESETS) { (label, url, defaultModel) ->
                        AssistChip(
                            onClick = {
                                baseUrl = url; model = defaultModel
                                if (name.isBlank()) name = label
                            },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    name, { name = it }, label = { Text("Name (e.g. \"Gemini — personal\")") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    apiKey, { apiKey = it },
                    label = { Text(if (editing != null) "API key (saved — leave blank to keep it)" else "API key") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(
                    "The key is encrypted on-device with Android Keystore and never leaves it except to this provider's own API.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, baseUrl, apiKey, model) },
                enabled = baseUrl.isNotBlank() && model.isNotBlank() && (editing != null || apiKey.isNotBlank() || SettingsStore.isKeylessLocal(baseUrl))
            ) { Text(if (editing == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
