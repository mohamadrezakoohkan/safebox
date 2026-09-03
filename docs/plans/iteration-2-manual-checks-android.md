# SafeBox — Iteration 2 Manual Checks (Android)

**Document:** `docs/plans/iteration-2-manual-checks-android.md`
**Scope:** On-device verification for the iteration-2 items. Everything here is behavior that unit tests cannot assert — motion, layout, gestures, containment (recents, FLAG_SECURE over the video surface, Picture-in-Picture), TalkBack and dark mode. **Partially executed** — see "Executed on device" below; everything not listed there is still outstanding. Companion: `iteration-2-manual-checks-ios.md`; the same behavior must pass on both platforms. Authority for what the checks assert: `iteration-2-decisions.md`.

## Executed on device — 2026-09-03 (Pixel_10 emulator, Android 16)

All of the following were driven on the emulator and passed. Everything else in this document remains unverified.

- **FLAG_SECURE over the whole app** — `adb exec-out screencap` of the unlocked vault returns a fully black frame (15 KB PNG) while the same capture of the launcher returns real content (1.4 MB). Screen capture of vault content is blocked.
- **FLAG_SECURE over the VIDEO surface (the blocking check)** — with the H.264 clip **actually decoding** (`media.metrics`: `c2.android.avc.decoder`, `video/avc`, 640×480, 31 frames decoded), a capture of the pager contains **zero** non-black pixels inside the app window (y 104–2380); the only content anywhere in the frame is the system status and navigation bars, which are not part of the app's window. `dumpsys SurfaceFlinger --list` shows no separate SurfaceView layer for the app — only the MainActivity window and its ViewRootImpl — confirming the `surface_type="texture_view"` choice keeps video inside the protected window. **A SurfaceView here would have been the leak this check exists to catch.**
- **Disguise / tell audit** — the locked calculator's accessibility tree exposes only calculator vocabulary (`seven`, `plus`, `equals`, `result`, `all clear`); nothing vault-related is present.
- **F1 / first-run** — a wrong code just calculates (display shows the arithmetic result, no feedback); setting a code shows the one-time "Remember your code" notice, which appears only after the vault is revealed.
- **P5 first-run unchanged** — the first-run guide's top-right button reads **Skip** (revisit mode is the one that reads Done), and the setup banner and hint are unchanged.
- **P5** — Settings IA exactly as specified: Security (Change passcode, Lock now) / Data (Recently deleted, subtitle "Items are kept for 30 days, then deleted permanently.", above Erase everything) / About (Version 1.0.0, How it works + "Revisit the guide", Privacy + "All data stays on this device.", Open-source licenses).
- **N3 licenses row** — reads "Jetpack (Apache 2.0), **Media3 (Apache 2.0)**, Coil (Apache 2.0), Kotlin & kotlinx libraries (Apache 2.0)."
- **P2** — Gallery and empty-album states render icon + title + one-line description + action ("No albums yet" / "Albums keep your imported photos and videos organized." / "Create album"; "No photos yet" / "Imports are copies — the originals stay in your library." / "Import photos").
- **N1 / P4 entry points** — the Gallery top bar carries Search and "Sort by" actions; the four-tab bar is intact.
- **N3 import** — the system picker offers images **and** video ("Video taken on … with duration 00:06"); importing a video plus a photo into one album produces a mixed grid whose video cell shows the duration badge **0:06 at the bottom-left**, with the poster frame extracted.
- **P3 delete copy** — the pager's delete dialog reads "Delete this photo?" / "You can restore it from Recently deleted for 30 days." with Delete / Cancel.
- **Regression found and fixed on device:** tapping a grid cell never opened the pager, so no photo or video could be viewed. `PhotoPagerViewModel.photos` seeded its `stateIn` with `emptyList()`, and the pager treats an empty list as "nothing to page" and pops itself — so it closed on its first composition, before Room answered. Now seeded with `null` (the pending convention used by every other list in the vault) and the pager renders nothing until the first emission. Covered by `PhotoPagerViewModelTest` (3 tests).

Not executed: the v1→v2 migration over a real v1 install, expiry by clock-shift, multi-select gestures, sort persistence across process death, the cross-tab back-stack walk, dark mode, TalkBack, and large-font layouts.


## Strings
- None. Foundation step only (resource additions + unit test); no UI reachable from these keys yet. Device checks land with the items that consume them (P2, N2, P3, P4, P5, P6, N1, N3).


## N2 — Photo metadata in the pager
- Gallery → album → open a photo → tap the (i) icon (first top-bar action, before the folder and trash icons). A bottom sheet titled "Details" appears with four rows in this order: Dimensions, File size, Type, Imported.
- Values match the source: compare Dimensions ("W × H") and File size with the same file's details in the system Photos/Files app; Type shows JPEG/PNG/HEIC/WEBP/GIF/BMP for the corresponding import; Imported shows the import moment as a medium date + short time in the device locale.
- Swipe to the next photo, tap (i) again → the rows now describe the new photo (especially Dimensions and File size).
- Dismiss paths: swipe the sheet down, tap the scrim, system back gesture (sheet closes first; the pager stays). Reopening works repeatedly.
- With the sheet open, take the app to recents / app switcher → thumbnail is blank (FLAG_SECURE covers the sheet's dialog window as well).
- Dark mode and light mode: the sheet is legible over the black pager; labels are secondary-tinted, values primary.
- Landscape is not applicable (activity is portrait-only), but verify the sheet content is not hidden under the navigation bar on a gesture-nav device and a 3-button-nav device.
- Change device language (e.g. German) → Imported reads like "02.09.2026, 14:05" and File size uses the localized unit/decimal separator; Type labels stay JPEG/PNG etc. (intentionally untranslated).
- Move and Delete from the pager still work with the Info action present; after a delete the pager advances and (i) shows the newly current photo.


## P2 — Empty-state parity
- Fresh vault (or right after Erase everything): Gallery shows the icon circle + "No albums yet" + "Albums keep your imported photos and videos organized." + a filled "Create album" button; the button opens the same New album dialog as the FAB.
- Create an album and open it: "No photos yet" + "Imports are copies — the originals stay in your library." + "Import photos"; the button launches the system picker and the app does not lock while the picker is in front.
- Import several large photos into that empty album: while importing, no empty state is shown and the "Importing N/M…" pill sits centered in the grid area (not at the bottom). When the first thumbnail appears the pill moves to bottom-center; when the import finishes the pill disappears and the grid stays.
- Notes tab with no notes: "No notes yet" + "Notes support markdown with a live preview." + "New note" button → opens a fresh editor (same as the FAB).
- Notes with at least one note: type a query matching nothing → "No results" + "Check the spelling or try a different search.", no button. Clear the query → list returns. Pick a tag chip whose notes do not match the query (or a tag with no live notes) → the same "No results" state with the description.
- Contacts tab with no contacts: "No contacts yet" + "Contacts live only in this vault." + "Add contact" → opens the editor. With contacts present, search a non-matching name → "No results" + description, no button.
- Every state above in light and dark: circle fill is surfaceContainerHigh, glyph and description are onSurfaceVariant, title is the primary text color.
- Font scale at the largest and smallest steps (Settings → Display → Font size): all eight strings wrap and stay centered inside the 32dp side padding, the button does not clip, no horizontal overflow.
- Spacing against iOS on the same content: 88dp circle, 20dp to title, 8dp to description, 24dp to button, block sits slightly above center (1 : 1.35). Notes/Contacts states are centered in the area below the search field (and chips) by design.
- Switch device language: nothing in the eight states or the import pill is hardcoded — copy still comes from strings.xml (English-only values for now), so the text is unchanged but must not show any stale literal.


## N2 (revision) — duration rounding
- No new device-visible surface today: the Duration row renders only once N3 passes a video duration. When N3 lands, import a clip of roughly 59.6 s and confirm both the grid badge and the Details sheet read "1:00" (not "0:59"); a clip under 0.5 s reads "0:00". Compare against the same clip on iOS — the strings must be identical.
- Re-run the existing N2 checklist above unchanged; nothing in the sheet layout or the Info action moved in this pass.


## P2 (revision) — glyph parity and scroll-on-overflow
- Notes tab with at least one note: type a query matching nothing → the icon circle shows the magnifier glyph (not the note glyph) above "No results" + description. Clear the query → the note glyph returns with "No notes yet" (only if there are no notes; otherwise the list). Pick a tag chip with no matching notes → magnifier as well. Compare with iOS on the same input: same glyph family (magnifier), same copy.
- Contacts tab: search a non-matching name → magnifier + "No results" + description, no button. Clear → person glyph + "No contacts yet" when the vault has no contacts.
- Gallery root and an empty album: no visible change expected (photo glyph, same copy); confirm "Create album" and "Import photos" still open the dialog / picker and the block still sits slightly above center.
- Font size at maximum (Settings → Display → Font size, and Display size at maximum) on a small-screen device: Notes tab with several tags, tap the search field so the keyboard is up, type a non-matching query → the "No results" block is not clipped; if it does not fit, it scrolls vertically and the whole description can be read. Same with no query and no notes: the "New note" button can be reached by scrolling. Dismiss the keyboard → the block re-centers.
- At the default font size the empty states must not scroll and must show no overscroll stretch when dragged up or down.
- Optical position is unchanged versus the previous build: with a ruler/screenshot, free space above the circle vs. below the button (or below the description when there is no button) is about 1 : 1.35 in every state.
- Developer options → "Force RTL layout direction": every empty state stays horizontally centered inside the 32dp side padding; the note glyph mirrors, the magnifier / photo / person glyphs do not.
- Light and dark mode: unchanged tokens (circle `surfaceContainerHigh`, glyph and description `onSurfaceVariant`), now also verified on the two magnifier states.


## F1 — Unlock transition (zoom-in reveal)
- Unlock: with a passcode set, type it and press `=` → the vault fades in while growing from ~92 % to full size and the calculator fades out in place over ~260 ms with a fast-then-settling (emphasized-decelerate) curve. The calculator keeps showing its last display (the `=` result) as it fades — no flicker to "0", no caption vanishing mid-fade. No flash of bare background between the two surfaces, no size jump, no clipping at the edges while the vault is still scaled down. The first vault frame appears on the `=` press itself (the §5.4 budget point), not after a gap.
- Lock now (Settings → Lock now): the calculator appears instantly — no fade, no scale — showing a pristine "0".
- Background lock: from the vault press Home (or open recents) and return → calculator instantly, pristine. The recents thumbnail is blank (FLAG_SECURE) whether taken while unlocked or mid-reveal.
- Lock during the reveal: unlock and press Home within the fade → on return the calculator is pristine ("0"), no residual vault pixels, no stuck half-faded frame; unlocking again replays the reveal normally.
- Wrong code: a non-matching sequence + `=` → the calculator shows the arithmetic result, nothing animates, nothing hints. Same for sub-minimum and overflowed sequences.
- First-run setup (fresh install or right after Erase everything): finish the guide, enter the code + `=`, re-enter to confirm + `=` → the same zoom-in reveal as a normal unlock (not a different motion). The "Enter it again to confirm" caption stays on the fading calculator. The no-recovery dialog appears only after the reveal has fully settled — never over the fading calculator — and Ok dismisses it.
- Erase everything (Settings → Erase everything → confirm twice): the onboarding guide appears as an instant cut, no reveal, no fade.
- Developer options → Animator duration scale = "Animation off": unlock (and setup confirm) is an instant cut — no fade, no scale — and the no-recovery dialog appears at once. Switch back to 1x *without* restarting the app → the very next unlock plays the reveal again. 5x → one slowed continuous motion (~1.3 s); 0.5x → ~130 ms; lock stays instant at every scale.
- During the reveal, tap where the vault's top bar title lands (over the calculator display) and where a calculator key sits under a non-interactive vault region → no key press registers on the fading calculator (no key highlight, no display change); once the reveal settles every vault control responds normally.
- Repeat unlock/lock ten times quickly → no animation build-up, no ghost surfaces, memory stable.
- Dark and light theme: no white or black flash between the two surfaces during the crossfade.
- TalkBack on / font scale at maximum: the transition rule is unchanged (TalkBack does not disable animations); the reveal still plays and the dialog still waits for it.


## F1 fix — interrupted reveal is a hard cut
- Lock within 260 ms of unlock (Developer options → Animator duration scale 5x makes the ~1.3 s reveal easy to interrupt; also try 1x): unlock, then trigger a lock while the vault is still fading in — press Home / open recents, or pull the notification shade and let the app stop. The vault is gone on the very next frame: no half-faded vault springing back to fully opaque, no vault pixel visible behind or above the calculator. On return the calculator is pristine ("0").
- Same at 1x with a screen recording (developer-side): step through the frames around the lock — the first frame after the lock shows the calculator fully opaque on top; no frame shows any vault content over the calculator.
- Normal lock (Settings → Lock now, background lock) is visually unchanged from before: instant, calculator pristine, no fade.
- TalkBack on: during the 260 ms reveal, swipe to move accessibility focus — the fading calculator's keys and display are not announced or focusable; only the vault's controls are. After a lock, the vault's controls are not announced while the calculator is up.
- Reveal stacking: during the reveal the calculator fades away IN FRONT of the vault growing up from behind (the vault is never drawn over the calculator); no bare-background flash, same as before.
- Known residual (not a vault leak): if a lock lands mid-reveal while the app stays visible, the calculator may ease from its mid-fade opacity back to full over ~0.3 s on top of the plain window background; no vault content is involved. Confirm that this is the ONLY motion in that case.


## P5 — Settings restructure + guide revisit mode
- Settings tab: title "Settings"; sections in order Security (Change passcode, Lock now) → Data (Erase everything only, red title, with its subtitle) → About (Version with the version number inline at the trailing edge, How it works "Revisit the guide", Privacy "All data stays on this device.", Open-source licenses with the library list). No long paragraphs anywhere; the whole screen is scannable without scrolling on a normal phone.
- Change device language: every Settings string still resolves from strings.xml (English values) — no stale literal, and the tab bar labels Gallery / Notes / Contacts / Settings also come from resources now.
- Version: matches `versionName` (1.0.0) and is not clickable (no ripple).
- How it works → the guide opens full-screen in the disguise palette, the bottom NavigationBar is gone, and the top-right button reads "Done" on page 1. Swipe through pages 2, 3, 4: "Done" is present on EVERY page (including the last, where the first-run guide hides Skip); the final CTA on page 4 reads "Done", not "Set my code". The next-button on pages 1–3 still reads "Next"; the playground keys, flip card and pulse animate as on first run.
- Tap "Done" on each page in turn (open the guide four times): each time it returns to Settings with the bottom bar back, the vault still unlocked (no calculator), and Settings scrolled where it was.
- Tap the final CTA "Done" on page 4 → same result.
- System back (gesture or button) while the guide is open → returns to Settings unlocked; the bottom bar is back.
- After any of the above: Settings → Erase everything → confirm twice → the FIRST-RUN guide appears (proves revisiting never wrote or cleared first-run state — the flag is still whatever it was; after erase it is reset by the nuke as before). Cancel out instead where you don't want to erase: the check is simply that revisiting changes nothing about when the first-run guide shows.
- Kill and relaunch the app after a revisit → calculator appears as usual (not the guide): the persisted onboarding flag is still set.
- With the guide open, press Home → the app locks; on return the calculator is up (the guide route died with the vault), unlocking lands on the Settings tab. Recents thumbnail blank (FLAG_SECURE).
- Privacy → a screen titled "Privacy" with a back arrow, two body paragraphs: the privacy statement and the no-recovery warning. Back arrow and system back return to Settings; the bottom bar stays visible on this screen.
- Double-tap "How it works" or "Privacy" quickly → only one instance is pushed (launchSingleTop); one back returns to Settings.
- Erase everything: the two dialogs' dismiss buttons read "Cancel"; Continue / Erase everything unchanged in red.
- First-run guide (fresh install or right after Erase everything): unchanged — Skip on pages 1–3 and hidden on page 4, Next, final CTA "Set my code" → the setup calculator with the "Choose your code" caption; skipping on page 1 goes straight to setup. Relaunch after setup → calculator, not the guide.
- TalkBack: the bottom bar items announce Gallery / Notes / Contacts / Settings; the Privacy back arrow announces "Back"; "Done" is announced on every guide page in revisit mode.
- Light and dark: Settings rows use the M3 scheme (error red for Erase everything, onSurfaceVariant subtitles/version); the guide keeps the disguise palette; Privacy body uses bodyLarge in the primary text color.


## F1 draw guard — vault never visible after lock
- Supersedes the "Known residual" bullet under "F1 fix" above. The vault is never visible after a lock, even if the lock lands mid-reveal. With Developer options → Animator duration scale 5x (reveal ≈ 1.3 s): unlock, then within the fade trigger a lock while the app stays on screen (Settings → Lock now is not reachable that fast; use the notification shade pull-and-release, or a quick Home + return, or `adb shell am start` of another activity and back). Step through a screen recording: from the first post-lock frame onward NO vault pixel appears anywhere — not through the calculator, not behind it, not at the edges. Repeat at 1x.
- The calculator may still ease from its mid-fade alpha back to fully opaque over ~0.4 s on top of the plain window background — this is the accepted residual; it must be the ONLY motion, and the background behind it must be the bare theme background, never vault content.
- Plain lock (Lock now, Home, notification shade) with no reveal running: unchanged — instant cut, calculator pristine ("0").
- Normal unlock: the reveal is visually unchanged (the guard only skips an EXITING vault; the entering vault draws from its first frame). Confirm no black/blank first frame and that the vault's top bar, list content and bottom bar all appear together with the fade+scale.
- Erase everything from the vault: the first-run guide cuts in instantly, no vault frame lingers.
- TalkBack: after a lock mid-reveal, no vault control is announced or focusable (same as before); the calculator keys are.


## P3 — Recently deleted, undo snackbar, v1→v2 migration
- **Migration on a real device (the only unit-untestable part of P3):** install the LAST commit's build (schema v1), create an album with 2 photos, a tagged note and a contact, lock it. Install this build over it (`adb install -r`, no uninstall). Unlock → every item is still there, nothing is in Recently deleted, no crash on first DB open. A wipe here means `fallbackToDestructiveMigration` crept back in.
- Settings → Data → "Recently deleted" sits ABOVE "Erase everything" with the subtitle "Items are kept for 30 days, then deleted permanently."; tapping it pushes the trash screen **with the bottom bar still visible** (only the guide hides it).
- Delete a photo from the pager: dialog reads "Delete this photo?" / "You can restore it from Recently deleted for 30 days." with Delete / Cancel. Confirm → the pager pops (if it was the last photo) and the snackbar "Photo deleted" + UNDO appears over the grid, above the bottom bar. Tap UNDO → the photo returns in its original position in the grid.
- Same for: photo grid multi-select (2+ photos → "Delete 2 photos?" and "2 photos deleted"), note swipe-to-delete, the note editor's trash icon (snackbar lands on the notes list after the editor pops), contact detail's trash icon (lands on the contacts list), album long-press → Delete ("Delete album and its N photos?" where N is the LIVE count).
- Let a snackbar time out instead of tapping UNDO (~5 s) → the item is still in Recently deleted; restore it from there.
- Delete an album with photos → the album disappears from Gallery; in the trash it appears under "Albums" with a cover thumbnail, "N photos · 30 days left" and no separate entries under "Photos". Restore it → the album and exactly those photos come back; a photo you had deleted individually BEFORE deleting the album stays in the trash.
- Trash rows show BOTH "Restore" and "Delete now" as visible text buttons (no swipe, no long-press needed). "Delete now" purges with no extra confirm; the item and its files are gone for good.
- Toolbar "Empty" is disabled when the trash is empty; enabled otherwise it asks "Delete everything in Recently deleted?" / "This permanently deletes every item here. This cannot be undone." with Delete (red) / Cancel. Confirm → the trash shows "Nothing here" with the trash glyph, and live content is untouched.
- Storage check: after deleting a photo (not purging), `adb shell run-as com.calcplus.calculator ls files/vault/photos` still lists the file; after "Delete now" or Empty it is gone. Kill and relaunch between the two — the startup orphan sweep must NOT remove a trashed photo's files.
- Lock the vault with items in the trash, unlock again → they are still there (the trash is persisted, unlike the snackbar). Deleting something and locking IMMEDIATELY (before the snackbar times out) is safe: the item is in the trash; the snackbar is gone.
- Erase everything with items in Recently deleted → after the reset and a new passcode, Recently deleted is empty and `files/vault` holds no bytes.
- Expiry cannot be waited out by hand; to simulate, set the device date forward 31 days, background+unlock the app → the trash empties itself (this runs at app start AND on every unlock). Set the date back afterwards.
- TalkBack: the trash back arrow announces "Back"; each row announces its title, "N days left", then the Restore and Delete now buttons; the snackbar announces its message and the Undo action.
- Light and dark: "Delete now" is error-red, the section labels are primary-colored, the row thumbnails are 44 dp rounded; the snackbar uses the M3 inverse surface and sits above the bottom bar.


## P6 — Multi-select in Notes and Contacts (device checks)

Not coverable by unit tests: gestures, the two top-bar shapes, and how the
snackbar reads over the bottom bar.

**Notes**
- [ ] Long-press a note row: the bar swaps to "1 selected" with a trash icon and Cancel, the FAB disappears, and every row grows a leading circle glyph.
- [ ] Tap three more rows: the title counts up; tap one again: it counts back down and the glyph empties.
- [ ] Cancel: the bar returns to "Notes" with the sort menu, the FAB comes back, and every glyph is gone.
- [ ] With 0 selected (long-press then untap the same row) the trash icon is visibly disabled and does nothing; Cancel still works.
- [ ] Delete 3 notes: ONE dialog reading "Delete 3 notes?" / "You can restore it from Recently deleted for 30 days.", then one snackbar "3 notes deleted" with Undo. Tap Undo — all three return.
- [ ] Delete exactly 1 through the selection bar: the dialog reads "Delete this note?" and the snackbar "Note deleted".
- [ ] Let the snackbar time out instead, then Settings → Recently deleted: all three notes are listed under Notes.
- [ ] **Swipe while selecting**: drag a row right-to-left in selection mode — nothing moves, no red background appears. Cancel first, then the same swipe still opens the single-note confirm.
- [ ] Swipe-delete one note while browsing: dialog "Delete this note?", snackbar "Note deleted", Undo restores it.
- [ ] Type in the search box while selecting: the count does NOT change as rows disappear, and Delete still removes exactly the notes counted.
- [ ] Enter selection mode, then background the app (auto-lock) and unlock: the notes list is back in browsing mode with nothing selected.
- [ ] TalkBack: focus a note row while selecting — it announces as selected/not selected; the trash icon announces "Delete"; a long-press through TalkBack's own gesture enters selection mode.

**Contacts**
- [ ] Long-press a contact row: same bar swap, same glyphs, FAB hidden.
- [ ] **Long-press a sticky section header ("A", "B", "#")**: nothing happens — headers never select, never highlight, and the header keeps sticking while scrolling in selection mode.
- [ ] Tapping a row while browsing still opens the contact detail; tapping while selecting only toggles.
- [ ] Delete 2 contacts: one dialog "Delete 2 contacts?", one snackbar "2 contacts deleted" + Undo; Undo returns both, in their original sections.
- [ ] Delete 1: "Delete this contact?" / "Contact deleted".
- [ ] Scroll a long list in selection mode: no accidental toggles from a fast flick (the tap must be a tap, not a drag).

**Both, dark mode + largest font size**
- [ ] The selection bar's count title is not truncated at the largest font setting; the trash icon and Cancel both stay on one line.
- [ ] The selection glyph is visible against the row background in dark mode (primary vs. onSurfaceVariant).


## P4 — Album sort and note sort (device checks)

**Gallery**
- [ ] The top bar shows a sort icon; opening it lists Manual / Name / Date created / Photo count with a check mark on Manual on a fresh install.
- [ ] Create albums "zebra", "Ångström", "apple". Under Name they read Ångström, apple, zebra (case- and accent-insensitive).
- [ ] Under Photo count the album with the most photos is first; delete some of the leader's photos and watch it drop — the trashed ones must not count.
- [ ] Under Date created the newest album is first.
- [ ] Switch back to Manual: the original insertion order returns.
- [ ] Pick Photo count, lock (background), unlock: still Photo count. Force-stop the app and relaunch: **still Photo count**.

**Notes**
- [ ] The browsing top bar shows the sort icon; the menu lists Date modified / Date created / Title, checked on Date modified on a fresh install.
- [ ] Under Title, notes read A→Z ignoring case and accents, and **notes with an empty first line sort to the bottom** (create one empty note to check — it shows as "New note" but ranks last).
- [ ] Editing a note's body bumps it to the top under Date modified but not under Date created or Title.
- [ ] The sort choice survives a lock/unlock and a force-stop relaunch.
- [ ] **The sort icon is absent from the selection bar**: long-press a note — only the trash icon and Cancel are there. Cancel — the sort icon comes back.
- [ ] The tag filter chips and the search box both keep working under every sort mode, and the visible order is the sorted one.

**Erase everything**
- [ ] Set Gallery to Photo count and Notes to Title, then Settings → Erase everything → confirm twice. After the fresh setup, both menus are back on Manual / Date modified.

**Rendering**
- [ ] Dark mode: the dropdown's check mark is legible; the active row is obvious.
- [ ] Largest font size: the menu labels do not truncate.
- [ ] RTL locale (e.g. ar): the sort glyph mirrors (it is `Icons.AutoMirrored.Filled.Sort`) and the menu anchors to the correct edge.


## N1 — Global search (device checks)

**Entry point and chrome**
- [ ] Gallery, Notes and Contacts each show a magnifier in the browsing top bar; the order is `search · sort` on Gallery and Notes, and `search` alone on Contacts.
- [ ] **Settings has no magnifier.**
- [ ] Tapping the magnifier from any of the three tabs opens the same full-screen Search screen, and **the bottom navigation bar disappears** while it is up.
- [ ] Back (arrow or system) from Search returns to the tab you came from with the bottom bar restored, on the same scroll position.
- [ ] "Recently deleted" (Settings → Recently deleted) still SHOWS the bottom bar — only the guide and search hide it.
- [ ] The keyboard comes up on its own when Search opens; the field placeholder reads "Notes, contacts, albums" and the title reads "Search".

**States**
- [ ] With nothing typed: "Search your vault" / "Find notes, contacts, and albums by name or content." — never a list of everything.
- [ ] Type only spaces: still the no-query state.
- [ ] Type "zzzzz": "No results" / "Check the spelling or try a different search."
- [ ] Clear the field with the keyboard: the no-query state returns immediately (no visible 300 ms lag).

**Matching**
- [ ] Create an album "Rëceipts", a note titled "Zoë" and a contact "Zoë Baker". Typing `zoe` finds the note and the contact; typing `receipts` finds the album; typing `ZOË` finds the same rows.
- [ ] Tag a note "Finance" but never write the word in it: typing `finance` finds it under Notes.
- [ ] Give a contact the phone `+34 600 123 456` and the email `ada@example.com`: `600 123` and `ADA@EXAMPLE` both find it.
- [ ] **Photos never appear**: import photos into an album, then search for anything — there is no Photos section.
- [ ] Delete a note, a contact and an album (they go to Recently deleted), then search their names: **no hits**. Restore them from Recently deleted and search again: they are back.
- [ ] Sections appear in the order Albums, Notes, Contacts, and a section with no hits is not rendered at all.

**Cross-tab navigation (the back-stack check)**
- [ ] From **Gallery**, search and tap a **contact**: the Contacts tab is selected and its detail screen is up. Back → the contacts LIST (not search, not Gallery). Back again → Gallery's album list.
- [ ] From **Contacts**, search and tap a **note**: Notes tab, editor open. Back → the notes list.
- [ ] From **Notes**, search and tap an **album**: Gallery tab, that album's photo grid with the album name as the title. Back → the album list.
- [ ] **Deep stack reset:** open Contacts → contact A → back to the list is NOT needed; leave A open, switch to Gallery, search, tap contact B. You land on B, and Back goes to the contacts LIST — contact A must not be underneath.
- [ ] Same with Gallery: leave the photo pager open in an album, switch to Notes, search, tap another album. You land on that album's grid; Back → the album list; the old pager is gone.
- [ ] After any cross-tab jump the bottom bar is back and the correct tab is highlighted.
- [ ] Opening search again after a jump starts with an empty field (the previous query is not restored).

**Per-tab searches (the fold move)**
- [ ] Notes tab search field: `zoe` finds "Zoë"; `ZOË` finds "Zoe". Same on the Contacts tab.
- [ ] Type `%` in the Notes search field: it matches only notes that literally contain `%` (it used to match everything).
- [ ] Contacts tab: typing a full name that spans both name columns ("Ada Lovelace") finds the contact.
- [ ] Notes tab: a tag filter chip plus a query still narrows correctly, and the sort menu still reorders the filtered list.

**Disguise / containment**
- [ ] Search is unreachable from the locked calculator (it lives inside the Unlocked branch by construction — check that no calculator long-press or gesture surfaces it).
- [ ] Background the app while Search is open: the app-switcher card shows nothing of the vault (FLAG_SECURE), and unlocking returns to the calculator, then to the Gallery — never back into Search.

**Rendering**
- [ ] Dark mode: section headers (primary), row titles and secondary lines all legible; the album cover tile's placeholder glyph is visible.
- [ ] Largest font size: rows grow, the two-line note rows do not clip, and the field/placeholder stay readable.
- [ ] RTL locale: the back arrow mirrors; rows lay out right-to-left.


---

## N3 — Video support (device checks)

**Import**
- [ ] Open an album → FAB. The system picker now offers **photos and videos** (Media/Photo picker "Photos and videos" chip). Pick one photo and one video.
- [ ] The import pill counts **items**, not bytes: a 500 MB video and a 2 MB photo are `1/2` then `2/2`.
- [ ] The video lands as a grid cell with a poster frame (roughly the 1 s mark, not a black first frame), a white play glyph centered, and a duration pill at the **bottom-LEFT**.
- [ ] Long-press → Select: the selection check mark is at the **bottom-RIGHT** and does not collide with the duration pill.
- [ ] Import a `.mov` from another device (AirDrop/USB into the phone gallery) — it stores as `.mov`, plays, and the Details sheet's Type reads `MOV`.
- [ ] Import a deliberately corrupt `.mp4` (truncate a real one with `head -c 5000`). The snackbar reads **"Some videos could not be imported."** with **no Undo button**, and the album shows no broken cell. Photos in the same batch still import.
- [ ] Portrait clip recorded on the phone: Details → Dimensions reads portrait (e.g. `1080 × 1920`), matching the poster's orientation. This is the rotation-metadata check.

**Playback**
- [ ] Tap a video cell → the pager shows an ExoPlayer with a controller; it does **not** auto-play. Press play; sound and picture work.
- [ ] Swipe to the next page while playing: audio stops immediately and does not resume. Swipe back: the player is rebuilt paused at the start.
- [ ] Press Home while playing → the app locks; reopening lands on the calculator. **Listen: no audio continues** after Home.
- [ ] Pull down the notification shade / open Control-Center-style quick settings while playing: playback pauses (ON_PAUSE), the item is kept, and dismissing the shade leaves it paused (never auto-resumes).
- [ ] There is **no media notification** and **nothing on the lock screen** while a vault video plays (no MediaSession is created).
- [ ] Play a video, then rotate/multi-window if the device allows: no PiP window ever appears; the swipe-up-to-home gesture does not float the video.

**FLAG_SECURE over the video surface (the real capture attempt — do this one, do not assume)**
- [ ] With a video **playing**, press the screenshot chord. Expect the system toast "Can't take screenshot due to security policy" (or a black capture). The screenshot must **not** contain the video frame.
- [ ] With a video **playing**, start a screen recording (quick-settings tile). Play for ~10 s, stop, and open the recording: the video area must be **black**, not the vault footage. *(This is the check that a `surface_view` PlayerView would fail — the layout pins `surface_type="texture_view"` for exactly this.)*
- [ ] Background the app while a video is on screen and open the app switcher: the card is blank.
- [ ] Repeat both captures with a **photo** on screen, to confirm the behaviour is the same and nothing regressed.

**Deletion / trash**
- [ ] Delete a video from the pager → "Photo deleted" + Undo. Undo restores it in place with its poster and duration intact.
- [ ] Delete a video, go to Settings → Recently deleted: the video row shows its poster. "Delete now" removes it; the album's storage drops by the video's size (check via Settings → Storage for the app before/after, or just confirm the file count).
- [ ] Delete a whole album containing videos, then "Empty" the trash: no leftover files (app storage returns to roughly its pre-import size).
- [ ] Erase everything with videos present (live and trashed): storage returns to baseline.

**Mixed albums**
- [ ] An album of interleaved photos and videos keeps its import order; moving a video to another album works exactly like a photo.
- [ ] Gallery sort → "Photo count": an album's count includes its videos.
- [ ] Global search still finds the album by name; there is still no Photos/Videos section in search.

**Manifest / permissions (disguise)**
- [ ] `adb shell dumpsys package com.calcplus.calculator | grep -A20 "requested permissions"` lists **no** `ACCESS_NETWORK_STATE` and **no** `WAKE_LOCK` (both are `tools:node="remove"`d out of Media3's merged manifest). If a future Media3 upgrade makes playback throw a `SecurityException`, that removal is the first thing to re-check.
- [ ] The app's Play/App-info permission list is unchanged from before N3.


## Review polish (P3 / P4 / N1 / N3)

**Sort menu header (P4)** — the only change with no automated rendering coverage (Compose UI tests
are not on the unit-test classpath).
- [ ] Gallery → tap the sort icon: the menu opens with a **"Sort by"** header row and a divider
      above the four modes; the header is not tappable and closes nothing.
- [ ] The active mode still carries its trailing check mark, and picking a mode still closes the
      menu and re-sorts.
- [ ] Notes → same menu, same header, three modes.
- [ ] At a large font scale the header does not clip the first mode.

**Trash album count (P3)**
- [ ] Delete one photo of a 3-photo album, then delete the album. The album row in Recently deleted
      reads **"2 photos"** (not 3) and still shows a cover.
- [ ] Restore that album: exactly the two come back, and the earlier photo stays in Recently deleted
      as its own row under the (now live) album — no row promises more than it returns.

**Video import notice (N3)** — needs a provider that hands over a video without a `video/…` type
(a file-manager "Open with" / SAF pick of a `.mov` on some devices, or a share from an app that
declares `application/octet-stream`).
- [ ] Pick such a clip into an album: it is dropped, and the **"Some videos could not be imported."**
      snackbar appears (before this pass it vanished silently).
- [ ] Pick a genuinely corrupt image: **no** video notice.

**Debug APK / schemas (P3)**
- [ ] `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep assets` prints nothing — the
      exported Room schemas (and the vault's table names) are no longer packaged. They live on the
      unit-test classpath only.
