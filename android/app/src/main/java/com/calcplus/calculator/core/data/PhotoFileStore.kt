package com.calcplus.calculator.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.calcplus.calculator.core.domain.model.MediaType
import java.io.File
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * All media file IO: original writes (byte-for-byte, real extension),
 * thumbnail/poster generation, deletes, and the startup orphan sweep (backstop
 * only). Vault bytes live only under filesDir/vault (app-private, not
 * media-scanned).
 *
 * Photos and videos share this store and one table (decisions §9): a video is
 * copied byte-for-byte like a photo and gets one extracted frame written as the
 * very same `<uuid>.jpg` ≤ [THUMBNAIL_MAX_PIXEL] thumbnail, so the grid stays
 * uniform and every later stage (ordering, trash, purge, sweep, nuke) is
 * media-type agnostic.
 *
 * @param mediaProbe reads duration / display size / poster frame out of a video.
 *   Injected because `MediaMetadataRetriever` is a native decoder with no
 *   Robolectric shadow — the unit tests push real bytes through this store with
 *   a fake probe.
 */
class PhotoFileStore(
    baseDir: File,
    private val mediaProbe: MediaProbe = MediaMetadataRetrieverProbe(),
) {
    companion object {
        const val THUMBNAIL_MAX_PIXEL = 600

        private const val VIDEO_PREFIX = "video/"
        private const val IMAGE_PREFIX = "image/"
        private const val DEFAULT_IMAGE_MIME = "image/jpeg"

        /**
         * File-name extensions that identify a video when the MIME type does
         * not (see [looksLikeVideo]). Deliberately wider than
         * [VIDEO_EXTENSION_BY_SUBTYPE]: this table only has to recognise a
         * container, not name one.
         */
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "m4v", "mov", "qt", "3gp", "3gpp", "3g2",
            "webm", "mkv", "avi", "ts", "mpeg", "mpg", "ogv",
        )

        private val EXTENSION_BY_MIME = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/heic" to "heic",
            "image/heif" to "heif",
            "image/gif" to "gif",
            "image/webp" to "webp",
            "image/bmp" to "bmp",
        )

        /**
         * Video subtype → on-disk extension (decisions §9). Anything not listed
         * keeps its own subtype as the extension, so an unusual-but-playable
         * container is still stored under a truthful name.
         */
        private val VIDEO_EXTENSION_BY_SUBTYPE = mapOf(
            "mp4" to "mp4",
            "quicktime" to "mov",
            "3gpp" to "3gp",
            "webm" to "webm",
            "x-matroska" to "mkv",
        )

        /** True for every MIME type this store files away as a video. */
        fun isVideoMime(mimeType: String?): Boolean =
            mimeType?.lowercase(Locale.ROOT)?.startsWith(VIDEO_PREFIX) == true

        /**
         * Whether a picker item is a video **for reporting purposes** — the
         * `video_import_failed` notice (decisions §9).
         *
         * [isVideoMime] alone is not enough: a picker (or a third-party
         * document provider) can hand a clip over with a missing or generic
         * MIME type, in which case the item takes the image path, fails the
         * bitmap decode and would otherwise disappear with no notice at all.
         * The name then decides.
         *
         * A declared `image/…` is never re-classified: a corrupt JPEG is a
         * photo failure and must stay silent.
         *
         * @param name the item's display name or last path segment; may be null
         *   or extensionless (a media-store picker URI often is), in which case
         *   only the MIME type can classify it.
         */
        fun looksLikeVideo(mimeType: String?, name: String?): Boolean {
            if (isVideoMime(mimeType)) return true
            val mime = mimeType.orEmpty().trim().lowercase(Locale.ROOT)
            if (mime.startsWith(IMAGE_PREFIX)) return false
            val extension = name
                .orEmpty()
                .substringBefore('?')
                .substringAfterLast('/')
                .substringAfterLast('.', "")
                .trim()
                .lowercase(Locale.ROOT)
            return extension in VIDEO_EXTENSIONS
        }

        /**
         * The extension a video of [mimeType] is stored under: the decided table
         * first, else the bare subtype (sanitised to the characters a file name
         * can safely carry), else "mp4".
         */
        fun videoExtension(mimeType: String): String {
            val subtype = mimeType
                .lowercase(Locale.ROOT)
                .substringAfter('/', "")
                .substringBefore(';')
                .trim()
            VIDEO_EXTENSION_BY_SUBTYPE[subtype]?.let { return it }
            val sanitized = subtype.filter { it.isLetterOrDigit() }
            return sanitized.ifEmpty { "mp4" }
        }
    }

    val photosDir: File = File(baseDir, "vault/photos")
    val thumbsDir: File = File(baseDir, "vault/thumbs")

    data class StoredPhoto(
        val id: String,
        val fileName: String,
        val thumbFileName: String,
        val mimeType: String,
        val width: Int,
        val height: Int,
        val byteCount: Long,
        /** [MediaType.PHOTO] or [MediaType.VIDEO]. */
        val mediaType: String = MediaType.PHOTO,
        /** Videos only; null for photos. */
        val durationMs: Long? = null,
    )

    /**
     * Copies the source stream byte-for-byte (originals are never re-encoded)
     * under the real extension, reads dimensions, and generates the thumbnail —
     * for a video MIME type, the poster frame at `min(1 s, duration / 2)`.
     * Files-first, row-second is the caller's job; on failure partial files
     * are deleted here (an orphan file is harmless; a row without a file is a
     * broken UI).
     */
    fun store(openStream: () -> InputStream?, mimeType: String?): StoredPhoto? {
        photosDir.mkdirs()
        thumbsDir.mkdirs()
        val id = UUID.randomUUID().toString()
        val isVideo = isVideoMime(mimeType)
        val mime = mimeType ?: DEFAULT_IMAGE_MIME
        val ext = if (isVideo) {
            videoExtension(mime)
        } else {
            EXTENSION_BY_MIME[mime.lowercase(Locale.ROOT)] ?: "jpg"
        }
        val photoFile = File(photosDir, "$id.$ext")
        val thumbFile = File(thumbsDir, "$id.jpg")
        try {
            val input = openStream() ?: return null
            input.use { source ->
                photoFile.outputStream().use { sink -> source.copyTo(sink) }
            }
            if (photoFile.length() == 0L) {
                photoFile.delete()
                return null
            }

            return if (isVideo) {
                storeVideo(id, photoFile, thumbFile, mime)
            } else {
                storeImage(id, photoFile, thumbFile, mime)
            }
        } catch (_: Exception) {
            photoFile.delete()
            thumbFile.delete()
            return null
        }
    }

    private fun storeImage(
        id: String,
        photoFile: File,
        thumbFile: File,
        mime: String,
    ): StoredPhoto? {
        // Dimensions without a full decode.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photoFile.path, bounds)
        val width = max(bounds.outWidth, 0)
        val height = max(bounds.outHeight, 0)
        if (width == 0 || height == 0) {
            photoFile.delete()
            return null
        }

        if (!writeImageThumbnail(photoFile, thumbFile, width, height)) {
            photoFile.delete()
            thumbFile.delete()
            return null
        }

        return StoredPhoto(
            id = id,
            fileName = photoFile.name,
            thumbFileName = thumbFile.name,
            mimeType = mime,
            width = width,
            height = height,
            byteCount = photoFile.length(),
            mediaType = MediaType.PHOTO,
            durationMs = null,
        )
    }

    /**
     * The video original is already on disk, unchanged. All that is left is
     * reading its metadata and writing the poster frame; a video with no poster
     * would be a hole in the grid, so a missing frame fails the import (exactly
     * like iOS's `StoreError.unreadableVideo`).
     */
    private fun storeVideo(
        id: String,
        photoFile: File,
        thumbFile: File,
        mime: String,
    ): StoredPhoto? {
        val metadata = mediaProbe.probe(photoFile)
        val poster = metadata?.poster
        if (metadata == null || poster == null || metadata.width <= 0 || metadata.height <= 0) {
            photoFile.delete()
            thumbFile.delete()
            return null
        }
        // The probe decoded this frame for us and hands ownership over with it
        // (nothing else can reach it — `metadata` dies with this call), so the
        // store recycles it. Without this a poster already ≤ THUMBNAIL_MAX_PIXEL
        // is never scaled, so no copy is made and the frame — a full ARGB_8888
        // bitmap — would sit in memory until the GC got to it. `writeThumbnail`
        // recycles a scaled copy separately and only recycles the source when
        // the copy is not the source itself, so this can never double-recycle.
        if (!writeThumbnail(poster, thumbFile, recycleSource = true)) {
            photoFile.delete()
            thumbFile.delete()
            return null
        }
        return StoredPhoto(
            id = id,
            fileName = photoFile.name,
            thumbFileName = thumbFile.name,
            mimeType = mime,
            width = metadata.width,
            height = metadata.height,
            byteCount = photoFile.length(),
            mediaType = MediaType.VIDEO,
            durationMs = metadata.durationMs.coerceAtLeast(0L),
        )
    }

    private fun writeImageThumbnail(
        photoFile: File,
        thumbFile: File,
        width: Int,
        height: Int,
    ): Boolean {
        val maxEdge = max(width, height)
        var sampleSize = 1
        while (maxEdge / (sampleSize * 2) >= THUMBNAIL_MAX_PIXEL) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeFile(photoFile.path, options) ?: return false
        return writeThumbnail(decoded, thumbFile, recycleSource = true)
    }

    /**
     * The ONE place a thumbnail JPEG is written — photo thumbnails and video
     * poster frames are the same `<uuid>.jpg`, longest edge ≤
     * [THUMBNAIL_MAX_PIXEL], quality 80. Anything that ever produces a third
     * kind of thumbnail calls this, rather than adding a second writer.
     *
     * @param recycleSource true when [source] was decoded for this call and
     *   nobody else holds it — both production callers (the image decode and
     *   the probe's poster frame) own their bitmap, so both pass true. When a
     *   scaled copy was made it is recycled here as well; when it was not, the
     *   copy IS the source and is recycled exactly once. Pass false only for a
     *   bitmap whose owner outlives this call.
     */
    private fun writeThumbnail(
        source: Bitmap,
        thumbFile: File,
        recycleSource: Boolean = false,
    ): Boolean {
        val scale = THUMBNAIL_MAX_PIXEL.toFloat() / max(source.width, source.height)
        val thumb = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt().coerceAtLeast(1),
                (source.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            source
        }
        thumbFile.outputStream().use { sink ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 80, sink)
        }
        if (thumb !== source) thumb.recycle()
        if (recycleSource) source.recycle()
        return thumbFile.length() > 0
    }

    /** Deletes bytes, not just rows — both the full-size file and the thumbnail. */
    fun delete(fileName: String, thumbFileName: String) {
        File(photosDir, fileName).delete()
        File(thumbsDir, thumbFileName).delete()
    }

    fun photoFile(fileName: String): File = File(photosDir, fileName)
    fun thumbFile(thumbFileName: String): File = File(thumbsDir, thumbFileName)

    /** Erase-everything path: removes every stored byte, originals and thumbs. */
    fun deleteAll() {
        photosDir.deleteRecursively()
        thumbsDir.deleteRecursively()
    }

    /** Startup backstop only (crash recovery) — never the deletion mechanism. */
    fun sweepOrphans(knownFileNames: Set<String>, knownThumbFileNames: Set<String>) {
        photosDir.listFiles()?.forEach { file ->
            if (file.name !in knownFileNames) file.delete()
        }
        thumbsDir.listFiles()?.forEach { file ->
            if (file.name !in knownThumbFileNames) file.delete()
        }
    }
}
