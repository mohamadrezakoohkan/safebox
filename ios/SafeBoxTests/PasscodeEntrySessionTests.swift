import Testing
@testable import SafeBox

@MainActor
struct PasscodeEntrySessionTests {
    private func makeSession() async -> (PasscodeEntrySession, InMemoryPasscodeStore) {
        let store = InMemoryPasscodeStore()
        try? await store.set(sequence: [.d1, .d2, .d3, .d4])
        let session = PasscodeEntrySession(passcodeStore: store)
        return (session, store)
    }

    @Test func startsInVerifyCurrent() async {
        let (session, _) = await makeSession()
        #expect(session.phase == .verifyCurrent)
        #expect(session.banner.primary == LockCopy.verifyCurrentCaption)
    }

    @Test func wrongCurrentShowsVisibleError() async {
        let (session, _) = await makeSession()
        let shakeBefore = session.shakeToken
        await session.commit(sequence: [.d9, .d9, .d9, .d9], overflowed: false)
        #expect(session.phase == .verifyCurrent) // unlimited retries
        #expect(session.banner.primary == LockCopy.verifyError)
        #expect(session.bannerIsError)
        #expect(session.shakeToken > shakeBefore)
    }

    @Test func subMinimumCommitInVerifyShowsSameError() async {
        let (session, _) = await makeSession()
        await session.commit(sequence: [.d1], overflowed: false)
        #expect(session.banner.primary == LockCopy.verifyError)
    }

    @Test func overflowedCommitInVerifyShowsSameError() async {
        let (session, _) = await makeSession()
        await session.commit(sequence: [.d1, .d2, .d3, .d4], overflowed: true)
        #expect(session.banner.primary == LockCopy.verifyError)
    }

    @Test func keyPressClearsErrorCaption() async {
        let (session, _) = await makeSession()
        await session.commit(sequence: [.d9, .d9, .d9, .d9], overflowed: false)
        session.keyPressed()
        #expect(!session.bannerIsError)
        #expect(session.banner.primary == LockCopy.verifyCurrentCaption)
    }

    @Test func rightCurrentAdvancesToEnterNew() async {
        let (session, _) = await makeSession()
        await session.commit(sequence: [.d1, .d2, .d3, .d4], overflowed: false)
        #expect(session.phase == .enterNew)
        #expect(session.banner.primary == LockCopy.changeEnterNewCaption)
    }

    @Test func newCodeTooShortStaysInEnterNew() async {
        let (session, _) = await makeSession()
        await session.commit(sequence: [.d1, .d2, .d3, .d4], overflowed: false)
        await session.commit(sequence: [.d5, .d6], overflowed: false)
        #expect(session.phase == .enterNew)
        #expect(session.banner.primary == LockCopy.setupTooShort)
    }

    @Test func mismatchOnConfirmReturnsToEnterNew() async {
        let (session, store) = await makeSession()
        await session.commit(sequence: [.d1, .d2, .d3, .d4], overflowed: false)
        await session.commit(sequence: [.d5, .d6, .d7, .d8], overflowed: false)
        await session.commit(sequence: [.d5, .d6, .d7, .d9], overflowed: false)
        #expect(session.phase == .enterNew)
        #expect(session.banner.primary == LockCopy.setupMismatch)
        #expect(store.stored == [.d1, .d2, .d3, .d4]) // old code untouched
    }

    @Test func successfulChangeReplacesPasscode() async {
        let (session, store) = await makeSession()
        await session.commit(sequence: [.d1, .d2, .d3, .d4], overflowed: false)
        await session.commit(sequence: [.d5, .add, .d7, .pct], overflowed: false)
        await session.commit(sequence: [.d5, .add, .d7, .pct], overflowed: false)
        #expect(session.phase == .done)
        // Old code fails from this moment; the new one works.
        #expect(!(await store.matches(sequence: [.d1, .d2, .d3, .d4])))
        #expect(await store.matches(sequence: [.d5, .add, .d7, .pct]))
    }
}
