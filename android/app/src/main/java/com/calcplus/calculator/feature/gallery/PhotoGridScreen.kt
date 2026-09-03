package com.calcplus.calculator.feature.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.calcplus.calculator.R
import com.calcplus.calculator.app.LocalUndoController
import com.calcplus.calculator.core.domain.repository.ImportProgress
import com.calcplus.calculator.core.domain.repository.TrashItemKind
import com.calcplus.calculator.core.ui.components.EmptyState
import com.calcplus.calculator.core.ui.components.VaultEmptyStates
import com.calcplus.calculator.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGridScreen(
    container: AppContainer,
    albumId: String,
    albumName: String,
    onBack: () -> Unit,
    onOpenPhoto: (photoId: String) -> Unit,
) {
    val viewModel: PhotoGridViewModel = viewModel(key = "grid-$albumId") {
        PhotoGridViewModel(albumId, container.photoRepository, container.albumRepository)
    }
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val isSelecting by viewModel.isSelecting.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val undo = LocalUndoController.current

    var confirmDelete by remember { mutableStateOf(false) }
    var showMoveMenu by remember { mutableStateOf(false) }

    // Launch protocol (§2.3): suppression flag set immediately BEFORE launching
    // the picker; cleared on result delivery. The result hands URIs to the
    // repository (applicationScope, keyed by albumId) — not consumed by UI.
    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        container.lockManager.endExternalActivity()
        viewModel.import(uris)
    }
    val launchPicker = {
        container.lockManager.beginExternalActivity()
        // Decisions §9: the vault holds mixed media, so the picker offers both.
        // The begin/endExternalActivity protocol is unchanged — it is what keeps
        // the picker round-trip from tripping the background lock.
        pickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        )
    }

    // A video the OS handed over but could not be probed leaves no row and no
    // file; the user is told through the vault's one snackbar host (decisions §9).
    LaunchedEffect(undo) {
        container.photoRepository.videoImportFailures.collect {
            undo?.postNotice(R.string.video_import_failed)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(albumName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action),
                        )
                    }
                },
                actions = {
                    if (isSelecting) {
                        if (selection.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { showMoveMenu = true }) {
                                    Icon(Icons.Filled.Folder, contentDescription = "Move to album")
                                }
                                DropdownMenu(
                                    expanded = showMoveMenu,
                                    onDismissRequest = { showMoveMenu = false },
                                ) {
                                    albums.filter { it.id != albumId }.forEach { album ->
                                        DropdownMenuItem(
                                            text = { Text(album.name) },
                                            onClick = {
                                                showMoveMenu = false
                                                viewModel.moveSelected(album.id)
                                            },
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = { confirmDelete = true }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete_action),
                                )
                            }
                        }
                        TextButton(onClick = { viewModel.exitSelecting() }) {
                            Text(stringResource(R.string.cancel_action))
                        }
                    } else if (photos.orEmpty().isNotEmpty()) {
                        TextButton(onClick = { viewModel.startSelecting() }) {
                            Text(stringResource(R.string.select_action))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!isSelecting) {
                FloatingActionButton(onClick = launchPicker) {
                    Icon(Icons.Filled.Add, contentDescription = "Import photos")
                }
            }
        },
    ) { padding ->
        // null = first emission pending: brief blank beats a false "No photos yet".
        val photoList = photos ?: return@Scaffold
        if (photoList.isEmpty() && !importProgress.isActive) {
            EmptyState(
                modifier = Modifier.padding(padding),
                content = VaultEmptyStates.photos,
                onAction = launchPicker,
            )
        } else {
            Box(modifier = Modifier.padding(padding)) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(photoList, key = { it.id }) { photo ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable {
                                    if (isSelecting) {
                                        viewModel.toggleSelection(photo.id)
                                    } else {
                                        onOpenPhoto(photo.id)
                                    }
                                },
                        ) {
                            AsyncImage(
                                model = container.photoFileStore.thumbFile(photo.thumbFileName),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Play glyph + duration pill (bottom-START); the
                            // selection indicator below stays bottom-END.
                            if (photo.isVideo) {
                                VideoCellOverlay(durationMs = photo.durationMs)
                            }
                            if (isSelecting) {
                                Icon(
                                    imageVector = if (photo.id in selection) {
                                        Icons.Filled.CheckCircle
                                    } else {
                                        Icons.Filled.RadioButtonUnchecked
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp),
                                )
                            }
                        }
                    }
                }
                if (importProgress.isActive) {
                    // Decisions §2: with zero photos the empty state stays suppressed and this
                    // pill is the only content, so it is centered; over photos it docks at the bottom.
                    ImportProgressPill(
                        progress = importProgress,
                        modifier = Modifier.align(importPillAlignment(gridIsEmpty = photoList.isEmpty())),
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        val count = selection.size
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = {
                Text(
                    if (count == 1) stringResource(R.string.confirm_delete_photo)
                    else stringResource(R.string.confirm_delete_photos, count)
                )
            },
            text = { Text(stringResource(R.string.confirm_delete_body_trash)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val deleted = viewModel.deleteSelected()
                    undo?.post(TrashItemKind.PHOTO, deleted.size) {
                        container.photoRepository.restore(deleted)
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

/**
 * Where the import progress pill sits in the grid area: centered when the grid has nothing
 * else to show (decisions §2), docked above the bottom edge once photos are present.
 */
internal fun importPillAlignment(gridIsEmpty: Boolean): Alignment =
    if (gridIsEmpty) Alignment.Center else Alignment.BottomCenter

@Composable
private fun ImportProgressPill(progress: ImportProgress, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(24.dp)
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))
        Text(stringResource(R.string.import_progress, progress.completed, progress.total))
    }
}
