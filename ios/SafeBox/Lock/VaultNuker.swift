import Foundation
import SwiftData

/// Erase everything: all vault rows and bytes, then the passcode, then the
/// onboarding flag, then the lock coordinator back to first-run.
///
/// Ordering is deliberate. Content goes first and the passcode last, so a
/// process death mid-nuke reopens the app locked over an already-empty vault —
/// a fresh passcode can never inherit old content. Rows go before files so a
/// crash between the two leaves only orphan files, which the startup sweep
/// already removes.
@MainActor
struct VaultNuker {
    let modelContainer: ModelContainer
    let fileStore: PhotoFileStore
    let passcodeStore: any PasscodeStore
    let lockCoordinator: AppLockCoordinator

    func nuke() async {
        let context = ModelContext(modelContainer)
        try? context.delete(model: Photo.self)
        try? context.delete(model: Album.self)
        try? context.delete(model: Note.self)
        try? context.delete(model: Tag.self)
        try? context.delete(model: Contact.self)
        try? context.save()
        await fileStore.deleteAll()
        passcodeStore.clear()
        OnboardingSentinel.reset()
        lockCoordinator.reset()
    }
}
