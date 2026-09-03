import Testing
@testable import SafeBox

/// Pins the shared unlock-reveal spec (decisions §1 / §11) and the one rule the
/// root view must never break: only a change INTO `.unlocked` animates —
/// locking, setup-phase changes, erase-everything and epoch bumps are cuts.
struct UnlockRevealTests {
    private static let setupEntry = LockState.firstRunSetup(.enterNew)
    private static let setupConfirm = LockState.firstRunSetup(.confirm(pending: [.d1, .d2, .add, .d4]))
    private static let everyState: [LockState] = [setupEntry, setupConfirm, .locked, .unlocked]

    // MARK: - Shared constants

    @Test func constantsMatchTheSharedSpec() {
        #expect(UnlockReveal.durationMs == 260)
        #expect(abs(UnlockReveal.duration - 0.26) < 0.000_001)
        #expect(UnlockReveal.initialScale == 0.92)
        #expect(UnlockReveal.curveX1 == 0.05)
        #expect(UnlockReveal.curveY1 == 0.7)
        #expect(UnlockReveal.curveX2 == 0.1)
        #expect(UnlockReveal.curveY2 == 1.0)
    }

    // MARK: - Which changes animate

    @Test func lockedToUnlockedIsTheZoomReveal() {
        #expect(UnlockReveal.kind(from: .locked, to: .unlocked, reduceMotion: false) == .zoomReveal)
    }

    @Test func firstRunSetupToUnlockedUsesTheSameReveal() {
        #expect(UnlockReveal.kind(from: Self.setupEntry, to: .unlocked, reduceMotion: false) == .zoomReveal)
        #expect(UnlockReveal.kind(from: Self.setupConfirm, to: .unlocked, reduceMotion: false) == .zoomReveal)
    }

    @Test func reduceMotionFallsBackToAnOpacityOnlyCrossfade() {
        for from in [Self.setupEntry, Self.setupConfirm, .locked] {
            #expect(UnlockReveal.kind(from: from, to: .unlocked, reduceMotion: true) == .crossfade)
        }
    }

    @Test func lockingIsAlwaysAnInstantCut() {
        for reduceMotion in [false, true] {
            // Manual lock, background lock — any lock.
            #expect(UnlockReveal.kind(from: .unlocked, to: .locked, reduceMotion: reduceMotion) == .cut)
            // Erase everything: unlocked straight back to setup.
            #expect(UnlockReveal.kind(from: .unlocked, to: Self.setupEntry, reduceMotion: reduceMotion) == .cut)
        }
    }

    @Test func setupAndLockedChangesThatDoNotUnlockAreCuts() {
        for reduceMotion in [false, true] {
            #expect(UnlockReveal.kind(from: Self.setupEntry, to: Self.setupConfirm, reduceMotion: reduceMotion) == .cut)
            #expect(UnlockReveal.kind(from: Self.setupConfirm, to: Self.setupEntry, reduceMotion: reduceMotion) == .cut)
            #expect(UnlockReveal.kind(from: Self.setupEntry, to: .locked, reduceMotion: reduceMotion) == .cut)
            #expect(UnlockReveal.kind(from: .locked, to: Self.setupEntry, reduceMotion: reduceMotion) == .cut)
        }
    }

    @Test func unchangedStateIsACut() {
        // An epoch bump re-renders the same state; recreating the calculator
        // must never read as a transition.
        for state in Self.everyState {
            for reduceMotion in [false, true] {
                #expect(UnlockReveal.kind(from: state, to: state, reduceMotion: reduceMotion) == .cut)
            }
        }
    }

    @Test func onlyChangesIntoUnlockedAnimate() {
        for from in Self.everyState {
            for to in Self.everyState {
                for reduceMotion in [false, true] {
                    let kind = UnlockReveal.kind(from: from, to: to, reduceMotion: reduceMotion)
                    let shouldAnimate = to == .unlocked && from != .unlocked
                    #expect(kind.animates == shouldAnimate,
                            "\(from) → \(to) with reduceMotion=\(reduceMotion) resolved to \(kind)")
                }
            }
        }
    }

    // MARK: - Shape of each kind

    @Test func kindAnimatesUnlessCut() {
        #expect(UnlockReveal.Kind.zoomReveal.animates)
        #expect(UnlockReveal.Kind.crossfade.animates)
        #expect(!UnlockReveal.Kind.cut.animates)
    }

    @Test func onlyTheFullRevealScalesTheVault() {
        #expect(UnlockReveal.vaultInitialScale(for: .zoomReveal) == UnlockReveal.initialScale)
        #expect(UnlockReveal.vaultInitialScale(for: .crossfade) == nil)
        #expect(UnlockReveal.vaultInitialScale(for: .cut) == nil)
    }
}
