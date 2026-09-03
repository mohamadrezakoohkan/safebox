package com.calcplus.calculator.feature.gallery

import android.content.Context
import androidx.annotation.StringRes
import com.calcplus.calculator.R
import com.calcplus.calculator.core.domain.model.Photo
import com.calcplus.calculator.core.format.MediaFormatting

/** One labeled row of the Details sheet. */
data class PhotoInfoEntry(@param:StringRes val labelRes: Int, val value: String)

/**
 * Display model for the pager's Details sheet (N2). Row order is decided cross-platform
 * (iteration-2-decisions §8): Dimensions, File size, Type, [Duration — videos only, N3], Imported.
 */
data class PhotoInfo(
    val dimensions: String,
    val fileSize: String,
    val type: String,
    val imported: String,
    val duration: String? = null,
) {
    /** Rows in display order; the Duration row appears only when [duration] is present. */
    val entries: List<PhotoInfoEntry>
        get() = buildList {
            add(PhotoInfoEntry(R.string.photo_info_dimensions, dimensions))
            add(PhotoInfoEntry(R.string.photo_info_size, fileSize))
            add(PhotoInfoEntry(R.string.photo_info_type, type))
            duration?.let { add(PhotoInfoEntry(R.string.photo_info_duration, it)) }
            add(PhotoInfoEntry(R.string.photo_info_imported, imported))
        }

    companion object {
        /**
         * Formats the stored values of [photo]. [durationMs] is the N3 hook: pass the video's
         * duration to get a Duration row; null (every photo today) omits it.
         */
        fun from(context: Context, photo: Photo, durationMs: Long? = null): PhotoInfo = PhotoInfo(
            dimensions = MediaFormatting.dimensions(photo.width, photo.height),
            fileSize = MediaFormatting.fileSize(context, photo.byteCount),
            type = MediaFormatting.typeLabel(photo.mimeType),
            imported = MediaFormatting.dateTime(photo.importedAt),
            duration = durationMs?.let(MediaFormatting::duration),
        )
    }
}
