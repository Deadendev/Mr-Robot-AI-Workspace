package com.mrrobot.aiworkspace.viewmodel

import android.app.Application
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrrobot.aiworkspace.sandbox.SandboxManager
import com.mrrobot.aiworkspace.sandbox.SandboxShell
import com.mrrobot.aiworkspace.sandbox.SandboxState
import com.mrrobot.aiworkspace.sandbox.TerminalLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/* ================================================================
 *  Data classes for UI state
 * ================================================================ */

data class SandboxFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L
)

data class PackageEntry(
    val name: String,
    val version: String,
    val description: String? = null
)

data class SandboxUiState(
    val sandboxState: SandboxState = SandboxState.NotInstalled,
    val diskUsageMB: Long = 0L,
    val commandInput: String = "",
    val isRunningCommand: Boolean = false,

    // Files tab
    val currentPath: String = "/root",
    val files: List<SandboxFileEntry> = emptyList(),
    val isLoadingFiles: Boolean = false,
    val openedFileContent: String? = null,
    val openedFileName: String? = null,
    val openedFilePath: String? = null,
    val isLoadingFileContent: Boolean = false,
    val fileOperationError: String? = null,

    // Packages tab
    val packages: List<PackageEntry> = emptyList(),
    val isLoadingPackages: Boolean = false,
    val packageSearchQuery: String = "",
    val isUpgrading: Boolean = false
)

/* ================================================================
 *  ViewModel
 * ================================================================ */

class SandboxViewModel(application: Application) : AndroidViewModel(application) {

    private val manager = SandboxManager.getInstance(application.applicationContext)
    private var shell: SandboxShell? = null

    private val _uiState = MutableStateFlow(SandboxUiState())
    val uiState: StateFlow<SandboxUiState> = _uiState.asStateFlow()

    /** Live terminal transcript — observed directly by the Terminal tab. */
    val transcript: SnapshotStateList<TerminalLine>
        get() = getOrCreateShell().transcript

    init {
        viewModelScope.launch {
            manager.state.collect { state ->
                _uiState.value = _uiState.value.copy(
                    sandboxState = state,
                    diskUsageMB = if (state is SandboxState.Ready) manager.getDiskUsageMB() else 0L
                )
                if (state is SandboxState.Ready) {
                    loadFiles()
                    loadInstalledPackages()
                }
            }
        }
    }

    /* ─── Setup / lifecycle ─── */

    fun setupSandbox() = manager.setup()
    fun cancelSetup() = manager.cancel()
    fun uninstallSandbox() = manager.uninstall()
    fun installBasicPackages() = manager.installBasicPackages()

    /* ─── Terminal ─── */

    fun updateCommandInput(value: String) {
        _uiState.value = _uiState.value.copy(commandInput = value)
    }

    fun runCommand() {
        val cmd = _uiState.value.commandInput.trim()
        if (cmd.isBlank() || _uiState.value.isRunningCommand) return
        _uiState.value = _uiState.value.copy(commandInput = "", isRunningCommand = true)

        viewModelScope.launch(Dispatchers.IO) {
            val sh = getOrCreateShell()
            sh.run(command = cmd, timeoutSeconds = 60)
            _uiState.value = _uiState.value.copy(isRunningCommand = false)
            // Refresh files if user cd'd or modified something
            loadFiles()
        }
    }

    fun clearTerminal() {
        shell?.clearTranscript()
    }

    /* ─── Files ─── */

    fun navigateTo(path: String) {
        _uiState.value = _uiState.value.copy(currentPath = path)
        loadFiles()
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath
        if (current == "/") return
        val parent = File(current).parent ?: "/"
        navigateTo(parent)
    }

    fun createFile(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val currentPath = _uiState.value.currentPath
        val fullPath = if (currentPath == "/") "/$trimmed" else "$currentPath/$trimmed"

        viewModelScope.launch(Dispatchers.IO) {
            val executor = manager.createProotExecutor()
            val result = executor.execute("touch '$fullPath'", timeoutSeconds = 10)
            val ok = result["success"] as? Boolean ?: false
            if (!ok) {
                val err = (result["stderr"] as? String).orEmpty().ifBlank {
                    (result["error"] as? String).orEmpty()
                }
                _uiState.value = _uiState.value.copy(
                    fileOperationError = "Failed to create file: ${err.take(100)}"
                )
            }
            loadFiles()
        }
    }

    fun createFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val currentPath = _uiState.value.currentPath
        val fullPath = if (currentPath == "/") "/$trimmed" else "$currentPath/$trimmed"

        viewModelScope.launch(Dispatchers.IO) {
            val executor = manager.createProotExecutor()
            val result = executor.execute("mkdir -p '$fullPath'", timeoutSeconds = 10)
            val ok = result["success"] as? Boolean ?: false
            if (!ok) {
                val err = (result["stderr"] as? String).orEmpty().ifBlank {
                    (result["error"] as? String).orEmpty()
                }
                _uiState.value = _uiState.value.copy(
                    fileOperationError = "Failed to create folder: ${err.take(100)}"
                )
            }
            loadFiles()
        }
    }

    fun openFile(file: SandboxFileEntry) {
        if (file.isDirectory) {
            navigateTo(file.path)
            return
        }
        _uiState.value = _uiState.value.copy(
            isLoadingFileContent = true,
            openedFileName = file.name,
            openedFilePath = file.path,
            openedFileContent = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            val executor = manager.createProotExecutor()
            val result = executor.execute("head -c 32000 '${file.path}'", timeoutSeconds = 15)
            val ok = result["success"] as? Boolean ?: false
            val content = if (ok) {
                (result["stdout"] as? String).orEmpty()
            } else {
                "[Could not read file: ${(result["stderr"] as? String).orEmpty().take(200)}]"
            }
            _uiState.value = _uiState.value.copy(
                openedFileContent = content,
                isLoadingFileContent = false
            )
        }
    }

    fun closeFileViewer() {
        _uiState.value = _uiState.value.copy(
            openedFileContent = null,
            openedFileName = null,
            openedFilePath = null
        )
    }

    fun saveFileContent(content: String) {
        val path = _uiState.value.openedFilePath ?: return

        viewModelScope.launch(Dispatchers.IO) {
            // Stage content via host tmp dir (same approach as SandboxToolHost)
            val tmpHost = File(manager.tmpPath).apply { mkdirs() }
            val nonce = System.currentTimeMillis().toString(36)
            val staged = File(tmpHost, ".mr_edit_$nonce")

            runCatching { staged.writeText(content) }.onFailure {
                _uiState.value = _uiState.value.copy(
                    fileOperationError = "Failed to stage write: ${it.message}"
                )
                return@launch
            }

            val executor = manager.createProotExecutor()
            val result = executor.execute(
                "cp /tmp/.mr_edit_$nonce '$path' && rm -f /tmp/.mr_edit_$nonce",
                timeoutSeconds = 15
            )
            runCatching { staged.delete() }

            val ok = result["success"] as? Boolean ?: false
            if (!ok) {
                val err = (result["stderr"] as? String).orEmpty().ifBlank {
                    (result["error"] as? String).orEmpty()
                }
                _uiState.value = _uiState.value.copy(
                    fileOperationError = "Failed to save: ${err.take(100)}"
                )
            } else {
                // Refresh content
                _uiState.value = _uiState.value.copy(openedFileContent = content)
            }
            loadFiles()
        }
    }

    fun renameFile(file: SandboxFileEntry, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank() || trimmed == file.name) return

        val parentDir = File(file.path).parent ?: _uiState.value.currentPath
        val newPath = if (parentDir == "/") "/$trimmed" else "$parentDir/$trimmed"

        viewModelScope.launch(Dispatchers.IO) {
            val executor = manager.createProotExecutor()
            val result = executor.execute("mv '${file.path}' '$newPath'", timeoutSeconds = 10)
            val ok = result["success"] as? Boolean ?: false
            if (!ok) {
                val err = (result["stderr"] as? String).orEmpty().ifBlank {
                    (result["error"] as? String).orEmpty()
                }
                _uiState.value = _uiState.value.copy(
                    fileOperationError = "Rename failed: ${err.take(100)}"
                )
            }
            loadFiles()
        }
    }

    fun deleteFile(file: SandboxFileEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val executor = manager.createProotExecutor()
            val result = executor.execute("rm -rf '${file.path}'", timeoutSeconds = 15)
            val ok = result["success"] as? Boolean ?: false
            if (!ok) {
                val err = (result["stderr"] as? String).orEmpty().ifBlank {
                    (result["error"] as? String).orEmpty()
                }
                _uiState.value = _uiState.value.copy(
                    fileOperationError = "Delete failed: ${err.take(100)}"
                )
            }
            loadFiles()
        }
    }

    fun clearFileError() {
        _uiState.value = _uiState.value.copy(fileOperationError = null)
    }

    private fun loadFiles() {
        if (_uiState.value.sandboxState !is SandboxState.Ready) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingFiles = true)
            val sandboxPath = _uiState.value.currentPath
            val executor = manager.createProotExecutor()
            val result = executor.execute("ls -la --color=never $sandboxPath 2>/dev/null || ls -la $sandboxPath", timeoutSeconds = 10)
            val stdout = result["stdout"] as? String ?: ""

            val entries = parseLsOutput(stdout, sandboxPath)
            _uiState.value = _uiState.value.copy(files = entries, isLoadingFiles = false)
        }
    }

    private fun parseLsOutput(output: String, currentPath: String): List<SandboxFileEntry> {
        val entries = mutableListOf<SandboxFileEntry>()
        output.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("total")) return@forEach
            // Parse `ls -la` output: permissions links owner group size month day time name
            val parts = trimmed.split(Regex("\\s+"), limit = 9)
            if (parts.size < 9) return@forEach
            val perms = parts[0]
            val size = parts[4].toLongOrNull() ?: 0L
            val name = parts[8]
            if (name == "." || name == "..") return@forEach

            val isDir = perms.startsWith("d") || perms.startsWith("l")
            val fullPath = if (currentPath == "/") "/$name" else "$currentPath/$name"
            entries.add(SandboxFileEntry(name = name, path = fullPath, isDirectory = isDir, size = size))
        }
        return entries.sortedWith(compareByDescending<SandboxFileEntry> { it.isDirectory }.thenBy { it.name })
    }

    /* ─── Packages ─── */

    fun updatePackageSearch(query: String) {
        _uiState.value = _uiState.value.copy(packageSearchQuery = query)
        if (query.isBlank()) {
            loadInstalledPackages()
        } else {
            searchPackages(query)
        }
    }

    fun upgradePackages() {
        if (_uiState.value.isUpgrading) return
        _uiState.value = _uiState.value.copy(isUpgrading = true)
        viewModelScope.launch(Dispatchers.IO) {
            val executor = manager.createProotExecutor()
            executor.execute("apk upgrade --no-cache", timeoutSeconds = 120)
            _uiState.value = _uiState.value.copy(isUpgrading = false)
            loadInstalledPackages()
        }
    }

    fun installPackage(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val executor = manager.createProotExecutor()
            executor.execute("apk add --no-cache $name", timeoutSeconds = 120)
            loadInstalledPackages()
        }
    }

    fun uninstallPackage(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val executor = manager.createProotExecutor()
            executor.execute("apk del $name", timeoutSeconds = 60)
            loadInstalledPackages()
        }
    }

    private fun loadInstalledPackages() {
        if (_uiState.value.sandboxState !is SandboxState.Ready) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingPackages = true)
            val executor = manager.createProotExecutor()
            val result = executor.execute("apk list --installed 2>/dev/null", timeoutSeconds = 30)
            val stdout = result["stdout"] as? String ?: ""
            val pkgs = parseApkList(stdout)
            _uiState.value = _uiState.value.copy(packages = pkgs, isLoadingPackages = false)
        }
    }

    private fun searchPackages(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoadingPackages = true)
            val executor = manager.createProotExecutor()
            val result = executor.execute("apk search -v $query 2>/dev/null", timeoutSeconds = 30)
            val stdout = result["stdout"] as? String ?: ""
            val pkgs = parseApkSearch(stdout)
            _uiState.value = _uiState.value.copy(packages = pkgs, isLoadingPackages = false)
        }
    }

    private fun parseApkList(output: String): List<PackageEntry> {
        // Format: name-version {arch} {flags} [installed]
        return output.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            val dashIdx = trimmed.indexOfFirst { it == ' ' }
            val nameVersion = if (dashIdx > 0) trimmed.substring(0, dashIdx) else trimmed
            val lastDash = nameVersion.lastIndexOf('-')
            if (lastDash <= 0) return@mapNotNull null
            val name = nameVersion.substring(0, lastDash)
            val version = nameVersion.substring(lastDash + 1)
            PackageEntry(name = name, version = version)
        }.sortedBy { it.name }
    }

    private fun parseApkSearch(output: String): List<PackageEntry> {
        // Format: name-version - description
        return output.lines().mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            val descSep = trimmed.indexOf(" - ")
            val nameVersion = if (descSep > 0) trimmed.substring(0, descSep) else trimmed
            val description = if (descSep > 0) trimmed.substring(descSep + 3) else null
            val lastDash = nameVersion.lastIndexOf('-')
            if (lastDash <= 0) return@mapNotNull null
            val name = nameVersion.substring(0, lastDash)
            val version = nameVersion.substring(lastDash + 1)
            PackageEntry(name = name, version = version, description = description)
        }.sortedBy { it.name }
    }

    private fun getOrCreateShell(): SandboxShell {
        shell?.let { return it }
        val newShell = SandboxShell(manager.createProotExecutor(), manager.tmpPath)
        shell = newShell
        return newShell
    }

    override fun onCleared() {
        super.onCleared()
        shell?.reset()
    }
}
