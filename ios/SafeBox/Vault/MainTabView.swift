import SwiftUI

/// The vault: four tabs shown only while unlocked.
struct MainTabView: View {
    let container: AppContainer

    var body: some View {
        TabView {
            AlbumListScreen(viewModel: AlbumListViewModel(repository: container.photoRepository),
                            container: container)
                .tabItem { Label("Gallery", systemImage: "photo.on.rectangle") }
            NotesListScreen(viewModel: NotesListViewModel(repository: container.noteRepository),
                            container: container)
                .tabItem { Label("Notes", systemImage: "note.text") }
            ContactsListScreen(viewModel: ContactsListViewModel(repository: container.contactRepository),
                               container: container)
                .tabItem { Label("Contacts", systemImage: "person.crop.circle") }
            SettingsScreen(container: container)
                .tabItem { Label("Settings", systemImage: "gearshape") }
        }
    }
}
