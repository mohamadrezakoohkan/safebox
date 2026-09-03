import SwiftUI
import UIKit

/// Read-only detail. No tel:/mailto: handoff anywhere (pinned decision) —
/// long-press copies the value to the clipboard instead.
struct ContactDetailScreen: View {
    let contact: Contact
    let repository: any ContactRepository
    /// The list screen performs the soft delete and posts the undo toast; this
    /// screen only dismisses afterwards.
    let onDelete: (Contact) -> Void
    var onChanged: () -> Void = {}

    @State private var showEdit = false
    @State private var confirmDelete = false
    @State private var copiedValue: String?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        List {
            Section {
                VStack(alignment: .center, spacing: 8) {
                    Image(systemName: "person.crop.circle.fill")
                        .font(.system(size: 64))
                        .foregroundStyle(.secondary)
                    Text(contact.displayName)
                        .font(.title2.bold())
                    if let org = contact.organization, !org.isEmpty, contact.displayName != org {
                        Text(org)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity)
                .listRowBackground(Color.clear)
            }
            if !contact.phones.isEmpty {
                Section("Phone") {
                    ForEach(contact.phones, id: \.self) { phone in
                        copyableRow(label: phone.label, value: phone.value)
                    }
                }
            }
            if !contact.emails.isEmpty {
                Section("Email") {
                    ForEach(contact.emails, id: \.self) { email in
                        copyableRow(label: email.label, value: email.value)
                    }
                }
            }
            if let address = contact.address, !address.isEmpty {
                Section("Address") {
                    copyableRow(label: "address", value: address)
                }
            }
            if let notes = contact.notes, !notes.isEmpty {
                Section("Notes") {
                    Text(notes)
                }
            }
            Section {
                Button("Delete contact", role: .destructive) { confirmDelete = true }
            }
        }
        .navigationTitle("")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button("Edit") { showEdit = true }
        }
        .sheet(isPresented: $showEdit, onDismiss: { onChanged() }) {
            NavigationStack {
                ContactEditScreen(viewModel: ContactEditViewModel(contact: contact,
                                                                  repository: repository))
            }
        }
        .confirmationDialog(VaultCopy.confirmDeleteContact,
                            isPresented: $confirmDelete, titleVisibility: .visible) {
            Button(VaultCopy.deleteAction, role: .destructive) {
                onDelete(contact)
                dismiss()
            }
            Button(VaultCopy.cancelAction, role: .cancel) {}
        } message: {
            Text(VaultCopy.confirmDeleteBodyTrash)
        }
        .overlay {
            if copiedValue != nil {
                Text("Copied")
                    .font(.callout.bold())
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background(.regularMaterial, in: Capsule())
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: copiedValue)
    }

    private func copyableRow(label: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value)
        }
        .contentShape(Rectangle())
        .onLongPressGesture {
            UIPasteboard.general.string = value
            copiedValue = value
            Task {
                try? await Task.sleep(for: .seconds(1.2))
                copiedValue = nil
            }
        }
    }
}
