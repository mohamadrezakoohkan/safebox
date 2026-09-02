package com.calcplus.calculator.app

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.toRoute
import com.calcplus.calculator.core.navigation.AlbumListRoute
import com.calcplus.calculator.core.navigation.ChangePasscodeRoute
import com.calcplus.calculator.core.navigation.ContactDetailRoute
import com.calcplus.calculator.core.navigation.ContactEditRoute
import com.calcplus.calculator.core.navigation.ContactListRoute
import com.calcplus.calculator.core.navigation.ContactsTab
import com.calcplus.calculator.core.navigation.GalleryTab
import com.calcplus.calculator.core.navigation.NoteEditorRoute
import com.calcplus.calculator.core.navigation.NoteListRoute
import com.calcplus.calculator.core.navigation.NotesTab
import com.calcplus.calculator.core.navigation.PhotoGridRoute
import com.calcplus.calculator.core.navigation.PhotoPagerRoute
import com.calcplus.calculator.core.navigation.SettingsRoute
import com.calcplus.calculator.core.navigation.SettingsTab
import com.calcplus.calculator.di.AppContainer
import com.calcplus.calculator.feature.contacts.ContactDetailScreen
import com.calcplus.calculator.feature.contacts.ContactEditScreen
import com.calcplus.calculator.feature.contacts.ContactListScreen
import com.calcplus.calculator.feature.gallery.AlbumListScreen
import com.calcplus.calculator.feature.gallery.PhotoGridScreen
import com.calcplus.calculator.feature.gallery.PhotoPagerScreen
import com.calcplus.calculator.feature.notes.NoteEditorScreen
import com.calcplus.calculator.feature.notes.NoteListScreen
import com.calcplus.calculator.feature.settings.ChangePasscodeScreen
import com.calcplus.calculator.feature.settings.SettingsScreen
import kotlin.reflect.KClass

private data class TabSpec(
    val graph: Any,
    val graphClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
)

/** The vault: Material 3 NavigationBar + nested graph per tab, per-tab back stacks. */
@Composable
fun VaultScaffold(container: AppContainer) {
    val navController = rememberNavController()
    val tabs = listOf(
        TabSpec(GalleryTab, GalleryTab::class, "Gallery", Icons.Filled.PhotoLibrary),
        TabSpec(NotesTab, NotesTab::class, "Notes", Icons.AutoMirrored.Filled.Note),
        TabSpec(ContactsTab, ContactsTab::class, "Contacts", Icons.Filled.Person),
        TabSpec(SettingsTab, SettingsTab::class, "Settings", Icons.Filled.Settings),
    )

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { dest ->
                        dest.hasRoute(tab.graphClass)
                    } == true
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
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = GalleryTab,
            modifier = Modifier.padding(padding),
        ) {
            navigation<GalleryTab>(startDestination = AlbumListRoute) {
                composable<AlbumListRoute> {
                    AlbumListScreen(container) { albumId, albumName ->
                        navController.navigate(PhotoGridRoute(albumId, albumName))
                    }
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
                    NoteListScreen(container) { noteId ->
                        navController.navigate(NoteEditorRoute(noteId))
                    }
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
                    SettingsScreen(container) {
                        navController.navigate(ChangePasscodeRoute)
                    }
                }
                composable<ChangePasscodeRoute> {
                    ChangePasscodeScreen(
                        container = container,
                        onDone = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
