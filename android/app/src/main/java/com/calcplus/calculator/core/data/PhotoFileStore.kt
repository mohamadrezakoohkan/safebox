package com.calcplus.calculator.core.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * All photo file IO: original writes (byte-for-byte, real extension),
 * thumbnail generation, deletes, and the startup orphan sweep (backstop only).
 * Vault bytes live only under filesDir/vault (app-private, not media-scanned).
 */
class PhotoFileStore(baseDir: File) {
    companion object {
        const val THUMBNAIL_MAX_PIXEL = 600
        private val EXTENSION_BY_MIME = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/heic" to "heic",
            "image/heif" to "heif",
            "image/gif" to "gif",
            "image/webp" to "webp",
            "image/bmp" to "bmp",
        )
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
    )

    /**
     * Copies the source stream byte-for-byte (originals are never re-encoded)
     * under the real extension, reads dimensions, and generates the thumbnail.
     * Files-first, row-second is the caller's job; on failure partial files
     * are deleted here (an orphan file is harmless; a row without a file is a
     * broken UI).
     */
    fun store(openStream: () -> InputStream?, mimeType: String?): StoredPhoto? {
        photosDir.mkdirs()
        thumbsDir.mkdirs()
        val id = UUID.randomUUID().toString()
        val mime = mimeType ?: "image/jpeg"
        val ext = EXTENSION_BY_MIME[mime.lowercase()] ?: "jpg"
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

            // Dimensions without a full decode.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photoFile.path, bounds)
            val width = max(bounds.outWidth, 0)
            val height = max(bounds.outHeight, 0)
            if (width == 0 || height == 0) {
                photoFile.delete()
                return null
            }

            if (!writeThumbnail(photoFile, thumbFile, width, height)) {
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
            )
        } catch (_: Exception) {
            photoFile.delete()
            thumbFile.delete()
            return null
        }
    }

    private fun writeThumbnail(photoFile: File, thumbFile: File, width: Int, height: Int): Boolean {
        val maxEdge = max(width, height)
        var sampleSize = 1
        while (maxEdge / (sampleSize * 2) >= THUMBNAIL_MAX_PIXEL) sampleSize *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeFile(photoFile.path, options) ?: return false
        val scale = THUMBNAIL_MAX_PIXEL.toFloat() / max(decoded.width, decoded.height)
        val thumb = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).roundToInt().coerceAtLeast(1),
                (decoded.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            decoded
        }
        thumbFile.outputStream().use { sink ->
            thumb.compress(Bitmap.CompressFormat.JPEG, 80, sink)
        }
        if (thumb !== decoded) thumb.recycle()
        decoded.recycle()
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
