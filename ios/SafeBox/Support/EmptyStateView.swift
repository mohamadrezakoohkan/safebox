import SwiftUI

/// The shared empty-state spacing spec (decisions §2; pt ≡ dp with the Android
/// `EmptyState`): icon circle → 20 → title → 8 → description → 24 → button,
/// 32 horizontal padding, and the block lifted above true center with flexible
/// space 1.0 above : 1.35 below.
enum EmptyStateMetrics {
    static let iconCircle: CGFloat = 88
    static let glyph: CGFloat = 40
    static let iconToTitle: CGFloat = 20
    static let titleToDescription: CGFloat = 8
    static let descriptionToAction: CGFloat = 24
    static let horizontalPadding: CGFloat = 32
    static let spaceAboveWeight: CGFloat = 1.0
    static let spaceBelowWeight: CGFloat = 1.35

    /// The optical-center guide for a view of `height`: aligning the block's
    /// guide with its container's guide leaves free space above : below in
    /// exactly `spaceAboveWeight : spaceBelowWeight`, with no measuring.
    static func opticalCenter(inHeight height: CGFloat) -> CGFloat {
        height * spaceAboveWeight / (spaceAboveWeight + spaceBelowWeight)
    }
}

/// One empty state's content: icon, title, one-line description and an
/// optional action label. A plain value so the vault's eight states are
/// declared once (see the presets below) and can be asserted in tests.
struct EmptyStateContent: Equatable, Sendable {
    let systemImage: String
    let title: String
    let description: String
    let actionTitle: String?

    init(systemImage: String, title: String, description: String, actionTitle: String? = nil) {
        self.systemImage = systemImage
        self.title = title
        self.description = description
        self.actionTitle = actionTitle
    }
}

/// Cross-platform empty state (decisions §2). Replaces `ContentUnavailableView`
/// everywhere in the vault so both platforms share one rhythm: an 88 pt
/// `secondarySystemFill` circle with a 40 pt secondary-tinted glyph, `.title2`
/// title, `.subheadline` secondary description, filled action button.
///
/// The block is optically centered (free space 1.0 above : 1.35 below). If it
/// is taller than the available area — large Dynamic Type with the keyboard up
/// behind a search field — it scrolls instead of overflowing.
struct EmptyStateView: View {
    let content: EmptyStateContent
    let action: (() -> Void)?

    /// - Parameters:
    ///   - content: what to show; use a preset such as `.noAlbums`.
    ///   - action: runs when the button is tapped. The button appears only when
    ///     both this and `content.actionTitle` are set.
    init(_ content: EmptyStateContent, action: (() -> Void)? = nil) {
        self.content = content
        self.action = action
    }

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                ZStack(alignment: Alignment(horizontal: .center, vertical: .emptyStateOpticalCenter)) {
                    // Sized to the visible area so the block centers within it;
                    // a taller block simply extends the scroll range.
                    Color.clear.frame(height: proxy.size.height)
                    block
                }
                .frame(maxWidth: .infinity)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
    }

    private var block: some View {
        VStack(spacing: 0) {
            icon
                .accessibilityHidden(true)
            Spacer().frame(height: EmptyStateMetrics.iconToTitle)
            VStack(spacing: EmptyStateMetrics.titleToDescription) {
                Text(content.title)
                    .font(.title2)
                    .multilineTextAlignment(.center)
                Text(content.description)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .accessibilityElement(children: .combine)
            if let actionTitle = content.actionTitle, let action {
                Spacer().frame(height: EmptyStateMetrics.descriptionToAction)
                Button(actionTitle, action: action)
                    .buttonStyle(.borderedProminent)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, EmptyStateMetrics.horizontalPadding)
    }

    private var icon: some View {
        ZStack {
            Circle().fill(Color(.secondarySystemFill))
            Image(systemName: content.systemImage)
                .resizable()
                .scaledToFit()
                .foregroundStyle(.secondary)
                .frame(width: EmptyStateMetrics.glyph, height: EmptyStateMetrics.glyph)
        }
        .frame(width: EmptyStateMetrics.iconCircle, height: EmptyStateMetrics.iconCircle)
    }
}

// MARK: - Optical center alignment

/// A vertical guide at `EmptyStateMetrics.opticalCenter`, i.e. 1 / 2.35 of the
/// height. The container and the block both expose it, so aligning them splits
/// the free space 1.0 above : 1.35 below — the Android weighted-spacer lift,
/// without measuring the block.
private enum EmptyStateOpticalCenter: AlignmentID {
    static func defaultValue(in context: ViewDimensions) -> CGFloat {
        EmptyStateMetrics.opticalCenter(inHeight: context.height)
    }
}

private extension VerticalAlignment {
    static let emptyStateOpticalCenter = VerticalAlignment(EmptyStateOpticalCenter.self)
}

// MARK: - Vault presets (decisions §2)

/// The vault's empty states. `noResults` is shared by the notes search / tag
/// filter, the contacts search and global search; `searchNoQuery` is the global
/// search screen before anything is typed (N1). Vault vocabulary: render only
/// inside the unlocked vault.
extension EmptyStateContent {
    static let noAlbums = EmptyStateContent(
        systemImage: "photo.on.rectangle.angled",
        title: VaultCopy.emptyAlbumsTitle,
        description: VaultCopy.emptyAlbumsBody,
        actionTitle: VaultCopy.emptyAlbumsAction
    )

    static let noPhotos = EmptyStateContent(
        systemImage: "photo",
        title: VaultCopy.emptyPhotosTitle,
        description: VaultCopy.emptyPhotosBody,
        actionTitle: VaultCopy.emptyPhotosAction
    )

    static let noNotes = EmptyStateContent(
        systemImage: "note.text",
        title: VaultCopy.emptyNotesTitle,
        description: VaultCopy.emptyNotesBody,
        actionTitle: VaultCopy.emptyNotesAction
    )

    static let noContacts = EmptyStateContent(
        systemImage: "person.crop.circle",
        title: VaultCopy.emptyContactsTitle,
        description: VaultCopy.emptyContactsBody,
        actionTitle: VaultCopy.emptyContactsAction
    )

    static let noResults = EmptyStateContent(
        systemImage: "magnifyingglass",
        title: VaultCopy.emptyResultsTitle,
        description: VaultCopy.emptyResultsBody
    )

    static let searchNoQuery = EmptyStateContent(
        systemImage: "magnifyingglass",
        title: VaultCopy.searchNoQueryTitle,
        description: VaultCopy.searchNoQueryBody
    )

    /// "Recently deleted" with nothing in it (P3).
    static let trashEmpty = EmptyStateContent(
        systemImage: "trash",
        title: VaultCopy.trashEmptyStateTitle,
        description: VaultCopy.trashEmptyStateBody
    )

    /// Every distinct vault preset, for audits and tests.
    static let vaultPresets: [EmptyStateContent] = [
        noAlbums, noPhotos, noNotes, noContacts, noResults, searchNoQuery, trashEmpty,
    ]

    // MARK: Selectors (mirror Android `VaultEmptyStates.forNotes` / `forContacts`)

    /// Notes list: "No results" whenever a query or a tag filter is active,
    /// otherwise "No notes yet" — the active filter decides, not the note count,
    /// so both platforms show the same state under the same input.
    static func forNotes(query: String, hasTagFilter: Bool) -> EmptyStateContent {
        isBlank(query) && !hasTagFilter ? noNotes : noResults
    }

    /// Contacts list: "No results" whenever a query is active, otherwise
    /// "No contacts yet".
    static func forContacts(query: String) -> EmptyStateContent {
        isBlank(query) ? noContacts : noResults
    }

    /// Same trim the list view models apply before matching, so a
    /// whitespace-only query is not treated as a search.
    private static func isBlank(_ query: String) -> Bool {
        query.trimmingCharacters(in: .whitespaces).isEmpty
    }
}

// MARK: - Previews

#Preview("With action") {
    EmptyStateView(.noAlbums) {}
}

#Preview("No results") {
    EmptyStateView(.noResults)
}
