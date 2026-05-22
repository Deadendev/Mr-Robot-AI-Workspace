package com.mrrobot.aiworkspace.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrrobot.aiworkspace.data.AppThemeMode
import com.mrrobot.aiworkspace.ui.components.GlassCard
import com.mrrobot.aiworkspace.ui.components.Subtitle
import com.mrrobot.aiworkspace.ui.components.Title
import com.mrrobot.aiworkspace.viewmodel.GeneralSettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Kai 9000-parity Theme dropdown options — System / Light / Dark / Pure-Black OLED. */
private data class ThemeOption(
    val mode: AppThemeMode,
    val label: String
)

private val THEME_OPTIONS = listOf(
    ThemeOption(AppThemeMode.Auto, "System"),
    ThemeOption(AppThemeMode.Light, "Light"),
    ThemeOption(AppThemeMode.Dark, "Dark"),
    ThemeOption(AppThemeMode.PureBlack, "Pure-Black OLED")
)

@Composable
fun GeneralSettingsScreen(
    viewModel: GeneralSettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DaemonModeCard(
                checked = state.isDaemonEnabled,
                hasActiveAiProvider = state.hasActiveAiProvider,
                onCheckedChange = viewModel::onToggleDaemon
            )
        }

        item {
            DynamicUiCard(
                checked = state.isDynamicUiEnabled,
                onCheckedChange = viewModel::onToggleDynamicUi
            )
        }

        item {
            ThemeCard(
                selected = state.themeMode,
                onSelected = viewModel::onChangeThemeMode
            )
        }

        item {
            ExportImportCard(
                viewModel = viewModel,
                lastMessage = state.importMessage,
                isSuccess = state.isImportSuccess
            )
        }

        item {
            BottomInfo()
        }
    }
}

// ---------------------------------------------------------------------------
// Cards
// ---------------------------------------------------------------------------

@Composable
private fun DaemonModeCard(
    checked: Boolean,
    hasActiveAiProvider: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var permissionDeniedVisible by remember { mutableStateOf(false) }

    // POST_NOTIFICATIONS launcher (required on Android 13+ to show the
    // ongoing "Heartbeat active" notification — without it the foreground
    // service silently fails to post and the user sees nothing happen).
    val notificationPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onCheckedChange(true)
        } else {
            permissionDeniedVisible = true
        }
    }

    GlassCard {
        ToggleRow(
            title = "Daemon Mode",
            description = "Keep Mr. Robot running in the background so scheduled tasks " +
                "execute even when the app is not in the foreground.",
            checked = checked,
            onCheckedChange = { wantsOn ->
                permissionDeniedVisible = false
                if (!wantsOn) {
                    onCheckedChange(false)
                    return@ToggleRow
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        onCheckedChange(true)
                    } else {
                        notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    onCheckedChange(true)
                }
            }
        )

        // Status hint — helps the user understand what the toggle just did.
        if (checked) {
            Spacer(Modifier.height(10.dp))
            DaemonStatusHint(
                hasActiveAiProvider = hasActiveAiProvider
            )
        }

        if (permissionDeniedVisible) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Notifications permission was denied. Daemon Mode needs it to post " +
                    "the persistent heartbeat notification that keeps the service alive. " +
                    "Enable it in system settings, then toggle Daemon Mode again.",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun DaemonStatusHint(
    hasActiveAiProvider: Boolean
) {
    if (hasActiveAiProvider) {
        Text(
            text = "Daemon active — heartbeat self-checks will run on schedule.",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp
        )
    } else {
        Text(
            text = "Daemon active, but no AI provider is configured. Add a key under the " +
                "AI tab so heartbeat ticks have a model to call.",
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun DynamicUiCard(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GlassCard {
        ToggleRow(
            title = "Dynamic UI",
            description = "Allow the AI to create interactive UI elements like buttons, " +
                "forms and cards inline in responses. Change applies to new conversations.",
            checked = checked,
            onCheckedChange = onCheckedChange
        )

        if (checked) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Active — assistant replies may include tappable suggestion chips.",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ThemeCard(
    selected: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit
) {
    GlassCard {
        Title("Theme")
        Spacer(Modifier.height(6.dp))
        Subtitle("Choose between system default, light, dark, or pure-black OLED.")
        Spacer(Modifier.height(14.dp))
        ThemeDropdown(selected = selected, onSelected = onSelected)
    }
}

@Composable
private fun ExportImportCard(
    viewModel: GeneralSettingsViewModel,
    lastMessage: String,
    isSuccess: Boolean
) {
    GlassCard {
        Title("Export / Import")
        Spacer(Modifier.height(6.dp))
        Subtitle(
            "The exported file contains sensitive data such as API keys and passwords."
        )
        Spacer(Modifier.height(14.dp))

        ExportImportButtons(viewModel = viewModel)

        if (lastMessage.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = lastMessage,
                color = if (isSuccess) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                fontSize = 13.sp
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Reusable controls
// ---------------------------------------------------------------------------

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Title(title)
            Spacer(Modifier.height(6.dp))
            Subtitle(description)
        }
        Spacer(Modifier.padding(horizontal = 6.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ThemeDropdown(
    selected: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val current = THEME_OPTIONS.firstOrNull { it.mode == selected }
        ?: THEME_OPTIONS.first()

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = current.label,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "▾",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            THEME_OPTIONS.forEach { option ->
                val isSelected = option.mode == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (isSelected) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(option.mode)
                    }
                )
            }
        }
    }
}

@Composable
private fun ExportImportButtons(viewModel: GeneralSettingsViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = runCatching {
                val json = viewModel.buildExportJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(json.toByteArray())
                    } ?: error("Could not open output stream")
                }
            }
            result.fold(
                onSuccess = { viewModel.onExportSucceeded() },
                onFailure = { viewModel.onExportFailed(it.message ?: "Unknown error") }
            )
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingImportUri = uri
    }

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = {
                val timestamp = SimpleDateFormat(
                    "yyyyMMdd-HHmmss",
                    Locale.US
                ).format(Date())
                createDocumentLauncher.launch("mr-robot-settings-$timestamp.json")
            },
            modifier = Modifier.weight(1f)
        ) {
            Text("Export")
        }

        OutlinedButton(
            onClick = {
                openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
            },
            modifier = Modifier.weight(1f)
        ) {
            Text("Import")
        }
    }

    pendingImportUri?.let { uri ->
        ImportConfirmDialog(
            onConfirm = { replace ->
                viewModel.onImportFromUri(uri, replace)
                pendingImportUri = null
            },
            onDismiss = { pendingImportUri = null }
        )
    }
}

@Composable
private fun ImportConfirmDialog(
    onConfirm: (replace: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var replace by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import settings") },
        text = {
            Column {
                Text(
                    text = "Importing will overwrite your current settings, including " +
                        "API keys, theme, and daemon configuration.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { replace = !replace },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Replace all",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (replace) {
                                "Existing keys will be cleared before import."
                            } else {
                                "Existing keys are preserved. Imported keys overwrite."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = replace,
                        onCheckedChange = { replace = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(replace) }) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ---------------------------------------------------------------------------
// Footer
// ---------------------------------------------------------------------------

@Composable
private fun BottomInfo() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val versionName = remember(context) {
        runCatching {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "AI makes mistakes, double check and don't share sensitive information.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(Modifier.height(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Version: $versionName",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            Text(
                text = "·",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )

            Text(
                text = "Documentation",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable {
                    runCatching {
                        uriHandler.openUri(
                            "https://github.com/Deadendev/Mr-Robot-AI-Workspace"
                        )
                    }
                }
            )
        }
    }
}
