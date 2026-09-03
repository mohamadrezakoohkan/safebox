import SwiftUI

/// Multi-select row plumbing shared by the Notes and Contacts lists (P6).
///
/// The rows are plain views rather than `Button` / `NavigationLink`: a
/// long-press must enter selection mode WITHOUT also firing the row's tap, and
/// a `NavigationLink` navigates on any touch-up (long ones included). Taps are
/// routed explicitly — toggle while selecting, open otherwise — and the
/// indicator is drawn by the row itself. The same set of checkmark glyphs as
/// the photo grid.
struct SelectionIndicator: View {
    let isSelected: Bool

    var body: some View {
        Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
            .font(.title3)
            .foregroundStyle(isSelected ? Color.accentColor : Color.secondary)
            .accessibilityHidden(true)
    }
}

/// Whether the per-tab search field is attached, as a pure decision so the
/// rule is pinned by a test rather than read out of a view body.
///
/// Decisions §6: selection mode shows exactly the count title, Cancel and
/// Delete — a search field over a selection is a spec miss and looks broken at
/// the inline title size. The typed query itself is NOT cleared (it lives in
/// the view model), so exiting selection mode brings the field back with what
/// the user had typed still in it, and the list stays filtered the same way
/// throughout.
enum SelectionSearchPolicy {
    static func showsSearchField(isSelecting: Bool) -> Bool { !isSelecting }
}

extension View {
    /// Attaches `.searchable` only while browsing (`SelectionSearchPolicy`).
    @ViewBuilder
    func searchableWhileBrowsing(isSelecting: Bool, text: Binding<String>) -> some View {
        if SelectionSearchPolicy.showsSearchField(isSelecting: isSelecting) {
            self.searchable(text: text)
        } else {
            self
        }
    }

    /// Tap → `onTap`, long-press → `onLongPress`, whole row tappable, one
    /// accessibility element that reads as a (possibly selected) button and
    /// offers "Select" as a custom action so VoiceOver users can enter
    /// selection mode without the long-press gesture.
    func selectableListRow(isSelecting: Bool, isSelected: Bool,
                           onTap: @escaping () -> Void,
                           onLongPress: @escaping () -> Void) -> some View {
        self
            .contentShape(Rectangle())
            .onTapGesture(perform: onTap)
            .onLongPressGesture(perform: onLongPress)
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isButton)
            .accessibilityAddTraits(isSelecting && isSelected ? .isSelected : [])
            .accessibilityAction(named: Text(VaultCopy.selectAction)) {
                if !isSelecting { onLongPress() }
            }
    }
}
