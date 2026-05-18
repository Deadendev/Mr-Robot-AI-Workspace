package com.mrrobot.aiworkspace.sandbox

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val MAX_OUTPUT_LENGTH = 15_000
private const val MAX_TRANSCRIPT_LINES = 500
private const val RS = "\u001e"
private const val US = "\u001f"
private const val PID_PROBE_PREFIX = "${RS}KAIBASHPID$US"

/**
 * Persistent interactive bash shell running inside the proot sandbox.
 * Supports multiple sequential commands sharing state (cd, env, etc).
 * Ported from Kai (Apache-2.0).
 */
class SandboxShell(private val executor: ProotExecutor, private val tmpPath: String) {

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var handle: ProotHandle? = null
    @Volatile private var bashPid: Int? = null
    private var watchdog: Job? = null
    private val currentSink = AtomicReference<CommandSink?>(null)

    val transcript: SnapshotStateList<TerminalLine> = mutableStateListOf()

    private class CommandSink(
        val nonce: String,
        val stdoutBuf: StringBuilder = StringBuilder(),
        val stderrBuf: StringBuilder = StringBuilder(),
        val onStdout: ((String) -> Unit)? = null,
        val onStderr: ((String) -> Unit)? = null,
        val done: CompletableDeferred<Result> = CompletableDeferred()
    )

    private data class Result(
        val exitCode: Int,
        val cwd: String,
        val bashPid: Int,
        val shellDied: Boolean = false
    )

    suspend fun run(
        command: String,
        timeoutSeconds: Long = 30,
        onStdout: ((String) -> Unit)? = null,
        onStderr: ((String) -> Unit)? = null
    ): Map<String, Any> = mutex.withLock {
        ensureShell()
        val nonce = randomNonce()
        val sink = CommandSink(nonce = nonce, onStdout = onStdout, onStderr = onStderr)
        currentSink.set(sink)

        appendTranscript(TerminalLine.Command(command))

        val cmdFile = File(tmpPath, ".mr_cmd_$nonce")
        try { cmdFile.writeText(command) } catch (e: Exception) {
            currentSink.set(null)
            return@withLock errorMap("Failed to stage command: ${e.message}")
        }

        val line = ". /tmp/.mr_cmd_$nonce; __mr_st=\$?; rm -f /tmp/.mr_cmd_$nonce; " +
            "printf '\\n\\036%s\\037%d\\037%d\\037%s\\036\\n' '$nonce' \"\$__mr_st\" \"\$\$\" \"\$PWD\" >&2"
        handle?.writeInput(line)

        val result = withTimeoutOrNull(timeoutSeconds.seconds) { sink.done.await() }
        if (result == null) {
            cancelForeground()
            val recovered = withTimeoutOrNull(2.seconds) { sink.done.await() }
            currentSink.set(null)
            if (recovered == null) {
                reset()
                return@withLock timeoutMap(sink)
            }
            return@withLock buildResult(sink, recovered, timedOut = true)
        }
        currentSink.set(null)
        if (result.shellDied) return@withLock buildResult(sink, result, shellDied = true)
        bashPid = result.bashPid
        return@withLock buildResult(sink, result)
    }

    fun writeInput(line: String) { handle?.writeInput(line) }

    fun cancelForeground() {
        val pid = bashPid
        if (pid == null) { reset(); return }
        scope.launch {
            for (signal in listOf("INT", "TERM", "KILL")) {
                runCatching {
                    executor.execute("kids=\$(pgrep -P $pid); [ -n \"\$kids\" ] && kill -$signal \$kids", timeoutSeconds = 5)
                }
                delay(500.milliseconds)
                val done = currentSink.get()?.done?.isCompleted
                if (done == null || done == true) return@launch
            }
            reset()
        }
    }

    fun reset() {
        watchdog?.cancel(); watchdog = null
        handle?.cancel(); handle = null
        bashPid = null
        currentSink.getAndSet(null)?.done?.complete(
            Result(exitCode = -1, cwd = "/root", bashPid = 0, shellDied = true)
        )
    }

    fun clearTranscript() {
        Snapshot.withMutableSnapshot { transcript.clear() }
    }

    private fun ensureShell() {
        if (handle != null) return
        val h = executor.executeStreaming(
            command = "exec bash --noprofile --norc",
            onStdout = { line -> dispatchStdout(line) },
            onStderr = { line -> dispatchStderr(line) }
        )
        handle = h
        h.writeInput("printf '\\n\\036KAIBASHPID\\037%d\\036\\n' \"\$\$\" >&2")
        watchdog = scope.launch {
            h.awaitExit()
            currentSink.getAndSet(null)?.done?.complete(
                Result(exitCode = -1, cwd = "/root", bashPid = bashPid ?: 0, shellDied = true)
            )
            handle = null; bashPid = null
        }
    }

    private fun dispatchStdout(line: String) {
        val sink = currentSink.get() ?: return
        appendBounded(sink.stdoutBuf, line)
        appendTranscript(TerminalLine.Output(line))
        sink.onStdout?.invoke(line)
    }

    private fun dispatchStderr(line: String) {
        if (line.isEmpty()) return
        if (line.startsWith(PID_PROBE_PREFIX) && line.endsWith(RS)) {
            val pidText = line.substring(PID_PROBE_PREFIX.length, line.length - 1)
            pidText.toIntOrNull()?.let { bashPid = it }
            return
        }
        val sink = currentSink.get() ?: return
        if (line.length >= 2 && line.startsWith(RS) && line.endsWith(RS)) {
            val payload = line.substring(1, line.length - 1)
            val parts = payload.split(US)
            if (parts.size == 4 && parts[0] == sink.nonce) {
                val exit = parts[1].toIntOrNull() ?: -1
                val pid = parts[2].toIntOrNull() ?: 0
                val cwd = parts[3]
                sink.done.complete(Result(exitCode = exit, cwd = cwd, bashPid = pid))
                return
            }
        }
        appendBounded(sink.stderrBuf, line)
        appendTranscript(TerminalLine.Error(line))
        sink.onStderr?.invoke(line)
    }

    private fun appendTranscript(line: TerminalLine) {
        Snapshot.withMutableSnapshot {
            transcript.add(line)
            val excess = transcript.size - MAX_TRANSCRIPT_LINES
            if (excess > 0) transcript.subList(0, excess).clear()
        }
    }

    private fun buildResult(sink: CommandSink, result: Result, timedOut: Boolean = false, shellDied: Boolean = false): Map<String, Any> {
        val stderr = if (shellDied || result.shellDied) {
            val tail = sink.stderrBuf.toString()
            if (tail.isEmpty()) "Shell session ended" else "$tail\nShell session ended"
        } else sink.stderrBuf.toString()
        return mapOf(
            "success" to (!timedOut && !shellDied && !result.shellDied && result.exitCode == 0),
            "stdout" to sink.stdoutBuf.toString().take(MAX_OUTPUT_LENGTH),
            "stderr" to stderr.take(MAX_OUTPUT_LENGTH),
            "exit_code" to if (timedOut) -1 else result.exitCode,
            "timed_out" to timedOut,
            "cwd" to result.cwd,
            "shell_died" to (shellDied || result.shellDied)
        )
    }

    private fun timeoutMap(sink: CommandSink): Map<String, Any> = mapOf(
        "success" to false,
        "stdout" to sink.stdoutBuf.toString().take(MAX_OUTPUT_LENGTH),
        "stderr" to (sink.stderrBuf.toString() + "\nCommand timed out").take(MAX_OUTPUT_LENGTH),
        "exit_code" to -1,
        "timed_out" to true,
        "cwd" to "/root",
        "shell_died" to true
    )

    private fun errorMap(stderr: String): Map<String, Any> = mapOf(
        "success" to false, "stdout" to "", "stderr" to stderr,
        "exit_code" to -1, "timed_out" to false, "cwd" to "/root", "shell_died" to false
    )
}

private fun appendBounded(buf: StringBuilder, line: String) {
    if (buf.length >= MAX_OUTPUT_LENGTH) return
    if (buf.isNotEmpty()) buf.append('\n')
    buf.append(line)
}

private fun randomNonce(): String = (0 until 16).map { "0123456789abcdef".random() }.joinToString("")
