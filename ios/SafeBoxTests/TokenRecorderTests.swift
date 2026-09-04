import Testing
@testable import SafeBox

/// The whole decisions §1.2 table, over token IDs.
struct TokenRecorderTests {
    @Test func recordsTokensInOrder() {
        var recorder = TokenRecorder()
        recorder.record("D1")
        recorder.record("ADD")
        recorder.record("D2")
        let commit = recorder.takeCommit()
        #expect(commit.tokens == ["D1", "ADD", "D2"])
        #expect(!commit.overflowed)
    }

    @Test func commitResetsBufferAndFlag() {
        var recorder = TokenRecorder()
        recorder.record("D1")
        _ = recorder.takeCommit()
        let second = recorder.takeCommit()
        #expect(second.tokens.isEmpty)
        #expect(!second.overflowed)
    }

    @Test func thirtyThirdTokenSetsOverflowFlag() {
        var recorder = TokenRecorder()
        for _ in 0..<32 { recorder.record("D7") }
        #expect(!recorder.overflowed)
        recorder.record("D7") // 33rd
        #expect(recorder.overflowed)
        let commit = recorder.takeCommit()
        #expect(commit.overflowed)
        #expect(commit.tokens.count == 32)
    }

    @Test func clearResetsBufferAndOverflowFlag() {
        var recorder = TokenRecorder()
        for _ in 0..<40 { recorder.record("D7") }
        #expect(recorder.overflowed)
        recorder.clear()
        #expect(recorder.buffer.isEmpty)
        #expect(!recorder.overflowed)
    }

    // MARK: - removeLast (§1.2)

    @Test func removeLastPopsTheLastToken() {
        var recorder = TokenRecorder()
        recorder.record("D1")
        recorder.record("D2")
        recorder.removeLast()
        #expect(recorder.buffer == ["D1"])
    }

    @Test func removeLastOnAnEmptyBufferIsANoOp() {
        var recorder = TokenRecorder()
        recorder.removeLast()
        #expect(recorder.buffer.isEmpty)
        #expect(!recorder.overflowed)
    }

    @Test func overflowIsStickyThroughRemoveLast() {
        var recorder = TokenRecorder()
        for _ in 0..<33 { recorder.record("D7") }
        #expect(recorder.overflowed)
        recorder.removeLast()
        recorder.removeLast()
        // Backspacing can never recover an overflowed entry.
        #expect(recorder.overflowed)
        #expect(recorder.buffer.count == 30)
        #expect(recorder.takeCommit().overflowed)
    }

    @Test func onlyClearOrCommitLiftsStickyOverflow() {
        var recorder = TokenRecorder()
        for _ in 0..<33 { recorder.record("D7") }
        recorder.removeLast()
        recorder.clear()
        #expect(!recorder.overflowed)

        for _ in 0..<33 { recorder.record("D7") }
        recorder.removeLast()
        _ = recorder.takeCommit()
        #expect(!recorder.overflowed)
    }

    // MARK: - apply(_:)

    @Test func applyReturnsAPayloadOnlyForCommit() {
        var recorder = TokenRecorder()
        #expect(recorder.apply(.token("D1")) == nil)
        #expect(recorder.apply(.removeLast) == nil)
        #expect(recorder.apply(.clear) == nil)
        recorder.record("D5")
        let commit = recorder.apply(.commit)
        #expect(commit?.tokens == ["D5"])
        #expect(commit?.overflowed == false)
    }

    @Test func applyDrivesTheWholeStream() {
        var recorder = TokenRecorder()
        for event in [DisguiseEvent.token("D1"), .token("D2"), .token("D3"), .removeLast, .token("D9")] {
            #expect(recorder.apply(event) == nil)
        }
        #expect(recorder.apply(.commit)?.tokens == ["D1", "D2", "D9"])
    }
}
