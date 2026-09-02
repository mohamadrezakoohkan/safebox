import Foundation

/// Keychain items survive app deletion; UserDefaults does not. If the sentinel
/// is absent at launch this is a fresh install (or first launch after a
/// reinstall): wipe all SafeBox Keychain items so a stale hash can never lock
/// the "new" user out, then write the sentinel.
enum InstallSentinel {
    static let key = "installSentinel.v1"

    @MainActor
    static func checkAndWipeIfNeeded(defaults: UserDefaults, passcodeStore: any PasscodeStore) {
        if !defaults.bool(forKey: key) {
            passcodeStore.clear()
            defaults.set(true, forKey: key)
        }
    }
}
