import Foundation
import Observation

// MARK: - Version comparison

/// Dotted-numeric version comparison (decisions §13), shared rule with Android.
///
/// Components are compared left to right and missing components count as `0`,
/// so `1.0` and `1.0.0` are equal — iOS ships `CFBundleShortVersionString`
/// `1.0` while Android ships `versionName` `1.0.0`. Anything that is not a
/// dotted run of digits is *unparseable* and compares as "not newer", so a
/// malformed `version.json` can never nag the user into a false update.
enum AppVersion {
    /// The running app's marketing version (`1.0`), never the build number.
    static var current: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    }

    /// The running build number, shown next to `current` in Settings.
    static var currentBuild: String {
        Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
    }

    /// `"1.02.3"` → `[1, 2, 3]`; `nil` for anything not a dotted run of
    /// digits (empty, `"abc"`, `"1.x"`, `"1..2"`, `"1.0-beta"`, overflow).
    /// Leading zeros are allowed and carry no meaning: `1.01` == `1.1`.
    static func components(_ version: String) -> [Int]? {
        let raw = version.split(separator: ".", omittingEmptySubsequences: false)
        guard !raw.isEmpty else { return nil }
        var parsed: [Int] = []
        parsed.reserveCapacity(raw.count)
        for part in raw {
            guard !part.isEmpty,
                  part.allSatisfy(\.isASCII),
                  part.allSatisfy({ $0.isNumber }),
                  let value = Int(part) else { return nil }
            parsed.append(value)
        }
        return parsed
    }

    /// True only when `latest` is strictly greater than `current`. Equal
    /// versions, older versions and unparseable input all return false.
    static func isNewer(_ latest: String, than current: String) -> Bool {
        guard let lhs = components(latest), let rhs = components(current) else { return false }
        let width = max(lhs.count, rhs.count)
        for index in 0..<width {
            let l = index < lhs.count ? lhs[index] : 0
            let r = index < rhs.count ? rhs[index] : 0
            if l != r { return l > r }
        }
        return false
    }
}

// MARK: - Manifest

/// `version.json` at the repo root. Publishing a new version means editing
/// that one file — no app release is needed to change the destination.
struct UpdateManifest: Decodable, Equatable, Sendable {
    let latestVersion: String
    /// Raw string from the file; `releasesURL` sanitizes it.
    let releasesUrl: String?

    /// Where the "available" row sends the user. Falls back to the repo's
    /// Releases page when the field is missing, empty, malformed, or not
    /// `https` — the manifest is remote input, so it may not choose the
    /// scheme (no `file:`, no custom URL scheme).
    var releasesURL: URL {
        guard let raw = releasesUrl,
              let url = URL(string: raw.trimmingCharacters(in: .whitespacesAndNewlines)),
              url.scheme?.lowercased() == "https",
              url.host != nil else {
            return UpdateEndpoint.defaultReleasesURL
        }
        return url
    }
}

// MARK: - State

/// What the "Check for updates" row shows and does. `idle` has no subtitle.
enum UpdateCheckState: Equatable, Sendable {
    case idle
    case checking
    case upToDate
    case available(version: String, releasesURL: URL)
    case failed

    /// The row's subtitle; `nil` in `idle` (the row carries only its title
    /// until the user has asked something).
    var subtitle: String? {
        switch self {
        case .idle:
            return nil
        case .checking:
            return VaultCopy.settingsUpdateChecking
        case .upToDate:
            return VaultCopy.settingsUpdateUpToDate
        case let .available(version, _):
            return VaultCopy.settingsUpdateAvailable(version)
        case .failed:
            return VaultCopy.settingsUpdateFailed
        }
    }

    /// Non-nil only when an update is available: tapping the row then opens
    /// the Releases page instead of re-running the check.
    var openURL: URL? {
        if case let .available(_, releasesURL) = self { return releasesURL }
        return nil
    }
}

// MARK: - Endpoint / network posture

/// Everything the one and only network request in the app touches
/// (decisions §13). The posture is deliberate and tested:
///
/// - **Manual only.** Nothing here runs at launch, on unlock or on a timer;
///   the sole caller is the user tapping the row.
/// - Bare `GET`: no query string, no custom headers, no cookies, no
///   credentials. The response is the only data that crosses the boundary.
/// - **No on-disk HTTP cache.** The configuration is `ephemeral` with
///   `urlCache` explicitly cleared, because a cache entry would leave the URL
///   — which names the repo — inside the app container: a forensic tell.
/// - 10 s timeout, and the in-flight task dies with the vault on lock.
/// - Neither the URL nor the response body is ever logged.
enum UpdateEndpoint {
    static let sourceURL = URL(string: "https://github.com/mohamadrezakoohkan/safebox")!
    static let manifestURL = URL(string: "https://raw.githubusercontent.com/mohamadrezakoohkan/safebox/main/version.json")!
    static let defaultReleasesURL = URL(string: "https://github.com/mohamadrezakoohkan/safebox/releases/latest")!

    /// Decisions §13: 10 s, so a captive portal or a dead link fails visibly
    /// instead of spinning.
    static let timeout: TimeInterval = 10

    /// A tiny manifest; anything larger is not our file. Caps the read so a
    /// hostile response cannot balloon memory.
    static let maxResponseBytes = 64 * 1024

    /// Ephemeral: no `urlCache`, no cookie storage, no credential storage, so
    /// nothing about the request or its response is written to the container.
    static func makeSessionConfiguration() -> URLSessionConfiguration {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.httpCookieStorage = nil
        configuration.httpCookieAcceptPolicy = .never
        configuration.httpShouldSetCookies = false
        configuration.urlCredentialStorage = nil
        configuration.httpAdditionalHeaders = nil
        configuration.waitsForConnectivity = false
        configuration.timeoutIntervalForRequest = timeout
        configuration.timeoutIntervalForResource = timeout
        return configuration
    }

    /// A bare GET for the manifest: no query, no headers, no cookies.
    static func makeRequest() -> URLRequest {
        var request = URLRequest(url: manifestURL,
                                 cachePolicy: .reloadIgnoringLocalCacheData,
                                 timeoutInterval: timeout)
        request.httpMethod = "GET"
        request.httpBody = nil
        request.httpShouldHandleCookies = false
        request.allHTTPHeaderFields = nil
        return request
    }

    /// The live fetch. Deliberately the only place a `URLSession` is created
    /// in the app; the session is torn down with the call so no connection
    /// outlives the check.
    static let liveFetch: UpdateFetch = {
        let session = URLSession(configuration: makeSessionConfiguration())
        defer { session.invalidateAndCancel() }
        let (data, response) = try await session.data(for: makeRequest())
        guard let http = response as? HTTPURLResponse,
              (200..<300).contains(http.statusCode),
              data.count <= maxResponseBytes else {
            // No URL, status code or body in the error: nothing to leak if it
            // is ever surfaced or logged by accident.
            throw UpdateCheckFailure.badResponse
        }
        return data
    }
}

/// The single opaque failure. It carries nothing describable on purpose.
enum UpdateCheckFailure: Error {
    case badResponse
}

/// Injectable so tests never touch the network.
typealias UpdateFetch = @Sendable () async throws -> Data

// MARK: - Pure resolution

/// The pure half of the check: bytes in, row state out. No networking, no
/// actor isolation, fully unit-testable.
enum UpdateCheck {
    static func manifest(from data: Data) -> UpdateManifest? {
        try? JSONDecoder().decode(UpdateManifest.self, from: data)
    }

    static func state(for data: Data, currentVersion: String) -> UpdateCheckState {
        guard let manifest = manifest(from: data) else { return .failed }
        guard AppVersion.isNewer(manifest.latestVersion, than: currentVersion) else { return .upToDate }
        return .available(version: manifest.latestVersion, releasesURL: manifest.releasesURL)
    }
}

// MARK: - Row model

/// Drives the "Check for updates" row. Owned by `SettingsScreen`, so it is
/// created when the vault's Settings tab appears and destroyed with the vault
/// on lock; `cancel()` abandons any in-flight request at that point.
@MainActor
@Observable
final class UpdateCheckModel {
    private(set) var state: UpdateCheckState = .idle

    private let currentVersion: String
    private let fetch: UpdateFetch
    private var task: Task<Void, Never>?
    /// Bumped by every `check()` and by `cancel()`, so a superseded or
    /// abandoned request can never write a stale state.
    private var generation = 0

    nonisolated init(currentVersion: String = AppVersion.current,
                     fetch: @escaping UpdateFetch = UpdateEndpoint.liveFetch) {
        self.currentVersion = currentVersion
        self.fetch = fetch
    }

    var isChecking: Bool { state == .checking }

    /// The row's tap action: open the Releases page when an update is
    /// available, otherwise (re-)run the check.
    func rowTapped(open: (URL) -> Void) {
        if let url = state.openURL {
            open(url)
        } else {
            check()
        }
    }

    func check() {
        guard !isChecking else { return }
        generation += 1
        let generation = generation
        state = .checking
        let fetch = fetch
        let currentVersion = currentVersion
        task = Task { [weak self] in
            let resolved: UpdateCheckState
            do {
                let data = try await fetch()
                try Task.checkCancellation()
                resolved = UpdateCheck.state(for: data, currentVersion: currentVersion)
            } catch {
                if Task.isCancelled { return }
                resolved = .failed
            }
            guard let self, generation == self.generation else { return }
            self.state = resolved
        }
    }

    /// Called when the Settings screen goes away (tab change, lock teardown).
    /// The request is abandoned and the row returns to `idle`, so a later
    /// visit can check again instead of being stuck on "Checking…".
    func cancel() {
        task?.cancel()
        task = nil
        generation += 1
        if state == .checking { state = .idle }
    }

    /// Test seam: waits for the in-flight check to finish.
    func waitForCurrentCheck() async {
        await task?.value
    }
}
