package com.calcplus.calculator.core.domain.model

import com.calcplus.calculator.core.database.entity.LabeledValue
import java.text.Normalizer

data class Album(
    val id: String,
    val name: String,
    val createdAt: Long,
    val sortIndex: Int,
    val photoCount: Int,
    val coverThumbFileName: String?, // derived: first photo by sortIndex
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
)

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
) {
    val displayName: String
        get() {
            val name = listOfNotNull(firstName?.trim(), lastName?.trim())
                .filter { it.isNotEmpty() }
                .joinToString(" ")
            if (name.isNotEmpty()) return name
            return organization?.trim().orEmpty()
        }

    /** familyName-first sort key with fallbacks: lastName → firstName → organization. */
    val sortKey: String
        get() {
            for (candidate in listOf(lastName, firstName, organization)) {
                val trimmed = candidate?.trim().orEmpty()
                if (trimmed.isNotEmpty()) {
                    return Normalizer.normalize(trimmed, Normalizer.Form.NFD)
                        .replace(Regex("\\p{Mn}+"), "")
                        .lowercase()
                }
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
