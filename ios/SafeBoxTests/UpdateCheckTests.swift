import Foundation
import Testing
@testable import SafeBox

/// Decisions §13. Nothing here touches the network: the model's fetch closure
/// is injected, and the posture assertions inspect the configuration/request
/// the app would build rather than performing one.
struct AppVersionTests {
    // MARK: - Missing components count as zero

    @Test func shorterAndLongerFormsOfTheSameVersionAreEqual() {
        // iOS ships CFBundleShortVersionString "1.0", Android versionName
        // "1.0.0"; neither may be reported as an update over the other.
        #expect(AppVersion.isNewer("1.0.0", than: "1.0") == false)
        #expect(AppVersion.isNewer("1.0", than: "1.0.0") == false)
        #expect(AppVersion.isNewer("1", than: "1.0.0.0") == false)
        #expect(AppVersion.isNewer("1.0.0.0", than: "1") == false)
    }

    @Test func newerPatchAndMajorAreDetected() {
        #expect(AppVersion.isNewer("1.0.1", than: "1.0"))
        #expect(AppVersion.isNewer("1.0.1", than: "1.0.0"))
        #expect(AppVersion.isNewer("2.0", than: "1.9.9"))
        #expect(AppVersion.isNewer("1.10", than: "1.9"))
        #expect(AppVersion.isNewer("10.0", than: "9.99.99"))
    }

    @Test func olderVersionsAreNotNewer() {
        #expect(AppVersion.isNewer("1.0", than: "1.0.1") == false)
        #expect(AppVersion.isNewer("1.9.9", than: "2.0") == false)
        #expect(AppVersion.isNewer("0.9", than: "1.0") == false)
    }

    // MARK: - Leading zeros / differing lengths

    @Test func leadingZerosCarryNoMeaning() {
        #expect(AppVersion.components("1.01.0") == [1, 1, 0])
        // "1.0.01" == "1.0.1", and both are newer than "1.0".
        #expect(AppVersion.isNewer("1.0.01", than: "1.0.1") == false)
        #expect(AppVersion.isNewer("1.0.1", than: "1.0.01") == false)
        #expect(AppVersion.isNewer("1.0.01", than: "1.0"))
        // 02 is two, not a string that sorts before "1".
        #expect(AppVersion.isNewer("1.02", than: "1.1"))
        #expect(AppVersion.isNewer("1.0000", than: "1.0") == false)
    }

    // MARK: - Unparseable input can never nag

    @Test func malformedVersionsAreNeverNewer() {
        let malformed = ["", " ", "abc", "1.x", "1..2", "1.0-beta", "v1.0", "1,0", "1.0 ", "-1.0", ".1", "1."]
        for value in malformed {
            #expect(AppVersion.components(value) == nil, "expected \(value) to be unparseable")
            #expect(AppVersion.isNewer(value, than: "1.0") == false, "expected \(value) not newer")
            #expect(AppVersion.isNewer("999.0", than: value) == false, "expected unparseable current to block")
        }
    }

    @Test func nonASCIIDigitsAreUnparseable() {
        // Arabic-Indic digits satisfy Character.isNumber but are not a version.
        #expect(AppVersion.components("١.٠") == nil)
        #expect(AppVersion.isNewer("١.٠", than: "1.0") == false)
    }

    @Test func overflowingComponentsAreUnparseable() {
        #expect(AppVersion.components("99999999999999999999.0") == nil)
        #expect(AppVersion.isNewer("99999999999999999999.0", than: "1.0") == false)
    }

    @Test func runningVersionParses() {
        // Guards against a build settings change that would make every check
        // silently report "up to date".
        #expect(AppVersion.components(AppVersion.current) != nil)
    }
}

// MARK: - Manifest decoding

struct UpdateManifestTests {
    private func data(_ json: String) -> Data { Data(json.utf8) }

    @Test func decodesBothFields() throws {
        let manifest = try #require(UpdateCheck.manifest(from: data(
            #"{"latestVersion": "1.2.3", "releasesUrl": "https://github.com/mohamadrezakoohkan/safebox/releases/tag/v1.2.3"}"#
        )))
        #expect(manifest.latestVersion == "1.2.3")
        #expect(manifest.releasesURL.absoluteString
            == "https://github.com/mohamadrezakoohkan/safebox/releases/tag/v1.2.3")
    }

    @Test func missingReleasesUrlFallsBackToTheReleasesPage() throws {
        let manifest = try #require(UpdateCheck.manifest(from: data(#"{"latestVersion": "1.0.0"}"#)))
        #expect(manifest.releasesUrl == nil)
        #expect(manifest.releasesURL == UpdateEndpoint.defaultReleasesURL)
    }

    @Test func nonHTTPSOrEmptyReleasesUrlFallsBack() throws {
        // The manifest is remote input, so it does not get to pick the scheme.
        for raw in ["", "  ", "http://example.com/x", "file:///etc/passwd", "safebox://open", "not a url"] {
            let json = #"{"latestVersion": "1.0.0", "releasesUrl": "\#(raw)"}"#
            let manifest = try #require(UpdateCheck.manifest(from: data(json)), "failed for \(raw)")
            #expect(manifest.releasesURL == UpdateEndpoint.defaultReleasesURL, "failed for \(raw)")
        }
    }

    @Test func malformedBodyDoesNotDecode() {
        #expect(UpdateCheck.manifest(from: data("")) == nil)
        #expect(UpdateCheck.manifest(from: data("not json at all")) == nil)
        #expect(UpdateCheck.manifest(from: data("<html><body>404</body></html>")) == nil)
        // Right shape, wrong key.
        #expect(UpdateCheck.manifest(from: data(#"{"version": "1.0.0"}"#)) == nil)
        // Right key, wrong type.
        #expect(UpdateCheck.manifest(from: data(#"{"latestVersion": 1.0}"#)) == nil)
    }

    // MARK: - Pure state resolution

    @Test func stateResolvesFromBytes() {
        #expect(UpdateCheck.state(for: data(#"{"latestVersion": "1.0.0"}"#), currentVersion: "1.0") == .upToDate)
        #expect(UpdateCheck.state(for: data(#"{"latestVersion": "0.9"}"#), currentVersion: "1.0") == .upToDate)
        #expect(UpdateCheck.state(for: data("garbage"), currentVersion: "1.0") == .failed)
        #expect(UpdateCheck.state(for: data(#"{"latestVersion": "2.0"}"#), currentVersion: "1.0")
            == .available(version: "2.0", releasesURL: UpdateEndpoint.defaultReleasesURL))
        // A malformed latestVersion is "up to date", never a nag.
        #expect(UpdateCheck.state(for: data(#"{"latestVersion": "banana"}"#), currentVersion: "1.0") == .upToDate)
    }

    @Test func subtitlesMatchTheSharedStringTable() {
        #expect(UpdateCheckState.idle.subtitle == nil)
        #expect(UpdateCheckState.checking.subtitle == "Checking…")
        #expect(UpdateCheckState.upToDate.subtitle == "Up to date")
        #expect(UpdateCheckState.failed.subtitle == "Couldn't check for updates")
        #expect(UpdateCheckState.available(version: "1.2.3",
                                           releasesURL: UpdateEndpoint.defaultReleasesURL).subtitle
            == "Version 1.2.3 available")
    }

    @Test func onlyTheAvailableStateOpensAURL() {
        #expect(UpdateCheckState.idle.openURL == nil)
        #expect(UpdateCheckState.checking.openURL == nil)
        #expect(UpdateCheckState.upToDate.openURL == nil)
        #expect(UpdateCheckState.failed.openURL == nil)
        #expect(UpdateCheckState.available(version: "2.0",
                                           releasesURL: UpdateEndpoint.defaultReleasesURL).openURL
            == UpdateEndpoint.defaultReleasesURL)
    }
}

// MARK: - Network posture

/// The app's one and only request. These assertions are the enforcement of
/// decisions §13 — if any of them fail, the posture regressed.
struct UpdateEndpointTests {
    @Test func sessionConfigurationLeavesNothingOnDisk() {
        let configuration = UpdateEndpoint.makeSessionConfiguration()
        // Ephemeral + explicitly cleared: no cache file naming the repo can
        // land inside the app container.
        #expect(configuration.urlCache == nil)
        #expect(configuration.requestCachePolicy == .reloadIgnoringLocalCacheData)
        #expect(configuration.httpCookieStorage == nil)
        #expect(configuration.urlCredentialStorage == nil)
        #expect(configuration.httpShouldSetCookies == false)
        #expect(configuration.httpCookieAcceptPolicy == .never)
        #expect(configuration.httpAdditionalHeaders == nil)
        // Not a background configuration: nothing survives the process.
        #expect(configuration.identifier == nil)
        #expect(configuration.timeoutIntervalForRequest == 10)
        #expect(configuration.timeoutIntervalForResource == 10)
    }

    @Test func ephemeralConfigurationIsTheBasis() {
        // Documents the platform behaviour the choice relies on: an ephemeral
        // configuration keeps no persistent cookie or credential store.
        let ephemeral = URLSessionConfiguration.ephemeral
        #expect(ephemeral.identifier == nil)
        #expect((ephemeral.urlCache?.diskCapacity ?? 0) == 0)
    }

    @Test func requestIsABareGet() {
        let request = UpdateEndpoint.makeRequest()
        #expect(request.httpMethod == "GET")
        #expect(request.httpBody == nil)
        #expect(request.httpShouldHandleCookies == false)
        #expect(request.cachePolicy == .reloadIgnoringLocalCacheData)
        #expect(request.timeoutInterval == 10)
        // No custom headers, and nothing about the device or vault in them.
        #expect((request.allHTTPHeaderFields ?? [:]).isEmpty)
        // No query string: the URL carries no identifier of any kind.
        let components = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)
        #expect(components?.query == nil)
        #expect(components?.fragment == nil)
        #expect(request.url == UpdateEndpoint.manifestURL)
    }

    @Test func urlsAreHTTPSAndPointAtTheRepo() {
        // HTTPS everywhere, so default App Transport Security suffices and
        // Info.plist needs no ATS exception.
        for url in [UpdateEndpoint.sourceURL, UpdateEndpoint.manifestURL, UpdateEndpoint.defaultReleasesURL] {
            #expect(url.scheme == "https")
        }
        #expect(UpdateEndpoint.sourceURL.absoluteString == "https://github.com/mohamadrezakoohkan/safebox")
        #expect(UpdateEndpoint.manifestURL.absoluteString
            == "https://raw.githubusercontent.com/mohamadrezakoohkan/safebox/main/version.json")
        #expect(UpdateEndpoint.defaultReleasesURL.absoluteString
            == "https://github.com/mohamadrezakoohkan/safebox/releases/latest")
    }
}

// MARK: - State machine

@MainActor
struct UpdateCheckModelTests {
    private func makeModel(currentVersion: String = "1.0",
                           fetch: @escaping UpdateFetch) -> UpdateCheckModel {
        UpdateCheckModel(currentVersion: currentVersion, fetch: fetch)
    }

    @Test func startsIdleAndMakesNoRequest() async {
        let calls = FetchCounter()
        let model = makeModel(fetch: { await calls.record(); return Data() })
        // Construction alone must not touch the network (manual-only posture).
        #expect(model.state == .idle)
        #expect(await calls.count == 0)
    }

    @Test func idleThenCheckingThenUpToDate() async {
        let model = makeModel(fetch: { Data(#"{"latestVersion": "1.0.0"}"#.utf8) })
        #expect(model.state == .idle)
        model.check()
        #expect(model.state == .checking)
        #expect(model.isChecking)
        await model.waitForCurrentCheck()
        #expect(model.state == .upToDate)
        #expect(model.isChecking == false)
    }

    @Test func availableCarriesTheVersionAndURL() async {
        let model = makeModel(fetch: {
            Data(#"{"latestVersion": "1.4", "releasesUrl": "https://github.com/x/y/releases/tag/v1.4"}"#.utf8)
        })
        model.check()
        await model.waitForCurrentCheck()
        #expect(model.state == .available(version: "1.4",
                                          releasesURL: URL(string: "https://github.com/x/y/releases/tag/v1.4")!))
        #expect(model.state.subtitle == "Version 1.4 available")
    }

    @Test func availableWithoutReleasesUrlUsesTheFallback() async {
        let model = makeModel(fetch: { Data(#"{"latestVersion": "9.9"}"#.utf8) })
        model.check()
        await model.waitForCurrentCheck()
        #expect(model.state == .available(version: "9.9", releasesURL: UpdateEndpoint.defaultReleasesURL))
    }

    @Test func thrownErrorBecomesFailed() async {
        let model = makeModel(fetch: { throw UpdateCheckFailure.badResponse })
        model.check()
        await model.waitForCurrentCheck()
        #expect(model.state == .failed)
    }

    @Test func malformedBodyBecomesFailed() async {
        let model = makeModel(fetch: { Data("<html>404</html>".utf8) })
        model.check()
        await model.waitForCurrentCheck()
        #expect(model.state == .failed)
    }

    @Test func failedStateRetriesOnTheNextTap() async {
        let responder = ScriptedFetch(responses: [
            .failure(UpdateCheckFailure.badResponse),
            .success(Data(#"{"latestVersion": "1.0.0"}"#.utf8)),
        ])
        let model = makeModel(fetch: { try await responder.next() })
        model.rowTapped(open: { _ in Issue.record("must not open a URL from \(String(describing: model.state))") })
        await model.waitForCurrentCheck()
        #expect(model.state == .failed)
        model.rowTapped(open: { _ in Issue.record("must not open a URL from failed") })
        await model.waitForCurrentCheck()
        #expect(model.state == .upToDate)
        let calls = await responder.calls
        #expect(calls == 2)
    }

    @Test func whileCheckingASecondTapDoesNotStartASecondRequest() async {
        let gate = FetchGate()
        let model = makeModel(fetch: { try await gate.wait() })
        model.check()
        model.check()
        model.rowTapped(open: { _ in Issue.record("must not open a URL while checking") })
        #expect(model.state == .checking)
        await gate.release(with: Data(#"{"latestVersion": "1.0.0"}"#.utf8))
        await model.waitForCurrentCheck()
        #expect(model.state == .upToDate)
        let calls = await gate.calls
        #expect(calls == 1)
    }

    @Test func availableRowOpensTheReleasesPageInsteadOfRechecking() async {
        let responder = ScriptedFetch(responses: [.success(Data(#"{"latestVersion": "2.0"}"#.utf8))])
        let model = makeModel(fetch: { try await responder.next() })
        model.check()
        await model.waitForCurrentCheck()
        var opened: [URL] = []
        model.rowTapped(open: { opened.append($0) })
        #expect(opened == [UpdateEndpoint.defaultReleasesURL])
        // The tap opened a URL; it did not fire another request.
        let calls = await responder.calls
        #expect(calls == 1)
        #expect(model.state == .available(version: "2.0", releasesURL: UpdateEndpoint.defaultReleasesURL))
    }

    // MARK: - A completed result is never discarded

    /// Regression: the row used to go blank after "Checking…". `SettingsScreen`
    /// cancelled on `onDisappear`, and a Settings Form disappears transiently
    /// (a NavigationLink push, a sheet, a tab change, the snapshot cover on
    /// resign-active). The cancel reset the state to `idle`, whose subtitle is
    /// `nil`, so the row rendered title-only.
    @Test func aCompletedResultSurvivesACancelRacingIt() async {
        let gate = FetchGate()
        let model = makeModel(fetch: { try await gate.wait() })
        model.check()
        #expect(model.state == .checking)
        // Exactly what a transient disappear used to trigger.
        model.cancel()
        // The request had in fact already completed; its answer must land.
        await gate.release(with: Data(#"{"latestVersion": "9.9"}"#.utf8))
        await model.waitForCurrentCheck()
        #expect(model.state == .available(version: "9.9", releasesURL: UpdateEndpoint.defaultReleasesURL))
        #expect(model.state != .idle)
        #expect(model.state.subtitle != nil)
    }

    /// `idle` — the only state with no subtitle — is reserved for "never
    /// checked". Once a check has started and been allowed to finish, the row
    /// always shows an outcome, whatever the fetch did.
    @Test(arguments: UpdateCheckFetchOutcome.allCases)
    func theRowIsNeverBlankAfterAFinishedCheck(outcome: UpdateCheckFetchOutcome) async {
        let model = makeModel(fetch: outcome.fetch)
        model.check()
        #expect(model.state == .checking)
        // A teardown-style cancel racing the answer changes nothing.
        model.cancel()
        await model.waitForCurrentCheck()
        #expect(model.state != .idle, "blank row after \(outcome)")
        #expect(model.state != .checking, "stuck on Checking… after \(outcome)")
        #expect(model.state.subtitle != nil, "no subtitle after \(outcome)")
        #expect(model.state == outcome.expected)
    }

    @Test func cancelLeavesASettledStateAlone() async {
        let model = makeModel(fetch: { Data(#"{"latestVersion": "1.0.0"}"#.utf8) })
        model.check()
        await model.waitForCurrentCheck()
        model.cancel()
        #expect(model.state == .upToDate)
    }

    // MARK: - Teardown actually abandons the request

    /// Decisions §13: the in-flight request must die with the vault. The model
    /// is `@State` on `SettingsScreen`, so that means its deallocation — not
    /// `onDisappear` — has to cancel the task.
    @Test func releasingTheModelCancelsTheInFlightTask() async {
        let probe = FetchProbe()
        var model: UpdateCheckModel? = makeModel(fetch: {
            await probe.markStarted()
            await withTaskCancellationHandler {
                // Long enough that only cancellation ends it.
                try? await Task.sleep(for: .seconds(30))
            } onCancel: {
                Task { await probe.markCancelled() }
            }
            throw UpdateCheckFailure.badResponse
        })
        model?.check()
        _ = await waitUntil { await probe.started }

        // The vault tears down: SettingsScreen and its @State model go away.
        model = nil

        let cancelled = await waitUntil { await probe.cancelled }
        #expect(cancelled, "releasing the model must cancel the in-flight request")
    }

    /// The other half of §13: nothing may be written after teardown. The
    /// `[weak self]` capture is what guarantees it, so a response arriving
    /// after the model is gone must simply evaporate.
    @Test func aLateResponseAfterTeardownWritesNothing() async {
        let gate = FetchGate()
        let probe = FetchProbe()
        var model: UpdateCheckModel? = makeModel(fetch: {
            await probe.markStarted()
            let data = try await gate.wait()
            await probe.markFinished()
            return data
        })
        model?.check()
        _ = await waitUntil { await probe.started }

        model = nil
        // The abandoned request answers late; there is nobody left to tell.
        await gate.release(with: Data(#"{"latestVersion": "9.9"}"#.utf8))

        let finished = await waitUntil { await probe.finished }
        #expect(finished)
        // Ran to completion against a deallocated model, writing no state.
        let calls = await gate.calls
        #expect(calls == 1)
    }
}

// MARK: - Parameterised fetch outcomes

/// The three ways the injected fetch can end. Each must leave a visible row.
enum UpdateCheckFetchOutcome: Sendable, CaseIterable, CustomStringConvertible {
    case success
    case malformedJSON
    case thrown

    var fetch: UpdateFetch {
        switch self {
        case .success:
            return { Data(#"{"latestVersion": "2.0"}"#.utf8) }
        case .malformedJSON:
            return { Data("<html><body>404</body></html>".utf8) }
        case .thrown:
            return { throw UpdateCheckFailure.badResponse }
        }
    }

    var expected: UpdateCheckState {
        switch self {
        case .success:
            return .available(version: "2.0", releasesURL: UpdateEndpoint.defaultReleasesURL)
        case .malformedJSON, .thrown:
            return .failed
        }
    }

    var description: String {
        switch self {
        case .success: return "success"
        case .malformedJSON: return "malformed JSON"
        case .thrown: return "thrown error"
        }
    }
}

// MARK: - Test doubles

/// Records lifecycle signals from an injected fetch closure.
private actor FetchProbe {
    private(set) var started = false
    private(set) var cancelled = false
    private(set) var finished = false

    func markStarted() { started = true }
    func markCancelled() { cancelled = true }
    func markFinished() { finished = true }
}

/// Polls `condition` for at most ~2 s. Bounded on purpose: a regression fails
/// the test instead of hanging the suite.
private func waitUntil(_ condition: @Sendable () async -> Bool) async -> Bool {
    for _ in 0..<400 {
        if await condition() { return true }
        try? await Task.sleep(for: .milliseconds(5))
    }
    return await condition()
}

/// Counts fetch invocations from a `@Sendable` closure.
private actor FetchCounter {
    private(set) var count = 0
    func record() { count += 1 }
}

/// Hands out scripted responses in order; the last one repeats.
private actor ScriptedFetch {
    private var responses: [Result<Data, Error>]
    private(set) var calls = 0

    init(responses: [Result<Data, Error>]) {
        self.responses = responses
    }

    func next() throws -> Data {
        let response = responses.count > 1 ? responses.removeFirst() : responses[0]
        calls += 1
        return try response.get()
    }
}

/// A fetch that blocks until the test releases it, so the `checking` state can
/// be observed and cancellation exercised without any real networking.
private actor FetchGate {
    private var continuation: CheckedContinuation<Data, Error>?
    private var pending: Data?
    private(set) var calls = 0

    func wait() async throws -> Data {
        calls += 1
        if let pending {
            self.pending = nil
            return pending
        }
        return try await withCheckedThrowingContinuation { continuation in
            self.continuation = continuation
        }
    }

    func release(with data: Data) {
        if let continuation {
            self.continuation = nil
            continuation.resume(returning: data)
        } else {
            pending = data
        }
    }
}
