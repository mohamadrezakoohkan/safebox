package com.calcplus.calculator.feature.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcplus.calculator.R
import com.calcplus.calculator.app.LocalUndoController
import com.calcplus.calculator.core.domain.repository.TrashItemKind
import com.calcplus.calculator.core.ui.components.ZoomableImage
import com.calcplus.calculator.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoPagerScreen(
    container: AppContainer,
    albumId: String,
    startPhotoId: String,
    onBack: () -> Unit,
) {
    val viewModel: PhotoPagerViewModel = viewModel(key = "pager-$albumId") {
        PhotoPagerViewModel(albumId, container.photoRepository, container.albumRepository)
    }
    val pendingPhotos by viewModel.photos.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val undo = LocalUndoController.current

    var confirmDelete by remember { mutableStateOf(false) }
    var showMoveMenu by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var initialised by remember { mutableStateOf(false) }

    // `null` = the first Room emission is still pending; render nothing rather
    // than mistaking it for an empty album and popping straight back out.
    val photos = pendingPhotos ?: return

    if (photos.isEmpty()) {
        // All photos deleted/moved: nothing to page.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = photos.indexOfFirst { it.id == startPhotoId }.coerceAtLeast(0),
        pageCount = { photos.size },
    )
    LaunchedEffect(photos.size) {
        if (initialised && pagerState.currentPage >= photos.size) {
            pagerState.scrollToPage(photos.size - 1)
        }
        initialised = true
    }
    val currentPhoto = photos.getOrNull(pagerState.currentPage)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.4f),
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back_action),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.photo_info_title),
                        )
                    }
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
                                        currentPhoto?.let { viewModel.move(it.id, album.id) }
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
                },
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
        ) { page ->
            val photo = photos[page]
            val isCurrent = pagerState.currentPage == page
            // key(photo.id, currentPage): zoom state resets on page change, and a
            // video page that stops being current is disposed — which is what
            // releases its player (PlaybackTeardownPolicy).
            key(photo.id, isCurrent) {
                if (photo.isVideo) {
                    VaultVideoPage(
                        file = container.photoFileStore.photoFile(photo.fileName),
                        isCurrent = isCurrent,
                    )
                } else {
                    ZoomableImage(
                        model = container.photoFileStore.photoFile(photo.fileName),
                    )
                }
            }
        }
    }

    // Details sheet reads the photo under the pager's current page — the same object the
    // move/delete actions target — so every row is the real stored value.
    if (showInfo) {
        currentPhoto?.let { photo ->
            PhotoInfoSheet(photo = photo, onDismiss = { showInfo = false })
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.confirm_delete_photo)) },
            text = { Text(stringResource(R.string.confirm_delete_body_trash)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    currentPhoto?.let { photo ->
                        viewModel.delete(photo.id)
                        // Deleting the last photo pops this screen; the snackbar
                        // is hosted by VaultScaffold, so it still appears (and
                        // still restores) on the grid underneath.
                        undo?.post(TrashItemKind.PHOTO, 1) {
                            container.photoRepository.restore(listOf(photo.id))
                        }
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
