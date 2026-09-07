package com.nexusai.agent.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.nexusai.agent.data.ProviderConfig
import com.nexusai.agent.data.ProviderPreset
import com.nexusai.agent.ui.theme.NexusSecondary
import com.nexusai.agent.ui.theme.NexusSurfaceContainer

@Composable
fun SettingsScreen(
    current: ProviderConfig,
    onSave: (ProviderConfig) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var baseUrl by remember(current) { mutableStateOf(current.baseUrl) }
    var apiKey by remember(current) { mutableStateOf(current.apiKey) }
    var model by remember(current) { mutableStateOf(current.model) }
    var showKey by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Provider Settings", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Connect to any OpenAI-compatible /chat/completions endpoint.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProviderPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = baseUrl == preset.baseUrl,
                        onClick = {
                            baseUrl = preset.baseUrl
                            model = preset.defaultModel
                        },
                        label = { Text(preset.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexusSecondary.copy(alpha = 0.2f),
                            selectedLabelColor = NexusSecondary
                        )
                    )
                }
            }

            LabeledField("Base URL", baseUrl) { baseUrl = it }
            LabeledField(
                label = "API Key",
                value = apiKey,
                onChange = { apiKey = it },
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = "Toggle visibility")
                    }
                }
            )
            LabeledField("Model", model) { model = it }

            Button(
                onClick = { onSave(ProviderConfig(baseUrl = baseUrl.trim(), apiKey = apiKey.trim(), model = model.trim())) },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = NexusSecondary)
            ) {
                Text("Save Connection", color = androidx.compose.ui.graphics.Color(0xFF00390D))
            }

            Text(
                "Keys are stored locally on-device only, never transmitted anywhere besides the base URL you configure.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    onChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = NexusSurfaceContainer,
                focusedContainerColor = NexusSurfaceContainer,
                focusedBorderColor = NexusSecondary
            )
        )
    }
}
