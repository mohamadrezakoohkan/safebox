import SwiftUI

/// Create/edit form with labeled multi-value phones/emails.
struct ContactEditScreen: View {
    @State private var viewModel: ContactEditViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: ContactEditViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    var body: some View {
        Form {
            Section("Name") {
                TextField("First name", text: Bindable(viewModel).givenName)
                TextField("Last name", text: Bindable(viewModel).familyName)
                TextField("Organization", text: Bindable(viewModel).organization)
            }
            Section("Phones") {
                ForEach(Bindable(viewModel).phones) { $phone in
                    labeledValueRow(label: $phone.label, value: $phone.value,
                                    placeholder: "Phone number", keyboard: .phonePad)
                }
                .onDelete { viewModel.removePhone(at: $0) }
                Button {
                    viewModel.addPhone()
                } label: {
                    Label("Add phone", systemImage: "plus.circle.fill")
                }
            }
            Section("Emails") {
                ForEach(Bindable(viewModel).emails) { $email in
                    labeledValueRow(label: $email.label, value: $email.value,
                                    placeholder: "Email", keyboard: .emailAddress)
                }
                .onDelete { viewModel.removeEmail(at: $0) }
                Button {
                    viewModel.addEmail()
                } label: {
                    Label("Add email", systemImage: "plus.circle.fill")
                }
            }
            Section("Address") {
                TextField("Address", text: Bindable(viewModel).address, axis: .vertical)
            }
            Section("Notes") {
                TextField("Notes", text: Bindable(viewModel).notes, axis: .vertical)
            }
        }
        .navigationTitle(viewModel.isEditingExisting ? "Edit contact" : "New contact")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") {
                    viewModel.save()
                    dismiss()
                }
                .disabled(!viewModel.isValid)
            }
        }
    }

    private func labeledValueRow(label: Binding<String>, value: Binding<String>,
                                 placeholder: String, keyboard: UIKeyboardType) -> some View {
        HStack {
            Picker("", selection: label) {
                ForEach(ContactEditViewModel.labels, id: \.self) { option in
                    Text(option).tag(option)
                }
            }
            .labelsHidden()
            .frame(width: 90)
            TextField(placeholder, text: value)
                .keyboardType(keyboard)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        }
    }
}
