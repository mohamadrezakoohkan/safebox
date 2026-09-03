import Foundation

/// A user-selectable list order (decisions §4). The raw values are the shared
/// cross-platform strings and are what gets persisted — never renumber or
/// rename them without changing Android in the same breath.
protocol VaultSortMode: RawRepresentable, CaseIterable, Hashable, Sendable where RawValue == String {
    /// The default applied on a fresh install and whenever the stored value is
    /// missing or unrecognised.
    static var fallback: Self { get }
    /// Menu label (decisions §10).
    var label: String { get }
}

extension VaultSortMode {
    /// Decodes a persisted raw value; an unknown or corrupt one falls back to
    /// the default rather than trapping or showing an empty list.
    init(storedRawValue: String?) {
        self = storedRawValue.flatMap(Self.init(rawValue:)) ?? Self.fallback
    }
}

/// Album list order. `manual` is `sortIndex` (insertion order) and the default.
enum AlbumSort: String, VaultSortMode {
    case manual
    case name
    case dateCreated = "date_created"
    case photoCount = "photo_count"

    static let fallback = AlbumSort.manual

    var label: String {
        switch self {
        case .manual: VaultCopy.sortAlbumManual
        case .name: VaultCopy.sortName
        case .dateCreated: VaultCopy.sortDateCreated
        case .photoCount: VaultCopy.sortPhotoCount
        }
    }
}

/// Note list order. `dateModified` (newest first) is the default.
enum NoteSort: String, VaultSortMode {
    case dateModified = "date_modified"
    case dateCreated = "date_created"
    case title

    static let fallback = NoteSort.dateModified

    var label: String {
        switch self {
        case .dateModified: VaultCopy.sortDateModified
        case .dateCreated: VaultCopy.sortDateCreated
        case .title: VaultCopy.sortNoteTitle
        }
    }
}

/// The two sort preferences, in UserDefaults, global (not per album or tab).
/// Same shape as `OnboardingSentinel`: static funcs with an injectable
/// `defaults` so tests never touch `.standard`.
///
/// UserDefaults (not view state) on purpose: the vault tears its view models
/// down on every lock, so a choice held in a view model would not survive
/// locking — let alone a relaunch. `VaultNuker` clears both, so erasing the
/// vault returns the app to its just-installed order.
enum SortPreferences {
    static let albumSortKey = "albumSort.v1"
    static let noteSortKey = "noteSort.v1"

    static func albumSort(defaults: UserDefaults = .standard) -> AlbumSort {
        AlbumSort(storedRawValue: defaults.string(forKey: albumSortKey))
    }

    static func setAlbumSort(_ sort: AlbumSort, defaults: UserDefaults = .standard) {
        defaults.set(sort.rawValue, forKey: albumSortKey)
    }

    static func noteSort(defaults: UserDefaults = .standard) -> NoteSort {
        NoteSort(storedRawValue: defaults.string(forKey: noteSortKey))
    }

    static func setNoteSort(_ sort: NoteSort, defaults: UserDefaults = .standard) {
        defaults.set(sort.rawValue, forKey: noteSortKey)
    }

    /// Back to the defaults (erase everything).
    static func reset(defaults: UserDefaults = .standard) {
        defaults.removeObject(forKey: albumSortKey)
        defaults.removeObject(forKey: noteSortKey)
    }
}
