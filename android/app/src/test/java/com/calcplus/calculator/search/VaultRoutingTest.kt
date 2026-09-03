package com.calcplus.calculator.search

import com.calcplus.calculator.core.domain.model.SearchResult
import com.calcplus.calculator.core.domain.model.SearchResultKind
import com.calcplus.calculator.core.navigation.AlbumListRoute
import com.calcplus.calculator.core.navigation.ContactDetailRoute
import com.calcplus.calculator.core.navigation.ContactListRoute
import com.calcplus.calculator.core.navigation.ContactsTab
import com.calcplus.calculator.core.navigation.GalleryTab
import com.calcplus.calculator.core.navigation.NavStep
import com.calcplus.calculator.core.navigation.NoteEditorRoute
import com.calcplus.calculator.core.navigation.NoteListRoute
import com.calcplus.calculator.core.navigation.NotesTab
import com.calcplus.calculator.core.navigation.PhotoGridRoute
import com.calcplus.calculator.core.navigation.SettingsRoute
import com.calcplus.calculator.core.navigation.SettingsTab
import com.calcplus.calculator.core.navigation.VaultRouting
import com.calcplus.calculator.core.navigation.VaultTab
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The cross-tab navigation contract of decisions §7, tested as the pure function
 * it is: tapping a result selects the target tab, resets that tab's back stack to
 * its root list, and pushes the detail — so Back lands on the list.
 */
class VaultRoutingTest {
    private fun album(id: String, name: String) =
        SearchResult(SearchResultKind.ALBUM, id, name, photoCount = 3)

    private fun note(id: String) = SearchResult(SearchResultKind.NOTE, id, "Quarterly report")

    private fun contact(id: String) = SearchResult(SearchResultKind.CONTACT, id, "Ada Lovelace")

    @Test
    fun everyTabKnowsItsGraphAndItsRootListRoute() {
        assertEquals(GalleryTab, VaultTab.GALLERY.graph)
        assertEquals(AlbumListRoute, VaultTab.GALLERY.listRoute)
        assertEquals(NotesTab, VaultTab.NOTES.graph)
        assertEquals(NoteListRoute, VaultTab.NOTES.listRoute)
        assertEquals(ContactsTab, VaultTab.CONTACTS.graph)
        assertEquals(ContactListRoute, VaultTab.CONTACTS.listRoute)
        assertEquals(SettingsTab, VaultTab.SETTINGS.graph)
        assertEquals(SettingsRoute, VaultTab.SETTINGS.listRoute)
    }

    @Test
    fun eachResultKindTargetsItsOwnTab() {
        assertEquals(VaultTab.GALLERY, VaultRouting.tab(SearchResultKind.ALBUM))
        assertEquals(VaultTab.NOTES, VaultRouting.tab(SearchResultKind.NOTE))
        assertEquals(VaultTab.CONTACTS, VaultRouting.tab(SearchResultKind.CONTACT))
        // Search never targets Settings (decisions §7) — no kind maps to it.
        assertEquals(3, SearchResultKind.entries.map { VaultRouting.tab(it) }.distinct().size)
    }

    @Test
    fun anAlbumOpensThePhotoGridOnTheGalleryTab() {
        assertEquals(
            listOf(
                NavStep.SelectTab(GalleryTab),
                NavStep.PopToTabRoot(AlbumListRoute),
                NavStep.PushDetail(PhotoGridRoute("al-1", "Receipts")),
            ),
            VaultRouting.plan(album("al-1", "Receipts")),
        )
    }

    @Test
    fun aNoteOpensTheEditorOnTheNotesTab() {
        assertEquals(
            listOf(
                NavStep.SelectTab(NotesTab),
                NavStep.PopToTabRoot(NoteListRoute),
                NavStep.PushDetail(NoteEditorRoute("n-1")),
            ),
            VaultRouting.plan(note("n-1")),
        )
    }

    @Test
    fun aContactOpensTheDetailOnTheContactsTab() {
        assertEquals(
            listOf(
                NavStep.SelectTab(ContactsTab),
                NavStep.PopToTabRoot(ContactListRoute),
                NavStep.PushDetail(ContactDetailRoute("c-1")),
            ),
            VaultRouting.plan(contact("c-1")),
        )
    }

    @Test
    fun thePlanIsAlwaysTabThenListThenDetailInThatOrder() {
        // The order IS the contract: select the tab, reset it to its list, and
        // only then push the detail — which is what makes Back land on the list.
        // (Leaving search happens before these steps, in applySearchPlan.)
        for (result in listOf(album("a", "A"), note("n"), contact("c"))) {
            val steps = VaultRouting.plan(result)
            assertEquals(3, steps.size)
            assertEquals(true, steps[0] is NavStep.SelectTab)
            assertEquals(true, steps[1] is NavStep.PopToTabRoot)
            assertEquals(true, steps[2] is NavStep.PushDetail)

            val tab = VaultRouting.tab(result.kind)
            assertEquals(NavStep.SelectTab(tab.graph), steps[0])
            assertEquals(NavStep.PopToTabRoot(tab.listRoute), steps[1])
            assertEquals(NavStep.PushDetail(VaultRouting.detailRoute(result)), steps[2])
        }
    }

    @Test
    fun thePhotoGridRouteCarriesTheAlbumNameItRendersAsItsTitle() {
        val route = VaultRouting.detailRoute(album("al-2", "Holiday")) as PhotoGridRoute
        assertEquals("al-2", route.albumId)
        assertEquals("Holiday", route.albumName)
    }
}
