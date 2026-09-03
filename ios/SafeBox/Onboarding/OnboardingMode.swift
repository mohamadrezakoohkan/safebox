import Foundation

/// The context the guide is shown in (decisions §5). The view renders the same
/// four pages either way; the mode decides the button labels, whether the
/// top-right button survives the last page, and — the part that matters —
/// whether finishing may touch first-run state at all.
enum OnboardingMode: Equatable, Sendable {
    /// Fresh install / post-erase: the guide precedes the setup calculator.
    /// Finishing (or skipping) records "onboarding complete".
    case firstRun
    /// Re-launched from Settings inside the unlocked vault. Finishing or
    /// dismissing only returns to Settings; neither the persisted flag nor the
    /// coordinator's in-memory flag is ever written.
    case revisit

    /// Whether finishing the guide records onboarding as complete. Only the
    /// first run does — a revisit happens after the flag is already set and
    /// must leave it (and `AppLockCoordinator.completeOnboarding()`) alone.
    var recordsCompletion: Bool {
        switch self {
        case .firstRun: true
        case .revisit: false
        }
    }

    /// Whether the top-right button stays on the final page. The first run
    /// hides Skip there so the CTA ("Set my code") is the only way forward; a
    /// revisit keeps Done on every page because the guide is dismissible at any
    /// point.
    var showsTrailingButtonOnLastPage: Bool {
        switch self {
        case .firstRun: false
        case .revisit: true
        }
    }
}
