package com.mrrobot.aiworkspace.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders markdown-formatted text with support for:
 * - **bold** and *italic*
 * - `inline code`
 * - ```fenced code blocks``` (with optional language label)
 * - [links](url)
 * - bullet lists (- or *)
 * - numbered lists (1. 2. etc.)
 * - headings (# ## ###)
 *
 * Does NOT attempt full CommonMark compliance — just the subset that
 * AI models actually emit in practice.
 */
@Composable
fun MarkdownText(
    content: String,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val blocks = remember(content) { parseMarkdownBlocks(content) }
    val context = LocalContext.current

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            when (block) {
                is MdBlock.CodeBlock -> {
                    CodeBlockView(
                        code = block.code,
                        language = block.language
                    )
                }

                is MdBlock.Paragraph -> {
                    val annotated = remember(block.text, textColor) {
                        parseInlineMarkdown(block.text, textColor, scheme.primary)
                    }

                    ClickableText(
                        text = annotated,
                        onClick = { offset ->
                            annotated.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { annotation ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                        )
                                    }
                                }
                        }
                    )
                }

                is MdBlock.Heading -> {
                    val fontSize = when (block.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        text = block.text,
                        color = textColor,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        lineHeight = fontSize * 1.3f
                    )
                }

                is MdBlock.ListItem -> {
                    val bullet = if (block.ordered) "${block.index}." else "\u2022"
                    val annotated = remember(block.text, textColor) {
                        parseInlineMarkdown("$bullet ${block.text}", textColor, scheme.primary)
                    }
                    ClickableText(
                        text = annotated,
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = { offset ->
                            annotated.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { annotation ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(annotation.item))
                                        )
                                    }
                                }
                        }
                    )
                }
            }

            if (index < blocks.lastIndex) {
                val spacing = when {
                    block is MdBlock.CodeBlock -> 10.dp
                    block is MdBlock.Heading -> 6.dp
                    else -> 4.dp
                }
                Spacer(Modifier.height(spacing))
            }
        }
    }
}

@Composable
private fun CodeBlockView(code: String, language: String?) {
    val scheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = scheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, scheme.outline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (!language.isNullOrBlank()) {
                Text(
                    text = language,
                    color = scheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Text(
                text = code,
                color = scheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

// ─── Block-level parsing ─────────────────────────────────────────

private sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class CodeBlock(val code: String, val language: String?) : MdBlock()
    data class Heading(val text: String, val level: Int) : MdBlock()
    data class ListItem(val text: String, val ordered: Boolean, val index: Int) : MdBlock()
}

private fun parseMarkdownBlocks(input: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = input.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Fenced code block
        if (line.trimStart().startsWith("```")) {
            val language = line.trimStart().removePrefix("```").trim().takeIf { it.isNotEmpty() }
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // skip closing ```
            blocks.add(MdBlock.CodeBlock(codeLines.joinToString("\n"), language))
            continue
        }

        // Heading
        val headingMatch = Regex("^(#{1,3})\\s+(.+)$").matchEntire(line.trim())
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val text = headingMatch.groupValues[2]
            blocks.add(MdBlock.Heading(text, level))
            i++
            continue
        }

        // Unordered list item
        val ulMatch = Regex("^\\s*[-*]\\s+(.+)$").matchEntire(line)
        if (ulMatch != null) {
            blocks.add(MdBlock.ListItem(ulMatch.groupValues[1], ordered = false, index = 0))
            i++
            continue
        }

        // Ordered list item
        val olMatch = Regex("^\\s*(\\d+)\\.\\s+(.+)$").matchEntire(line)
        if (olMatch != null) {
            val idx = olMatch.groupValues[1].toIntOrNull() ?: 1
            blocks.add(MdBlock.ListItem(olMatch.groupValues[2], ordered = true, index = idx))
            i++
            continue
        }

        // Empty line = paragraph break
        if (line.isBlank()) {
            i++
            continue
        }

        // Regular paragraph — collect consecutive non-special lines
        val paraLines = mutableListOf<String>()
        while (i < lines.size) {
            val l = lines[i]
            if (l.isBlank() ||
                l.trimStart().startsWith("```") ||
                Regex("^#{1,3}\\s+").containsMatchIn(l) ||
                Regex("^\\s*[-*]\\s+").containsMatchIn(l) ||
                Regex("^\\s*\\d+\\.\\s+").containsMatchIn(l)
            ) break
            paraLines.add(l)
            i++
        }
        if (paraLines.isNotEmpty()) {
            blocks.add(MdBlock.Paragraph(paraLines.joinToString("\n")))
        }
    }

    return blocks
}

// ─── Inline parsing (bold, italic, code, links) ──────────────────

private fun parseInlineMarkdown(
    text: String,
    textColor: Color,
    linkColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val defaultStyle = SpanStyle(color = textColor, fontSize = 15.sp)
        withStyle(defaultStyle) {
            var i = 0
            val chars = text.toCharArray()
            val len = chars.size

            while (i < len) {
                // Bold **text** or __text__
                if (i + 1 < len && ((chars[i] == '*' && chars[i + 1] == '*') || (chars[i] == '_' && chars[i + 1] == '_'))) {
                    val delim = "${chars[i]}${chars[i + 1]}"
                    val end = text.indexOf(delim, i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                        continue
                    }
                }

                // Italic *text* or _text_ (single)
                if ((chars[i] == '*' || chars[i] == '_') && (i + 1 >= len || chars[i + 1] != chars[i])) {
                    val delim = chars[i]
                    val end = text.indexOf(delim, i + 1)
                    if (end > i + 1 && (i == 0 || chars[i - 1] != delim)) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                        continue
                    }
                }

                // Inline code `text`
                if (chars[i] == '`') {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                background = linkColor.copy(alpha = 0.1f)
                            )
                        ) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                        continue
                    }
                }

                // Link [text](url)
                if (chars[i] == '[') {
                    val closeBracket = text.indexOf(']', i + 1)
                    if (closeBracket > i + 1 && closeBracket + 1 < len && chars[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen > closeBracket + 2) {
                            val linkText = text.substring(i + 1, closeBracket)
                            val url = text.substring(closeBracket + 2, closeParen)
                            pushStringAnnotation("URL", url)
                            withStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) {
                                append(linkText)
                            }
                            pop()
                            i = closeParen + 1
                            continue
                        }
                    }
                }

                // Regular character
                append(chars[i])
                i++
            }
        }
    }
}
