package com.calcplus.calculator.core.navigation

import com.calcplus.calculator.core.domain.model.SearchResult
import com.calcplus.calculator.core.domain.model.SearchResultKind

/**
 * The four vault tabs, each paired with its nested-graph key and the root list
 * route that graph starts on. The Android twin of iOS `enum VaultTab`.
 *
 * Declaration order is bottom-bar order. Settings is present for completeness —
 * global search never targets it (decisions §7).
 */
enum class VaultTab(val graph: Any, val listRoute: Any) {
    GALLERY(GalleryTab, AlbumListRoute),
    NOTES(NotesTab, NoteListRoute),
    CONTACTS(ContactsTab, ContactListRoute),
    SETTINGS(SettingsTab, SettingsRoute),
}

/**
 * One step of a cross-tab jump. The steps are values rather than `NavController`
 * calls so the whole contract can be planned — and unit-tested — without a
 * navigation graph; `VaultScaffold.applySearchPlan` is the only thing that turns
 * them into navigation.
 */
sealed interface NavStep {
    /**
     * `navigate(graph)` with the vault's standard tab options
     * (`popUpTo(start) { saveState }` + `launchSingleTop` + `restoreState`) —
     * the same call the bottom bar makes, so per-tab state is preserved exactly
     * as it is for an ordinary tab switch.
     */
    data class SelectTab(val graph: Any) : NavStep

    /**
     * `popBackStack(route, inclusive = false)` — resets the target tab's back
     * stack to its root list, discarding whatever `restoreState` just brought
     * back. A no-op when the tab is already on its list.
     */
    data class PopToTabRoot(val route: Any) : NavStep

    /** `navigate(route)` — the detail screen the result points at. */
    data class PushDetail(val route: Any) : NavStep
}

/**
 * The cross-tab navigation contract of decisions §7, as a pure function.
 *
 * Tapping a search result must (1) leave search, (2) select the target tab,
 * (3) reset that tab's back stack to its root list and (4) push the detail — so
 * Back from the detail lands on the tab's list, not back in search.
 *
 * [plan] returns steps (2)–(4) in order; (1) is the search route being popped by
 * `VaultScaffold.applySearchPlan` before it runs them, because dismissing the
 * screen you are on is navigation mechanics, not routing. Nothing here touches a
 * `NavController`, which is what makes the contract testable.
 */
object VaultRouting {
    /** Which tab owns a result's detail screen. */
    fun tab(kind: SearchResultKind): VaultTab = when (kind) {
        SearchResultKind.ALBUM -> VaultTab.GALLERY
        SearchResultKind.NOTE -> VaultTab.NOTES
        SearchResultKind.CONTACT -> VaultTab.CONTACTS
    }

    /**
     * The detail route a result opens: album → photo grid, note → editor,
     * contact → detail.
     *
     * `PhotoGridRoute` also carries the album name because that route renders it
     * as the screen title; [SearchResult.title] is the album's name.
     */
    fun detailRoute(result: SearchResult): Any = when (result.kind) {
        SearchResultKind.ALBUM -> PhotoGridRoute(result.id, result.title)
        SearchResultKind.NOTE -> NoteEditorRoute(result.id)
        SearchResultKind.CONTACT -> ContactDetailRoute(result.id)
    }

    /** The ordered navigation steps for a tapped result. */
    fun plan(result: SearchResult): List<NavStep> {
        val tab = tab(result.kind)
        return listOf(
            NavStep.SelectTab(tab.graph),
            NavStep.PopToTabRoot(tab.listRoute),
            NavStep.PushDetail(detailRoute(result)),
        )
    }
}
