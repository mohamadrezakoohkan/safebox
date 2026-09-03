import SwiftUI

struct RootView: View {
    let container: AppContainer
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var showCover = false

    /// View-side mirror of `coordinator.state` (decisions §1). The coordinator
    /// stays animation-free; this copy is what the switch renders. It is
    /// updated inside `withAnimation` only for a change INTO `.unlocked` and
    /// inside a `disablesAnimations` transaction for everything else, so lock,
    /// setup-phase changes and epoch bumps can never pick up a transition.
    @State private var displayedState: LockState
    /// The kind chosen for the change currently on screen; drives the vault's
    /// insertion transition (zoom vs. opacity-only vs. none).
    @State private var revealKind: UnlockReveal.Kind = .cut
    /// True from the first reveal frame until the reveal completes. Gates the
    /// one-time no-recovery notice so it never pops over the animation.
    @State private var isRevealing = false
    /// Identifies the reveal in flight so a stale completion cannot clear a
    /// newer one.
    @State private var revealToken = 0

    private var coordinator: AppLockCoordinator { container.lockCoordinator }

    init(container: AppContainer) {
        self.container = container
        _displayedState = State(initialValue: container.lockCoordinator.state)
    }

    var body: some View {
        ZStack {
            // Animated container: only the lock-state switch lives in here. The
            // snapshot cover below is a sibling on purpose — it must never be
            // part of the reveal.
            ZStack {
                switch displayedState {
                case .firstRunSetup:
                    // First run (and post-erase): the guide runs before the
                    // calculator ever appears. Only while NO passcode exists — the
                    // disguise is never preceded by an explainer once a vault is
                    // set up.
                    if coordinator.showOnboarding {
                        OnboardingView(mode: .firstRun, onFinish: {
                            coordinator.completeOnboarding()
                            OnboardingSentinel.recordCompletion(for: .firstRun)
                        })
                    } else {
                        LockCalculatorView(coordinator: coordinator)
                            .id(coordinator.calculatorEpoch)
                            .transition(UnlockReveal.calculatorTransition)
                    }
                case .locked:
                    // .id(epoch): every lock transition recreates a pristine
                    // calculator (display and recorder buffer cleared). The epoch
                    // change arrives in a plain transaction, so the swap is a cut.
                    LockCalculatorView(coordinator: coordinator)
                        .id(coordinator.calculatorEpoch)
                        .transition(UnlockReveal.calculatorTransition)
                case .unlocked:
                    // zIndex(1): the vault reveals OVER the fading calculator.
                    MainTabView(container: container)
                        .transition(UnlockReveal.vaultTransition(for: revealKind))
                        .zIndex(1)
                }
            }
            if showCover {
                CalculatorCoverView()
                    .zIndex(10)
            }
        }
        // Pins the UIWindowScene for the window-level snapshot cover.
        .background(SnapshotCoverSceneHook())
        .onChange(of: coordinator.state) { from, to in
            display(to, from: from)
        }
        .onChange(of: scenePhase) { _, phase in
            // Snapshot cover on resign-active (app switcher, notification
            // shade, incoming calls) — independent of the lock decision.
            // Two layers, one rule: the in-ZStack overlay above, and the
            // window-level cover that also sits above every presented
            // sheet/alert (which UIKit hosts above this view hierarchy).
            let covered = SnapshotCoverPolicy.shouldCover(for: phase)
            showCover = covered
            SnapshotCover.shared.setCovered(covered)
            switch phase {
            case .inactive:
                break
            case .background:
                coordinator.sceneDidEnterBackground()
            case .active:
                coordinator.sceneDidBecomeActive()
            @unknown default:
                break
            }
        }
        .alert(LockCopy.noRecoveryTitle, isPresented: noRecoveryNoticePresented) {
            Button(LockCopy.noRecoveryButton, role: .cancel) {}
        } message: {
            Text(LockCopy.noRecoveryBody)
        }
    }

    // MARK: - Lock-state mirroring

    /// Puts `to` on screen. The unlock reveal is the ONLY animated path; every
    /// other change is committed with animations disabled so nothing in the
    /// switch — including the `.id(epoch)` recreation — can animate.
    private func display(_ to: LockState, from: LockState) {
        let kind = UnlockReveal.kind(from: from, to: to, reduceMotion: reduceMotion)
        if kind.animates {
            revealToken += 1
            let token = revealToken
            isRevealing = true
            withAnimation(UnlockReveal.animation, completionCriteria: .logicallyComplete) {
                revealKind = kind
                displayedState = to
            } completion: {
                if revealToken == token {
                    isRevealing = false
                }
            }
        } else {
            var cut = Transaction()
            cut.disablesAnimations = true
            withTransaction(cut) {
                revealKind = .cut
                displayedState = to
                isRevealing = false
            }
        }
    }

    // MARK: - One-time no-recovery notice

    /// The coordinator's flag stays the source of truth; presentation is
    /// derived so the alert (a) waits for the reveal to complete and (b) is
    /// only ever shown over the unlocked vault — never over the calculator if
    /// the app locks before the user acknowledges it. Only a user dismissal
    /// (while unlocked) clears the flag, so an unacknowledged notice returns
    /// after the next unlock.
    private var noRecoveryNoticePresented: Binding<Bool> {
        Binding(
            get: { coordinator.showNoRecoveryNotice && displayedState == .unlocked && !isRevealing },
            set: { presented in
                if !presented, displayedState == .unlocked {
                    coordinator.showNoRecoveryNotice = false
                }
            }
        )
    }
}
