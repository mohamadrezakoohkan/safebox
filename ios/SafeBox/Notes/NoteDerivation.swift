import Foundation

/// Derived-title model (idea plan §3.2): title = first non-empty line of body,
/// markdown-stripped; snippet = the following non-empty lines, title excluded.
/// Recomputed on every save.
enum NoteDerivation {
    static let emptyTitleFallback = "New note"
    static let snippetMaxLength = 160

    static func derive(from body: String) -> (title: String, snippet: String) {
        let lines = body.components(separatedBy: .newlines)
        var title = ""
        var titleLineIndex = -1
        for (index, line) in lines.enumerated() {
            let stripped = stripMarkdown(line)
            if !stripped.isEmpty {
                title = stripped
                titleLineIndex = index
                break
            }
        }
        var snippetParts: [String] = []
        if titleLineIndex >= 0 {
            for line in lines[(titleLineIndex + 1)...] {
                let stripped = stripMarkdown(line)
                if !stripped.isEmpty { snippetParts.append(stripped) }
                if snippetParts.joined(separator: " ").count > snippetMaxLength { break }
            }
        }
        var snippet = snippetParts.joined(separator: " ")
        if snippet.count > snippetMaxLength {
            snippet = String(snippet.prefix(snippetMaxLength))
        }
        return (title, snippet)
    }

    /// Removes block markers (headings, list/checklist markers) and inline
    /// emphasis/code syntax from a single line.
    static func stripMarkdown(_ line: String) -> String {
        var s = line.trimmingCharacters(in: .whitespaces)

        // Checklist markers: - [ ] / - [x] / * [X]
        for marker in ["- [ ] ", "- [x] ", "- [X] ", "* [ ] ", "* [x] ", "* [X] "] {
            if s.hasPrefix(marker) {
                s = String(s.dropFirst(marker.count))
                break
            }
        }
        // Heading markers.
        if s.hasPrefix("#") {
            let hashes = s.prefix(while: { $0 == "#" })
            if hashes.count <= 6 {
                let rest = s.dropFirst(hashes.count)
                if rest.hasPrefix(" ") || rest.isEmpty {
                    s = String(rest).trimmingCharacters(in: .whitespaces)
                }
            }
        }
        // Bullet list markers.
        for marker in ["- ", "* ", "+ "] {
            if s.hasPrefix(marker) {
                s = String(s.dropFirst(marker.count))
                break
            }
        }
        // Numbered list marker (e.g. "12. item").
        if let dotIndex = s.firstIndex(of: "."),
           !s[s.startIndex..<dotIndex].isEmpty,
           s[s.startIndex..<dotIndex].allSatisfy({ $0.isNumber }),
           s.index(after: dotIndex) < s.endIndex,
           s[s.index(after: dotIndex)] == " " {
            s = String(s[s.index(dotIndex, offsetBy: 2)...])
        }
        // Blockquote.
        if s.hasPrefix("> ") { s = String(s.dropFirst(2)) }

        // Inline emphasis/code characters.
        s = s.replacingOccurrences(of: "**", with: "")
        s = s.replacingOccurrences(of: "__", with: "")
        for ch in ["*", "`", "~"] {
            s = s.replacingOccurrences(of: ch, with: "")
        }
        return s.trimmingCharacters(in: .whitespaces)
    }
}
