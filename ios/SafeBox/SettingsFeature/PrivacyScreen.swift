import SwiftUI

/// Settings → Privacy: the privacy statement and the no-recovery warning as
/// proper paragraphs (decisions §5) instead of list subtitles. Pushed from the
/// About section, so it exists only inside the unlocked vault.
struct PrivacyScreen: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                Text(VaultCopy.settingsPrivacyBody)
                Text(LockCopy.noRecoveryBody)
            }
            .font(.body)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.vertical, 16)
        }
        .navigationTitle(VaultCopy.settingsPrivacyTitle)
        .navigationBarTitleDisplayMode(.inline)
    }
}

#Preview {
    NavigationStack {
        PrivacyScreen()
    }
}
