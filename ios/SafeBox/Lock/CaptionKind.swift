import Foundation

/// Semantic caption states (decisions §1.3). The host decides *which* state
/// applies; the face supplies the words, so a PIN pad can say "tap ✓" where the
/// calculator says "press =". The host never carries a literal caption string.
///
/// Names are identical on both platforms (Android `CaptionKind`).
enum CaptionKind: String, CaseIterable, Equatable, Sendable {
    case promptNewSetup = "PROMPT_NEW_SETUP"
    case promptNewChange = "PROMPT_NEW_CHANGE"
    case strengthHint = "STRENGTH_HINT"
    case tooShort = "TOO_SHORT"
    case tooLong = "TOO_LONG"
    case promptConfirmSetup = "PROMPT_CONFIRM_SETUP"
    case promptConfirmChange = "PROMPT_CONFIRM_CHANGE"
    case mismatch = "MISMATCH"
    case trivialWarning = "TRIVIAL_WARNING"
    case promptCurrent = "PROMPT_CURRENT"
    case wrongCode = "WRONG_CODE"

    /// The only kind rendered in `disguise/captionError`.
    var isError: Bool { self == .wrongCode }
}

/// Caption strip content (design §5.5): a primary line plus an optional
/// secondary hint/warning line — both semantic, never strings.
struct LockBanner: Equatable, Sendable {
    var primary: CaptionKind
    var secondary: CaptionKind?

    init(primary: CaptionKind, secondary: CaptionKind? = nil) {
        self.primary = primary
        self.secondary = secondary
    }
}
