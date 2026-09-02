package com.calcplus.calculator.feature.contacts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcplus.calculator.core.domain.model.Contact
import com.calcplus.calculator.core.ui.components.EmptyState
import com.calcplus.calculator.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactListScreen(
    container: AppContainer,
    onOpenContact: (contactId: String) -> Unit,
    onCreateContact: () -> Unit,
) {
    val viewModel: ContactListViewModel = viewModel {
        ContactListViewModel(container.contactRepository)
    }
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Contacts") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateContact) {
                Icon(Icons.Filled.Add, contentDescription = "Add contact")
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
                EmptyState(
                    icon = Icons.Filled.Person,
                    title = if (query.isBlank()) "No contacts yet" else "No results",
                    description = if (query.isBlank()) "Contacts live only in this vault." else null,
                    actionLabel = if (query.isBlank()) "Add contact" else null,
                    onAction = if (query.isBlank()) onCreateContact else null,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    sectionList.forEach { (letter, contacts) ->
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
                            ContactRow(contact = contact, onClick = { onOpenContact(contact.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatarColor = AvatarPalette[Math.floorMod(contact.id.hashCode(), AvatarPalette.size)]
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(avatarColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contact.displayName.take(1).uppercase().ifEmpty { "#" },
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Text(
            text = contact.displayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}

private val AvatarPalette = listOf(
    Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF6C00),
    Color(0xFF7E57C2), Color(0xFFC2185B), Color(0xFF00838F),
)
