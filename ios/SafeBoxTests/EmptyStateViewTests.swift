import CoreGraphics
import Testing
@testable import SafeBox

/// Pins the shared empty-state spec (decisions §2) so the iOS component cannot
/// drift from the Android `EmptyState`: the spacing metrics, the 1.0 : 1.35
/// optical-centering split, and the content of the vault's eight states.
struct EmptyStateViewTests {
    // MARK: - Spacing spec

    @Test func metricsMatchTheSharedSpec() {
        #expect(EmptyStateMetrics.iconCircle == 88)
        #expect(EmptyStateMetrics.glyph == 40)
        #expect(EmptyStateMetrics.iconToTitle == 20)
        #expect(EmptyStateMetrics.titleToDescription == 8)
        #expect(EmptyStateMetrics.descriptionToAction == 24)
        #expect(EmptyStateMetrics.horizontalPadding == 32)
        #expect(EmptyStateMetrics.spaceAboveWeight == 1.0)
        #expect(EmptyStateMetrics.spaceBelowWeight == 1.35)
    }

    // MARK: - Optical centering

    @Test func opticalCenterSplitsFreeSpaceOneToOnePointThreeFive() {
        // Aligning the block's guide with the container's guide leaves
        // (H − h) · 1 / 2.35 above and (H − h) · 1.35 / 2.35 below.
        let container: CGFloat = 700
        let block: CGFloat = 230
        let above = EmptyStateMetrics.opticalCenter(inHeight: container)
            - EmptyStateMetrics.opticalCenter(inHeight: block)
        let below = container - block - above
        #expect(above > 0)
        #expect(abs(above + below - (container - block)) < 0.0001)
        #expect(abs(below / above - 1.35) < 0.0001)
    }

    @Test func opticalCenterIsAboveTrueCenter() {
        let height: CGFloat = 1000
        #expect(EmptyStateMetrics.opticalCenter(inHeight: height) < height / 2)
        #expect(EmptyStateMetrics.opticalCenter(inHeight: 0) == 0)
    }

    // MARK: - Vault presets

    @Test func everyVaultStateHasIconTitleAndDescription() {
        #expect(EmptyStateContent.vaultPresets.count == 7) // + P3's trashEmpty
        for preset in EmptyStateContent.vaultPresets {
            #expect(!preset.systemImage.isEmpty)
            #expect(!preset.title.isEmpty)
            #expect(!preset.description.isEmpty)
            #expect(preset.title != preset.description)
        }
    }

    @Test func actionsOnlyWhereTheTableSpecifiesOne() {
        #expect(EmptyStateContent.noAlbums.actionTitle == VaultCopy.emptyAlbumsAction)
        #expect(EmptyStateContent.noPhotos.actionTitle == VaultCopy.emptyPhotosAction)
        #expect(EmptyStateContent.noNotes.actionTitle == VaultCopy.emptyNotesAction)
        #expect(EmptyStateContent.noContacts.actionTitle == VaultCopy.emptyContactsAction)
        #expect(EmptyStateContent.noResults.actionTitle == nil)
        #expect(EmptyStateContent.searchNoQuery.actionTitle == nil)
        #expect(EmptyStateContent.trashEmpty.actionTitle == nil)
    }

    @Test func presetsUseTheSharedStrings() {
        #expect(EmptyStateContent.noAlbums.title == VaultCopy.emptyAlbumsTitle)
        #expect(EmptyStateContent.noAlbums.description == VaultCopy.emptyAlbumsBody)
        #expect(EmptyStateContent.noPhotos.title == VaultCopy.emptyPhotosTitle)
        #expect(EmptyStateContent.noPhotos.description == VaultCopy.emptyPhotosBody)
        #expect(EmptyStateContent.noNotes.title == VaultCopy.emptyNotesTitle)
        #expect(EmptyStateContent.noNotes.description == VaultCopy.emptyNotesBody)
        #expect(EmptyStateContent.noContacts.title == VaultCopy.emptyContactsTitle)
        #expect(EmptyStateContent.noContacts.description == VaultCopy.emptyContactsBody)
        #expect(EmptyStateContent.noResults.title == VaultCopy.emptyResultsTitle)
        #expect(EmptyStateContent.noResults.description == VaultCopy.emptyResultsBody)
        #expect(EmptyStateContent.searchNoQuery.title == VaultCopy.searchNoQueryTitle)
        #expect(EmptyStateContent.searchNoQuery.description == VaultCopy.searchNoQueryBody)
    }

    @Test func actionIsOptionalInTheContentValue() {
        let plain = EmptyStateContent(systemImage: "photo", title: "T", description: "D")
        #expect(plain.actionTitle == nil)
        let withAction = EmptyStateContent(systemImage: "photo", title: "T", description: "D", actionTitle: "A")
        #expect(withAction.actionTitle == "A")
        #expect(plain != withAction)
    }

    // MARK: - State selectors (same rule as Android `VaultEmptyStates`)

    @Test func notesShowNoResultsWheneverAQueryOrTagFilterIsActive() {
        #expect(EmptyStateContent.forNotes(query: "", hasTagFilter: false) == .noNotes)
        #expect(EmptyStateContent.forNotes(query: "   ", hasTagFilter: false) == .noNotes)
        #expect(EmptyStateContent.forNotes(query: "abc", hasTagFilter: false) == .noResults)
        #expect(EmptyStateContent.forNotes(query: "", hasTagFilter: true) == .noResults)
        #expect(EmptyStateContent.forNotes(query: "abc", hasTagFilter: true) == .noResults)
    }

    @Test func contactsShowNoResultsWheneverAQueryIsActive() {
        #expect(EmptyStateContent.forContacts(query: "") == .noContacts)
        #expect(EmptyStateContent.forContacts(query: " \t") == .noContacts)
        #expect(EmptyStateContent.forContacts(query: "zoe") == .noResults)
    }
}
