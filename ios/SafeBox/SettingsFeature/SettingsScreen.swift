import SwiftUI

/// Vault settings (decisions §5): Security → Data → About. No auto-lock
/// setting (backgrounding always locks immediately) and no biometric UI of
/// any kind. Every string is a shared-ID entry (`LockCopy` / `VaultCopy`).
struct SettingsScreen: View {
    let container: AppContainer

    @State private var showChangeFlow = false
    @State private var showDisguiseFlow = false
    @State private var showChangedConfirmation = false
    @State private var showDisguiseChanged = false
    @State private var showEraseConfirm = false
    @State private var showEraseFinal = false
    @State private var showGuide = false
    /// Owned here on purpose (decisions §13): it is created with the Settings
    /// tab and dies with the vault on lock, so no check can outlive the
    /// unlocked session. Deliberately NOT in AppContainer.
    @State private var updateCheck = UpdateCheckModel()

    @Environment(\.openURL) private var openURL

    var body: some View {
        NavigationStack {
            Form {
                Section(VaultCopy.settingsSectionSecurity) {
                    Button(LockCopy.settingsChangeTitle) {
                        showChangeFlow = true
                    }
                    // Directly under "Change passcode" (decisions §5): this row
                    // re-enrolls the code, so it belongs beside the other
                    // passcode row — not under an Appearance section.
                    Button {
                        showDisguiseFlow = true
                    } label: {
                        SettingsRowLabel(
                            title: VaultCopy.settingsChangeDisguiseTitle,
                            subtitle: container.lockCoordinator.activeDisguise.displayName
                        )
                    }
                    Button(VaultCopy.settingsLockNow) {
                        container.lockCoordinator.lock()
                    }
                }
                Section(VaultCopy.settingsSectionData) {
                    NavigationLink {
                        TrashScreen(viewModel: TrashViewModel(repository: container.trashRepository),
                                    container: container)
                    } label: {
                        SettingsRowLabel(title: VaultCopy.trashTitle, subtitle: VaultCopy.trashSubtitle)
                    }
                    // Erase everything stays the red, last row of the section.
                    Button(role: .destructive) {
                        showEraseConfirm = true
                    } label: {
                        SettingsRowLabel(title: LockCopy.nukeRowTitle, subtitle: LockCopy.nukeRowSubtitle)
                    }
                }
                Section(VaultCopy.settingsSectionAbout) {
                    LabeledContent(VaultCopy.settingsVersion, value: Self.versionString)
                    // An action, not a paragraph: re-opens the real guide in
                    // revisit mode. This row exists only inside the unlocked
                    // vault, so the guide's vault vocabulary is never reachable
                    // from the locked calculator.
                    Button {
                        showGuide = true
                    } label: {
                        SettingsRowLabel(
                            title: VaultCopy.settingsHowItWorks,
                            subtitle: VaultCopy.settingsHowItWorksSubtitle
                        )
                    }
                    NavigationLink {
                        PrivacyScreen()
                    } label: {
                        SettingsRowLabel(
                            title: VaultCopy.settingsPrivacyTitle,
                            subtitle: VaultCopy.settingsPrivacySubtitle
                        )
                    }
                    // Decisions §13. Both rows leave the app; neither reveals
                    // anything about the vault's contents.
                    Button {
                        openURL(UpdateEndpoint.sourceURL)
                    } label: {
                        SettingsRowLabel(
                            title: VaultCopy.settingsSourceCode,
                            subtitle: VaultCopy.settingsSourceCodeSubtitle
                        )
                    }
                    // The app's only network request, and it happens only on
                    // this tap. Once an update is known the same tap opens the
                    // Releases page instead of re-checking.
                    Button {
                        updateCheck.rowTapped(open: { openURL($0) })
                    } label: {
                        SettingsRowLabel(
                            title: VaultCopy.settingsCheckUpdates,
                            subtitle: updateCheck.state.subtitle
                        )
                    }
                    .disabled(updateCheck.isChecking)
                }
                // Future placeholders (decoy passcode, break-in alerts,
                // disguise themes) are deliberately not shown.
            }
            .navigationTitle(VaultCopy.settingsTitle)
            // Deliberately NO .onDisappear { updateCheck.cancel() }. This Form
            // disappears transiently all the time — a NavigationLink push into
            // Privacy or Recently deleted, a sheet, a tab change, the snapshot
            // cover on resign-active — and cancelling there discarded results
            // the user had just asked for, leaving the row blank. Abandonment
            // on lock (decisions §13) is tied to this @State model being
            // deallocated with the vault instead; see UpdateCheckTaskBox.
            .sheet(isPresented: $showChangeFlow) {
                ChangePasscodeFlow(
                    session: makeSession(kind: .changePasscode),
                    onChanged: { showChangedConfirmation = true }
                )
                .interactiveDismissDisabled()
            }
            .sheet(isPresented: $showDisguiseFlow) {
                ChangePasscodeFlow(
                    session: makeSession(kind: .changeDisguise),
                    onChanged: {
                        container.lockCoordinator.reloadActiveDisguise()
                        showDisguiseChanged = true
                    }
                )
                .interactiveDismissDisabled()
            }
            // Revisit mode (decisions §5): swipe-dismissible — deliberately no
            // interactiveDismissDisabled — and every finish path only closes
            // the sheet. Nothing here calls completeOnboarding() or touches
            // OnboardingSentinel; the vault stays unlocked underneath.
            .sheet(isPresented: $showGuide) {
                OnboardingView(mode: .revisit,
                               registry: container.lockCoordinator.registry,
                               currentDisguiseId: container.lockCoordinator.activeDisguise.id,
                               onFinish: { _ in showGuide = false })
            }
            .alert(LockCopy.changeSuccess, isPresented: $showChangedConfirmation) {
                Button(VaultCopy.okAction, role: .cancel) {}
            }
            .alert(VaultCopy.disguiseSwitchSuccessTitle, isPresented: $showDisguiseChanged) {
                Button(VaultCopy.okAction, role: .cancel) {}
            } message: {
                Text(VaultCopy.disguiseSwitchSuccessBody)
            }
            // Two-step destructive confirm; the nuke leaves this screen the
            // moment the lock state resets, so nothing here awaits UI state.
            .alert(LockCopy.nukeConfirmTitle, isPresented: $showEraseConfirm) {
                Button(LockCopy.nukeConfirmContinue, role: .destructive) {
                    showEraseFinal = true
                }
                Button(VaultCopy.cancelAction, role: .cancel) {}
            } message: {
                Text(LockCopy.nukeConfirmBody)
            }
            .alert(LockCopy.nukeFinalTitle, isPresented: $showEraseFinal) {
                Button(LockCopy.nukeFinalErase, role: .destructive) {
                    Task { await container.vaultNuker.nuke() }
                }
                Button(VaultCopy.cancelAction, role: .cancel) {}
            } message: {
                Text(LockCopy.nukeFinalBody)
            }
        }
    }

    private func makeSession(kind: PasscodeEntrySession.Kind) -> PasscodeEntrySession {
        PasscodeEntrySession(
            passcodeStore: container.passcodeStore,
            registry: container.lockCoordinator.registry,
            currentDisguise: container.lockCoordinator.activeDisguise,
            kind: kind
        )
    }

    private static var versionString: String {
        "\(AppVersion.current) (\(AppVersion.currentBuild))"
    }
}

/// Title + secondary footnote subtitle, the row label shared by every Settings
/// row that carries a subtitle (Erase everything, How it works, Privacy, and
/// P3's Recently deleted). Tint comes from the enclosing Button / NavigationLink.
struct SettingsRowLabel: View {
    let title: String
    /// Optional so the update row can render title-only in its idle state
    /// (decisions §13) without the layout shifting between states.
    let subtitle: String?

    init(title: String, subtitle: String?) {
        self.title = title
        self.subtitle = subtitle
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            if let subtitle {
                Text(subtitle)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
