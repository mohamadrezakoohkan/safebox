import SwiftUI

/// Renders the shared cross-platform markdown subset (idea plan §3.2):
/// headings, bold/italic, inline code, bullet & numbered lists, and checklists
/// as non-interactive styled text. Line-aware block pre-pass over
/// AttributedString(markdown:) inline rendering.
struct MarkdownPreview: View {
    let markdown: String

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 6) {
                ForEach(Array(markdown.components(separatedBy: .newlines).enumerated()), id: \.offset) { _, line in
                    lineView(line)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding()
        }
    }

    @ViewBuilder
    private func lineView(_ rawLine: String) -> some View {
        let line = rawLine.trimmingCharacters(in: .whitespaces)
        if line.isEmpty {
            Spacer().frame(height: 4)
        } else if line.hasPrefix("# ") {
            inlineText(String(line.dropFirst(2))).font(.title.bold())
        } else if line.hasPrefix("## ") {
            inlineText(String(line.dropFirst(3))).font(.title2.bold())
        } else if line.hasPrefix("### ") {
            inlineText(String(line.dropFirst(4))).font(.title3.bold())
        } else if let checked = checklistContent(line, checked: true) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Image(systemName: "checkmark.square")
                inlineText(checked).strikethrough(false)
            }
        } else if let unchecked = checklistContent(line, checked: false) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Image(systemName: "square")
                inlineText(unchecked)
            }
        } else if line.hasPrefix("- ") || line.hasPrefix("* ") || line.hasPrefix("+ ") {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text("•")
                inlineText(String(line.dropFirst(2)))
            }
        } else if let (number, content) = numberedItem(line) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text("\(number).")
                inlineText(content)
            }
        } else {
            inlineText(line)
        }
    }

    private func checklistContent(_ line: String, checked: Bool) -> String? {
        let markers = checked ? ["- [x] ", "- [X] ", "* [x] ", "* [X] "] : ["- [ ] ", "* [ ] "]
        for marker in markers where line.hasPrefix(marker) {
            return String(line.dropFirst(marker.count))
        }
        return nil
    }

    private func numberedItem(_ line: String) -> (String, String)? {
        guard let dotIndex = line.firstIndex(of: "."),
              !line[line.startIndex..<dotIndex].isEmpty,
              line[line.startIndex..<dotIndex].allSatisfy({ $0.isNumber }),
              line.index(after: dotIndex) < line.endIndex,
              line[line.index(after: dotIndex)] == " " else { return nil }
        return (String(line[line.startIndex..<dotIndex]),
                String(line[line.index(dotIndex, offsetBy: 2)...]))
    }

    private func inlineText(_ content: String) -> Text {
        if let attributed = try? AttributedString(
            markdown: content,
            options: AttributedString.MarkdownParsingOptions(interpretedSyntax: .inlineOnlyPreservingWhitespace)
        ) {
            return Text(attributed)
        }
        return Text(content)
    }
}
