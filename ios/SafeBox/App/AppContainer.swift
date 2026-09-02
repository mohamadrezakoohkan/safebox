import Foundation
import SwiftData

/// One DI container built at launch; parents construct child view models from
/// the dependencies they hold. No DI framework, no singletons in view models.
@MainActor
struct AppContainer {
    let modelContainer: ModelContainer
    let photoFileStore: PhotoFileStore
    let photoRepository: any PhotoRepository
    let noteRepository: any NoteRepository
    let contactRepository: any ContactRepository
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
        let importer = PhotoImporter(fileStore: fileStore, repository: photoRepository)
        let coordinator = AppLockCoordinator(
            passcodeStore: passcodeStore,
            onboardingComplete: OnboardingSentinel.isComplete()
        )
        coordinator.onLock = {
            Task { await fileStore.cleanTemporaryDirectory() }
        }
        let nuker = VaultNuker(
            modelContainer: modelContainer,
            fileStore: fileStore,
            passcodeStore: passcodeStore,
            lockCoordinator: coordinator
        )

        // Launch housekeeping: orphan sweep (backstop) + tmp cleanup.
        Task {
            await photoRepository.performOrphanSweep()
            await fileStore.cleanTemporaryDirectory()
        }

        return AppContainer(
            modelContainer: modelContainer,
            photoFileStore: fileStore,
            photoRepository: photoRepository,
            noteRepository: noteRepository,
            contactRepository: contactRepository,
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
    private(set) var stored: [CalcKey]?

    var hasPasscode: Bool { stored != nil }

    func set(sequence: [CalcKey]) async throws {
        stored = sequence
    }

    func matches(sequence: [CalcKey]) async -> Bool {
        stored == sequence
    }

    func clear() {
        stored = nil
    }
}
