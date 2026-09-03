import SwiftUI

/// The undo toast: message on the left, Undo on the right, material capsule.
/// Rendered once, by `MainTabView`, above the tab bar.
struct UndoToastView: View {
    let message: String
    /// `nil` for a plain notice (N3's failed video import): the message shows
    /// for the same 5 s with no Undo button.
    let onUndo: (() -> Void)?

    /// Height of the standard tab bar the toast must clear (the overlay is
    /// already inset from the home indicator by the safe area).
    static let tabBarClearance: CGFloat = 49

    var body: some View {
        HStack(spacing: 12) {
            Text(message)
                .font(.subheadline)
                .lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let onUndo {
                Button(VaultCopy.undoAction, action: onUndo)
                    .font(.subheadline.weight(.semibold))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .shadow(color: .black.opacity(0.15), radius: 8, y: 2)
        .accessibilityElement(children: .contain)
    }
}

#Preview {
    UndoToastView(message: VaultCopy.deletedPhotos(3), onUndo: {})
        .padding()
}
