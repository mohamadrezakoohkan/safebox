package com.calcplus.calculator.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcplus.calculator.R
import com.calcplus.calculator.app.LocalUndoController
import com.calcplus.calculator.core.domain.repository.TrashItemKind
import com.calcplus.calculator.core.markdown.NoteDerivation
import com.calcplus.calculator.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditorScreen(
    container: AppContainer,
    noteId: String,
    onBack: () -> Unit,
) {
    val viewModel: NoteEditorViewModel = viewModel(key = "editor-$noteId") {
        NoteEditorViewModel(noteId, container.noteRepository)
    }
    val note by viewModel.note.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val draft by viewModel.draftBody.collectAsStateWithLifecycle()
    val undo = LocalUndoController.current

    var showPreview by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showAddTag by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }

    note?.let { viewModel.initialiseDraft(it.body) }

    // Mandatory synchronous flush on backgrounding and on editor exit —
    // a debounced write racing the lock could otherwise be lost.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flush() }
    DisposableEffect(Unit) {
        onDispose { viewModel.flush() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        note?.title?.ifEmpty { NoteDerivation.EMPTY_TITLE_FALLBACK }
                            ?: NoteDerivation.EMPTY_TITLE_FALLBACK,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.flush()
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.flush()
                        showPreview = !showPreview
                    }) {
                        Icon(
                            if (showPreview) Icons.Filled.Edit else Icons.Filled.Visibility,
                            contentDescription = if (showPreview) "Edit" else "Preview",
                        )
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete_action),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Tag chip row: toggleable chips + "+" chip.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val noteTagIds = note?.tags?.map { it.id }?.toSet() ?: emptySet()
                allTags.forEach { tag ->
                    FilterChip(
                        selected = tag.id in noteTagIds,
                        onClick = { viewModel.toggleTag(tag) },
                        label = { Text(tag.name) },
                    )
                }
                AssistChip(
                    onClick = {
                        newTagName = ""
                        showAddTag = true
                    },
                    label = { Text("Tag") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                )
            }
            if (showPreview) {
                // Markdown preview constrained to the shared subset.
                MarkdownPreview(
                    markdown = draft.orEmpty(),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            } else {
                TextField(
                    value = draft.orEmpty(),
                    onValueChange = { viewModel.bodyChanged(it) },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = { Text("Start typing — the first line is the title") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
            }
        }
    }

    if (showAddTag) {
        AlertDialog(
            onDismissRequest = { showAddTag = false },
            title = { Text("New tag") },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    singleLine = true,
                    label = { Text("Tag name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addTag(newTagName)
                        showAddTag = false
                    },
                    enabled = newTagName.trim().isNotEmpty(),
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTag = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.confirm_delete_note)) },
            text = { Text(stringResource(R.string.confirm_delete_body_trash)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete { onBack() }
                    // The editor pops immediately; the snackbar belongs to
                    // VaultScaffold, so it lands on the notes list behind it.
                    undo?.post(TrashItemKind.NOTE, 1) {
                        container.noteRepository.restore(listOf(noteId))
                    }
                }) { Text(stringResource(R.string.delete_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
        )
    }
}
