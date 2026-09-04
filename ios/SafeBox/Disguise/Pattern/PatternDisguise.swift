import SwiftUI

/// The pattern face's own copy (decisions §7). No string here contains
/// "passcode", "vault", "unlock" or "SafeBox".
enum PatternCopy {
    static let displayName = localizedCopy("pattern_display_name", "Pattern")
    static let coverName = localizedCopy("cover_name_gallery", "Gallery+")
    static let tagline = localizedCopy("pattern_tagline", "Connect the dots in one stroke. A wrong pattern shakes and clears, like an Android lock.")
    static let commitGesture = localizedCopy("pattern_commit_gesture", "a finger lift")
    static let a11yNote = localizedCopy("pattern_a11y_note", "Not usable with a screen reader")
    static let guidePage3Title = localizedCopy("pattern_guide_page3_title", "Your pattern connects the dots")
    static let guidePage3Body = localizedCopy("pattern_guide_page3_body", "Draw one stroke through 4 to 9 dots — each dot once, and the path matters. Try one here. This is just practice; nothing is saved.")
    static let guideTry = localizedCopy("pattern_guide_try", "Connect at least 4 dots")
    static let guideOk = localizedCopy("pattern_guide_ok", "That would work — more dots and turns make it stronger")
    static let guidePage4Title = localizedCopy("pattern_guide_page4_title", "Lift your finger to enter")
    static let guidePage4Body = localizedCopy("pattern_guide_page4_body", "To unlock, draw your pattern and lift your finger. A wrong pattern shakes and clears — anyone can see it's a lock screen, but not what it protects.")

    static let faceTitle = localizedCopy("pattern_face_title", "Draw your pattern")
    static let promptNew = localizedCopy("pattern_prompt_new", "Choose a pattern: connect at least 4 dots, then lift your finger")
    static let promptNewChange = localizedCopy("pattern_prompt_new_change", "Draw your new pattern, then lift your finger")
    static let hint = localizedCopy("pattern_hint", "Best: 6 or more dots with a turn or two — not a straight line or a letter.")
    static let tooShort = localizedCopy("pattern_too_short", "Too short — connect at least 4 dots")
    static let promptConfirm = localizedCopy("pattern_prompt_confirm", "Draw the same pattern again")
    static let promptConfirmChange = localizedCopy("pattern_prompt_confirm_change", "Draw the new pattern again")
    static let mismatch = localizedCopy("pattern_mismatch", "Patterns didn't match — start again")
    static let promptCurrent = localizedCopy("pattern_prompt_current", "Draw your current pattern")
    static let wrongCode = localizedCopy("pattern_wrong_code", "Wrong pattern — try again")

    /// `TOO_LONG` and `TRIVIAL_WARNING` are unreachable here (9 nodes max, no
    /// repeats), so they map defensively to the current mode's prompt rather
    /// than to a string that would be a lie.
    static func text(for kind: CaptionKind, mode: DisguiseMode) -> String {
        switch kind {
        case .promptNewSetup: promptNew
        case .promptNewChange: promptNewChange
        case .strengthHint: hint
        case .tooShort: tooShort
        case .promptConfirmSetup: promptConfirm
        case .promptConfirmChange: promptConfirmChange
        case .mismatch: mismatch
        case .promptCurrent: promptCurrent
        case .wrongCode: wrongCode
        case .tooLong, .trivialWarning: modePrompt(mode)
        }
    }

    static func modePrompt(_ mode: DisguiseMode) -> String {
        switch mode {
        case .captureNew: promptNew
        case .confirmNew: promptConfirm
        case .verifyCurrent, .disguise: promptCurrent
        }
    }
}

/// Connect the dots in one stroke. Overt: a wrong pattern shakes and clears.
@MainActor
struct PatternDisguise: DisguiseProviding {
    let id = "pattern"
    let isCovert = false

    let alphabet = AlphabetDescriptor(
        tokenSetId: "pattern",
        alphabetVersion: 1,
        tokens: PatternGeometry.tokens
    )

    /// A locked gallery is as ordinary as a locked notes app, and a pattern is
    /// exactly how one locks (§9a).
    var coverIdentityName: String { PatternCopy.coverName }
    var alternateIconName: String? { "AppIconGallery" }

    var displayName: String { PatternCopy.displayName }
    var tagline: String { PatternCopy.tagline }
    var commitGesture: String { PatternCopy.commitGesture }
    var page3Title: String { PatternCopy.guidePage3Title }
    var page3Body: String { PatternCopy.guidePage3Body }
    var page3Try: String { PatternCopy.guideTry }
    var page3Ok: String { PatternCopy.guideOk }
    var page4Title: String { PatternCopy.guidePage4Title }
    var page4Body: String { PatternCopy.guidePage4Body }
    var a11yNote: String? { PatternCopy.a11yNote }

    func makePlayground(onCountChanged: @escaping (Int) -> Void) -> AnyView {
        AnyView(PatternPlayground(onCountChanged: onCountChanged))
    }

    func makeCommitHero() -> AnyView {
        AnyView(PatternCommitHero())
    }

    func makeSurface(mode: DisguiseMode,
                     caption: LockBanner?,
                     failedAttemptCount: Int,
                     events: @escaping (DisguiseEvent) -> Void) -> AnyView {
        AnyView(PatternSurface(mode: mode,
                               caption: caption,
                               failedAttemptCount: failedAttemptCount,
                               events: events))
    }

    func makeCoverFace() -> AnyView {
        AnyView(PatternCoverFace())
    }
}

// MARK: - Guide page 3 playground

/// A compact drawable 3×3 grid. Nothing leaves this view but a count.
private struct PatternPlayground: View {
    let onCountChanged: (Int) -> Void

    @Environment(\.colorScheme) private var colorScheme
    @State private var stroke = PatternStroke()
    @State private var drawing = false

    private let side: CGFloat = 180

    var body: some View {
        let theme = DisguiseTheme.theme(for: colorScheme)
        let cell = side / CGFloat(PatternGeometry.side)
        VStack(spacing: 0) {
            ZStack {
                Canvas { context, _ in
                    var path = Path()
                    for (offset, index) in stroke.selected.enumerated() {
                        let point = PatternGeometry.center(of: index, cell: cell)
                        if offset == 0 { path.move(to: point) } else { path.addLine(to: point) }
                    }
                    context.stroke(path, with: .color(theme.keyOp.opacity(PatternGeometry.lineOpacity)),
                                   style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
                }
                ForEach(0..<PatternGeometry.nodeCount, id: \.self) { index in
                    let isSelected = stroke.selected.contains(index)
                    Circle()
                        .fill(isSelected ? theme.keyOp : theme.keyFn)
                        .frame(width: isSelected ? 20 : 13, height: isSelected ? 20 : 13)
                        .position(PatternGeometry.center(of: index, cell: cell))
                }
            }
            .frame(width: side, height: side)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if !drawing {
                            drawing = true
                            stroke.reset()
                            onCountChanged(0)
                        }
                        guard let node = PatternGeometry.node(at: value.location, cell: cell) else { return }
                        let entered = stroke.enter(node: node)
                        if !entered.isEmpty {
                            KeyHaptics.fire()
                            onCountChanged(stroke.selected.count)
                        }
                    }
                    .onEnded { _ in drawing = false }
            )
            .accessibilityHidden(true)

            Button(GuideCopy.page3Clear) {
                withAnimation { stroke.reset() }
                onCountChanged(0)
            }
            .font(.system(size: 13))
            .foregroundStyle(theme.caption)
            .padding(.top, 10)
        }
    }
}

// MARK: - Guide page 4 hero

/// A looping finger path over a 3×3 grid, ending in a lift.
private struct PatternCommitHero: View {
    @Environment(\.colorScheme) private var colorScheme
    @State private var progress = 0

    private let path = [0, 3, 6, 7, 4, 2]
    private let side: CGFloat = 132

    var body: some View {
        let theme = DisguiseTheme.theme(for: colorScheme)
        let cell = side / CGFloat(PatternGeometry.side)
        let shown = Array(path.prefix(progress))
        return ZStack {
            Canvas { context, _ in
                var line = Path()
                for (offset, index) in shown.enumerated() {
                    let point = PatternGeometry.center(of: index, cell: cell)
                    if offset == 0 { line.move(to: point) } else { line.addLine(to: point) }
                }
                context.stroke(line, with: .color(theme.keyOp.opacity(PatternGeometry.lineOpacity)),
                               style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
            }
            ForEach(0..<PatternGeometry.nodeCount, id: \.self) { index in
                let isSelected = shown.contains(index)
                Circle()
                    .fill(isSelected ? theme.keyOp : theme.keyFn)
                    .frame(width: isSelected ? 16 : 11, height: isSelected ? 16 : 11)
                    .position(PatternGeometry.center(of: index, cell: cell))
            }
        }
        .frame(width: side, height: side)
        .animation(.easeInOut(duration: 0.25), value: progress)
        .accessibilityHidden(true)
        .task {
            while !Task.isCancelled {
                for step in 1...path.count {
                    progress = step
                    try? await Task.sleep(for: .milliseconds(260))
                }
                // The lift, then a beat before the loop restarts.
                try? await Task.sleep(for: .milliseconds(700))
                progress = 0
                try? await Task.sleep(for: .milliseconds(400))
            }
        }
    }
}
