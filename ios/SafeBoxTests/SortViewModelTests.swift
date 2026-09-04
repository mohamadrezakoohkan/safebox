import Foundation
import Testing
@testable import SafeBox

/// The list view models own the sort choice: read from UserDefaults at init,
/// written back on change, and therefore unaffected by a lock (which rebuilds
/// the view models) or a relaunch (decisions §4).
@MainActor
struct SortViewModelTests {
    private func makeSuite() -> (UserDefaults, String) {
        let suiteName = "test.sort.\(UUID().uuidString)"
        return (UserDefaults(suiteName: suiteName)!, suiteName)
    }

    private func makePhotoRepository() -> (SwiftDataPhotoRepository, URL) {
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("SafeBoxSortVM-\(UUID().uuidString)", isDirectory: true)
        return (SwiftDataPhotoRepository(container: ModelContainerFactory.inMemory(),
                                         fileStore: PhotoFileStore(rootURL: root)), root)
    }

    // MARK: - Albums

    @Test func albumViewModelStartsAtManualAndReordersOnChange() throws {
        let (defaults, suiteName) = makeSuite()
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let (repo, root) = makePhotoRepository()
        defer { try? FileManager.default.removeItem(at: root) }
        _ = try repo.createAlbum(name: "zulu")
        _ = try repo.createAlbum(name: "alpha")

        let viewModel = AlbumListViewModel(repository: repo, defaults: defaults)
        #expect(viewModel.sort == .manual)
        viewModel.reload()
        #expect(viewModel.albums.map(\.name) == ["zulu", "alpha"])

        viewModel.setSort(.name)
        #expect(viewModel.sort == .name)
        #expect(viewModel.albums.map(\.name) == ["alpha", "zulu"]) // reordered without a reload() call
        #expect(defaults.string(forKey: SortPreferences.albumSortKey) == "name")
    }

    @Test func albumSortSurvivesLockAndRelaunch() throws {
        let (defaults, suiteName) = makeSuite()
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let (repo, root) = makePhotoRepository()
        defer { try? FileManager.default.removeItem(at: root) }
        _ = try repo.createAlbum(name: "zulu")
        _ = try repo.createAlbum(name: "alpha")

        let beforeLock = AlbumListViewModel(repository: repo, defaults: defaults)
        beforeLock.setSort(.name)

        // Lock: MainTabView is torn down and a brand-new view model is built on
        // the next unlock — the choice comes back because it is not view state.
        let afterUnlock = AlbumListViewModel(repository: repo, defaults: defaults)
        afterUnlock.reload()
        #expect(afterUnlock.sort == .name)
        #expect(afterUnlock.albums.map(\.name) == ["alpha", "zulu"])

        // Relaunch: a fresh UserDefaults object over the same suite.
        let relaunched = AlbumListViewModel(repository: repo,
                                            defaults: UserDefaults(suiteName: suiteName)!)
        relaunched.reload()
        #expect(relaunched.sort == .name)
        #expect(relaunched.albums.map(\.name) == ["alpha", "zulu"])
    }

    @Test func albumViewModelFallsBackWhenTheStoredValueIsUnknown() {
        let (defaults, suiteName) = makeSuite()
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let (repo, root) = makePhotoRepository()
        defer { try? FileManager.default.removeItem(at: root) }
        defaults.set("largest_first", forKey: SortPreferences.albumSortKey)
        #expect(AlbumListViewModel(repository: repo, defaults: defaults).sort == .manual)
    }

    @Test func settingTheSameAlbumModeIsANoOp() throws {
        let (defaults, suiteName) = makeSuite()
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let (repo, root) = makePhotoRepository()
        defer { try? FileManager.default.removeItem(at: root) }
        let viewModel = AlbumListViewModel(repository: repo, defaults: defaults)
        viewModel.setSort(.manual)
        #expect(viewModel.sort == .manual)
        // Nothing was written, so a later default change would still apply.
        #expect(defaults.string(forKey: SortPreferences.albumSortKey) == nil)
    }

    // MARK: - Notes

    @Test func noteViewModelStartsAtDateModifiedAndAsksTheRepositoryForTheMode() {
        let (defaults, suiteName) = makeSuite()
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let repo = SpyNoteRepository()
        repo.seed(["a", "b"])

        let viewModel = NotesListViewModel(repository: repo, defaults: defaults)
        #expect(viewModel.sort == .dateModified)
        viewModel.reload()
        #expect(repo.sortsRequested == [.dateModified])

        viewModel.setSort(.title)
        #expect(viewModel.sort == .title)
        #expect(repo.sortsRequested == [.dateModified, .title]) // reloaded with the new mode
        #expect(defaults.string(forKey: SortPreferences.noteSortKey) == "title")
    }

    @Test func noteSortSurvivesLockAndRelaunch() throws {
        let (defaults, suiteName) = makeSuite()
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let repo = SwiftDataNoteRepository(container: ModelContainerFactory.inMemory())
        let zulu = try repo.createNote(body: "zulu")
        let alpha = try repo.createNote(body: "alpha")

        let beforeLock = NotesListViewModel(repository: repo, defaults: defaults)
        beforeLock.setSort(.title)

        let afterUnlock = NotesListViewModel(repository: repo, defaults: defaults)
        afterUnlock.reload()
        #expect(afterUnlock.sort == .title)
        #expect(afterUnlock.visibleNotes.map(\.id) == [alpha.id, zulu.id])

        let relaunched = NotesListViewModel(repository: repo,
                                            defaults: UserDefaults(suiteName: suiteName)!)
        relaunched.reload()
        #expect(relaunched.sort == .title)
        #expect(relaunched.visibleNotes.map(\.id) == [alpha.id, zulu.id])
    }

    @Test func noteViewModelFallsBackWhenTheStoredValueIsUnknown() {
        let (defaults, suiteName) = makeSuite()
        defer { defaults.removePersistentDomain(forName: suiteName) }
        defaults.set("alphabetical", forKey: SortPreferences.noteSortKey)
        #expect(NotesListViewModel(repository: SpyNoteRepository(), defaults: defaults).sort
                == .dateModified)
    }

    @Test func theSearchAndTagFilterKeepTheChosenOrder() throws {
        let (defaults, suiteName) = makeSuite()
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let repo = SwiftDataNoteRepository(container: ModelContainerFactory.inMemory())
        _ = try repo.createNote(body: "zebra note")
        _ = try repo.createNote(body: "apple note")
        _ = try repo.createNote(body: "banana note")

        let viewModel = NotesListViewModel(repository: repo, defaults: defaults)
        viewModel.setSort(.title)
        viewModel.reload()
        viewModel.searchText = "note"
        // The filter narrows; it must not reshuffle.
        #expect(viewModel.visibleNotes.map(\.title) == ["apple note", "banana note", "zebra note"])
    }

    // MARK: - Surfaces that only READ the album order (review polish)

    @Test func theMoveToAlbumMenuUsesTheChosenAlbumSort() throws {
        // `AlbumGridViewModel.otherAlbums` used to call the `.manual`
        // convenience, so the Move menu ignored the user's choice.
        let (defaults, suiteName) = makeSuite()
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let (repo, root) = makePhotoRepository()
        defer { try? FileManager.default.removeItem(at: root) }
        let here = try repo.createAlbum(name: "here")
        _ = try repo.createAlbum(name: "zulu")
        _ = try repo.createAlbum(name: "alpha")

        let importer = PhotoImporter(fileStore: PhotoFileStore(rootURL: root), repository: repo)
        let manual = AlbumGridViewModel(album: here, repository: repo,
                                        importer: importer, defaults: defaults)
        #expect(manual.otherAlbums.map(\.name) == ["zulu", "alpha"])

        SortPreferences.setAlbumSort(.name, defaults: defaults)
        let byName = AlbumGridViewModel(album: here, repository: repo,
                                        importer: importer, defaults: defaults)
        #expect(byName.otherAlbums.map(\.name) == ["alpha", "zulu"])
    }

    // MARK: - Erase everything

    @Test func nukingResetsBothPreferences() async throws {
        let (defaults, suiteName) = makeSuite()
        defer { defaults.removePersistentDomain(forName: suiteName) }
        let root = FileManager.default.temporaryDirectory
            .appendingPathComponent("SafeBoxSortNuke-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: root) }

        SortPreferences.setAlbumSort(.photoCount, defaults: defaults)
        SortPreferences.setNoteSort(.title, defaults: defaults)

        let passcodeStore = InMemoryPasscodeStore()
        let nuker = VaultNuker(modelContainer: ModelContainerFactory.inMemory(),
                               fileStore: PhotoFileStore(rootURL: root),
                               passcodeStore: passcodeStore,
                               lockCoordinator: AppLockCoordinator(passcodeStore: passcodeStore, appIcons: AppIconManager(icons: FakeAlternateIcons())),
                               preferenceDefaults: defaults)
        await nuker.nuke()

        #expect(defaults.string(forKey: SortPreferences.albumSortKey) == nil)
        #expect(defaults.string(forKey: SortPreferences.noteSortKey) == nil)
        #expect(SortPreferences.albumSort(defaults: defaults) == .manual)
        #expect(SortPreferences.noteSort(defaults: defaults) == .dateModified)
    }
}
