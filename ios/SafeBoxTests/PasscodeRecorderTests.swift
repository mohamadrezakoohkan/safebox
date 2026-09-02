import Testing
@testable import SafeBox

struct PasscodeRecorderTests {
    @Test func recordsPasscodeKeysInOrder() {
        var recorder = PasscodeRecorder()
        recorder.record(.d1)
        recorder.record(.add)
        recorder.record(.d2)
        let commit = recorder.takeCommit()
        #expect(commit.keys == [.d1, .add, .d2])
        #expect(!commit.overflowed)
    }

    @Test func commitResetsBufferAndFlag() {
        var recorder = PasscodeRecorder()
        recorder.record(.d1)
        _ = recorder.takeCommit()
        let second = recorder.takeCommit()
        #expect(second.keys.isEmpty)
        #expect(!second.overflowed)
    }

    @Test func thirtyThirdKeySetsOverflowFlag() {
        var recorder = PasscodeRecorder()
        for _ in 0..<32 { recorder.record(.d7) }
        #expect(!recorder.overflowed)
        recorder.record(.d7) // 33rd
        #expect(recorder.overflowed)
        let commit = recorder.takeCommit()
        #expect(commit.overflowed)
        #expect(commit.keys.count == 32)
    }

    @Test func clearResetsBufferAndOverflowFlag() {
        var recorder = PasscodeRecorder()
        for _ in 0..<40 { recorder.record(.d7) }
        #expect(recorder.overflowed)
        recorder.record(.clear) // AC/C is the natural "start over" gesture
        #expect(recorder.buffer.isEmpty)
        #expect(!recorder.overflowed)
    }

    @Test func equalsIsNotRecorded() {
        var recorder = PasscodeRecorder()
        recorder.record(.d1)
        recorder.record(.equals)
        #expect(recorder.buffer == [.d1])
    }

    @Test func serializationContract() {
        #expect(CalcKey.serialize([.d7, .add, .d3, .dot]) == "D7|ADD|D3|DOT")
        #expect(CalcKey.serialize([.d1, .d2, .add, .d3, .d4]) == "D1|D2|ADD|D3|D4")
    }
}
