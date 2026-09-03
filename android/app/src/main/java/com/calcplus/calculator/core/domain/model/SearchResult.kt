package com.calcplus.calculator.core.domain.model

import com.calcplus.calculator.core.markdown.NoteDerivation

/**
 * The three searchable entity types (decisions §7). **Photos deliberately do not
 * participate**: their file names are UUIDs and mean nothing to a user; the
 * album carries the name worth searching.
 *
 * Declaration order is the section order of the results list — Albums, Notes,
 * Contacts — and the tab order of the bottom bar.
 */
enum class SearchResultKind { ALBUM, NOTE, CONTACT }

/**
 * One global-search hit: a plain value snapshot, never a live row.
 *
 * A result crosses a navigation boundary (the search screen hands it to
 * `VaultRouting`, which rebuilds a tab's back stack from [id]), and the row it
 * points at may be trashed or purged in between — so nothing here is a
 * reference into the database.
 */
data class SearchResult(
    val kind: SearchResultKind,
    /** The entity's id — also the navigation payload. */
    val id: String,
    val title: String,
    /** Secondary line; empty when the entity has nothing to show there. */
    val subtitle: String = "",
    /** Albums only: live photo count, rendered through `trash_photo_count`. */
    val photoCount: Int = 0,
    /** Albums only: derived cover thumbnail file name, or null for an empty album. */
    val thumbFileName: String? = null,
)

/**
 * A [SearchResult] plus the strings the query is matched against. Built from the
 * live corpus and filtered in memory on every debounced keystroke.
 */
data class SearchCandidate(
    val result: SearchResult,
    val haystacks: List<String>,
)

/** Hits grouped by type, in the decided section order Albums → Notes → Contacts. */
data class SearchResults(
    val albums: List<SearchResult> = emptyList(),
    val notes: List<SearchResult> = emptyList(),
    val contacts: List<SearchResult> = emptyList(),
) {
    val isEmpty: Boolean get() = albums.isEmpty() && notes.isEmpty() && contacts.isEmpty()
    val count: Int get() = albums.size + notes.size + contacts.size

    /** Every hit in section order — the flat view of the same grouping. */
    val all: List<SearchResult> get() = albums + notes + contacts
}

/**
 * What each entity is matched against (decisions §7), in ONE place so the per-tab
 * filters and global search can never disagree about scope.
 */
object SearchHaystacks {
    /** Albums match on their name. */
    fun album(album: Album): List<String> = listOf(album.name)

    /** Global search over notes: title, body **and tag names**. */
    fun note(note: Note): List<String> =
        listOf(note.title, note.body) + note.tags.map { it.name }

    /**
     * The Notes tab's own filter: title + body only. Tag names are not included
     * because the tab already has a dedicated tag-chip filter next to the field
     * (and this is the iOS `NotesListViewModel.visibleNotes` scope verbatim).
     */
    fun noteInList(note: Note): List<String> = listOf(note.title, note.body)

    /**
     * Contacts, for both the tab filter and global search: name parts, the
     * derived display name (so "Ada Lovelace" matches across the two columns),
     * organization, and every phone and email VALUE — never the stored JSON.
     */
    fun contact(contact: Contact): List<String> =
        listOf(
            contact.firstName.orEmpty(),
            contact.lastName.orEmpty(),
            contact.displayName,
            contact.organization.orEmpty(),
        ) + contact.phones.map { it.value } + contact.emails.map { it.value }
}

/**
 * Builds the searchable snapshot of the vault. Only LIVE rows ever reach it —
 * the repositories filter `deletedAt IS NULL` (P3) — so a trashed album, note or
 * contact can never surface in a result.
 */
object SearchCorpus {
    fun build(
        albums: List<Album>,
        notes: List<Note>,
        contacts: List<Contact>,
    ): List<SearchCandidate> {
        val candidates = ArrayList<SearchCandidate>(albums.size + notes.size + contacts.size)
        albums.mapTo(candidates) { album ->
            SearchCandidate(
                result = SearchResult(
                    kind = SearchResultKind.ALBUM,
                    id = album.id,
                    title = album.name,
                    photoCount = album.photoCount,
                    thumbFileName = album.coverThumbFileName,
                ),
                haystacks = SearchHaystacks.album(album),
            )
        }
        notes.mapTo(candidates) { note ->
            SearchCandidate(
                result = SearchResult(
                    kind = SearchResultKind.NOTE,
                    id = note.id,
                    title = note.title.ifEmpty { NoteDerivation.EMPTY_TITLE_FALLBACK },
                    subtitle = note.snippet,
                ),
                haystacks = SearchHaystacks.note(note),
            )
        }
        contacts.mapTo(candidates) { contact ->
            val organization = contact.organization.orEmpty()
            SearchCandidate(
                result = SearchResult(
                    kind = SearchResultKind.CONTACT,
                    id = contact.id,
                    title = contact.displayName,
                    // For an org-only contact the display name IS the org — don't
                    // print it twice.
                    subtitle = if (organization == contact.displayName) "" else organization,
                ),
                haystacks = SearchHaystacks.contact(contact),
            )
        }
        return candidates
    }
}

/** The pure matching step: candidates + raw query → grouped results. */
object SearchMatching {
    /**
     * A blank query yields NO results (decisions §7: the empty-query state is its
     * own screen state, never "everything").
     */
    fun results(candidates: List<SearchCandidate>, query: String): SearchResults {
        val folded = SearchNormalizer.foldedQuery(query)
        if (folded.isEmpty()) return SearchResults()

        val albums = mutableListOf<SearchResult>()
        val notes = mutableListOf<SearchResult>()
        val contacts = mutableListOf<SearchResult>()
        for (candidate in candidates) {
            if (!SearchNormalizer.foldedContainsAny(candidate.haystacks, folded)) continue
            when (candidate.result.kind) {
                SearchResultKind.ALBUM -> albums += candidate.result
                SearchResultKind.NOTE -> notes += candidate.result
                SearchResultKind.CONTACT -> contacts += candidate.result
            }
        }
        return SearchResults(albums = albums, notes = notes, contacts = contacts)
    }
}
