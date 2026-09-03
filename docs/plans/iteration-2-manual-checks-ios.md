# SafeBox — Iteration 2 Manual Checks (iOS)

**Document:** `docs/plans/iteration-2-manual-checks-ios.md`
**Scope:** On-device verification for the iteration-2 items. Everything here is behavior that unit tests cannot assert — motion, layout, gestures, containment (app switcher, screenshots, Picture-in-Picture), VoiceOver and dark mode. **Partially executed** — see "Executed on device" below; everything not listed there is still outstanding. Companion: `iteration-2-manual-checks-android.md`; the same behavior must pass on both platforms. Authority for what the checks assert: `iteration-2-decisions.md`.

## Executed on device — 2026-09-03 (iPhone 17 Pro simulator, iOS 26.5)

All of the following were driven on the simulator and passed. Everything else in this document remains unverified.

- **F1** — correct code unlocks into the vault; wrong-state paths not exercised. The 260 ms reveal itself cannot be judged from stills, so the motion quality check stands.
- **F1 / lock** — Home → reopen relaunches into the calculator with a pristine display (`0`, `AC`); the vault and the sheet that was open were torn down. Auto-lock on backgrounding confirmed.
- **P5 containment (the window-level snapshot cover)** — with the revisit guide sheet OPEN, the app-switcher card shows the calculator cover, not the sheet. This is the fix for the review finding that SwiftUI sheets render above the in-view cover; confirmed working for a sheet.
- **P5** — Settings IA exactly as specified: Security (Change passcode, Lock now) / Data (Recently deleted above Erase everything) / About (Version, How it works + "Revisit the guide", Privacy). The four long About paragraphs are gone.
- **P5 revisit mode** — "How it works" opens the guide as a dismissible sheet with **Done** in place of Skip on page 1.
- **P2** — Gallery and Notes empty states render icon + title + one-line description + action button.
- **P4** — Notes sort menu offers exactly Date modified (checked by default) / Date created / Title; selecting Title reorders A–Z and the choice survives navigation.
- **P6** — long-press enters selection mode with the pressed row selected and does **not** open the editor; the search field is hidden while selecting; toolbar reads Cancel · "N selected" · trash; tapping toggles; one confirm dialog titled "Delete 2 notes?" with the "restore from Recently deleted for 30 days" body.
- **P3** — bulk delete shows the "2 notes deleted · Undo" toast above the tab bar; both notes appear in Recently deleted under a **Notes** section with "30 days left" and both a visible **Restore** and a red **Delete now** control; Restore returns the note to the list.
- **N1** — magnifier opens full-screen search; no-query state "Search your vault"; a query matching only a **trashed** note returns "No results" (soft delete respected); a case-insensitive body match ("Hotel" → "Flights and hotel") returns the note grouped under a **Notes** header; tapping the result dismisses search, selects the Notes tab and pushes the editor, and **Back lands on the Notes list**.

Not executed: N2 and N3 (need photos/videos imported through the picker), dark mode, Dynamic Type, VoiceOver, and every check that needs a real device (PiP, screen recording).


## Strings (foundation)
- No device checks required: string-catalog + `VaultCopy` only, no UI wired yet. Catalog presence is covered by `VaultCopyTests.keysArePresentInCompiledCatalog`.
- Deferred to the items that wire `VaultCopy` into screens: on device, walk the locked calculator (idle, mid-entry, wrong code, first-run setup) and confirm none of the new vault copy (e.g. "Search your vault", "Recently deleted") is visible anywhere before unlock.


## N2 — Photo metadata in the pager
- Open any photo in the pager. The trailing toolbar now reads ⓘ · folder (only when other albums exist) · trash. Tap ⓘ: a half-height sheet titled "Details" appears with rows Dimensions / File size / Type / Imported, in that order, with a drag indicator and a "Done" button.
- Compare the values with the same image's info in the Photos app: pixel dimensions and type must match exactly (JPEG / HEIC / PNG); file size is the imported file's real size in the platform's file style (decimal kB/MB).
- Swipe to a different photo and tap ⓘ again: every value changes to that photo's. Import a HEIC, a PNG and a JPEG into one album and confirm Type reads "HEIC", "PNG", "JPEG" respectively.
- Dismiss with "Done" and again by swiping down — both must work (no interactive-dismiss lock).
- Set the device language/region to German (or another comma-decimal locale): File size uses the locale's separator (e.g. "1,5 MB") and Imported is a localized medium date + short time.
- Dark mode and the largest accessibility text size: rows stay readable inside the medium detent and the list scrolls if the rows no longer fit.
- Disguise: with the Details sheet open, background the app. On return the calculator is showing and nothing of the sheet survives (it is torn down with `MainTabView`); the app switcher thumbnail shows the calculator cover, not the sheet.
- VoiceOver: the ⓘ button is announced as "Details".


## P2 — Empty-state parity
- Fresh vault (or after Erase everything): Gallery, Notes and Contacts each show icon circle + title + one-line description + filled button: "No albums yet / Albums keep your imported photos and videos organized. / Create album", "No notes yet / Notes support markdown with a live preview. / New note", "No contacts yet / Contacts live only in this vault. / Add contact". Open a new empty album: "No photos yet / Imports are copies — the originals stay in your library. / Import photos". Each button performs the same action as the toolbar `+` / compose button.
- Side by side with the Android build on the same content: the 88 pt circle, 40 pt glyph, 20 / 8 / 24 gaps, 32 pt side margins and the slightly-above-center placement (free space 1 : 1.35) read identically. The title is regular weight (not bold) on both.
- Notes: type a query with no match → "No results / Check the spelling or try a different search." with a magnifier icon and **no** button. Clear the query and pick a tag filter that has no notes → the same "No results" state. Contacts: same for a non-matching search.
- Both search "No results" states with the keyboard up: the block centers in the area above the keyboard; nothing is hidden under it.
- Dark mode: the circle is `secondarySystemFill`, glyph and description secondary grey, title primary. Light mode the same hierarchy.
- Accessibility text size AX5 (Settings → Accessibility → Display & Text Size → Larger Text, max): every state remains fully readable; where the block is taller than the screen (Notes/Contacts with the keyboard up) it scrolls instead of clipping; at default size there is no scroll bounce and the large "Gallery"/"Notes"/"Contacts" titles stay expanded.
- Import into an empty album: pick several photos. While the album is still empty the "Importing 1/N…" pill is centered in the empty grid area (no empty state shown). After the first photo lands the pill slides to the bottom (24 pt above the edge) and stays there until the import finishes.
- VoiceOver: on any empty state, swiping right reads the title then the description as one element, then the button (icon is skipped).
- Disguise: lock (background) from any empty state and return — the calculator shows; none of the empty-state copy is visible on the locked calculator or in the app-switcher snapshot.

- (N2 re-verification addendum) Side by side with Android on the same imported photo: Dimensions, Type and Imported must read identically; File size may differ only in unit casing ("kB" vs "KB") and separator. Once N3 lands, a clip of 59.6 s must read "1:00" in the Details sheet **and** the grid badge on both platforms (Android currently floors — see handoff).


## P2 — addendum (state-selector alignment with Android)
- Fresh vault, Notes tab with zero notes: pull down the search field and type any letters → the state switches to "No results / Check the spelling or try a different search." with **no** button (previously "No notes yet / New note"). Clear the query → "No notes yet / New note" returns. Repeat on Contacts with zero contacts. Both now match Android under the same input.
- Whitespace-only query (spaces) on an empty Notes / Contacts tab keeps "No notes yet" / "No contacts yet" — whitespace is not a search on either platform.
- The simulator used for tests already has a passcode set; the full visual walk above still needs a fresh install (or Erase everything) on a device/simulator where the code is known.


## F1 — Unlock transition
- Unlock: on the locked calculator type the real code and press `=`. Expect one motion only: the vault fades in while growing from 92 % to full size over ~260 ms with a fast start and a soft stop (emphasized decelerate); the calculator fades away underneath it in place — no slide, no bounce, no gap where neither surface is visible. The tab bar arrives as part of the vault (it scales with it). The first vault frame appears the moment verification completes; the 260 ms tail is not part of the ≤ 300 ms budget (design §5.4).
- Lock now (Settings → Lock now): the calculator appears instantly with "0" — no fade, no scale, no flash of a half-transparent vault.
- Background lock: from anywhere in the vault press Home. The app-switcher card shows the calculator cover (never vault pixels). Reopen: the calculator is already there showing "0", no animation. Repeat while the vault is mid-reveal (press `=` with the correct code and swipe up within the 260 ms): the card shows the cover and on return the app is locked — calculator, no leftover partial vault.
- Resign-active without lock: with the correct code committed, immediately pull down the notification shade during the reveal. The cover shows; dismiss the shade → the vault is fully revealed (no half-faded state) because the cover was outside the animated container.
- Epoch bump while locked: type a partial expression (e.g. `1 2 +`), press Home, reopen → the display reads "0" immediately, no crossfade between the old and new calculator.
- Wrong code: type any wrong sequence and `=` → the arithmetic result shows (e.g. `1234` → "1,234") and nothing else happens: no flicker, no partial reveal, no delay difference visible versus a correct code until the reveal starts. Sub-minimum (`1 =`) and overflow (> 32 keys) behave the same. (Verified on the simulator for `1 2 3 4 =`.)
- First-run setup (fresh install, or Settings → Erase everything → confirm → confirm): guide pages → "Set my code" → calculator appears with a cut. Enter a code, `=`, re-enter, `=` → the vault appears with exactly the same zoom reveal as a normal unlock (not a different animation, not a cut). The "Remember your code" alert appears only AFTER the reveal has finished (about a quarter second after the first vault frame), never on top of the moving vault. Tap "I understand" → it is gone; lock and unlock again → it does not return.
- Unacknowledged notice + lock: after first-run setup, background the app BEFORE tapping "I understand". On return the locked calculator shows and the alert is NOT visible over it. Unlock → after the reveal the alert appears once more; acknowledge it.
- Erase everything from the vault: the setup calculator / onboarding appears instantly (cut), no reveal in reverse.
- Reduce Motion (Settings → Accessibility → Motion → Reduce Motion ON, relaunch not required): unlock is an opacity-only crossfade over the same ~260 ms — the vault does not grow. Lock is still an instant cut. Turn Reduce Motion off again and confirm the scale returns without relaunching.
- Dark mode: run the unlock once in dark mode — the fading calculator and the incoming vault both use their own backgrounds; there must be no light/dark flash between them.
- Disguise: at no point during any of the above is vault vocabulary visible on the locked calculator; the app-switcher snapshot is always the calculator cover.


## P5 — Settings restructure + "How it works" revisit
- Settings tab: exactly three sections in this order — Security (Change passcode, Lock now), Data (Erase everything only, red, with its subtitle), About (Version with the inline value, "How it works / Revisit the guide", "Privacy / All data stays on this device." with a chevron). The four long paragraphs are gone; the screen fits without scrolling on a 6.1" device at default text size.
- Tap "How it works": the four-page guide slides up as a sheet on the calculator palette. The top-right button reads "Done" on page 1, 2, 3 AND 4 (first run hides Skip on page 4). Page through with Next; on page 4 the big button reads "Done" (not "Set my code"). Tap either Done → back on Settings, vault still unlocked, tab bar still there.
- Open the guide again and dismiss it by swiping down from page 2 → back on Settings, vault still unlocked (no interactive-dismiss lock, unlike Change passcode which must NOT be swipe-dismissible — check that too).
- Open the guide, background the app, return: the locked calculator shows ("0"); unlock → Settings with NO guide sheet on top. The app-switcher card while the guide is open shows the calculator cover, never the guide pages.
- First-run state untouched: after revisiting the guide (finish once, swipe-dismiss once), Lock now → unlock → no guide, no "Remember your code" alert. Then Settings → Erase everything → confirm twice → the first-run guide DOES appear again (post-erase reset still works) with Skip/Next/"Set my code" labels.
- Tap "Privacy": a pushed screen titled "Privacy" (inline title, back button "Settings") with two body-size paragraphs — the privacy statement ("All data stays on this device. This app has no servers…") and the no-recovery warning ("There is no way to recover this code…"). Back returns to Settings.
- Change passcode → success alert: its button now reads "OK" from the shared table (visually identical).
- Dark mode: both the Settings list and the Privacy page use system colours; the guide sheet uses the dark disguise palette and its Done button is legible on page 4 (caption grey on dark background).
- Largest accessibility text size: every Settings row wraps instead of truncating; the Privacy paragraphs wrap; in the guide sheet the top-right Done stays tappable and the bottom Done button is not clipped (the page content may scroll/compress as on first run).
- VoiceOver: "How it works, Revisit the guide, button"; "Privacy, All data stays on this device., button" (NavigationLink); in the guide the top-right element is announced "Done, button" on every page.
- Disguise: none of the new Settings copy ("Recently deleted" is not present yet, "How it works", "Revisit the guide", "Privacy") is visible anywhere on the locked calculator or during setup; the guide in revisit mode cannot be reached without unlocking.

### P5 review fix — snapshot cover above sheets (this REPLACES the earlier P5 bullet "Open the guide, background the app, return …" for the app-switcher part)
- With ANY sheet open — the "How it works" guide (any page), Change passcode (mid-flow), a contact create/edit sheet, a photo info sheet — swipe up to the app switcher: the app card shows the calculator cover ("0", AC keypad, disguise palette), never the sheet or the vault behind it. Repeat by pulling down the notification shade / Control Center over each sheet: the same cover appears the instant the app resigns active.
- Return to the app from the switcher with the guide sheet open: the locked calculator shows ("0"); unlock → Settings with NO guide sheet on top (lock still tears sheets down). Cancel out of the switcher without backgrounding (resign-active only, e.g. shade pulled and released): the cover disappears instantly, the sheet is still there and still interactive — no animation in either direction.
- Alerts: open Erase everything → first confirm alert → app switcher: the card shows the calculator cover, not the alert. Its Cancel button reads "Cancel" (shared `cancel_action`; visually identical to before).
- Dark and light mode: the cover window's background matches the live calculator (graphite in dark, light grey in light) with no flash of white/black at the edges or under the status bar.
- The cover never intercepts touches: after returning to the active app, every sheet and the vault behind it respond to the first tap.


## P3 — Deletion undo / Recently deleted
- **Migration (do this FIRST on a device that still has the iteration-1 build installed with real content):** install the new build over it and unlock. Every album, photo, note (with its tags), and contact is still there; nothing is in Settings → Recently deleted; the app does not crash or fall back to an empty vault (the empty in-memory fallback would show "No albums yet" with everything gone — that is a failed migration).
- Delete an album from the Gallery context menu: the dialog reads "Delete album and its N photos?" (N = its photos) with "You can restore it from Recently deleted for 30 days." and Delete / Cancel — no "This cannot be undone." Confirm → a toast "Album deleted · Undo" slides up above the tab bar (not covering it, not under it), auto-hides after ~5 s. Tap Undo before that → the album returns in its old position with all its photos.
- Photo grid: Select two photos → trash → "Delete 2 photos?" (select ONE → "Delete this photo?") + the restore body. Confirm → "2 photos deleted · Undo"; Undo → both photos return at their original positions. In the pager: trash → "Delete this photo?" → the pager moves to the next photo (or pops when it was the last), the toast shows over the pager; go back to the grid and tap Undo (if still visible) → the photo is back in place.
- Notes: swipe-delete → "Delete this note?" + body → "Note deleted · Undo". Open a note, type a few characters, immediately tap the trash → confirm: the editor pops, the toast shows, Undo restores the note and its tags; the restored note's body is the last SAVED text (the un-flushed draft was correctly discarded, not written into the trashed note).
- Contacts: swipe-delete and, separately, the "Delete contact" button inside a contact → both show "Delete this contact?" + body → "Contact deleted · Undo" on the list; Undo brings the contact back into its section.
- Toast semantics: delete two different things within 5 s → the second toast replaces the first; Undo restores only the second; the first item is in Recently deleted. Lock (background) while a toast is up → after unlock there is no toast, but the item is in Recently deleted.
- Settings → Data section: "Recently deleted / Items are kept for 30 days, then deleted permanently." sits ABOVE the red "Erase everything". Open it: inline title "Recently deleted", sections Albums / Photos / Notes / Contacts (only non-empty ones), each row shows a thumbnail or glyph, a name (album name / the photo's album name / note title / contact name), "N photos · 30 days left" for albums or "30 days left" otherwise, and a small "Restore" button. Photos that were deleted together with their album appear ONLY under the album row, not in Photos.
- Row actions: tap Restore → the row disappears and the item is back in its tab (photos in their original position; an album with the photos that were trashed with it, but NOT a photo you had trashed individually before deleting the album — that one stays in Photos). Swipe a row left → "Delete now" (no full swipe) → the row is gone for good with no extra confirm. Long-press a row → context menu with Restore and Delete now.
- Toolbar "Empty": disabled when the screen is empty; otherwise → "Delete everything in Recently deleted?" / "This permanently deletes every item here. This cannot be undone." / Delete / Cancel → everything is gone and the empty state shows: trash glyph in the 88 pt circle, "Nothing here", "Deleted items appear here for 30 days." (same rhythm as the other empty states, no button).
- Files: after deleting a photo, `Application Support/SafeBox/Photos` and `Thumbnails` still contain its files (check via the Files/Devices window or by restoring — the thumbnail and full image load instantly with no placeholder). After Delete now / Empty / Erase everything they are gone.
- Expiry (needs a clock jump or a debug build): trash a note, set the device date forward 31 days, lock and unlock → the note is gone from Recently deleted (purged on unlock); relaunching the app also purges (purge at start). A note trashed 29 days ago survives with "1 days left".
- Erase everything with items in Recently deleted (including a tagged note and an album with photos) → after the two confirms the app returns to first-run; set a code and unlock: every tab is empty, Recently deleted is empty, and the vault directory has no `Photos/` or `Thumbnails/` folders.
- Live lists never show trashed rows: album covers/counts, the notes tag filter, notes search and contacts search all ignore trashed items.
- Dark mode + AX5 text: the toast wraps to two lines and keeps the Undo button tappable; trash rows wrap; the Restore button stays visible.
- VoiceOver: the toast message then "Undo, button"; trash rows announce name, "N photos · 30 days left", "Restore, button"; the rotor's actions list offers "Delete now".
- Disguise: none of "Recently deleted", "Undo", "Restore", "Delete now" or the toast is visible on the locked calculator, during setup, or in the app-switcher card (the cover window still sits above the toast); locking with the trash screen open returns to the calculator and unlock lands on Settings' root.


## P3 follow-ups (review pass)
- **Correction to the P3 Notes bullet above** ("the restored note's body is the last SAVED text (the un-flushed draft was correctly discarded…)"): this is no longer the behavior. `NoteEditorViewModel.delete()` now flushes the pending draft BEFORE the soft delete, so: open a note, type a few characters, immediately tap the trash → confirm → Undo. The restored note must contain the characters you just typed (the exact on-screen text), and re-opening it shows them. The `onDisappear` flush after the dismiss still writes nothing (the editor is frozen once deleted).
- Recently deleted → an album row now shows TWO controls: "Restore" and a red trash button (VoiceOver: "Delete now"). Tapping the trash button removes the row immediately with no confirm, exactly like the swipe action and the context-menu entry; all three paths behave the same.
- Recently deleted → album row count: trash a photo individually, THEN delete its album (which still holds 2 other photos). The album row reads "2 photos · 30 days left" (not 3): only the photos Restore brings back are counted. Restore the album → 2 photos return; the earlier photo stays under Photos. Delete now on the album still purges all 3 (their files included).
- Delete now / Empty while the trash list is visible must never crash or show a blank row during the purge (the row leaves the list before the files are deleted).
- Purge order (not directly visible): rows are deleted and saved before files are removed. A forced kill mid-purge can leave orphan files (picked up by the next launch's orphan sweep), never a trash row whose files are missing.


## P6 — Multi-select in Notes and Contacts
- Notes, entry: with at least 5 notes, long-press a row → the row gains a filled blue checkmark on the left, the title bar collapses to a small "1 selected", the left button reads "Cancel" and the right one is a red trash glyph. The long-press must NOT also open the editor.
- Notes, toggling: tap three more rows → "4 selected"; tap one of them again → "3 selected" and its circle goes back to empty. Tapping a row never opens the editor while selecting. Deselect every row → "0 selected", the trash button is greyed out (disabled) and the screen stays in selection mode.
- Notes, Cancel: tap Cancel → the checkmarks disappear, the large "Notes" title and the compose/filter buttons come back, and re-entering selection mode starts at "1 selected" (nothing was remembered).
- Notes, bulk delete: select 3 notes → trash → ONE dialog "Delete 3 notes?" / "You can restore it from Recently deleted for 30 days." / Delete / Cancel. Cancel → still selecting, still 3 selected. Delete → all three disappear at once, selection mode exits, and a single toast "3 notes deleted · Undo" appears above the tab bar. Tap Undo → all three come back in their original order.
- Notes, count copy: select exactly 1 → trash → the dialog reads "Delete this note?" (singular), and the toast after deleting reads "Note deleted".
- Notes, swipe while selecting: enter selection mode and swipe a row left → NO Delete action appears. Cancel, then swipe the same row → Delete appears again and works as before.
- Notes, selection vs. search: select two notes, then type a query that hides them both → the title still reads "2 selected"; tap the trash → the dialog says "Delete 2 notes?" and confirming deletes exactly those two (clear the search to verify). Same with a tag filter.
- Notes, delete elsewhere: select two notes, Cancel, open one of them and delete it with the editor's trash, go back, re-select the remaining ones — the count never counts the deleted note.
- Contacts, entry and toggling: long-press a contact row → "1 selected", Cancel + red trash, and the chevron disappears from every row while selecting. Tap rows to toggle; tapping never pushes the contact detail while selecting.
- Contacts, section headers: the sticky letter headers (A, B, … , #) are NOT tappable and never gain a checkmark — long-pressing a header does nothing.
- Contacts, bulk delete: select 2 contacts spanning two different letter sections → trash → "Delete 2 contacts?" + the restore body → Delete → both vanish, an empty section header disappears with its last row, one toast "2 contacts deleted · Undo"; Undo puts both back into their correct sections.
- Contacts, single and disabled states: select exactly 1 → "Delete this contact?" and the toast "Contact deleted". Deselect everything → the trash button is disabled.
- Contacts, swipe while selecting: no swipe-to-delete while selecting; it returns after Cancel.
- Recently deleted after a bulk delete: Settings → Recently deleted lists all N notes / contacts (individually), each with "30 days left". Restoring them one by one also works (Undo is not the only path).
- Lock resets selection: enter selection mode with 3 notes selected, background the app (or Settings → Lock now) → the calculator shows; unlock → the Notes tab is at its root, NOT in selection mode, nothing selected. Same for Contacts.
- Toast + selection: bulk-delete 3 notes, then immediately bulk-delete 2 contacts within 5 s → the second toast replaces the first; Undo restores only the contacts; the notes are in Recently deleted.
- Dark mode: the selection checkmarks (accent blue / secondary grey) are legible on both list backgrounds; the inline "N selected" title and the red trash glyph have enough contrast.
- Largest accessibility text size: "12 selected" is not truncated in the inline title; the checkmark column does not squeeze the note title/snippet into an unreadable width; the confirm dialog's title wraps.
- VoiceOver: a browsing row announces its content and "button", and the rotor's actions list offers "Select" — using it enters selection mode without a long press. While selecting, a selected row announces "selected"; the toolbar reads "Cancel, button" and "Delete, button" (dimmed/unavailable at 0 selected).
- Disguise: none of "N selected", the confirm dialogs or the toasts is reachable from the locked calculator or during first-run setup; the app-switcher card over a selection-mode list shows only the calculator cover.


## P4 — Album sort and note sort
- Gallery toolbar: the top-right group reads sort (`arrow.up.arrow.down`) then `+`. Tap the sort glyph → a menu titled "Sort by" with Manual / Name / Date created / Photo count, a check mark on Manual on a fresh install.
- Gallery, each mode: with ≥4 albums named out of alphabetical order and different photo counts, pick each mode in turn and confirm the grid reorders immediately (no pull-to-refresh, no flicker of the old order): Manual = creation order, Name = A–Z ignoring case and accents ("apple" before "Banana" before "Éclair"), Date created = newest first, Photo count = most photos first with equal counts falling back to A–Z.
- Gallery, photo_count counts LIVE photos: pick "Photo count", note the top album, delete some of its photos (they go to Recently deleted) → the album drops in the order right away; restore them from Recently deleted (or Undo) → it climbs back. A trashed album never appears in any mode.
- Notes toolbar: the browsing group reads sort · [tag filter, only when tags exist] · compose. The sort menu is "Sort by" with Date modified / Date created / Title, checked on Date modified by default.
- Notes, each mode: with ≥4 notes, edit the oldest one → under Date modified it jumps to the top, under Date created it stays where it was. Title sorts A–Z case/accent-insensitively and pushes every note whose first line is empty ("Untitled" rows) to the BOTTOM.
- Notes, sort + search/tag filter together: choose Title, type a query → the surviving rows stay in title order (the filter narrows, it never reshuffles). Same with a tag filter.
- Selection mode has NO sort control: long-press a note (or a contact) → the toolbar shows exactly Cancel + the red trash and no sort glyph; Cancel brings the sort menu back.
- Survives lock: pick "Photo count" for albums and "Title" for notes, then Settings → Lock now (or background the app), unlock → both lists come back in the chosen order with the same check marks.
- Survives relaunch: with those modes set, force-quit from the app switcher and reopen → after unlocking, both are still in the chosen order.
- Erase everything resets sort: with non-default modes set, Settings → Erase everything (both steps) → set a new passcode → the Gallery is back to Manual and Notes back to Date modified.
- Dark mode + large text: the sort menu labels and the check mark are legible; the toolbar glyph does not collide with `+` / the compose button at the largest accessibility text size.
- VoiceOver: the sort control announces "Sort by, button"; opening it reads each mode and announces the active one as selected.
- Disguise: the sort menu is reachable only from the unlocked vault; nothing about it appears on the locked calculator, in first-run setup, or in the app-switcher card.


## N1 — Global search
- Entry points: the top bar of Gallery (album list), Notes and Contacts each show a magnifier as the FIRST trailing button — Gallery reads `search · sort · +`, Notes `search · sort · [filter] · compose`, Contacts `search · +`. Settings has NO magnifier. Tapping any of the three opens the same full-screen "Search" screen.
- No fifth tab: the tab bar still shows exactly Gallery / Notes / Contacts / Settings, and the labels come from the shared strings (they change with the device language).
- Empty-query state: the search screen opens with the field focusable at the top, the title "Search", the placeholder "Notes, contacts, albums", and the "Search your vault" empty state (magnifier circle + body) — NOT a list of everything.
- Whitespace is not a query: type three spaces → the screen stays on "Search your vault", never "No results" and never a full list.
- No-results state: type a string nothing contains (e.g. "qqqzz") → "No results / Check the spelling or try a different search.", with no action button.
- Grouped results: seed an album, a note and a contact that all contain the same word, type it → three sections in the order Albums, Notes, Contacts, with the headers "Albums", "Notes", "Contacts". Album rows show a cover thumbnail (or the photo glyph) + name + "N photos"; note rows show title + snippet; contact rows show the name (+ organization when it differs).
- Debounce: type a long query fast → the list updates once, ~0.3 s after you stop; it must not flicker through intermediate result sets per character. Deleting the whole query snaps back to the "Search your vault" state instantly (no 0.3 s lag).
- Diacritics both ways: with a contact "Zoë" and a note containing "Zoe", typing `zoe` finds both, and typing `Zoë` also finds both. Same with an album named "Café" and the query `cafe`.
- Scope: a note matches on its title, on text deep in its body, AND on a tag name (tag a note "Voyages", search "voyage" → the note appears). A contact matches on given/family name, organization, a phone number (digits only, e.g. "900123") and an email. An album matches only on its name.
- Photos never appear: import photos into an album, search the album's name → only the ALBUM row appears, never individual photos; there is no Photos section.
- Cross-tab jump, album: from the Notes tab, search an album name and tap it → search closes, the tab bar switches to Gallery, and the album's photo grid is on screen. Press Back once → you land on the album LIST (not on Notes), and the tab bar still shows Gallery.
- Cross-tab jump, note: from the Gallery tab, search a note and tap it → Notes tab, the note editor open with the right note. Back once → the notes list.
- Cross-tab jump, contact: from the Gallery tab, search a contact and tap it → Contacts tab, that contact's detail. Back once → the contacts list (in the right letter section).
- Back stack is reset, not stacked: on the Notes tab open note A, go back, open note B, go back — then from Gallery search note C and tap it. Back once must land on the notes list; there must be no second Back that walks through A or B.
- Deep push is replaced: open an album's photo grid in Gallery, switch to Contacts, search a DIFFERENT album and tap it → Gallery shows the new album's grid, and Back goes to the album list (you never end up under the old album).
- Pager interaction: open an album, open a photo in the pager, switch to the Notes tab, search an album and tap it → the pager is gone and the chosen album's grid is on screen; Back → the album list.
- Trashed items are unreachable: delete a note, a contact and an album (they go to Recently deleted), then search a word that only they contained → "No results". Restore one from Settings → Recently deleted (or Undo) → the same query now finds it again.
- Per-tab searches agree with global search: in the Notes tab's own search field, typing `cafe` finds a note about a "Café" (this was case-only before N1); in the Contacts tab, `cafe eclair` finds a contact whose organization is "Café Éclair".
- Undo toast still works and is unaffected: swipe-delete a note → the toast appears above the tab bar; while it is up, open global search → the toast is hidden behind the cover; close search → the toast is gone (its 5 s window elapsed) and the note is still in Recently deleted. Deleting from a search result is not possible (rows have no swipe actions).
- Selection mode is untouched: long-press a note → the toolbar is exactly Cancel + red trash with "N selected"; there is NO magnifier and NO sort control while selecting. Cancel → the magnifier comes back first in the group.
- Sort menus still work after the search refactor: change the album sort and the note sort, and confirm both lists reorder and survive a lock (P4 checks unchanged).
- Cancel/dismiss: the search screen's top-right "Cancel" closes it and returns you to the tab you came from, on the same screen, with that tab's own search field and scroll position intact.
- Lock while searching: open global search, then background the app (or Settings → Lock now from another tab first) → the app switcher shows only the calculator cover, and unlocking lands on the Gallery tab at its root with search closed and the query gone.
- Dark mode: section headers, the row thumbnails/glyphs and the two empty states are legible; the search field contrasts against the navigation bar.
- Largest accessibility text size: the row titles wrap rather than truncating to nothing, the "N photos" line stays readable, and the two empty states scroll instead of clipping.
- VoiceOver: the toolbar magnifier announces "Search, button"; on the search screen each result row is one element announcing its title (and subtitle) plus "button"; section headers are announced as headings.
- Disguise: no part of search is reachable from the locked calculator or during first-run setup; nothing about notes/contacts/albums appears in the app-switcher card while search is open.


## N3 — Video support

Device/simulator checks. The unit suite covers import, metadata, ordering, purge and the teardown decision; everything below is what only a real screen (or a real app switcher) can prove.

**Import**
- Picker accepts both: album → `+` → the picker shows photos AND videos (previously images only). Pick one photo and one video in a single selection → both land, the progress pill counts `1/2`, `2/2` (item-count based — a long video does not stall the counter at a fraction).
- No re-encode: import a video whose size you know, then Info (ⓘ) in the pager → the File size row matches the original within a byte, and Type reads MOV/MP4 as appropriate.
- Portrait video: import a clip recorded holding the phone upright → the Details sheet's Dimensions row is portrait (e.g. `1080 × 1920`, not `1920 × 1080`) and the grid poster is upright, not sideways.
- Long video: import something over ~500 MB. Memory must stay flat during the import (Debug navigator / Instruments) — videos are streamed as files, never loaded into memory. The app must not be jetsammed.
- Failed import: hard to force deliberately; if you can (an iCloud-only clip with networking off, or a corrupt file via Files → Photos), the grid shows the toast "Some videos could not be imported." with **no Undo button**, and no broken cell appears.
- Staging is empty afterwards: with a debug build, check `<app container>/Library/Application Support/SafeBox/Staging/` after an import — it must be absent or empty. It must NOT be under `tmp/`.
- Kill mid-import (background the app during a large import, then force-quit): relaunch, unlock → no half-imported cell in the grid, and `Staging/` is empty again (cleared at launch).

**Grid**
- Affordance: a video cell shows a white play glyph centered and a duration pill (`0:07`, `1:23`, `1:02:05`) at the **bottom-left**. A photo cell shows neither.
- No collision with selection: tap Select, then tap the video → the checkmark appears **bottom-right** while the duration pill stays bottom-left; both readable at once, in light and dark.
- Mixed album: import photo, video, photo, video → the grid shows them in import order with the badges on the right cells; no regrouping by type.
- Album list / search / trash: the album cover, a global-search album row and a Recently-deleted row all show the video's poster frame (no play glyph there — deliberate).

**Playback**
- Tap a video cell → the pager opens with standard playback controls on a black background. Photos in the same album still zoom/pan as before, and swiping between a photo page and a video page works both ways.
- Autoplay must NOT happen: opening the pager on a video shows the first frames paused. Playback starts only when you press play.
- Page change stops it: start playing, swipe to the next page → the video stops immediately (no audio bleeding from the previous page). Swipe back → it is paused at the start, not still playing.
- Back stops it: start playing, press Back to the grid → audio stops instantly.
- **PiP is impossible:** while a video is playing, (a) there must be no PiP button in the player controls, and (b) swipe up to the home screen — the video must NOT continue in a floating window. Repeat with iOS Settings → General → Picture in Picture → "Start PiP Automatically" **on**; it must still not happen.
- **AirPlay:** with an Apple TV / AirPlay receiver on the network, the player must not offer to send the video to it (external playback is disabled).
- **App switcher while playing (the critical one):** start playback, then double-press home / swipe up and hold → the card must show the **calculator**, never a video frame, and the audio must already have stopped. Repeat with the Details sheet open over the playing video (the P5 window-level `SnapshotCover` sits above sheets) — still the calculator.
- Lock while playing: start playback, background the app, return → the vault is locked and shows the calculator; unlocking lands on the Gallery root with no player anywhere.
- Control Center / lock screen: while a video is playing, pull down Control Center and check the Now Playing tile — it must NOT show anything from this app (no title, no artwork, no scrubber). Same on the device lock screen.
- Silent switch / volume: audio follows the normal media behaviour of the app's default session; confirm nothing keeps playing after the app leaves the foreground.

**Metadata and deletion**
- Details sheet on a video: rows read Dimensions, File size, Type, **Duration**, Imported — in that order. Duration matches the player's own total (`m:ss`, or `h:mm:ss` past an hour). On a photo the Duration row is absent.
- Delete a video from the pager or the grid selection → confirm dialog, undo toast "Photo deleted", and the cell disappears. Undo restores it in place with its poster and badge intact.
- Recently deleted: a trashed video appears under Photos with its poster; Restore brings it back playable; "Delete now" removes it. After a purge (or Empty), the file and the poster are both gone — check `Photos/` and `Thumbnails/` in the container.
- Erase everything with a video present: `Photos/`, `Thumbnails/` and `Staging/` are all gone afterwards.

**Accessibility / appearance**
- Dark mode: the duration pill and play glyph stay legible over a bright poster frame and a dark one.
- Largest text size: the duration pill grows without pushing the selection checkmark off the cell.
- VoiceOver: the grid cell announces as a single button (the overlay is `accessibilityHidden`); in the pager the player exposes the system's own transport controls.

**Disguise**
- Nothing video-related is reachable from the locked calculator or during first-run setup.
- With a video playing, the recents card, any screenshot taken by iOS's own screenshot gesture during a resign-active frame, and the multitasking preview all show the calculator cover.


## Review polish (P6/P4/N1/N3) — device checks

**Live Photos / motion stills (the classification not changed in code).** `PhotoImporter.isVideo(item)` forks on `supportedContentTypes.contains { $0.conforms(to: .movie) }`. A Live Photo advertises both an image and a movie type, so on a real device with iCloud Photos it may import down the VIDEO path (stored as `.mov`, poster frame, play glyph + duration badge) rather than as a still. On a device with real Live Photos and slow-motion / time-lapse clips in the library:
- Import one Live Photo, one slow-mo, one time-lapse and one ordinary still in a single pick. Record for each whether the grid cell shows the play glyph + duration badge and what the Details sheet's Type row reads.
- Whatever it does, it must be CONSISTENT with what plays: a cell with a play glyph must open a working player, and a cell without one must open the still viewer. A "video" that cannot play, or a still that shows a badge, is the failure mode to look for.
- Deletion, restore and Erase everything must behave identically for whichever path it took (both files gone after purge).
- If the classification turns out to be wrong for Live Photos, the fix belongs in `PhotoImporter.isVideo` (prefer the still representation when an item conforms to BOTH `.image` and `.movie`), and Android's `video/*` filter must be checked in the same breath.

**Selection mode has no search field (P6 polish).** Notes and Contacts: type a query, long-press a row → the search field disappears with the toolbar switch (title "N selected", Cancel, Delete only), and the list stays filtered by what was typed. Cancel → the field is back WITH the query still in it and the same rows visible. Repeat with the keyboard up when the long-press happens (the field must dismiss cleanly, no floating keyboard).

**No first-keystroke flash in global search (N1 polish).** Open search, type one character slowly: "No results / Check the spelling" must NOT blink before the list appears. Type a query that matches, then keep typing until it stops matching — the previous results stay on screen until the new (empty) result arrives, then "No results" appears once and stays.

**A route whose row was purged pops (N1 polish).** Open a note (or contact) from global search so the tab's stack is [detail]; background/lock is not needed. From another device path — Settings → Recently deleted → "Delete now" — is not reachable while the detail is up, so instead: delete the note from its own editor (trash button) and confirm the stack pops back to the list with the undo toast; then Undo and confirm the list shows it again (the editor does NOT re-push). No blank pushed screen at any point.

**Video staging under the owning store (N3 polish).** Import a video, then check the container: bytes appear under `<Application Support>/SafeBox/Staging/` only transiently and end up in `Photos/`. Nothing is written to `Staging/` while running a Preview.

**Album count copy (P4/N1 polish).** The album card, the global-search album row and a Recently-deleted album row all read "N photos" identically for the same album (same string ID now).
