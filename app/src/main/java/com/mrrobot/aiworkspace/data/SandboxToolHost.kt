package com.mrrobot.aiworkspace.data

import com.mrrobot.aiworkspace.sandbox.SandboxManager
import com.mrrobot.aiworkspace.sandbox.SandboxState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Bridges the AI's sandbox tool directives to the on-device Linux sandbox.
 *
 * Each operation spawns a one-shot proot process via [SandboxManager].
 * File writes stage content through the host-mounted /tmp dir so that
 * multi-line content with quotes/newlines round-trips intact.
 */
class SandboxToolHost(private val sandboxManager: SandboxManager) {

    data class ToolResult(
        val label: String,
        val ok: Boolean,
        val output: String
    )

    fun isReady(): Boolean = sandboxManager.state.value is SandboxState.Ready

    private fun unavailabilityReason(): String? = when (val s = sandboxManager.state.value) {
        is SandboxState.Ready -> null
        is SandboxState.NotInstalled ->
            "Sandbox is not installed. Tell the user to open the Linux Sandbox screen and tap Install."
        is SandboxState.Downloading ->
            "Sandbox is downloading (${(s.progress * 100).toInt()}%). Try again shortly."
        is SandboxState.Extracting ->
            "Sandbox is extracting. Try again shortly."
        is SandboxState.Installing ->
            "Sandbox is configuring (${s.detail}). Try again shortly."
        is SandboxState.Error ->
            "Sandbox error: ${s.message}. Tell the user to retry from the Linux Sandbox screen."
    }

    suspend fun runShell(command: String, timeoutSeconds: Long = 60): ToolResult {
        val cmd = command.trim()
        if (cmd.isBlank()) return ToolResult("shell: <empty>", false, "Empty command")

        unavailabilityReason()?.let {
            return ToolResult("shell: $cmd", false, it)
        }

        // ProotExecutor.execute() ultimately calls Process.waitFor(), which
        // is blocking. If the suspend caller is on the Main dispatcher (the
        // default for viewModelScope.launch), the UI thread is parked for
        // the entire command duration — up to `timeoutSeconds`. Force IO so
        // long shell commands never freeze the chat screen.
        val result = withContext(Dispatchers.IO) {
            sandboxManager.createProotExecutor()
                .execute(command = cmd, timeoutSeconds = timeoutSeconds)
        }

        val ok = result["success"] as? Boolean ?: false
        val timedOut = result["timed_out"] as? Boolean ?: false
        val exit = result["exit_code"] as? Int ?: -1
        val stdout = (result["stdout"] as? String).orEmpty()
        val stderr = (result["stderr"] as? String).orEmpty()
        val errorMsg = (result["error"] as? String).orEmpty()

        val body = buildString {
            append("$ ").append(cmd).append('\n')
            if (stdout.isNotEmpty()) append(stdout.trimEnd()).append('\n')
            if (stderr.isNotEmpty()) append("[stderr]\n").append(stderr.trimEnd()).append('\n')
            if (errorMsg.isNotEmpty()) append("[error] ").append(errorMsg).append('\n')
            if (!ok) {
                if (timedOut) append("[timed out after ${timeoutSeconds}s]")
                else append("[exit $exit]")
            }
        }.trimEnd()

        return ToolResult("shell: ${cmd.take(60)}", ok, body)
    }

    suspend fun writeFile(path: String, content: String): ToolResult {
        val target = path.trim()
        if (target.isBlank()) return ToolResult("write: <empty>", false, "Empty path")

        val quoted = singleQuote(target)
            ?: return ToolResult("write: $target", false, "Path contains unsafe characters (single quote or NUL).")

        unavailabilityReason()?.let {
            return ToolResult("write: $target", false, it)
        }

        val tmpHost = File(sandboxManager.tmpPath).apply { mkdirs() }
        val nonce = UUID.randomUUID().toString().take(12).replace("-", "")
        val staged = File(tmpHost, ".mr_ai_write_$nonce")

        runCatching { staged.writeText(content) }.onFailure {
            return ToolResult("write: $target", false, "Failed to stage: ${it.message}")
        }

        val bytes = content.toByteArray(Charsets.UTF_8).size
        val cmd = "set -e; mkdir -p \"\$(dirname $quoted)\" && " +
            "cp /tmp/.mr_ai_write_$nonce $quoted && " +
            "rm -f /tmp/.mr_ai_write_$nonce && " +
            "echo wrote $bytes bytes to $target"

        val r = runShell(cmd, timeoutSeconds = 30)
        runCatching { staged.delete() }
        return r.copy(label = "write: $target")
    }

    suspend fun readFile(path: String, maxBytes: Int = DEFAULT_READ_BYTES): ToolResult {
        val target = path.trim()
        if (target.isBlank()) return ToolResult("read: <empty>", false, "Empty path")

        val quoted = singleQuote(target)
            ?: return ToolResult("read: $target", false, "Path contains unsafe characters.")

        unavailabilityReason()?.let {
            return ToolResult("read: $target", false, it)
        }

        val cmd = "if [ -f $quoted ]; then " +
            "head -c $maxBytes $quoted; echo; " +
            "echo \"[file: $target, size: \$(wc -c < $quoted) bytes]\"; " +
            "else echo \"[error] file not found: $target\"; exit 1; fi"

        return runShell(cmd, timeoutSeconds = 30).copy(label = "read: $target")
    }

    suspend fun deleteFile(path: String): ToolResult {
        val target = path.trim()
        if (target.isBlank()) return ToolResult("delete: <empty>", false, "Empty path")

        val quoted = singleQuote(target)
            ?: return ToolResult("delete: $target", false, "Path contains unsafe characters.")

        unavailabilityReason()?.let {
            return ToolResult("delete: $target", false, it)
        }

        val cmd = "if [ -e $quoted ]; then rm -rf $quoted && echo \"deleted: $target\"; " +
            "else echo \"[error] not found: $target\"; exit 1; fi"

        return runShell(cmd, timeoutSeconds = 15).copy(label = "delete: $target")
    }

    private fun singleQuote(arg: String): String? {
        if (arg.contains('\'') || arg.contains('\u0000')) return null
        return "'$arg'"
    }

    companion object {
        const val DEFAULT_READ_BYTES = 8000
    }
}
