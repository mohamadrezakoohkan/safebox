# SafeBox — Iteration 3 Manual Checks (Android)

**Document:** `docs/plans/iteration-3-manual-checks-android.md`
**Scope:** On-device verification for iteration 3 (lock faces). Everything here is behavior unit tests cannot assert — gestures, motion, layout, containment, TalkBack, dark mode, and the upgrade path. Companion: `iteration-3-manual-checks-ios.md`; the same behavior must pass on both platforms. Authority for what the checks assert: `iteration-3-decisions.md`.
**Status:** Not yet executed.

---

## U — Upgrade path (do this FIRST, before any fresh install)

The riskiest change in this iteration is the passcode envelope and its plain mirror key. Verify on a real upgrade, not a clean install.

- Install the last iteration-2 build, set a calculator code, add at least one photo, one note and one contact, then background the app.
- Install the iteration-3 build **over it** (`adb install -r`, no uninstall, no `pm clear`).
- The lock screen is the calculator, unchanged. The old code unlocks **on the first try** — no setup screen, no re-enrollment, no guide.
- All vault content is intact, and the Room database is untouched by this iteration.
- Force-stop and relaunch: still the calculator, still the same code. This is the check that proves the mirror key was written during the migration rewrite rather than only held in memory.
- Settings → Change disguise → the current face reads **Calculator**.

## S — Setup on a chosen face (fresh install, once per face)

Run this whole section three times: once picking Calculator, once PIN pad, once Pattern.

- Fresh install. The guide opens on **Pick a disguise** with a horizontally scrollable carousel; the calculator card is centered and selected.
- **Nested scrolling:** swiping horizontally on the carousel moves the carousel only — it must never flip to guide page 2, even when you fling hard past the last card. Swiping on the page margins, title, or body still turns the page, and the Next button always does.
- Cards snap, neighbours peek, and the centered card is the selection with no tap needed. Tapping a peeking card scrolls it to centre and selects it.
- The identity disclosure sits below the carousel and is legible in full. The PIN pad and pattern cards say they do **not** match the app's name and icon; the calculator card says it does. The pattern card carries the screen-reader note.
- Advance: page 2 (vault) is unchanged. **Pages 3 and 4 show the selected face's copy and illustration.** Go back, pick a different face, come forward — pages 3 and 4 changed to match, with the playground reset.
- Page 3's playground is interactive practice: 4 inputs fill the pips and flip the caption to the "that would work" line. Reset clears it.
- Page 4 shows the face's own commit gesture ("press =", "tap ✓", "lift your finger") and the no-recovery warning card.
- Finish the guide. Setup runs **on the chosen face** with that face's prompt wording. Set a code, confirm, land in the vault with the no-recovery dialog appearing only after the reveal completes.
- Lock (Settings → Lock now). The lock screen is the chosen face and the code unlocks it.

**Skip path:** fresh install, scroll to the PIN pad, tap Skip on page 1. Setup runs on the PIN pad — Skip must carry the centered selection.

**Interrupted setup:** fresh install, finish the guide picking Pattern, then swipe the app away from Recents **before** setting a code. Relaunch: the **guide appears again** with the carousel offered. If it lands on a bare lock face, the completion flag was persisted too early.

**Process death:** with a face set and the vault unlocked, use `adb shell am kill com.calcplus.calculator`, then reopen from Recents. The app cold-starts **locked**, on the correct face — this exercises the launch read of the mirror key.

## L — Lock-screen behavior per face

### Calculator (covert — unchanged from iteration 2)
- Wrong code: **absolutely nothing happens.** No shake, no flash, no clear, no caption; the display keeps the arithmetic result. Try a 2-key, a 5-key and a 40-key wrong code — all equally silent.
- Arithmetic still works end to end; the operator ring still appears; `AC`/`C` still toggles.

### PIN pad and Pattern (overt)
- Wrong code: the entry shakes horizontally then clears, with **no error text** while locked. Same for a too-short entry and for a 40-digit one.
- Correct code unlocks with the usual 260 ms reveal.
- PIN pad: one dot per digit; the row shrinks rather than overflowing; ⌫ removes one dot; **long-press ⌫ clears everything**; ✓ commits; ✓ on an empty entry is harmless.
- Pattern: the line follows your finger; nodes light as entered; **dragging straight across a skipped node selects it too** (top-left to top-right, and top-left to bottom-right); re-entering a lit node does nothing; lifting commits; lifting having touched no node does nothing; a new stroke clears the old path.
- Interrupt a stroke with a system gesture (pull the notification shade down mid-drag): the path resets and nothing is committed.
- Neither face shows vault vocabulary. The only locked text is the static face title ("Enter PIN" / "Draw your pattern").

### Every face
- Type a partial code, background, return: the face is **pristine** — no dots, no path, no half-typed display. This is the clear that `FLAG_SECURE` alone does not provide.
- Lock and unlock repeatedly: pristine each time, and the recreation never reads as an animated transition.

## C — Containment (the disguise must not leak)

- With each face active, open Recents: `FLAG_SECURE` blanks the thumbnail. Confirm it is still unconditional and that no face changed it.
- Attempt a screenshot from inside the vault: blocked, as before.
- Attempt a screenshot on each new lock face: also blocked (the flag is activity-level, so this should hold — confirm rather than assume).
- Nothing in the locked experience says vault, passcode, unlock or SafeBox.

## D — Change disguise (Settings → Security)

- The row sits directly under **Change passcode** and its subtitle names the current face.
- Tap it: the flow opens on the **current** face asking for the current code, with visible wrong-code feedback and unlimited retries. Cancel is available at every step, and the system Back gesture behaves the same as Cancel.
- Correct code: the picker appears with the explainer naming the **current** face and its commit gesture, the disclosure, and the carousel centered on the current face with a "Current" badge.
- **The action button is disabled while the current face is centered**; scrolling to another face enables it.
- Continue: the **new** face takes the new code, then the confirmation. Enter mismatching codes on purpose — back to entry with a mismatch caption, still on the new face.
- Complete: a success message restates that there is no recovery, and the Settings subtitle names the new face.
- **The old code no longer works.** Lock and confirm only the new face and new code work.
- Repeat, cancelling at each phase in turn: the **old** face and **old** code survive every time.
- Background mid-flow at each phase: the flow is gone on return, the vault is locked, and the old code and face are intact.

## P — Change passcode still works

- Settings → Change passcode with a non-calculator face active: the whole flow renders on the **active** face.
- After changing, the face is unchanged and the new code works. Force-stop and relaunch to confirm the mirror key still names the right face.

## R — Revisit guide (Settings → How it works)

- Page 1 shows the carousel **locked on the current face**: it does not scroll, the card is badged, and the hint points to Settings → Change disguise.
- Pages 3 and 4 show the current face's guide content.
- Done returns to Settings; nothing is written; the vault stays unlocked.

## E — Erase everything

- With a non-calculator face active, erase everything. The app returns to the guide with the **calculator** centered as the default again.
- Confirm via `adb shell run-as` (or simply by the behavior above) that the stored face is gone with the passcode — the old code must not work on the new setup.

## A — Accessibility and appearance

- TalkBack on each face: calculator keys read as calculator keys; PIN pad reads "one"…"nine", "zero", "delete", "enter", and the dot row reads as entered digits with a count. No element hints at unlocking, a vault, or a passcode.
- The **pattern grid cannot be operated by TalkBack** — confirm this is true and that the pattern card discloses it, so a screen-reader user can choose the calculator or PIN pad instead.
- "Remove animations" (animator duration scale 0) in Developer options: transitions snap, and the shake is replaced by a still hold of the same length with the entry still clearing.
- Dark and light mode on all three faces and the carousel; largest font-size and display-size settings — captions wrap to two lines without clipping and the keypads stay usable.
- Small phone and tablet: the PIN pad column stays centered within its max width; the pattern grid stays square and fully on screen.
