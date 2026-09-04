package com.calcplus.calculator.app

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.toRoute
import com.calcplus.calculator.R
import com.calcplus.calculator.core.navigation.AlbumListRoute
import com.calcplus.calculator.core.navigation.ChangeDisguiseRoute
import com.calcplus.calculator.core.navigation.ChangePasscodeRoute
import com.calcplus.calculator.core.navigation.ContactDetailRoute
import com.calcplus.calculator.core.navigation.ContactEditRoute
import com.calcplus.calculator.core.navigation.ContactListRoute
import com.calcplus.calculator.core.navigation.ContactsTab
import com.calcplus.calculator.core.navigation.GalleryTab
import com.calcplus.calculator.core.navigation.GuideRoute
import com.calcplus.calculator.core.navigation.NavStep
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
import com.calcplus.calculator.di.AppContainer
import com.calcplus.calculator.feature.disguise.ChangeDisguiseScreen
import com.calcplus.calculator.feature.contacts.ContactDetailScreen
import com.calcplus.calculator.feature.contacts.ContactEditScreen
import com.calcplus.calculator.feature.contacts.ContactListScreen
import com.calcplus.calculator.feature.gallery.AlbumListScreen
import com.calcplus.calculator.feature.gallery.PhotoGridScreen
import com.calcplus.calculator.feature.gallery.PhotoPagerScreen
import com.calcplus.calculator.feature.notes.NoteEditorScreen
import com.calcplus.calculator.feature.notes.NoteListScreen
import com.calcplus.calculator.feature.onboarding.OnboardingMode
import com.calcplus.calculator.feature.onboarding.OnboardingScreen
import com.calcplus.calculator.feature.search.GlobalSearchScreen
import com.calcplus.calculator.feature.settings.ChangePasscodeScreen
import com.calcplus.calculator.feature.settings.PrivacyScreen
import com.calcplus.calculator.feature.settings.SettingsScreen
import com.calcplus.calculator.feature.trash.TrashScreen
import kotlin.reflect.KClass

/**
 * Executes a [VaultRouting.plan] on the real back stack — the ONLY place the
 * cross-tab search contract touches navigation (the planning itself is pure and
 * unit-tested in `VaultRoutingTest`).
 *
 * Step by step: search is popped first (step 1 of the contract);
 * [NavStep.SelectTab] is the vault's ordinary tab-switch navigate;
 * [NavStep.PopToTabRoot] discards whatever `restoreState` just brought back so
 * the tab is on its list; [NavStep.PushDetail] pushes the detail on top of that
 * list, which is why Back from it lands on the list and not in search.
 *
 * All of it happens inside one event, so the tab underneath never flashes.
 *
 * `internal` rather than private so `SearchNavigationTest` can drive it against
 * a real `NavController` over the real vault graph shape — this is the only
 * code in the item that touches the back stack, and the planner's own tests
 * cannot see any of it.
 */
internal fun NavHostController.applySearchPlan(steps: List<NavStep>) {
    // (1) Leave search. Popping it explicitly — rather than letting the tab
    // navigate's `popUpTo(start) { saveState }` swallow it — leaves no saved
    // entry, and therefore no live search view model still holding a snapshot
    // of the vault, behind.
    popBackStack(SearchRoute, inclusive = true)
    val startDestinationId = graph.findStartDestination().id
    for (step in steps) {
        when (step) {
            is NavStep.SelectTab -> navigate(step.graph) {
                popUpTo(startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            // False when the tab is already on its list — a no-op, not a failure.
            is NavStep.PopToTabRoot -> popBackStack(step.route, inclusive = false)
            is NavStep.PushDetail -> navigate(step.route)
        }
    }
}

private data class TabSpec(
    val graph: Any,
    val graphClass: KClass<*>,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
)

/**
 * The vault: Material 3 NavigationBar + nested graph per tab, per-tab back
 * stacks. Everything in here exists only while the lock state is Unlocked
 * (see [SafeBoxApp]), which is what makes the guide's revisit route safe.
 *
 * It also owns the vault's ONE undo [SnackbarHostState] (decisions §3),
 * published through [LocalUndoController]. Hoisting it here is what lets a
 * delete performed on a detail screen — the photo pager, the note editor, a
 * contact — show its snackbar after that screen has already popped.
 */
@Composable
fun VaultScaffold(container: AppContainer) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val presentScope = rememberCoroutineScope()
    val context = LocalContext.current
    val undoController = remember(snackbarHostState, presentScope, context) {
        UndoController(
            hostState = snackbarHostState,
            presentScope = presentScope,
            // The restore itself outlives the vault: an Undo tapped just before
            // a background lock still completes.
            workScope = container.applicationScope,
            context = context,
        )
    }
    val tabs = listOf(
        TabSpec(GalleryTab, GalleryTab::class, R.string.vault_tab_gallery, Icons.Filled.PhotoLibrary),
        TabSpec(NotesTab, NotesTab::class, R.string.vault_tab_notes, Icons.AutoMirrored.Filled.Note),
        TabSpec(ContactsTab, ContactsTab::class, R.string.vault_tab_contacts, Icons.Filled.Person),
        TabSpec(SettingsTab, SettingsTab::class, R.string.vault_tab_settings, Icons.Filled.Settings),
    )

    Scaffold(
        // The one undo host for the whole vault. It sits in the OUTER Scaffold,
        // so it is unaffected by the bottom-bar `if` below and survives every
        // navigation inside the NavHost.
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            // Two full-screen routes hide the bar: the guide in revisit mode
            // (decisions §5) and global search (decisions §7). Everything else,
            // "Recently deleted" included, keeps it. The bar returns the moment
            // either route pops.
            val hidesBottomBar = currentDestination?.hasRoute(GuideRoute::class) == true ||
                currentDestination?.hasRoute(SearchRoute::class) == true
            if (!hidesBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { dest ->
                            dest.hasRoute(tab.graphClass)
                        } == true
                        val label = stringResource(tab.labelRes)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.graph) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        CompositionLocalProvider(LocalUndoController provides undoController) {
            VaultNavHost(
                container = container,
                navController = navController,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun VaultNavHost(
    container: AppContainer,
    navController: NavHostController,
    modifier: Modifier,
) {
        val openSearch: () -> Unit = {
            navController.navigate(SearchRoute) { launchSingleTop = true }
        }
        NavHost(
            navController = navController,
            startDestination = GalleryTab,
            modifier = modifier,
        ) {
            // Global search (decisions §7) sits at the TOP LEVEL, outside every
            // tab graph: three tabs open it and it can hand navigation to any of
            // them. See [applySearchPlan] for the cross-tab contract.
            composable<SearchRoute> {
                GlobalSearchScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                    onSelect = { result ->
                        navController.applySearchPlan(VaultRouting.plan(result))
                    },
                )
            }
            navigation<GalleryTab>(startDestination = AlbumListRoute) {
                composable<AlbumListRoute> {
                    AlbumListScreen(
                        container = container,
                        onOpenAlbum = { albumId, albumName ->
                            navController.navigate(PhotoGridRoute(albumId, albumName))
                        },
                        onOpenSearch = openSearch,
                    )
                }
                composable<PhotoGridRoute> { entry ->
                    val route = entry.toRoute<PhotoGridRoute>()
                    PhotoGridScreen(
                        container = container,
                        albumId = route.albumId,
                        albumName = route.albumName,
                        onBack = { navController.popBackStack() },
                        onOpenPhoto = { photoId ->
                            navController.navigate(PhotoPagerRoute(route.albumId, photoId))
                        },
                    )
                }
                composable<PhotoPagerRoute> { entry ->
                    val route = entry.toRoute<PhotoPagerRoute>()
                    PhotoPagerScreen(
                        container = container,
                        albumId = route.albumId,
                        startPhotoId = route.photoId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            navigation<NotesTab>(startDestination = NoteListRoute) {
                composable<NoteListRoute> {
                    NoteListScreen(
                        container = container,
                        onOpenNote = { noteId -> navController.navigate(NoteEditorRoute(noteId)) },
                        onOpenSearch = openSearch,
                    )
                }
                composable<NoteEditorRoute> { entry ->
                    val route = entry.toRoute<NoteEditorRoute>()
                    NoteEditorScreen(
                        container = container,
                        noteId = route.noteId,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            navigation<ContactsTab>(startDestination = ContactListRoute) {
                composable<ContactListRoute> {
                    ContactListScreen(
                        container = container,
                        onOpenContact = { id -> navController.navigate(ContactDetailRoute(id)) },
                        onCreateContact = { navController.navigate(ContactEditRoute(null)) },
                        onOpenSearch = openSearch,
                    )
                }
                composable<ContactDetailRoute> { entry ->
                    val route = entry.toRoute<ContactDetailRoute>()
                    ContactDetailScreen(
                        container = container,
                        contactId = route.contactId,
                        onEdit = { navController.navigate(ContactEditRoute(route.contactId)) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<ContactEditRoute> { entry ->
                    val route = entry.toRoute<ContactEditRoute>()
                    ContactEditScreen(
                        container = container,
                        contactId = route.contactId,
                        onDone = { navController.popBackStack() },
                    )
                }
            }
            navigation<SettingsTab>(startDestination = SettingsRoute) {
                composable<SettingsRoute> {
                    SettingsScreen(
                        container = container,
                        onChangePasscode = { navController.navigate(ChangePasscodeRoute) },
                        onChangeDisguise = { navController.navigate(ChangeDisguiseRoute) },
                        onOpenGuide = { navController.navigate(GuideRoute) { launchSingleTop = true } },
                        onOpenPrivacy = { navController.navigate(PrivacyRoute) { launchSingleTop = true } },
                        onOpenTrash = { navController.navigate(TrashRoute) { launchSingleTop = true } },
                    )
                }
                composable<ChangePasscodeRoute> {
                    ChangePasscodeScreen(
                        container = container,
                        onDone = { navController.popBackStack() },
                    )
                }
                // Change disguise (decisions §5): the switch flow, mirroring
                // the change-passcode route above.
                composable<ChangeDisguiseRoute> {
                    ChangeDisguiseScreen(
                        container = container,
                        onDone = { navController.popBackStack() },
                    )
                }
                composable<PrivacyRoute> {
                    PrivacyScreen(onBack = { navController.popBackStack() })
                }
                // "Recently deleted" (decisions §3): an ordinary detail route —
                // the bottom bar deliberately STAYS visible for it, unlike the
                // guide below.
                composable<TrashRoute> {
                    TrashScreen(
                        container = container,
                        onBack = { navController.popBackStack() },
                    )
                }
                // Revisit mode (decisions §5): every finish path — Done on any
                // page, the final CTA, system back — only pops this route. Nothing
                // here references the lock manager or the onboarding store, so
                // first-run state cannot be written from a revisit by
                // construction; the vault stays unlocked and Settings is below.
                composable<GuideRoute> {
                    val currentFace by container.lockManager.activeDisguise
                        .collectAsStateWithLifecycle()
                    OnboardingScreen(
                        mode = OnboardingMode.REVISIT,
                        registry = container.disguiseRegistry,
                        currentFace = currentFace,
                        onFinish = { navController.popBackStack() },
                    )
                }
            }
        }
}
