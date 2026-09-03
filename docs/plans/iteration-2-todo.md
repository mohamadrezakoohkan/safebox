# SafeBox — Iteration 2 Todo (UI & Features)

**Document:** `docs/plans/iteration-2-todo.md`
**Scope:** Selected UI fixes, polish, and new features for both platforms. Nothing in this document is security- or encryption-related — those items are tracked separately.
**Status:** Not started. This is a task list only; no code has been written for any item below.
**Sources of truth:** Product behavior remains `docs/plans/idea-plan.md`; visuals remain `docs/plans/calculator-disguise-design.md`. Where an item below changes user-visible behavior, the idea plan must be updated in the same change.

---

## How to use this list

Every item is specced as **shared decisions → iOS tasks → Android tasks → tests → done when**. The shared decisions must be settled *before* either platform starts, so the two implementations don't diverge — this project has kept strict cross-platform parity (the 17-row calculator table, key serialization, note derivation, contact sorting, and the shared copy-table string IDs), and these items should hold the same line.

**Rules that apply to every item:**

- New user-facing copy gets a shared string ID, added to **both** `android/app/src/main/res/values/strings.xml` and `ios/SafeBox/Support/Localizable.xcstrings` with the same key.
- Both unit suites (115 iOS / 115 Android at time of writing) stay green; each item adds its own tests.
- No logging of key tokens, buffers, salts, hashes, or lock internals — unchanged from iteration 1.
- Nothing may weaken the disguise: no vault vocabulary reachable from the locked calculator, and no vault content visible in the app switcher / recents.

**Migration batching (important):** P2 (undo/trash) and N3 (video) both require a persistence schema change. If both land in this iteration, do them as a **single** `v1 → v2` migration (Room `Migration(1,2)` + exported schema; SwiftData `SchemaV2` + `MigrationPlan`) rather than two sequential ones. Decide this before starting either.

---

## Fixes

### F1 — Unlock transition

Both roots are a bare switch over lock state ([`app/SafeBoxApp.kt`](../../android/app/src/main/java/com/calcplus/calculator/app/SafeBoxApp.kt), [`App/RootView.swift`](../../ios/SafeBox/App/RootView.swift)), so calculator → vault is a hard cut.

**Shared decisions**
- [ ] Evaluate and pick a motion treatment beyond a plain crossfade. Candidates to compare: fade-through (fade out → fade in with a short gap), shared-axis Z (vault scales up from ~0.92 while the calculator fades and scales up past it), or a masked/circular reveal originating from the `=` key. Pick one; document why.
- [ ] Fix duration and curve (starting point: 200–320ms, emphasized-decelerate). One value used by both platforms.
- [ ] **Lock direction stays instant** — no animation on lock, manual lock, background lock, or epoch bump. Only `Locked → Unlocked` animates.
- [ ] Confirm the transition never animates the `NeedsSetup → Unlocked` first-run path differently than normal unlock (or decide deliberately that it does).
- [ ] Reduced-motion behavior: fall back to a plain opacity crossfade, or to instant. Pick one.

**iOS**
- [ ] Wrap the root switch in a transition-capable container; drive the change with `withAnimation` scoped to the unlock transition only.
- [ ] Keep `.id(coordinator.calculatorEpoch)` recreation un-animated — epoch bumps must not read as a transition.
- [ ] Honor `@Environment(\.accessibilityReduceMotion)`.
- [ ] Verify the `CalculatorCoverView` snapshot cover still appears correctly on resign-active during and after the transition.

**Android**
- [ ] Replace the `when (lockState)` switch with `AnimatedContent`, with a `transitionSpec` that returns the chosen animation for `Locked → Unlocked` and a snap/`ExitTransition.None` for every other direction.
- [ ] Honor `Settings.Global.ANIMATOR_DURATION_SCALE == 0` (and/or the reduced-motion setting) for the fallback.
- [ ] Verify the vault is never composed early enough to appear in a recents snapshot before FLAG_SECURE covers it.

**Tests**
- [ ] Existing lock-state tests unchanged and green — this item must not touch `AppLockManager` / `AppLockCoordinator` logic.
- [ ] Manual: unlock, lock now, background-lock, wrong code, and first-run setup all verified on device for correct (or absent) motion.

**Done when:** unlocking reads as an intentional reveal on both platforms, locking is still instantaneous, and reduced-motion users get the fallback.

---

## UI polish

### P2 — Empty-state padding and spacing

Android's shared `EmptyState` component was already reworked (88dp tinted icon circle, 8/20/24dp rhythm, 32dp horizontal padding, weighted optical centering). The remaining work is iOS parity and the states that were left bare.

**Shared decisions**
- [ ] Confirm the Android component's spacing rhythm is the cross-platform spec, and record it in the design doc.
- [ ] Decide whether *filtered/search* empty states ("No results") get descriptions, or stay title-only by design.

**iOS**
- [ ] Audit all `ContentUnavailableView` usages (`AlbumListScreen`, `AlbumGridScreen`, `NotesListScreen`, `ContactsListScreen`) — none currently pass a description, so they read thinner than Android's.
- [ ] Add the matching one-line descriptions using the shared string IDs.
- [ ] Verify spacing/optical centering against Android on the same content; adjust with a custom container if `ContentUnavailableView` can't match.

**Android**
- [ ] Audit the remaining empty states not covered last round — the "No results" search/filter states in Notes and Contacts still have no description.
- [ ] Check the empty state in the photo grid while an import is active (currently suppressed by `importProgress.isActive`) still looks right.

**Tests**
- [ ] Manual: every empty state on both platforms, light and dark, small and large text sizes.

**Done when:** all eight empty states (4 per platform) share one spacing spec and read identically in structure.

---

### P3 — Deletion undo / "Recently deleted"

Every destructive path is confirm-dialog-then-gone; there is no undo anywhere. This is the largest item in the polish tier and the one with real data-loss consequences.

**Shared decisions**
- [ ] **Choose the model.** A snackbar-only undo is much cheaper but *fragile in this app specifically*: the vault locks and tears down the moment the app backgrounds, so any in-memory undo window dies with it. A persisted "Recently deleted" holding area survives that. Recommend the holding area, with a snackbar as the immediate-feedback layer on top. Decide and record.
- [ ] Which entities: photos, albums, notes, contacts (recommend all four).
- [ ] Retention period (e.g. 30 days) and when purging runs (recommend on unlock, plus at app start).
- [ ] Where the trash UI lives: one entry in Settings, or per-tab. Recommend a single Settings entry to avoid four new screens.
- [ ] Album semantics: soft-deleting an album soft-deletes its photos; restoring the album restores them. Confirm.
- [ ] Erase-everything (`VaultNuker`) must hard-purge trash too — it currently clears all tables, so verify it still does once soft-delete lands.

**Shared / persistence**
- [ ] Add `deletedAt` (nullable timestamp) to photos, albums, notes, contacts. See the migration-batching note above.
- [ ] Every observe/list query filters `deletedAt IS NULL`.
- [ ] **Orphan sweep gotcha:** `sweepOrphans` deletes vault files not referenced by a row. Trashed photos keep their rows, so this is safe *only if* the sweep enumerates trashed rows too — verify explicitly on both platforms, this is an easy way to silently destroy trashed media.
- [ ] Photo files are **not** deleted on soft-delete; they are deleted on purge/expiry. The "delete rows AND files" invariant moves from delete-time to purge-time.

**iOS**
- [ ] `SchemaV2` + migration plan (lightweight if only additive).
- [ ] Repository changes: `delete` → soft-delete; add `restore`, `purge`, `purgeExpired`.
- [ ] "Recently deleted" screen with restore / delete-now / empty-all.
- [ ] Snackbar-equivalent undo affordance after a delete.

**Android**
- [ ] Room `Migration(1, 2)` + updated exported schema in `app/schemas`.
- [ ] Same repository changes as iOS.
- [ ] "Recently deleted" screen (new nav route under Settings).
- [ ] `Snackbar` with an Undo action wired to `restore`.

**Tests**
- [ ] Soft-deleted items disappear from every list and search.
- [ ] Restore returns the item, its files, and (for photos) its album membership.
- [ ] Purge deletes both the row and the full-size + thumbnail files.
- [ ] Expired items purge on unlock; non-expired survive.
- [ ] Orphan sweep does **not** delete files belonging to trashed rows.
- [ ] `VaultNuker` purges trash (both rows and files).
- [ ] Album soft-delete/restore round-trips its photos.

**Done when:** no routine delete on either platform is unrecoverable, and the trash cannot leak files past an erase-everything.

---

### P4 — Album sort and reorder, note sort

Albums are ordered by `sortIndex` (insertion order) with no user control; notes are modified-date only. `sortIndex` was built for manual ordering but nothing writes it except append.

**Shared decisions**
- [ ] Album sort options: name A–Z, date created, photo count, manual. Confirm the set and the default.
- [ ] Note sort options: modified, created, title. Confirm the set and the default.
- [ ] Where the preference persists (reuse the existing prefs store on each platform) and whether it's per-tab or global.
- [ ] **Split the work:** ship sort first — it is cheap and captures most of the value. Drag-to-reorder is expensive on *both* platforms (neither SwiftUI's `LazyVGrid` nor Compose's `LazyVerticalGrid` has built-in grid reordering) and should be a separate follow-up, not a blocker.

**iOS**
- [ ] Sort menu in the Gallery and Notes toolbars; persist the choice.
- [ ] Apply sort in the view model (or the fetch descriptor) rather than re-sorting in the view body.
- [ ] *(Follow-up)* Manual reorder writing `sortIndex` — needs a custom drag implementation for the grid.

**Android**
- [ ] Sort menu in the Gallery and Notes top bars; persist the choice.
- [ ] Add DAO query variants (or an `ORDER BY` parameterized query) per sort mode.
- [ ] *(Follow-up)* Manual reorder via `detectDragGesturesAfterLongPress` + index math writing `sortIndex`.

**Tests**
- [ ] Each sort mode returns the expected order, including ties.
- [ ] Preference survives lock, process death, and relaunch.
- [ ] *(Follow-up)* Reorder persists `sortIndex` and survives relaunch.

**Done when:** albums and notes can be sorted by the agreed modes on both platforms, with the choice remembered.

---

### P5 — Settings restructure, "How it works" launches the guide

The About section is four long paragraphs rendered as list subtitles. Meanwhile the onboarding guide already explains the same thing far better.

**Shared decisions**
- [ ] New Settings information architecture: which rows stay inline, which move behind a detail screen, what order.
- [ ] "How it works" becomes an action that **re-launches the onboarding guide** in a *revisit* mode instead of a static paragraph.
- [ ] Revisit mode differences: final CTA reads "Done" (not "Set my code"), finishing does **not** write the onboarding-complete flag (it's already set), and it is dismissible at any point.
- [ ] New string ID for the revisit CTA, added to both platforms.
- [ ] **Disguise constraint:** the guide contains vault vocabulary, so revisit mode must be reachable *only* from inside the unlocked vault. It must never be presentable from the locked calculator.

**iOS**
- [ ] Add a `mode` parameter to `OnboardingView` (first-run vs revisit) controlling the CTA label and the finish action.
- [ ] Present it as a sheet from `SettingsScreen`.
- [ ] Restructure the About section per the agreed IA.

**Android**
- [ ] Add the same `mode` parameter to `OnboardingScreen`.
- [ ] Present it as a full-screen route inside the vault nav graph (not the root lock switch).
- [ ] Restructure the About section per the agreed IA.

**Tests**
- [ ] Revisit mode does not alter the persisted onboarding flag.
- [ ] Finishing or dismissing revisit mode returns to Settings with the vault still unlocked.
- [ ] First-run mode behavior is unchanged (existing tests stay green).

**Done when:** Settings is scannable, and "How it works" opens the real guide on both platforms without touching first-run state.

---

### P6 — Multi-select in Notes and Contacts

Selection exists only in the photo grid, so deleting five notes means five confirm dialogs.

**Shared decisions**
- [ ] Entry gesture: long-press to enter selection mode (mirrors the photo grid). Confirm.
- [ ] Bulk actions in scope: delete only for v1. (Bulk tagging for notes is a possible follow-up — decide now whether it's in or out.)
- [ ] One confirm dialog for the whole selection, with the count in the message.
- [ ] **Sequencing:** land after P3 (undo), so bulk delete is recoverable. Bulk-deleting five notes irreversibly is exactly the misfire P3 exists to prevent.

**iOS**
- [ ] Use `List` edit mode with a selection binding — SwiftUI provides multi-select natively here, so this should be markedly cheaper than the Android side.
- [ ] Toolbar swaps to selection actions; Cancel exits.

**Android**
- [ ] Mirror the `PhotoGridScreen` pattern: `isSelecting` / `selection` state in the view model, top bar swaps, per-row selection indicator.
- [ ] Ensure it composes correctly with the existing swipe-to-delete on notes (disable swipe while selecting).

**Tests**
- [ ] Selection add/remove/clear; exiting clears selection.
- [ ] Bulk delete calls the repository once with N ids, not N times.
- [ ] Selection state resets on lock.

**Done when:** notes and contacts support the same long-press multi-select and bulk delete as photos.

---

## New features

### N1 — Global search

Each tab searches only itself; there is no way to search the vault as a whole.

**Shared decisions**
- [ ] Entry point. A fifth tab would violate the four-tab spec in the idea plan, so recommend a search affordance in each tab's top bar that opens one full-screen global search.
- [ ] Scope of matching: note title/body/tags, contact name/org/phone/email, album name. Decide whether photos participate at all (filenames are UUIDs and meaningless to users — likely album-name matches only).
- [ ] Result presentation: grouped by type with section headers, or interleaved and ranked. Recommend grouped.
- [ ] Tapping a result must navigate to the correct tab **and** push the detail screen. Define this cross-tab navigation contract once for both platforms — it is the fiddliest part of this item.
- [ ] Reuse the existing 300ms debounce.
- [ ] Empty state and no-query state copy (shared string IDs).

**iOS**
- [ ] New search screen with `.searchable`.
- [ ] Cross-tab navigation: switch the selected tab and push the detail — requires routing the tab selection and each tab's navigation path from a shared place.

**Android**
- [ ] New search route.
- [ ] Cross-tab navigation through the nested `navigation<Tab>` graphs while preserving the existing saveState/restoreState behavior — verify back-stack behavior after a cross-tab jump.

**Tests**
- [ ] Query matches expected entities across all types; is case- and diacritic-insensitive consistently with existing per-tab search.
- [ ] Empty query shows the no-query state, not all results.
- [ ] Navigating to a result lands on the right detail screen with the right tab selected.

**Done when:** one search field finds notes, contacts, and albums, and results navigate correctly on both platforms.

---

### N2 — Photo metadata in the pager

Every field is already stored on the photo entity (`width`, `height`, `byteCount`, `mimeType`, `importedAt`) and never surfaced. Smallest item here and fully self-contained — a good first task.

**Shared decisions**
- [ ] Fields to show and their order: dimensions, file size, type, imported date. Confirm.
- [ ] Presentation: info button in the pager toolbar opening a sheet / bottom sheet. Confirm.
- [ ] Date format (localized, medium) and byte format (localized, human-readable).
- [ ] Labels get shared string IDs on both platforms.

**iOS**
- [ ] Info button in `PhotoPagerScreen`; sheet with the metadata rows.
- [ ] Format bytes with `ByteCountFormatter`, dates with a localized formatter.

**Android**
- [ ] Info action in `PhotoPagerScreen`; `ModalBottomSheet` with the metadata rows.
- [ ] Format bytes with `Formatter.formatShortFileSize`, dates with a localized formatter.

**Tests**
- [ ] Formatting is correct at boundary sizes (bytes, KB, MB, GB) and locale-appropriate.
- [ ] Metadata reflects the real stored values for an imported photo.

**Done when:** any photo's details are one tap away in the pager on both platforms.

---

### N3 — Video support

The file store, thumbnailer, grid, and pager are all photo-only. This is the biggest single expansion of what the vault holds, and should be scheduled accordingly.

**Shared decisions**
- [ ] Schema: add `mediaType` (photo/video) and `durationMs` to the photo entity, keeping one table for mixed media. See the migration-batching note above.
- [ ] Formats accepted, and whether any size cap applies.
- [ ] Thumbnail policy: extract a frame at a fixed offset (e.g. 0s or 1s) and store it exactly like a photo thumbnail, so the grid stays uniform.
- [ ] Grid affordance for videos: play glyph plus duration badge. Confirm placement.
- [ ] **Playback rules while locked** — decide and document: playback must stop and tear down when the app backgrounds (it locks anyway), and Picture-in-Picture must be **disabled**, since PiP would render vault content outside the app in a floating window. This is a disguise/containment concern, not a crypto one, and it is easy to get wrong by inheriting platform defaults.
- [ ] Import progress currently counts files; large videos make that misleading. Decide whether to move to byte-based progress.

**iOS**
- [ ] Extend the picker to `.any(of: [.images, .videos])`.
- [ ] Extract the poster frame with `AVAssetImageGenerator` and duration with `AVAsset`.
- [ ] Grid badge; `VideoPlayer`/`AVPlayer` in the pager, with teardown on lock and PiP disabled.
- [ ] Confirm originals are still copied byte-for-byte with the real extension (no re-encode).

**Android**
- [ ] Extend the picker to `PickVisualMedia.ImageAndVideo`.
- [ ] Extract the frame and duration with `MediaMetadataRetriever`.
- [ ] Add a player dependency (Media3/ExoPlayer) — new dependency, note it in the build files and the licenses row in Settings.
- [ ] Grid badge; player in the pager, with teardown on lock and PiP disabled.
- [ ] Confirm FLAG_SECURE still covers video surfaces (verify with a real capture attempt, since some video surfaces behave differently).

**Tests**
- [ ] Video import stores the original bytes unchanged, a poster thumbnail, and a correct duration.
- [ ] A mixed photo/video album lists and orders correctly.
- [ ] Player is released on lock/background.
- [ ] Deleting a video removes the original and its thumbnail.
- [ ] Licenses row updated for the new dependency (Android).

**Done when:** videos import, list, play, and delete on both platforms with the same guarantees photos already have.

---

## Suggested sequence

1. **N2** (photo metadata) — smallest, self-contained, immediate payoff.
2. **P2** (empty-state parity) and **F1** (unlock transition) — independent polish, no persistence impact.
3. **P5** (Settings + guide revisit) — reuses what already exists.
4. **P3** (undo / recently deleted) — schema change; batch its migration with N3 if both are in scope.
5. **P6** (multi-select) — deliberately after P3 so bulk delete is recoverable.
6. **P4** (sort now, drag-reorder as a follow-up).
7. **N1** (global search) — cross-tab navigation is the real cost.
8. **N3** (video) — largest; plan its migration together with P3's.

Items 1–3 are roughly a day's work combined and make the app feel notably more finished. Items 4–8 are each a meaningful chunk on their own.
