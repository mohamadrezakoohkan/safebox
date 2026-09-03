package com.calcplus.calculator.feature.search

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.calcplus.calculator.R
import com.calcplus.calculator.core.domain.model.SearchResult
import com.calcplus.calculator.core.ui.components.ContactAvatar
import com.calcplus.calculator.core.ui.components.EmptyState
import com.calcplus.calculator.core.ui.components.VaultEmptyStates
import com.calcplus.calculator.di.AppContainer

/**
 * One full-screen global search over the whole vault (decisions §7), reached from
 * the magnifier in the Gallery, Notes and Contacts top bars. There is no fifth
 * tab, and the bottom bar is hidden while this route is up.
 *
 * The screen only *reports* a tap: `VaultScaffold` turns the result into the
 * `VaultRouting.plan` steps, which leave search, select the target tab, reset
 * that tab to its list and push the detail. Nothing here deletes, so nothing
 * here needs the undo snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSelect: (SearchResult) -> Unit,
) {
    val viewModel: GlobalSearchViewModel = viewModel {
        GlobalSearchViewModel(
            albumRepository = container.albumRepository,
            noteRepository = container.noteRepository,
            contactRepository = container.contactRepository,
        )
    }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val hasQuery by viewModel.hasQuery.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            when {
                // Nothing typed yet: its own state, never "everything" (§2/§7).
                !hasQuery -> EmptyState(content = VaultEmptyStates.searchNoQuery)
                results.isEmpty -> EmptyState(content = VaultEmptyStates.noResults)
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    // Grouped with section headers in tab order.
                    if (results.albums.isNotEmpty()) {
                        item { SearchSectionLabel(R.string.search_section_albums) }
                        items(results.albums, key = { "album-${it.id}" }) { result ->
                            SearchRow(onClick = { onSelect(result) }) {
                                AlbumResultRow(result, container)
                            }
                        }
                    }
                    if (results.notes.isNotEmpty()) {
                        item { SearchSectionLabel(R.string.search_section_notes) }
                        items(results.notes, key = { "note-${it.id}" }) { result ->
                            SearchRow(onClick = { onSelect(result) }) { NoteResultRow(result) }
                        }
                    }
                    if (results.contacts.isNotEmpty()) {
                        item { SearchSectionLabel(R.string.search_section_contacts) }
                        items(results.contacts, key = { "contact-${it.id}" }) { result ->
                            SearchRow(onClick = { onSelect(result) }) { ContactResultRow(result) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionLabel(@StringRes titleRes: Int) {
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** One tappable result. The push happens on ANOTHER tab's back stack, not this one. */
@Composable
private fun SearchRow(onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
    HorizontalDivider()
}

/** Album card styling compacted to a row: cover tile + name + live photo count. */
@Composable
private fun AlbumResultRow(result: SearchResult, container: AppContainer) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val cover = result.thumbFileName
        if (cover != null) {
            AsyncImage(
                model = container.photoFileStore.thumbFile(cover),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.Photo,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Column(modifier = Modifier.padding(start = 12.dp)) {
        Text(result.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(
            // The §10 ID is named for the trash screen, but its English is
            // exactly the album card's count line — iOS reuses it the same way.
            stringResource(R.string.trash_photo_count, result.photoCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Notes list row styling: derived title + snippet. */
@Composable
private fun NoteResultRow(result: SearchResult) {
    Column {
        Text(result.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
        if (result.subtitle.isNotEmpty()) {
            Text(
                result.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

/** Contacts list row styling: avatar + display name (+ organization when it adds something). */
@Composable
private fun ContactResultRow(result: SearchResult) {
    ContactAvatar(contactId = result.id, displayName = result.title)
    Column(modifier = Modifier.padding(start = 14.dp)) {
        Text(result.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
        if (result.subtitle.isNotEmpty()) {
            Text(
                result.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
