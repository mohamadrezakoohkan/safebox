# SafeBox — Iteration 3 Shared Decisions: Lock Faces

**Document:** `docs/plans/iteration-3-decisions.md`
**Implements:** `docs/plans/disguise-skeleton-plan.md` (the pluggable-disguise contract, designed for iteration 2 and never built), with the amendments in §1. Where this document and the skeleton plan disagree, **this document wins**; §10 lists every statement it supersedes.
**Companion:** `docs/plans/iteration-2-decisions.md` §5 (Settings IA), §10 (string-table style), §11 (constants). Pinned calculator copy stays in `docs/plans/calculator-disguise-design.md` §6.
**Status:** Decided 2026-09-03. Implementation follows the sequence in §9.

---

## 0. Scope and vocabulary

The calculator is no longer "the app's front". It is one of several **lock faces** — *disguises* in product language — and the host lock subsystem renders whichever face the owner enrolled. Iteration 3 ships three, in this registry order:

| `id` (= `tokenSetId`) | Display name | `isCovert` | Identity grade under "Calculator+" | Commit gesture |
|---|---|---|---|---|
| `calculator` | Calculator | `true` | native | `=` key |
| `numpad` | PIN pad | `false` | incoherent | `✓` key |
| `pattern` | Pattern | `false` | incoherent | finger lift |

Vocabulary carried from the skeleton plan unchanged: **host**, **disguise**, **token**, **alphabet**, **mode** (`DisguiseMode` = `disguise | captureNew | confirmNew | verifyCurrent`). New terms:

- **Covert face** — a non-match is silent (the calculator).
- **Overt face** — a non-match shakes and clears, like a phone lock screen (PIN pad, pattern).
- **Failed-attempt pulse** — the host→face "that attempt failed" signal (§1.1).
- **Pending disguise** — the face chosen in the guide, before any envelope exists (§4).

Rules for every item (restating iteration 2): new user-facing copy uses the shared string IDs in §7 on both platforms; both unit suites stay green and every step adds tests; **no logging** of tokens, buffers, salts, hashes, alphabet or lock internals — extended explicitly to the faces (a face logging its own "key pressed" line would leak the token stream, and the pattern gesture loop must never log coordinates or node indices); nothing vault-related is composed while locked.

The host owns everything in skeleton §3.3, unchanged: lock state machine, `TokenRecorder`, rules (4–32, overflow flag, trivial warning), PBKDF2/storage, re-lock triggers, suppression window, mode assignment, registry, switch flow. **Faces are renderers and input devices only.** The skeleton's review heuristic still applies: if a line of code makes a decision that would appear in a security audit, it lives in the host.

---

## 1. Contract amendments

The skeleton plan's six-item contract stands except for these four changes. They are deliberately thin — each is forced by a face actually shipping in this iteration — and the §4.3 freeze otherwise holds.

### 1.1 `isCovert` and the failed-attempt pulse

Every disguise declares `isCovert: Bool`. The host exposes one additional input to the surface, beside `mode` and `caption`:

```
failedAttemptCount: Int    // monotonically increasing per surface instance; starts at 0
```

**Host bump rule (exhaustive):**

| Mode | Non-accepted commit (wrong, < 4 tokens, or overflowed) | Bump? |
|---|---|---|
| `disguise` | active face `isCovert == true` | **never** (silent; unchanged from iteration 1) |
| `disguise` | active face `isCovert == false` | **yes** |
| `verifyCurrent` | any face | **yes** (generalizes today's `shakeToken`) |
| `captureNew`, `confirmNew` | any face | **never** — captions carry too-short / too-long / mismatch |

- The bump happens on the main actor **after verification completes**: after the KDF for a valid-length attempt, immediately for a short or overflowed one (the KDF is skipped, as today — that invariant is unchanged and still tested). The resulting ~0–300 ms timing difference is visible only on overt faces and is accepted: an overt face is not hiding that it is a lock.
- **Overt-face buffer rule:** whenever the host bumps the pulse for an **overt** face, it also clears the `TokenRecorder` buffer and overflow flag in the same step. The face visibly resets its entry, so tokens typed during the verification window must not linger in a buffer the face no longer depicts. For the **calculator in `verifyCurrent` the buffer is not cleared** — that is today's behavior and it stays.
- The surface reacts only to **increments observed after its first render** (iOS `.onChange`, Compose `LaunchedEffect` skipping the initial value). The host additionally resets the count to 0 whenever it instantiates a fresh surface (§1.5), so a stale count can never shake a new face.
- The pulse carries no semantics beyond "failed". It never says why, and the face never learns which token was wrong. The stream stays one-way in every other respect.

### 1.2 `removeLast` event and `TokenRecorder` semantics

`DisguiseEvent` gains a fourth case: `token(id) | commit | clear | removeLast`. The host-owned recorder (`Lock/TokenRecorder.swift`, `core/lock/TokenRecorder.kt` — generalized from `PasscodeRecorder` / `KeySequenceRecorder` over `String` token IDs):

| Event | Buffer | Overflow flag |
|---|---|---|
| `token(id)`, buffer < 32 | append | unchanged |
| `token(id)`, buffer == 32 | unchanged | **set** |
| `removeLast`, buffer empty | no-op | unchanged |
| `removeLast`, buffer non-empty | pop last | **unchanged (sticky)** |
| `clear` | empty | reset |
| `commit` | host takes `(tokens, overflowed)`, then empty | reset |

The overflow flag is sticky through `removeLast` on purpose: once a 33rd token has been seen the entry is unrecoverable by backspacing, and the only recovery is `clear` (PIN pad: long-press ⌫) or a commit that fails with `TOO_LONG`. Emitters: **only `numpad` emits `removeLast`**; `calculator` and `pattern` never do. The 32 cap, 4 minimum, and skip-KDF-on-short-or-overflow rules are unchanged.

### 1.3 Semantic captions (`CaptionKind`) and the error-revert rule

The host hands the surface `caption: CaptionState?` = `{ primary: CaptionKind, secondary: CaptionKind? }`; the face maps kinds to its own strings (§2, §7). The host never carries literal caption strings. Shared enumeration, identical names on both platforms (Android's `BannerText` is renamed to it; iOS's `LockBanner` stops carrying strings):

`PROMPT_NEW_SETUP`, `PROMPT_NEW_CHANGE`, `STRENGTH_HINT`, `TOO_SHORT`, `TOO_LONG`, `PROMPT_CONFIRM_SETUP`, `PROMPT_CONFIRM_CHANGE`, `MISMATCH`, `TRIVIAL_WARNING`, `PROMPT_CURRENT`, `WRONG_CODE`

`WRONG_CODE` is the only kind rendered in the error color (`disguise/captionError`).

**Revert rule (host):** while the caption is `WRONG_CODE`, any `token`, `clear` or `removeLast` event reverts it to `PROMPT_CURRENT` (generalizes today's `keyPressed()`). A `commit` does not revert.

The caption strip keeps design §5.5 metrics on every face: 13 pt/sp, min height 28, centered, up to two lines, 150 ms fade in/out on mode entry/exit, 120 ms crossfade on text change. It is **never composed in `disguise` mode**; overt faces put their own static title in that slot instead (§2.2).

### 1.4 Guide content slot

Each disguise supplies, for the onboarding carousel and the switch picker (§6):

```
DisguiseGuideContent {
  displayName, tagline, commitGestureName   // IDs: <id>_display_name, <id>_tagline, <id>_commit_gesture
  identityGrade: native | plausible | incoherent
  page3Title, page3Body, page3Try, page3Ok  // <id>_guide_page3_title / _body, <id>_guide_try / _ok
  page4Title, page4Body                     // <id>_guide_page4_title / _body
  makePlayground(onCountChanged) -> View    // small interactive demo for page 3; reports only a tap count
  makeCommitHero() -> View                  // looping, non-interactive illustration of the commit gesture
  a11yNote: String?                         // pattern only: pattern_a11y_note
}
```

The card thumbnail is **not** part of the slot — it is `makeCoverFace()` rendered at scale (§6), which is why the cover face is now required and used on Android too.

### 1.5 Re-instantiation rule (clarified)

The host tears down and freshly instantiates the surface — and resets `failedAttemptCount` — on:

- every lock transition,
- backgrounding while locked or in setup,
- `reset()` (post-erase),
- and **whenever the face identity changes** (switch flow `PICK → CAPTURE_NEW`, guide selection change).

It does **not** re-instantiate on phase changes within a flow (`captureNew → confirmNew`, `verifyCurrent → captureNew` on the same face) — today's calculator keeps its display across those phases, and that behavior is preserved. `calculatorEpoch` is renamed `disguiseEpoch` on both platforms.

### 1.6 Registry

`DisguiseRegistry`: an ordered, **append-only**, compiled-in list `[calculator, numpad, pattern]`; `default = calculator`; `resolve(id) -> Disguise` falling back to the default. A shipped face is never removed (skeleton §10 risk 4).

### 1.7 Host-side shape (platform-neutral)

```
protocol Disguise {
  id, isCovert, alphabet: AlphabetDescriptor, guide: DisguiseGuideContent
  makeSurface(mode, caption, failedAttemptCount, events: (DisguiseEvent) -> Void) -> View
  makeCoverFace() -> View
}
AlphabetDescriptor { tokenSetId, alphabetVersion, tokens: [String], serialize = "|"-join }
```

One host object per surface instance (`DisguiseSurfaceHost` on both platforms) owns the `TokenRecorder`, translates events, and calls the state machine's `commit(tokens:overflowed:)`. The §1.1 overt buffer clear lives there.

---

## 2. Per-face specs

### 2.1 Calculator (re-homed; behavior byte-identical)

- **Descriptor:** `tokenSetId = "calculator"`, `alphabetVersion = 1`, tokens `D0 D1 D2 D3 D4 D5 D6 D7 D8 D9 DOT ADD SUB MUL DIV PCT SIGN` (17), serialization `|`-join — e.g. `D1|D2|ADD|D3|D4`. `isCovert = true`. Identity grade: **native**.
- Commit `=` → `commit`; `AC`/`C` → `clear`; never `removeLast`. Engine, 17-row behavior table, display rules, operator ring, haptics and shake target (the display readout) are all unchanged.
- **Caption mapping — pinned strings, verbatim** (design §6 remains the string authority): `PROMPT_NEW_SETUP → setup_entry_banner`, `PROMPT_NEW_CHANGE → change_enter_new_caption`, `STRENGTH_HINT → setup_entry_hint`, `TOO_SHORT → setup_too_short`, `TOO_LONG → setup_too_long`, `PROMPT_CONFIRM_SETUP → setup_confirm_banner`, `PROMPT_CONFIRM_CHANGE → change_confirm_caption`, `MISMATCH → setup_mismatch`, `TRIVIAL_WARNING → setup_trivial_warning`, `PROMPT_CURRENT → verify_current_caption`, `WRONG_CODE → verify_error`.
- Pulse: reacts only in `verifyCurrent`; by §1.1 it never receives one in `disguise` mode. Cover face = today's `CalculatorCoverView` content.
- Guide: existing copy, IDs renamed to the `calculator_guide_*` scheme (§7); the existing mini-keypad playground and pulsing `=` hero become `makePlayground` / `makeCommitHero`.

### 2.2 Rules shared by both overt faces

1. **Believability** reduces to "looks like a native lock screen": no branding, no app name, no vault vocabulary, palette from `DisguiseTheme` (the same `disguise/bg` as the calculator, so a switch keeps the app's look).
2. **Static face title** — face-owned decoy text, `disguise` mode only, occupying the caption slot: `numpad_face_title`, `pattern_face_title`. In the three capture modes the host caption occupies that slot instead.
3. **Commit consequence by mode:**
   - `disguise`, `verifyCurrent`: on commit the entry (dots / path) **stays visible** and the face keeps accepting input. On unlock the host tears it down. On a pulse: shake, hold, then clear.
   - `captureNew`, `confirmNew`: the face clears its entry **immediately on commit** (the caption tells the outcome; no pulse arrives in these modes).
4. **Shake spec** (design §5.6 reused verbatim): the shake target — PIN pad: the dot row; pattern: the whole grid — translates horizontally ±8 pt/dp, 3 cycles, 300 ms, ease-in-out. The entry clears when the shake ends (`OVERT_FAIL_HOLD_MS = 300`). Under reduce-motion there is no translation but the same 300 ms hold, so the user still sees what failed. **No text on a `disguise`-mode failure** (that mode has no caption slot); in `verifyCurrent` the host's `WRONG_CODE` caption appears alongside.
5. **Haptics:** one identical haptic per key/node, same as the calculator (iOS light impact 0.7; Android `KEYBOARD_TAP`). No haptic on unlock, none on the pulse.
6. **Decoy-consequence clause:** commit does nothing beyond the screen — no sound, no notification, no scheduled OS work, no writes.
7. **Entry-trace clause:** live trace = the visible entry; residual trace after a failed attempt = none (cleared by the pulse); after a successful unlock = none (teardown); on lock = fresh surface, empty entry, by §1.5. Verified in §9.
8. **Accessibility:** real elements with genre labels; no hints, no custom actions; identifiers contain none of `passcode|vault|unlock|secret|lock|safebox`. No announcement on commit, match, non-match or pulse. The caption strip is read before the entry region.
9. **Guessing posture unchanged:** unlimited silent attempts remain accepted (idea plan §2.4). Overt faces make attempts *visible*, not *counted*, and add no lockout.

### 2.3 PIN pad (`numpad`)

- **Descriptor:** `tokenSetId = "numpad"`, `alphabetVersion = 1`, tokens `D0…D9` (10), `|`-join. `isCovert = false`. Identity grade: **incoherent**. Length 4–32 = host rules.
  The IDs deliberately coincide with the calculator's digits: token IDs are opaque *per alphabet*, salts differ per enrollment, and the overlap gives the fail-closed calculator face a chance to still accept a digits-only PIN if a face ever fails to resolve (§3).
- **Layout (identical on both platforms):** a centered column of max width `NUMPAD_COLUMN_MAX_WIDTH = 320`, side margin as the calculator's `m`. Top to bottom: caption slot (static title in `disguise` mode) → dot row → 40 gap → 3×4 grid of **circular** keys: `1 2 3` / `4 5 6` / `7 8 9` / `⌫ 0 ✓`. Key diameter `d = clamp((columnWidth − 2·24) / 3, 64, 80)`, gap 24 on both axes. Digits: `keyDigit` fill, `keyLabel` glyph 32 medium. `⌫` and `✓`: `keyFn` fill, `keyLabel` glyph (`delete.left` / `checkmark`; Material `Backspace` / `Check`), 26. Pressed fills and the 0 ms-down / 180 ms-up fade rule as the calculator. The block is vertically centered in the remaining space — a lock screen, not a bottom-anchored keypad.
- **Dot row:** one filled dot (`displayText`, `NUMPAD_DOT = 12`, gap 12) per entered digit, in a single centered row; when the row would exceed the column width, dot size and gap shrink uniformly (floor 6). Display-only, capped at `PASSCODE_MAX_TOKENS` (32) visible dots — presses beyond still animate, buzz and emit `token` (the recorder sets overflow), they just add no dot. Empty row at rest.
- **Events:** digit → `token(Dn)` on touch-down; `✓` → `commit` on touch-down; `⌫` → `removeLast` **on release**, unless a long-press already fired; long-press `⌫` (platform default timeout — the ~100 ms divergence is accepted) → `clear` once, with one haptic, and the release is swallowed. `✓` is never disabled: a short or empty commit is the host's business (§1.1 pulses it in `disguise`/`verifyCurrent`; captions handle it in capture modes).
- **Resting / cover face:** title `numpad_face_title`, empty dot row, keypad.
- **Collision and entropy disclosure:** a 10-token, digits-only alphabet — the low-entropy case the idea plan's threat framing warns about; real PINs cluster on dates and repeats. `numpad_hint` nudges toward 6+ digits, no dates, no repeats. The host's trivial warning (a single repeated digit) is reachable and renders as `numpad_trivial_warning`.
- **Accessibility labels:** `zero`…`nine`, `delete` (⌫), `enter` (✓); the dot row is one element labelled `entered digits` with the count as its value. Identifiers `numpad_key_0…9`, `numpad_key_delete`, `numpad_key_enter`, `numpad_dots`.
- **Caption mapping:** `PROMPT_NEW_SETUP → numpad_prompt_new`, `PROMPT_NEW_CHANGE → numpad_prompt_new_change`, `STRENGTH_HINT → numpad_hint`, `TOO_SHORT → numpad_too_short`, `TOO_LONG → numpad_too_long`, `PROMPT_CONFIRM_SETUP → numpad_prompt_confirm`, `PROMPT_CONFIRM_CHANGE → numpad_prompt_confirm_change`, `MISMATCH → numpad_mismatch`, `TRIVIAL_WARNING → numpad_trivial_warning`, `PROMPT_CURRENT → numpad_prompt_current`, `WRONG_CODE → numpad_wrong_code`.
- **Guide:** playground = a compact 3×4 PIN pad filling a mini dot row (Reset clears); hero = a pulsing `✓` key (same 0.85 s / 1.09× loop as the `=` hero).

### 2.4 Pattern (`pattern`)

- **Descriptor:** `tokenSetId = "pattern"`, `alphabetVersion = 1`, tokens `N0 N1 N2 N3 N4 N5 N6 N7 N8` — row-major, `N0 N1 N2` top row, `N3 N4 N5` middle, `N6 N7 N8` bottom — `|`-join. `isCovert = false`. Identity grade: **incoherent**. A node cannot repeat within a stroke, so a commit carries 1–9 tokens and **overflow is impossible by construction** (unit-tested). The 4-token minimum is the host's rule.
- **Layout:** a centered square grid of side `min(columnWidth, PATTERN_GRID_MAX = 300)`, cell = side / 3; caption slot above with a 32 gap (static title in `disguise` mode). Node at rest: `keyFn` circle 16; selected: `keyOp` circle 28; connecting line: `keyOp` at 70 % alpha, width 6, drawn node-to-node plus a live segment from the last node to the finger. On a pulse, nodes and line switch to `captionError` for the hold. Node hit area: a square of `cell × PATTERN_NODE_HIT_FACTOR (0.6)` centered on the node.
- **Stroke semantics** — a pure, unit-tested reducer (`PatternStroke` / `PatternGeometry`):
  - **Touch down anywhere on the grid:** emit `clear`, reset the path. (This also protects against a previous stroke that ended in a system cancel.)
  - **Finger enters an unselected node `B`:** if a last selected node `A` exists and the pair is two apart in a straight line, the midpoint node is auto-selected first when not already selected — emit `token(mid)` then `token(B)`. Entering an already-selected node emits nothing. Exhaustive midpoint table (and their reverses): `(N0,N2)→N1`, `(N3,N5)→N4`, `(N6,N8)→N7`, `(N0,N6)→N3`, `(N1,N7)→N4`, `(N2,N8)→N5`, `(N0,N8)→N4`, `(N2,N6)→N4`.
  - **Lift with ≥ 1 node selected:** emit `commit`. **Lift with 0 nodes:** emit nothing — a stroke that touched no node is not a pattern. This is the face defining its own commit gesture, not a length rule.
  - **System touch-cancel:** emit `clear`, reset the path, no commit.
- **Resting / cover face:** title `pattern_face_title`, nine resting nodes.
- **Collision and entropy disclosure:** 9 tokens, no repeats, adjacency-shaped; casual users draw letters (L, Z, N) and straight lines — the classic weak set. `pattern_hint` nudges toward 6+ nodes with turns.
- **Unreachable states:** `TOO_LONG` and `TRIVIAL_WARNING` cannot occur (9 max, no repeats). The face maps them defensively to the current mode's prompt, and a unit test asserts unreachability. Too-short renders as `TOO_SHORT` in capture modes and as the pulse elsewhere.
- **Accessibility:** the grid is a single element labelled `pattern grid`, and it is **not operable by synthesized activation** — a stroke cannot be synthesized. The consequence is disclosed on its carousel and picker card via `pattern_a11y_note`; screen-reader users are expected to choose the calculator or PIN pad. Identifier `pattern_grid`.
- **Caption mapping:** `PROMPT_NEW_SETUP → pattern_prompt_new`, `PROMPT_NEW_CHANGE → pattern_prompt_new_change`, `STRENGTH_HINT → pattern_hint`, `TOO_SHORT → pattern_too_short`, `PROMPT_CONFIRM_SETUP → pattern_prompt_confirm`, `PROMPT_CONFIRM_CHANGE → pattern_prompt_confirm_change`, `MISMATCH → pattern_mismatch`, `PROMPT_CURRENT → pattern_prompt_current`, `WRONG_CODE → pattern_wrong_code`; `TOO_LONG` / `TRIVIAL_WARNING` → the current mode's prompt (unreachable).
- **Guide:** playground = a compact drawable 3×3 grid (Reset clears); hero = a looping animated finger path over a 3×3 grid ending in a lift.

---

## 3. Envelope v2 and migration

```
PasscodeEnvelope v2 (JSON, camelCase like v1):
  algo "PBKDF2-HMAC-SHA256", version 2, iterations 600000, salt (16 B), hash (32 B),
  tokenSetId: String, alphabetVersion: Int, activeDisguiseId: String
```

- **Storage locations unchanged.** iOS: Keychain `service com.calcplus.calculator`, `account passcode.v1` — the account names a *location*, not a schema version; renaming it would orphan every install. Android: `KEY_BLOB` in the `passcode_store` DataStore, Keystore-wrapped as today.
- **Android mirror key:** `stringPreferencesKey("active_disguise_id")` in the same DataStore, written **in the same `edit {}` transaction** as `KEY_BLOB` on every `set()` and every migration rewrite, and removed by `clear()`. The envelope copy is **authoritative**; after any successful `matches()` the store compares and rewrites the mirror when it differs. Because both keys live in one file and one transaction, a desync is unreachable through app code; the fail-closed rule covers it regardless.
- **Launch read — Android:** `AppContainer`'s single existing `runBlocking { prefsDataStore.data.first() }` additionally reads `active_disguise_id` from the same snapshot and passes it to `AppLockManager`. **The envelope is never unwrapped at launch** — no Keystore work in `Application.onCreate`.
- **Launch read — iOS:** `KeychainPasscodeStore.activeDisguiseId` decodes the Keychain item already read for `hasPasscode`; `nil` when absent or undecodable; a v1 envelope yields `"calculator"`.
- **Fail-closed face rule (both):** an unresolvable id — missing, undecodable, unwrappable, unknown to the registry, or envelope version > 2 — renders **`calculator` in `disguise` mode** with buffer rules unchanged. `matches()` returns false for an unreadable envelope, and **`hasPasscode` stays true whenever an item exists**, so setup can never be reached over an existing vault. Never an error surface, never a non-disguise surface.
- **Migration:** a v1 envelope is interpreted as `tokenSetId = "calculator"`, `alphabetVersion = 1`, `activeDisguiseId = "calculator"` — absence of the new fields means calculator.v1, by definition. On the first read the store eagerly rewrites it as v2 with **salt and hash copied byte for byte** (Android: the mirror written in the same commit). A rewrite failure is silent and the v1 interpretation holds forever; the v1 read path is never deleted. Android guards the rewrite with a "already attempted" flag so a persistently failing store does not retry on every verification.
- **Version ceiling:** the decoder explicitly **rejects `version > 2`** (skeleton §3.4 forward obligation).
- **Store API (both):** `set(tokens: [String], alphabet: AlphabetDescriptor, activeDisguiseId: String)`, `matches(tokens: [String]) -> Bool`, `activeDisguiseId`, `hasPasscode`, `clear()`. `matches` serializes with the universal `|`-join — version- and set-invariant by rule — and does **not** compare `tokenSetId`. The only cross-alphabet coincidence is the deliberate shared `D0–D9` (§2.3), which is harmless: one envelope, one salt.
- **Plain change-passcode** calls `set(tokens, alphabet: activeFace.alphabet, activeDisguiseId: activeFace.id)` — the face is preserved.

---

## 4. First-run setup on a chosen face

- The lock coordinator/manager gains an in-memory `pendingDisguiseId: String`, default `calculator`, set when the guide finishes (`completeOnboarding(selectedDisguiseId:)`). **Skip** passes whatever card is centered at that moment (calculator unless the user scrolled). It survives backgrounding — setup returns to `captureNew` on the same face, as today — and dies with the process.
- While `firstRunSetup`, the root renders `registry.resolve(pendingDisguiseId)` in `captureNew` / `confirmNew`, and the iOS snapshot cover shows that face's cover. Setup commits use that face's alphabet and id for the first envelope write; on success it becomes the active face and the unlock reveal runs unchanged.
- **Completion-flag timing changes.** The onboarding-complete sentinel (`onboardingComplete.v1` / `onboarding_complete`) is now written **when the first envelope is stored**, not when the guide finishes. Otherwise a process death between the two would strand the user on a face they can no longer choose. iOS: the coordinator invokes an injected `onSetupComplete` hook that calls `OnboardingSentinel.setComplete()`. Android: `recordOnboardingCompletion` keeps flipping the in-memory flag at guide finish, and the persisted write moves to an observer of the `NeedsSetup → Unlocked` transition, alongside the existing trash housekeeping. An iteration-2 install that finished the guide but died mid-setup already has the flag and lands on calculator setup — accepted.
- `reset()` (post-erase) restores `pendingDisguiseId = calculator`, the active face to the default, and bumps `disguiseEpoch`.

---

## 5. Settings row and the switch flow

**Row placement:** the **Security** section, directly under "Change passcode" and above "Lock now". The skeleton's "Appearance → Disguise" placement is superseded: this row re-enrolls the code, so it belongs beside the other passcode row. Title `settings_change_disguise_title`, subtitle = the active face's display name. iOS: a sheet with `interactiveDismissDisabled`, mirroring `ChangePasscodeFlow`. Android: `ChangeDisguiseRoute` in the Settings tab graph, mirroring `ChangePasscodeRoute`. Navigation title `settings_change_disguise_title`; Cancel (`change_cancel`) visible in every phase.

**State machine** (`DisguiseSwitchSession` / the generalized change view model), in the user-specified order:

```
[VERIFY_CURRENT]  current face, mode verifyCurrent, caption PROMPT_CURRENT
  ├─ commit matches (valid length, !overflow, KDF match) → PICK
  ├─ any other commit → caption WRONG_CODE + failedAttemptCount += 1
  │      (overt current face: buffer cleared too, §1.1); stay; unlimited retries
  └─ Cancel → ABANDON

[PICK]  carousel (§6) in "pick" mode, centered on the current face; identity disclosure;
        explainer disguise_switch_explainer(currentName, currentCommitGesture);
        primary button disguise_pick_action, DISABLED while the centered card is the
        current face — the current face cannot be picked, so there is no no-op path
  ├─ pick ≠ current → CAPTURE_NEW(new)     (fresh surface: face identity changed, §1.5)
  └─ Cancel / back → ABANDON

[CAPTURE_NEW]  new face, mode captureNew, caption PROMPT_NEW_CHANGE + STRENGTH_HINT
  ├─ overflowed → TOO_LONG + hint, stay      ├─ < 4 → TOO_SHORT + hint, stay
  ├─ valid → hold pending in memory → CONFIRM_NEW (TRIVIAL_WARNING as secondary if trivial)
  └─ Cancel → ABANDON

[CONFIRM_NEW]  new face, mode confirmNew, caption PROMPT_CONFIRM_CHANGE
  ├─ tokens == pending && !overflowed → COMMIT
  ├─ else → pending discarded → CAPTURE_NEW with caption MISMATCH + hint
  └─ Cancel → ABANDON

[COMMIT]  store.set(tokens, alphabet: new.alphabet, activeDisguiseId: new.id)
          — ONE atomic replace (Android: blob + mirror in one edit)
  ├─ success → active face = new → DONE: dismiss, alert
  │            disguise_switch_success_title / _body (restates no-recovery)
  └─ write failure → CAPTURE_NEW with PROMPT_NEW_CHANGE (old envelope intact)

ABANDON / backgrounding at ANY step: buffers and pending discarded, session dropped,
old code and old face fully intact. Backgrounding also locks the vault (unchanged),
which tears the flow down with it.
```

Until the single write lands, the old blob is authoritative: old code valid, old face shown. After it lands, only the new code is valid and the lock screen shows the new face from the next lock onward. There is no intermediate state to observe or recover.

A **plain change-passcode** is unchanged except that it now passes the active face's alphabet and id through `set()`, preserving `activeDisguiseId`.

---

## 6. Carousel and picker (one component, three modes)

`DisguiseCarousel(mode: firstRun | revisit | pick, selection, current)`:

- **Cards**, one per registry entry, in registry order: thumbnail (the face's `makeCoverFace()` rendered into a 360×640 virtual canvas, scaled 0.35 into a 126×224 frame, radius 20, clipped), display name (16 semibold), tagline (13 caption), identity-grade line (`disguise_grade_native` / `disguise_grade_incoherent`), `pattern_a11y_note` on the pattern card, and `disguise_current_badge` on the current face in `revisit`/`pick`. Card 256×340, radius 24, `keyDigit` fill; spacing 12; neighbours peek 24 each side; snapping. **The centered (snapped) card is the selection** — no tap required, though tapping a neighbour scrolls it to centre.
- Below the carousel, in every mode: `disguise_identity_disclosure`.
- **firstRun** (guide page 1, replacing "Looks like a calculator"): title `onboarding_disguise_title`, body `onboarding_disguise_body`, scrollable, starting on `calculator`. Pages 3 and 4 bind to the selection and update live — page 3 = the face's `page3Title/Body`, its `makePlayground` (recreated when the selection changes), the shared 4 progress pips, `page3Try`/`page3Ok` and `onboarding_page3_clear`; page 4 = its `makeCommitHero`, `page4Title/Body` and the shared `onboarding_page4_warning` card. Page 2 is unchanged. "Set my code" / "Skip" → `completeOnboarding(selectedDisguiseId:)`.
- **revisit** (Settings → How it works): locked on the current face (not scrollable; neighbours visible but dimmed), badge shown, hint `onboarding_disguise_revisit_hint`; pages 3/4 show the current face's content. Nothing is written (iteration-2 §5 unchanged).
- **pick** (switch flow): scrollable, starting on the current face, badge on it, with the explainer and `disguise_pick_action` below the disclosure.

---

## 7. Shared string table (IDs identical on both platforms)

Format arguments: iOS `%1$@ %2$@`, Android `%1$s %2$s`. iOS reads guide/vault strings through `VaultCopy` (camelCased IDs) and face strings through each face's own copy enum; Android through `stringResource`.

**Pinned calculator strings — verbatim, reused as the calculator's caption mapping (no change):** `setup_entry_banner`, `setup_entry_hint`, `setup_too_short`, `setup_too_long`, `setup_confirm_banner`, `setup_mismatch`, `setup_trivial_warning`, `setup_no_recovery_title/body/button`, `verify_current_caption`, `verify_error`, `change_enter_new_caption`, `change_confirm_caption`, `change_success`, `change_cancel`, `settings_change_title`. Also reused unchanged: `onboarding_skip/next/start/done`, `onboarding_page2_*`, `onboarding_page3_clear`, `onboarding_page4_warning`, `cancel_action`, `ok_action`.

**Renamed (text verbatim):** `onboarding_page3_title → calculator_guide_page3_title`, `onboarding_page3_body → calculator_guide_page3_body`, `onboarding_page3_try → calculator_guide_try`, `onboarding_page3_ok → calculator_guide_ok`, `onboarding_page4_title → calculator_guide_page4_title`, `onboarding_page4_body → calculator_guide_page4_body`.

**Retired (deleted on both platforms):** `onboarding_page1_title`, `onboarding_page1_body`.

**New:**

| ID | English | Where |
|---|---|---|
| `calculator_display_name` | Calculator | card, Settings subtitle, explainer arg |
| `calculator_tagline` | A fully working calculator. A wrong code just calculates — no error, no hint. | card |
| `calculator_commit_gesture` | the = key | explainer arg |
| `numpad_display_name` | PIN pad | card, Settings subtitle, explainer arg |
| `numpad_tagline` | A plain PIN screen. A wrong PIN shakes and clears, like a phone lock. | card |
| `numpad_commit_gesture` | the ✓ key | explainer arg |
| `pattern_display_name` | Pattern | card, Settings subtitle, explainer arg |
| `pattern_tagline` | Connect the dots in one stroke. A wrong pattern shakes and clears, like an Android lock. | card |
| `pattern_commit_gesture` | a finger lift | explainer arg |
| `pattern_a11y_note` | Not usable with a screen reader | pattern card |
| `disguise_grade_native` | Matches the app's name and icon | calculator card |
| `disguise_grade_incoherent` | Doesn't match the app's name and icon | PIN pad, pattern cards |
| `disguise_identity_disclosure` | On your home screen the app is always called Calculator+ and keeps its calculator icon. Only the screen shown while locked changes — so a Calculator+ that opens to a PIN pad or a pattern is itself a hint that something is hidden. | below the carousel (all modes) |
| `disguise_current_badge` | Current | card badge (revisit, pick) |
| `onboarding_disguise_title` | Pick a disguise | guide page 1 |
| `onboarding_disguise_body` | Anyone who opens Calculator+ sees only this screen. You can change it later in Settings. | guide page 1 |
| `onboarding_disguise_revisit_hint` | This is your current disguise. You can change it in Settings → Change disguise. | guide page 1, revisit |
| `numpad_guide_page3_title` | Your PIN is 4 to 32 digits | guide page 3 |
| `numpad_guide_page3_body` | Any digits, any length from 4 to 32 — order matters. Try one here. This is just practice; nothing is saved. | guide page 3 |
| `numpad_guide_try` | Tap at least 4 digits | guide page 3 |
| `numpad_guide_ok` | That would work — more digits make it stronger | guide page 3 |
| `numpad_guide_page4_title` | Tap ✓ to enter | guide page 4 |
| `numpad_guide_page4_body` | To unlock, enter your PIN and tap ✓. A wrong PIN shakes and clears — anyone can see it's a lock screen, but not what it protects. | guide page 4 |
| `pattern_guide_page3_title` | Your pattern connects the dots | guide page 3 |
| `pattern_guide_page3_body` | Draw one stroke through 4 to 9 dots — each dot once, and the path matters. Try one here. This is just practice; nothing is saved. | guide page 3 |
| `pattern_guide_try` | Connect at least 4 dots | guide page 3 |
| `pattern_guide_ok` | That would work — more dots and turns make it stronger | guide page 3 |
| `pattern_guide_page4_title` | Lift your finger to enter | guide page 4 |
| `pattern_guide_page4_body` | To unlock, draw your pattern and lift your finger. A wrong pattern shakes and clears — anyone can see it's a lock screen, but not what it protects. | guide page 4 |
| `numpad_face_title` | Enter PIN | PIN pad, `disguise` mode only |
| `numpad_prompt_new` | Choose a PIN: enter 4 to 32 digits, then tap ✓ | captureNew (first run) |
| `numpad_prompt_new_change` | Enter your new PIN, then tap ✓ | captureNew (change / switch) |
| `numpad_hint` | Best: 6 or more digits — not a date, not a repeat. | secondary line |
| `numpad_too_short` | Too short — use at least 4 digits | captureNew |
| `numpad_too_long` | Too long — start again (max 32 digits) | captureNew |
| `numpad_prompt_confirm` | Re-enter the same PIN, then tap ✓ | confirmNew (first run) |
| `numpad_prompt_confirm_change` | Re-enter the new PIN, then tap ✓ | confirmNew (change / switch) |
| `numpad_mismatch` | PINs didn't match — start again | back in captureNew |
| `numpad_trivial_warning` | Easy to guess — re-enter it to keep it anyway, or enter a different PIN to start over. | confirmNew secondary |
| `numpad_prompt_current` | Enter your current PIN, then tap ✓ | verifyCurrent |
| `numpad_wrong_code` | Incorrect PIN — try again | verifyCurrent, error color |
| `pattern_face_title` | Draw your pattern | pattern, `disguise` mode only |
| `pattern_prompt_new` | Choose a pattern: connect at least 4 dots, then lift your finger | captureNew (first run) |
| `pattern_prompt_new_change` | Draw your new pattern, then lift your finger | captureNew (change / switch) |
| `pattern_hint` | Best: 6 or more dots with a turn or two — not a straight line or a letter. | secondary line |
| `pattern_too_short` | Too short — connect at least 4 dots | captureNew |
| `pattern_prompt_confirm` | Draw the same pattern again | confirmNew (first run) |
| `pattern_prompt_confirm_change` | Draw the new pattern again | confirmNew (change / switch) |
| `pattern_mismatch` | Patterns didn't match — start again | back in captureNew |
| `pattern_prompt_current` | Draw your current pattern | verifyCurrent |
| `pattern_wrong_code` | Wrong pattern — try again | verifyCurrent, error color |
| `settings_change_disguise_title` | Change disguise | Settings row, switch-flow nav title |
| `disguise_pick_action` | Use this disguise | picker CTA |
| `disguise_switch_explainer` | Your current code belongs to the %1$@ disguise and is confirmed with %2$@. The new disguise needs a new code, set on its own keys. Your photos, notes, and contacts are unchanged. | picker (args: current display name, current commit gesture) |
| `disguise_switch_success_title` | Disguise changed | alert after commit |
| `disguise_switch_success_body` | Your new code works from now on. There is no way to recover it — if you forget it, your vault contents cannot be retrieved. | alert after commit |

The Settings row subtitle is the bare `<id>_display_name`, no template. **No lock-screen string** (`*_face_title`, any face caption) contains "passcode", "vault", "unlock" or "SafeBox"; vault vocabulary is confined to guide, picker and alert copy, which render only pre-setup or inside the unlocked vault.

---

## 8. Shared constants

| Constant | Value |
|---|---|
| `PASSCODE_MIN_TOKENS` / `PASSCODE_MAX_TOKENS` | 4 / 32 (unchanged) |
| `ENVELOPE_VERSION` / max accepted | 2 / 2 |
| Alphabet versions | calculator 1, numpad 1, pattern 1 |
| Android mirror key | `active_disguise_id` |
| Shake | ±8 pt/dp, 3 cycles, 300 ms, ease-in-out (unchanged) |
| `OVERT_FAIL_HOLD_MS` | 300 (entry clears when the shake ends; same delay under reduce-motion) |
| Caption strip | 13 pt/sp, min height 28, 150 ms fade, 120 ms crossfade (unchanged) |
| PIN pad grid | 3 × 4; key diameter clamp [64, 80]; gap 24; column max width 320; dots→keypad gap 40 |
| PIN pad dots | 12 diameter, 12 gap, shrink floor 6, visible cap 32 |
| PIN pad backspace long-press | platform default long-press timeout (accepted divergence) |
| Pattern grid | 3 × 3; side ≤ 300; node hit factor 0.6; node 16 resting / 28 selected; line width 6 at 70 % alpha |
| Pattern midpoint table | the eight pairs in §2.4 |
| Carousel | card 256 × **424**, radius 24, spacing 12; thumbnail 126 × 224, radius 20, from a 360 × 640 canvas at scale 0.35. Peek is whatever centring leaves (see note) |
| Key press feedback / haptics | as calculator design §5.1–5.2 |
| Unlock reveal | 260 ms, unchanged; every other transition and every `disguiseEpoch` bump is a cut |

**Amended during implementation** (both platforms landed on the same values; recorded here so this table matches what shipped):

- **Card height 340 → 424.** The tallest card — pattern while it is *also* the current face, so it carries the "Current" badge, a three-line tagline, the identity grade **and** the screen-reader note — does not fit under the 224 thumbnail at 340, and both platforms use a fixed-height card that clips rather than grows. Measured from an on-device view dump those five text rows come to 387 px at density 2.625, so the card needs 250 + 147 + 16 ≈ 413; 424 leaves a working margin. (380 and 400 were both tried and both still clipped the badge case.) Android additionally caps the tagline at three lines with an ellipsis as a backstop for very large font scales. Width, spacing, radius and thumbnail geometry are unchanged.
- **The picker's primary action is pinned, not scrolled.** Card, disclosure and explainer together overflow a phone screen, so they scroll; with the button inside that scroll area it sat just past the bottom edge with nothing indicating more content, and the switch read as a dead end. The button now sits below the scroll area — a bottom inset on iOS, a non-scrolling row under a weighted scroll column on Android — so it is always visible. It still disables while the centred card is the current face.
- **Carousel thumbnails must escape their parent's constraints.** The thumbnail renders a real face into a 360 × 640 virtual canvas and scales it down. On Android a plain `size` modifier is only a preference and stays bounded by the 126 × 224 parent, so the face laid itself out for a 126 dp-wide screen and the calculator keypad computed a negative padding, which **crashed the app at launch**. Use `requiredSize`. The calculator's glyph-offset padding is now clamped at zero as well, so no render size can crash a lock face.
- **Peek is not a constant.** Centring the selected card (required for "the centered card is the selection") makes the peek a function of screen width — roughly 61 pt on a 402 pt-wide device rather than the 24 originally pinned. The 24 stood only as a hint that neighbours must be visible; that requirement stands, the exact number does not.
- **The guide playground is digits-only**, not the full 3×4 keypad: ⌫ and ✓ have no meaning in a pad that saves nothing. Both platforms render 1–9 plus a lone 0 and the shared Reset button.

---

## 9. Implementation sequence and acceptance

Both platforms complete a step before either starts the next; the app is shippable after every step.

1. **This document.** Written and reviewed before any face is built (the skeleton plan forbids building a face ahead of its spec).
2. **Host seam.** Contract types, registry with one entry, `TokenRecorder` with `removeLast`, `failedAttemptCount` replacing `shakeToken`, `calculatorEpoch → disguiseEpoch`. *Accept:* both suites pass with mechanical renames only; new recorder tests cover the whole §1.2 table, including sticky overflow through `removeLast` and the no-op on empty.
3. **Envelope v2.** *Accept:* (a) a v1 blob verifies the same code before and after rewrite; (b) salt and hash are byte-preserved; (c) a failed rewrite leaves a verifiable v1 blob; (d) an upgraded install unlocks first try, with no re-enrollment path reachable; (e) a version-3 envelope is rejected while `hasPasscode` stays true; (f, Android) a missing or tampered mirror falls back to the calculator and heals on the next unlock, and the nuke removes it; plus `activeDisguiseId` of a v1 envelope reads `"calculator"`. Manual: upgrade an iteration-2 install in place and unlock.
4. **Re-home the calculator.** *Accept:* the engine table passes unmodified; every pinned caption renders verbatim through the new mapping; an alphabet-drift test asserts the surface's emittable tokens equal the descriptor's 17; setup, unlock and change-passcode are behaviorally identical; backgrounding mid-entry while locked resumes pristine; the snapshot cover still shows the calculator from every tab; VerifyCurrent still shakes and the caption reverts on the next key.
5. **The two new faces.** *Accept:* alphabet-drift tests (10 and 9 tokens, no `|` in any ID); pattern reducer tests cover all eight midpoint pairs and their reverses, no-repeat, lift-with-no-nodes, cancel and the overflow-impossible property; PIN pad tests cover touch-down digits, backspace on release, long-press clear and the dot cap; host tests cover the full §1.1 pulse matrix, including that short and overflowed commits still skip the KDF. Manual: both lock screens shake and clear on a wrong entry with no text, unlock on the right one, leak no vault vocabulary, and read correctly under VoiceOver and TalkBack.
6. **Onboarding carousel.** *Accept:* finishing the guide with each of the three faces renders setup on that face; process death after the guide but before setup shows the guide again with the sentinel unwritten; revisit writes nothing and stays locked on the current face; string tests cover the renamed and retired IDs.
7. **Settings row and switch flow.** *Accept:* state-machine tests for every §5 edge — cancel intact at each phase, mismatch loops back to capture, a wrong verify pulses, the current face is unpickable, a write failure leaves the old envelope; after commit the old code no longer matches, `activeDisguiseId` is the new id and the Settings subtitle updates; a plain change preserves the face; Android writes blob and mirror in one transaction and resolves an induced conflict in the envelope's favour on unlock.
8. **Docs pass.** Apply §10 to the affected documents and add `iteration-3-manual-checks-{ios,android}.md`.

---

## 10. Statements superseded by this iteration

**`docs/plans/disguise-skeleton-plan.md`**
- §1 "a believable, fully functional decoy app surface" and §3.2a "genuinely functional instance of the app it pretends to be" → a disguise is a *lock face*; for overt faces the function is to be a credible native lock screen (§2.2).
- §2.3 flow order `PICK_DISGUISE → VERIFY_CURRENT → …` → `VERIFY_CURRENT → PICK → CAPTURE_NEW → CONFIRM_NEW` (§5); the explainer shows on the picker, after verification.
- §3.1 / §3.2b / §4.3 "the token stream is `token`, `commit`, `clear`; the enum has three cases; a fourth requires amending this document" → amended: `removeLast` added (§1.2).
- §3.2d "`Disguise` mode: nothing, ever … silent non-match, no error, no animation" → true for **covert** faces only; overt faces receive the failed-attempt pulse (§1.1), and the "nothing composed in the caption slot" rule becomes calculator-specific (overt faces show a static title).
- §3.2b / §3.3 reset guarantee → clarified: no re-instantiation on phase changes within a flow (§1.5).
- §6 "iteration 2 adds one row — Disguise — under the Appearance section" → "Change disguise" under **Security**, below Change passcode (§5).
- §6 first-run "runs on the default disguise" → runs on the face chosen in the carousel (§4).
- §7 "the believability spec … is written, reviewed and committed before a disguise is built" → satisfied for the PIN pad and pattern by §2 of this document.
- §10 Q1 "Tip calculator first" → superseded; iteration 3 ships the PIN pad and pattern.
- §10 Q3 "No pre-setup picker" → superseded; the guide's first page *is* the picker.
- §10 risk 2 "ship only identity-plausible disguises under the current identity" → superseded by the user's accepted trade-off, with the disclosure copy in §7.
- §4.1 / §4.2 "`CoverFace()` unused by the host on Android" → now used for carousel thumbnails on both platforms (§6).

**`docs/plans/idea-plan.md`**
- §1 "To anyone who opens it, it is a plain, fully working calculator" → "…it is whichever lock face the owner chose (calculator by default)".
- §2.3 "First run is the only time the lock screen may show non-calculator UI" → the lock screen is the enrolled face; pre-setup shows the guide with the carousel.
- §2.5 / §6 "pristine calculator" wording → "pristine resting face of the active disguise".
- §3.4 Settings layout reservation "Appearance: disguise themes" → the disguise row lives in Security; theming remains future work.
- §5 roadmap "Alternate disguises (unit converter, clock…)" → shipped as the PIN pad and pattern in iteration 3.

**`docs/plans/calculator-disguise-design.md`**
- §5.6 shake "VerifyCurrent only" → also the overt-face failure feedback in `disguise` mode (§2.2).
- §6 "The pure LOCKED state has zero strings beyond key labels" → calculator-only; the PIN pad and pattern render a static face title.
- §6 rule that caption copy names "press =" → generalized: each face names its own commit gesture.

**`docs/plans/iteration-2-decisions.md`**
- §1 `calculatorEpoch` and "the calculator fades out in place" → `disguiseEpoch`, "the lock face fades out in place".
- §5 revisit mode "renders the same four pages either way" → page 1 is the carousel locked on the current face; pages 3/4 are the current face's (§6).
- §10 `onboarding_page1_*`, `onboarding_page3_*`, `onboarding_page4_title/body` → retired or renamed per §7.
