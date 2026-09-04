import SwiftUI

/// Hosts whichever face a `PasscodeEntrySession` is currently on:
/// VerifyCurrent → (Pick) → EnterNew → Confirm, with visible wrong-current
/// feedback and a Cancel button in every phase. Same components as the lock
/// screen, in a different mode.
struct ChangePasscodeFlow: View {
    @State private var session: PasscodeEntrySession
    let onChanged: () -> Void
    @Environment(\.dismiss) private var dismiss

    init(session: PasscodeEntrySession, onChanged: @escaping () -> Void) {
        _session = State(initialValue: session)
        self.onChanged = onChanged
    }

    var body: some View {
        NavigationStack {
            Group {
                if session.phase == .pickDisguise {
                    DisguisePickerView(session: session)
                } else {
                    DisguiseSurfaceHost(
                        disguise: session.surfaceDisguise,
                        mode: session.surfaceMode,
                        caption: session.caption,
                        failedAttemptCount: session.failedAttemptCount,
                        onEvent: { session.eventObserved($0) },
                        onCommit: { tokens, overflowed in
                            Task { await session.commit(tokens: tokens, overflowed: overflowed) }
                        }
                    )
                    // The recorder and the face are rebuilt together when the
                    // face swaps (decisions §1.5, PICK → CAPTURE_NEW).
                    .id(session.surfaceDisguise.id)
                }
            }
            .navigationTitle(session.navigationTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(LockCopy.changeCancel) { dismiss() }
                }
            }
        }
        .onChange(of: session.phase) { _, phase in
            if phase == .done {
                onChanged()
                dismiss()
            }
        }
    }
}

/// The switch flow's PICK phase: the carousel in "pick" mode, the explainer,
/// and a CTA that is disabled while the centered card is the current face —
/// the current face cannot be picked, so there is no no-op path.
private struct DisguisePickerView: View {
    let session: PasscodeEntrySession

    @Environment(\.colorScheme) private var colorScheme
    @State private var selection: String?

    init(session: PasscodeEntrySession) {
        self.session = session
        _selection = State(initialValue: session.currentDisguise.id)
    }

    private var selected: any DisguiseProviding {
        session.registry.resolve(id: selection)
    }

    var body: some View {
        let theme = DisguiseTheme.theme(for: colorScheme)
        ZStack {
            theme.background.ignoresSafeArea()
            ScrollView {
                VStack(spacing: 16) {
                    DisguiseCarousel(mode: .pick,
                                     registry: session.registry,
                                     currentId: session.currentDisguise.id,
                                     selection: $selection)
                    Text(VaultCopy.disguiseSwitchExplainer(
                        currentName: session.currentDisguise.displayName,
                        currentGesture: session.currentDisguise.commitGesture
                    ))
                    .font(.system(size: 13))
                    .lineSpacing(3)
                    .foregroundStyle(theme.caption)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 24)
                }
                .padding(.vertical, 12)
            }
            // The card, the disclosure and the explainer together overflow a
            // phone screen, so they scroll — but the primary action must not
            // scroll with them. Pinned to the bottom it is always visible;
            // inside the ScrollView it sat just past the bottom edge with
            // nothing hinting there was more, and the switch looked like a
            // dead end.
            .safeAreaInset(edge: .bottom) {
                Button {
                    session.pick(selected)
                } label: {
                    Text(VaultCopy.disguisePickAction)
                        .font(.system(size: 17, weight: .semibold))
                        .frame(maxWidth: .infinity)
                        .frame(height: 54)
                        .background(theme.keyOp, in: RoundedRectangle(cornerRadius: 16))
                        .foregroundStyle(theme.keyLabelOnOp)
                }
                .disabled(!session.canPick(selected))
                .opacity(session.canPick(selected) ? 1 : 0.4)
                .padding(.horizontal, 24)
                .padding(.top, 8)
                .padding(.bottom, 12)
                .background(theme.background)
            }
        }
    }
}
