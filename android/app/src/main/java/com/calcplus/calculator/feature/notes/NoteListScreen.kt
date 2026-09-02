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
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcplus.calculator.core.domain.model.Note
import com.calcplus.calculator.core.markdown.NoteDerivation
import com.calcplus.calculator.core.ui.components.EmptyState
import com.calcplus.calculator.di.AppContainer
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteListScreen(
    container: AppContainer,
    onOpenNote: (noteId: String) -> Unit,
) {
    val viewModel: NoteListViewModel = viewModel {
        NoteListViewModel(container.noteRepository)
    }
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filterTagId by viewModel.filterTagId.collectAsStateWithLifecycle()

    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Notes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.createNote { id -> onOpenNote(id) }
            }) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
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
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.Note,
                    title = if (query.isBlank() && filterTagId == null) "No notes yet" else "No results",
                    description = if (query.isBlank() && filterTagId == null) {
                        "Notes support markdown with a live preview."
                    } else {
                        null
                    },
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(noteList, key = { it.id }) { note ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    noteToDelete = note
                                }
                                false // never auto-dismiss; deletion confirms first
                            },
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.errorContainer),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Delete",
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(end = 24.dp),
                                    )
                                }
                            },
                        ) {
                            NoteRow(note = note, onClick = { onOpenNote(note.id) })
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    noteToDelete?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToDelete = null },
            title = { Text("Delete note") },
            text = { Text("Delete this note? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(note.id)
                    noteToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { noteToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NoteRow(note: Note, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
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
