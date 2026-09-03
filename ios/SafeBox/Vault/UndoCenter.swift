import Foundation
import Observation

/// The vault's single undo host (decisions §3). Every delete path posts one
/// entry — a count message and a closure that restores the ids just trashed —
/// and `MainTabView` renders the current entry as a toast with an Undo button
/// for ~5 s. Created by `MainTabView`, so it dies with the vault on lock; the
/// persisted "Recently deleted" screen is the durable layer underneath.
@MainActor
@Observable
final class UndoCenter {
    struct Entry: Identifiable {
        let id: UUID
        let message: String
        /// `nil` for a plain notice (N3's `video_import_failed`): same toast,
        /// same 5 s, but no Undo button because there is nothing to undo.
        let undo: (@MainActor () -> Void)?
    }

    /// Shared with Android's `SnackbarDuration.Long` (~5 s).
    static let displayDuration: Duration = .seconds(5)

    private(set) var current: Entry?

    private let displayDuration: Duration
    private var dismissTask: Task<Void, Never>?

    init(displayDuration: Duration = UndoCenter.displayDuration) {
        self.displayDuration = displayDuration
    }

    /// Replaces whatever is showing (the previous entry's undo is dropped — its
    /// items remain recoverable from Recently deleted) and restarts the timer.
    func post(message: String, undo: @escaping @MainActor () -> Void) {
        show(Entry(id: UUID(), message: message, undo: undo))
    }

    /// A message with no undo — a failure the user must see (N3's failed video
    /// import). Same host, same timer, no Undo button.
    func postNotice(message: String) {
        show(Entry(id: UUID(), message: message, undo: nil))
    }

    private func show(_ entry: Entry) {
        dismissTask?.cancel()
        current = entry
        let duration = displayDuration
        dismissTask = Task { [weak self] in
            try? await Task.sleep(for: duration)
            guard !Task.isCancelled else { return }
            self?.dismiss(entryId: entry.id)
        }
    }

    /// Runs the current entry's restore and hides the toast. A notice (no undo
    /// closure) is left alone — its toast has no Undo button to press.
    func undo() {
        guard let entry = current, let undo = entry.undo else { return }
        clear()
        undo()
    }

    func dismiss() {
        clear()
    }

    /// Timer target. The id check is what makes a stale timer harmless: a
    /// timer armed for an entry that has since been replaced (or undone) finds
    /// a different `current` and does nothing. Internal so tests can exercise
    /// the decision without waiting on the clock.
    func dismiss(entryId: UUID) {
        guard current?.id == entryId else { return }
        clear()
    }

    private func clear() {
        dismissTask?.cancel()
        dismissTask = nil
        current = nil
    }
}
