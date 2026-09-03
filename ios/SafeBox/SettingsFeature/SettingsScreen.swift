import SwiftUI

/// Vault settings (decisions §5): Security → Data → About. No auto-lock
/// setting (backgrounding always locks immediately) and no biometric UI of
/// any kind. Every string is a shared-ID entry (`LockCopy` / `VaultCopy`).
struct SettingsScreen: View {
    let container: AppContainer

    @State private var showChangeFlow = false
    @State private var showChangedConfirmation = false
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
            // Abandons any in-flight update request when this screen goes
            // away — including the vault teardown on lock (decisions §13).
            .onDisappear { updateCheck.cancel() }
            .sheet(isPresented: $showChangeFlow) {
                ChangePasscodeFlow(
                    session: PasscodeEntrySession(passcodeStore: container.passcodeStore),
                    onChanged: { showChangedConfirmation = true }
                )
                .interactiveDismissDisabled()
            }
            // Revisit mode (decisions §5): swipe-dismissible — deliberately no
            // interactiveDismissDisabled — and every finish path only closes
            // the sheet. Nothing here calls completeOnboarding() or touches
            // OnboardingSentinel; the vault stays unlocked underneath.
            .sheet(isPresented: $showGuide) {
                OnboardingView(mode: .revisit, onFinish: { showGuide = false })
            }
            .alert(LockCopy.changeSuccess, isPresented: $showChangedConfirmation) {
                Button(VaultCopy.okAction, role: .cancel) {}
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
