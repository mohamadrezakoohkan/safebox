import Foundation
import SwiftData

/// Erase everything: all vault rows and bytes, then the passcode, then the
/// stored preferences (onboarding flag, sort modes), then the lock coordinator
/// back to first-run.
///
/// Ordering is deliberate. Content goes first and the passcode last, so a
/// process death mid-nuke reopens the app locked over an already-empty vault —
/// a fresh passcode can never inherit old content. Rows go before files so a
/// crash between the two leaves only orphan files, which the startup sweep
/// already removes.
///
/// Every row of every table goes, trashed ("Recently deleted") ones included,
/// and `deleteAll()` removes every file — so the trash can never leak past an
/// erase (decisions §3).
///
/// Rows are fetched and deleted one by one on purpose. `ModelContext.delete(
/// model:)` is a Core Data batch delete, and batch deletes refuse tables with
/// mandatory inverse relationships ("Batch delete failed due to mandatory MTM
/// nullify inverse on Note/tags") — a tagged note or a photo with an album
/// silently survived the erase. Per-object deletes maintain the inverses.
@MainActor
struct VaultNuker {
    let modelContainer: ModelContainer
    let fileStore: PhotoFileStore
    let passcodeStore: any PasscodeStore
    let lockCoordinator: AppLockCoordinator
    /// Where the onboarding flag and the sort preferences live; injectable for
    /// tests so `.standard` is never touched.
    var preferenceDefaults: UserDefaults = .standard

    func nuke() async {
        let context = ModelContext(modelContainer)
        deleteAllRows(Photo.self, in: context)
        deleteAllRows(Album.self, in: context)
        deleteAllRows(Note.self, in: context)
        deleteAllRows(Tag.self, in: context)
        deleteAllRows(Contact.self, in: context)
        try? context.save()
        await fileStore.deleteAll()
        passcodeStore.clear()
        OnboardingSentinel.reset(defaults: preferenceDefaults)
        // Album/note sort go back to their defaults too: an erase must leave
        // the app exactly as it was just after install (decisions §4).
        SortPreferences.reset(defaults: preferenceDefaults)
        lockCoordinator.reset()
    }

    private func deleteAllRows<T: PersistentModel>(_ type: T.Type, in context: ModelContext) {
        let rows = (try? context.fetch(FetchDescriptor<T>())) ?? []
        for row in rows {
            context.delete(row)
        }
    }
}
