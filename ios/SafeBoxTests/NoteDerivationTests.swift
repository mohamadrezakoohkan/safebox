import Testing
@testable import SafeBox

/// Asserts the shared derivation example table from the idea plan §3.2.
struct NoteDerivationTests {
    @Test func headingWithList() {
        let derived = NoteDerivation.derive(from: "# Shopping\n- milk\n- eggs")
        #expect(derived.title == "Shopping")
        #expect(derived.snippet == "milk eggs")
    }

    @Test func boldStartWithBlankLine() {
        let derived = NoteDerivation.derive(from: "**Bold** start\n\nsecond para")
        #expect(derived.title == "Bold start")
        #expect(derived.snippet == "second para")
    }

    @Test func checklistLines() {
        let derived = NoteDerivation.derive(from: "- [ ] pack bags\n- [x] tickets")
        #expect(derived.title == "pack bags")
        #expect(derived.snippet == "tickets")
    }

    @Test func inlineCode() {
        let derived = NoteDerivation.derive(from: "`config` notes")
        #expect(derived.title == "config notes")
        #expect(derived.snippet == "")
    }

    @Test func emptyBody() {
        let derived = NoteDerivation.derive(from: "")
        #expect(derived.title == "")
        #expect(derived.snippet == "")
    }

    @Test func leadingBlankLinesSkipped() {
        let derived = NoteDerivation.derive(from: "\n\n\nFirst real line\nmore")
        #expect(derived.title == "First real line")
        #expect(derived.snippet == "more")
    }

    @Test func titleLineExcludedFromSnippet() {
        let derived = NoteDerivation.derive(from: "Title\nTitle again\nbody")
        #expect(derived.snippet == "Title again body")
    }

    @Test func numberedListMarkerStripped() {
        let derived = NoteDerivation.derive(from: "1. first step\n2. second step")
        #expect(derived.title == "first step")
        #expect(derived.snippet == "second step")
    }
}
