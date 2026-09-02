import SwiftUI

struct ContactsListScreen: View {
    @State private var viewModel: ContactsListViewModel
    let container: AppContainer

    @State private var contactToDelete: Contact?
    @State private var showCreate = false

    init(viewModel: ContactsListViewModel, container: AppContainer) {
        _viewModel = State(initialValue: viewModel)
        self.container = container
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.contacts.isEmpty {
                    ContentUnavailableView {
                        Label("No contacts yet", systemImage: "person.crop.circle")
                    } actions: {
                        Button("Add contact") { showCreate = true }
                            .buttonStyle(.borderedProminent)
                    }
                } else if viewModel.visibleContacts.isEmpty {
                    ContentUnavailableView.search
                } else {
                    contactsList
                }
            }
            .navigationTitle("Contacts")
            .toolbar {
                Button { showCreate = true } label: { Image(systemName: "plus") }
            }
            .searchable(text: Bindable(viewModel).searchText)
            .sheet(isPresented: $showCreate, onDismiss: { viewModel.reload() }) {
                NavigationStack {
                    ContactEditScreen(viewModel: ContactEditViewModel(contact: nil,
                                                                      repository: viewModel.repository))
                }
            }
            .confirmationDialog("Delete this contact? This cannot be undone.",
                                isPresented: Binding(
                                    get: { contactToDelete != nil },
                                    set: { if !$0 { contactToDelete = nil } }
                                ), titleVisibility: .visible) {
                Button("Delete", role: .destructive) {
                    if let contact = contactToDelete { viewModel.delete(contact) }
                    contactToDelete = nil
                }
                Button("Cancel", role: .cancel) { contactToDelete = nil }
            }
            .onAppear { viewModel.reload() }
        }
    }

    private var contactsList: some View {
        List {
            ForEach(viewModel.sections, id: \.key) { section in
                Section(section.key) {
                    ForEach(section.contacts) { contact in
                        NavigationLink {
                            ContactDetailScreen(contact: contact,
                                                repository: viewModel.repository,
                                                onChanged: { viewModel.reload() })
                        } label: {
                            Text(contact.displayName)
                        }
                        .swipeActions {
                            Button("Delete", role: .destructive) { contactToDelete = contact }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}
