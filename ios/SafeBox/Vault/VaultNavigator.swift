import Foundation
import Observation

/// The four vault tabs (decisions §10 copy IDs). `TabView(selection:)` tags its
/// tabs with these, so search can select one programmatically.
enum VaultTab: String, CaseIterable, Hashable, Sendable, Identifiable {
    case gallery
    case notes
    case contacts
    case settings

    var id: String { rawValue }

    var title: String {
        switch self {
        case .gallery: VaultCopy.vaultTabGallery
        case .notes: VaultCopy.vaultTabNotes
        case .contacts: VaultCopy.vaultTabContacts
        case .settings: VaultCopy.vaultTabSettings
        }
    }

    var systemImage: String {
        switch self {
        case .gallery: "photo.on.rectangle"
        case .notes: "note.text"
        case .contacts: "person.crop.circle"
        case .settings: "gearshape"
        }
    }
}

// MARK: - Typed routes
//
// One enum per tab whose stack can be driven programmatically. Each case
// carries an ID, never a `@Model` object: a path outlives the row it points at
// (it survives a tab switch, and search rebuilds it from a snapshot), so the
// destination re-fetches by id and renders nothing if the row is gone.

/// Gallery stack. The photo pager is NOT a route — it stays an item-driven
/// destination inside `AlbumGridScreen` (see the N1 handoff notes).
enum GalleryRoute: Hashable, Sendable {
    case album(UUID)
}

enum NotesRoute: Hashable, Sendable {
    case note(UUID)
}

enum ContactsRoute: Hashable, Sendable {
    case contact(UUID)
}

/// A tab plus the single detail route to show on it — the whole payload of the
/// cross-tab navigation contract.
enum VaultDestination: Hashable, Sendable {
    case gallery(GalleryRoute)
    case notes(NotesRoute)
    case contacts(ContactsRoute)

    var tab: VaultTab {
        switch self {
        case .gallery: .gallery
        case .notes: .notes
        case .contacts: .contacts
        }
    }
}

/// The result → destination mapping, as a pure function so the contract is
/// unit-testable without a view hierarchy (decisions §7).
enum VaultRouting {
    static func destination(for result: SearchResult) -> VaultDestination {
        switch result.kind {
        case .album: .gallery(.album(result.id))
        case .note: .notes(.note(result.id))
        case .contact: .contacts(.contact(result.id))
        }
    }
}

/// Owns the vault's navigation state: which tab is selected, each tab's stack,
/// and whether global search is up.
///
/// Created by `MainTabView` and therefore rebuilt on every unlock and destroyed
/// on lock — deliberately NOT in `AppContainer`, which outlives the lock: a
/// path surviving a lock would reopen a note or an album the moment the vault
/// unlocks again.
@MainActor
@Observable
final class VaultNavigator {
    var selectedTab: VaultTab = .gallery

    /// One path per tab whose stack search can target. Settings keeps its own
    /// `NavigationStack` — search never navigates there (decisions §7).
    var galleryPath: [GalleryRoute] = []
    var notesPath: [NotesRoute] = []
    var contactsPath: [ContactsRoute] = []

    /// The one global-search presentation, shared by all three magnifier
    /// actions.
    var isSearchPresented = false

    func presentSearch() {
        isSearchPresented = true
    }

    /// The cross-tab navigation contract (decisions §7) in one place:
    /// (1) dismiss search, (2) select the target tab, (3) reset that tab's back
    /// stack to its root list, (4) push the detail.
    ///
    /// (3) and (4) are one assignment: replacing the path with exactly one route
    /// leaves the list underneath, so Back from the detail lands on the tab's
    /// list. The other tabs' stacks are untouched.
    func open(_ destination: VaultDestination) {
        isSearchPresented = false
        switch destination {
        case .gallery(let route): galleryPath = [route]
        case .notes(let route): notesPath = [route]
        case .contacts(let route): contactsPath = [route]
        }
        selectedTab = destination.tab
    }

    func open(_ result: SearchResult) {
        open(VaultRouting.destination(for: result))
    }

    /// Drops a route whose row no longer exists (purged from Recently deleted,
    /// expired, or erased) so the stack pops back to the tab's list instead of
    /// leaving an empty pushed screen. A route that is not on its path — or a
    /// route for another tab — leaves everything untouched.
    func dismiss(_ destination: VaultDestination) {
        switch destination {
        case .gallery(let route): galleryPath.removeAll { $0 == route }
        case .notes(let route): notesPath.removeAll { $0 == route }
        case .contacts(let route): contactsPath.removeAll { $0 == route }
        }
    }
}
