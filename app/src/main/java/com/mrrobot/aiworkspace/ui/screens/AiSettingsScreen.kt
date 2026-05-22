package com.mrrobot.aiworkspace.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrrobot.aiworkspace.data.AiModels
import com.mrrobot.aiworkspace.data.ApiProvider
import com.mrrobot.aiworkspace.ui.components.CyberButton
import com.mrrobot.aiworkspace.ui.components.GlassCard
import com.mrrobot.aiworkspace.ui.components.ScreenShell
import com.mrrobot.aiworkspace.ui.components.Subtitle
import com.mrrobot.aiworkspace.ui.components.Title
import com.mrrobot.aiworkspace.viewmodel.SettingsUiState
import com.mrrobot.aiworkspace.viewmodel.SettingsViewModel

@Composable
fun AiSettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    var editorProvider by remember { mutableStateOf(ApiProvider.OpenRouter) }
    var editorApiKey by remember { mutableStateOf("") }
    var editorModel by remember {
        mutableStateOf(AiModels.defaultForProvider(ApiProvider.OpenRouter).id)
    }

    LaunchedEffect(state.isLoaded) {
        if (state.isLoaded) {
            val firstSaved = state.configuredProviderModels().firstOrNull()
            editorProvider = firstSaved?.provider ?: ApiProvider.OpenRouter
            editorApiKey = state.keyFor(editorProvider)
            editorModel = firstSaved?.modelId ?: AiModels.defaultForProvider(editorProvider).id
        }
    }

    ScreenShell {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item {
                Text(
                    text = "AI Settings",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 34.sp
                )

                Spacer(Modifier.height(8.dp))

                Subtitle("Configure providers, API keys, models, and theme.")

                Spacer(Modifier.height(14.dp))

                GlassCard {
                    ProviderDashboard(
                        state = state,
                        onEditProvider = { provider ->
                            editorProvider = provider
                            editorApiKey = state.keyFor(provider)
                            editorModel = state.modelFor(provider)
                        },
                        onActivateProvider = viewModel::activateProvider
                    )

                    Spacer(Modifier.height(18.dp))

                    AddApiKeyCard(
                        provider = editorProvider,
                        apiKey = editorApiKey,
                        model = editorModel,
                        onProviderChange = { provider ->
                            editorProvider = provider
                            editorApiKey = state.keyFor(provider)
                            editorModel = state.modelFor(provider)
                        },
                        onApiKeyChange = { editorApiKey = it },
                        onModelChange = { editorModel = it },
                        onSave = {
                            viewModel.saveProviderConfiguration(
                                provider = editorProvider,
                                model = editorModel,
                                apiKey = editorApiKey,
                                activate = false
                            )
                        },
                        onSaveAndActivate = {
                            viewModel.saveProviderConfiguration(
                                provider = editorProvider,
                                model = editorModel,
                                apiKey = editorApiKey,
                                activate = true
                            )
                        }
                    )

                    Spacer(Modifier.height(18.dp))

                    CyberButton("Save Settings") {
                        viewModel.save()
                    }

                    if (state.savedMessage.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Subtitle(state.savedMessage)
                    }

                    Spacer(Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { viewModel.clear() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text("Clear Settings")
                    }
                }

                Spacer(Modifier.height(16.dp))

                GlassCard {
                    Title("Model Catalog")

                    Spacer(Modifier.height(8.dp))

                    Subtitle("Choose a provider above to view its supported models.")

                    Spacer(Modifier.height(8.dp))

                    AiModels.byProvider(editorProvider).forEach { model ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    editorModel = model.id
                                }
                                .padding(vertical = 10.dp)
                        ) {
                            Subtitle("${model.name} — ${model.provider}")
                            Subtitle(model.id)
                        }

                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderDashboard(
    state: SettingsUiState,
    onEditProvider: (ApiProvider) -> Unit,
    onActivateProvider: (ApiProvider) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Title("AI Provider")

        Spacer(Modifier.height(6.dp))

        Subtitle("Configure models and runtime keys.")

        Spacer(Modifier.height(14.dp))

        ActiveProviderCard(state = state)

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Keys",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                ),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(
                    text = "+ Add Key",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        val saved = state.configuredProviderModels()

        if (saved.isEmpty()) {
            Subtitle("No API keys saved yet. Add a key below.")
        } else {
            saved.forEach { config ->
                SavedKeyRow(
                    provider = config.provider,
                    modelId = config.modelId,
                    isActive = state.hasActiveConfiguration() &&
                        config.provider == state.selectedProvider,
                    onEdit = { onEditProvider(config.provider) },
                    onActivate = { onActivateProvider(config.provider) }
                )

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun ActiveProviderCard(state: SettingsUiState) {
    val hasActive = state.hasActiveConfiguration()

    Surface(
        color = if (hasActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (hasActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
            }
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Active",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = if (hasActive) {
                        state.selectedProvider.displayName
                    } else {
                        "No active model"
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = if (hasActive) {
                        AiModels.findById(state.modelFor(state.selectedProvider)).name
                    } else {
                        "Add a key below or activate a saved provider."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Text(
                text = if (hasActive) "Connected" else "No Key",
                color = if (hasActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SavedKeyRow(
    provider: ApiProvider,
    modelId: String,
    isActive: Boolean,
    onEdit: () -> Unit,
    onActivate: () -> Unit
) {
    Surface(
        color = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            1.dp,
            if (isActive) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
            }
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Text(
                    text = "${AiModels.findById(modelId).name} • ${if (isActive) "Active" else "Saved"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Edit",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clickable { onEdit() }
                        .padding(6.dp)
                )

                if (!isActive) {
                    Text(
                        text = "Activate",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clickable { onActivate() }
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddApiKeyCard(
    provider: ApiProvider,
    apiKey: String,
    model: String,
    onProviderChange: (ApiProvider) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (String) -> Unit,
    onSave: () -> Unit,
    onSaveAndActivate: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Text(
                text = "Add API Key",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            Spacer(Modifier.height(12.dp))

            ProviderDropdown(
                selected = provider,
                onSelected = onProviderChange
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text(provider.keyLabel) },
                placeholder = { Text(provider.keyPlaceholder) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            ModelDropdown(
                selectedProvider = provider,
                selectedModel = model,
                onSelected = onModelChange
            )

            Spacer(Modifier.height(10.dp))

            Subtitle(provider.helpText)

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Save")
                }

                Button(
                    onClick = onSaveAndActivate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Save & Activate",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    selected: ApiProvider,
    onSelected: (ApiProvider) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Provider") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            ApiProvider.values().forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider.displayName) },
                    onClick = {
                        onSelected(provider)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    selectedProvider: ApiProvider,
    selectedModel: String,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val models = AiModels.byProvider(selectedProvider)
    val selected = models.firstOrNull { it.id == selectedModel }
        ?: AiModels.defaultForProvider(selectedProvider)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("Model") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.name)
                            Text(
                                text = model.id,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    },
                    onClick = {
                        onSelected(model.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

