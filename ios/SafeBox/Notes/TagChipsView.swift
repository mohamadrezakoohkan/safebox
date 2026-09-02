import SwiftUI

/// Shared tag palette semantics: colorIndex into a fixed palette.
enum TagPalette {
    static let colors: [Color] = [.blue, .green, .orange, .purple, .pink, .teal]

    static func color(for index: Int) -> Color {
        colors[abs(index) % colors.count]
    }
}

struct TagChip: View {
    let tag: Tag
    let isSelected: Bool

    var body: some View {
        Text(tag.name)
            .font(.caption)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(
                Capsule().fill(TagPalette.color(for: tag.colorIndex).opacity(isSelected ? 0.35 : 0.15))
            )
            .overlay(
                Capsule().stroke(TagPalette.color(for: tag.colorIndex).opacity(isSelected ? 1 : 0.4), lineWidth: 1)
            )
    }
}

/// Chip row in the editor: existing tags as toggleable chips + a "+" chip
/// opening a small add-tag sheet.
struct TagChipsView: View {
    let viewModel: NoteEditorViewModel

    @State private var showAddSheet = false
    @State private var newTagName = ""

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(viewModel.allTags) { tag in
                    Button {
                        viewModel.toggleTag(tag)
                    } label: {
                        TagChip(tag: tag, isSelected: viewModel.isTagged(tag))
                    }
                    .buttonStyle(.plain)
                }
                Button {
                    newTagName = ""
                    showAddSheet = true
                } label: {
                    Label("Tag", systemImage: "plus")
                        .font(.caption)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Capsule().fill(Color(.secondarySystemFill)))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal)
            .padding(.vertical, 6)
        }
        .alert("New tag", isPresented: $showAddSheet) {
            TextField("Tag name", text: $newTagName)
            Button("Add") { viewModel.addTag(named: newTagName) }
            Button("Cancel", role: .cancel) {}
        }
    }
}
