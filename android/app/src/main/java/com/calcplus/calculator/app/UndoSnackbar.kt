package com.calcplus.calculator.app

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.staticCompositionLocalOf
import com.calcplus.calculator.R
import com.calcplus.calculator.core.domain.repository.TrashItemKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The count-bearing snackbar message for one delete (decisions §10). Pure so
 * the singular/plural choice is unit-testable without a Context; [resolve]
 * turns it into the localized string.
 *
 * A singular entry takes no format argument, a plural one takes the count —
 * passing an argument to a string without a placeholder is a silent no-op on
 * Android but a crash-shaped mistake to copy, so the two are kept apart.
 */
data class UndoMessage(@param:StringRes val res: Int, val count: Int?)

object UndoMessages {
    /**
     * @param kind what was deleted; [TrashItemKind.ALBUM] is always one album
     *   (there is no bulk album delete on either platform).
     */
    fun deleted(kind: TrashItemKind, count: Int): UndoMessage = when (kind) {
        TrashItemKind.ALBUM -> UndoMessage(R.string.deleted_album, null)
        TrashItemKind.PHOTO ->
            if (count == 1) UndoMessage(R.string.deleted_photo, null)
            else UndoMessage(R.string.deleted_photos, count)
        TrashItemKind.NOTE ->
            if (count == 1) UndoMessage(R.string.deleted_note, null)
            else UndoMessage(R.string.deleted_notes, count)
        TrashItemKind.CONTACT ->
            if (count == 1) UndoMessage(R.string.deleted_contact, null)
            else UndoMessage(R.string.deleted_contacts, count)
    }

    fun resolve(context: Context, message: UndoMessage): String =
        if (message.count == null) context.getString(message.res)
        else context.getString(message.res, message.count)
}

/**
 * The vault's single undo snackbar (decisions §3). One [SnackbarHostState] is
 * hoisted into [VaultScaffold]'s outer Scaffold and handed down through
 * [LocalUndoController], so a delete performed on a detail screen still shows
 * its snackbar after that screen has popped.
 *
 * @param presentScope a scope tied to the vault: the snackbar must disappear
 *   with the vault on lock, never outlive it.
 * @param workScope a lock-surviving scope: an Undo tapped in the last moments
 *   before a lock still finishes its restore.
 */
class UndoController(
    val hostState: SnackbarHostState,
    private val presentScope: CoroutineScope,
    private val workScope: CoroutineScope,
    private val context: Context,
) {
    /**
     * Shows "<n> deleted" with an Undo action for [SnackbarDuration.Long].
     * Only [onUndo] restores anything — dismissing leaves the items in
     * "Recently deleted", where they stay for the retention period.
     */
    fun post(kind: TrashItemKind, count: Int, onUndo: suspend () -> Unit) {
        if (count <= 0) return
        val message = UndoMessages.resolve(context, UndoMessages.deleted(kind, count))
        presentScope.launch {
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = context.getString(R.string.undo_action),
                withDismissAction = false,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                workScope.launch { onUndo() }
            }
        }
    }

    /**
     * A message with **no Undo action** — "the user must see this, there is
     * nothing to undo" (N3's `video_import_failed`). Same host and same
     * duration as [post]; an Undo button that does nothing would be worse than
     * none. iOS twin: `UndoCenter.postNotice(message:)`.
     */
    fun postNotice(@StringRes res: Int) {
        val message = context.getString(res)
        presentScope.launch {
            hostState.showSnackbar(
                message = message,
                actionLabel = null,
                withDismissAction = false,
                duration = SnackbarDuration.Long,
            )
        }
    }
}

/**
 * null outside [VaultScaffold] (previews, and anything composed before unlock),
 * which makes `LocalUndoController.current?.post(…)` a safe no-op there.
 */
val LocalUndoController = staticCompositionLocalOf<UndoController?> { null }
