package com.mrrobot.aiworkspace.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import com.mrrobot.aiworkspace.viewmodel.ChatAttachment
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Extracts AI-readable content from any file URI the user attaches.
 *
 * The result is a populated [ChatAttachment] with:
 *  - `extractedText` / `readableText` for text-bearing files (text, code, PDF,
 *    Office docs, HTML, JSON, CSV, etc.)
 *  - `imageDataUrl` for images and video thumbnails (base64 data URL so
 *    vision-capable models can actually see them)
 *  - `description` for media files (duration, resolution, bitrate, etc.)
 *  - `extractionStatus` reflecting what we managed to pull out
 *
 * Bounds:
 *  - text files are capped at [MAX_TEXT_BYTES] characters
 *  - images are JPEG-recompressed to a max [MAX_IMAGE_DIMENSION]px long edge
 *  - videos extract a single thumbnail frame (no full transcript yet)
 *
 * All work runs on [Dispatchers.IO]. Failures degrade gracefully: an
 * unrecognised binary still gets a useful "MIME + size + hex preview"
 * description so the AI knows what's there.
 */
object AttachmentExtractor {

    private const val MAX_TEXT_BYTES = 64_000          // ~16k tokens, plenty
    private const val MAX_IMAGE_DIMENSION = 1280       // long-edge in px
    private const val IMAGE_JPEG_QUALITY = 80
    private const val MAX_HEX_PREVIEW = 512            // bytes to hex-dump

    private var pdfBoxInitialized = false

    /**
     * Read [uri] and return [base] enriched with whatever content we could
     * extract. Always returns a non-null result; on total failure the
     * `extractionStatus` field is populated with the error.
     */
    suspend fun enrich(
        context: Context,
        base: ChatAttachment
    ): ChatAttachment = withContext(Dispatchers.IO) {
        val uri = base.uri ?: return@withContext base.copy(
            extractionStatus = "No URI; nothing to read"
        )
        val mime = (base.mimeType.orEmpty()).ifBlank {
            context.contentResolver.getType(uri).orEmpty()
        }
        val name = base.displayName.ifBlank { base.name }

        try {
            when {
                mime.startsWith("image/") -> extractImage(context, uri, base, mime)
                mime.startsWith("video/") -> extractVideo(context, uri, base, mime)
                mime.startsWith("audio/") -> extractAudio(context, uri, base, mime)
                mime == "application/pdf" -> extractPdf(context, uri, base)
                isOfficeZip(mime, name) -> extractOfficeZip(context, uri, base, mime)
                isTextLike(mime, name) -> extractText(context, uri, base, mime)
                mime == "text/html" || name.endsWith(".html", ignoreCase = true) ->
                    extractHtml(context, uri, base)
                else -> extractBinary(context, uri, base, mime)
            }
        } catch (t: Throwable) {
            base.copy(
                mimeType = if (mime.isBlank()) base.mimeType else mime,
                extractionStatus = "Failed: ${t.message?.take(200) ?: t.javaClass.simpleName}"
            )
        }
    }

    /* ────────────────────────── Text ────────────────────────── */

    private fun extractText(
        context: Context,
        uri: Uri,
        base: ChatAttachment,
        mime: String
    ): ChatAttachment {
        val bytes = readBytes(context, uri, MAX_TEXT_BYTES + 1)
        val truncated = bytes.size > MAX_TEXT_BYTES
        val text = String(
            bytes.take(MAX_TEXT_BYTES).toByteArray(),
            Charsets.UTF_8
        )
        val status = if (truncated) {
            "Read first $MAX_TEXT_BYTES chars (file is larger)"
        } else {
            "Read ${text.length} chars"
        }
        return base.copy(
            mimeType = if (mime.isBlank()) base.mimeType else mime,
            extractedText = text,
            readableText = text,
            extractionStatus = status
        )
    }

    private fun extractHtml(
        context: Context,
        uri: Uri,
        base: ChatAttachment
    ): ChatAttachment {
        val raw = readBytes(context, uri, MAX_TEXT_BYTES + 1)
        val html = String(raw, Charsets.UTF_8)
        val stripped = stripHtml(html).take(MAX_TEXT_BYTES)
        return base.copy(
            mimeType = "text/html",
            extractedText = stripped,
            readableText = stripped,
            extractionStatus = "Stripped HTML to ${stripped.length} chars"
        )
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?</style>"), " ")
            .replace(Regex("(?s)<!--.*?-->"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("&#39;"), "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /* ────────────────────────── Image ────────────────────────── */

    private fun extractImage(
        context: Context,
        uri: Uri,
        base: ChatAttachment,
        mime: String
    ): ChatAttachment {
        val (dataUrl, w, h) = encodeImage(context, uri)
            ?: return base.copy(
                mimeType = mime,
                extractionStatus = "Could not decode image"
            )
        val description = "Image ${w}×${h}, sent to model for vision."
        return base.copy(
            mimeType = mime,
            imageDataUrl = dataUrl,
            description = description,
            extractionStatus = "Encoded ${w}×${h} image for vision"
        )
    }

    /**
     * Decode, downscale, recompress to JPEG, base64-encode and wrap as a
     * data URL. Returns Triple(dataUrl, width, height) or null on failure.
     */
    private fun encodeImage(
        context: Context,
        uri: Uri
    ): Triple<String, Int, Int>? {
        // First pass: read bounds only.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = computeSampleSize(bounds.outWidth, bounds.outHeight)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null

        return try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            Triple("data:image/jpeg;base64,$b64", bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    private fun computeSampleSize(srcW: Int, srcH: Int): Int {
        val long = maxOf(srcW, srcH)
        if (long <= MAX_IMAGE_DIMENSION) return 1
        var sample = 1
        while (long / (sample * 2) >= MAX_IMAGE_DIMENSION) sample *= 2
        return sample
    }

    /* ────────────────────────── Video ────────────────────────── */

    private fun extractVideo(
        context: Context,
        uri: Uri,
        base: ChatAttachment,
        mime: String
    ): ChatAttachment {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val bitrate = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()
            val mimeType = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: mime
            val title = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)

            // Grab a single representative frame (~midpoint).
            val frameTimeUs = if (durationMs > 0) (durationMs * 1000L) / 2 else 0L
            val frame = retriever.getFrameAtTime(
                frameTimeUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            )

            val (dataUrl, fW, fH) = if (frame != null) {
                val resized = downscaleBitmap(frame)
                val baos = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, baos)
                val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                val triple = Triple(
                    "data:image/jpeg;base64,$b64",
                    resized.width,
                    resized.height
                )
                if (resized !== frame) frame.recycle()
                resized.recycle()
                triple
            } else Triple(null as String?, 0, 0)

            val description = buildString {
                append("Video file")
                if (mimeType.isNotBlank()) append(" (").append(mimeType).append(")")
                append(".\n")
                append("Duration: ").append(formatDuration(durationMs)).append("\n")
                if (width > 0 && height > 0) {
                    append("Resolution: ").append(width).append("×").append(height).append("\n")
                }
                bitrate?.let { append("Bitrate: ").append(it / 1000).append(" kbps\n") }
                if (!title.isNullOrBlank()) append("Title: ").append(title).append("\n")
                if (dataUrl != null) {
                    append("Thumbnail: ").append(fW).append("×").append(fH)
                        .append(" frame extracted at midpoint, sent for vision.")
                } else {
                    append("Thumbnail: could not extract a representative frame.")
                }
            }

            return base.copy(
                mimeType = mimeType.ifBlank { mime },
                imageDataUrl = dataUrl,
                description = description,
                readableText = description,
                extractionStatus = if (dataUrl != null) {
                    "Extracted thumbnail + metadata"
                } else {
                    "Extracted metadata (no thumbnail)"
                }
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun downscaleBitmap(src: Bitmap): Bitmap {
        val long = maxOf(src.width, src.height)
        if (long <= MAX_IMAGE_DIMENSION) return src
        val scale = MAX_IMAGE_DIMENSION.toFloat() / long
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    /* ────────────────────────── Audio ────────────────────────── */

    private fun extractAudio(
        context: Context,
        uri: Uri,
        base: ChatAttachment,
        mime: String
    ): ChatAttachment {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val title = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val bitrate = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toIntOrNull()

            val description = buildString {
                append("Audio file (").append(mime).append(").\n")
                append("Duration: ").append(formatDuration(duration)).append("\n")
                bitrate?.let { append("Bitrate: ").append(it / 1000).append(" kbps\n") }
                if (!title.isNullOrBlank()) append("Title: ").append(title).append("\n")
                if (!artist.isNullOrBlank()) append("Artist: ").append(artist).append("\n")
                if (!album.isNullOrBlank()) append("Album: ").append(album).append("\n")
                append("(No transcription performed; tell the user to use a transcription tool.)")
            }
            return base.copy(
                mimeType = mime,
                description = description,
                readableText = description,
                extractionStatus = "Extracted audio metadata"
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    /* ────────────────────────── PDF ────────────────────────── */

    private fun extractPdf(
        context: Context,
        uri: Uri,
        base: ChatAttachment
    ): ChatAttachment {
        ensurePdfBox(context)
        return context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) {
                return base.copy(extractionStatus = "Could not open PDF stream")
            }
            PDDocument.load(input).use { doc ->
                val stripper = PDFTextStripper()
                val rawText = stripper.getText(doc).orEmpty()
                val truncated = rawText.length > MAX_TEXT_BYTES
                val text = rawText.take(MAX_TEXT_BYTES)
                val status = buildString {
                    append("PDF: ").append(doc.numberOfPages).append(" pages, ")
                    append(text.length).append(" chars extracted")
                    if (truncated) append(" (truncated)")
                }
                base.copy(
                    mimeType = "application/pdf",
                    extractedText = text,
                    readableText = text,
                    extractionStatus = status
                )
            }
        }
    }

    private fun ensurePdfBox(context: Context) {
        if (pdfBoxInitialized) return
        synchronized(this) {
            if (!pdfBoxInitialized) {
                PDFBoxResourceLoader.init(context.applicationContext)
                pdfBoxInitialized = true
            }
        }
    }

    /* ─────────────────────── Office docs ─────────────────────── */

    private fun isOfficeZip(mime: String, name: String): Boolean {
        if (mime.contains("officedocument") || mime.contains("opendocument")) return true
        val lower = name.lowercase()
        return lower.endsWith(".docx") ||
            lower.endsWith(".odt") ||
            lower.endsWith(".xlsx") ||
            lower.endsWith(".pptx") ||
            lower.endsWith(".ods") ||
            lower.endsWith(".odp")
    }

    /**
     * Office (OOXML) and OpenDocument files are zip archives containing XML.
     * We unzip in-memory, concatenate the relevant XML entries, and strip
     * tags to get plain text. Not perfect, but good enough for the model.
     */
    private fun extractOfficeZip(
        context: Context,
        uri: Uri,
        base: ChatAttachment,
        mime: String
    ): ChatAttachment {
        val sb = StringBuilder()
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) {
                return base.copy(extractionStatus = "Could not open document stream")
            }
            ZipInputStream(input).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val n = entry.name
                    val isContent = n.endsWith(".xml") && (
                        n.startsWith("word/document") ||
                            n.startsWith("xl/sharedStrings") ||
                            n.startsWith("xl/worksheets/") ||
                            n.startsWith("ppt/slides/slide") ||
                            n == "content.xml" // ODF
                        )
                    if (!isContent) {
                        zis.closeEntry()
                        continue
                    }
                    val xml = zis.readBytes().toString(Charsets.UTF_8)
                    sb.append(stripHtml(xml)).append('\n')
                    zis.closeEntry()
                    if (sb.length > MAX_TEXT_BYTES) break
                }
            }
        }
        val text = sb.toString().take(MAX_TEXT_BYTES).trim()
        val status = if (text.isBlank()) {
            "No readable text found inside document"
        } else {
            "Extracted ${text.length} chars from document"
        }
        return base.copy(
            mimeType = if (mime.isBlank()) base.mimeType else mime,
            extractedText = text.takeIf { it.isNotBlank() },
            readableText = text,
            extractionStatus = status
        )
    }

    /* ────────────────────── Binary fallback ────────────────────── */

    private fun extractBinary(
        context: Context,
        uri: Uri,
        base: ChatAttachment,
        mime: String
    ): ChatAttachment {
        val head = readBytes(context, uri, MAX_HEX_PREVIEW)
        val hex = head.joinToString(" ") {
            "%02x".format(it.toInt() and 0xff)
        }
        val description = buildString {
            append("Binary file (").append(mime.ifBlank { "unknown MIME" }).append(").\n")
            append("Size: ").append(base.sizeBytes).append(" bytes\n")
            append("First ").append(head.size).append(" bytes (hex):\n").append(hex)
        }
        return base.copy(
            mimeType = if (mime.isBlank()) base.mimeType else mime,
            description = description,
            readableText = description,
            extractionStatus = "Binary; sent hex preview only"
        )
    }

    /* ────────────────────────── Utils ────────────────────────── */

    private fun isTextLike(mime: String, name: String): Boolean {
        if (mime.startsWith("text/")) return true
        if (mime in TEXTUAL_APPLICATION_MIMES) return true
        val lower = name.lowercase()
        return TEXT_EXTENSIONS.any { lower.endsWith(it) }
    }

    private val TEXTUAL_APPLICATION_MIMES = setOf(
        "application/json",
        "application/xml",
        "application/x-yaml",
        "application/yaml",
        "application/javascript",
        "application/x-sh",
        "application/x-shellscript",
        "application/x-httpd-php",
        "application/sql",
        "application/x-sql",
        "application/toml",
        "application/x-toml"
    )

    private val TEXT_EXTENSIONS = listOf(
        ".txt", ".md", ".markdown", ".rst",
        ".log", ".csv", ".tsv",
        ".json", ".jsonl", ".ndjson",
        ".xml", ".yaml", ".yml", ".toml", ".ini", ".cfg", ".conf", ".env",
        ".kt", ".kts", ".java", ".py", ".rb", ".go", ".rs", ".swift",
        ".c", ".h", ".cpp", ".hpp", ".cc", ".cs", ".m", ".mm",
        ".js", ".mjs", ".cjs", ".ts", ".tsx", ".jsx",
        ".html", ".htm", ".xhtml", ".css", ".scss", ".sass", ".less",
        ".sh", ".bash", ".zsh", ".fish", ".bat", ".ps1", ".psm1",
        ".sql", ".gradle", ".kts", ".pro", ".lock",
        ".php", ".pl", ".lua", ".vim", ".gitignore", ".dockerignore",
        ".dart", ".scala", ".clj", ".cljs", ".ex", ".exs", ".erl",
        ".gradle", ".properties", ".tf", ".tfvars", ".hcl"
    )

    private fun readBytes(context: Context, uri: Uri, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        context.contentResolver.openInputStream(uri).use { input: InputStream? ->
            if (input == null) return ByteArray(0)
            val buf = ByteArray(8192)
            var total = 0
            while (total < max) {
                val want = minOf(buf.size, max - total)
                val n = input.read(buf, 0, want)
                if (n <= 0) break
                out.write(buf, 0, n)
                total += n
            }
        }
        return out.toByteArray()
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "unknown"
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "%d:%02d:%02d".format(h, m, s)
            else -> "%d:%02d".format(m, s)
        }
    }
}
