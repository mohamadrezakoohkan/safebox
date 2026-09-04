import SwiftUI

/// How credible the face is under the shipped launcher identity ("Calculator+",
/// calculator icon). Disclosed on every carousel/picker card (decisions §6).
enum DisguiseIdentityGrade: Equatable, Sendable {
    /// Matches the app's name and icon.
    case native
    /// Reserved: coherent with the identity without being it. No shipped face
    /// uses it yet; it renders the conservative (incoherent) disclosure line.
    case plausible
    /// Doesn't match the app's name and icon.
    case incoherent

    var disclosure: String {
        switch self {
        case .native: VaultCopy.disguiseGradeNative
        case .plausible, .incoherent: VaultCopy.disguiseGradeIncoherent
        }
    }
}

/// One compiled-in lock face. Faces are renderers and input devices only —
/// every security decision lives in the host (skeleton §3.3).
///
/// The guide slot (display name … a11yNote, `makePlayground`, `makeCommitHero`)
/// is decisions §1.4; the card thumbnail is deliberately NOT part of it — it is
/// `makeCoverFace()` rendered at scale (§6).
@MainActor
protocol DisguiseProviding {
    /// Registry id; equal to `alphabet.tokenSetId` for every shipped face.
    var id: String { get }
    /// `false` when a non-match shakes and clears (§1.1).
    var isCovert: Bool { get }
    var alphabet: AlphabetDescriptor { get }

    // MARK: - Guide content slot (§1.4)

    var displayName: String { get }
    var tagline: String { get }
    var commitGesture: String { get }
    var identityGrade: DisguiseIdentityGrade { get }
    var page3Title: String { get }
    var page3Body: String { get }
    var page3Try: String { get }
    var page3Ok: String { get }
    var page4Title: String { get }
    var page4Body: String { get }
    /// Pattern only: the screen-reader disclosure.
    var a11yNote: String? { get }

    /// Small interactive demo for guide page 3. Reports only a tap/node count —
    /// never what was tapped.
    func makePlayground(onCountChanged: @escaping (Int) -> Void) -> AnyView
    /// Looping, non-interactive illustration of the commit gesture (page 4).
    func makeCommitHero() -> AnyView

    // MARK: - Surfaces

    func makeSurface(mode: DisguiseMode,
                     caption: LockBanner?,
                     failedAttemptCount: Int,
                     events: @escaping (DisguiseEvent) -> Void) -> AnyView
    /// Static resting face: the snapshot cover and the carousel thumbnail.
    func makeCoverFace() -> AnyView
}

extension DisguiseProviding {
    var a11yNote: String? { nil }
}
