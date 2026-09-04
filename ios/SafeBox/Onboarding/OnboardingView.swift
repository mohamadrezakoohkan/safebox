import SwiftUI

private let successGreen = Color(hex: 0x4ADE80)
private let pageCount = 4

/// The guide: which lock face you get, what the app really is, and how the code
/// on that face works.
///
/// `.firstRun` shows it while no passcode exists (fresh install / post-erase),
/// before any lock face appears. `.revisit` re-opens the same pages from
/// Settings inside the unlocked vault, locked on the current face and writing
/// nothing (decisions §5, §6).
///
/// Page 1 is the disguise carousel; pages 3 and 4 bind to the selection and
/// update live. Finishing hands the selected face id back — the caller decides
/// what to do with it. **Nothing here persists the onboarding sentinel:** that
/// write now happens when the first envelope is stored (§4).
struct OnboardingView: View {
    let mode: OnboardingMode
    let registry: DisguiseRegistry
    /// The enrolled face, when there is one. Revisit locks onto it.
    let currentDisguiseId: String?
    let onFinish: (String) -> Void

    @Environment(\.colorScheme) private var colorScheme
    @State private var page = 0
    @State private var selection: String?

    init(mode: OnboardingMode,
         registry: DisguiseRegistry,
         currentDisguiseId: String? = nil,
         onFinish: @escaping (String) -> Void) {
        self.mode = mode
        self.registry = registry
        self.currentDisguiseId = currentDisguiseId
        self.onFinish = onFinish
        let start: String
        switch mode {
        case .firstRun: start = DisguiseRegistry.defaultId
        case .revisit: start = currentDisguiseId ?? DisguiseRegistry.defaultId
        }
        _selection = State(initialValue: start)
    }

    private var theme: DisguiseTheme { DisguiseTheme.theme(for: colorScheme) }
    private var isLast: Bool { page == pageCount - 1 }
    private var selectedDisguise: any DisguiseProviding { registry.resolve(id: selection) }

    /// Top-right: Skip on the first run, Done on a revisit.
    private var trailingTitle: String {
        switch mode {
        case .firstRun: GuideCopy.skip
        case .revisit: VaultCopy.onboardingDone
        }
    }

    /// Final CTA: "Set my code" leads into setup on the first run; a revisit
    /// has nothing to set up, so it reads Done like the top-right button.
    private var finalTitle: String {
        switch mode {
        case .firstRun: GuideCopy.start
        case .revisit: VaultCopy.onboardingDone
        }
    }

    private var showsTrailingButton: Bool {
        !isLast || mode.showsTrailingButtonOnLastPage
    }

    /// Skip passes whatever card is centered at that moment.
    private func finish() {
        onFinish(selectedDisguise.id)
    }

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button(trailingTitle, action: finish)
                        .font(.system(size: 15))
                        .foregroundStyle(theme.caption)
                        .opacity(showsTrailingButton ? 1 : 0)
                        .disabled(!showsTrailingButton)
                }
                .padding(.horizontal, 16)
                .frame(minHeight: 44)

                TabView(selection: $page) {
                    DisguisePickerPage(theme: theme,
                                       mode: mode,
                                       registry: registry,
                                       currentId: currentDisguiseId,
                                       selection: $selection)
                        .tag(0)
                    VaultPage(theme: theme).tag(1)
                    // Keyed by face id: the paged TabView caches its children,
                    // so a new selection must build a new page.
                    PlaygroundPage(theme: theme, disguise: selectedDisguise)
                        .id(selectedDisguise.id)
                        .tag(2)
                    CommitPage(theme: theme, disguise: selectedDisguise)
                        .id(selectedDisguise.id)
                        .tag(3)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .animation(.easeInOut, value: page)

                PageDots(current: page, theme: theme)
                    .padding(.top, 4)

                Button {
                    if isLast {
                        finish()
                    } else {
                        withAnimation { page += 1 }
                    }
                } label: {
                    Text(isLast ? finalTitle : GuideCopy.next)
                        .font(.system(size: 17, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(theme.keyOp, in: RoundedRectangle(cornerRadius: 16))
                        .foregroundStyle(theme.keyLabelOnOp)
                        .contentTransition(.opacity)
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 20)
            }
        }
    }
}

// MARK: - Shared page scaffold

private struct PageColumn<Hero: View>: View {
    let theme: DisguiseTheme
    let title: String
    let body_: String
    @ViewBuilder let hero: () -> Hero

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            hero()
            Spacer(minLength: 0)
            Text(title)
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(theme.displayText)
                .multilineTextAlignment(.center)
            Text(body_)
                .font(.system(size: 15))
                .lineSpacing(4)
                .foregroundStyle(theme.caption)
                .multilineTextAlignment(.center)
                .padding(.top, 12)
            Spacer(minLength: 0)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 28)
    }
}

// MARK: - Page 1: pick a disguise

private struct DisguisePickerPage: View {
    let theme: DisguiseTheme
    let mode: OnboardingMode
    let registry: DisguiseRegistry
    let currentId: String?
    @Binding var selection: String?

    private var carouselMode: DisguiseCarousel.Mode {
        switch mode {
        case .firstRun: .firstRun
        case .revisit: .revisit
        }
    }

    var body: some View {
        // Deliberately NOT wrapped in a vertical ScrollView: a third nested
        // scroll view (page TabView → vertical scroll → horizontal carousel)
        // makes UIKit's gesture arbitration swallow the carousel's pan.
        VStack(spacing: 0) {
            Text(VaultCopy.onboardingDisguiseTitle)
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(theme.displayText)
                .multilineTextAlignment(.center)
            Text(VaultCopy.onboardingDisguiseBody)
                .font(.system(size: 15))
                .lineSpacing(4)
                .foregroundStyle(theme.caption)
                .multilineTextAlignment(.center)
                .padding(.top, 10)
                .padding(.horizontal, 28)
            Spacer(minLength: 8)
            DisguiseCarousel(mode: carouselMode,
                             registry: registry,
                             currentId: currentId,
                             selection: $selection)
            Spacer(minLength: 8)
        }
        .padding(.vertical, 8)
    }
}

// MARK: - Page 2: what's inside (staggered feature cards)

private struct VaultPage: View {
    let theme: DisguiseTheme
    @State private var revealed = false

    private var cards: [(icon: String, title: String, sub: String)] {
        [
            ("photo.fill", GuideCopy.page2Photos, GuideCopy.page2PhotosSub),
            ("note.text", GuideCopy.page2Notes, GuideCopy.page2NotesSub),
            ("person.fill", GuideCopy.page2Contacts, GuideCopy.page2ContactsSub),
        ]
    }

    var body: some View {
        PageColumn(theme: theme, title: GuideCopy.page2Title, body_: GuideCopy.page2Body) {
            VStack(spacing: 12) {
                ForEach(Array(cards.enumerated()), id: \.offset) { index, card in
                    HStack(spacing: 14) {
                        Circle()
                            .fill(theme.keyOp)
                            .frame(width: 44, height: 44)
                            .overlay {
                                Image(systemName: card.icon)
                                    .font(.system(size: 19))
                                    .foregroundStyle(.white)
                            }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(card.title)
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(theme.keyLabel)
                            Text(card.sub)
                                .font(.system(size: 13))
                                .foregroundStyle(theme.caption)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(16)
                    .background(theme.keyDigit, in: RoundedRectangle(cornerRadius: 18))
                    .opacity(revealed ? 1 : 0)
                    .offset(y: revealed ? 0 : 24)
                    .animation(.easeOut(duration: 0.42).delay(Double(index) * 0.14), value: revealed)
                }
            }
        }
        // Paged TabView caches its children, so parent props don't reliably
        // propagate; page visibility (appear/disappear) is the dependable
        // trigger, and it re-runs the stagger on every visit.
        .onAppear { revealed = true }
        .onDisappear { revealed = false }
    }
}

// MARK: - Page 3: the selected face's interactive playground

private struct PlaygroundPage: View {
    let theme: DisguiseTheme
    let disguise: any DisguiseProviding

    @State private var count = 0

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            Text(disguise.page3Title)
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(theme.displayText)
                .multilineTextAlignment(.center)
            Text(disguise.page3Body)
                .font(.system(size: 14))
                .lineSpacing(3)
                .foregroundStyle(theme.caption)
                .multilineTextAlignment(.center)
                .padding(.top, 10)
            Spacer(minLength: 0)

            disguise.makePlayground { count = $0 }

            // 4 progress pips → green caption once the demo code is long enough.
            HStack(spacing: 8) {
                ForEach(0..<PasscodeRules.minTokens, id: \.self) { index in
                    Circle()
                        .fill(count > index ? successGreen : theme.keyFn)
                        .frame(width: 10, height: 10)
                }
            }
            .animation(.default, value: count)
            .padding(.top, 14)

            Text(count >= PasscodeRules.minTokens ? disguise.page3Ok : disguise.page3Try)
                .font(.system(size: 13))
                .foregroundStyle(count >= PasscodeRules.minTokens ? successGreen : theme.caption)
                .multilineTextAlignment(.center)
                .contentTransition(.opacity)
                .animation(.easeInOut, value: count >= PasscodeRules.minTokens)
                .padding(.top, 10)
            Spacer(minLength: 0)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 28)
    }
}

// MARK: - Page 4: the commit gesture and the no-recovery warning

private struct CommitPage: View {
    let theme: DisguiseTheme
    let disguise: any DisguiseProviding

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            disguise.makeCommitHero()
            Spacer(minLength: 0)
            Text(disguise.page4Title)
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(theme.displayText)
                .multilineTextAlignment(.center)
            Text(disguise.page4Body)
                .font(.system(size: 15))
                .lineSpacing(4)
                .foregroundStyle(theme.caption)
                .multilineTextAlignment(.center)
                .padding(.top, 12)
            HStack(alignment: .center, spacing: 12) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(theme.keyOpActiveRing)
                Text(GuideCopy.page4Warning)
                    .font(.system(size: 13))
                    .lineSpacing(3)
                    .foregroundStyle(theme.keyLabel)
                Spacer(minLength: 0)
            }
            .padding(14)
            .background(theme.keyDigit, in: RoundedRectangle(cornerRadius: 16))
            .padding(.top, 18)
            Spacer(minLength: 0)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 28)
    }
}

// MARK: - Indicator

private struct PageDots: View {
    let current: Int
    let theme: DisguiseTheme

    var body: some View {
        HStack(spacing: 8) {
            ForEach(0..<pageCount, id: \.self) { index in
                Capsule()
                    .fill(current == index ? theme.keyOp : theme.keyFn)
                    .frame(width: current == index ? 26 : 8, height: 8)
            }
        }
        .animation(.spring(duration: 0.35), value: current)
    }
}

#Preview("First run") {
    OnboardingView(mode: .firstRun, registry: DisguiseRegistry(), onFinish: { _ in })
}

#Preview("Revisit") {
    OnboardingView(mode: .revisit, registry: DisguiseRegistry(),
                   currentDisguiseId: "numpad", onFinish: { _ in })
}
