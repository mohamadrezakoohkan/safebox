import Foundation

/// The in-memory orderings behind `AlbumSort` / `NoteSort` (decisions §4).
///
/// Sorting happens here — called from the repository on the fetched list — and
/// never in a view body. Every mode is a *total* order: the tie-breakers below
/// are the shared cross-platform ones, and the final `id` comparison makes the
/// result stable no matter what order the store hands rows back in.
///
///   albums: name → createdAt asc → id
///           date_created → id
///           photo_count → name → id
///           manual → sortIndex → id
///   notes:  title → updatedAt desc → id (empty derived titles LAST)
///           date_modified / date_created → id
@MainActor
enum VaultSorting {
    /// Case- and diacritic-insensitive comparison key. Delegates to
    /// `SearchFold` — the vault's ONE folding implementation (N1) — so A–Z
    /// ordering, `Contact.sortKey` and every search agree by construction.
    static func foldedKey(_ value: String) -> String {
        SearchFold.fold(value.trimmingCharacters(in: .whitespaces))
    }

    /// Last-resort tie-break so equal rows keep a stable, reproducible order.
    private static func idPrecedes(_ lhs: UUID, _ rhs: UUID) -> Bool {
        lhs.uuidString < rhs.uuidString
    }

    // MARK: - Albums

    /// - Parameter photoCount: LIVE photo count for an album. The caller passes
    ///   the repository's live query; `album.photos` also holds trashed photos
    ///   (P3) and must not be counted here.
    static func sorted(_ albums: [Album], by mode: AlbumSort,
                       photoCount: (Album) -> Int) -> [Album] {
        switch mode {
        case .manual:
            return albums.sorted { lhs, rhs in
                lhs.sortIndex != rhs.sortIndex
                    ? lhs.sortIndex < rhs.sortIndex
                    : idPrecedes(lhs.id, rhs.id)
            }
        case .name:
            let keys = keyed(albums, id: \.id) { foldedKey($0.name) }
            return albums.sorted { lhs, rhs in
                let l = keys[lhs.id] ?? "", r = keys[rhs.id] ?? ""
                if l != r { return l < r }
                if lhs.createdAt != rhs.createdAt { return lhs.createdAt < rhs.createdAt }
                return idPrecedes(lhs.id, rhs.id)
            }
        case .dateCreated:
            return albums.sorted { lhs, rhs in
                lhs.createdAt != rhs.createdAt
                    ? lhs.createdAt > rhs.createdAt
                    : idPrecedes(lhs.id, rhs.id)
            }
        case .photoCount:
            // Counted once per album: the live count is a query, not a field.
            let counts = keyed(albums, id: \.id, key: photoCount)
            let keys = keyed(albums, id: \.id) { foldedKey($0.name) }
            return albums.sorted { lhs, rhs in
                let lc = counts[lhs.id] ?? 0, rc = counts[rhs.id] ?? 0
                if lc != rc { return lc > rc }
                let l = keys[lhs.id] ?? "", r = keys[rhs.id] ?? ""
                if l != r { return l < r }
                return idPrecedes(lhs.id, rhs.id)
            }
        }
    }

    // MARK: - Notes

    static func sorted(_ notes: [Note], by mode: NoteSort) -> [Note] {
        switch mode {
        case .dateModified:
            return notes.sorted { lhs, rhs in
                lhs.updatedAt != rhs.updatedAt
                    ? lhs.updatedAt > rhs.updatedAt
                    : idPrecedes(lhs.id, rhs.id)
            }
        case .dateCreated:
            return notes.sorted { lhs, rhs in
                lhs.createdAt != rhs.createdAt
                    ? lhs.createdAt > rhs.createdAt
                    : idPrecedes(lhs.id, rhs.id)
            }
        case .title:
            let keys = keyed(notes, id: \.id) { foldedKey($0.title) }
            return notes.sorted { lhs, rhs in
                let l = keys[lhs.id] ?? "", r = keys[rhs.id] ?? ""
                // A note whose derived title is empty ("Untitled" in the row)
                // sorts LAST rather than first under an A–Z order.
                if l.isEmpty != r.isEmpty { return r.isEmpty }
                if l != r { return l < r }
                if lhs.updatedAt != rhs.updatedAt { return lhs.updatedAt > rhs.updatedAt }
                return idPrecedes(lhs.id, rhs.id)
            }
        }
    }

    // MARK: - Helpers

    /// Precomputes one comparison key per row, so `sorted(by:)` never derives
    /// the same key O(n log n) times (folding and the live photo count are both
    /// too expensive to repeat inside the comparator).
    private static func keyed<Row, Key>(
        _ rows: [Row], id: (Row) -> UUID, key: (Row) -> Key
    ) -> [UUID: Key] {
        Dictionary(rows.map { (id($0), key($0)) }, uniquingKeysWith: { first, _ in first })
    }
}
