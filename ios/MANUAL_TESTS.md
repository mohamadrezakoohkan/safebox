# SafeBox iOS — Manual Test Script (M8)

Run on a physical device, dark and light themes. Every item is pass/fail.

## Disguise & lock

1. Fresh install → launches into a working calculator with the setup banner; keypad computes normally underneath.
2. Reproduce the idea-plan §2.1 shared input table by hand (all 17 rows, incl. `± ± + % =` → `0`, `+ + =` → `0`, `% =` → `0`).
3. Setup: commit `1 2 3` → "Too short"; type 33+ keys and commit → "Too long — start again"; confirm mismatch → back to entry; backgrounding mid-setup → returns to entry with buffers discarded.
4. Set a symbol passcode (e.g. `7 + 7 %`), confirm → one-time "Remember your code" notice → vault opens.
5. Relaunch → pure calculator, no banner. Wrong sequences (incl. <4 keys and >32 keys) just calculate — no delay, flicker, or feedback. Correct sequence + `=` unlocks in ≤300ms perceived.
6. Delete app → reinstall → lands in setup (install sentinel wiped Keychain), never locked behind a stale code.

## Re-lock & snapshot

7. From each tab: press home → reopen → calculator immediately (no grace period). Force-quit → relaunch → locked.
8. App switcher from every tab and from the inactive (peek/notification shade) state shows the calculator cover face — never vault content.
9. "Lock now" in Settings locks instantly; the calculator display and attempt buffer are pristine afterwards.
10. Start a photo import, spend <2 min in the picker → return, still unlocked, photos imported. Stay backgrounded >2 min mid-picker → app is locked on return, but the import completed (photos present after re-unlock). Changing the device wall clock while backgrounded does not defeat the cap.

## Gallery

11. Create/rename/delete albums (delete confirms with photo count); card grid shows first-photo cover, name, count; empty states render.
12. Import 20+ mixed HEIC/JPEG photos: **no permission dialog**, no lock during round-trip, originals byte-identical (spot-check checksums), real extensions preserved, thumbnails generated.
13. Grid scrolls smoothly on thumbnails; pager: double-tap 2.5×, pinch to 5×, pan clamped, page-swipe disabled while zoomed, zoom resets on page change.
14. Deleting photos/albums removes rows + full-size + thumbnail files (inspect the container); force-quit mid-import leaves no inconsistency after relaunch (orphan sweep).

## Notes

15. Derived title = first non-empty line markdown-stripped; snippet excludes the title line; relative dates; sorted by modified date.
16. Editor preview renders the shared subset (headings, bold/italic, inline code, lists, checklists as styled text); autosave lands within 1s and flushes synchronously on exit/backgrounding (kill mid-edit → at most the final sub-second lost).
17. Tags: add with autocomplete/dedupe, chips tinted, tag filter menu works; search matches title+body; swipe-delete confirms, no undo.

## Contacts

18. Full CRUD with labeled multi-value phones/emails, address; org-only contact allowed; familyName-first sections with `#` bucket; search matches name/org/phone/email.
19. Tapping phone/email does **nothing** (no dialer/mail handoff anywhere); long-press copies with "Copied" confirmation.

## Settings

20. Change passcode: wrong current → visible shake + "Incorrect code — try again" (unlimited retries); mismatch on confirm → back to enter-new; Cancel works in every phase; after change, old code fails immediately, new works.
21. No auto-lock setting anywhere; no biometric UI anywhere; About shows version + privacy + no-recovery statements.

## Onboarding & erase everything

25. Fresh install → animated 4-page guide before the calculator (flip card, staggered feature cards, interactive demo keypad, pulsing `=` + no-recovery warning). Skip and "Set my code" both land on the calculator in setup mode; the guide never reappears once finished — including after force-quit with no passcode set yet.
26. Force-quit **mid-guide** (before finishing) → guide shows again on relaunch. Once a passcode exists, the guide never shows under any circumstance.
27. Demo keypad on page 3: taps appear as chips with a spring pop, 4 pips fill green, caption flips to "That would work" at 4 keys; Reset clears. Nothing typed there is stored.
28. Settings → Erase everything: two-step destructive confirm (Continue → Last chance). Cancel at either step changes nothing. Confirming destroys all albums/photos/notes/contacts (rows AND files), removes the passcode, and returns to the guide. Setting a new passcode afterwards opens an empty vault; the old code no longer works.

## Non-functional

22. All data survives relaunch and reboot. No network requests (verify via proxy). Backup exclusion + `.completeUnlessOpen` on store and photo files (spot check with a container inspector).
23. VoiceOver: keys labeled as calculator keys only ("seven", "plus", "equals"); no vault-revealing identifiers; a screen-reader user can type and commit a code.
24. Home-screen name "Calculator+", neutral icon; nothing externally visible references SafeBox.
