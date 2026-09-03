package com.calcplus.calculator.core.domain.model

import com.calcplus.calculator.core.database.entity.LabeledValue

/**
 * Values of the photo table's `mediaType` column (decisions §0/§9). Mirrors the
 * iOS `enum MediaType: String`; the raw strings are shared verbatim. N3 writes
 * [VIDEO]; until then every row is a [PHOTO].
 */
object MediaType {
    const val PHOTO = "photo"
    const val VIDEO = "video"
}

data class Album(
    val id: String,
    val name: String,
    val createdAt: Long,
    val sortIndex: Int,
    val photoCount: Int,
    val coverThumbFileName: String?, // derived: first photo by sortIndex
    /** Non-null while the album sits in "Recently deleted"; null for every live album. */
    val deletedAt: Long? = null,
)

data class Photo(
    val id: String,
    val albumId: String,
    val fileName: String,
    val thumbFileName: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val byteCount: Long,
    val importedAt: Long,
    val sortIndex: Int,
    /** Non-null while the photo sits in "Recently deleted"; null for every live photo. */
    val deletedAt: Long? = null,
    /** [MediaType.PHOTO] or [MediaType.VIDEO] (N3 reads it; every row is a photo until then). */
    val mediaType: String = MediaType.PHOTO,
    /** Videos only (N3); null for photos. */
    val durationMs: Long? = null,
) {
    /** The one place `mediaType` is compared (iOS twin: `Photo.isVideo`). */
    val isVideo: Boolean get() = mediaType == MediaType.VIDEO
}

data class Tag(
    val id: String,
    val name: String,
    val colorIndex: Int,
)

data class Note(
    val id: String,
    val body: String,
    val title: String,
    val snippet: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: List<Tag>,
    /** Non-null while the note sits in "Recently deleted"; null for every live note. */
    val deletedAt: Long? = null,
)

data class Contact(
    val id: String,
    val firstName: String?,
    val lastName: String?,
    val organization: String?,
    val phones: List<LabeledValue>,
    val emails: List<LabeledValue>,
    val address: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    /** Non-null while the contact sits in "Recently deleted"; null for every live contact. */
    val deletedAt: Long? = null,
) {
    val displayName: String
        get() {
            val name = listOfNotNull(firstName?.trim(), lastName?.trim())
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            if (name.isNotEmpty()) return name
            return organization?.trim().orEmpty()
        }

    /**
     * familyName-first sort key with fallbacks: lastName → firstName →
     * organization, through the vault's one shared case- and
     * diacritic-insensitive fold ([VaultTextFold]).
     */
    val sortKey: String
        get() {
            for (candidate in listOf(lastName, firstName, organization)) {
                val trimmed = candidate?.trim().orEmpty()
                if (trimmed.isNotEmpty()) return VaultTextFold.fold(trimmed)
            }
            return ""
        }

    /** Section header letter; non-letter/empty keys bucket under "#". */
    val sectionKey: String
        get() {
            val first = sortKey.firstOrNull() ?: return "#"
            return if (first.isLetter()) first.uppercase() else "#"
        }
}
