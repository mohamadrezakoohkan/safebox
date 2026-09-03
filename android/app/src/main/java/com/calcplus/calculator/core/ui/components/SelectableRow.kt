package com.calcplus.calculator.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import com.calcplus.calculator.R

/**
 * Multi-select plumbing shared by the notes and contacts lists (decisions §6),
 * the Android twin of iOS `Support/SelectableRow.swift`.
 *
 * Selection state itself lives in the view models — mirroring
 * `PhotoGridViewModel.isSelecting` / `selection` — and is torn down with the
 * vault on lock, so there is no lock hook here to write or to forget.
 */

/**
 * The per-row indicator: exactly the photo grid's glyph pair, so a selected
 * note reads like a selected photo.
 *
 * It carries no content description of its own — [selectableRow] publishes the
 * row's selected state to accessibility, and a second announcement per row
 * would only be noise.
 */
@Composable
fun SelectionIndicator(isSelected: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
        contentDescription = null,
        tint = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

/**
 * Row gestures for a selectable list row: tap opens the row while browsing and
 * toggles it while selecting, long-press enters selection mode with this row
 * already selected.
 *
 * @param isSelecting whether the list is in selection mode right now.
 * @param isSelected whether THIS row is in the selection (published to
 *   accessibility so a screen reader announces the state the indicator draws).
 * @param onTap open-or-toggle; the caller decides which, since only it knows
 *   where the row navigates.
 * @param onLongPress enter selection mode. Wired only while browsing — a
 *   long-press during selection must not re-enter and re-seed the selection.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.selectableRow(
    isSelecting: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = this
    .combinedClickable(
        onClick = onTap,
        onLongClick = if (isSelecting) null else onLongPress,
    )
    .semantics { selected = isSelecting && isSelected }

/**
 * A count-bearing confirm-dialog title (decisions §6/§10). Pure so the
 * singular/plural choice is unit-testable without a Context: a singular string
 * has no placeholder and therefore carries no [count], exactly like
 * `UndoMessage`.
 */
data class ConfirmDeleteTitle(@param:StringRes val res: Int, val count: Int?)

/** One confirm dialog per selection, with the count in the message. */
object SelectionCopy {
    fun confirmDeleteNotes(count: Int): ConfirmDeleteTitle =
        if (count == 1) ConfirmDeleteTitle(R.string.confirm_delete_note, null)
        else ConfirmDeleteTitle(R.string.confirm_delete_notes, count)

    fun confirmDeleteContacts(count: Int): ConfirmDeleteTitle =
        if (count == 1) ConfirmDeleteTitle(R.string.confirm_delete_contact, null)
        else ConfirmDeleteTitle(R.string.confirm_delete_contacts, count)
}

/** Resolves a [ConfirmDeleteTitle] the same way `UndoMessages.resolve` does. */
@Composable
fun confirmDeleteTitleText(title: ConfirmDeleteTitle): String =
    if (title.count == null) stringResource(title.res)
    else stringResource(title.res, title.count)
