package com.calcplus.calculator.feature.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.calcplus.calculator.R
import com.calcplus.calculator.app.LocalUndoController
import com.calcplus.calculator.core.domain.model.Album
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.repository.TrashItemKind
import com.calcplus.calculator.core.ui.components.EmptyState
import com.calcplus.calculator.core.ui.components.SortMenu
import com.calcplus.calculator.core.ui.components.VaultEmptyStates
import com.calcplus.calculator.core.ui.components.labelRes
import com.calcplus.calculator.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AlbumListScreen(
    container: AppContainer,
    onOpenAlbum: (albumId: String, albumName: String) -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel: AlbumListViewModel = viewModel {
        AlbumListViewModel(container.albumRepository, container.sortPreferences)
    }
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val undo = LocalUndoController.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var albumToRename by remember { mutableStateOf<Album?>(null) }
    var albumToDelete by remember { mutableStateOf<Album?>(null) }
    var menuAlbumId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.vault_tab_gallery)) },
                // Order is search · sort (decisions §7 / §4).
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = stringResource(R.string.search_title),
                        )
                    }
                    SortMenu(
                        options = AlbumSort.entries,
                        selected = sort,
                        label = { stringResource(it.labelRes) },
                        onSelect = viewModel::setSort,
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New album")
            }
        },
    ) { padding ->
        // null = first emission pending: brief blank beats a false "No albums yet".
        val albumList = albums ?: return@Scaffold
        if (albumList.isEmpty()) {
            EmptyState(
                modifier = Modifier.padding(padding),
                content = VaultEmptyStates.albums,
                onAction = { showCreateDialog = true },
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(albumList, key = { it.id }) { album ->
                    Box {
                        AlbumCard(
                            album = album,
                            container = container,
                            onClick = { onOpenAlbum(album.id, album.name) },
                            onLongClick = { menuAlbumId = album.id },
                        )
                        DropdownMenu(
                            expanded = menuAlbumId == album.id,
                            onDismissRequest = { menuAlbumId = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    menuAlbumId = null
                                    albumToRename = album
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = {
                                    menuAlbumId = null
                                    albumToDelete = album
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        NameDialog(
            title = "New album",
            confirmLabel = "Create",
            initial = "",
            onConfirm = {
                viewModel.createAlbum(it)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }
    albumToRename?.let { album ->
        NameDialog(
            title = "Rename album",
            confirmLabel = "Rename",
            initial = album.name,
            onConfirm = {
                viewModel.renameAlbum(album.id, it)
                albumToRename = null
            },
            onDismiss = { albumToRename = null },
        )
    }
    albumToDelete?.let { album ->
        // `photoCount` is the LIVE count (trashed photos are excluded by the
        // DAO), which is exactly what the album delete is about to stamp.
        AlertDialog(
            onDismissRequest = { albumToDelete = null },
            title = { Text(stringResource(R.string.confirm_delete_album, album.photoCount)) },
            text = { Text(stringResource(R.string.confirm_delete_body_trash)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAlbum(album.id)
                    albumToDelete = null
                    // Undo goes straight to the repository, not the view model:
                    // it must still work if this screen is gone by then.
                    undo?.post(TrashItemKind.ALBUM, 1) {
                        container.albumRepository.restore(listOf(album.id))
                    }
                }) { Text(stringResource(R.string.delete_action)) }
            },
            dismissButton = {
                TextButton(onClick = { albumToDelete = null }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: Album,
    container: AppContainer,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val cover = album.coverThumbFileName
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
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "${album.photoCount} photos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun NameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.trim().isNotEmpty(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
