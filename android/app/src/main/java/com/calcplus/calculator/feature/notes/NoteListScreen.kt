package com.calcplus.calculator.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcplus.calculator.R
import com.calcplus.calculator.app.LocalUndoController
import com.calcplus.calculator.core.domain.model.Note
import com.calcplus.calculator.core.domain.model.NoteSort
import com.calcplus.calculator.core.domain.repository.TrashItemKind
import com.calcplus.calculator.core.markdown.NoteDerivation
import com.calcplus.calculator.core.ui.components.EmptyState
import com.calcplus.calculator.core.ui.components.SelectionCopy
import com.calcplus.calculator.core.ui.components.SelectionIndicator
import com.calcplus.calculator.core.ui.components.SortMenu
import com.calcplus.calculator.core.ui.components.VaultEmptyStates
import com.calcplus.calculator.core.ui.components.labelRes
import com.calcplus.calculator.core.ui.components.confirmDeleteTitleText
import com.calcplus.calculator.core.ui.components.selectableRow
import com.calcplus.calculator.di.AppContainer
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteListScreen(
    container: AppContainer,
    onOpenNote: (noteId: String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel: NoteListViewModel = viewModel {
        NoteListViewModel(container.noteRepository, container.sortPreferences)
    }
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filterTagId by viewModel.filterTagId.collectAsStateWithLifecycle()
    val isSelecting by viewModel.isSelecting.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val undo = LocalUndoController.current

    // ONE dialog for every delete entry point on this screen: the swipe (one
    // note) and the selection bar (the whole batch). Adding a third entry point
    // means adding a case here, never a second dialog.
    var pendingDelete by remember { mutableStateOf<PendingNoteDelete?>(null) }

    Scaffold(
        topBar = {
            if (isSelecting) {
                // Selection mode (decisions §6): count title, Delete disabled at
                // zero, Cancel exits and clears. Nothing else belongs here — a
                // sort or search control over a selection is a spec miss.
                TopAppBar(
                    title = { Text(stringResource(R.string.selection_count, selection.size)) },
                    actions = {
                        IconButton(
                            onClick = { pendingDelete = PendingNoteDelete.Selection },
                            enabled = selection.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete_action),
                            )
                        }
                        TextButton(onClick = { viewModel.exitSelecting() }) {
                            Text(stringResource(R.string.cancel_action))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.vault_tab_notes)) },
                    // Browsing bar only — neither search nor sort may appear in
                    // the selection bar above. Order is search · sort.
                    actions = {
                        IconButton(onClick = onOpenSearch) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.search_title),
                            )
                        }
                        SortMenu(
                            options = NoteSort.entries,
                            selected = sort,
                            label = { stringResource(it.labelRes) },
                            onSelect = viewModel::setSort,
                        )
                    },
                )
            }
        },
        floatingActionButton = {
            if (!isSelecting) {
                FloatingActionButton(onClick = {
                    viewModel.createNote { id -> onOpenNote(id) }
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "New note")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            if (tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = filterTagId == null,
                        onClick = { viewModel.setFilterTag(null) },
                        label = { Text("All") },
                    )
                    tags.forEach { tag ->
                        FilterChip(
                            selected = filterTagId == tag.id,
                            onClick = {
                                viewModel.setFilterTag(if (filterTagId == tag.id) null else tag.id)
                            },
                            label = { Text(tag.name) },
                        )
                    }
                }
            }
            // null = first emission pending: brief blank beats a false "No notes yet".
            val noteList = notes
            if (noteList == null) {
                // loading — search field and chips stay interactive above
            } else if (noteList.isEmpty()) {
                // "No notes yet" with a New note action, or "No results" under a query / tag
                // filter — the content decides whether the action button renders.
                EmptyState(
                    content = VaultEmptyStates.forNotes(query, filterTagId),
                    onAction = { viewModel.createNote { id -> onOpenNote(id) } },
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(noteList, key = { it.id }) { note ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    pendingDelete = PendingNoteDelete.Single(note)
                                }
                                false // never auto-dismiss; deletion confirms first
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            // Swipe-to-delete is off while selecting (decisions §6):
                            // a horizontal drag there belongs to nothing.
                            enableDismissFromEndToStart = !isSelecting,
                            backgroundContent = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.errorContainer),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(R.string.delete_action),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(end = 24.dp),
                                    )
                                }
                            },
                        ) {
                            NoteRow(
                                note = note,
                                isSelecting = isSelecting,
                                isSelected = note.id in selection,
                                onTap = {
                                    if (isSelecting) viewModel.toggleSelection(note.id)
                                    else onOpenNote(note.id)
                                },
                                onLongPress = { viewModel.startSelecting(note.id) },
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    pendingDelete?.let { pending ->
        val ids = when (pending) {
            is PendingNoteDelete.Single -> listOf(pending.note.id)
            PendingNoteDelete.Selection -> selection.toList()
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(confirmDeleteTitleText(SelectionCopy.confirmDeleteNotes(ids.size))) },
            text = { Text(stringResource(R.string.confirm_delete_body_trash)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    // Bulk delete is ONE repository call with every id (one
                    // shared stamp); the single case reuses the same tail.
                    val deleted = when (pending) {
                        is PendingNoteDelete.Single -> {
                            viewModel.delete(pending.note.id)
                            listOf(pending.note.id)
                        }
                        PendingNoteDelete.Selection -> viewModel.deleteSelected()
                    }
                    // Undo goes straight to the repository, never through the
                    // view model: this screen may be gone by the time it fires.
                    undo?.post(TrashItemKind.NOTE, deleted.size) {
                        container.noteRepository.restore(deleted)
                    }
                }) { Text(stringResource(R.string.delete_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
        )
    }
}

/** The two delete entry points this screen offers, sharing one dialog. */
private sealed interface PendingNoteDelete {
    data class Single(val note: Note) : PendingNoteDelete
    data object Selection : PendingNoteDelete
}

@Composable
private fun NoteRow(
    note: Note,
    isSelecting: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .selectableRow(
                isSelecting = isSelecting,
                isSelected = isSelected,
                onTap = onTap,
                onLongPress = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelecting) {
            SelectionIndicator(
                isSelected = isSelected,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(24.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.title.ifEmpty { NoteDerivation.EMPTY_TITLE_FALLBACK },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            if (note.snippet.isNotEmpty()) {
                Text(
                    text = note.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(note.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                note.tags.forEach { tag ->
                    TagChip(name = tag.name, colorIndex = tag.colorIndex)
                }
            }
        }
    }
}

private val TagPalette = listOf(
    Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFFD81B60), Color(0xFF00897B),
)

@Composable
fun TagChip(name: String, colorIndex: Int, onClick: (() -> Unit)? = null) {
    val color = TagPalette[Math.floorMod(colorIndex, TagPalette.size)]
    Text(
        text = name,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .padding(start = 6.dp)
            .background(color.copy(alpha = 0.15f), MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
