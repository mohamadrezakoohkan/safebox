import Foundation
import Observation

@MainActor
@Observable
final class ContactEditViewModel {
    struct EditableLabeledValue: Identifiable {
        let id = UUID()
        var label: String
        var value: String
    }

    static let labels = ["mobile", "home", "work", "other"]

    private let existing: Contact?
    private let repository: any ContactRepository

    var givenName: String
    var familyName: String
    var organization: String
    var phones: [EditableLabeledValue]
    var emails: [EditableLabeledValue]
    var address: String
    var notes: String

    var isEditingExisting: Bool { existing != nil }

    init(contact: Contact?, repository: any ContactRepository) {
        self.existing = contact
        self.repository = repository
        givenName = contact?.givenName ?? ""
        familyName = contact?.familyName ?? ""
        organization = contact?.organization ?? ""
        phones = (contact?.phones ?? []).map { EditableLabeledValue(label: $0.label, value: $0.value) }
        emails = (contact?.emails ?? []).map { EditableLabeledValue(label: $0.label, value: $0.value) }
        address = contact?.address ?? ""
        notes = contact?.notes ?? ""
    }

    /// At least one of givenName / familyName / organization must be non-empty
    /// (organization-only contacts are legal).
    var isValid: Bool {
        ![givenName, familyName, organization]
            .allSatisfy { $0.trimmingCharacters(in: .whitespaces).isEmpty }
    }

    func addPhone() {
        phones.append(EditableLabeledValue(label: "mobile", value: ""))
    }

    func addEmail() {
        emails.append(EditableLabeledValue(label: "home", value: ""))
    }

    func removePhone(at offsets: IndexSet) {
        phones.remove(atOffsets: offsets)
    }

    func removeEmail(at offsets: IndexSet) {
        emails.remove(atOffsets: offsets)
    }

    func save() {
        guard isValid else { return }
        let cleanedPhones = phones
            .filter { !$0.value.trimmingCharacters(in: .whitespaces).isEmpty }
            .map { LabeledValue(label: $0.label, value: $0.value.trimmingCharacters(in: .whitespaces)) }
        let cleanedEmails = emails
            .filter { !$0.value.trimmingCharacters(in: .whitespaces).isEmpty }
            .map { LabeledValue(label: $0.label, value: $0.value.trimmingCharacters(in: .whitespaces)) }

        func normalized(_ s: String) -> String? {
            let trimmed = s.trimmingCharacters(in: .whitespaces)
            return trimmed.isEmpty ? nil : trimmed
        }

        if let contact = existing {
            contact.givenName = normalized(givenName)
            contact.familyName = normalized(familyName)
            contact.organization = normalized(organization)
            contact.phones = cleanedPhones
            contact.emails = cleanedEmails
            contact.address = normalized(address)
            contact.notes = normalized(notes)
            contact.updatedAt = .now
            try? repository.save()
        } else {
            let contact = Contact(
                givenName: normalized(givenName),
                familyName: normalized(familyName),
                organization: normalized(organization),
                phones: cleanedPhones,
                emails: cleanedEmails,
                address: normalized(address),
                notes: normalized(notes)
            )
            try? repository.insert(contact)
        }
    }
}
