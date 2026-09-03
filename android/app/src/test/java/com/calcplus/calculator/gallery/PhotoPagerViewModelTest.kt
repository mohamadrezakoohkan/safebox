package com.calcplus.calculator.gallery

import android.net.Uri
import com.calcplus.calculator.core.domain.model.Album
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.Photo
import com.calcplus.calculator.core.domain.repository.AlbumRepository
import com.calcplus.calculator.core.domain.repository.ImportProgress
import com.calcplus.calculator.core.domain.repository.PhotoRepository
import com.calcplus.calculator.feature.gallery.PhotoPagerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * The pager pops itself when its photo list is empty ("all photos deleted or
 * moved: nothing to page"). That makes the INITIAL value of the flow
 * load-bearing: with `emptyList()`, the pager closed itself on its very first
 * composition, before Room had answered — the bug found on device, where no
 * photo or video could be opened at all.
 *
 * `null` therefore means "still loading" and empty means "really empty", the
 * convention every other list in the vault already uses.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhotoPagerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun photosStartNullSoThePagerDoesNotMistakeLoadingForAnEmptyAlbum() = runTest(dispatcher) {
        val viewModel = PhotoPagerViewModel(
            albumId = "album-1",
            photoRepository = FakePhotoRepository(emptyFlow()),
            albumRepository = FakeAlbumRepository(),
        )

        assertNull(
            "A pending first emission must be null, never emptyList() — the pager reads empty as 'close me'.",
            viewModel.photos.value,
        )
    }

    @Test
    fun photosBecomeTheAlbumContentsOnceRoomAnswers() = runTest(dispatcher) {
        val viewModel = PhotoPagerViewModel(
            albumId = "album-1",
            photoRepository = FakePhotoRepository(flowOf(listOf(photo("p1"), photo("p2")))),
            albumRepository = FakeAlbumRepository(),
        )

        val collector = launch { viewModel.photos.collect { } }
        advanceUntilIdle()

        assertEquals(listOf("p1", "p2"), viewModel.photos.value?.map { it.id })
        collector.cancel()
    }

    @Test
    fun anAlbumThatIsGenuinelyEmptyEmitsAnEmptyListNotNull() = runTest(dispatcher) {
        val viewModel = PhotoPagerViewModel(
            albumId = "album-1",
            photoRepository = FakePhotoRepository(flowOf(emptyList())),
            albumRepository = FakeAlbumRepository(),
        )

        val collector = launch { viewModel.photos.collect { } }
        advanceUntilIdle()

        assertEquals(emptyList<String>(), viewModel.photos.value?.map { it.id })
        collector.cancel()
    }

    private fun photo(id: String) = Photo(
        id = id,
        albumId = "album-1",
        fileName = "$id.jpg",
        thumbFileName = "$id-thumb.jpg",
        mimeType = "image/jpeg",
        width = 10,
        height = 10,
        byteCount = 100,
        importedAt = 0,
        sortIndex = 0,
    )

    /** [photos] models Room: an empty flow is "has not answered yet". */
    private class FakePhotoRepository(private val photos: Flow<List<Photo>>) : PhotoRepository {
        override fun observePhotos(albumId: String): Flow<List<Photo>> = photos
        override val importProgress: StateFlow<ImportProgress> = MutableStateFlow(ImportProgress(0, 0))
        override val videoImportFailures: Flow<Int> = emptyFlow()
        override fun import(albumId: String, uris: List<Uri>) = Unit
        override suspend fun deletePhotos(ids: List<String>) = Unit
        override suspend fun movePhotos(ids: List<String>, toAlbumId: String) = Unit
        override suspend fun restore(ids: List<String>) = Unit
        override suspend fun purge(ids: List<String>) = Unit
        override suspend fun purgeExpired(now: Long) = Unit
        override suspend fun sweepOrphans() = Unit
    }

    private class FakeAlbumRepository : AlbumRepository {
        override fun observeAlbums(sort: AlbumSort): Flow<List<Album>> = flowOf(emptyList())
        override suspend fun createAlbum(name: String) = Unit
        override suspend fun renameAlbum(id: String, name: String) = Unit
        override suspend fun deleteAlbum(id: String) = Unit
        override suspend fun restore(ids: List<String>) = Unit
        override suspend fun purge(ids: List<String>) = Unit
        override suspend fun purgeExpired(now: Long) = Unit
    }
}
