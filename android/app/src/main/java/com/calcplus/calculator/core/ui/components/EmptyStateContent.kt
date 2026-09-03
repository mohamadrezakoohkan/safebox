package com.calcplus.calculator.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import com.calcplus.calculator.R

/**
 * One vault empty state (iteration-2-decisions §2): the glyph shown in the icon circle, a title,
 * a one-line body and — only where the §2 table lists a button — an action label. Every state,
 * including the filtered "No results" ones, has all three of icon, title and body. The string
 * IDs are the shared §10 table and the glyphs follow the iOS `EmptyStateContent` presets, so the
 * same state reads identically on both platforms.
 */
data class EmptyStateContent(
    val icon: ImageVector,
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
    @param:StringRes val action: Int? = null,
)

/**
 * The vault empty states of decisions §2, plus the selectors that pick between a tab's
 * "nothing yet" state and its "No results" state while a search or filter is active.
 *
 * Glyphs: a "nothing yet" state shows its tab's glyph; every search state — "No results" under
 * a query or tag filter, and global search before a query — shows the magnifier (iOS
 * `magnifyingglass`), so filtering a tab visibly swaps the glyph as well as the copy.
 */
object VaultEmptyStates {
    /** Gallery root with no albums. */
    val albums = EmptyStateContent(
        icon = Icons.Filled.Photo,
        title = R.string.empty_albums_title,
        body = R.string.empty_albums_body,
        action = R.string.empty_albums_action,
    )

    /** An album with no photos and no import running (the grid suppresses this while importing). */
    val photos = EmptyStateContent(
        icon = Icons.Filled.Photo,
        title = R.string.empty_photos_title,
        body = R.string.empty_photos_body,
        action = R.string.empty_photos_action,
    )

    /** Notes tab with no notes and neither a search query nor a tag filter applied. */
    val notes = EmptyStateContent(
        icon = Icons.AutoMirrored.Filled.Note,
        title = R.string.empty_notes_title,
        body = R.string.empty_notes_body,
        action = R.string.empty_notes_action,
    )

    /** Contacts tab with no contacts and no search query applied. */
    val contacts = EmptyStateContent(
        icon = Icons.Filled.Person,
        title = R.string.empty_contacts_title,
        body = R.string.empty_contacts_body,
        action = R.string.empty_contacts_action,
    )

    /** A search or tag filter that matched nothing (notes, contacts, global search). No action. */
    val noResults = EmptyStateContent(
        icon = Icons.Filled.Search,
        title = R.string.empty_results_title,
        body = R.string.empty_results_body,
    )

    /** "Recently deleted" with nothing in it (P3). No action. */
    val trash = EmptyStateContent(
        icon = Icons.Filled.Delete,
        title = R.string.trash_empty_state_title,
        body = R.string.trash_empty_state_body,
    )

    /** Global search before anything has been typed (N1). No action. */
    val searchNoQuery = EmptyStateContent(
        icon = Icons.Filled.Search,
        title = R.string.search_no_query_title,
        body = R.string.search_no_query_body,
    )

    /** Notes list: "No results" whenever a query or a tag filter is active, otherwise "No notes yet". */
    fun forNotes(query: String, filterTagId: String?): EmptyStateContent =
        if (query.isBlank() && filterTagId == null) notes else noResults

    /** Contacts list: "No results" whenever a query is active, otherwise "No contacts yet". */
    fun forContacts(query: String): EmptyStateContent =
        if (query.isBlank()) contacts else noResults
}
