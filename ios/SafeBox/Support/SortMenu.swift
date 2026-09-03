import SwiftUI

/// The toolbar sort control shared by the Gallery and Notes lists (decisions
/// §4): an `arrow.up.arrow.down` menu holding an inline `Picker` labelled
/// "Sort by", which draws the check mark on the active mode for free.
///
/// It belongs in the BROWSING toolbar shape only — selection mode (P6) keeps
/// exactly Cancel + Delete.
struct SortMenu<Mode: VaultSortMode>: View where Mode.AllCases: RandomAccessCollection {
    let selection: Mode
    let onChange: (Mode) -> Void

    var body: some View {
        Menu {
            Picker(VaultCopy.sortTitle, selection: Binding(get: { selection }, set: onChange)) {
                ForEach(Mode.allCases, id: \.self) { mode in
                    Text(mode.label).tag(mode)
                }
            }
            .pickerStyle(.inline)
        } label: {
            Image(systemName: "arrow.up.arrow.down")
        }
        .accessibilityLabel(VaultCopy.sortTitle)
    }
}

#Preview {
    NavigationStack {
        Text("Gallery")
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    SortMenu(selection: AlbumSort.manual) { _ in }
                    SortMenu(selection: NoteSort.dateModified) { _ in }
                }
            }
    }
}
