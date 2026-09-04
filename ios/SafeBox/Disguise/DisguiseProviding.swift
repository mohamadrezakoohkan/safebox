import SwiftUI

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

    // MARK: - Cover identity (§9a)

    /// The app this face appears to be on the home screen — "Calculator+",
    /// "Notepad+", "Gallery+". Named on the card; on iOS it is the *icon* only,
    /// because `setAlternateIconName` cannot rename an app.
    var coverIdentityName: String { get }
    /// The asset-catalog alternate app icon for this cover identity, or `nil`
    /// for the primary icon (the calculator). Must be listed in
    /// `ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES` or the set silently
    /// fails at runtime.
    var alternateIconName: String? { get }

    // MARK: - Guide content slot (§1.4)

    var displayName: String { get }
    var tagline: String { get }
    var commitGesture: String { get }
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
