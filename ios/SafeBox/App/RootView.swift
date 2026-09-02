import SwiftUI

struct RootView: View {
    let container: AppContainer
    @Environment(\.scenePhase) private var scenePhase
    @State private var showCover = false

    private var coordinator: AppLockCoordinator { container.lockCoordinator }

    var body: some View {
        ZStack {
            switch coordinator.state {
            case .firstRunSetup:
                // First run (and post-erase): the guide runs before the
                // calculator ever appears. Only while NO passcode exists — the
                // disguise is never preceded by an explainer once a vault is
                // set up.
                if coordinator.showOnboarding {
                    OnboardingView(onFinish: {
                        coordinator.completeOnboarding()
                        OnboardingSentinel.setComplete()
                    })
                } else {
                    LockCalculatorView(coordinator: coordinator)
                        .id(coordinator.calculatorEpoch)
                }
            case .locked:
                // .id(epoch): every lock transition recreates a pristine
                // calculator (display and recorder buffer cleared).
                LockCalculatorView(coordinator: coordinator)
                    .id(coordinator.calculatorEpoch)
            case .unlocked:
                MainTabView(container: container)
            }
            if showCover {
                CalculatorCoverView()
                    .zIndex(10)
            }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .inactive:
                // Snapshot cover on resign-active (app switcher, notification
                // shade, incoming calls) — independent of the lock decision.
                showCover = true
            case .background:
                showCover = true
                coordinator.sceneDidEnterBackground()
            case .active:
                showCover = false
                coordinator.sceneDidBecomeActive()
            @unknown default:
                break
            }
        }
        .alert(LockCopy.noRecoveryTitle, isPresented: Bindable(coordinator).showNoRecoveryNotice) {
            Button(LockCopy.noRecoveryButton, role: .cancel) {}
        } message: {
            Text(LockCopy.noRecoveryBody)
        }
    }
}
