import Testing
@testable import SafeBox

@MainActor
struct PasscodeEntrySessionTests {
    private let registry = DisguiseRegistry()

    private func makeSession(kind: PasscodeEntrySession.Kind = .changePasscode,
                             currentId: String = "calculator")
    async -> (PasscodeEntrySession, InMemoryPasscodeStore) {
        let store = InMemoryPasscodeStore()
        let current = registry.resolve(id: currentId)
        try? await store.set(tokens: ["D1", "D2", "D3", "D4"],
                             alphabet: current.alphabet,
                             activeDisguiseId: current.id)
        let session = PasscodeEntrySession(passcodeStore: store,
                                           registry: registry,
                                           currentDisguise: current,
                                           kind: kind)
        return (session, store)
    }

    // MARK: - Change passcode

    @Test func startsInVerifyCurrent() async {
        let (session, _) = await makeSession()
        #expect(session.phase == .verifyCurrent)
        #expect(session.caption.primary == .promptCurrent)
        #expect(session.surfaceMode == .verifyCurrent)
    }

    @Test func wrongCurrentShowsVisibleError() async {
        let (session, _) = await makeSession()
        await session.commit(tokens: ["D9", "D9", "D9", "D9"], overflowed: false)
        #expect(session.phase == .verifyCurrent) // unlimited retries
        #expect(session.caption.primary == .wrongCode)
        #expect(session.caption.primary.isError)
        #expect(session.failedAttemptCount == 1)
    }

    @Test func subMinimumCommitInVerifyShowsSameError() async {
        let (session, _) = await makeSession()
        await session.commit(tokens: ["D1"], overflowed: false)
        #expect(session.caption.primary == .wrongCode)
        #expect(session.failedAttemptCount == 1)
    }

    @Test func overflowedCommitInVerifyShowsSameError() async {
        let (session, _) = await makeSession()
        await session.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: true)
        #expect(session.caption.primary == .wrongCode)
    }

    @Test func anyInputEventClearsTheErrorCaption() async {
        for event in [DisguiseEvent.token("D5"), .clear, .removeLast] {
            let (session, _) = await makeSession()
            await session.commit(tokens: ["D9", "D9", "D9", "D9"], overflowed: false)
            session.eventObserved(event)
            #expect(!session.caption.primary.isError)
            #expect(session.caption.primary == .promptCurrent)
        }
    }

    @Test func aCommitDoesNotRevertTheErrorCaption() async {
        let (session, _) = await makeSession()
        await session.commit(tokens: ["D9", "D9", "D9", "D9"], overflowed: false)
        session.eventObserved(.commit)
        #expect(session.caption.primary == .wrongCode)
    }

    @Test func rightCurrentAdvancesToEnterNew() async {
        let (session, _) = await makeSession()
        await session.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        #expect(session.phase == .enterNew)
        #expect(session.caption.primary == .promptNewChange)
        #expect(session.surfaceMode == .captureNew)
    }

    @Test func newCodeTooShortStaysInEnterNew() async {
        let (session, _) = await makeSession()
        await session.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        await session.commit(tokens: ["D5", "D6"], overflowed: false)
        #expect(session.phase == .enterNew)
        #expect(session.caption.primary == .tooShort)
    }

    @Test func mismatchOnConfirmReturnsToEnterNew() async {
        let (session, store) = await makeSession()
        await session.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        await session.commit(tokens: ["D5", "D6", "D7", "D8"], overflowed: false)
        await session.commit(tokens: ["D5", "D6", "D7", "D9"], overflowed: false)
        #expect(session.phase == .enterNew)
        #expect(session.caption.primary == .mismatch)
        #expect(store.stored == ["D1", "D2", "D3", "D4"]) // old code untouched
    }

    @Test func successfulChangeReplacesPasscodeAndPreservesTheFace() async {
        let (session, store) = await makeSession(currentId: "numpad")
        await session.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        await session.commit(tokens: ["D5", "D7", "D7", "D2"], overflowed: false)
        await session.commit(tokens: ["D5", "D7", "D7", "D2"], overflowed: false)
        #expect(session.phase == .done)
        #expect(!(await store.matches(tokens: ["D1", "D2", "D3", "D4"])))
        #expect(await store.matches(tokens: ["D5", "D7", "D7", "D2"]))
        // A plain change preserves the enrolled face and its alphabet.
        #expect(store.storedDisguiseId == "numpad")
        #expect(store.storedAlphabet?.tokenSetId == "numpad")
    }

    @Test func aWriteFailureLeavesTheOldEnvelopeIntact() async {
        let (session, store) = await makeSession()
        await session.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        await session.commit(tokens: ["D5", "D6", "D7", "D8"], overflowed: false)
        store.failNextSet = true
        await session.commit(tokens: ["D5", "D6", "D7", "D8"], overflowed: false)
        #expect(session.phase == .enterNew)
        #expect(session.caption.primary == .promptNewChange)
        #expect(await store.matches(tokens: ["D1", "D2", "D3", "D4"]))
    }

    // MARK: - Change disguise (§5)

    @Test func verifyLeadsToThePickerOnlyForADisguiseSwitch() async {
        let (session, _) = await makeSession(kind: .changeDisguise)
        await session.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        #expect(session.phase == .pickDisguise)
        #expect(session.surfaceDisguise.id == "calculator")
    }

    @Test func theCurrentFaceCannotBePicked() async {
        let (session, _) = await makeSession(kind: .changeDisguise)
        await session.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        let calculator = registry.resolve(id: "calculator")
        #expect(!session.canPick(calculator))
        session.pick(calculator)
        #expect(session.phase == .pickDisguise) // no no-op path
        #expect(session.canPick(registry.resolve(id: "pattern")))
    }

    @Test func pickingSwapsTheFaceAndEntersCapture() async {
        let (session, _) = await makeSession(kind: .changeDisguise)
        await session.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        session.pick(registry.resolve(id: "pattern"))
        #expect(session.phase == .enterNew)
        #expect(session.surfaceDisguise.id == "pattern")
        #expect(session.surfaceMode == .captureNew)
        #expect(session.caption.primary == .promptNewChange)
        #expect(session.caption.secondary == .strengthHint)
    }

    @Test func aSwitchWritesTheNewFacesAlphabetAndIdExactlyOnce() async {
        let store = InMemoryPasscodeStore()
        let current = registry.resolve(id: "calculator")
        try? await store.set(tokens: ["D1", "D2", "D3", "D4"],
                             alphabet: current.alphabet, activeDisguiseId: current.id)
        let spy = SpyPasscodeStore()
        await spy.seed(["D1", "D2", "D3", "D4"])
        let session = PasscodeEntrySession(passcodeStore: spy, registry: registry,
                                           currentDisguise: current, kind: .changeDisguise)
        await session.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        session.pick(registry.resolve(id: "pattern"))
        await session.commit(tokens: ["N0", "N3", "N6", "N7"], overflowed: false)
        await session.commit(tokens: ["N0", "N3", "N6", "N7"], overflowed: false)
        #expect(session.phase == .done)
        #expect(spy.setCallCount == 1) // ONE atomic replace
        #expect(spy.activeDisguiseId == "pattern")
        #expect(spy.storedAlphabet?.tokenSetId == "pattern")
        #expect(!(await spy.matches(tokens: ["D1", "D2", "D3", "D4"])))
        #expect(await spy.matches(tokens: ["N0", "N3", "N6", "N7"]))
    }

    @Test func abandoningAtAnyPhaseLeavesEverythingIntact() async {
        // Nothing is written before CONFIRM matches, at any phase.
        let spy = SpyPasscodeStore()
        await spy.seed(["D1", "D2", "D3", "D4"])
        let session = PasscodeEntrySession(passcodeStore: spy, registry: registry,
                                           currentDisguise: registry.resolve(id: "calculator"),
                                           kind: .changeDisguise)
        await session.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        session.pick(registry.resolve(id: "numpad"))
        await session.commit(tokens: ["D5", "D6", "D7", "D8"], overflowed: false)
        // The user cancels here; the session is simply dropped.
        #expect(spy.setCallCount == 0)
        #expect(spy.activeDisguiseId == "calculator")
        #expect(await spy.matches(tokens: ["D1", "D2", "D3", "D4"]))
    }

    @Test func aSwitchWriteFailureKeepsTheOldFaceAndCode() async {
        let store = InMemoryPasscodeStore()
        let current = registry.resolve(id: "calculator")
        try? await store.set(tokens: ["D1", "D2", "D3", "D4"],
                             alphabet: current.alphabet, activeDisguiseId: current.id)
        let session = PasscodeEntrySession(passcodeStore: store, registry: registry,
                                           currentDisguise: current, kind: .changeDisguise)
        await session.commit(tokens: ["D1", "D2", "D3", "D4"], overflowed: false)
        session.pick(registry.resolve(id: "numpad"))
        await session.commit(tokens: ["D5", "D6", "D7", "D8"], overflowed: false)
        store.failNextSet = true
        await session.commit(tokens: ["D5", "D6", "D7", "D8"], overflowed: false)
        #expect(session.phase == .enterNew)
        #expect(store.activeDisguiseId == "calculator")
        #expect(await store.matches(tokens: ["D1", "D2", "D3", "D4"]))
    }

    @Test func theNavigationTitleNamesTheFlow() async {
        let (change, _) = await makeSession(kind: .changePasscode)
        let (switchFlow, _) = await makeSession(kind: .changeDisguise)
        #expect(change.navigationTitle == "Change passcode")
        #expect(switchFlow.navigationTitle == "Change disguise")
    }
}
