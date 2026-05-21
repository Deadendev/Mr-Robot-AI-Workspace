package com.mrrobot.aiworkspace.sandbox

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Manages the Alpine Linux sandbox lifecycle: download, extract, configure,
 * run commands, install packages, and reset.
 *
 * Ported from Kai's LinuxSandboxManager (Apache-2.0), adapted for Mr. Robot
 * (no Ktor, no Koin, singleton via companion).
 */
class SandboxManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var instance: SandboxManager? = null

        fun getInstance(context: Context): SandboxManager {
            return instance ?: synchronized(this) {
                instance ?: SandboxManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val _state = MutableStateFlow<SandboxState>(SandboxState.NotInstalled)
    val state: StateFlow<SandboxState> = _state

    private val sandboxDir: File get() = File(context.filesDir, "linux-sandbox")
    val rootfsPath: String get() = File(sandboxDir, "rootfs").absolutePath
    val homePath: String by lazy {
        val external = context.getExternalFilesDir(null)
        val target = if (external != null) File(external, "sandbox-home") else File(sandboxDir, "home")
        target.mkdirs()
        target.absolutePath
    }
    val tmpPath: String get() = File(sandboxDir, "tmp").also { it.mkdirs() }.absolutePath
    val prootPath: String get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so").absolutePath
    val nativeLibDir: String get() = context.applicationInfo.nativeLibraryDir

    init {
        checkExistingInstallation()
    }

    private fun checkExistingInstallation() {
        val rootfs = File(sandboxDir, "rootfs")
        val proot = File(prootPath)
        if (rootfs.isDirectory && proot.exists() && proot.canExecute()) {
            _state.value = SandboxState.Ready
        }
    }

    private fun getLinuxArch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "armhf"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> "aarch64"
        }
    }

    fun setup() {
        if (currentJob?.isActive == true) return
        currentJob = scope.launch {
            try {
                setupInternal()
            } catch (e: kotlinx.coroutines.CancellationException) {
                checkExistingInstallation()
            } catch (e: Exception) {
                Log.e("SandboxManager", "Setup failed", e)
                _state.value = SandboxState.Error(e.message ?: "Setup failed")
            }
        }
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        File(sandboxDir, "rootfs.tar.gz").delete()
        checkExistingInstallation()
    }

    private suspend fun setupInternal() {
        val arch = getLinuxArch()

        val proot = File(prootPath)
        if (!proot.exists()) {
            throw IllegalStateException("Proot binary not found at $prootPath")
        }

        sandboxDir.mkdirs()
        File(sandboxDir, "tmp").mkdirs()
        // Ensure homePath dir is created
        File(homePath).mkdirs()

        copyLibtalloc()

        val rootfsDir = File(sandboxDir, "rootfs")
        if (!rootfsDir.isDirectory) {
            val tarGzFile = File(sandboxDir, "rootfs.tar.gz")
            try {
                _state.value = SandboxState.Downloading(0f)
                RootfsDownloader.download(arch, tarGzFile) { progress ->
                    _state.value = SandboxState.Downloading(progress)
                }
                _state.value = SandboxState.Extracting
                RootfsDownloader.extractTarGz(tarGzFile, rootfsDir)
            } finally {
                tarGzFile.delete()
            }
        }

        _state.value = SandboxState.Installing("Configuring...")
        RootfsDownloader.makeWritable(rootfsDir)
        RootfsDownloader.writeResolvConf(rootfsDir)

        val executor = createProotExecutor()
        var updated = false
        for (mirror in ALPINE_MIRRORS) {
            coroutineContext.ensureActive()
            RootfsDownloader.writeRepositories(rootfsDir, mirror)
            val result = executor.execute("apk update", timeoutSeconds = 60)
            if (result["success"] as? Boolean == true) {
                updated = true
                break
            }
        }
        if (!updated) {
            throw IllegalStateException("apk update failed on all Alpine mirrors")
        }

        _state.value = SandboxState.Ready
    }

    private fun copyLibtalloc() {
        val tallocTarget = File(sandboxDir, "libtalloc.so.2")
        if (tallocTarget.exists()) return
        val source = File(nativeLibDir, "libtalloc.so")
        if (source.exists()) source.copyTo(tallocTarget, overwrite = true)
    }

    fun createProotExecutor(): ProotExecutor = ProotExecutor(
        prootPath = prootPath,
        libDir = sandboxDir.absolutePath,
        rootfsPath = rootfsPath,
        homePath = homePath,
        tmpPath = tmpPath
    )

    // One persistent bash session for AI-driven shell commands. Lazily
    // created on first access so we don't pay startup cost until the AI
    // actually runs a command. Callers (currently `SandboxToolHost`) get
    // state preservation between commands — `cd`, `export`, sourced
    // scripts, aliases all survive. For one-shot UI ops (the Sandbox
    // screen's file create/edit) `createProotExecutor()` is still the
    // right tool: those don't want or need shared state.
    @Volatile
    private var aiShell: PersistentSandboxShell? = null

    fun aiShell(): PersistentSandboxShell {
        return aiShell ?: synchronized(this) {
            aiShell ?: PersistentSandboxShell(createProotExecutor(), tmpPath).also {
                aiShell = it
            }
        }
    }

    /**
     * Tear down the persistent AI shell. Next call to [aiShell] will
     * lazily restart it. Used by [uninstall] and exposed for UI-driven
     * "reset session" actions.
     */
    fun resetAiShell() {
        aiShell?.reset()
        aiShell = null
    }

    fun installBasicPackages() {
        if (currentJob?.isActive == true) return
        val packages = listOf(
            "bash", "curl", "wget", "git", "jq", "python3", "py3-pip", "nodejs",
            "openssh-client", "nano"
        )
        currentJob = scope.launch {
            try {
                val executor = createProotExecutor()
                for (pkg in packages) {
                    ensureActive()
                    _state.value = SandboxState.Installing("Installing $pkg...")
                    val result = executor.execute("apk add --no-cache $pkg", timeoutSeconds = 120)
                    ensureActive()
                    val success = result["success"] as? Boolean ?: false
                    if (!success) {
                        val stderr = result["stderr"] as? String ?: ""
                        val error = result["error"] as? String ?: ""
                        _state.value = SandboxState.Error(
                            "Failed to install $pkg: ${stderr.ifEmpty { error }.take(200)}"
                        )
                        return@launch
                    }
                }
                _state.value = SandboxState.Ready
            } catch (_: kotlinx.coroutines.CancellationException) {
                _state.value = SandboxState.Ready
            } catch (e: Exception) {
                _state.value = SandboxState.Error("Install failed: ${e.message}")
            }
        }
    }

    fun uninstall() {
        scope.launch {
            // Tear down any live persistent shell first — leaving it
            // running while we delete its rootfs would surface confusing
            // I/O errors on the next AI command.
            resetAiShell()
            sandboxDir.deleteRecursively()
            _state.value = SandboxState.NotInstalled
        }
    }

    fun getDiskUsageMB(): Long {
        if (!sandboxDir.isDirectory) return 0
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(sandboxDir)
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = try { dir.listFiles() } catch (_: Throwable) { null } ?: continue
            for (child in children) {
                try {
                    when {
                        child.isDirectory -> stack.addLast(child)
                        child.isFile -> total += child.length()
                    }
                } catch (_: Throwable) { }
            }
        }
        return total / (1024 * 1024)
    }

    fun areBasicPackagesInstalled(): Boolean {
        if (_state.value !is SandboxState.Ready) return false
        return File(rootfsPath, "usr/bin/python3").exists()
    }
}
