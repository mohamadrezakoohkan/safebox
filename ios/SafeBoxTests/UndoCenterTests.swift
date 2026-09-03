import Foundation
import Testing
@testable import SafeBox

@MainActor
struct UndoCenterTests {
    /// Reference box so the undo closures can count calls without capturing a
    /// mutable local.
    private final class Counter {
        var calls = 0
    }

    @Test func sharedDisplayDurationIsFiveSeconds() {
        #expect(UndoCenter.displayDuration == .seconds(5))
    }

    @Test func postShowsTheEntryAndUndoRunsTheRestoreExactlyOnce() {
        let center = UndoCenter(displayDuration: .seconds(60))
        let counter = Counter()
        #expect(center.current == nil)

        center.post(message: VaultCopy.deletedNote) { counter.calls += 1 }
        #expect(center.current?.message == VaultCopy.deletedNote)
        #expect(counter.calls == 0)

        center.undo()
        #expect(counter.calls == 1)
        #expect(center.current == nil)

        center.undo() // nothing to undo any more
        #expect(counter.calls == 1)
    }

    @Test func aNewPostReplacesThePreviousEntryWithoutRunningItsUndo() {
        let center = UndoCenter(displayDuration: .seconds(60))
        let first = Counter()
        let second = Counter()
        center.post(message: "first") { first.calls += 1 }
        let firstId = center.current?.id
        center.post(message: "second") { second.calls += 1 }
        #expect(center.current?.message == "second")
        #expect(center.current?.id != firstId)

        center.undo()
        #expect(first.calls == 0)   // the replaced entry's items stay in Recently deleted
        #expect(second.calls == 1)
    }

    @Test func dismissHidesTheEntryWithoutUndoing() {
        let center = UndoCenter(displayDuration: .seconds(60))
        let counter = Counter()
        center.post(message: "x") { counter.calls += 1 }
        center.dismiss()
        #expect(center.current == nil)
        #expect(counter.calls == 0)
    }

    // MARK: - Timer semantics
    //
    // The scheduling DECISION (`dismiss(entryId:)`) is tested without a clock:
    // a timer only ever takes down the entry it was armed for. The one test
    // that does wait on the clock polls with a generous deadline instead of
    // asserting on a fixed sleep, so scheduler jitter cannot fail it.

    @Test func aTimerArmedForAReplacedEntryDoesNothing() {
        let center = UndoCenter(displayDuration: .seconds(60))
        center.post(message: "first") {}
        let firstId = center.current!.id
        center.post(message: "second") {}

        center.dismiss(entryId: firstId) // the first entry's timer firing late
        #expect(center.current?.message == "second")
    }

    @Test func aTimerArmedForTheCurrentEntryDismissesItWithoutUndoing() {
        let center = UndoCenter(displayDuration: .seconds(60))
        let counter = Counter()
        center.post(message: "x") { counter.calls += 1 }
        let id = center.current!.id

        center.dismiss(entryId: id)
        #expect(center.current == nil)
        #expect(counter.calls == 0)

        center.dismiss(entryId: id) // idempotent once cleared
        #expect(center.current == nil)
    }

    @Test func aTimerArmedForAnUndoneEntryDoesNotTouchTheNextOne() {
        let center = UndoCenter(displayDuration: .seconds(60))
        center.post(message: "first") {}
        let firstId = center.current!.id
        center.undo()
        center.post(message: "second") {}

        center.dismiss(entryId: firstId)
        #expect(center.current?.message == "second")
    }

    @Test func theEntryAutoDismissesAfterTheDisplayDuration() async throws {
        let center = UndoCenter(displayDuration: .milliseconds(20))
        let counter = Counter()
        center.post(message: "x") { counter.calls += 1 }
        #expect(center.current != nil)

        // Poll up to 5 s (250× the duration) rather than asserting at a fixed
        // instant; only a timer that never fires can fail this.
        let deadline = ContinuousClock.now.advanced(by: .seconds(5))
        while center.current != nil && ContinuousClock.now < deadline {
            try await Task.sleep(for: .milliseconds(10))
        }
        #expect(center.current == nil)
        #expect(counter.calls == 0)
    }
}
