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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()

    var confirmDelete by remember { mutableStateOf(false) }
    var showMoveMenu by remember { mutableStateOf(false) }
    var initialised by remember { mutableStateOf(false) }

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
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
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
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
            // key(photo.id, currentPage): zoom state resets on page change.
            key(photo.id, pagerState.currentPage == page) {
                ZoomableImage(
                    model = container.photoFileStore.photoFile(photo.fileName),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete photo") },
            text = { Text("Delete this photo? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    currentPhoto?.let { viewModel.delete(it.id) }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
