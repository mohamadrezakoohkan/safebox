package com.calcplus.calculator.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.R
import com.calcplus.calculator.core.domain.repository.TrashItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The undo snackbar's message selection (decisions §10 `deleted_*`): singular
 * strings carry no format argument, plural ones carry the count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class UndoMessagesTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun oneItemUsesTheSingularStringWithNoArgument() {
        val photo = UndoMessages.deleted(TrashItemKind.PHOTO, 1)
        assertEquals(R.string.deleted_photo, photo.res)
        assertNull(photo.count)
        assertEquals(R.string.deleted_note, UndoMessages.deleted(TrashItemKind.NOTE, 1).res)
        assertEquals(R.string.deleted_contact, UndoMessages.deleted(TrashItemKind.CONTACT, 1).res)
    }

    @Test
    fun severalItemsUseThePluralStringWithTheCount() {
        val photos = UndoMessages.deleted(TrashItemKind.PHOTO, 4)
        assertEquals(R.string.deleted_photos, photos.res)
        assertEquals(4, photos.count)
        assertEquals(7, UndoMessages.deleted(TrashItemKind.NOTE, 7).count)
        assertEquals(R.string.deleted_contacts, UndoMessages.deleted(TrashItemKind.CONTACT, 2).res)
    }

    @Test
    fun anAlbumIsAlwaysSingular() {
        // There is no bulk album delete on either platform.
        assertEquals(R.string.deleted_album, UndoMessages.deleted(TrashItemKind.ALBUM, 1).res)
        assertNull(UndoMessages.deleted(TrashItemKind.ALBUM, 3).count)
    }

    @Test
    fun resolvedMessagesReadAsTheSharedCopyTable() {
        fun message(kind: TrashItemKind, count: Int) =
            UndoMessages.resolve(context, UndoMessages.deleted(kind, count))

        assertEquals("Album deleted", message(TrashItemKind.ALBUM, 1))
        assertEquals("Photo deleted", message(TrashItemKind.PHOTO, 1))
        assertEquals("3 photos deleted", message(TrashItemKind.PHOTO, 3))
        assertEquals("Note deleted", message(TrashItemKind.NOTE, 1))
        assertEquals("2 notes deleted", message(TrashItemKind.NOTE, 2))
        assertEquals("Contact deleted", message(TrashItemKind.CONTACT, 1))
        assertEquals("5 contacts deleted", message(TrashItemKind.CONTACT, 5))
        assertEquals("Undo", context.getString(R.string.undo_action))
    }
}
