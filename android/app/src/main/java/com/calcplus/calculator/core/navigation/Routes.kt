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

// Tab graph roots.
@Serializable object GalleryTab
@Serializable object NotesTab
@Serializable object ContactsTab
@Serializable object SettingsTab
