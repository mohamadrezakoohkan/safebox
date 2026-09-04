# SafeBox — Iteration 3 Manual Checks (iOS)

**Document:** `docs/plans/iteration-3-manual-checks-ios.md`
**Scope:** On-device verification for iteration 3 (lock faces). Everything here is behavior unit tests cannot assert — gestures, motion, layout, containment, VoiceOver, dark mode, and the upgrade path. Companion: `iteration-3-manual-checks-android.md`; the same behavior must pass on both platforms. Authority for what the checks assert: `iteration-3-decisions.md`.
**Status:** Not yet executed.

---

## U — Upgrade path (do this FIRST, before any fresh install)

The riskiest change in this iteration is the passcode envelope. Verify it on a real upgrade, not a clean build.

- Install the last iteration-2 build, set a calculator code, add at least one photo, one note and one contact, then background the app.
- Install the iteration-3 build **over it** (no uninstall, no data wipe).
- The lock screen is the calculator, unchanged. The old code unlocks **on the first try** — no setup screen, no re-enrollment, no guide.
- All vault content is intact.
- Background and reopen: still the calculator, still the same code.
- Settings → Change disguise → the current face reads **Calculator**.

## S — Setup on a chosen face (fresh install, once per face)

Run this whole section three times: once picking Calculator, once PIN pad, once Pattern.

- Fresh install. The guide opens on **Pick a disguise** with a horizontally scrollable carousel; the calculator card is centered and selected.
- Swipe the carousel: cards snap, neighbours peek at both edges, and the centered card becomes the selection with no tap needed. Tapping a peeking card scrolls it to centre and selects it.
- The identity disclosure sits below the carousel and is legible in full: the app stays "Calculator+" on the home screen, only the locked screen changes. The PIN pad and pattern cards say they do **not** match the app's name and icon; the calculator card says it does. The pattern card carries the screen-reader note.
- Advance: page 2 (vault) is unchanged. **Page 3 and page 4 show the selected face's copy and illustration.** Go back to page 1, pick a different face, come forward again — pages 3 and 4 have changed to match, with the playground reset.
- Page 3's playground is interactive and purely practice: tapping keys, digits, or drawing a pattern fills the pips and flips the caption to the "that would work" line at 4 inputs. Reset clears it.
- Page 4 shows the face's own commit gesture ("press =", "tap ✓", "lift your finger") and the no-recovery warning card.
- Finish the guide. Setup runs **on the chosen face**, with that face's own prompt wording. Set a code, confirm it, and land in the vault with the no-recovery alert appearing only after the reveal has finished.
- Lock (Settings → Lock now). The lock screen is the chosen face. The code unlocks it.

**Skip path:** fresh install, scroll the carousel to the PIN pad, tap Skip on page 1. Setup runs on the PIN pad — Skip must carry the centered selection, not silently revert to the calculator.

**Interrupted setup:** fresh install, finish the guide picking Pattern, then kill the app from the app switcher **before** setting a code. Relaunch: the **guide appears again** with the carousel offered, not a bare setup screen. This is the completion-flag timing change; if it lands on a lock face instead, the flag was written too early.

## L — Lock-screen behavior per face

### Calculator (covert — unchanged from iteration 2)
- Wrong code: **absolutely nothing happens.** No shake, no flash, no clear, no caption. The display keeps showing the arithmetic result. Verify a wrong code that is 2 keys, 5 keys, and 40 keys — all equally silent.
- Arithmetic still works end to end; the operator ring still appears; `AC`/`C` still toggles correctly.

### PIN pad and Pattern (overt)
- Wrong code: the entry shakes horizontally and then clears, with **no error text** on the lock screen. Same for a too-short entry (fewer than 4) and for a 40-digit entry.
- Correct code unlocks with the usual 260 ms reveal.
- PIN pad: one dot appears per digit; the row shrinks rather than overflowing as digits pile up; ⌫ removes one dot; **long-press ⌫ clears the whole entry**; ✓ commits. Confirm ✓ on an empty entry is harmless.
- Pattern: dragging draws a line that follows your finger; a node lights as you enter it; **dragging straight across a skipped node selects it too** (try top-left to top-right, and top-left to bottom-right); re-entering an already-lit node does nothing; lifting commits. Lifting after touching no node at all does nothing. Starting a new stroke clears the previous path.
- Neither face shows any word from the vault vocabulary. The only text while locked is the static face title ("Enter PIN" / "Draw your pattern").

### Every face
- Type a partial code, background the app, return: the face is **pristine** — no dots, no path, no half-typed display.
- Lock and unlock a few times: the face is pristine each time, and the recreation never reads as an animated transition.

## C — Containment (the disguise must not leak)

- With each face active, open the app switcher: the card shows that face's resting state, never the vault.
- Open the revisit guide sheet from Settings, then background: the switcher card still shows the **lock face**, not the sheet. Repeat with the Change disguise sheet open mid-flow.
- Take a screenshot from inside the vault, then check it in Photos — this is the documented iOS behavior, note what it shows rather than treating it as a failure.
- Nothing anywhere in the locked experience says vault, passcode, unlock or SafeBox.

## D — Change disguise (Settings → Security)

- The row sits directly under **Change passcode** and its subtitle names the current face.
- Tap it: the flow opens on the **current** face asking for the current code. A wrong entry gives visible feedback and unlimited retries. Cancel is available here and at every later step.
- Enter the correct code: the picker appears with the explainer naming the **current** face and its commit gesture, the identity disclosure, and the carousel centered on the current face with a "Current" badge.
- **The action button is disabled while the current face is centered.** Scroll to another face: it enables.
- Continue: the **new** face appears for the new code, then confirms it. Enter mismatching codes on purpose — you go back to entry with a mismatch caption, still on the new face.
- Complete it: a success alert restates that there is no recovery. Settings' subtitle now names the new face.
- **The old code no longer works.** Lock the app and confirm the new face appears and only the new code unlocks it.
- Repeat the flow but cancel at each phase in turn (verify, pick, new code, confirm). Each time: lock the app and confirm the **old** face and **old** code still work.
- Background the app mid-flow at each phase: the flow is gone on return, the vault is locked, and the old code and face are intact.

## P — Change passcode still works

- Settings → Change passcode with a non-calculator face active: the whole flow renders on the **active** face, not the calculator.
- After changing, the face is unchanged and the new code works.

## R — Revisit guide (Settings → How it works)

- Page 1 shows the carousel **locked on the current face**: it does not scroll, the current card is badged, and the hint points to Settings → Change disguise.
- Pages 3 and 4 show the current face's guide content.
- Done dismisses; nothing is written; the vault stays unlocked. Reopening shows the same state.

## E — Erase everything

- With a non-calculator face active, erase everything. The app returns to the guide with the carousel offered and the **calculator** centered again as the default.
- The old code does not work on the new setup.

## A — Accessibility and appearance

- VoiceOver on each face: calculator keys read as calculator keys; PIN pad reads "one"…"nine", "zero", "delete", "enter", and the dot row reads as entered digits with a count. No element hints at unlocking, a vault, or a passcode.
- The **pattern grid cannot be operated by VoiceOver** — confirm this is true and that the pattern card discloses it. A screen-reader user must be able to reach the calculator or PIN pad instead.
- Reduce Motion on: the shake is replaced by a still hold of the same length, and the entry still clears. The unlock reveal degrades to the opacity-only crossfade as in iteration 2.
- Dark and light mode on all three faces and the carousel; largest Dynamic Type size — captions wrap to two lines without clipping and the keypads stay usable.
- Small device (iPhone SE) and large (Pro Max): the PIN pad column stays centered and within its max width; the pattern grid stays square and fully on screen.
