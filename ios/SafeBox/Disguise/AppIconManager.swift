import UIKit

/// The one thing `AppIconManager` needs from `UIApplication`, extracted so the
/// no-op rule below can be tested without a live application object.
@MainActor
protocol AlternateIconControlling {
    /// False on devices/configurations that cannot swap icons at all.
    var supportsAlternateIcons: Bool { get }
    /// The icon in force right now; `nil` means the primary icon.
    var currentAlternateIconName: String? { get }
    /// Fire-and-forget: the result is deliberately unobservable (see below).
    func setAlternateIconName(_ name: String?)
}

/// The live implementation. The completion handler is supplied and ignored on
/// purpose: passing `nil` makes UIKit log the failure itself, and nothing about
/// an icon change may reach a log (§0).
@MainActor
struct SystemAlternateIcons: AlternateIconControlling {
    var supportsAlternateIcons: Bool { UIApplication.shared.supportsAlternateIcons }
    var currentAlternateIconName: String? { UIApplication.shared.alternateIconName }

    func setAlternateIconName(_ name: String?) {
        UIApplication.shared.setAlternateIconName(name) { _ in }
    }
}

/// Cover identities (decisions §9a): the home-screen icon follows the lock
/// face. On iOS the icon is all that changes — `setAlternateIconName` cannot
/// rename an app — and iOS shows an unsuppressable system alert on every real
/// change, which is why `apply` is scrupulous about not making one.
///
/// Three rules, all of them load-bearing:
///
/// - **No-op when the icon already matches.** Re-setting the current icon still
///   pops the system alert, so a redundant call would nag the user for nothing
///   — on every unlock, in the worst case.
/// - **Swallow every failure.** A missing asset, a rejected set, an
///   unsupported device: none of it may surface, block a disguise switch, or be
///   logged. By the time this runs the envelope is already rewritten; a stale
///   icon is cosmetic.
/// - **Never log.** Not the face, not the icon name, not the outcome. The
///   home-screen icon is public, but which face is enrolled is lock state.
@MainActor
struct AppIconManager {
    private let icons: any AlternateIconControlling

    init(icons: any AlternateIconControlling = SystemAlternateIcons()) {
        self.icons = icons
    }

    /// Apply the cover identity of `disguise`. Call sites: first-run setup
    /// completion, a disguise-switch commit **after** the envelope write
    /// succeeded, and erase-everything (which restores the calculator).
    func apply(_ disguise: any DisguiseProviding) {
        apply(alternateIconName: disguise.alternateIconName)
    }

    /// `nil` restores the primary (calculator) icon.
    func apply(alternateIconName: String?) {
        guard icons.supportsAlternateIcons else { return }
        guard icons.currentAlternateIconName != alternateIconName else { return }
        icons.setAlternateIconName(alternateIconName)
    }
}
