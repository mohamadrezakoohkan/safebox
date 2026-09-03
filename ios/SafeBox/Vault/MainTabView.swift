import SwiftUI

/// The vault: four tabs shown only while unlocked.
///
/// Owns the `UndoCenter` (so undo state dies with the vault on lock) and renders
/// its toast above the tab bar; owns the `VaultNavigator` (tab selection + one
/// typed navigation path per tab, so global search can jump across tabs) — also
/// per-unlock, never in `AppContainer`; and runs the on-unlock expired-trash
/// purge (this view is recreated on every unlock, so `.task` fires exactly once
/// per unlock).
struct MainTabView: View {
    let container: AppContainer
    @State private var undoCenter = UndoCenter()
    @State private var navigator = VaultNavigator()

    var body: some View {
        TabView(selection: $navigator.selectedTab) {
            NavigationStack(path: $navigator.galleryPath) {
                AlbumListScreen(viewModel: AlbumListViewModel(repository: container.photoRepository),
                                container: container)
            }
            .tabItem { Label(VaultTab.gallery.title, systemImage: VaultTab.gallery.systemImage) }
            .tag(VaultTab.gallery)

            NavigationStack(path: $navigator.notesPath) {
                NotesListScreen(viewModel: NotesListViewModel(repository: container.noteRepository),
                                container: container)
            }
            .tabItem { Label(VaultTab.notes.title, systemImage: VaultTab.notes.systemImage) }
            .tag(VaultTab.notes)

            NavigationStack(path: $navigator.contactsPath) {
                ContactsListScreen(viewModel: ContactsListViewModel(repository: container.contactRepository),
                                   container: container)
            }
            .tabItem { Label(VaultTab.contacts.title, systemImage: VaultTab.contacts.systemImage) }
            .tag(VaultTab.contacts)

            // Settings keeps its own NavigationStack: search never targets it,
            // so it needs no externally driven path.
            SettingsScreen(container: container)
                .tabItem { Label(VaultTab.settings.title, systemImage: VaultTab.settings.systemImage) }
                .tag(VaultTab.settings)
        }
        .environment(navigator)
        .environment(undoCenter)
        // One global search for the whole vault, presented from here rather
        // than from a tab so a result can dismiss it and rebuild ANY tab's
        // stack in the same mutation (`VaultNavigator.open`).
        .fullScreenCover(isPresented: $navigator.isSearchPresented) {
            GlobalSearchScreen(
                viewModel: GlobalSearchViewModel(photoRepository: container.photoRepository,
                                                 noteRepository: container.noteRepository,
                                                 contactRepository: container.contactRepository),
                container: container,
                onSelect: { navigator.open($0) }
            )
        }
        .overlay(alignment: .bottom) {
            undoToast
        }
        .task {
            await container.trashRepository.purgeExpired(now: .now)
        }
    }

    /// Scoped animation: only the toast's own appearance animates, never the
    /// tab content (and never anything keyed on lock state — F1).
    private var undoToast: some View {
        ZStack {
            if let entry = undoCenter.current {
                UndoToastView(message: entry.message,
                              onUndo: entry.undo == nil ? nil : { undoCenter.undo() })
                    .padding(.horizontal, 16)
                    .padding(.bottom, UndoToastView.tabBarClearance + 8)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut(duration: 0.2), value: undoCenter.current?.id)
    }
}
