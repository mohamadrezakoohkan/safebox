package com.calcplus.calculator.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Renders the shared cross-platform markdown subset (idea plan §3.2):
 * headings, bold/italic, inline code, bullet & numbered lists, and checklists
 * as non-interactive styled text. Line-aware block pre-pass over an inline
 * AnnotatedString renderer — the sanctioned custom fallback (android-plan
 * §4.3), mirroring the iOS MarkdownPreview line by line.
 */
@Composable
fun MarkdownPreview(markdown: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        markdown.lines().forEach { rawLine ->
            MarkdownLine(rawLine.trim())
        }
    }
}

@Composable
private fun MarkdownLine(line: String) {
    when {
        line.isEmpty() -> Spacer(modifier = Modifier.height(4.dp))
        line.startsWith("# ") -> Text(
            inlineMarkdown(line.removePrefix("# ")),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        line.startsWith("## ") -> Text(
            inlineMarkdown(line.removePrefix("## ")),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        line.startsWith("### ") -> Text(
            inlineMarkdown(line.removePrefix("### ")),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        checklistContent(line, checked = true) != null -> ChecklistRow("☑", checklistContent(line, true)!!)
        checklistContent(line, checked = false) != null -> ChecklistRow("☐", checklistContent(line, false)!!)
        line.startsWith("- ") || line.startsWith("* ") || line.startsWith("+ ") ->
            ChecklistRow("•", line.drop(2))
        numberedItem(line) != null -> {
            val (number, content) = numberedItem(line)!!
            ChecklistRow("$number.", content)
        }
        else -> Text(inlineMarkdown(line), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ChecklistRow(marker: String, content: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(marker, style = MaterialTheme.typography.bodyLarge)
        Text(
            inlineMarkdown(content),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun checklistContent(line: String, checked: Boolean): String? {
    val markers = if (checked) {
        listOf("- [x] ", "- [X] ", "* [x] ", "* [X] ")
    } else {
        listOf("- [ ] ", "* [ ] ")
    }
    for (marker in markers) {
        if (line.startsWith(marker)) return line.removePrefix(marker)
    }
    return null
}

private fun numberedItem(line: String): Pair<String, String>? {
    val dotIndex = line.indexOf('.')
    if (dotIndex > 0 &&
        line.substring(0, dotIndex).all { it.isDigit() } &&
        dotIndex + 1 < line.length && line[dotIndex + 1] == ' '
    ) {
        return line.substring(0, dotIndex) to line.substring(dotIndex + 2)
    }
    return null
}

/** Inline subset: **bold**, *italic*, `code`. */
internal fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(text[i]); i += 1
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(text[i]); i += 1
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(text[i]); i += 1
                }
            }
            else -> {
                append(text[i]); i += 1
            }
        }
    }
}
