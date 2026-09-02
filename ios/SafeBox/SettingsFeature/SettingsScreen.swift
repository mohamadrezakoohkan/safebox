import SwiftUI

/// Iteration-1 contents are exactly: Change passcode, Lock now, About.
/// No auto-lock setting (backgrounding always locks immediately) and no
/// biometric UI of any kind.
struct SettingsScreen: View {
    let container: AppContainer

    @State private var showChangeFlow = false
    @State private var showChangedConfirmation = false
    @State private var showEraseConfirm = false
    @State private var showEraseFinal = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Security") {
                    Button(LockCopy.settingsChangeTitle) {
                        showChangeFlow = true
                    }
                    Button("Lock now") {
                        container.lockCoordinator.lock()
                    }
                    Button(role: .destructive) {
                        showEraseConfirm = true
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(LockCopy.nukeRowTitle)
                            Text(LockCopy.nukeRowSubtitle)
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                Section("About") {
                    LabeledContent("Version", value: Self.versionString)
                    Text("All data stays on this device. SafeBox has no servers and sends nothing anywhere.")
                        .font(.footnote)
                    Text("How it works: the calculator is the lock screen. Type your secret key sequence and press = to open the vault. The vault locks the moment the app leaves the foreground.")
                        .font(.footnote)
                    Text(LockCopy.noRecoveryBody)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                // Future placeholders (decoy passcode, break-in alerts,
                // disguise themes) are deliberately not shown.
            }
            .navigationTitle("Settings")
            .sheet(isPresented: $showChangeFlow) {
                ChangePasscodeFlow(
                    session: PasscodeEntrySession(passcodeStore: container.passcodeStore),
                    onChanged: { showChangedConfirmation = true }
                )
                .interactiveDismissDisabled()
            }
            .alert(LockCopy.changeSuccess, isPresented: $showChangedConfirmation) {
                Button("OK", role: .cancel) {}
            }
            // Two-step destructive confirm; the nuke leaves this screen the
            // moment the lock state resets, so nothing here awaits UI state.
            .alert(LockCopy.nukeConfirmTitle, isPresented: $showEraseConfirm) {
                Button(LockCopy.nukeConfirmContinue, role: .destructive) {
                    showEraseFinal = true
                }
                Button(LockCopy.changeCancel, role: .cancel) {}
            } message: {
                Text(LockCopy.nukeConfirmBody)
            }
            .alert(LockCopy.nukeFinalTitle, isPresented: $showEraseFinal) {
                Button(LockCopy.nukeFinalErase, role: .destructive) {
                    Task { await container.vaultNuker.nuke() }
                }
                Button(LockCopy.changeCancel, role: .cancel) {}
            } message: {
                Text(LockCopy.nukeFinalBody)
            }
        }
    }

    private static var versionString: String {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(version) (\(build))"
    }
}
