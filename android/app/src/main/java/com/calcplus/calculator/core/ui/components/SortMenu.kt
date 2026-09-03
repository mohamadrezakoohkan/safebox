package com.calcplus.calculator.core.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.calcplus.calculator.R
import com.calcplus.calculator.core.domain.model.AlbumSort
import com.calcplus.calculator.core.domain.model.NoteSort

/**
 * The shared sort control (decisions §4): a top-bar icon button opening a
 * `DropdownMenu` headed by `sort_title` ("Sort by") with a check mark on the
 * active mode. The Android twin of iOS's `SortMenu` (a `Menu` wrapping an
 * inline `Picker`), where the picker's own label renders as the menu's section
 * header — hence the non-interactive header row here, so the two menus read
 * identically instead of Android hiding "Sort by" in a content description.
 *
 * It belongs to a list's BROWSING top bar only. A selection bar (P6) must keep
 * exactly its count title, Delete and Cancel — adding sort there is a spec
 * miss.
 */
@Composable
fun <T> SortMenu(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.sort_title),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // Header, not a menu item: no click, no check mark slot. It is the
            // visible twin of iOS's inline-picker section label.
            Text(
                text = stringResource(R.string.sort_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            options.forEach { option ->
                val isActive = option == selected
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                    trailingIcon = {
                        if (isActive) Icon(Icons.Filled.Check, contentDescription = null)
                    },
                )
            }
        }
    }
}

/** §10 menu labels. Kept next to the menu so the domain enums stay resource-free. */
@get:StringRes
val AlbumSort.labelRes: Int
    get() = when (this) {
        AlbumSort.MANUAL -> R.string.sort_album_manual
        AlbumSort.NAME -> R.string.sort_name
        AlbumSort.DATE_CREATED -> R.string.sort_date_created
        AlbumSort.PHOTO_COUNT -> R.string.sort_photo_count
    }

@get:StringRes
val NoteSort.labelRes: Int
    get() = when (this) {
        NoteSort.DATE_MODIFIED -> R.string.sort_date_modified
        NoteSort.DATE_CREATED -> R.string.sort_date_created
        NoteSort.TITLE -> R.string.sort_note_title
    }
