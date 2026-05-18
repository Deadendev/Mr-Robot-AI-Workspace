package com.mrrobot.aiworkspace.sandbox

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_OUTPUT_LENGTH = 15_000
private const val DEFAULT_TIMEOUT_SECONDS = 30L
private const val MAX_TIMEOUT_SECONDS = 180L

/**
 * Handle to a streaming proot process. Allows cancellation and stdin writes.
 */
class ProotHandle internal constructor(
    private val process: Process,
    private val cancelled: AtomicBoolean,
    private val readerFutures: List<CompletableFuture<Void>>
) {
    fun isCancelled(): Boolean = cancelled.get()

    fun cancel() {
        cancelled.set(true)
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
        process.destroyForcibly()
    }

    fun writeInput(line: String) {
        if (cancelled.get()) return
        runCatching {
            val bytes = (line + "\n").toByteArray()
            process.outputStream.write(bytes)
            process.outputStream.flush()
        }
    }

    fun awaitExit(): Int {
        while (!cancelled.get() && process.isAlive) {
            runCatching { process.waitFor(200, TimeUnit.MILLISECONDS) }
        }
        if (cancelled.get()) return -1
        readerFutures.forEach { runCatching { it.get(500, TimeUnit.MILLISECONDS) } }
        return runCatching { process.exitValue() }.getOrDefault(-1)
    }
}

/**
 * Executes commands inside the proot sandbox. Ported from Kai (Apache-2.0).
 */
class ProotExecutor(
    private val prootPath: String,
    private val libDir: String,
    private val rootfsPath: String,
    private val homePath: String,
    private val tmpPath: String
) {

    fun execute(
        command: String,
        timeoutSeconds: Long = DEFAULT_TIMEOUT_SECONDS,
        workingDir: String = "/root",
        extraEnv: Map<String, String> = emptyMap()
    ): Map<String, Any> {
        val effectiveTimeout = timeoutSeconds.coerceIn(1, MAX_TIMEOUT_SECONDS)
        return try {
            val process = Runtime.getRuntime().exec(
                buildProcessArgs(command, workingDir),
                buildEnvVars(extraEnv),
                File(rootfsPath).parentFile
            )

            val stdoutFuture = CompletableFuture.supplyAsync {
                readBounded(process.inputStream.bufferedReader())
            }
            val stderrFuture = CompletableFuture.supplyAsync {
                readBounded(process.errorStream.bufferedReader())
            }

            val completed = process.waitFor(effectiveTimeout, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return mapOf(
                    "success" to false,
                    "stdout" to smartTruncate(stdoutFuture.get(1, TimeUnit.SECONDS)),
                    "stderr" to smartTruncate(stderrFuture.get(1, TimeUnit.SECONDS)),
                    "exit_code" to -1,
                    "timed_out" to true
                )
            }

            mapOf(
                "success" to (process.exitValue() == 0),
                "stdout" to smartTruncate(stdoutFuture.get()),
                "stderr" to smartTruncate(stderrFuture.get()),
                "exit_code" to process.exitValue(),
                "timed_out" to false
            )
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "error" to (e.message ?: "Failed to execute command in sandbox")
            )
        }
    }

    fun executeStreaming(
        command: String,
        workingDir: String = "/root",
        extraEnv: Map<String, String> = emptyMap(),
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit
    ): ProotHandle {
        val process = Runtime.getRuntime().exec(
            buildProcessArgs(command, workingDir),
            buildEnvVars(extraEnv),
            File(rootfsPath).parentFile
        )
        val cancelled = AtomicBoolean(false)
        val stdoutFuture = CompletableFuture.runAsync {
            streamLines(process.inputStream.bufferedReader(), cancelled, onStdout)
        }
        val stderrFuture = CompletableFuture.runAsync {
            streamLines(process.errorStream.bufferedReader(), cancelled, onStderr)
        }
        return ProotHandle(process, cancelled, listOf(stdoutFuture, stderrFuture))
    }

    private fun buildProcessArgs(command: String, workingDir: String): Array<String> = arrayOf(
        prootPath,
        "--rootfs=$rootfsPath",
        "--bind=/dev",
        "--bind=/proc",
        "--bind=/sys",
        "--bind=$homePath:/root",
        "--bind=$tmpPath:/tmp",
        "-0",
        "-w", workingDir,
        "/bin/sh", "-c", command
    )

    private fun buildEnvVars(extraEnv: Map<String, String>): Array<String> {
        val loaderPath = File(prootPath).parent.orEmpty() + "/libproot-loader.so"
        val baseEnv = arrayOf(
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "LANG=C.UTF-8",
            "LD_LIBRARY_PATH=$libDir",
            "PROOT_TMP_DIR=$tmpPath",
            "PROOT_LOADER=$loaderPath"
        )
        return baseEnv + extraEnv.map { (k, v) -> "$k=$v" }.toTypedArray()
    }

    private fun readBounded(reader: BufferedReader): String {
        val sb = StringBuilder()
        val buf = CharArray(8192)
        try {
            var read: Int
            while (reader.read(buf).also { read = it } != -1) {
                sb.append(buf, 0, read)
                if (sb.length >= MAX_OUTPUT_LENGTH) break
            }
            if (sb.length >= MAX_OUTPUT_LENGTH) {
                while (reader.read(buf) != -1) { /* discard */ }
            }
        } catch (_: IOException) { }
        return sb.toString()
    }

    private fun streamLines(
        reader: BufferedReader,
        cancelled: AtomicBoolean,
        onLine: (String) -> Unit
    ) {
        try {
            while (!cancelled.get()) {
                val line = try { reader.readLine() } catch (e: IOException) {
                    if (cancelled.get()) break else throw e
                } ?: break
                onLine(line)
            }
        } finally {
            runCatching { reader.close() }
        }
    }

    private fun smartTruncate(text: String): String {
        return if (text.length > MAX_OUTPUT_LENGTH) {
            text.take(MAX_OUTPUT_LENGTH / 2) + "\n...[truncated]...\n" + text.takeLast(MAX_OUTPUT_LENGTH / 2)
        } else text
    }
}
