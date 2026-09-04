import SwiftUI

/// The pattern face: caption slot (or static title) → a centered 3×3 grid drawn
/// in one stroke. Overt — a wrong pattern shakes and clears.
///
/// The gesture loop never logs: not a coordinate, not a node index, not a
/// stroke length.
struct PatternSurface: View {
    let mode: DisguiseMode
    let caption: LockBanner?
    let failedAttemptCount: Int
    let events: (DisguiseEvent) -> Void

    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var stroke = PatternStroke()
    @State private var fingerPoint: CGPoint?
    @State private var drawing = false
    @State private var shakeToken = 0
    @State private var showingFailure = false
    @GestureState private var gestureActive = false

    var body: some View {
        GeometryReader { geo in
            let theme = DisguiseTheme.theme(for: colorScheme)
            let margin = NumpadMetrics.sideMargin(width: geo.size.width)
            let available = min(geo.size.width - 2 * margin, geo.size.height)
            let gridSide = max(min(available, PatternGeometry.gridMaxSide), 1)
            VStack(spacing: 0) {
                captionSlot(theme: theme, margin: margin)
                Spacer(minLength: 0)
                grid(theme: theme, side: gridSide)
                    .padding(.top, 32)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background { DisguiseTheme.theme(for: colorScheme).background.ignoresSafeArea() }
        .onChange(of: failedAttemptCount) { old, new in
            guard new > old else { return }
            shakeToken += 1
            showingFailure = true
            Task {
                try? await Task.sleep(for: .milliseconds(OvertFeedback.failHoldMs))
                showingFailure = false
                stroke.reset()
                fingerPoint = nil
            }
        }
        .onChange(of: gestureActive) { _, active in
            // A system touch-cancel ends the gesture without `onEnded`.
            guard !active, drawing else { return }
            drawing = false
            stroke.reset()
            fingerPoint = nil
            events(.clear)
        }
    }

    // MARK: - Caption slot

    @ViewBuilder
    private func captionSlot(theme: DisguiseTheme, margin: CGFloat) -> some View {
        if mode == .disguise {
            CaptionStrip(primary: PatternCopy.faceTitle, theme: theme, horizontalPadding: margin)
        } else if let caption {
            CaptionStrip(primary: PatternCopy.text(for: caption.primary, mode: mode),
                         primaryIsError: caption.primary.isError,
                         secondary: caption.secondary.map { PatternCopy.text(for: $0, mode: mode) },
                         theme: theme,
                         horizontalPadding: margin)
                .animation(.easeInOut(duration: 0.15), value: caption)
        }
    }

    // MARK: - Grid

    private func grid(theme: DisguiseTheme, side: CGFloat) -> some View {
        let cell = side / CGFloat(PatternGeometry.side)
        let accent = showingFailure ? theme.captionError : theme.keyOp
        return ZStack {
            Canvas { context, _ in
                var path = Path()
                for (offset, index) in stroke.selected.enumerated() {
                    let point = PatternGeometry.center(of: index, cell: cell)
                    if offset == 0 {
                        path.move(to: point)
                    } else {
                        path.addLine(to: point)
                    }
                }
                if let last = stroke.selected.last, let fingerPoint {
                    path.move(to: PatternGeometry.center(of: last, cell: cell))
                    path.addLine(to: fingerPoint)
                }
                context.stroke(
                    path,
                    with: .color(accent.opacity(PatternGeometry.lineOpacity)),
                    style: StrokeStyle(lineWidth: PatternGeometry.lineWidth,
                                       lineCap: .round, lineJoin: .round)
                )
            }
            ForEach(0..<PatternGeometry.nodeCount, id: \.self) { index in
                let isSelected = stroke.selected.contains(index)
                Circle()
                    .fill(isSelected ? accent : theme.keyFn)
                    .frame(width: isSelected ? PatternGeometry.selectedNodeDiameter
                                             : PatternGeometry.restingNodeDiameter,
                           height: isSelected ? PatternGeometry.selectedNodeDiameter
                                              : PatternGeometry.restingNodeDiameter)
                    .position(PatternGeometry.center(of: index, cell: cell))
            }
        }
        .frame(width: side, height: side)
        .contentShape(Rectangle())
        .modifier(ShakeEffect(travel: reduceMotion ? 0 : 8, animatableData: CGFloat(shakeToken)))
        .animation(.easeInOut(duration: Double(OvertFeedback.shakeDurationMs) / 1000), value: shakeToken)
        .gesture(strokeGesture(cell: cell))
        // A stroke cannot be synthesized, so the grid is one non-operable
        // element. The consequence is disclosed on the carousel/picker card.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("pattern grid")
        .accessibilityIdentifier("pattern_grid")
    }

    private func strokeGesture(cell: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _, state, _ in state = true }
            .onChanged { value in
                if !drawing {
                    // Touch down anywhere on the grid resets the path. This
                    // also recovers from a previous stroke that was cancelled.
                    drawing = true
                    stroke.reset()
                    events(.clear)
                }
                fingerPoint = value.location
                guard let node = PatternGeometry.node(at: value.location, cell: cell) else { return }
                for token in stroke.enter(node: node) {
                    events(.token(token))
                }
            }
            .onEnded { _ in
                drawing = false
                fingerPoint = nil
                // Lift with no node is not a pattern: emit nothing.
                guard !stroke.isEmpty else { return }
                events(.commit)
                if mode == .captureNew || mode == .confirmNew {
                    stroke.reset()
                }
            }
    }
}

/// Resting face: title and nine resting nodes.
struct PatternCoverFace: View {
    var body: some View {
        PatternSurface(mode: .disguise, caption: nil, failedAttemptCount: 0, events: { _ in })
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}
