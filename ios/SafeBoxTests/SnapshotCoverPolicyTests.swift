import SwiftUI
import Testing
@testable import SafeBox

/// The snapshot-cover rule (decisions §0: nothing vault-related in the app
/// switcher). Both the in-`RootView` overlay and the window-level
/// `SnapshotCover` read this single mapping, so it is pinned here.
struct SnapshotCoverPolicyTests {
    @Test func activeIsNotCovered() {
        #expect(!SnapshotCoverPolicy.shouldCover(for: .active))
    }

    @Test func inactiveIsCovered() {
        #expect(SnapshotCoverPolicy.shouldCover(for: .inactive))
    }

    @Test func backgroundIsCovered() {
        #expect(SnapshotCoverPolicy.shouldCover(for: .background))
    }

    /// Exhaustive over the known phases: covered exactly when not active.
    @Test func coveredExactlyWhenNotActive() {
        let phases: [ScenePhase] = [.active, .inactive, .background]
        for phase in phases {
            #expect(SnapshotCoverPolicy.shouldCover(for: phase) == (phase != .active))
        }
    }
}
