# SafeBox — Iteration 2 Shared Decisions

**Document:** `docs/plans/iteration-2-decisions.md`
**Companion to:** `docs/plans/iteration-2-todo.md` (the task list). This document settles every "shared decision" checkbox in the todo so the iOS and Android implementations cannot diverge. Where this document and the todo disagree, this document wins; where it changes user-visible behavior described in `idea-plan.md` or `calculator-disguise-design.md`, those documents are updated as part of the iteration.
**Status:** Decided 2026-09-02, shipped. **Partly superseded by `docs/plans/iteration-3-decisions.md`** (see its §10): the lock-surface epoch was renamed, and the guide's first page became a lock-face carousel whose selection also drives pages 3 and 4, so revisit mode no longer renders a fixed set of four calculator pages.

---

## 0. Scope, sequence, batching

All nine items in the todo are in scope: **N2, P2, F1, P5, P3, P6, P4, N1, N3**, implemented in that order on both platforms.

**Migration batching (decided):** P3 and N3 both land, so there is exactly **one** persistence migration, `v1 → v2`, which adds every new column at once:

| Table / model | New column | Type | Default |
|---|---|---|---|
| albums | `deletedAt` | timestamp, nullable | `NULL` |
| photos | `deletedAt` | timestamp, nullable | `NULL` |
| photos | `mediaType` | string, not null | `"photo"` (values: `photo`, `video`) |
| photos | `durationMs` | int64, nullable | `NULL` (videos only) |
| notes | `deletedAt` | timestamp, nullable | `NULL` |
| contacts | `deletedAt` | timestamp, nullable | `NULL` |

- Android: `SafeBoxDatabase` version 2, `Migration(1, 2)` with `ALTER TABLE … ADD COLUMN …`, exported `app/schemas/…/2.json` committed, `MigrationTestHelper` test (schemas dir wired as a test asset). Never `fallbackToDestructiveMigration`.
- iOS: models move into `enum SchemaV2` with `typealias`es at file scope; `enum SchemaV1` keeps a verbatim snapshot of the v1 models; `SafeBoxMigrationPlan.stages = [.lightweight(fromVersion: SchemaV1.self, toVersion: SchemaV2.self)]`. `inMemory()` uses `SchemaV2` without a plan (unchanged trap). A migration test opens an on-disk temp store with V1, reopens with V2 + plan, and asserts data survives.

The migration is implemented as the first step of **P3**; N3 later only reads/writes the two photo columns that already exist.

**Rules for every item (restating the todo):**
- New user-facing copy uses the shared string IDs in §10, added to `android/app/src/main/res/values/strings.xml` and `ios/SafeBox/Support/Localizable.xcstrings` with identical keys. iOS reads them through a `VaultCopy` enum (`ios/SafeBox/Support/VaultCopy.swift`, `localizedCopy(key, default)`), Android through `stringResource(R.string.<id>)`.
- Both unit suites stay green; each item adds its own tests.
- No logging of key tokens, buffers, salts, hashes or lock internals.
- Nothing weakens the disguise: no vault vocabulary reachable from the locked calculator; nothing vault-related in the app switcher / recents.
- Drag-to-reorder (P4 follow-up) is **out of scope** for this iteration on both platforms.

---

## 1. F1 — Unlock transition

**Treatment (decided): "zoom-in reveal" (shared-axis Z).** The vault fades in while scaling from **0.92 → 1.0**; the calculator fades out in place (opacity 1 → 0, no scale). Both run concurrently over the same duration.

Why this over the alternatives:
- *Plain crossfade* (the iteration-1 spec, design §5.4) reads as a flicker rather than a reveal; it is what the todo asked to move beyond.
- *Fade-through* inserts a gap in which neither surface is visible. That pushes the first vault frame later, which conflicts with the §5.4 budget definition ("vault visible = first transition frame"), and the momentary bare background reads as a glitch.
- *Circular reveal from `=`* needs the key's runtime geometry, an animated clip mask, and per-platform tuning to look identical; it also makes the `=` key visibly special. Too expensive for the value.
- The zoom-in reveal is concurrent (first transition frame shows the vault, so the §5.4 budget measurement point is unchanged), reads as depth ("something was behind the calculator"), and is a one-line transition on both SwiftUI (`.scale(0.92).combined(with: .opacity)`) and Compose (`scaleIn(initialScale = 0.92f) + fadeIn()`).

**Duration and curve (decided):** **260 ms**, emphasized-decelerate cubic bezier **(0.05, 0.7, 0.1, 1.0)**. One constant used by both platforms (`UnlockReveal.durationMs = 260` / `UNLOCK_REVEAL_DURATION_MS = 260`).

**Directions:**
- Only `Locked → Unlocked` and `NeedsSetup/firstRunSetup → Unlocked` animate (first-run setup confirmation uses the **same** reveal — it is the first time the user sees the vault and there is no reason for it to feel different).
- **Every other transition is an instant cut:** `Unlocked → Locked` (manual, background, any lock), `NeedsSetup ↔ Locked`, and every `disguiseEpoch` (named `calculatorEpoch` when this was written; renamed in iteration 3) bump (the calculator recreation must never read as a transition).
- The one-time no-recovery notice (shown after first-run setup) must not pop over the reveal: present it only after the reveal completes (gate on transition completion or delay by the reveal duration).
- The design doc's §5.4 budget statement stays: "≤ 300 ms perceived" is measured from the `=` press to the **first reveal frame**; the 260 ms tail is excluded.

**Reduced motion (decided):** the fallback is an **opacity-only crossfade** with the same 260 ms / curve (drop the scale). iOS reads `@Environment(\.accessibilityReduceMotion)`. Android has no reduce-motion flag; its only signal is `Settings.Global.ANIMATOR_DURATION_SCALE == 0` ("Remove animations"), which means *no animations at all*, so Android snaps in that case.

**Implementation constraints:**
- `AppLockCoordinator` / `AppLockManager` are untouched. The animation is driven view-side (iOS: mirror `coordinator.state` into displayed state via `.onChange`, `withAnimation` only for `→ .unlocked`, `Transaction(disablesAnimations: true)` otherwise; Android: `AnimatedContent(targetState = lockState)` whose `transitionSpec` returns the reveal only when `targetState == Unlocked`, and `EnterTransition.None togetherWith ExitTransition.None` otherwise).
- iOS: `.id(coordinator.calculatorEpoch)` stays on `LockCalculatorView`; the `CalculatorCoverView` overlay stays outside the animated container with `zIndex(10)`.
- Android: `key(epoch)` stays inside the calculator content; FLAG_SECURE is unconditional so the transitional frames are never captured.

---

## 2. P2 — Empty states

**Spacing spec (decided, cross-platform; pt ≡ dp):** icon circle **88** (glyph **40**, tinted secondary/`onSurfaceVariant`, circle fill `surfaceContainerHigh` / `secondarySystemFill`) → **20** → title (`titleLarge` / `.title2`, centered) → **8** → description (`bodyMedium` / `.subheadline`, secondary color, centered) → **24** → filled button. Horizontal padding **32**. Vertical optical centering with flexible space **1.0 above : 1.35 below**. Recorded in the design doc as a new "Vault empty states" appendix.

**Structure (decided):** every empty state, including filtered "No results", has **icon + title + one-line description**; action button only where the todo/idea plan specifies one. iOS gets a custom `EmptyStateView` (Support/) matching the spec exactly — `ContentUnavailableView` and `ContentUnavailableView.search` are no longer used in the vault.

The eight states and their strings (IDs in §10):

| State | title | body | action |
|---|---|---|---|
| No albums | `empty_albums_title` | `empty_albums_body` | `empty_albums_action` |
| Empty album | `empty_photos_title` | `empty_photos_body` | `empty_photos_action` |
| No notes | `empty_notes_title` | `empty_notes_body` | `empty_notes_action` |
| Notes: no results (search or tag filter) | `empty_results_title` | `empty_results_body` | — |
| No contacts | `empty_contacts_title` | `empty_contacts_body` | `empty_contacts_action` |
| Contacts: no results | `empty_results_title` | `empty_results_body` | — |
| Global search: no query (N1) | `search_no_query_title` | `search_no_query_body` | — |
| Global search: no results (N1) | `empty_results_title` | `empty_results_body` | — |

The photo grid while an import is running with zero photos keeps the empty state suppressed and shows the import progress pill centered in the empty grid area.

---

## 3. P3 — Deletion undo / Recently deleted

**Model (decided): persisted "Recently deleted" holding area, with a snackbar/toast Undo as the immediate-feedback layer.** In-memory undo alone dies with the vault on every background lock.

- **Entities:** photos, albums, notes, contacts (all four).
- **Retention:** **30 days** (`TRASH_RETENTION_DAYS = 30`). Purge of expired items runs **at app start** and **on every transition to Unlocked**.
- **Where the UI lives:** one Settings row, "Recently deleted" (Data section), opening one screen with sections Albums / Photos / Notes / Contacts. Each row: Restore, Delete now (per-row confirm not required). Toolbar: "Empty" → confirm → hard-purge everything.
- **Confirm dialogs stay** on every delete path (P6 needs a single count-bearing confirm anyway), but the copy changes from "This cannot be undone." to `confirm_delete_body_trash` ("You can restore it from Recently deleted for 30 days.").
- **Undo affordance:** after any delete, a snackbar/toast with the count message (§10 `deleted_*`) and `undo_action`. Visible ~5 s (Android `SnackbarDuration.Long`, iOS 5 s custom toast). Undo calls `restore` with the ids just trashed. One host per platform: Android hoists a single `SnackbarHostState` into `VaultScaffold` (so a delete from a detail screen shows after popping back); iOS adds an `UndoCenter` observable created in `MainTabView` (dies on lock) with a toast overlay at the tab-view level.

**Semantics:**
- Soft delete = set `deletedAt = now`. Every list/observe/search query filters `deletedAt IS NULL`. Album photo counts and derived covers exclude trashed photos.
- **Album:** soft-deleting an album stamps the album and all its *live* photos with the **same** `deletedAt` instant; restoring the album clears `deletedAt` on the album and on photos whose `deletedAt` equals the album's stamp (photos trashed individually earlier stay trashed). Purging an album purges all of its photos (rows + both files) then the album row.
- **Photo:** restore keeps its original `sortIndex` (returns in place). A trashed photo whose album is also trashed is shown under the album in the trash, not individually. If a photo is restored while its album is trashed (only reachable through Undo), the album row is restored too, without its other trashed photos, so the photo stays reachable.
- **Erase everything with trash present:** `VaultNuker` must delete rows individually (or otherwise prove every row is gone) — batch table clears can silently skip rows that carry mandatory relationships (found on iOS with tagged notes); the nuke test seeds trashed and tagged rows.
- **Files are not deleted on soft delete.** The "rows AND files" invariant moves to purge time: purge, empty-all, expiry, and `VaultNuker` delete both files and rows. `sweepOrphans` **must keep enumerating all photo rows including trashed ones** (explicit test on both platforms).
- `VaultNuker` continues to hard-clear every table and every file, including trash (explicit test with trashed rows present).
- Repository API additions (both platforms): `delete → soft-delete`, `restore(ids)`, `purge(ids)`, `purgeExpired(now)`, plus a `TrashRepository` (observe/list trash contents grouped by type, restore, purge, emptyAll, purgeExpired).

---

## 4. P4 — Sort (albums, notes)

- **Album sort modes:** `manual` (by `sortIndex`, **default**), `name` (A–Z), `date_created` (newest first), `photo_count` (most first).
- **Note sort modes:** `date_modified` (newest first, **default**), `date_created` (newest first), `title` (A–Z).
- **Persistence:** global (not per album/tab). Raw values are exactly the snake_case strings above on both platforms. iOS: `UserDefaults` keys `albumSort.v1` / `noteSort.v1` via a `SortPreferences` enum following `OnboardingSentinel` (injectable `defaults:`). Android: `SortPrefsStore` on the existing `passcode_store` DataStore with keys `album_sort` / `note_sort`, exposed as `Flow`. **`VaultNuker` resets both** (erase returns the app to its just-installed state).
- **Tie-breakers (identical on both):** name → `createdAt` asc → `id`; date_created → `id`; photo_count → name → `id`; title → `updatedAt` desc → `id`; date_modified/date_created → `id`. Title comparison is case- and diacritic-insensitive (same fold as §7); notes with an empty derived title sort **last** under `title`.
- **Where sorting happens:** in the repository/view model (in-memory sort on the fetched list), not in the view body. Android keeps its DAO ORDER BY as the base order and re-sorts in the repository; iOS passes the mode to `albums(sortedBy:)` / `notes(sortedBy:)`.
- **UI:** a sort menu in the Gallery and Notes top bars (iOS `Menu` with an inline `Picker`; Android sort icon + `DropdownMenu` with a check on the active mode). Strings in §10 (`sort_*`).
- Drag-to-reorder: **follow-up, not this iteration.**

---

## 5. P5 — Settings restructure, guide revisit

**Information architecture (decided):**

| Section | Row | Behavior |
|---|---|---|
| Security | Change passcode | existing flow |
| Security | Lock now | existing |
| Data | Recently deleted | pushes the P3 trash screen (subtitle: `trash_subtitle`) |
| Data | Erase everything | existing two-step destructive flow, red, last row of the section |
| About | Version | inline value |
| About | How it works | **action**: launches the onboarding guide in *revisit* mode; subtitle `settings_how_it_works_subtitle` |
| About | Privacy | pushes a detail screen with the privacy statement and the no-recovery warning as proper paragraphs (`settings_privacy_body`, `setup_no_recovery_body`); subtitle `settings_privacy_subtitle` |
| About | Open-source licenses | Android only (accepted asymmetry); lists Media3 once N3 lands |

The four long About paragraphs are removed from the list.

**Revisit mode (decided; amended in iteration 3 — page 1 is now the lock-face carousel, locked on the current face and not selectable, and pages 3–4 show that face's guide content instead of the calculator's):** `OnboardingView` / `OnboardingScreen` gain a `mode` parameter (`firstRun` | `revisit`). In revisit mode: the top-right button reads `onboarding_done` on every page (it replaces Skip and dismisses); the final CTA also reads `onboarding_done`; finishing/dismissing **never** writes the onboarding-complete flag or calls `completeOnboarding()`; the vault stays unlocked and the user returns to Settings. Presentation: iOS `.sheet` from `SettingsScreen` (dismissible by swipe, no `interactiveDismissDisabled`); Android full-screen route `GuideRoute` inside `navigation<SettingsTab>` with the bottom bar hidden for that route. Both are reachable only from the unlocked vault by construction.

---

## 6. P6 — Multi-select in Notes and Contacts

- **Entry:** long-press a row enters selection mode (both platforms, both lists). Tapping rows toggles while selecting. Sticky section headers (contacts) are not selectable.
- **Bulk actions:** delete only. Bulk tagging is **out** for this iteration.
- **Toolbar in selection mode:** title `selection_count` ("N selected"), a Delete action (disabled at 0), and `cancel_action` to exit. One confirm dialog with the count (`confirm_delete_notes` / `confirm_delete_contacts` + `confirm_delete_body_trash`).
- **Repository:** bulk soft-delete in **one** call with N ids (`delete(notes:)` / `delete(ids)`), never N calls. Undo snackbar restores the whole batch.
- Swipe-to-delete on notes is disabled while selecting. Selection state lives in the view model and resets when selection mode exits and (by construction) on lock.
- Both platforms hold `isSelecting` / `selection` in the view model, mirroring the existing `PhotoGridViewModel` pattern, and draw their own per-row selection indicator. **Amended during implementation:** the original decision said iOS would use `List(selection:)` + `editMode`. It does not — SwiftUI's edit mode has no long-press entry (the decided entry gesture), gives no control over the indicator, and conflicts with the `.swipeActions` this iteration keeps on notes. iOS instead uses a shared `selectableListRow` modifier (tap toggles while selecting, long-press enters; rows are not `NavigationLink`s so a long press cannot also navigate). Every behavior above is unchanged; only the mechanism differs, and the two platforms now match each other more closely than the original wording would have.

---

## 7. N1 — Global search

- **Entry point:** a search (magnifier) action in the top bar of Gallery, Notes and Contacts (not Settings) opens one full-screen global search. No fifth tab.
- **Scope:** notes (title, body, tag names), contacts (name, organization, phones, emails), albums (name). Photos do not participate.
- **Matching (decided, shared fold):** case- **and** diacritic-insensitive substring match on both platforms: fold = Unicode NFD → strip combining marks → lowercase; `fold(haystack).contains(fold(query))`. The per-tab searches adopt the same fold so results are consistent (Android moves note/contact query filtering from SQLite `LIKE` into Kotlin using the fold; iOS notes search adds `.diacriticInsensitive`). A shared helper exists on each platform (`SearchFold` / `SearchNormalizer`).
- **Debounce:** 300 ms on both (Android reuses `debounce(300)`; iOS adds an equivalent task-based debounce in the search view model).
- **Presentation:** grouped by type with section headers, in tab order: Albums, Notes, Contacts. Rows reuse the tab's row styling. Empty query → `search_no_query_*` state; no matches → `empty_results_*`.
- **Cross-tab navigation contract (decided, both platforms):** tapping a result (1) dismisses search, (2) selects the target tab, (3) resets that tab's back stack to its root list, and (4) pushes the detail (album → photo grid; note → editor; contact → detail). Back from the detail therefore lands on the tab's list. iOS introduces `VaultTab` + `TabView(selection:)` and typed `NavigationStack(path:)` routes per tab held in a `VaultNavigator` created in `MainTabView` (dies on lock; never stored in `AppContainer`). Android navigates to the tab graph with the existing saveState/restoreState options, pops to the tab's list route, then pushes the detail route.

---

## 8. N2 — Photo metadata

- **Fields and order:** Dimensions (`W × H`), File size, Type, Imported; videos (N3) add Duration after Type.
- **Presentation:** an Info action in the pager toolbar opens a sheet (iOS `.sheet` with `.medium` detent; Android `ModalBottomSheet`) titled `photo_info_title`, one labeled row per field.
- **Formats:** bytes via `ByteCountFormatter` (.file) / `Formatter.formatShortFileSize`; dates localized medium date + short time (`Date.FormatStyle(date: .abbreviated, time: .shortened)` / `DateFormat.getDateTimeInstance(MEDIUM, SHORT)`); Type = MIME subtype mapped through a shared table (`jpeg→JPEG, png→PNG, heic→HEIC, heif→HEIF, gif→GIF, webp→WEBP, bmp→BMP, mp4→MP4, quicktime→MOV, x-matroska→MKV, webm→WEBM, 3gpp→3GP`, else uppercased subtype); duration `m:ss` / `h:mm:ss`, **rounded to the nearest whole second** (59.6 s → `1:00`, negatives → `0:00`) on both platforms; dimensions use `×` (U+00D7) with a space on each side.
- Labels in §10 (`photo_info_*`). No data-layer change.

---

## 9. N3 — Video support

- **Schema:** `mediaType` (`photo`|`video`) and `durationMs` on the photo entity (part of the §0 migration). One table for mixed media.
- **Formats:** whatever the system picker hands over that the OS can play — iOS `.movie` (mov/mp4/m4v), Android `video/*` (mp4/3gp/webm/mkv). **No size cap.** Originals are stored byte-for-byte under their real extension; on iOS videos are received as files (`FileRepresentation`), never loaded whole into memory, and staged under the vault directory, not the system tmp dir (which is wiped on lock).
- **Thumbnail policy:** extract one frame at `min(1 s, duration / 2)`, store it exactly like a photo thumbnail (`<uuid>.jpg`, ≤ 600 px), so the grid stays uniform.
- **Grid affordance:** white play glyph centered on the cell plus a duration badge (`m:ss`, dark translucent pill) at the **bottom-left**; the selection indicator stays bottom-right.
- **Playback rules (disguise/containment):** playback stops and the player is torn down when the app backgrounds or the vault locks (the pager leaves the hierarchy on lock; the player additionally pauses and releases on disappear/`ON_STOP`). **Picture-in-Picture is disabled**: iOS uses `AVPlayerViewController` with `allowsPictureInPicturePlayback = false`; Android declares no `supportsPictureInPicture`, never calls `enterPictureInPictureMode`, and uses a TextureView-backed `PlayerView` so FLAG_SECURE demonstrably covers the video surface. No background audio mode.
- **Android dependency:** Media3 ExoPlayer + UI (`androidx.media3:media3-exoplayer`, `media3-ui`) via the version catalog; added to the open-source licenses row.
- **Import progress:** stays **item-count based** (one video = one item). Byte-based progress is deferred; recorded here as the decision.
- Metadata sheet (N2) shows Duration for videos.

---

## 10. Shared string table (IDs identical on both platforms)

Format arguments use each platform's syntax (`%1$d` / `%1$s` on Android, `%lld` / `%@` on iOS). Values are the English source.

| ID | English |
|---|---|
| `vault_tab_gallery` | Gallery |
| `vault_tab_notes` | Notes |
| `vault_tab_contacts` | Contacts |
| `vault_tab_settings` | Settings |
| `cancel_action` | Cancel |
| `delete_action` | Delete |
| `done_action` | Done |
| `ok_action` | OK |
| `select_action` | Select |
| `undo_action` | Undo |
| `selection_count` | %d selected |
| `empty_albums_title` | No albums yet |
| `empty_albums_body` | Albums keep your imported photos and videos organized. |
| `empty_albums_action` | Create album |
| `empty_photos_title` | No photos yet |
| `empty_photos_body` | Imports are copies — the originals stay in your library. |
| `empty_photos_action` | Import photos |
| `empty_notes_title` | No notes yet |
| `empty_notes_body` | Notes support markdown with a live preview. |
| `empty_notes_action` | New note |
| `empty_contacts_title` | No contacts yet |
| `empty_contacts_body` | Contacts live only in this vault. |
| `empty_contacts_action` | Add contact |
| `empty_results_title` | No results |
| `empty_results_body` | Check the spelling or try a different search. |
| `confirm_delete_album` | Delete album and its %d photos? |
| `confirm_delete_photo` | Delete this photo? |
| `confirm_delete_photos` | Delete %d photos? |
| `confirm_delete_note` | Delete this note? |
| `confirm_delete_notes` | Delete %d notes? |
| `confirm_delete_contact` | Delete this contact? |
| `confirm_delete_contacts` | Delete %d contacts? |
| `confirm_delete_body_trash` | You can restore it from Recently deleted for 30 days. |
| `deleted_album` | Album deleted |
| `deleted_photo` | Photo deleted |
| `deleted_photos` | %d photos deleted |
| `deleted_note` | Note deleted |
| `deleted_notes` | %d notes deleted |
| `deleted_contact` | Contact deleted |
| `deleted_contacts` | %d contacts deleted |
| `trash_title` | Recently deleted |
| `trash_subtitle` | Items are kept for 30 days, then deleted permanently. |
| `trash_restore` | Restore |
| `trash_delete_now` | Delete now |
| `trash_empty` | Empty |
| `trash_empty_confirm_title` | Delete everything in Recently deleted? |
| `trash_empty_confirm_body` | This permanently deletes every item here. This cannot be undone. |
| `trash_empty_state_title` | Nothing here |
| `trash_empty_state_body` | Deleted items appear here for 30 days. |
| `trash_section_albums` | Albums |
| `trash_section_photos` | Photos |
| `trash_section_notes` | Notes |
| `trash_section_contacts` | Contacts |
| `trash_days_left` | %d days left |
| `trash_photo_count` | %d photos |
| `sort_title` | Sort by |
| `sort_album_manual` | Manual |
| `sort_name` | Name |
| `sort_date_created` | Date created |
| `sort_photo_count` | Photo count |
| `sort_date_modified` | Date modified |
| `sort_note_title` | Title |
| `settings_title` | Settings |
| `settings_section_security` | Security |
| `settings_section_data` | Data |
| `settings_section_about` | About |
| `settings_lock_now` | Lock now |
| `settings_version` | Version |
| `settings_how_it_works` | How it works |
| `settings_how_it_works_subtitle` | Revisit the guide |
| `settings_privacy_title` | Privacy |
| `settings_privacy_subtitle` | All data stays on this device. |
| `settings_privacy_body` | All data stays on this device. This app has no servers and sends nothing anywhere — no accounts, no analytics, no cloud sync. |
| `settings_licenses` | Open-source licenses |
| `onboarding_done` | Done |
| `search_title` | Search |
| `search_placeholder` | Notes, contacts, albums |
| `search_no_query_title` | Search your vault |
| `search_no_query_body` | Find notes, contacts, and albums by name or content. |
| `search_section_albums` | Albums |
| `search_section_notes` | Notes |
| `search_section_contacts` | Contacts |
| `photo_info_title` | Details |
| `photo_info_dimensions` | Dimensions |
| `photo_info_size` | File size |
| `photo_info_type` | Type |
| `photo_info_imported` | Imported |
| `photo_info_duration` | Duration |
| `video_import_failed` | Some videos could not be imported. |
| `import_progress` | Importing %1$d/%2$d… |

Existing IDs that are reused unchanged: `settings_change_title`, `nuke_row_title`, `nuke_row_subtitle`, `nuke_confirm_*`, `nuke_final_*`, `setup_no_recovery_title/body/button`, `onboarding_*`.

Accepted Android-only IDs (no iOS counterpart by design): `settings_licenses_body` (the licenses row has no iOS equivalent) and `back_action` (Compose needs an explicit content description for the back arrow; iOS uses the system back button). Any further platform-only ID is recorded here.

---

## 11. Shared constants

| Constant | Value |
|---|---|
| Unlock reveal duration | 260 ms |
| Unlock reveal curve | cubic-bezier(0.05, 0.7, 0.1, 1.0) |
| Unlock reveal initial scale | 0.92 |
| Trash retention | 30 days |
| Search debounce | 300 ms |
| Video poster frame offset | min(1 s, duration / 2) |
| Thumbnail max pixel | 600 (unchanged) |

---

## 12. Idea-plan / design-doc statements superseded by this iteration

Updated in the same change set (see the docs pass at the end of the iteration):
- idea §3.1 "Still images only … no video" → mixed photo/video media policy; photo detail actions gain Info; §4 Photo table gains `mediaType`, `durationMs`; every entity gains `deletedAt`.
- idea §3.1 "Deletion (rows AND files)" → files deleted at purge/expiry; orphan sweep respects trashed rows.
- idea §3.2 "Swipe-to-delete with confirmation; no undo" and §5 candidate "note-delete undo" → undo + Recently deleted on both platforms.
- idea §3.2 notes "Sorted by modified date" and §3.1 "Albums are ordered by their own sortIndex" → user-selectable sort with the defaults above; §4 AppSettings gains the two sort preferences.
- idea §3.4 Settings → the IA in §5 (plus Erase everything and the onboarding guide, which the plan never recorded).
- idea §3 → new "Global search" subsection; §3.2/§3.3 multi-select.
- design §5.4 → the zoom-in reveal, 260 ms, curve, reduced-motion fallback; lock stays an instant cut; budget measurement point unchanged.
- design → new appendix "Vault empty states" with the §2 spacing spec.

---

## 13. Update check and source link (added after iteration 2)

A late addition, decided with the user on 2026-09-03. It is the **first and only** outbound network request in the app, so it is specified tightly.

**What ships**
- Settings → About gains two rows below Version:
  - **Source code** → opens `https://github.com/mohamadrezakoohkan/safebox` in the browser.
  - **Check for updates** → on tap, fetches `https://raw.githubusercontent.com/mohamadrezakoohkan/safebox/main/version.json` and reports the result as that row's subtitle.
- `version.json` lives at the repo root: `{ "latestVersion": "1.0.0", "releasesUrl": "…/releases/latest" }`. Publishing a new version means editing that one file — no app release needed to change the destination.
- When the fetched `latestVersion` is **greater** than the running version, the subtitle becomes "Version X.Y.Z available" and the row's tap target changes to open `releasesUrl` (default: the repo's Releases page). Equal or lower ⇒ "Up to date".
- **Amended during implementation:** this section first said the up-to-date row was "not tappable". Both platforms instead made every state except *checking* re-run the check on tap, which is what a user expects from a row labelled "Check for updates" — a result that can never be refreshed is a dead end. Only the *available* state diverts its tap to `releasesUrl`. The row is disabled while a check is in flight so it cannot be hammered.

**Network posture (decided)**
- **Manual only.** No automatic check — not at launch, not on unlock, not on a timer. The app makes zero requests until the user taps the row. A fresh install that never opens the row never touches the network.
- The request is a bare `GET` with no query string, no custom headers, no credentials and no cookies. Nothing about the device, the vault or its contents is transmitted; the response is the only data that crosses the boundary.
- **No on-disk HTTP caching** (iOS: ephemeral `URLSession` configuration; Android: `useCaches = false`). A cache entry would leave the URL — which names the repo — inside the app container, which is a forensic tell.
- The check is reachable only from the unlocked vault, and any in-flight request is abandoned when the vault tears down on lock.
- Per the no-logging rule, neither the URL nor the response body is ever logged.

**Version comparison (shared rule)**
Dotted numeric compare, component by component, missing components treated as `0` — so `1.0` and `1.0.0` are equal (iOS ships `CFBundleShortVersionString` `1.0`, Android ships `versionName` `1.0.0`). Non-numeric or unparseable values compare as "not newer", so a malformed `version.json` can never nag the user. Both platforms implement the identical rule and unit-test it.

**Accepted disguise tell (explicit user decision)**
The URL contains the repo name `safebox`. Anyone inspecting network traffic, or reading an OS per-app network report, can see a "Calculator+" contacting a repository named *safebox* — which names the app's true purpose. The alternative (a neutral host or path) was offered and **declined**; the repo URL is used as-is. Consequences, recorded so they are not rediscovered later:
- The tell is only exposed if the user taps the row; the app is silent otherwise.
- Android must declare `android.permission.INTERNET`, which is visible in app info for a calculator.

**Copy that had to change**
`settings_privacy_body` can no longer claim the app "sends nothing anywhere". It now states that all data stays on the device and that the only outbound request is the manual update check. `idea-plan.md`'s "Zero network usage" NFR/DoD item is amended to the same effect.

**New shared string IDs** (added to both platforms; args are `%@` on iOS, `%1$s` on Android)

| ID | English |
|---|---|
| `settings_source_code` | Source code |
| `settings_source_code_subtitle` | View this app on GitHub |
| `settings_check_updates` | Check for updates |
| `settings_update_checking` | Checking… |
| `settings_update_up_to_date` | Up to date |
| `settings_update_available` | Version %@ available |
| `settings_update_failed` | Couldn't check for updates |

**Revised** `settings_privacy_body`: "All data stays on this device — no accounts, no analytics, no cloud sync. The only time this app connects to the internet is when you tap Check for updates."
