package com.calcplus.calculator.core.navigation

import kotlinx.serialization.Serializable

// Type-safe route keys (navigation-compose 2.8+ serializable APIs).

@Serializable object AlbumListRoute
@Serializable data class PhotoGridRoute(val albumId: String, val albumName: String)
@Serializable data class PhotoPagerRoute(val albumId: String, val photoId: String)

@Serializable object NoteListRoute
@Serializable data class NoteEditorRoute(val noteId: String)

@Serializable object ContactListRoute
@Serializable data class ContactDetailRoute(val contactId: String)
@Serializable data class ContactEditRoute(val contactId: String?)

@Serializable object SettingsRoute
@Serializable object ChangePasscodeRoute
/**
 * Settings → "Change disguise" (iteration-3-decisions §5). Mirrors
 * [ChangePasscodeRoute] in the Settings tab graph: it re-enrolls the code, so
 * it lives beside the other passcode row rather than under Appearance.
 */
@Serializable object ChangeDisguiseRoute
/** Settings → Privacy detail (decisions §5). */
@Serializable object PrivacyRoute
/**
 * Settings → "Recently deleted" (decisions §3). A normal detail route inside
 * the Settings tab graph: the bottom bar stays visible for it.
 */
@Serializable object TrashRoute
/**
 * Settings → "How it works": the onboarding guide in revisit mode, full-screen
 * with the bottom bar hidden. Lives inside the Settings tab graph, so it is
 * reachable only from the unlocked vault by construction (decisions §5).
 */
@Serializable object GuideRoute

/**
 * Global search (decisions §7). A TOP-LEVEL route of the NavHost, not part of any
 * tab graph: it is opened from three different tabs and, when a result is tapped,
 * it has to hand navigation to any of the other three. Sitting above the start
 * destination is also what lets the standard tab-switch `popUpTo(start)` dismiss
 * it. The bottom bar is hidden while it — like [GuideRoute] — is on top.
 */
@Serializable object SearchRoute

// Tab graph roots.
@Serializable object GalleryTab
@Serializable object NotesTab
@Serializable object ContactsTab
@Serializable object SettingsTab
