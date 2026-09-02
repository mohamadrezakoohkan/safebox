package com.calcplus.calculator.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.calcplus.calculator.core.database.entity.LabeledValue
import com.calcplus.calculator.core.domain.model.Contact
import com.calcplus.calculator.core.domain.repository.ContactRepository
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ContactListViewModel(
    private val repository: ContactRepository,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Alphabetical sections, familyName-first sort keys, "#" bucket last.
     * null = first Room emission pending — render nothing, never a false empty state.
     */
    val sections: StateFlow<List<Pair<String, List<Contact>>>?> = _query
        .debounce(300)
        .flatMapLatest { repository.observeContacts(it.trim()) }
        .map { contacts ->
            contacts.groupBy { it.sectionKey }
                .toList()
                .sortedWith(compareBy({ it.first == "#" }, { it.first }))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) {
        _query.value = value
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}

class ContactDetailViewModel(
    contactId: String,
    private val repository: ContactRepository,
) : ViewModel() {
    val contact: StateFlow<Contact?> = repository.observeContact(contactId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun delete(onDeleted: () -> Unit) {
        val id = contact.value?.id ?: return
        viewModelScope.launch {
            repository.delete(id)
            onDeleted()
        }
    }
}

class ContactEditViewModel(
    private val contactId: String?,
    private val repository: ContactRepository,
) : ViewModel() {
    data class EditableRow(val id: String = UUID.randomUUID().toString(), val label: String, val value: String)

    data class FormState(
        val firstName: String = "",
        val lastName: String = "",
        val organization: String = "",
        val phones: List<EditableRow> = emptyList(),
        val emails: List<EditableRow> = emptyList(),
        val address: String = "",
        val notes: String = "",
        val loaded: Boolean = false,
    ) {
        /** At least one of firstName / lastName / organization non-blank. */
        val isValid: Boolean
            get() = listOf(firstName, lastName, organization).any { it.isNotBlank() }
    }

    companion object {
        val LABELS = listOf("mobile", "home", "work", "other")
    }

    private val _form = MutableStateFlow(FormState(loaded = contactId == null))
    val form: StateFlow<FormState> = _form.asStateFlow()

    private var existing: Contact? = null

    init {
        if (contactId != null) {
            viewModelScope.launch {
                val contact = repository.observeContact(contactId).first()
                existing = contact
                if (contact != null) {
                    _form.value = FormState(
                        firstName = contact.firstName.orEmpty(),
                        lastName = contact.lastName.orEmpty(),
                        organization = contact.organization.orEmpty(),
                        phones = contact.phones.map { EditableRow(label = it.label, value = it.value) },
                        emails = contact.emails.map { EditableRow(label = it.label, value = it.value) },
                        address = contact.address.orEmpty(),
                        notes = contact.notes.orEmpty(),
                        loaded = true,
                    )
                } else {
                    _form.value = _form.value.copy(loaded = true)
                }
            }
        }
    }

    fun update(transform: (FormState) -> FormState) {
        _form.value = transform(_form.value)
    }

    fun addPhone() = update { it.copy(phones = it.phones + EditableRow(label = "mobile", value = "")) }
    fun addEmail() = update { it.copy(emails = it.emails + EditableRow(label = "home", value = "")) }
    fun removePhone(id: String) = update { it.copy(phones = it.phones.filterNot { row -> row.id == id }) }
    fun removeEmail(id: String) = update { it.copy(emails = it.emails.filterNot { row -> row.id == id }) }

    fun save(onSaved: () -> Unit) {
        val state = _form.value
        if (!state.isValid) return
        fun normalized(s: String): String? = s.trim().ifEmpty { null }
        val phones = state.phones
            .filter { it.value.isNotBlank() }
            .map { LabeledValue(it.label, it.value.trim()) }
        val emails = state.emails
            .filter { it.value.isNotBlank() }
            .map { LabeledValue(it.label, it.value.trim()) }
        val now = System.currentTimeMillis()
        val contact = Contact(
            id = existing?.id ?: contactId ?: UUID.randomUUID().toString(),
            firstName = normalized(state.firstName),
            lastName = normalized(state.lastName),
            organization = normalized(state.organization),
            phones = phones,
            emails = emails,
            address = normalized(state.address),
            notes = normalized(state.notes),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        viewModelScope.launch {
            repository.upsert(contact)
            onSaved()
        }
    }
}
