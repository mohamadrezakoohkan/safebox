import SwiftUI

/// The one place the unlock reveal is specified (iteration-2 decisions §1 and
/// §11), value-for-value the same as Android's `object UnlockReveal`.
///
/// Treatment: "zoom-in reveal" (shared-axis Z). The vault fades in while
/// scaling `initialScale → 1.0`; the calculator fades out in place (no scale).
/// Both run concurrently over `durationMs` on the emphasized-decelerate curve.
///
/// It plays ONLY when the lock state becomes `.unlocked` — from `.locked` or
/// from first-run setup, which deliberately uses the same reveal. Every other
/// change is an instant cut: manual lock, background lock, setup-phase
/// changes, erase-everything, and every `calculatorEpoch` bump (a recreated
/// calculator must never read as a transition).
///
/// Reduced motion (`accessibilityReduceMotion`) drops the scale and keeps an
/// opacity-only crossfade with the same duration and curve.
enum UnlockReveal {
    /// Shared duration (Android `UNLOCK_REVEAL_DURATION_MS`).
    static let durationMs = 260
    static var duration: TimeInterval { TimeInterval(durationMs) / 1000 }

    /// Scale the vault starts from in the full reveal.
    static let initialScale: CGFloat = 0.92

    /// cubic-bezier(0.05, 0.7, 0.1, 1.0) — Material "emphasized decelerate".
    static let curveX1: Double = 0.05
    static let curveY1: Double = 0.7
    static let curveX2: Double = 0.1
    static let curveY2: Double = 1.0

    /// How one lock-state change is put on screen.
    enum Kind: Equatable, Sendable {
        /// Vault opacity 0→1 and scale `initialScale`→1; calculator opacity 1→0.
        case zoomReveal
        /// Reduced-motion fallback: same timing, opacity only, no scale.
        case crossfade
        /// Instant — no transition of any kind.
        case cut

        var animates: Bool { self != .cut }
    }

    /// The pure decision the root view follows: only a change INTO
    /// `.unlocked` animates; the origin may be `.locked` or any
    /// `.firstRunSetup` phase. Same-state re-renders (epoch bumps) are cuts.
    static func kind(from: LockState, to: LockState, reduceMotion: Bool) -> Kind {
        guard to == .unlocked, from != .unlocked else { return .cut }
        return reduceMotion ? .crossfade : .zoomReveal
    }

    /// The scale the vault starts from for `kind`, or nil when it must not
    /// scale (reduced motion, or no transition at all).
    static func vaultInitialScale(for kind: Kind) -> CGFloat? {
        kind == .zoomReveal ? initialScale : nil
    }

    /// `durationMs` on the shared curve. Used as the ONLY animation the root
    /// view ever attaches to a lock-state change.
    static var animation: Animation {
        .timingCurve(curveX1, curveY1, curveX2, curveY2, duration: duration)
    }

    /// Transition for the vault surface. Only insertion is ever animated —
    /// removal (lock) is a cut and additionally runs with animations disabled.
    static func vaultTransition(for kind: Kind) -> AnyTransition {
        guard kind.animates else { return .identity }
        let fade = AnyTransition.opacity
        let insertion = vaultInitialScale(for: kind).map { fade.combined(with: .scale(scale: $0)) } ?? fade
        return .asymmetric(insertion: insertion, removal: .identity)
    }

    /// Transition for the calculator surface: it fades out in place and never
    /// scales. Insertion (lock) is a cut. Constant on purpose — a removal
    /// transition is captured from the last frame the view was on screen, so
    /// it must not depend on the state being transitioned to.
    static var calculatorTransition: AnyTransition {
        .asymmetric(insertion: .identity, removal: .opacity)
    }
}
