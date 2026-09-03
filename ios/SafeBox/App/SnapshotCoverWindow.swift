import SwiftUI
import UIKit

/// The one rule behind the app-switcher snapshot cover: covered whenever the
/// scene is not active. Pure so it can be pinned by a unit test; both the
/// in-`RootView` overlay and the window-level cover read it.
enum SnapshotCoverPolicy {
    /// `.active` → not covered; `.inactive` / `.background` → covered. Any
    /// phase this build does not know about is treated as "not active" so
    /// the fail-safe direction is always toward hiding vault content.
    static func shouldCover(for phase: ScenePhase) -> Bool {
        switch phase {
        case .active:
            return false
        case .inactive, .background:
            return true
        @unknown default:
            return true
        }
    }
}

/// Window-level snapshot cover (decisions §0: "nothing vault-related in the
/// app switcher / recents").
///
/// `RootView` already overlays `CalculatorCoverView` inside its own ZStack,
/// but SwiftUI `.sheet`s and `.alert`s are UIKit-presented ABOVE the root
/// view controller, so with any sheet open (guide revisit, change passcode,
/// contact create/edit, photo info) the snapshot would show the sheet's vault
/// content. This helper owns a second `UIWindow` in the same scene at
/// `.alert + 1`, hosting the same `CalculatorCoverView`, so the cover sits
/// above every presented sheet and alert regardless of which screen opened
/// them. The in-ZStack overlay stays as harmless redundancy.
///
/// Show/hide is a plain `isHidden` flip — no animation — driven from the
/// same scenePhase transitions `RootView` already handles.
@MainActor
final class SnapshotCover {
    static let shared = SnapshotCover()

    private var window: UIWindow?
    /// Pinned by `SnapshotCoverSceneHook` once the root view is in a window.
    /// Falls back to the app's connected scenes (single-scene app).
    private weak var scene: UIWindowScene?

    private init() {}

    /// Remembers the scene the root view lives in. Idempotent.
    func attach(to scene: UIWindowScene) {
        self.scene = scene
    }

    /// Installs (once) and shows the cover, or hides it. Immediate, no
    /// animation. Does nothing if no `UIWindowScene` can be resolved — the
    /// in-`RootView` overlay is still there in that case.
    func setCovered(_ covered: Bool) {
        if covered {
            guard let scene = resolveScene() else { return }
            let window = window(in: scene)
            window.isHidden = false
        } else {
            window?.isHidden = true
        }
    }

    // MARK: - Internals

    private func resolveScene() -> UIWindowScene? {
        if let scene { return scene }
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        // Prefer the scene that is (about to be) in the foreground; the app
        // is single-scene, so this is normally the only element.
        let foreground = scenes.first {
            $0.activationState == .foregroundActive || $0.activationState == .foregroundInactive
        }
        let resolved = foreground ?? scenes.first
        scene = resolved
        return resolved
    }

    private func window(in scene: UIWindowScene) -> UIWindow {
        if let window, window.windowScene === scene {
            return window
        }
        let cover = UIWindow(windowScene: scene)
        // Above alerts (and therefore above every sheet, which sits below
        // the alert level). Never made key: the app window keeps focus.
        cover.windowLevel = UIWindow.Level(rawValue: UIWindow.Level.alert.rawValue + 1)
        cover.isUserInteractionEnabled = false
        cover.backgroundColor = Self.coverBackground
        cover.accessibilityViewIsModal = false

        let host = UIHostingController(rootView: CalculatorCoverView())
        host.view.backgroundColor = Self.coverBackground
        host.view.isUserInteractionEnabled = false
        cover.rootViewController = host

        window = cover
        return cover
    }

    /// Same graphite/grey as `CalculatorSurface`'s background, resolved per
    /// trait collection so the first frame matches before SwiftUI lays out.
    private static let coverBackground = UIColor { traits in
        let theme: DisguiseTheme = traits.userInterfaceStyle == .dark ? .dark : .light
        return UIColor(theme.background)
    }
}

/// Zero-cost hook that pins the root view's `UIWindowScene` for
/// `SnapshotCover` as soon as the view hierarchy is in a window. Place it in
/// a `.background` of the root; it draws nothing and takes no touches.
struct SnapshotCoverSceneHook: UIViewRepresentable {
    func makeUIView(context: Context) -> SceneReportingView {
        let view = SceneReportingView()
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
        return view
    }

    func updateUIView(_ uiView: SceneReportingView, context: Context) {}

    final class SceneReportingView: UIView {
        override func didMoveToWindow() {
            super.didMoveToWindow()
            if let scene = window?.windowScene {
                SnapshotCover.shared.attach(to: scene)
            }
        }
    }
}
