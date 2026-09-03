package com.calcplus.calculator.feature.contacts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcplus.calculator.R
import com.calcplus.calculator.app.LocalUndoController
import com.calcplus.calculator.core.domain.model.Contact
import com.calcplus.calculator.core.domain.repository.TrashItemKind
import com.calcplus.calculator.core.ui.components.ContactAvatar
import com.calcplus.calculator.core.ui.components.EmptyState
import com.calcplus.calculator.core.ui.components.SelectionCopy
import com.calcplus.calculator.core.ui.components.SelectionIndicator
import com.calcplus.calculator.core.ui.components.VaultEmptyStates
import com.calcplus.calculator.core.ui.components.confirmDeleteTitleText
import com.calcplus.calculator.core.ui.components.selectableRow
import com.calcplus.calculator.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactListScreen(
    container: AppContainer,
    onOpenContact: (contactId: String) -> Unit,
    onCreateContact: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val viewModel: ContactListViewModel = viewModel {
        ContactListViewModel(container.contactRepository)
    }
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isSelecting by viewModel.isSelecting.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val undo = LocalUndoController.current

    var confirmDeleteSelection by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (isSelecting) {
                // Selection mode (decisions §6): count title, Delete disabled at
                // zero, Cancel exits and clears.
                TopAppBar(
                    title = { Text(stringResource(R.string.selection_count, selection.size)) },
                    actions = {
                        IconButton(
                            onClick = { confirmDeleteSelection = true },
                            enabled = selection.isNotEmpty(),
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete_action),
                            )
                        }
                        TextButton(onClick = { viewModel.exitSelecting() }) {
                            Text(stringResource(R.string.cancel_action))
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.vault_tab_contacts)) },
                    // Browsing bar only. Contacts has no sort (decisions §4
                    // covers albums and notes), so global search is the whole
                    // row — and it must never appear in the selection bar above.
                    actions = {
                        IconButton(onClick = onOpenSearch) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.search_title),
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!isSelecting) {
                FloatingActionButton(onClick = onCreateContact) {
                    Icon(Icons.Filled.Add, contentDescription = "Add contact")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            // null = first emission pending: brief blank beats a false "No contacts yet".
            val sectionList = sections
            if (sectionList == null) {
                // loading — the search field stays interactive above
            } else if (sectionList.isEmpty()) {
                // "No contacts yet" with an Add contact action, or "No results" under a query —
                // the content decides whether the action button renders.
                EmptyState(
                    content = VaultEmptyStates.forContacts(query),
                    onAction = onCreateContact,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    sectionList.forEach { (letter, contacts) ->
                        // Sticky section headers are NOT selectable (decisions
                        // §6) — they carry no gesture at all, by construction.
                        stickyHeader {
                            Text(
                                text = letter,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        items(contacts, key = { it.id }) { contact ->
                            ContactRow(
                                contact = contact,
                                isSelecting = isSelecting,
                                isSelected = contact.id in selection,
                                onTap = {
                                    if (isSelecting) viewModel.toggleSelection(contact.id)
                                    else onOpenContact(contact.id)
                                },
                                onLongPress = { viewModel.startSelecting(contact.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmDeleteSelection) {
        // ONE dialog for the whole selection, with the count in the title.
        val ids = selection.toList()
        AlertDialog(
            onDismissRequest = { confirmDeleteSelection = false },
            title = { Text(confirmDeleteTitleText(SelectionCopy.confirmDeleteContacts(ids.size))) },
            text = { Text(stringResource(R.string.confirm_delete_body_trash)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteSelection = false
                    // ONE repository call with every id (one shared stamp).
                    val deleted = viewModel.deleteSelected()
                    // Undo goes straight to the repository, never through the
                    // view model: this screen may be gone by the time it fires.
                    undo?.post(TrashItemKind.CONTACT, deleted.size) {
                        container.contactRepository.restore(deleted)
                    }
                }) { Text(stringResource(R.string.delete_action)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteSelection = false }) {
                    Text(stringResource(R.string.cancel_action))
                }
            },
        )
    }
}

@Composable
private fun ContactRow(
    contact: Contact,
    isSelecting: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectableRow(
                isSelecting = isSelecting,
                isSelected = isSelected,
                onTap = onTap,
                onLongPress = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelecting) {
            SelectionIndicator(
                isSelected = isSelected,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(24.dp),
            )
        }
        ContactAvatar(contactId = contact.id, displayName = contact.displayName)
        Text(
            text = contact.displayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

