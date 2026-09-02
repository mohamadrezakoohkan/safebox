package com.calcplus.calculator.feature.contacts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.calcplus.calculator.di.AppContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditScreen(
    container: AppContainer,
    contactId: String?,
    onDone: () -> Unit,
) {
    val viewModel: ContactEditViewModel = viewModel(key = "edit-${contactId ?: "new"}") {
        ContactEditViewModel(contactId, container.contactRepository)
    }
    val form by viewModel.form.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (contactId == null) "New contact" else "Edit contact") },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("Cancel") }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onSaved = onDone) },
                        enabled = form.isValid,
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (!form.loaded) return@Scaffold
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = form.firstName,
                onValueChange = { v -> viewModel.update { it.copy(firstName = v) } },
                label = { Text("First name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.lastName,
                onValueChange = { v -> viewModel.update { it.copy(lastName = v) } },
                label = { Text("Last name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.organization,
                onValueChange = { v -> viewModel.update { it.copy(organization = v) } },
                label = { Text("Organization") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader("Phones")
            form.phones.forEach { row ->
                LabeledValueRow(
                    row = row,
                    keyboardType = KeyboardType.Phone,
                    placeholder = "Phone number",
                    onLabelChange = { label ->
                        viewModel.update { state ->
                            state.copy(phones = state.phones.map { if (it.id == row.id) it.copy(label = label) else it })
                        }
                    },
                    onValueChange = { value ->
                        viewModel.update { state ->
                            state.copy(phones = state.phones.map { if (it.id == row.id) it.copy(value = value) else it })
                        }
                    },
                    onRemove = { viewModel.removePhone(row.id) },
                )
            }
            TextButton(onClick = { viewModel.addPhone() }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add phone")
            }

            SectionHeader("Emails")
            form.emails.forEach { row ->
                LabeledValueRow(
                    row = row,
                    keyboardType = KeyboardType.Email,
                    placeholder = "Email",
                    onLabelChange = { label ->
                        viewModel.update { state ->
                            state.copy(emails = state.emails.map { if (it.id == row.id) it.copy(label = label) else it })
                        }
                    },
                    onValueChange = { value ->
                        viewModel.update { state ->
                            state.copy(emails = state.emails.map { if (it.id == row.id) it.copy(value = value) else it })
                        }
                    },
                    onRemove = { viewModel.removeEmail(row.id) },
                )
            }
            TextButton(onClick = { viewModel.addEmail() }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add email")
            }

            SectionHeader("Address")
            OutlinedTextField(
                value = form.address,
                onValueChange = { v -> viewModel.update { it.copy(address = v) } },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
            )
            SectionHeader("Notes")
            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun LabeledValueRow(
    row: ContactEditViewModel.EditableRow,
    keyboardType: KeyboardType,
    placeholder: String,
    onLabelChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        var labelMenu by remember { mutableStateOf(false) }
        TextButton(onClick = { labelMenu = true }, modifier = Modifier.width(100.dp)) {
            Text(row.label)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Change label")
        }
        DropdownMenu(expanded = labelMenu, onDismissRequest = { labelMenu = false }) {
            ContactEditViewModel.LABELS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        labelMenu = false
                        onLabelChange(option)
                    },
                )
            }
        }
        OutlinedTextField(
            value = row.value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, contentDescription = "Remove")
        }
    }
}
