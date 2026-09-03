import SwiftUI

/// Copy for the guide. Deliberately NOT part of LockCopy: the guide runs only
/// while no passcode exists (first run) or inside the unlocked vault (revisit
/// from Settings), so vault vocabulary is allowed here — it never appears on
/// the armed disguise. Keys match the shared cross-platform copy-table IDs;
/// translations live in Localizable.xcstrings. The revisit label is
/// `VaultCopy.onboardingDone` (`onboarding_done`).
private enum OnboardingCopy {
    static let skip = localizedCopy("onboarding_skip", "Skip")
    static let next = localizedCopy("onboarding_next", "Next")
    static let start = localizedCopy("onboarding_start", "Set my code")
    static let page1Title = localizedCopy("onboarding_page1_title", "Looks like a calculator")
    static let page1Body = localizedCopy("onboarding_page1_body", "Calculator+ is a fully working calculator on the surface. Anyone who opens it sees exactly that — and nothing else.")
    static let page2Title = localizedCopy("onboarding_page2_title", "Secretly, it's your vault")
    static let page2Body = localizedCopy("onboarding_page2_body", "Everything stays on this device. No account, no cloud, no sync.")
    static let page2Photos = localizedCopy("onboarding_page2_photos", "Photos")
    static let page2PhotosSub = localizedCopy("onboarding_page2_photos_sub", "Private albums, imported from your library")
    static let page2Notes = localizedCopy("onboarding_page2_notes", "Notes")
    static let page2NotesSub = localizedCopy("onboarding_page2_notes_sub", "Rich text with tags and live preview")
    static let page2Contacts = localizedCopy("onboarding_page2_contacts", "Contacts")
    static let page2ContactsSub = localizedCopy("onboarding_page2_contacts_sub", "People only you know about")
    static let page3Title = localizedCopy("onboarding_page3_title", "Your code is a key sequence")
    static let page3Body = localizedCopy("onboarding_page3_body", "Pick 4 to 32 calculator keys — digits and symbols all count, and order matters. Try one here. This is just practice; nothing is saved.")
    static let page3Try = localizedCopy("onboarding_page3_try", "Tap at least 4 keys")
    static let page3Ok = localizedCopy("onboarding_page3_ok", "That would work — symbols make it stronger")
    static let page3Clear = localizedCopy("onboarding_page3_clear", "Reset")
    static let page4Title = localizedCopy("onboarding_page4_title", "Press = to enter")
    static let page4Body = localizedCopy("onboarding_page4_body", "To unlock, type your code on the calculator and press =. A wrong code just calculates — no error, no hint that anything is hidden.")
    static let page4Warning = localizedCopy("onboarding_page4_warning", "There is no recovery. If you forget your code, the vault stays locked forever.")
}

private let successGreen = Color(hex: 0x4ADE80)
private let pageCount = 4

/// The guide: what the app really is and how the key-sequence passcode works.
/// `.firstRun` shows it while no passcode exists (fresh install / post-erase),
/// before the calculator ever appears — once a vault is set up the disguise is
/// never preceded by an explainer. `.revisit` re-opens the same pages from
/// Settings inside the unlocked vault; there every finish path is a plain
/// dismissal (decisions §5). Persisting completion is the caller's job, gated
/// by `OnboardingSentinel.recordCompletion(for:)`.
/// Styled with the disguise palette so it flows straight into the calculator.
struct OnboardingView: View {
    let mode: OnboardingMode
    let onFinish: () -> Void

    @Environment(\.colorScheme) private var colorScheme
    @State private var page = 0

    private var theme: DisguiseTheme { DisguiseTheme.theme(for: colorScheme) }
    private var isLast: Bool { page == pageCount - 1 }

    /// Top-right: Skip on the first run, Done on a revisit.
    private var trailingTitle: String {
        switch mode {
        case .firstRun: OnboardingCopy.skip
        case .revisit: VaultCopy.onboardingDone
        }
    }

    /// Final CTA: "Set my code" leads into setup on the first run; a revisit
    /// has nothing to set up, so it reads Done like the top-right button.
    private var finalTitle: String {
        switch mode {
        case .firstRun: OnboardingCopy.start
        case .revisit: VaultCopy.onboardingDone
        }
    }

    private var showsTrailingButton: Bool {
        !isLast || mode.showsTrailingButtonOnLastPage
    }

    var body: some View {
        ZStack {
            theme.background.ignoresSafeArea()
            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button(trailingTitle, action: onFinish)
                        .font(.system(size: 15))
                        .foregroundStyle(theme.caption)
                        .opacity(showsTrailingButton ? 1 : 0)
                        .disabled(!showsTrailingButton)
                }
                .padding(.horizontal, 16)
                .frame(minHeight: 44)

                TabView(selection: $page) {
                    DisguisePage(theme: theme).tag(0)
                    VaultPage(theme: theme).tag(1)
                    CodePlaygroundPage(theme: theme).tag(2)
                    EqualsPage(theme: theme).tag(3)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .animation(.easeInOut, value: page)

                PageDots(current: page, theme: theme)
                    .padding(.top, 4)

                Button {
                    if isLast {
                        onFinish()
                    } else {
                        withAnimation { page += 1 }
                    }
                } label: {
                    Text(isLast ? finalTitle : OnboardingCopy.next)
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

// MARK: - Page 1: the disguise (flip card)

private struct DisguisePage: View {
    let theme: DisguiseTheme
    @State private var angle: Double = 0
    @State private var showsLock = false

    var body: some View {
        PageColumn(theme: theme, title: OnboardingCopy.page1Title, body_: OnboardingCopy.page1Body) {
            RoundedRectangle(cornerRadius: 34)
                .fill(showsLock ? theme.keyOp : theme.keyDigit)
                .frame(width: 148, height: 148)
                .overlay {
                    if showsLock {
                        Image(systemName: "lock.fill")
                            .font(.system(size: 56))
                            .foregroundStyle(.white)
                    } else {
                        Text("=")
                            .font(.system(size: 64, weight: .medium))
                            .foregroundStyle(theme.keyLabel)
                    }
                }
                .rotation3DEffect(.degrees(angle), axis: (x: 0, y: 1, z: 0), perspective: 0.55)
                .task {
                    // Half-flip, swap face edge-on, half-flip back into view —
                    // the classic two-phase card flip, looping forever.
                    while !Task.isCancelled {
                        try? await Task.sleep(for: .seconds(1.7))
                        withAnimation(.easeIn(duration: 0.3)) { angle = 90 }
                        try? await Task.sleep(for: .seconds(0.3))
                        showsLock.toggle()
                        angle = -90
                        withAnimation(.easeOut(duration: 0.3)) { angle = 0 }
                    }
                }
        }
    }
}

// MARK: - Page 2: what's inside (staggered feature cards)

private struct VaultPage: View {
    let theme: DisguiseTheme
    @State private var revealed = false

    private var cards: [(icon: String, title: String, sub: String)] {
        [
            ("photo.fill", OnboardingCopy.page2Photos, OnboardingCopy.page2PhotosSub),
            ("note.text", OnboardingCopy.page2Notes, OnboardingCopy.page2NotesSub),
            ("person.fill", OnboardingCopy.page2Contacts, OnboardingCopy.page2ContactsSub),
        ]
    }

    var body: some View {
        PageColumn(theme: theme, title: OnboardingCopy.page2Title, body_: OnboardingCopy.page2Body) {
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

// MARK: - Page 3: interactive code playground

/// A real mini keypad the user can tap to feel how a key-sequence code works.
/// Purely illustrative: nothing leaves this view.
private struct CodePlaygroundPage: View {
    let theme: DisguiseTheme
    @State private var taps: [String] = []

    private let rows: [[(label: String, isOp: Bool)]] = [
        [("7", false), ("8", false), ("9", false), ("÷", true)],
        [("4", false), ("5", false), ("6", false), ("×", true)],
        [("1", false), ("2", false), ("3", false), ("+", true)],
    ]

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            Text(OnboardingCopy.page3Title)
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(theme.displayText)
                .multilineTextAlignment(.center)
            Text(OnboardingCopy.page3Body)
                .font(.system(size: 14))
                .lineSpacing(3)
                .foregroundStyle(theme.caption)
                .multilineTextAlignment(.center)
                .padding(.top, 10)
            Spacer(minLength: 0)

            // Recorded-sequence chips (last 8 shown), popping in with a spring.
            HStack(spacing: 6) {
                if taps.count > 8 {
                    Text("…").font(.system(size: 18)).foregroundStyle(theme.caption)
                }
                ForEach(Array(taps.suffix(8).enumerated()), id: \.offset) { _, label in
                    Text(label)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(theme.keyLabel)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(theme.keyFn, in: RoundedRectangle(cornerRadius: 8))
                        .transition(.scale(scale: 0.5).combined(with: .opacity))
                }
            }
            .frame(minHeight: 40)
            .animation(.spring(duration: 0.35, bounce: 0.4), value: taps)

            // 4 progress pips → green caption once the demo code is long enough.
            HStack(spacing: 8) {
                ForEach(0..<4, id: \.self) { i in
                    Circle()
                        .fill(taps.count > i ? successGreen : theme.keyFn)
                        .frame(width: 10, height: 10)
                }
            }
            .animation(.default, value: taps.count)
            .padding(.top, 14)

            Text(taps.count >= 4 ? OnboardingCopy.page3Ok : OnboardingCopy.page3Try)
                .font(.system(size: 13))
                .foregroundStyle(taps.count >= 4 ? successGreen : theme.caption)
                .contentTransition(.opacity)
                .animation(.easeInOut, value: taps.count >= 4)
                .padding(.top, 10)

            VStack(spacing: 10) {
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    HStack(spacing: 10) {
                        ForEach(Array(row.enumerated()), id: \.offset) { _, key in
                            DemoKey(label: key.label, isOperator: key.isOp, theme: theme) {
                                taps.append(key.label)
                            }
                        }
                    }
                }
            }
            .padding(.top, 18)

            Button(OnboardingCopy.page3Clear) {
                withAnimation { taps.removeAll() }
            }
            .font(.system(size: 13))
            .foregroundStyle(theme.caption)
            .padding(.top, 10)
            Spacer(minLength: 0)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 28)
    }
}

private struct DemoKey: View {
    let label: String
    let isOperator: Bool
    let theme: DisguiseTheme
    let onTap: () -> Void

    @State private var pressed = false

    var body: some View {
        Text(label)
            .font(.system(size: 24, weight: .medium))
            .foregroundStyle(isOperator ? theme.keyLabelOnOp : theme.keyLabel)
            .frame(width: 58, height: 58)
            .background(
                fill,
                in: RoundedRectangle(cornerRadius: 14)
            )
            .onTapGesture {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
                pressed = true
                withAnimation(.easeOut(duration: 0.18)) { pressed = false }
                onTap()
            }
            .accessibilityLabel("demo key \(label)")
    }

    private var fill: Color {
        if isOperator {
            return pressed ? theme.keyOpPressed : theme.keyOp
        } else {
            return pressed ? theme.keyDigitPressed : theme.keyDigit
        }
    }
}

// MARK: - Page 4: the = ritual and no-recovery warning

private struct EqualsPage: View {
    let theme: DisguiseTheme
    @State private var pulsing = false

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 0)
            RoundedRectangle(cornerRadius: 26)
                .fill(theme.keyOp)
                .frame(width: 104, height: 104)
                .overlay {
                    Text("=")
                        .font(.system(size: 48, weight: .medium))
                        .foregroundStyle(theme.keyLabelOnOp)
                }
                .scaleEffect(pulsing ? 1.09 : 1)
                .animation(.easeInOut(duration: 0.85).repeatForever(autoreverses: true), value: pulsing)
                .onAppear { pulsing = true }
            Spacer(minLength: 0)
            Text(OnboardingCopy.page4Title)
                .font(.system(size: 26, weight: .bold))
                .foregroundStyle(theme.displayText)
                .multilineTextAlignment(.center)
            Text(OnboardingCopy.page4Body)
                .font(.system(size: 15))
                .lineSpacing(4)
                .foregroundStyle(theme.caption)
                .multilineTextAlignment(.center)
                .padding(.top, 12)
            HStack(alignment: .center, spacing: 12) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(theme.keyOpActiveRing)
                Text(OnboardingCopy.page4Warning)
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
            ForEach(0..<pageCount, id: \.self) { i in
                Capsule()
                    .fill(current == i ? theme.keyOp : theme.keyFn)
                    .frame(width: current == i ? 26 : 8, height: 8)
            }
        }
        .animation(.spring(duration: 0.35), value: current)
    }
}

#Preview("First run") {
    OnboardingView(mode: .firstRun, onFinish: {})
}

#Preview("Revisit") {
    OnboardingView(mode: .revisit, onFinish: {})
}
