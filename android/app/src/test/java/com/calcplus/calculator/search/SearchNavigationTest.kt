package com.calcplus.calculator.search

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.createGraph
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import com.calcplus.calculator.app.applySearchPlan
import com.calcplus.calculator.core.domain.model.SearchResult
import com.calcplus.calculator.core.domain.model.SearchResultKind
import com.calcplus.calculator.core.navigation.AlbumListRoute
import com.calcplus.calculator.core.navigation.ChangePasscodeRoute
import com.calcplus.calculator.core.navigation.ContactDetailRoute
import com.calcplus.calculator.core.navigation.ContactEditRoute
import com.calcplus.calculator.core.navigation.ContactListRoute
import com.calcplus.calculator.core.navigation.ContactsTab
import com.calcplus.calculator.core.navigation.GalleryTab
import com.calcplus.calculator.core.navigation.GuideRoute
import com.calcplus.calculator.core.navigation.NoteEditorRoute
import com.calcplus.calculator.core.navigation.NoteListRoute
import com.calcplus.calculator.core.navigation.NotesTab
import com.calcplus.calculator.core.navigation.PhotoGridRoute
import com.calcplus.calculator.core.navigation.PhotoPagerRoute
import com.calcplus.calculator.core.navigation.PrivacyRoute
import com.calcplus.calculator.core.navigation.SearchRoute
import com.calcplus.calculator.core.navigation.SettingsRoute
import com.calcplus.calculator.core.navigation.SettingsTab
import com.calcplus.calculator.core.navigation.TrashRoute
import com.calcplus.calculator.core.navigation.VaultRouting
import com.calcplus.calculator.core.navigation.VaultTab
import kotlin.reflect.KClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The cross-tab search contract (decisions §7) against a REAL `NavController`.
 *
 * `VaultRoutingTest` covers the pure planner; this covers the other half —
 * `VaultScaffold.applySearchPlan`, the only code in the vault that touches the
 * back stack. The graph below mirrors `VaultNavHost`'s shape (the same route
 * types, the same nesting, the same start destinations) with empty content, so
 * the navigation semantics under test are the production ones without needing a
 * composition, an `AppContainer` or a database.
 * [theTestGraphMatchesTheRoutingTable] pins that mirror against `VaultTab`.
 */
@RunWith(RobolectricTestRunner::class)
class SearchNavigationTest {
    private lateinit var navController: TestNavHostController

    @Before
    fun setUp() {
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        navController.navigatorProvider.addNavigator(ComposeNavigator())
        navController.graph = navController.createGraph(startDestination = GalleryTab) {
            // Top level, outside every tab graph — exactly where VaultNavHost
            // registers it.
            composable<SearchRoute> {}
            navigation<GalleryTab>(startDestination = AlbumListRoute) {
                composable<AlbumListRoute> {}
                composable<PhotoGridRoute> {}
                composable<PhotoPagerRoute> {}
            }
            navigation<NotesTab>(startDestination = NoteListRoute) {
                composable<NoteListRoute> {}
                composable<NoteEditorRoute> {}
            }
            navigation<ContactsTab>(startDestination = ContactListRoute) {
                composable<ContactListRoute> {}
                composable<ContactDetailRoute> {}
                composable<ContactEditRoute> {}
            }
            navigation<SettingsTab>(startDestination = SettingsRoute) {
                composable<SettingsRoute> {}
                composable<ChangePasscodeRoute> {}
                composable<PrivacyRoute> {}
                composable<TrashRoute> {}
                composable<GuideRoute> {}
            }
        }
    }

    // ---- the vault's own navigation gestures, copied from VaultScaffold ----

    /** The bottom bar's tab switch. */
    private fun selectTab(graph: Any) {
        navController.navigate(graph) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** The magnifier action in the three list top bars. */
    private fun openSearch() {
        navController.navigate(SearchRoute) { launchSingleTop = true }
    }

    /** Tapping a result: plan, then apply — the production `onSelect`. */
    private fun tapResult(result: SearchResult) {
        navController.applySearchPlan(VaultRouting.plan(result))
    }

    // ---- back-stack readers ----

    /** Screens on the back stack, bottom to top; the graph entries are not screens. */
    private fun screens(): List<NavDestination> =
        navController.currentBackStack.value.map { it.destination }.filterNot { it is NavGraph }

    /** The part of the back stack that belongs to one tab. */
    private fun stackIn(tab: VaultTab): List<KClass<*>?> =
        screens()
            .filter { screen -> screen.hierarchy.any { it.hasRoute(tab.graph::class) } }
            .map { screen -> ROUTE_CLASSES.firstOrNull { screen.hasRoute(it) } }

    /** Selected tab, computed the way the bottom bar computes it. */
    private fun selectedTab(): VaultTab? =
        navController.currentBackStackEntry?.destination?.let { current ->
            VaultTab.entries.firstOrNull { tab ->
                current.hierarchy.any { it.hasRoute(tab.graph::class) }
            }
        }

    private fun currentIs(route: KClass<*>): Boolean =
        navController.currentBackStackEntry?.destination?.hasRoute(route) == true

    @Test
    fun theTestGraphMatchesTheRoutingTable() {
        // Drift guard: every tab in VaultRouting is a real graph here, starting
        // on the list route the plan pops back to.
        for (tab in VaultTab.entries) {
            val graph = navController.graph.findNode(tab.graph)
            assertTrue("${tab.name} has no graph", graph is NavGraph)
            assertTrue(
                "${tab.name} does not start on its list route",
                (graph as NavGraph).findStartDestination().hasRoute(tab.listRoute::class),
            )
        }
        assertTrue(currentIs(AlbumListRoute::class))
    }

    @Test
    fun anAlbumResultOpensTheGridOnTopOfTheAlbumListInTheGalleryTab() {
        // Searching from another tab, which is the interesting direction.
        selectTab(NotesTab)
        openSearch()

        tapResult(SearchResult(SearchResultKind.ALBUM, id = "album-7", title = "Trips"))

        assertEquals(VaultTab.GALLERY, selectedTab())
        assertEquals(
            listOf(AlbumListRoute::class, PhotoGridRoute::class),
            stackIn(VaultTab.GALLERY),
        )
        val route = navController.currentBackStackEntry!!.toRoute<PhotoGridRoute>()
        assertEquals("album-7", route.albumId)
        // The grid renders the album name as its title, so the plan carries it.
        assertEquals("Trips", route.albumName)
        assertNoSearchOnTheStack()

        // Back from the detail lands on the tab's list, never in search.
        navController.popBackStack()
        assertTrue(currentIs(AlbumListRoute::class))
    }

    @Test
    fun aNoteResultOpensTheEditorOnTopOfTheNoteListInTheNotesTab() {
        openSearch()

        tapResult(SearchResult(SearchResultKind.NOTE, id = "note-3", title = "Milk"))

        assertEquals(VaultTab.NOTES, selectedTab())
        assertEquals(listOf(NoteListRoute::class, NoteEditorRoute::class), stackIn(VaultTab.NOTES))
        assertEquals("note-3", navController.currentBackStackEntry!!.toRoute<NoteEditorRoute>().noteId)
        assertNoSearchOnTheStack()

        navController.popBackStack()
        assertTrue(currentIs(NoteListRoute::class))
    }

    @Test
    fun aContactResultOpensTheDetailOnTopOfTheContactListInTheContactsTab() {
        openSearch()

        tapResult(SearchResult(SearchResultKind.CONTACT, id = "contact-9", title = "Grace Hopper"))

        assertEquals(VaultTab.CONTACTS, selectedTab())
        assertEquals(
            listOf(ContactListRoute::class, ContactDetailRoute::class),
            stackIn(VaultTab.CONTACTS),
        )
        assertEquals(
            "contact-9",
            navController.currentBackStackEntry!!.toRoute<ContactDetailRoute>().contactId,
        )
        assertNoSearchOnTheStack()

        navController.popBackStack()
        assertTrue(currentIs(ContactListRoute::class))
    }

    @Test
    fun aTabSittingDeepInItsOwnStackIsResetToItsListBeforeTheDetailIsPushed() {
        // Gallery is left on album A's pager, then the user leaves the tab: the
        // stack is SAVED, so the tab-switch step restores it.
        navController.navigate(PhotoGridRoute("album-old", "Old"))
        navController.navigate(PhotoPagerRoute("album-old", "photo-1"))
        selectTab(NotesTab)
        openSearch()

        tapResult(SearchResult(SearchResultKind.ALBUM, id = "album-new", title = "New"))

        // Everything the restore brought back above the list is gone.
        assertEquals(
            listOf(AlbumListRoute::class, PhotoGridRoute::class),
            stackIn(VaultTab.GALLERY),
        )
        assertEquals("album-new", navController.currentBackStackEntry!!.toRoute<PhotoGridRoute>().albumId)
        assertFalse(screens().any { it.hasRoute(PhotoPagerRoute::class) })

        // One Back leaves the album list — the pager is not underneath it.
        navController.popBackStack()
        assertTrue(currentIs(AlbumListRoute::class))
    }

    @Test
    fun aTabAlreadyOnItsListSurvivesTheNoOpPopStep() {
        // popBackStack(listRoute, inclusive = false) returns false here — the
        // contract relies on that being a no-op rather than a throw.
        selectTab(ContactsTab)
        assertTrue(currentIs(ContactListRoute::class))
        openSearch()

        tapResult(SearchResult(SearchResultKind.CONTACT, id = "contact-1", title = "Ada"))

        assertEquals(VaultTab.CONTACTS, selectedTab())
        assertEquals(
            listOf(ContactListRoute::class, ContactDetailRoute::class),
            stackIn(VaultTab.CONTACTS),
        )
    }

    @Test
    fun aSecondJumpIntoTheSameTabReplacesTheDetailInsteadOfStackingOne() {
        // The second jump's tab-switch step RESTORES the stack the first jump
        // left (ordinary saved-tab-state behaviour), so the pop step is what
        // keeps two editors from piling up.
        openSearch()
        tapResult(SearchResult(SearchResultKind.NOTE, id = "note-1", title = "One"))
        openSearch()

        tapResult(SearchResult(SearchResultKind.NOTE, id = "note-2", title = "Two"))

        assertEquals(VaultTab.NOTES, selectedTab())
        assertEquals(listOf(NoteListRoute::class, NoteEditorRoute::class), stackIn(VaultTab.NOTES))
        assertEquals(1, screens().count { it.hasRoute(NoteEditorRoute::class) })
        assertEquals("note-2", navController.currentBackStackEntry!!.toRoute<NoteEditorRoute>().noteId)
        assertNoSearchOnTheStack()

        navController.popBackStack()
        assertTrue(currentIs(NoteListRoute::class))
    }

    private fun assertNoSearchOnTheStack() {
        // Step 1 of the contract: search is popped explicitly, so no saved
        // entry — and therefore no live search view model — is left behind.
        assertFalse(screens().any { it.hasRoute(SearchRoute::class) })
    }

    private companion object {
        /** Every route the test graph declares, for naming a destination in an assertion. */
        val ROUTE_CLASSES = listOf(
            SearchRoute::class,
            AlbumListRoute::class,
            PhotoGridRoute::class,
            PhotoPagerRoute::class,
            NoteListRoute::class,
            NoteEditorRoute::class,
            ContactListRoute::class,
            ContactDetailRoute::class,
            ContactEditRoute::class,
            SettingsRoute::class,
            ChangePasscodeRoute::class,
            PrivacyRoute::class,
            TrashRoute::class,
            GuideRoute::class,
        )
    }
}
