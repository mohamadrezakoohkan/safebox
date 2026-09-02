package com.calcplus.calculator.core.markdown

/**
 * Derived-title model (idea plan §3.2): title = first non-empty line of body,
 * markdown-stripped; snippet = the following non-empty lines, title excluded.
 * Recomputed on every save. Must reproduce the shared example table exactly.
 */
object NoteDerivation {
    const val EMPTY_TITLE_FALLBACK = "New note"
    const val SNIPPET_MAX_LENGTH = 160

    data class Derived(val title: String, val snippet: String)

    fun derive(body: String): Derived {
        val lines = body.lines()
        var title = ""
        var titleLineIndex = -1
        for ((index, line) in lines.withIndex()) {
            val stripped = stripMarkdown(line)
            if (stripped.isNotEmpty()) {
                title = stripped
                titleLineIndex = index
                break
            }
        }
        val snippetParts = mutableListOf<String>()
        if (titleLineIndex >= 0) {
            for (line in lines.drop(titleLineIndex + 1)) {
                val stripped = stripMarkdown(line)
                if (stripped.isNotEmpty()) snippetParts.add(stripped)
                if (snippetParts.joinToString(" ").length > SNIPPET_MAX_LENGTH) break
            }
        }
        var snippet = snippetParts.joinToString(" ")
        if (snippet.length > SNIPPET_MAX_LENGTH) snippet = snippet.take(SNIPPET_MAX_LENGTH)
        return Derived(title, snippet)
    }

    /** Removes block markers and inline emphasis/code syntax from a single line. */
    fun stripMarkdown(line: String): String {
        var s = line.trim()

        // Checklist markers: - [ ] / - [x] / * [X]
        for (marker in listOf("- [ ] ", "- [x] ", "- [X] ", "* [ ] ", "* [x] ", "* [X] ")) {
            if (s.startsWith(marker)) {
                s = s.removePrefix(marker)
                break
            }
        }
        // Heading markers.
        if (s.startsWith("#")) {
            val hashes = s.takeWhile { it == '#' }
            if (hashes.length <= 6) {
                val rest = s.drop(hashes.length)
                if (rest.startsWith(" ") || rest.isEmpty()) {
                    s = rest.trim()
                }
            }
        }
        // Bullet list markers.
        for (marker in listOf("- ", "* ", "+ ")) {
            if (s.startsWith(marker)) {
                s = s.removePrefix(marker)
                break
            }
        }
        // Numbered list marker (e.g. "12. item").
        val dotIndex = s.indexOf('.')
        if (dotIndex > 0 &&
            s.substring(0, dotIndex).all { it.isDigit() } &&
            dotIndex + 1 < s.length && s[dotIndex + 1] == ' '
        ) {
            s = s.substring(dotIndex + 2)
        }
        // Blockquote.
        if (s.startsWith("> ")) s = s.removePrefix("> ")

        // Inline emphasis/code characters.
        s = s.replace("**", "").replace("__", "")
        for (ch in listOf("*", "`", "~")) {
            s = s.replace(ch, "")
        }
        return s.trim()
    }
}
