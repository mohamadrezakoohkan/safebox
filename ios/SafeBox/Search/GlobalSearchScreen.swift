import SwiftUI

/// One full-screen global search over the whole vault (decisions §7). Reached
/// from the magnifier in the Gallery, Notes and Contacts top bars; there is no
/// fifth tab.
///
/// It only *reports* a tap — `MainTabView` hands the result to `VaultNavigator`,
/// which dismisses this screen, selects the tab and rebuilds that tab's stack.
/// Nothing here deletes, so nothing here needs the undo toast (which lives
/// below this cover on `MainTabView`).
struct GlobalSearchScreen: View {
    @State private var viewModel: GlobalSearchViewModel
    let container: AppContainer
    let onSelect: (SearchResult) -> Void

    @Environment(\.dismiss) private var dismiss

    init(viewModel: GlobalSearchViewModel,
         container: AppContainer,
         onSelect: @escaping (SearchResult) -> Void) {
        _viewModel = State(initialValue: viewModel)
        self.container = container
        self.onSelect = onSelect
    }

    /// Every edit goes through `setQuery`, so the 300 ms debounce lives in the
    /// view model and the field cannot bypass it.
    private var queryBinding: Binding<String> {
        Binding(get: { viewModel.query }, set: { viewModel.setQuery($0) })
    }

    var body: some View {
        NavigationStack {
            content
                .navigationTitle(VaultCopy.searchTitle)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button(VaultCopy.cancelAction) { dismiss() }
                    }
                }
                .searchable(text: queryBinding,
                            placement: .navigationBarDrawer(displayMode: .always),
                            prompt: Text(VaultCopy.searchPlaceholder))
        }
        .onAppear { viewModel.loadCorpus() }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.content {
        case .noQuery:
            EmptyStateView(.searchNoQuery)
        case .pending:
            // A debounce is in flight and there is nothing older to keep on
            // screen. Showing "No results" here would flash on every first
            // character (the results are 300 ms behind the keystroke).
            Color.clear
        case .results:
            resultsList
        case .noResults:
            EmptyStateView(.noResults)
        }
    }

    // MARK: - Results
    //
    // Grouped with section headers in tab order: Albums, Notes, Contacts.

    private var resultsList: some View {
        List {
            if !viewModel.results.albums.isEmpty {
                Section(VaultCopy.searchSectionAlbums) {
                    ForEach(viewModel.results.albums) { result in
                        resultRow(result) { albumRow(result) }
                    }
                }
            }
            if !viewModel.results.notes.isEmpty {
                Section(VaultCopy.searchSectionNotes) {
                    ForEach(viewModel.results.notes) { result in
                        resultRow(result) { noteRow(result) }
                    }
                }
            }
            if !viewModel.results.contacts.isEmpty {
                Section(VaultCopy.searchSectionContacts) {
                    ForEach(viewModel.results.contacts) { result in
                        resultRow(result) { contactRow(result) }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    /// One tappable row. A plain `Button` (not a `NavigationLink`): the push
    /// happens on another tab's stack, not this one.
    private func resultRow<Content: View>(_ result: SearchResult,
                                          @ViewBuilder content: () -> Content) -> some View {
        Button {
            onSelect(result)
        } label: {
            HStack(spacing: 12) {
                content()
                Spacer(minLength: 8)
                Image(systemName: "chevron.right")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.tertiary)
                    .accessibilityHidden(true)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// Album card styling, compacted to a list row: cover tile + name + count.
    private func albumRow(_ result: SearchResult) -> some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 6).fill(Color(.secondarySystemFill))
                if let cover = viewModel.coverPhoto(forAlbum: result.id) {
                    PhotoThumbnailView(photo: cover, fileStore: container.photoFileStore)
                        .clipShape(RoundedRectangle(cornerRadius: 6))
                } else {
                    Image(systemName: "photo")
                        .foregroundStyle(.secondary)
                }
            }
            .frame(width: 44, height: 44)
            VStack(alignment: .leading, spacing: 2) {
                Text(result.title)
                    .font(.headline)
                    .lineLimit(1)
                Text(result.subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }

    /// Notes list row styling: title + snippet.
    private func noteRow(_ result: SearchResult) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(result.title)
                .font(.headline)
                .lineLimit(1)
            if !result.subtitle.isEmpty {
                Text(result.subtitle)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// Contacts list row styling: display name (+ organization when it adds
    /// something).
    private func contactRow(_ result: SearchResult) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(result.title)
            if !result.subtitle.isEmpty {
                Text(result.subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
