# SafeBox — Idea Plan (Product Plan)

**Document:** `docs/plans/idea-plan.md`
**Status:** Source of truth for iteration 1. The iOS and Android plans must implement this document exactly; where they diverge for platform reasons, they must say so explicitly and link back to the section they diverge from.
**Last updated:** 2026-08

---

## 1. Concept & Positioning

SafeBox is a private vault app that hides in plain sight. To anyone who opens it, it is a plain, fully working calculator; to its owner, entering a secret sequence of calculator keys unlocks a local, offline vault containing private photos, notes, and contacts. SafeBox competes not on feature count but on the credibility of its disguise and the strength of its privacy posture: nothing leaves the device, nothing hints that a vault exists, and a wrong passcode produces no error — just arithmetic.

### Target users

- People who share or occasionally hand their phone to others (family, partners, colleagues) and want a private space that doesn't announce itself.
- People in situations where a visible "vault" or "locked" app would itself invite pressure to open it (the disguise is the feature, not just the lock).
- Privacy-conscious users who want photos/notes/contacts kept out of system apps, cloud sync, and backups.

### Guiding principles

1. **The disguise never breaks.** No error messages, no lock icons, no "vault" branding on the lock screen, no behavior a casual user of the calculator would find strange. Any feature that risks the disguise is rejected or redesigned.
2. **Privacy first.** All data is local, app-private, and offline. No accounts, no backend, no analytics, no third-party SDKs that phone home. Iteration 1 has zero network permissions/usage.
3. **Offline and honest.** The app never needs connectivity and never asks for it. Permissions are requested only at the moment they are needed (photo picker) and only in scope-minimal form.
4. **Fail closed.** Any ambiguity (backgrounding, crash, timeout) resolves to the locked/calculator state.

### Launcher identity (product decision, applies to both platforms)

The disguise identity is a **single product decision**, not a per-platform naming detail:

- **Display name: "Calculator+"** on both platforms (launcher label, store listing title, app switcher label).
- **Icon:** a neutral, original calculator glyph — must not imitate Apple's or Google's calculator icons or any trademark.
- **Identifiers:** bundle identifier / applicationId = **`com.calcplus.calculator`** on both platforms. Identifiers must not reference "safebox", the vault, or the owner; they are permanent once shipped, so this is decided now.
- **Rule:** every piece of externally visible metadata — name, icon, identifiers, launch screen, notification-free behavior, settings-app entry — must pass the *"looks like a calculator"* test. Anything a bystander (or the OS UI) can surface must read as a calculator.

(How the app is presented to *store reviewers* is deliberately different — see §7, Distribution & Store-Review Risk.)

---

## 2. The Calculator Lock

### 2.1 The calculator itself

- A standard 4-function calculator layout: digits `0–9`, `.`, operators `+ − × ÷`, `%`, `=`, `AC/C`, sign toggle `±`, and a result display.
- It must **actually calculate correctly**. A broken calculator breaks the disguise.
- The lock screen shows no branding beyond a calculator; app name/icon/identifiers per §1 (Launcher identity).

**Behavior specification.** "Correct" is pinned to a reference model: **both apps match iOS Calculator basic-mode semantics**, including:

- **Chaining:** `a op b op` evaluates the pending operation and carries the result (`2 + 3 ×` shows `5`, pending `× …`).
- **Operator replacement:** pressing a second operator before entering the next operand replaces the pending operator (`2 + ×` means `2 × …`).
- **Percent:** unary on a lone operand (`7 %` → `0.07`); binary in an additive context computes a percentage *of the first operand* (`7 + 7 %` → pending `7 + 0.49`), and in a multiplicative context scales (`8 × 50 %` → pending `8 × 0.5`).
- **Repeated `=`:** repeats the last binary operation with the last right-hand operand (`2 + 3 = =` → `8`).
- **`±` edge cases:** on a fresh/zero display, `±` shows `-0`; toggling twice returns to `0`; `±` applies to the displayed operand at any time.
- **`=` with no pending operation:** no-op on the display.
- **Division by zero:** display `Error`; the next digit or `AC` recovers.
- **Display precision/overflow:** up to 9 significant digits with rounding (so `0.1 + 0.2 =` displays `0.3`); larger magnitudes switch to scientific notation identically on both platforms.

**Shared input-sequence table.** Both apps must reproduce this table *exactly* (identical display strings); it is part of the Definition of Done (§8, criterion 1). Note the degenerate all-symbol sequences — symbol-heavy passcodes guarantee the engine will be fed operator streams, and "it never looks broken" depends on these:

| # | Key sequence | Display after |
|---|---|---|
| 1 | `2 + 3 =` | `5` |
| 2 | `2 + 3 = =` | `8` |
| 3 | `2 + × 3 =` | `6` |
| 4 | `7 + 7 % =` | `7.49` |
| 5 | `7 %` | `0.07` |
| 6 | `8 × 50 % =` | `4` |
| 7 | `2 × =` | `4` |
| 8 | `. 5 + . 5 =` | `1` |
| 9 | `8 ÷ 0 =` | `Error` |
| 10 | `0.1 + 0.2 =` | `0.3` |
| 11 | `=` (from clear) | `0` |
| 12 | `±` (from clear) | `-0` |
| 13 | `± ±` (from clear) | `0` |
| 14 | `± ± + % =` (all-symbol, degenerate) | `0` |
| 15 | `+ + = ` (all-symbol, degenerate) | `0` |
| 16 | `% =` (from clear) | `0` |
| 17 | `1 2 + 3 4 =` | `46` |

Row 17 doubles as the **accidental-unlock collision example**: if the owner's passcode were `1 2 + 3 4`, anyone computing 12+34 on the decoy calculator would unlock the vault (see §2.2, passcode-choice guidance).

### 2.2 Passcode definition

- A **passcode is an ordered sequence of calculator key identifiers**, committed with `=`.
- **Allowed keys in a passcode:** digits `0–9`, `.`, `+`, `−`, `×`, `÷`, `%`, `±`. Excluded: `=` (it is the commit gesture), `AC/C` (it is the "start over" gesture — pressing it clears the candidate buffer, in both calculator and passcode senses).
- **Canonical key IDs (shared by both platforms):** `D0…D9, DOT, ADD, SUB, MUL, DIV, PCT, SIGN`. Comparison is key-for-key and order-sensitive over these IDs.
- **Token serialization (shared):** the sequence is serialized as the canonical key IDs joined with `|` (e.g. `D1|D2|ADD|D3|D4`). This exact serialization is the input to hashing on both platforms.
- **Rules:** minimum **4** keys, maximum **32** keys. No hard composition requirements (a digits-only passcode is allowed), but:
  - Setup copy **encourages at least 6 keys including at least one symbol key** (`+ − × ÷ % ± .`) — this is the strength nudge.
  - Both platforms show a **soft warning on trivial sequences** (a single repeated key, e.g. `7 7 7 7`): a caption-level nudge, never a block.
  - Setup guidance notes the **accidental-unlock collision risk**: a passcode that is a plausible everyday arithmetic expression (like `1 2 + 3 4`) can be typed by an innocent calculator user and unlock the vault. Copy nudges toward non-obvious sequences (symbols, unusual patterns).

**Hashing scheme (pinned once, both platforms identical):**

- **PBKDF2-HMAC-SHA256, 600,000 iterations** (current OWASP figure for HMAC-SHA256), **16-byte random salt**, 256-bit output.
- Input = the `|`-joined canonical key-ID serialization above. The raw sequence is never persisted.
- Stored record: `{algo, version, iterations, salt, hash}` — the version tag and stored iteration count allow future parameter upgrades.
- Stored in platform secure storage: iOS Keychain with `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` (never restorable to another device); Android as a Keystore-AES/GCM-wrapped blob (platform plans specify mechanics and fallbacks).
- **Verification runs off the UI path.** On `=` in LOCKED, the arithmetic result renders immediately and *identically* for match and non-match; the KDF + comparison run asynchronously and, on match, fire the unlock transition. This resolves the deliberate-slow-KDF vs. no-behavioral-tell tension.
- **Constant-time comparison** of the derived hash against the stored hash on both platforms (naive byte-array equality is not constant-time).

**Honest threat framing (accepted risks, stated plainly):**

- The hash gate is a **UI lock, not encryption**. Iteration 1 vault data is protected by OS app-sandbox + file protection, not by the passcode (encryption-at-rest is iteration 2+, see §5).
- The passcode keyspace is small: 17 tokens, minimum length 4. A short digits-only code is **offline-crackable in seconds if the stored hash is extracted** (rooted/jailbroken device, forensic image), even at 600k iterations. The KDF raises the cost; it cannot fix low entropy. This is why setup copy pushes toward ≥6 keys with a symbol.
- **Silent unlimited guessing is an accepted iteration-1 risk**: there is no attempt counter or lockout, because any lockout behavior is a tell. The disguise itself is the control (an attacker doesn't know there is anything to guess). Break-in alerts are an iteration 2+ candidate.

### 2.3 First-run setup — state machine

First run is the only time the lock screen may show non-calculator UI, and even then it is minimal: a single unobtrusive banner/overlay line above the calculator. States:

```
[FRESH_INSTALL]
   └─ launch → SETUP_ENTRY
        banner: "Set your secret code: type it on the keypad, then press ="
        (sub-caption encourages ≥6 keys incl. a symbol; see §2.2)
        calculator still computes normally underneath

[SETUP_ENTRY]
   ├─ key presses (allowed keys) → appended to candidate buffer (and to the calc engine)
   ├─ 33rd key arrives → overflow flag set on the candidate buffer (calculator keeps working)
   ├─ AC pressed → candidate buffer + overflow flag cleared, stay in SETUP_ENTRY
   ├─ "=" pressed, buffer length < 4 → banner: "Too short — use at least 4 keys",
   │                                    buffer cleared, stay in SETUP_ENTRY
   ├─ "=" pressed, overflow flag set → banner: "Too long — start again (max 32 keys)",
   │                                    buffer + flag cleared, stay in SETUP_ENTRY
   └─ "=" pressed, buffer length 4–32, no overflow → hold candidate, go to SETUP_CONFIRM
        (trivial-sequence soft warning may show here as a caption; never blocks)

[SETUP_CONFIRM]
        banner: "Re-enter the same code, then press ="
   ├─ key presses → appended to confirm buffer (same 32-key overflow-flag rule)
   ├─ AC pressed → confirm buffer cleared, stay in SETUP_CONFIRM
   ├─ "=" pressed, confirm == candidate → hash & store passcode → UNLOCKED (first entry
   │        into the vault; show a one-time "remember: no recovery" notice — see 2.6)
   └─ "=" pressed, confirm != candidate (incl. overflowed confirm) →
            banner: "Codes didn't match — start again",
            both buffers cleared, → SETUP_ENTRY
```

Notes:
- Setup is not complete until confirm succeeds. Killing the app mid-setup returns to `SETUP_ENTRY` on next launch.
- **Backgrounding mid-setup (either state) discards both buffers and returns to `SETUP_ENTRY`** — fail closed, consistent with §1. No candidate survives a trip to the background.
- **Overflow semantics (shared by both platforms):** the buffer is capped at 32 keys; when a 33rd key arrives, an **overflow flag** is set (the calculator engine still processes every key). A commit of an overflowed buffer **never matches / is never accepted**: in setup it produces the "start again" banner; in LOCKED it silently doesn't match (§2.4). This replaces any "truncate and accept" behavior — a truncated buffer must not be a valid passcode.
- During setup, the **pending candidate is held in memory and compared as a plain sequence** at confirm time; the hash (with a fresh salt) is computed only once, on successful confirm. Salts are not needed for the in-memory setup comparison.

### 2.4 Unlock flow (subsequent launches)

```
[LOCKED]  ← the default state on every launch
        UI: pure calculator. No banner, no hint, nothing.
   ├─ key presses → appended to attempt buffer AND processed by the calc engine
   ├─ 33rd key since last AC/= → overflow flag set (calculator unaffected)
   ├─ AC pressed → attempt buffer + overflow flag cleared (and calculator cleared) —
   │        this is how a user recovers from a typo without any tell
   ├─ "=" pressed, buffer < 4 keys or overflow flag set → no verification is even
   │        attempted (skip the KDF); calculator shows the arithmetic result. Stay LOCKED.
   ├─ "=" pressed, attempt == stored passcode → attempt buffer cleared → UNLOCKED
   └─ "=" pressed, attempt != stored passcode → attempt buffer cleared,
            calculator simply shows the arithmetic result. No error, no delay,
            no animation. Stay in LOCKED.
```

- The attempt buffer records **every allowed key pressed since the last `AC` or `=`**, so the unlock gesture is: (from a clear state) type the code, press `=`.
- The attempt buffer is **capped at 32 keys with the same overflow-flag rule as setup** (§2.3): an overflowed attempt silently never matches. This bounds memory during long real calculations and keeps both platforms identical.
- On `=`, the arithmetic result renders immediately; verification runs off the UI path (§2.2) and renders identically for match/non-match until the unlock transition fires. Sub-minimum and overflowed commits skip the KDF entirely (no timing structure to observe — the visible behavior is already identical).
- Wrong entries are indistinguishable from calculator use. There is no attempt counter, no lockout, and no visual state change in iteration 1 (see §2.2, accepted risks; break-in alerts are an iteration 2+ feature).
- The attempt buffer lives only in memory and is cleared on commit, on `AC`, and on backgrounding. **On backgrounding while LOCKED, both the attempt buffer and the calculator display state are cleared** — a partially typed passcode must never be visible or resident when the app is resumed by someone else.

### 2.5 Re-lock model

There is exactly **one auto-lock model** in iteration 1: **background grace**. Foreground use never auto-locks — there is **no foreground idle timer** in iteration 1 (a documented simplification: reading a note or looking at a photo without touching the screen must not lock the vault).

The app transitions `UNLOCKED → LOCKED` (calculator shown) when:

1. **Backgrounding beyond the grace period:** when the app leaves the foreground, a grace timer starts. If time-out-of-foreground exceeds the Auto-lock setting, the app is locked (at expiry or on return, whichever the platform can enforce — fail closed: if in doubt, locked). Settings options: **Immediately / 1 minute / 5 minutes — default: Immediately.** "Never" is not offered in iteration 1. Returning within the window keeps the vault open.
   - Elapsed background time is measured with **monotonic clocks** (Android `elapsedRealtime`, iOS `systemUptime`-equivalents) — never wall-clock time, which the user can change. If the monotonic reading is unavailable or inconsistent (e.g. reboot), **fail closed: lock.**
2. **Manual lock:** a **"Lock now"** action in Settings on **both platforms** (platform plans may add an additional affordance, but Settings → Lock now is mandatory and identical).
3. **Process death / crash / device restart:** by construction — locked is the launch default.

On **every** lock transition, the calculator display state and the attempt buffer are cleared (so the resumed lock screen is a pristine calculator).

Snapshot protection is a separate, always-on mechanism independent of the lock decision — see §6 (the app-switcher image must never show vault content even when the grace period keeps the vault unlocked).

#### 2.5.1 Auto-lock suppression for app-initiated system UI

Backgrounding-to-lock collides with system UI the app itself launches: the system photo picker (Android Photo Picker fully backgrounds the app; iOS presentation styles and permission sheets resign active). Without an exemption, every photo import with the default "Immediately" setting would lock the vault mid-flow. Therefore:

- When the app **initiates** a system presentation — the photo picker, or an OS permission dialog — it sets an **in-flight flag** before launching it. While the flag is set, the background-lock trigger is **suspended** for that round-trip.
- **Hard cap:** the exemption lasts at most **2 minutes of backgrounded time** (measured on the monotonic clock; same value on both platforms). If the app remains backgrounded beyond the cap — the user wandered off inside the picker or switched apps — the app locks anyway. Fail closed.
- The flag is cleared when the presentation returns its result (or is cancelled).
- **If the lock happened anyway** (cap exceeded, or process death) while a picker result was pending: the **import still completes at the repository level**, keyed by the target album id — the copy-into-vault operation must not depend on vault UI being composed. The user returns to the calculator; after re-unlocking, the imported photos are in the album. No vault UI, data, or confirmation is ever shown while locked.
- The exemption list is closed: **only app-initiated pickers and permission dialogs**. User-initiated backgrounding (home, app switcher, incoming call takeover) always follows the normal grace model. Both platform plans must enumerate exactly which presentations they exempt.

### 2.6 Forgotten passcode — iteration 1 policy

**Decision: no recovery.** There is no "forgot passcode" path, no security questions, no email reset (there are no accounts).

- **Why:** any recovery path is a bypass path, and any visible recovery affordance breaks the disguise. A calculator with a "forgot passcode?" button is not a calculator.
- **Tradeoff, documented:** a user who forgets their code permanently loses access to their vault data. This is stated plainly during setup (the one-time notice after confirm: *"There is no way to recover this code. If you forget it, your vault contents cannot be retrieved."*) and repeated in Settings → About.
- **Escape hatch:** the standard OS mechanism — uninstall/reinstall (or clear app data on Android) — wipes everything and returns to `FRESH_INSTALL`. This is wipe-and-restart, not recovery, and needs no in-app UI.

**Fresh-install correctness (mechanism, because it affects the data model):**

- **Android:** app-private storage and Keystore keys are removed with the app; nothing survives uninstall. Clear-app-data likewise returns to `FRESH_INSTALL`.
- **iOS:** Keychain items **survive uninstall** by default, so passcode presence alone cannot define setup state — a reinstalled app would boot LOCKED against a stale hash. The mechanism is an **install sentinel**: a marker in app-container storage (UserDefaults), which *is* wiped on uninstall. On every launch, **if the sentinel is absent, delete all SafeBox Keychain items and enter `FRESH_INSTALL`** (then write the sentinel). Thus `isSetupComplete` = *passcode present in Keychain AND install sentinel present* (see §4, AppSettings). Additionally, all SafeBox Keychain items use **`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`** so the hash never restores to another device via backup.

### 2.7 Changing the passcode

Available only from inside the vault (Settings → Change passcode), reusing the calculator keypad. Explicit state machine, identical on both platforms:

```
[VERIFY_CURRENT]
        caption: "Enter your current code, then press ="
   ├─ "=" with matching current code → ENTER_NEW
   ├─ "=" with non-matching code → visible error ("Incorrect code — try again"),
   │        buffer cleared, stay in VERIFY_CURRENT. Unlimited retries in iteration 1.
   └─ Cancel → back to Settings, nothing changed

[ENTER_NEW]
        caption: "Enter your new code, then press ="
        (same rules as SETUP_ENTRY: 4–32 keys, overflow flag, AC clears,
         strength nudge and trivial-sequence warning apply)
   └─ valid commit → CONFIRM_NEW

[CONFIRM_NEW]
        caption: "Re-enter the new code, then press ="
   ├─ match → new hash (fresh salt) stored atomically replacing the old → back to Settings
   │        with a confirmation; the old code stops unlocking immediately
   └─ mismatch → visible "Codes didn't match — start again" → ENTER_NEW
```

**Silence is a disguise feature only on the lock screen.** Inside the vault the user is already authenticated, so wrong-current-code produces *visible* feedback — a silent, unresponsive change-passcode screen would just be broken UX. Unlimited retries in iteration 1 (the user is already inside; a limit adds nothing). Backgrounding mid-flow discards all buffers and abandons the change (normal re-lock rules of §2.5 apply).

---

## 3. Feature Spec per Tab

After unlock, a standard tab bar with four tabs: **Gallery, Notes, Contacts, Settings**. All screens below are iteration-1 scope unless marked otherwise.

### 3.1 Gallery

**Screens**

1. **Albums list** — a **card grid on both platforms**: each card shows a cover thumbnail, album name, and photo count. The **cover is derived: the first photo in the album by `sortIndex`** (no stored cover id — nothing to dangle when photos are deleted or moved); an empty album shows a placeholder card. Albums are ordered by their own `sortIndex`. Actions: create album (name prompt), rename, delete (with confirmation: "Delete album and its N photos? This cannot be undone.").
2. **Photo grid** (inside an album) — 3-column-ish adaptive thumbnail grid, ordered by photo `sortIndex` (import order). Actions: import photos (opens the system photo picker, multi-select), select mode (multi-select → delete, move to another album), tap → photo detail.
3. **Photo detail** — full-screen viewer; swipe left/right between photos in the album; zoom with **shared constants on both platforms: double-tap toggles 1× ↔ 2.5×, pinch zoom up to 5× max**, pan clamped to image bounds, zoom resets when swiping to another photo; actions: delete (confirm), move to album. (Share/export is explicitly **out** of iteration 1 — exporting is a data-egress feature that needs its own design.)

**Import & media policy (shared by both platforms)**

- Import uses the OS scope-minimal picker (iOS `PhotosPicker`, Android Photo Picker) so no broad photo-library permission is requested. The auto-lock suppression of §2.5.1 applies to the picker round-trip.
- **Still images only** in iteration 1 — the picker is filtered to images; **no video**.
- **Original bytes are preserved byte-for-byte** (no re-encoding, no recompression), stored under the file's **real extension** (see Photo.fileName, §4). **HEIC is stored as-is** (both target OSes decode it). **Live Photos import the still image** only. GIFs, if selected, are stored byte-for-byte like any image; animated playback is not guaranteed in iteration 1 (the grid/viewer may show the first frame).
- **EXIF (including location) is retained** with the original bytes in iteration 1 — preserving originals is the contract. An optional **"strip metadata on import" is an iteration-2 candidate**, noted here so the asymmetric privacy tradeoff is a recorded decision, not an accident.
- Imported photos are **copied** into app-private storage (originals stay in the system library; SafeBox does not delete from the system library in iteration 1 — auto-deleting originals requires broader permissions and its own UX, deferred to iteration 2+).
- **Both platforms generate a downsampled thumbnail file at import time**, stored alongside the original, for fast grids.

**Deletion (rows AND files)**

- Deleting a photo deletes its database row **and** its full-size file **and** its thumbnail file.
- Deleting an album enumerates its photos and deletes their files **before/alongside** the row cascade — a DB-only cascade would silently orphan bytes on disk, which for a vault means "deleted" photos linger.
- As a backstop only, a **startup orphan sweep** removes any vault files with no referencing row (covers crash-between-file-and-row windows). "**No orphan files**" is a Definition-of-Done item (§8).

**Empty states**

- No albums: illustration/text "No albums yet" + prominent "Create album" button.
- Empty album: "No photos yet" + prominent "Import photos" button.

### 3.2 Notes

**Screens**

1. **Notes list** — Apple Notes-like rows: **derived title**, **snippet**, **date** (modified date, relative formatting: "14:32", "Yesterday", "Aug 12"). Sorted by modified date, newest first. Search field filtering by **title + body** text; tag chips or a tag filter to show only notes carrying a tag (tag filtering is **firmly iteration-1 scope on both platforms**). **Swipe-to-delete with confirmation; no undo** (identical on both platforms — undo is a possible later addition, but only ever on both at once). "New note" button.
2. **Note editor** — full-screen editor over the **markdown body; there is no separate title field** (Apple-Notes model: the first line *is* the title). Tag entry: add/remove tags on the note (autocomplete against existing tags; creating a new tag inline). Autosave per the contract below — no explicit save button. Delete from within the editor (confirm).

**Derived title & snippet (shared derivation rules — the note model is derived-title, §4):**

- **title** = first non-empty line of `body`, **markdown-stripped** (heading markers, emphasis markers, code backticks, list/checklist markers removed). Empty body ⇒ display "New note" in the list.
- **snippet** = the following non-empty lines (title line **excluded**), markdown-stripped, up to roughly two list-row lines.
- Both are **recomputed on every save and stored denormalized** on the Note row for fast list rendering (§4). Derivation rules are identical across platforms; shared examples:

| body (raw markdown) | derived title | derived snippet |
|---|---|---|
| `# Shopping`↵`- milk`↵`- eggs` | `Shopping` | `milk eggs` |
| `**Bold** start`↵↵`second para` | `Bold start` | `second para` |
| `- [ ] pack bags`↵`- [x] tickets` | `pack bags` | `tickets` |
| `` `config` notes `` | `config notes` | *(empty)* |
| *(empty body)* | *(list shows "New note")* | *(empty)* |

**Markdown subset (shared, both platforms render the same set):** headings, bold/italic, inline code, bullet and numbered lists, and **checklists (`- [ ]` / `- [x]`) rendered as non-interactive styled text** — tap-to-toggle checkboxes are an iteration-2 candidate, not iteration 1, on either platform. Platform plans may choose live styled editing or an edit-raw/preview toggle, but must support exactly this subset; renderers richer than the subset must be constrained/themed down to it so the same note looks equivalent on both platforms.

**Autosave contract (shared, concrete):** edits are persisted within **1 second of the last keystroke** (debounce) **and synchronously flushed on editor exit and on app backgrounding** — the flush must not rely on the debounce timer surviving the transition. Killing the app mid-edit loses at most the final sub-second of typing.

**Empty states**

- No notes: "No notes yet" + "New note" button.
- Search/tag filter with no matches: "No results".

### 3.3 Contacts

**Screens**

1. **Contacts list** — alphabetical with section headers and an index (A–Z + `#`) where the platform supports it; search field; "Add contact" button. Swipe/long-press delete (confirm).
2. **Contact detail** — read view showing all filled fields. **No system handoff in iteration 1**: tapping a phone/email does **not** open the dialer or mail composer (that leaks vault contact data into system apps and their logs; revisit in iteration 2+ as an explicit user choice). Instead, **long-press on a phone/email/address value copies it to the clipboard** — with the caveat, noted here as a known limitation, that the OS clipboard is itself readable by other apps / synced on some platforms; users copy at their own initiative. Edit button → edit mode.
3. **Contact edit/create** — form: first name, last name, organization, **phone numbers (multiple, with labels: mobile/home/work/other), emails (multiple, with labels)**, address, notes field. Save / cancel; delete inside edit for an existing contact. Validation: **at least one of first name / last name / organization** is required (organization-only contacts are allowed — real contact books allow them).

**Display name & sort key (shared derivation, both platforms):**

- displayName = "First Last" (whichever parts exist, joined); if both names are empty ⇒ organization.
- **Sort key = familyName-first**: lastName, falling back to firstName, falling back to organization. Sorting is case- and diacritic-insensitive.
- Entries whose sort key does not start with a letter go into a trailing **`#` bucket**.
- **Search matches name + organization + phone + email** (same field set on both platforms).

Full CRUD, all local. **No import from system contacts in iteration 1** (requires contacts permission and an import UX; iteration 2+ candidate).

**Empty states**

- No contacts: "No contacts yet" + "Add contact" button.
- Search with no matches: "No results".

### 3.4 Settings

**Iteration-1 items (exactly these four):**

1. **Change passcode** — flow per §2.7 (VerifyCurrent → EnterNew → Confirm, on a calculator keypad, with visible wrong-current-code feedback).
2. **Auto-lock** — picker: **Immediately (default) / 1 minute / 5 minutes** of backgrounded time before lock (§2.5).
3. **Lock now** — immediate manual lock (§2.5).
4. **About** — app version/build, a short privacy statement ("All data stays on this device. SafeBox has no servers and sends nothing anywhere."), a short how-it-works blurb, and the no-recovery warning restated. (Android additionally lists open-source licenses for its third-party libraries; iOS iteration 1 has none — a noted, accepted asymmetry.)

**No biometric row in iteration 1** — not as a toggle, not as "coming soon" (dead UI is odd product surface and pre-commits an unresolved design; see §5 for the recorded scope decision and open questions).

**Layout requirement:** grouped-list structure with room for future sections (Security: biometric unlock, decoy passcode, break-in alerts; Appearance: disguise themes; Data: export/backup). These appear in this plan only so both platforms leave layout room; none are built in iteration 1.

---

## 4. Domain Model

Platform-neutral entities; **this section is authoritative** — platform plans map it to SwiftData and Room respectively without adding/removing iteration-1 fields. All IDs are UUIDs generated on-device. All timestamps are stored as UTC instants.

### Album
| Field | Type | Iteration |
|---|---|---|
| id | UUID | 1 |
| name | string | 1 |
| createdAt | timestamp | 1 |
| sortIndex | int (album ordering) | 1 |

**No `coverPhotoId` column.** The album cover is **derived: first photo by `sortIndex`** — a stored cover id dangles when the cover photo is deleted or moved, and iteration 1 needs no override. (An explicit cover override is a future field.)

Relationship: Album 1—* Photo. Deleting an album deletes its photos — **rows via cascade AND their files** (full-size + thumbnails) via repository enumeration, per §3.1, after user confirmation.

### Photo
| Field | Type | Iteration |
|---|---|---|
| id | UUID | 1 |
| albumId | UUID (FK → Album, **required**, cascade) | 1 |
| fileName | string (relative path in app-private storage, **real extension**: `.heic`, `.jpg`, `.png`, `.gif`) | 1 |
| thumbFileName | string (thumbnail generated at import, both platforms) | 1 |
| mimeType | string | 1 |
| width / height | int | 1 |
| byteCount | int64 | 1 |
| importedAt | timestamp | 1 |
| sortIndex | int (import order; grid ordering key) | 1 |
| isFavorite | bool | future |
| caption | string? | future |

Every photo belongs to an album (`albumId` is a required FK — album-less photos would be unreachable orphans since all UI is album-scoped). Photo **bytes live on the filesystem** (app-private, excluded from cloud/device backups — see §6); the database stores metadata + relative paths only. Original bytes are preserved; see §3.1 media policy.

### Note
| Field | Type | Iteration |
|---|---|---|
| id | UUID | 1 |
| body | string (markdown source — the single source of truth for content) | 1 |
| title | string (**derived, denormalized**: first non-empty line of body, markdown-stripped; recomputed on every save) | 1 |
| snippet | string (**derived, denormalized**: following lines, title excluded, markdown-stripped; recomputed on every save) | 1 |
| createdAt / updatedAt | timestamp | 1 |
| isPinned | bool | future |

The **derived-title model** (§3.2) is the product decision: there is no user-editable title field; `title`/`snippet` are cached projections of `body`, stored for list performance, never edited directly, and always recomputed on save (so they can never desync beyond an in-progress edit).

Relationship: Note *—* Tag via NoteTag.

### Tag
| Field | Type | Iteration |
|---|---|---|
| id | UUID | 1 |
| name | string (unique, case-insensitive) | 1 |
| colorIndex | int (index into a shared tag palette; same palette semantics on both platforms) | 1 |

### NoteTag (join)
| Field | Type | Iteration |
|---|---|---|
| noteId | UUID (FK) | 1 |
| tagId | UUID (FK) | 1 |

Deleting a note removes its NoteTag rows; a tag with zero notes is kept (simplest; tag management surfaced later).

### Contact
| Field | Type | Iteration |
|---|---|---|
| id | UUID | 1 |
| firstName / lastName | string? | 1 |
| organization | string? | 1 |
| phones | list of {label, value} (labels: mobile/home/work/other) | 1 |
| emails | list of {label, value} | 1 |
| address | string? | 1 |
| notes | string? | 1 |
| createdAt / updatedAt | timestamp | 1 |
| avatarFileName | string? (photo in app-private storage) | future |
| birthday | date | future |

Validation: **at least one of firstName / lastName / organization** is required. Display name and sort key are derived per §3.3. Phones/emails may be modeled as child tables or embedded/encoded lists per platform ORM idiom; behavior (multiple entries, labeled, ordered) must match.

### VaultSecurity (secure storage, not the database)
| Field | Where | Iteration |
|---|---|---|
| passcode record `{algo, version, iterations, salt, hash}` | Keychain (iOS, ThisDeviceOnly) / Keystore-wrapped blob (Android) | 1 |
| decoyPasscodeHash | same | future |
| biometricEnabled | same | future (iteration 2, with the feature) |

Parameters per §2.2: PBKDF2-HMAC-SHA256, 600,000 iterations, 16-byte salt, over the `|`-joined canonical key-ID serialization.

### AppSettings (database or platform preferences)
| Field | Iteration |
|---|---|
| autoLockOption (enum: immediately (default), 1min, 5min) | 1 |
| installSentinel (iOS only: app-container marker; absence at launch ⇒ wipe SafeBox Keychain items ⇒ `FRESH_INSTALL` — see §2.6) | 1 |
| isSetupComplete (**derived, never stored as an independent flag**: passcode record present AND — on iOS — install sentinel present) | 1 |
| disguiseTheme (enum) | future |

---

## 5. Iteration Roadmap

### Iteration 1 — the skeleton with full local persistence (THIS PLAN)

**In scope:**
- Fully functional calculator lock screen with the exact state machines and behavior spec of §2 (calculator semantics + shared sequence table, setup with overflow/backgrounding rules, silent unlock, single background-grace re-lock model with picker suppression, change-passcode flow, no recovery).
- Passcode stored per the pinned scheme (§2.2) in Keychain / Keystore-wrapped storage, with the iOS install sentinel (§2.6).
- Gallery: albums CRUD (card grid, derived covers), photo import via system picker into app-private storage (still images, original bytes, thumbnails at import), grid, full-screen viewer with swipe + shared zoom constants, delete/move with file deletion + orphan sweep.
- Notes: list with derived title/snippet/date, search (title+body), markdown editor (shared subset, checklists as styled text), tags with colorIndex + tag filtering, 1 s autosave + flush-on-exit, CRUD (swipe-delete with confirm, no undo).
- Contacts: alphabetical searchable list (familyName-first, `#` bucket), detail with long-press copy (no tel:/mailto handoff), full CRUD with multiple labeled phones/emails, address, organization-only contacts.
- Settings: Change passcode, Auto-lock (Immediately/1 min/5 min), Lock now, About.
- Real local database (SwiftData / Room), app-private file storage for photos, snapshot protection (§6), backup exclusion (§6), no-logging rule (§6).

**Explicitly OUT of scope for iteration 1 (do not build, do not partially build):**
- Encryption-at-rest beyond OS file protection (SQLCipher-style DB encryption, per-file photo encryption) — iteration 2+.
- **Biometric unlock (Face ID / fingerprint) — iteration 2.** *Recorded scope decision:* the original iteration-1 sketch listed a biometric toggle in Settings; it is deliberately moved out of iteration 1 (this document is the sign-off). Reasons: (a) the disguise question is unresolved — an **automatic biometric prompt on foregrounding** announces the app is not a calculator, while a **hidden manual trigger** (e.g. long-press on the display) preserves the disguise but is undiscoverable; the two drafts diverged on exactly this, and shipping either without deciding cements the wrong UX; (b) a dead "coming soon" toggle is poor product surface. **Open questions carried to iteration 2:** auto-prompt (opt-in, disclosed) vs hidden gesture; behavior on biometric failure/cancel (must stay silent calculator); availability gating and toggle visibility on devices without enrolled biometrics; platform authenticator-class constraints. No biometric UI of any kind ships in iteration 1.
- Foreground idle auto-lock timer — iteration 2 candidate (§2.5 documents the simplification).
- EXIF/metadata stripping on import — iteration 2 option (§3.1).
- Interactive (tappable) note checklists — iteration 2 (§3.2).
- Decoy passcode (second code opening a fake/secondary vault) — iteration 2+.
- Break-in alerts / intruder selfie / failed-attempt logging — iteration 2+.
- Alternate disguises (unit converter, clock, etc.) and disguise theming — iteration 2+.
- Import/export, sharing out of the vault, "delete original after import" — iteration 2+.
- Cloud backup/sync of any kind — iteration 3+ at earliest, and only with end-to-end encryption.
- System contacts import; dial/email handoff from contact detail — iteration 2+.
- iPad/tablet-optimized layouts beyond "does not crash, is usable".

### Iteration 2+ candidates (for planning awareness only)
Biometrics (with the open questions above); encryption-at-rest; decoy passcode; break-in photo/alerts; disguise themes/alternates; export/import; delete-original-after-import; metadata stripping; interactive checklists; note-delete undo (both platforms together); contacts integration + dial/mail handoff; localization.

---

## 6. Non-Functional Requirements

**Privacy posture**
- Zero network usage. Iteration 1 builds must declare/request no network-dependent capabilities and make no outbound requests. No analytics, crash reporting, or third-party SDKs with network behavior.
- No data leaves the device. Photos, database, and preferences live in app-private storage.

**Backup & at-rest protection — separate, mandatory mechanisms per platform:**
- **iOS — two distinct requirements, not one:**
  1. **Backup exclusion:** `isExcludedFromBackup` set on the SafeBox container/files (this — and only this — controls iCloud/iTunes backup propagation).
  2. **At-rest file protection (separate mechanism):** an NSFileProtection class — `.completeUnlessOpen` — applied **per-file at write time** to photo files and the SQLite store including its `-wal`/`-shm` sidecars (directory attributes don't reliably propagate; `.complete` would break writes that land after device lock, e.g. a flushed autosave on backgrounding).
- **Android — two distinct requirements, not one:**
  1. `android:allowBackup="false"` (cloud backup), **and**
  2. `android:dataExtractionRules` excluding vault data (the API 31+ device-to-device transfer path, which `allowBackup` alone does not govern).
- Rationale: iteration 1 data is not encrypted at rest beyond OS protection, so it must not propagate to backups or device transfers.

**Snapshot/app-switcher protection — per-platform mechanisms, documented as a deliberate divergence:**
- **iOS:** a **calculator-face (or opaque) cover view installed in `willResignActive`** and removed on `didBecomeActive` — *independent of the lock decision* (it must also cover Control Center pulls, notification shade, incoming-call banners, and the grace window where the vault stays unlocked). iOS **cannot block screenshots**; a user-taken screenshot of on-screen vault content is accepted as out of scope.
- **Android:** **unconditional activity-level `FLAG_SECURE`** — screenshots and screen recording are blocked app-wide, *including the calculator*, and the recents card is blank. The blank recents card is a mild tell and is **accepted**: per-screen toggling of `FLAG_SECURE` is racy (the recents thumbnail is captured around `onPause`, often before any recomposition lands) and an unreliable disguise is worse than a blank card.
- Verifying the app-switcher/recents image after backgrounding from every tab is a DoD item (§8).

**No-logging rule (both platforms):** never log key tokens, key buffers, candidate/attempt sequences, salts, hashes, or lock-state internals — at any log level. Release builds strip debug logging entirely (platform plans specify the mechanism). Lock internals are also excluded from crash metadata (there is no crash reporting in iteration 1 anyway).

- Photo picker only — never broad photo-library, contacts, camera, or location permissions in iteration 1.
- Security posture and accepted risks: see §2.2 (UI lock not encryption; low-entropy offline crackability if extracted; silent unlimited guessing accepted).

**Performance targets (both platforms)**
- Cold launch to interactive calculator: ≤ 1.5 s on a mid-range device (~3-year-old hardware).
- Unlock (`=` press with correct code → vault visible): ≤ 300 ms perceived.
- Calculator key response: ≤ 50 ms — it must feel like a real calculator. (Achieved together with the 600k-iteration KDF by running verification off the UI path, §2.2 — key handling and the arithmetic result never wait on the KDF.)
- Photo grid: smooth scrolling (no visible hitching) with 1,000+ photos in an album, via stored thumbnails and lazy loading.
- Photo import: 20 photos import without blocking the UI; progress indication for larger batches.
- Notes list/search responsive with 500+ notes.

**Accessibility — and its tension with the disguise**
- The vault (post-unlock) is held to normal accessibility standards: dynamic type, screen-reader labels, contrast, touch targets.
- The calculator lock screen is accessible **as a calculator**: keys have proper labels ("plus", "equals"), it works with screen readers and large text. However, its accessibility metadata must never reveal the vault — no hints, no accessibility identifiers containing "passcode"/"vault"/"unlock", no announcement on unlock-relevant state. This is a deliberate, documented tension: we make the calculator genuinely accessible while keeping the secret function undiscoverable through assistive tooling. A screen-reader user can still unlock (the keys are labeled and `=` commits); we do not add any unlock-specific assistive affordance.
- Passcode entry via symbols may be harder for some users; digits-only passcodes remain fully supported for this reason.

**Reliability**
- Fail closed (§1, §2.5). The notes autosave contract (§3.2) means no data loss on backgrounding/kill beyond the final sub-second of typing. Photo import is atomic per photo (a failed import leaves no orphan DB row or orphan file); deletion removes files with rows, with the startup orphan sweep as backstop (§3.1).

---

## 7. Distribution & Store-Review Risk

Shipping a disguised app to the stores is a launch-blocking policy question, not a naming detail. Position, recorded here as the product decision:

- **The disguise is from bystanders, not from the store reviewer.** SafeBox does not attempt to sneak past review as "just a calculator".
- **Apple — App Review Guideline 2.3.1** (hidden, dormant, or undocumented features) is the direct exposure: "calculator that is secretly a vault" is the canonical category this guideline polices. Mitigation: the **App Store listing (description and screenshots) openly discloses the vault functionality** ("a private photo/notes/contacts vault behind a calculator lock screen"); the **App Review notes include a demo passcode** and step-by-step unlock instructions so the reviewer can exercise the full app. The iOS plan must include preparing these notes as a release-checklist item.
- **Google Play — deceptive-behavior / hidden-functionality policies** are the analogous exposure. Same mitigation: the Play listing discloses the vault; review/testing notes include a demo passcode.
- The app's *on-device* surface still passes the looks-like-a-calculator test (§1) — disclosure lives in the store listing, which a bystander holding the phone never sees. Residual risk: a reviewer may still judge the disguise itself as deceptive; this is an **accepted, monitored launch risk**, and if rejection occurs the fallback conversation (e.g. more prominent in-app disclosure at setup) happens then — not preemptively at the cost of the disguise.
- If the product owner ever wants a fully undisclosed listing, that is a materially higher rejection risk and must be recorded here as a deliberate reversal before any submission.

---

## 8. Success Criteria — Definition of Done for Iteration 1

A platform build is DONE when every item below passes on a physical device:

**Disguise & lock**
1. Fresh install launches directly into a working calculator with the setup banner; the calculator matches the §2.1 behavior spec, and **reproduces the §2.1 shared input-sequence table exactly** (all 17 rows, including the degenerate all-symbol sequences), with identical display strings on both platforms.
2. First-run setup follows §2.3 exactly: too-short rejection, **>32-key overflow rejection with the "start again" treatment**, confirm mismatch returns to entry, **backgrounding mid-setup discards both buffers and returns to entry**, successful confirm shows the one-time no-recovery notice and enters the vault. The ≥6-keys-with-a-symbol nudge and the trivial-sequence soft warning appear per §2.2.
3. After setup, every subsequent launch shows a pure calculator with no banner or any vault indication.
4. Entering the stored key sequence and pressing `=` unlocks in ≤ 300 ms; any other committed sequence — including sub-4-key and **overflowed (>32-key) attempts, which silently never match** — just shows the arithmetic result with zero behavioral tells (no delay difference, no flicker; verification runs off the UI path).
5. A passcode containing at least one symbol key (e.g. `7 + 7 %`) sets, confirms, and unlocks correctly.
6. **Auto-lock follows the single background-grace model of §2.5:** with the default "Immediately", backgrounding and returning shows the calculator; with "1 min"/"5 min", returning within the window keeps the vault open and returning after it shows the calculator; elapsed time is measured with a monotonic clock (changing the wall clock in the background does not defeat it); foreground use never auto-locks. Force-quit → relaunch is locked. Locking clears the calculator display and attempt buffer.
7. **The app-switcher/recents image never shows vault content, verified after backgrounding from every tab** (iOS: cover view from `willResignActive`; Android: blank recents via activity-level `FLAG_SECURE`) — per §6, including during an unlocked grace window.
8. **Picker round-trip does not break the vault (§2.5.1):** starting a photo import with Auto-lock = Immediately, picking photos, and returning lands back in the vault with the import completed; if the suppression cap is exceeded and the app locked, the import still completes at the repository level and the photos are present after re-unlock, with no vault UI shown while locked.
9. The passcode is not recoverable from the app bundle, database, preferences files, or logs (stored only per the §2.2 pinned scheme: PBKDF2-HMAC-SHA256/600k/16-byte salt in Keychain/Keystore-wrapped storage; no lock internals in any log); uninstall → reinstall lands in `FRESH_INSTALL` with no stale unlock possible (iOS: install-sentinel wipe of Keychain items, §2.6).

**Gallery**
10. Create, rename, and delete (with confirm) albums; the album list is a **card grid with derived cover (first photo by sortIndex), name, and count**; empty states render per §3.1.
11. Import multiple photos via the system scope-minimal picker into an album; **still images only; original bytes preserved byte-for-byte with real extensions (HEIC as-is, Live Photo imports the still)**; thumbnails generated at import; system library originals are untouched.
12. Grid scrolls smoothly with 1,000 photos; detail view supports swipe-between and zoom with the shared constants (double-tap 2.5×, pinch max 5×, pan clamped, zoom resets on page change); delete and move-to-album work.
13. **No orphan files:** deleting photos and deleting an album remove full-size and thumbnail files along with rows; after any delete (and after a simulated crash mid-import followed by relaunch), the vault directory contains no files without a referencing row.

**Notes**
14. Create a note; list row shows the **derived title and snippet per the §3.2 rules (matching the shared example table)** and relative date, sorted by modified date.
15. Editor supports the shared markdown subset (headings, bold/italic, inline code, bullet/numbered lists, **checklists as non-interactive styled text**) and honors the autosave contract: persisted within 1 s of the last keystroke and flushed synchronously on exit/backgrounding; killing the app mid-edit loses at most the final sub-second of typing.
16. Add/remove tags with autocomplete; tags carry a colorIndex rendered from the shared palette; filter list by tag; search matches title + body. Swipe-to-delete asks for confirmation; there is no undo.

**Contacts**
17. Full CRUD with multiple labeled phones and emails, address, and organization; an organization-only contact can be created and displays/sorts per the §3.3 derivation rules; list is alphabetical familyName-first with a `#` bucket; search matches name + organization + phone + email; delete requires confirmation.
18. Tapping phone/email performs **no** dial/mail handoff; long-press copies the value to the clipboard.

**Settings**
19. Change passcode follows §2.7: wrong current code shows a **visible** "incorrect" error with unlimited retries; new-code entry enforces the same rules as setup; on success the old code stops unlocking immediately.
20. Auto-lock options (Immediately default / 1 min / 5 min) function as configured; Lock Now locks instantly; About shows version, the privacy + no-recovery statements, and the how-it-works blurb. **No biometric UI is present anywhere.**

**Non-functional**
21. All data persists across app restarts and device reboots (real database + files, not memory).
22. The build makes zero network requests (verified by proxy/inspection) and requests no permissions beyond the photo picker flow.
23. Backup/at-rest posture per §6, both mechanisms per platform verified: iOS `isExcludedFromBackup` **and** `.completeUnlessOpen` file protection (incl. `-wal`/`-shm`); Android `allowBackup="false"` **and** `dataExtractionRules`. On Android, screenshots are blocked app-wide by activity-level `FLAG_SECURE`.
24. Calculator lock screen is operable via the platform screen reader with calculator-appropriate labels and no vault-revealing metadata.
25. Cold launch ≤ 1.5 s and calculator key response ≤ 50 ms on a mid-range test device.
26. All externally visible metadata passes the looks-like-a-calculator test (§1): display name "Calculator+", neutral icon, identifier `com.calcplus.calculator`; store submission materials follow §7 (listing discloses the vault; review notes include a demo passcode).

Both platform teams check off this same list; any deviation is recorded in the platform plan with a reference to the section it deviates from.
