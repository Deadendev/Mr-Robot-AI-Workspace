package com.mrrobot.aiworkspace.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrrobot.aiworkspace.R
import com.mrrobot.aiworkspace.sandbox.SandboxState
import com.mrrobot.aiworkspace.sandbox.TerminalLine
import com.mrrobot.aiworkspace.viewmodel.PackageEntry
import com.mrrobot.aiworkspace.viewmodel.SandboxFileEntry
import com.mrrobot.aiworkspace.viewmodel.SandboxViewModel
import kotlinx.coroutines.launch

private enum class SandboxTab { Terminal, Files, Packages }

private val TerminalDarkBg = Color(0xFF0D1117)
private val TerminalGreen = Color(0xFF4ADE80)
private val TerminalCyan = Color(0xFF22D3EE)
private val TerminalRed = Color(0xFFFF6B6B)

@Composable
fun SandboxScreen(viewModel: SandboxViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(SandboxTab.Terminal) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        when (state.sandboxState) {
            is SandboxState.NotInstalled,
            is SandboxState.Downloading,
            is SandboxState.Extracting,
            is SandboxState.Installing,
            is SandboxState.Error -> {
                SetupCard(
                    sandboxState = state.sandboxState,
                    diskUsageMB = state.diskUsageMB,
                    onSetup = { viewModel.setupSandbox() },
                    onCancel = { viewModel.cancelSetup() },
                    onUninstall = { viewModel.uninstallSandbox() },
                    onInstallPackages = { viewModel.installBasicPackages() }
                )
            }

            is SandboxState.Ready -> {
                // Tab selector
                TabSelector(
                    selected = selectedTab,
                    onSelect = { selectedTab = it }
                )

                Spacer(Modifier.height(8.dp))

                // Tab content
                when (selectedTab) {
                    SandboxTab.Terminal -> TerminalTab(viewModel)
                    SandboxTab.Files -> FilesTab(viewModel)
                    SandboxTab.Packages -> PackagesTab(viewModel)
                }
            }
        }
    }
}

/* ================================================================
 *  Setup Card (not-installed / downloading / error states)
 * ================================================================ */

@Composable
private fun SetupCard(
    sandboxState: SandboxState,
    diskUsageMB: Long,
    onSetup: () -> Unit,
    onCancel: () -> Unit,
    onUninstall: () -> Unit,
    onInstallPackages: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Alpine Linux",
                color = scheme.onSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            if (diskUsageMB > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$diskUsageMB MB",
                    color = scheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            when (sandboxState) {
                is SandboxState.NotInstalled -> {
                    Text(
                        text = "Install a real Linux environment on your device. " +
                            "Run commands, manage files, and install packages — all sandboxed.",
                        color = scheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onSetup, modifier = Modifier.fillMaxWidth()) {
                        Text("Install Linux Sandbox")
                    }
                }

                is SandboxState.Downloading -> {
                    Text("Downloading Alpine Linux...", color = scheme.onSurface, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { sandboxState.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${(sandboxState.progress * 100).toInt()}%",
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }

                is SandboxState.Extracting -> {
                    Text("Extracting rootfs...", color = scheme.onSurface, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is SandboxState.Installing -> {
                    Text(sandboxState.detail.ifBlank { "Installing..." }, color = scheme.onSurface, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                        Text("Cancel")
                    }
                }

                is SandboxState.Error -> {
                    Text(
                        text = sandboxState.message,
                        color = scheme.error,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = onSetup, modifier = Modifier.weight(1f)) {
                            Text("Retry")
                        }
                        OutlinedButton(onClick = onUninstall, modifier = Modifier.weight(1f)) {
                            Text("Uninstall")
                        }
                    }
                }

                is SandboxState.Ready -> { /* handled above */ }
            }
        }
    }
}

/* ================================================================
 *  Tab Selector
 * ================================================================ */

@Composable
private fun TabSelector(selected: SandboxTab, onSelect: (SandboxTab) -> Unit) {
    val scheme = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SandboxTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelect(tab) },
                shape = RoundedCornerShape(50),
                color = if (isSelected) scheme.primary.copy(alpha = 0.2f) else Color.Transparent
            ) {
                Text(
                    text = tab.name,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/* ================================================================
 *  Terminal Tab
 * ================================================================ */

@Composable
private fun TerminalTab(viewModel: SandboxViewModel) {
    val state by viewModel.uiState.collectAsState()
    val transcript = viewModel.transcript
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(transcript.lastIndex) }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Session / Temporary chips (simplified: just one session)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
            ) {
                Text(
                    text = "Session",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.weight(1f))

            TextButton(onClick = { viewModel.clearTerminal() }) {
                Text("Clear", fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(6.dp))

        // Terminal output
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = TerminalDarkBg
        ) {
            if (transcript.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    Text(
                        text = "The AI uses this environment to run commands.\nYou can also run commands manually below.",
                        color = Color(0xFF6B7280),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        count = transcript.size,
                        key = { it }
                    ) { index ->
                        val line = transcript[index]
                        TerminalLineRow(line)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Command input
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "$",
                color = TerminalCyan,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )

            OutlinedTextField(
                value = state.commandInput,
                onValueChange = { viewModel.updateCommandInput(it) },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("enter command…", color = Color(0xFF6B7280), fontSize = 14.sp)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            IconButton(
                onClick = { viewModel.runCommand() },
                enabled = !state.isRunningCommand && state.commandInput.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (state.isRunningCommand) Color.Gray
                        else MaterialTheme.colorScheme.primary
                    )
            ) {
                if (state.isRunningCommand) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lucide_send),
                        contentDescription = "Run",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalLineRow(line: TerminalLine) {
    val (color, prefix) = when (line) {
        is TerminalLine.Command -> TerminalCyan to "$ "
        is TerminalLine.Output -> Color(0xFFD1D5DB) to ""
        is TerminalLine.Error -> TerminalRed to ""
    }

    Text(
        text = prefix + line.text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 17.sp
    )
}

/* ================================================================
 *  Files Tab
 * ================================================================ */

@Composable
private fun FilesTab(viewModel: SandboxViewModel) {
    val state by viewModel.uiState.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        // Breadcrumb
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = scheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.currentPath != "/") {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lucide_arrow_left),
                        contentDescription = "Up",
                        tint = scheme.primary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { viewModel.navigateUp() }
                    )
                    Spacer(Modifier.width(8.dp))
                }

                Text(
                    text = state.currentPath,
                    color = scheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.isLoadingFiles) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Empty directory",
                    color = scheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = state.files,
                    key = { it.path }
                ) { file ->
                    FileRow(
                        file = file,
                        onClick = {
                            if (file.isDirectory) viewModel.navigateTo(file.path)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(file: SandboxFileEntry, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = file.isDirectory) { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(
                    id = if (file.isDirectory) R.drawable.ic_lucide_folder
                    else R.drawable.ic_lucide_file
                ),
                contentDescription = null,
                tint = if (file.isDirectory) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = scheme.onSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!file.isDirectory && file.size > 0) {
                    Text(
                        text = formatFileSize(file.size),
                        color = scheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }

            if (file.isDirectory) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lucide_arrow_left),
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                    // Rotated 180 degrees to point right — simple workaround
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}

/* ================================================================
 *  Packages Tab
 * ================================================================ */

@Composable
private fun PackagesTab(viewModel: SandboxViewModel) {
    val state by viewModel.uiState.collectAsState()
    val scheme = MaterialTheme.colorScheme

    Column(modifier = Modifier.fillMaxSize()) {
        // Search
        OutlinedTextField(
            value = state.packageSearchQuery,
            onValueChange = { viewModel.updatePackageSearch(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search packages") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lucide_sparkles),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )

        Spacer(Modifier.height(8.dp))

        // Upgrade button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { viewModel.upgradePackages() },
                enabled = !state.isUpgrading
            ) {
                if (state.isUpgrading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text("Upgrade packages")
            }
        }

        // Package list
        if (state.isLoadingPackages && state.packages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (state.packages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (state.packageSearchQuery.isNotBlank()) "No packages found"
                    else "No packages installed",
                    color = scheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = state.packages,
                    key = { "${it.name}@${it.version}" }
                ) { pkg ->
                    PackageRow(
                        entry = pkg,
                        isSearchResult = state.packageSearchQuery.isNotBlank(),
                        onInstall = { viewModel.installPackage(pkg.name) },
                        onUninstall = { viewModel.uninstallPackage(pkg.name) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PackageRow(
    entry: PackageEntry,
    isSearchResult: Boolean,
    onInstall: () -> Unit,
    onUninstall: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_lucide_cpu),
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = entry.name,
                        color = scheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (entry.version.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = entry.version,
                            color = scheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
                entry.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc,
                        color = scheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            TextButton(
                onClick = if (isSearchResult) onInstall else onUninstall,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isSearchResult) scheme.primary else scheme.error
                )
            ) {
                Text(
                    text = if (isSearchResult) "Install" else "Uninstall",
                    fontSize = 12.sp
                )
            }
        }
    }
}
