import Foundation
import SwiftData
import SwiftUI

/// One DI container built at launch; parents construct child view models from
/// the dependencies they hold. No DI framework, no singletons in view models.
@MainActor
struct AppContainer {
    let modelContainer: ModelContainer
    let photoFileStore: PhotoFileStore
    let photoRepository: any PhotoRepository
    let noteRepository: any NoteRepository
    let contactRepository: any ContactRepository
    let trashRepository: any TrashRepository
    let passcodeStore: any PasscodeStore
    let photoImporter: PhotoImporter
    let lockCoordinator: AppLockCoordinator
    let vaultNuker: VaultNuker

    static func live() -> AppContainer {
        let passcodeStore = KeychainPasscodeStore()
        // Fresh-install detection BEFORE reading hasPasscode (idea plan §2.6).
        InstallSentinel.checkAndWipeIfNeeded(defaults: .standard, passcodeStore: passcodeStore)

        let modelContainer: ModelContainer
        do {
            modelContainer = try ModelContainerFactory.live()
        } catch {
            // A corrupt store is unrecoverable in iteration 1; fall back to an
            // in-memory container rather than crashing out of the disguise.
            modelContainer = ModelContainerFactory.inMemory()
        }

        let fileStore = PhotoFileStore(rootURL: ModelContainerFactory.vaultDirectoryURL())
        let photoRepository = SwiftDataPhotoRepository(container: modelContainer, fileStore: fileStore)
        let noteRepository = SwiftDataNoteRepository(container: modelContainer)
        let contactRepository = SwiftDataContactRepository(container: modelContainer)
        let trashRepository = SwiftDataTrashRepository(photoRepository: photoRepository,
                                                       noteRepository: noteRepository,
                                                       contactRepository: contactRepository)
        let importer = PhotoImporter(fileStore: fileStore, repository: photoRepository)
        let coordinator = AppLockCoordinator(
            passcodeStore: passcodeStore,
            onboardingComplete: OnboardingSentinel.isComplete()
        )
        coordinator.onLock = {
            Task { await fileStore.cleanTemporaryDirectory() }
        }
        // Decisions §4: the onboarding sentinel is written when the FIRST
        // envelope is stored, never when the guide finishes — a process death
        // between the two would otherwise strand the user on a face they can
        // no longer choose.
        coordinator.onSetupComplete = {
            OnboardingSentinel.setComplete()
        }
        // The window-level snapshot cover renders the active face's own cover.
        SnapshotCover.shared.coverFaceProvider = { [weak coordinator] in
            guard let coordinator else { return AnyView(EmptyView()) }
            return AnyView(DisguiseCoverView(disguise: coordinator.surfaceDisguise))
        }
        let nuker = VaultNuker(
            modelContainer: modelContainer,
            fileStore: fileStore,
            passcodeStore: passcodeStore,
            lockCoordinator: coordinator
        )

        // Launch housekeeping: expired-trash purge (decisions §3: at app start
        // and on every unlock — the unlock half lives in MainTabView), then the
        // orphan sweep (backstop) + tmp cleanup + the N3 video staging area
        // (whose leftovers are only ever a crash mid-import; cleaning it on
        // lock instead would kill an import still in the picker round-trip).
        // Purge runs first so the sweep sees the final set of rows.
        Task {
            await trashRepository.purgeExpired(now: .now)
            await photoRepository.performOrphanSweep()
            await fileStore.cleanStagingDirectory()
            await fileStore.cleanTemporaryDirectory()
        }

        return AppContainer(
            modelContainer: modelContainer,
            photoFileStore: fileStore,
            photoRepository: photoRepository,
            noteRepository: noteRepository,
            contactRepository: contactRepository,
            trashRepository: trashRepository,
            passcodeStore: passcodeStore,
            photoImporter: importer,
            lockCoordinator: coordinator,
            vaultNuker: nuker
        )
    }

    static func preview() -> AppContainer {
        let modelContainer = ModelContainerFactory.inMemory()
        let tmpRoot = FileManager.default.temporaryDirectory.appendingPathComponent("SafeBoxPreview", isDirectory: true)
        let fileStore = PhotoFileStore(rootURL: tmpRoot)
        let photoRepository = SwiftDataPhotoRepository(container: modelContainer, fileStore: fileStore)
        let noteRepository = SwiftDataNoteRepository(container: modelContainer)
        let contactRepository = SwiftDataContactRepository(container: modelContainer)
        let trashRepository = SwiftDataTrashRepository(photoRepository: photoRepository,
                                                       noteRepository: noteRepository,
                                                       contactRepository: contactRepository)
        let passcodeStore = InMemoryPasscodeStore()
        let importer = PhotoImporter(fileStore: fileStore, repository: photoRepository)
        let coordinator = AppLockCoordinator(passcodeStore: passcodeStore)
        let nuker = VaultNuker(
            modelContainer: modelContainer,
            fileStore: fileStore,
            passcodeStore: passcodeStore,
            lockCoordinator: coordinator
        )
        return AppContainer(
            modelContainer: modelContainer,
            photoFileStore: fileStore,
            photoRepository: photoRepository,
            noteRepository: noteRepository,
            contactRepository: contactRepository,
            trashRepository: trashRepository,
            passcodeStore: passcodeStore,
            photoImporter: importer,
            lockCoordinator: coordinator,
            vaultNuker: nuker
        )
    }
}

/// In-memory fake used by previews (and mirrored in the test target).
@MainActor
final class InMemoryPasscodeStore: PasscodeStore {
    private(set) var stored: [String]?
    private(set) var storedAlphabet: AlphabetDescriptor?
    private(set) var storedDisguiseId: String?
    /// Makes the next `set(...)` throw, to exercise the write-failure paths.
    var failNextSet = false

    var hasPasscode: Bool { stored != nil }
    var activeDisguiseId: String? { storedDisguiseId }

    func set(tokens: [String], alphabet: AlphabetDescriptor, activeDisguiseId: String) async throws {
        if failNextSet {
            failNextSet = false
            throw CocoaError(.fileWriteUnknown)
        }
        stored = tokens
        storedAlphabet = alphabet
        storedDisguiseId = activeDisguiseId
    }

    func matches(tokens: [String]) async -> Bool {
        stored == tokens
    }

    func clear() {
        stored = nil
        storedAlphabet = nil
        storedDisguiseId = nil
    }
}
