import SwiftUI

/// Root of the Contacts tab's stack. The `NavigationStack` lives in
/// `MainTabView` (its path is owned by `VaultNavigator`, so global search can
/// reset this tab to the list and push a detail); this screen declares the
/// destinations.
struct ContactsListScreen: View {
    @State private var viewModel: ContactsListViewModel
    let container: AppContainer
    @Environment(UndoCenter.self) private var undoCenter: UndoCenter?
    /// Optional so previews (and any host without `MainTabView` above) render.
    @Environment(VaultNavigator.self) private var navigator: VaultNavigator?

    /// What the single confirm dialog is about: one contact (swipe or the
    /// detail screen's Delete), or the whole selection (P6).
    private enum PendingDelete {
        case single(Contact)
        case selection
    }

    @State private var pendingDelete: PendingDelete?
    @State private var showCreate = false

    init(viewModel: ContactsListViewModel, container: AppContainer) {
        _viewModel = State(initialValue: viewModel)
        self.container = container
    }

    var body: some View {
        Group {
            if viewModel.visibleContacts.isEmpty {
                // "No contacts yet" with an Add contact action, or "No
                // results" under a query — the content decides whether the
                // action button renders (decisions §2).
                EmptyStateView(.forContacts(query: viewModel.searchText)) { showCreate = true }
            } else {
                contactsList
            }
        }
        .navigationTitle(viewModel.isSelecting
                         ? VaultCopy.selectionCount(viewModel.selection.count)
                         : VaultCopy.vaultTabContacts)
        .navigationBarTitleDisplayMode(viewModel.isSelecting ? .inline : .automatic)
        .toolbar { toolbarContent }
        // Browsing only: selection mode keeps exactly the count title, Cancel
        // and Delete (§6). The query survives the round trip.
        .searchableWhileBrowsing(isSelecting: viewModel.isSelecting,
                                 text: Bindable(viewModel).searchText)
        // The Contacts stack's destination. The route carries the id, so a
        // detail opened from global search resolves the same way as a tap.
        .navigationDestination(for: ContactsRoute.self) { route in
            destination(route)
        }
        .sheet(isPresented: $showCreate, onDismiss: { viewModel.reload() }) {
            NavigationStack {
                ContactEditScreen(viewModel: ContactEditViewModel(contact: nil,
                                                                  repository: viewModel.repository))
            }
        }
        .confirmationDialog(deleteDialogTitle,
                            isPresented: Binding(
                                get: { pendingDelete != nil },
                                set: { if !$0 { pendingDelete = nil } }
                            ), titleVisibility: .visible) {
            Button(VaultCopy.deleteAction, role: .destructive) {
                switch pendingDelete {
                case .single(let contact): deleteContact(contact)
                case .selection: deleteSelected()
                case .none: break
                }
                pendingDelete = nil
            }
            Button(VaultCopy.cancelAction, role: .cancel) { pendingDelete = nil }
        } message: {
            Text(VaultCopy.confirmDeleteBodyTrash)
        }
        .onAppear { viewModel.reload() }
    }

    @ViewBuilder
    private func destination(_ route: ContactsRoute) -> some View {
        switch route {
        case .contact(let id):
            if let contact = viewModel.repository.contact(withId: id) {
                ContactDetailScreen(contact: contact,
                                    repository: viewModel.repository,
                                    onDelete: { deleteContact($0) },
                                    onChanged: { viewModel.reload() })
            } else {
                // The contact was trashed or purged while its route was still
                // on the stack: pop instead of pushing an empty detail.
                // `onAppear` runs after the update, so the mutation is never
                // made during a view evaluation.
                Color.clear.onAppear { navigator?.dismiss(.contacts(route)) }
            }
        }
    }

    /// Pushes onto this tab's path. Rows are not `NavigationLink`s (P6: a link
    /// navigates on any touch-up, which would fight the long-press entry into
    /// selection mode), so navigation is explicit.
    private func open(_ contact: Contact) {
        navigator?.contactsPath.append(.contact(contact.id))
    }

    // MARK: - List

    private var contactsList: some View {
        List {
            // Section headers are plain `Section` titles — not rows, never
            // selectable (§6).
            ForEach(viewModel.sections, id: \.key) { section in
                Section(section.key) {
                    ForEach(section.contacts) { contact in
                        contactRow(contact)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private func contactRow(_ contact: Contact) -> some View {
        let isSelected = viewModel.selection.contains(contact.id)
        return HStack(spacing: 12) {
            if viewModel.isSelecting {
                SelectionIndicator(isSelected: isSelected)
            }
            Text(contact.displayName)
            Spacer(minLength: 8)
            if !viewModel.isSelecting {
                // Disclosure indicator (the row is not a NavigationLink, see
                // `SelectableRow.swift`).
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
        }
        .selectableListRow(isSelecting: viewModel.isSelecting, isSelected: isSelected,
                           onTap: {
                               // Rows never navigate while selecting.
                               if viewModel.isSelecting {
                                   viewModel.toggleSelection(contact)
                               } else {
                                   open(contact)
                               }
                           },
                           onLongPress: {
                               if !viewModel.isSelecting {
                                   viewModel.enterSelectMode(selecting: contact)
                               }
                           })
        .swipeActions {
            // Swipe-to-delete is unavailable while selecting (§6).
            if !viewModel.isSelecting {
                Button(VaultCopy.deleteAction, role: .destructive) { pendingDelete = .single(contact) }
            }
        }
    }

    // MARK: - Toolbar
    //
    // Two shapes, switched on `viewModel.isSelecting`:
    //   selecting  → leading Cancel · trailing Delete (disabled at 0), title
    //                "N selected" (inline)
    //   browsing   → trailing group: search · add
    // Everything new goes in the BROWSING trailing group only; selection mode
    // keeps exactly Cancel + Delete.

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        if viewModel.isSelecting {
            ToolbarItem(placement: .topBarLeading) {
                Button(VaultCopy.cancelAction) { viewModel.exitSelectMode() }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button(role: .destructive) {
                    pendingDelete = .selection
                } label: {
                    Image(systemName: "trash")
                }
                .disabled(viewModel.selection.isEmpty)
                .accessibilityLabel(VaultCopy.deleteAction)
            }
        } else {
            ToolbarItemGroup(placement: .topBarTrailing) {
                Button { navigator?.presentSearch() } label: {
                    Image(systemName: "magnifyingglass")
                }
                .accessibilityLabel(VaultCopy.searchTitle)
                Button { showCreate = true } label: { Image(systemName: "plus") }
            }
        }
    }

    // MARK: - Delete + undo

    private var deleteDialogTitle: String {
        switch pendingDelete {
        case .single, .none:
            return VaultCopy.confirmDeleteContact
        case .selection:
            let count = viewModel.selection.count
            return count == 1 ? VaultCopy.confirmDeleteContact : VaultCopy.confirmDeleteContacts(count)
        }
    }

    /// Soft delete + undo toast, shared by the swipe action and the detail
    /// screen's Delete button. The closure carries the id only and restores
    /// through this view model so the list reloads.
    private func deleteContact(_ contact: Contact) {
        let id = contact.id
        viewModel.delete(contact)
        contactsWereDeleted(ids: [id])
    }

    /// Bulk path (P6): one repository call for the whole selection, selection
    /// mode ends, one toast whose Undo restores the whole batch.
    private func deleteSelected() {
        let ids = viewModel.deleteSelected()
        guard !ids.isEmpty else { return }
        contactsWereDeleted(ids: ids)
    }

    private func contactsWereDeleted(ids: [UUID]) {
        let viewModel = viewModel
        let message = ids.count == 1 ? VaultCopy.deletedContact : VaultCopy.deletedContacts(ids.count)
        undoCenter?.post(message: message) {
            viewModel.restore(ids: ids)
        }
    }
}
