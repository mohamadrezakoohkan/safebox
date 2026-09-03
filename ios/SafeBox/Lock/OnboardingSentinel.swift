import Foundation

/// Persists whether the first-run guide was finished (or skipped). Lives in
/// UserDefaults (dies with the app on uninstall, exactly like the guide
/// should); erasing the vault clears it so the guide returns with the fresh
/// state.
enum OnboardingSentinel {
    static let key = "onboardingComplete.v1"

    static func isComplete(defaults: UserDefaults = .standard) -> Bool {
        defaults.bool(forKey: key)
    }

    static func setComplete(defaults: UserDefaults = .standard) {
        defaults.set(true, forKey: key)
    }

    /// Records completion only when `mode` says the guide was the first run.
    /// This is the single gate between the guide and the persisted flag: the
    /// revisit launched from Settings passes `.revisit` and nothing is written
    /// (decisions §5).
    static func recordCompletion(for mode: OnboardingMode, defaults: UserDefaults = .standard) {
        guard mode.recordsCompletion else { return }
        setComplete(defaults: defaults)
    }

    static func reset(defaults: UserDefaults = .standard) {
        defaults.removeObject(forKey: key)
    }
}
