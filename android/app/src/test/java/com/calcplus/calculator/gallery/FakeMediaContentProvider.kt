package com.calcplus.calculator.gallery

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * The only thing the import path asks a picker URI for is its MIME type
 * (`ContentResolver.getType`); the bytes come from `openInputStream`, which the
 * tests register directly on Robolectric's shadow resolver. This provider exists
 * so `getType` has somewhere to answer from.
 */
class FakeMediaContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? = TYPES[uri.toString()]

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        const val AUTHORITY = "com.calcplus.calculator.test.media"

        private val TYPES = ConcurrentHashMap<String, String>()

        /**
         * Mints a picker-shaped URI that reports [mimeType] — or, with a null
         * [mimeType], one whose `getType` answers null the way a provider that
         * will not say does. The last path segment is [name], which is all the
         * import path has left to classify such an item by.
         */
        fun uri(name: String, mimeType: String?): Uri {
            val uri = Uri.parse("content://$AUTHORITY/$name")
            if (mimeType == null) TYPES.remove(uri.toString()) else TYPES[uri.toString()] = mimeType
            return uri
        }

        fun clear() {
            TYPES.clear()
        }
    }
}
