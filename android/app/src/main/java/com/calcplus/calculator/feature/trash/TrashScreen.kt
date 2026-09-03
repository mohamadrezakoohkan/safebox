package com.calcplus.calculator.feature.trash

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.calcplus.calculator.R
import com.calcplus.calculator.core.domain.repository.TrashItemId
import com.calcplus.calculator.core.domain.repository.TrashItemKind
import com.calcplus.calculator.core.markdown.NoteDerivation
import com.calcplus.calculator.core.ui.components.EmptyState
import com.calcplus.calculator.core.ui.components.VaultEmptyStates
import com.calcplus.calculator.di.AppContainer
import java.io.File

/**
 * "Recently deleted" (decisions §3): one screen under Settings with the four
 * sections Albums / Photos / Notes / Contacts. Every row carries BOTH a
 * Restore and a Delete now control — neither is hidden behind a swipe or a
 * long press — and neither asks for a per-row confirmation. Only the toolbar's
 * Empty (which purges everything, files included) confirms.
 *
 * This is the one place in the vault where "This cannot be undone." is still
 * true, and the only place it is still said outside erase-everything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: TrashViewModel = viewModel {
        TrashViewModel(container.trashRepository, container.applicationScope)
    }
    val contents by viewModel.contents.collectAsStateWithLifecycle()
    var confirmEmpty by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.trash_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action),
                        )
                    }
                },
                actions = {
                    val enabled = contents?.isEmpty == false
                    TextButton(onClick = { confirmEmpty = true }, enabled = enabled) {
                        Text(stringResource(R.string.trash_empty))
                    }
                },
            )
        },
    ) { padding ->
        // null = first emission pending: a brief blank beats a false "Nothing here".
        val trash = contents ?: return@Scaffold
        if (trash.isEmpty) {
            EmptyState(
                modifier = Modifier.padding(padding),
                content = VaultEmptyStates.trash,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (trash.albums.isNotEmpty()) {
                    item { TrashSectionLabel(R.string.trash_section_albums) }
                    items(trash.albums, key = { "album-${it.id}" }) { album ->
                        TrashRow(
                            title = album.name,
                            subtitle = stringResource(R.string.trash_photo_count, album.photoCount) +
                                " · " +
                                stringResource(R.string.trash_days_left, viewModel.daysLeft(album.deletedAt)),
                            thumbFile = album.coverThumbFileName?.let {
                                container.photoFileStore.thumbFile(it)
                            },
                            glyph = Icons.Filled.Photo,
                            onRestore = { viewModel.restore(TrashItemId(TrashItemKind.ALBUM, album.id)) },
                            onDeleteNow = { viewModel.purge(TrashItemId(TrashItemKind.ALBUM, album.id)) },
                        )
                    }
                }
                if (trash.photos.isNotEmpty()) {
                    item { TrashSectionLabel(R.string.trash_section_photos) }
                    items(trash.photos, key = { "photo-${it.id}" }) { photo ->
                        TrashRow(
                            title = trash.albumNames[photo.albumId]
                                ?: stringResource(R.string.trash_section_photos),
                            subtitle = stringResource(
                                R.string.trash_days_left,
                                viewModel.daysLeft(photo.deletedAt),
                            ),
                            thumbFile = container.photoFileStore.thumbFile(photo.thumbFileName),
                            glyph = Icons.Filled.Photo,
                            onRestore = { viewModel.restore(TrashItemId(TrashItemKind.PHOTO, photo.id)) },
                            onDeleteNow = { viewModel.purge(TrashItemId(TrashItemKind.PHOTO, photo.id)) },
                        )
                    }
                }
                if (trash.notes.isNotEmpty()) {
                    item { TrashSectionLabel(R.string.trash_section_notes) }
                    items(trash.notes, key = { "note-${it.id}" }) { note ->
                        TrashRow(
                            title = note.title.ifEmpty { NoteDerivation.EMPTY_TITLE_FALLBACK },
                            subtitle = stringResource(
                                R.string.trash_days_left,
                                viewModel.daysLeft(note.deletedAt),
                            ),
                            thumbFile = null,
                            glyph = Icons.AutoMirrored.Filled.Note,
                            onRestore = { viewModel.restore(TrashItemId(TrashItemKind.NOTE, note.id)) },
                            onDeleteNow = { viewModel.purge(TrashItemId(TrashItemKind.NOTE, note.id)) },
                        )
                    }
                }
                if (trash.contacts.isNotEmpty()) {
                    item { TrashSectionLabel(R.string.trash_section_contacts) }
                    items(trash.contacts, key = { "contact-${it.id}" }) { contact ->
                        TrashRow(
                            title = contact.displayName,
                            subtitle = stringResource(
                                R.string.trash_days_left,
                                viewModel.daysLeft(contact.deletedAt),
                            ),
                            thumbFile = null,
                            glyph = Icons.Filled.Person,
                            onRestore = { viewModel.restore(TrashItemId(TrashItemKind.CONTACT, contact.id)) },
                            onDeleteNow = { viewModel.purge(TrashItemId(TrashItemKind.CONTACT, contact.id)) },
                        )
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.trash_empty_confirm_title)) },
            text = { Text(stringResource(R.string.trash_empty_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    viewModel.emptyAll()
                }) {
                    Text(
                        stringResource(R.string.delete_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
        )
    }
}

@Composable
private fun TrashSectionLabel(@StringRes titleRes: Int) {
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/**
 * One trashed item. The two actions sit on their own line so both labels stay
 * fully readable at large font scales — the iOS review asked for Restore and
 * Delete now to be equally discoverable, not for one to hide behind a gesture.
 */
@Composable
private fun TrashRow(
    title: String,
    subtitle: String,
    thumbFile: File?,
    glyph: ImageVector,
    onRestore: () -> Unit,
    onDeleteNow: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (thumbFile != null) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        glyph,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onRestore) {
                Text(stringResource(R.string.trash_restore))
            }
            TextButton(onClick = onDeleteNow) {
                Text(
                    stringResource(R.string.trash_delete_now),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    HorizontalDivider()
}
