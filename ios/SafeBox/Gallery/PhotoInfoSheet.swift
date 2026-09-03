import SwiftUI

/// Snapshot of one photo's stored metadata (decisions §8). It is taken from
/// the SwiftData model at the moment the Info action is tapped, so the sheet
/// shows exactly what is on disk for the photo that was current; being a plain
/// value, its row derivation is unit-testable without a view.
struct PhotoMetadata: Identifiable, Equatable, Sendable {
    let id: UUID
    let width: Int
    let height: Int
    let byteCount: Int
    let mimeType: String
    let importedAt: Date
    /// Videos only (N3); `nil` hides the Duration row.
    let durationMs: Int?

    init(id: UUID, width: Int, height: Int, byteCount: Int, mimeType: String,
         importedAt: Date, durationMs: Int? = nil) {
        self.id = id
        self.width = width
        self.height = height
        self.byteCount = byteCount
        self.mimeType = mimeType
        self.importedAt = importedAt
        self.durationMs = durationMs
    }

    init(photo: Photo) {
        self.init(id: photo.id, width: photo.width, height: photo.height, byteCount: photo.byteCount,
                  mimeType: photo.mimeType, importedAt: photo.importedAt, durationMs: photo.durationMs)
    }

    /// Labeled rows in the decided order: Dimensions, File size, Type,
    /// Duration (videos only), Imported.
    func rows(locale: Locale = .autoupdatingCurrent,
              calendar: Calendar = .autoupdatingCurrent,
              timeZone: TimeZone = .autoupdatingCurrent) -> [PhotoInfoRow] {
        var rows = [
            PhotoInfoRow(label: VaultCopy.photoInfoDimensions,
                         value: MediaMetadataFormatter.dimensions(width: width, height: height)),
            PhotoInfoRow(label: VaultCopy.photoInfoSize,
                         value: MediaMetadataFormatter.fileSize(byteCount, locale: locale)),
            PhotoInfoRow(label: VaultCopy.photoInfoType,
                         value: MediaMetadataFormatter.typeLabel(mimeType: mimeType)),
        ]
        if let durationMs {
            rows.append(PhotoInfoRow(label: VaultCopy.photoInfoDuration,
                                     value: MediaMetadataFormatter.duration(milliseconds: durationMs)))
        }
        rows.append(PhotoInfoRow(label: VaultCopy.photoInfoImported,
                                 value: MediaMetadataFormatter.dateTime(importedAt, locale: locale,
                                                                        calendar: calendar, timeZone: timeZone)))
        return rows
    }
}

/// One labeled line of the Details sheet. Labels are unique per sheet, so the
/// label doubles as the identity.
struct PhotoInfoRow: Identifiable, Equatable, Sendable {
    let label: String
    let value: String
    var id: String { label }
}

/// The "Details" sheet opened from the pager's Info action (N2): presented at
/// the `.medium` detent with one labeled row per field.
struct PhotoInfoSheet: View {
    let metadata: PhotoMetadata
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List(metadata.rows()) { row in
                LabeledContent(row.label, value: row.value)
            }
            .navigationTitle(VaultCopy.photoInfoTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(VaultCopy.doneAction) { dismiss() }
                }
            }
        }
        .presentationDetents([.medium])
        .presentationDragIndicator(.visible)
    }
}
