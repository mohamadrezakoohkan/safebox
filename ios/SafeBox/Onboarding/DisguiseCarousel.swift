import SwiftUI

/// One component, three modes (decisions §6). **The centered card is the
/// selection** — no tap required, though tapping a neighbour brings it to
/// centre.
///
/// Deliberately NOT a `ScrollView`: guide page 1 lives inside a paged
/// `TabView`, and a nested horizontal scroll view there is arbitrated by UIKit
/// — it swallows the pan without moving, and neither `.scrollPosition(id:)`
/// nor `.scrollTargetBehavior(.viewAligned)` ever runs. A three-card picker
/// does not need a scroll view: an offset-driven strip with an explicit snap
/// is deterministic, always lands on exactly one card, and behaves identically
/// on every screen width.
struct DisguiseCarousel: View {
    enum Mode: Equatable {
        /// Guide page 1 on a fresh install: swipeable, starts on calculator.
        case firstRun
        /// Settings → How it works: locked on the current face, nothing written.
        case revisit
        /// The switch flow: swipeable, starts on the current face.
        case pick
    }

    let mode: Mode
    let registry: DisguiseRegistry
    /// The current (enrolled) face, when there is one. Gets the badge.
    let currentId: String?
    @Binding var selection: String?

    @Environment(\.colorScheme) private var colorScheme
    @State private var index: Int
    @GestureState private var dragTranslation: CGFloat = 0

    private static let cardWidth: CGFloat = 256
    /// Decisions §8 pins 340, but the pattern card carries four text lines
    /// under the pinned 126×224 thumbnail (name, tagline, identity grade and
    /// `pattern_a11y_note`) and they do not fit. Widening or shrinking the
    /// thumbnail would make the face less recognizable, which is the card's
    /// whole job, so the height gives instead. Width, spacing, peek, radius
    /// and the thumbnail are unchanged.
    /// 424, not the 340 pinned in decisions §8: the tallest card is the pattern
    /// one while it is the current face — thumbnail, name, "Current" badge, a
    /// three-line tagline, the identity grade and the screen-reader note — and
    /// a fixed frame clips rather than grows. Measured from an Android device
    /// dump that content needs ~413pt; 424 leaves a margin. Kept identical on
    /// both platforms so the carousel reads the same.
    private static let cardHeight: CGFloat = 424
    private static let spacing: CGFloat = 12
    /// Fraction of a card's pitch the finger must cover to advance.
    private static let advanceFraction: CGFloat = 1.0 / 3.0

    private var pitch: CGFloat { Self.cardWidth + Self.spacing }
    private var isSwipeable: Bool { mode != .revisit }

    init(mode: Mode, registry: DisguiseRegistry, currentId: String?, selection: Binding<String?>) {
        self.mode = mode
        self.registry = registry
        self.currentId = currentId
        _selection = selection
        let start = registry.all.firstIndex { $0.id == selection.wrappedValue } ?? 0
        _index = State(initialValue: start)
    }

    var body: some View {
        let theme = DisguiseTheme.theme(for: colorScheme)
        VStack(spacing: 14) {
            // The strip is wider than the screen by design. A GeometryReader
            // pins the reported width to the container's, so the overflow is
            // purely visual and never stretches the copy below it.
            GeometryReader { geo in
                strip(theme: theme)
                    .frame(width: geo.size.width, height: Self.cardHeight)
            }
            .frame(height: Self.cardHeight)
            .clipped()

            Text(VaultCopy.disguiseIdentityDisclosure)
                .font(.system(size: 12))
                .lineSpacing(2)
                .foregroundStyle(theme.caption)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            if mode == .revisit {
                Text(VaultCopy.onboardingDisguiseRevisitHint)
                    .font(.system(size: 12))
                    .foregroundStyle(theme.caption)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
            }
        }
        .onChange(of: index, initial: true) { _, new in
            let id = registry.all[new].id
            if selection != id { selection = id }
        }
    }

    // MARK: - The strip

    private func strip(theme: DisguiseTheme) -> some View {
        // Offset so that `index`'s card sits on the container's centre line.
        let centred = -(CGFloat(index) - CGFloat(registry.all.count - 1) / 2) * pitch
        return HStack(spacing: Self.spacing) {
            ForEach(Array(registry.all.enumerated()), id: \.element.id) { position, disguise in
                card(disguise, theme: theme)
                    .opacity(dimmed(position) ? 0.35 : 1)
                    .onTapGesture {
                        guard isSwipeable else { return }
                        index = position
                    }
            }
        }
        .offset(x: centred + dragTranslation)
        .animation(.snappy(duration: 0.28), value: index)
        .gesture(isSwipeable ? swipe : nil)
    }

    private var swipe: some Gesture {
        DragGesture(minimumDistance: 8)
            .updating($dragTranslation) { value, state, _ in
                state = value.translation.width
            }
            .onEnded { value in
                // Predicted end travel, so a flick advances the way a scroll
                // view would while a slow short drag springs back.
                let travel = value.predictedEndTranslation.width
                let threshold = pitch * Self.advanceFraction
                if travel < -threshold {
                    index = min(index + 1, registry.all.count - 1)
                } else if travel > threshold {
                    index = max(index - 1, 0)
                }
            }
    }

    private func dimmed(_ position: Int) -> Bool {
        mode == .revisit && registry.all[position].id != currentId
    }

    // MARK: - Card

    private func card(_ disguise: any DisguiseProviding, theme: DisguiseTheme) -> some View {
        VStack(spacing: 8) {
            thumbnail(disguise)
            Text(disguise.displayName)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(theme.keyLabel)
            Text(disguise.tagline)
                .font(.system(size: 13))
                .foregroundStyle(theme.caption)
                .multilineTextAlignment(.center)
                .fixedSize(horizontal: false, vertical: true)
            Text(VaultCopy.disguiseCoverIdentity(disguise.coverIdentityName))
                .font(.system(size: 11))
                .foregroundStyle(theme.caption)
                .multilineTextAlignment(.center)
            if let note = disguise.a11yNote {
                Text(note)
                    .font(.system(size: 11))
                    .foregroundStyle(theme.caption)
                    .multilineTextAlignment(.center)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .frame(width: Self.cardWidth, height: Self.cardHeight)
        .background(theme.keyDigit, in: RoundedRectangle(cornerRadius: 24))
        .overlay(alignment: .topTrailing) {
            if mode != .firstRun, disguise.id == currentId {
                Text(VaultCopy.disguiseCurrentBadge)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(theme.keyLabelOnOp)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(theme.keyOp, in: Capsule())
                    .padding(10)
            }
        }
        .contentShape(RoundedRectangle(cornerRadius: 24))
        .accessibilityElement(children: .combine)
    }

    /// The face's own resting surface, rendered into a 360×640 virtual canvas
    /// and scaled 0.35 into a 126×224 frame.
    private func thumbnail(_ disguise: any DisguiseProviding) -> some View {
        disguise.makeCoverFace()
            .frame(width: 360, height: 640)
            .scaleEffect(0.35, anchor: .topLeading)
            .frame(width: 126, height: 224, alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}
