# SafeBox — Android Plan (iteration 1)

**Document:** `docs/plans/android-plan.md`
**Scope:** How to build the iteration-1 SafeBox Android app under `android/`. Product behavior (calculator disguise, passcode-as-key-sequence, four tabs, full local persistence) is defined in the **idea plan** (`docs/plans/idea-plan.md`), which is the source of truth for behavior and the domain model; this document covers the Android implementation only. Where this plan deliberately diverges from the iOS implementation, the divergence is flagged inline with a back-reference to the idea plan.

---

## 1. Project setup

### 1.1 Toolchain

| Item | Choice | Notes |
|---|---|---|
| Language | Kotlin 2.x (latest stable) | K2 compiler; Compose compiler ships with Kotlin via the `org.jetbrains.kotlin.plugin.compose` Gradle plugin. |
| Build | Gradle (Kotlin DSL) + version catalog | All versions in `gradle/libs.versions.toml`; no versions hard-coded in build scripts. |
| AGP | Latest stable 8.x | Whatever `com.android.application` stable is at project creation. |
| Compose | Compose BOM, latest stable (2025.x+ line) | BOM pins all `androidx.compose.*` artifacts. |
| Java toolchain | JDK 17 | AGP 8.x default. |
| compileSdk / targetSdk | 36 | Current target requirement era. |
| minSdk | **26** | Justified below. |
| applicationId | **`com.calcplus.calculator`** | Disguise identity is a single product decision (idea plan, Disguise surface section): display name **"Calculator+"**, neutral non-trademark calculator icon, identifiers that pass the "looks like a calculator" test. The applicationId must never reference "safebox" or the owner, and is permanent once shipped. Internal Kotlin packages use the same root (`com.calcplus.calculator`). |

### 1.2 minSdk = 26 justification

- **Photo Picker:** `ActivityResultContracts.PickVisualMedia` degrades gracefully by itself — system picker on Android 13+, Google Play services backport on Android 11–12, and an automatic **`ACTION_OPEN_DOCUMENT`** fallback below that. It imposes **no** minSdk floor, so the picker is not the constraint.
- **Crypto/passcode:** we do *not* depend on `EncryptedSharedPreferences` (deprecated — see §3.4), so no Jetpack Security floor either. `PBKDF2WithHmacSHA256` and Keystore AES/GCM are available well below 26.
- **What 26 buys:** `java.time` without desugaring, stable Keystore behavior, adaptive icons, and it drops the long tail of Android 5–7 devices that are effectively gone in 2026 (~99%+ coverage). Going higher (29+) buys nothing we need; going lower adds desugaring and legacy testing burden for near-zero users.

### 1.3 Module structure: single `:app`

**Decision: single `:app` module.** Multi-module (feature modules, `:core:database`, etc.) pays off for build-time parallelism and team ownership boundaries — neither applies to an iteration-1 skeleton built by one or two people. Instead we keep *package-level* discipline mirroring a future module split (`core/`, `feature/`), so extraction later is mechanical. Revisit when build times or team size justify it.

### 1.4 `gradle/libs.versions.toml` (shape, latest stable for each)

```toml
[versions]
agp = "latest stable 8.x"
kotlin = "latest stable 2.x"
composeBom = "latest stable"
room = "latest stable 2.x"
ksp = "matching kotlin"
coil = "latest stable 3.x"
datastore = "latest stable"
lifecycle = "latest stable"
navigation = "see §2.4"
coroutines = "latest stable"
kotlinxSerialization = "latest stable"   # route keys + contact list-column JSON
markdownRenderer = "latest stable"        # mikepenz multiplatform-markdown-renderer
junit = "4.13.2"
robolectric = "latest stable"
turbine = "latest stable"

[libraries]
# compose-bom, compose-ui, compose-material3, compose-material-icons,
# activity-compose, lifecycle-viewmodel-compose, lifecycle-runtime-compose,
# lifecycle-process (ProcessLifecycleOwner), navigation (see §2.4),
# room-runtime, room-ktx, room-compiler, datastore-preferences,
# kotlinx-serialization-json, markdown-renderer-m3, coil-compose,
# coroutines-core/-test, junit, robolectric, androidx-test-core,
# room-testing, turbine

[plugins]
android-application, kotlin-android, kotlin-compose, kotlin-serialization, ksp
```

There is **no biometric dependency**: biometric unlock is not in iteration 1 (see §4.5 and the idea-plan roadmap). Room uses **KSP** (never kapt). Enable `room { schemaDirectory("$projectDir/schemas") }` via the Room Gradle plugin for schema export from day one (§8.4).

---

## 2. Architecture

### 2.1 Pattern: MVVM + repositories

- **ViewModel + `StateFlow<UiState>`** per screen. ViewModels expose a single immutable `UiState` data class plus event functions; collect in Compose with `collectAsStateWithLifecycle()`.
- **Repository interfaces** in `core/domain` (e.g. `NoteRepository`), implementations in `core/data` backed by Room DAOs / file storage / DataStore. ViewModels depend only on interfaces — this is what keeps unit tests fast and a future module split clean.
- **No use-case layer** for iteration 1; repositories are thin enough. Add interactors only where logic outgrows a repository method (the passcode matcher and calculator engine are plain classes, not use-cases).

### 2.2 DI: manual, not Hilt

**Decision: manual DI via an `AppContainer`.** Justification: a single module with ~10 injectable types (database, 5 repositories, 3 stores, 2 engines) doesn't amortize Hilt's cost (KSP processing, annotation ceremony, learning curve, slower builds). Manual container = one file, zero magic, trivially testable.

- `SafeBoxApplication` holds `val container: AppContainer`.
- `AppContainer` lazily constructs the Room DB, `PhotoFileStore`, repositories, `PasscodeStore`, `AppLockManager`. (No `SettingsStore` in iteration 1 — §3.5.)
- ViewModels are created with a small `viewModelFactory { }` helper that pulls from the container.
- Because everything depends on interfaces, migrating to Hilt later is a rename exercise, not a redesign.

### 2.3 Single-activity Compose app + root lock state

One `MainActivity`, `setContent { SafeBoxApp() }`. **App lock is root-level state, above navigation:**

```kotlin
// AppLockManager (in container, process-wide singleton)
val lockState: StateFlow<LockState> // Locked | Unlocked | NeedsSetup

@Composable fun SafeBoxApp() {
    when (lockState) {
        NeedsSetup, Locked -> CalculatorScreen(...)  // no NavHost at all
        Unlocked -> VaultScaffold(...)               // tabs + nav graphs
    }
}
```

Key properties:

- The locked branch contains **no navigation graph and no vault composables** — nothing vault-related is even composed while locked, so nothing leaks via recomposition or tooling.
- `LockState` lives in memory only and **defaults to `Locked`** on process creation (see §8.1 — deliberately *not* saved/restored).
- **Startup resolution of `NeedsSetup` vs `Locked` (decided): the passcode-existence check runs synchronously at process start.** `AppContainer` performs a one-key Preferences DataStore read via `runBlocking` inside `Application.onCreate` (acceptable at process start for a single small key) so `AppLockManager` is constructed already knowing whether a passcode exists. First composition therefore renders the correct mode — a first-run user sees the setup caption immediately and never types into a screen that changes semantics mid-stream. (The considered alternative — an `Initializing` state rendering calculator chrome with input disabled until an async read lands — is rejected in favor of the simpler synchronous read.) This behavior is an explicit M1/M2 acceptance item (§6).
- **Auto-lock (per the idea-plan re-lock model): backgrounding locks immediately, always.** There is no grace period, no timeout setting, and nothing persisted: a `DefaultLifecycleObserver` on `ProcessLifecycleOwner` calls `AppLockManager.lock()` in `onStop` — unless the §2.3 suppression flag is in flight (below), whose hard cap uses **`SystemClock.elapsedRealtime()`** (monotonic — immune to wall-clock changes); if the elapsed value is unavailable or nonsensical (process restart), **fail closed** (lock). There is no foreground idle timer in iteration 1 (documented simplification in the idea plan).
- **Auto-lock suppression for app-initiated system UI (required).** With immediate lock-on-background, launching the system Photo Picker would background the app and lock the vault mid-import. `AppLockManager` therefore exposes an in-flight exemption: `beginExternalActivity()` sets a flag **before** the app launches a trusted outgoing system UI (in iteration 1 this is exactly the photo picker and permission dialogs); while the flag is set, `onStop` records the `elapsedRealtime` timestamp instead of locking. The flag is cleared on `onStart`/activity-result delivery, and carries a **hard cap** (2 minutes of backgrounded time measured on `elapsedRealtime`, pinned in idea plan §2.5.1 and shared with iOS), beyond which the normal lock rule applies regardless — the exemption is for a picker round-trip, not for leaving the app open indefinitely. **Immediate lock thus means "immediately, except during a trusted app-initiated outgoing intent"** — the exemption is part of the documented lock semantics. **If the lock happened anyway** (cap exceeded, or process death during the round-trip): the picker result is not consumed by UI — the import completes at the **repository level**, launched in `applicationScope` and keyed by `albumId` before the picker was launched, so delivered URIs are copied into the vault regardless of what is composed. The user returning to a locked app sees the calculator, re-enters the passcode, and finds the imported photos in the target album. See §4.2 and §8.5.
- **Locking clears transient input:** every `lock()` transition clears the `CalculatorEngine` display state and the `KeySequenceRecorder` buffer. Additionally, on `onStop` **while already `Locked`**, the same clear runs — otherwise a half-typed passcode would still be on screen (and in the buffer) when someone else foregrounds the app. FLAG_SECURE blanks the recents thumbnail but not the live resumed screen, so this clear is load-bearing. Covered by `AppLockManager`/`CalculatorViewModel` tests (§7).
- **Manual "Lock now"** in Settings calls `AppLockManager.lock()` directly (§4.5).

### 2.4 Navigation

**Decision: Navigation 3 (`androidx.navigation3`) if it is stable at project start; otherwise Navigation-Compose 2.9.x with type-safe (`@Serializable` route) APIs.**

Why Nav3 is the sensible 2026 default: it is Google's stated direction for Compose navigation — the back stack is a plain `SnapshotStateList<NavKey>` **owned by you**, which fits SafeBox unusually well: the lock gate is ordinary conditional composition, per-tab back stacks are just per-tab lists we retain across tab switches, and there is no hidden `NavController` state that could survive a lock in surprising ways. Why the fallback clause: Nav3's stabilization timeline has been fluid; the plan must not gamble the skeleton on an alpha. **The structure below is identical under both** (a `NavigationRouter` abstraction with per-tab stacks of serializable keys), so the choice is confined to `core/navigation/` — call it at M1 kickoff based on what's stable that week.

Structure either way:

- `VaultScaffold`: Material 3 `NavigationBar` with 4 items (Gallery, Notes, Contacts, Settings).
- **Nested graph per tab**, each tab keeping its own back stack (Gallery: albums → grid → pager; Notes: list → editor; Contacts: list → detail → edit; Settings: root → change-passcode). Switching tabs preserves each stack; system back pops the current tab's stack, then falls through to default behavior.
- All route keys are `@Serializable` data classes/objects in `core/navigation/Routes.kt`.

---

## 3. Persistence

### 3.1 Room schema

Database `SafeBoxDatabase`, version 1, exported schema. Entities (IDs are `String` UUIDs generated app-side — simplifies file naming and future sync). This schema mirrors the idea plan's domain model exactly; field names align with the shared domain model (`byteCount`, `thumbFileName`).

```
AlbumEntity      (id PK, name, createdAt, sortIndex)
PhotoEntity      (id PK, albumId FK→Album CASCADE /* required, non-null */,
                  fileName /* real extension */, thumbFileName, mimeType,
                  width, height, byteCount, importedAt, sortIndex /* import order */)
                  index(albumId)
NoteEntity       (id PK, body /* raw markdown, single source of truth */,
                  title /* DERIVED, denormalized — see below */,
                  snippet /* DERIVED, denormalized — see below */,
                  createdAt, updatedAt)
TagEntity        (id PK, name UNIQUE, colorIndex)
NoteTagCrossRef  (noteId FK CASCADE, tagId FK CASCADE, composite PK, index both)
ContactEntity    (id PK, firstName?, lastName?, organization?,
                  phones /* JSON list of {label, value} via converter */,
                  emails /* JSON list of {label, value} via converter */,
                  address?, notes?, createdAt, updatedAt)
                  /* invariant: at least one of firstName/lastName/organization non-blank */
```

Notes on specific entities:

- **AlbumEntity has no `coverPhotoId`.** The album cover is **derived: the first photo by `sortIndex`** in the `AlbumWithCount` query. This avoids dangling-FK/nullify logic when the cover photo is deleted or moved — there is simply no invariant to maintain. `sortIndex` makes albums manually orderable (parity with iOS).
- **PhotoEntity.albumId is a required (non-null) FK** — every photo belongs to exactly one album; import always targets an album. `thumbFileName` references the thumbnail file generated at import (§3.3). `byteCount` (not `sizeBytes`) matches the shared domain model.
- **NoteEntity follows the derived-title model** (Apple-Notes style, per the idea plan): the markdown `body` is the only thing the user edits. On every save, `title` = first non-empty line of `body`, **markdown-stripped** (syntax characters like `#`, `**`, `- [ ]` removed), and `snippet` = the following lines (title line excluded), markdown-stripped and truncated. Both are recomputed on save and **stored denormalized** so the list query never parses markdown. Derivation rules and the shared example table live in the idea plan; the Android mapper must reproduce them exactly. A note whose body is blank gets title `"New note"` / empty snippet per the idea plan.
- **TagEntity has `colorIndex`** (parity with iOS — tags render as colored chips from a fixed palette indexed by `colorIndex`).
- **ContactEntity carries multi-value labeled phones and emails** — `List<LabeledValue>` (`label: String`, `value: String`) serialized to JSON string columns via a Room `TypeConverter` using kotlinx-serialization. Iteration-1 scale makes JSON columns fine; a child table is a later migration if querying into values is ever needed. `organization` and `address` are first-class fields. Validation invariant: **at least one of firstName / lastName / organization** must be non-blank (organization-only contacts are legal).

### 3.2 DAOs — Flow-based reads, suspend writes

- `AlbumDao`: `observeAlbumsWithCounts(): Flow<List<AlbumWithCount>>` (uses `@Query` with `COUNT` join, plus the first-photo-by-sortIndex cover resolution), ordered by album `sortIndex`; CRUD.
- `PhotoDao`: `observePhotos(albumId): Flow<List<PhotoEntity>>` ordered by `sortIndex, importedAt`, insert batch, delete (returns `fileName`/`thumbFileName` pairs so the repository can delete files), move-to-album.
- `NoteDao`: `observeNotes(query: String): Flow<List<NoteEntity>>` ordered by `updatedAt DESC`, with **search**: `WHERE title LIKE '%'||:query||'%' OR body LIKE '%'||:query||'%'` (empty query = all), and an overload filtered by tag id (join through `NoteTagCrossRef`) for **tag filtering** (firmly in iteration-1 scope — §4.3); `observeNoteWithTags(id): Flow<NoteWithTags>` via `@Relation`; upsert; delete; tag cross-ref insert/clear in a `@Transaction`.
- `TagDao`: `observeAll()`, `insertIgnore` (get-or-create by name; `colorIndex` assigned round-robin at creation).
- `ContactDao`: `observeContacts(query: String): Flow<List<ContactEntity>>` — `WHERE` with `LIKE '%' || :query || '%'` over **firstName, lastName, organization, phones, emails** (the JSON columns are LIKE-searchable for digit/text substrings; label noise in the JSON is acceptable at this scale — documented trade-off), ordered by the **familyName-first sort key** (lastName, then firstName, then organization as fallback; grouping into sticky-header sections including the `#` bucket for non-letter keys happens in the ViewModel, matching the idea plan's shared sort spec); CRUD.

All queries returning lists return `Flow` so screens are reactive for free; all mutations are `suspend`.

### 3.3 Photo binaries — files, not blobs

- Directory: `context.filesDir/vault/photos/` (app-private internal storage; no permissions needed, not media-scanned). Thumbnails in `filesDir/vault/thumbs/`.
- **Import (still images only — the picker is filtered to `ImageOnly`; policy in the idea plan):** copy the picker `Uri` stream to `vault/photos/<uuid>.<ext>` **byte-for-byte — original bytes are always preserved, never re-encoded**, with the **real extension** derived from the source MIME type (HEIC stays `.heic`, GIF policy per idea plan); read dimensions via `BitmapFactory.Options(inJustDecodeBounds)`; **generate a thumbnail file at import** (downsampled decode → JPEG thumb at grid resolution → `vault/thumbs/<uuid>.jpg`, referenced by `thumbFileName`); insert `PhotoEntity`. Copy + thumb + insert are ordered files-first, row-second; on failure delete the partial files (an orphan file without a row is harmless and swept later; a row without a file is a broken UI).
- **Deletion deletes files too:** deleting a photo (or an album — enumerate its photos *before* the cascade) deletes rows in Room **and** the full-size + thumbnail files. A **startup orphan sweep** (at process start, on first database open — the sweep needs no unlock, per idea plan §3.1) removes files not referenced by any row, as backstop only — "no orphan files" is a DoD item in the idea plan.
- **Coil:** vault image bytes must live only under `filesDir/vault` — configure the `ImageLoader` with the **disk cache disabled** for vault images (memory cache is fine). Grid cells load `thumbFileName`; the pager loads `fileName`.

### 3.4 Passcode storage

**Status note:** `androidx.security:security-crypto` (`EncryptedSharedPreferences` / `MasterKey`) is **deprecated** (Jetpack Security was deprecated in 2024 and never left alpha). Do not use it.

**Pattern (per the idea plan's pinned decision): a salted, key-stretched hash, wrapped with a hardware-backed Keystore key.** Never the passcode itself, never a reversible form of it. Honest threat framing (stated once in the idea plan, repeated here because it shapes implementation choices): the hash gate is a **UI lock, not encryption** — a calculator-key passcode is low-entropy (17-token alphabet, minimum 4 keys), so a hash extracted from storage is offline-brute-forceable regardless of iteration count. The Keystore wrap raises the bar for offline attack on an extracted image (the blob alone is ciphertext without the device's non-exportable hardware key), but on a rooted device the wrap key can be exercised on-device to unwrap the blob — the idea plan's accepted-risk framing stands.

- **Hash: PBKDF2-HMAC-SHA256** via built-in `javax.crypto` (`SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")`). Chosen over Argon2 because it needs **no third-party dependency**. Parameters (pinned identically on both platforms by the idea plan): random **16-byte salt** (`SecureRandom`), **600,000 iterations** (the current OWASP figure for PBKDF2-HMAC-**SHA256**; note 210k is OWASP's SHA-512 figure — do not copy that number into code comments), 256-bit output.
- **Input normalization:** the passcode is the ordered list of **canonical key IDs** — `D0…D9, DOT, ADD, SUB, MUL, DIV, PCT, SIGN` — joined with `|`, e.g. `"D7|ADD|D3|PCT"`. Canonical IDs, not display glyphs — the serialization is pinned cross-platform in the idea plan so a future migration/sync never has to reconcile token spellings. Passcode length: **4–32 keys** (§4.1).
- **Keystore wrap (mandatory in iteration 1):** the stored blob `{algo, version, iterations, salt, hash}` is encrypted with an **Android Keystore AES/GCM key** (`KeyGenParameterSpec`, `AES/GCM/NoPadding`, non-exportable, **no `setUserAuthenticationRequired`** — the vault's credential is the calculator passcode, not the device credential, and auth-bound keys add invalidation-on-enrollment-change failure modes for zero benefit here). The Keystore key never leaves secure hardware; wrapping happens in `PasscodeStore` behind the `PasscodeRepository` interface.
- **Keystore-unavailability fallback (documented):** if Keystore key generation or use fails (rare: corrupted keystore daemon, exotic OEM breakage), `PasscodeStore` falls back to storing the PBKDF2 blob **unwrapped**, records `wrapped=false` in the same DataStore, and opportunistically retries wrapping on the next successful passcode set/change. Verification transparently handles both forms. No user-visible error (nothing to act on), no logging of the failure detail beyond a generic marker (§8.6).
- **Storage:** Preferences DataStore (`passcode_store`): `blob` (Base64 of the GCM ciphertext + IV, or the plain blob when `wrapped=false`), `wrapped` (Boolean), `createdAt`. The iteration count is inside the blob to allow future parameter upgrades.
- **Verification runs off the UI path:** on `=` while locked, the arithmetic result renders **immediately and identically** whether or not the sequence matches; PBKDF2 + unwrap + compare run on `Dispatchers.Default`, and a match flips `LockState` when it completes. There must be no visible latency or rendering difference between match and non-match up to the moment the vault appears. (PBKDF2 latency is *not* a rate limiter and must not be described as one — offline attackers don't use our UI; silent unlimited guessing is an accepted iteration-1 risk per the idea plan.)
- **Comparison:** constant-time (`MessageDigest.isEqual`). No failure counter in iteration 1 (failures are invisible by design).

### 3.5 Settings storage

**None in iteration 1.** Nothing user-configurable is persisted: auto-lock is always immediate (no timeout preference, §2.3), biometrics is deferred (§4.5), and the remaining Settings items (Change passcode, Lock now, About) hold no state outside `PasscodeStore`. A `SettingsStore` DataStore is introduced only when iteration 2 adds its first real preference (e.g. a configurable auto-lock grace period).

---

## 4. Feature build notes

### 4.1 Calculator engine + passcode capture

Two cleanly separated pure-Kotlin classes (both fully unit-testable on the JVM):

1. **`CalculatorEngine`** — a real calculator whose reference model is **iOS Calculator basic-mode semantics**, per the idea plan's calculator behavior spec: immediate-execution with `display`, `accumulator`, `pendingOp`; digits, `.`, `+ − × ÷`; consecutive operators replace the pending operator; `%` = unary (÷100) standalone, percent-of-accumulator in binary context; repeated `=` repeats the last operation; `±` edge cases per the spec; division-by-zero → `"Error"` then reset; display precision/overflow rules per the spec. Input/output is a `CalcKey` sealed type → `CalculatorState`. Format via `BigDecimal` with trailing-zero stripping, cap display length. **The engine must reproduce the idea plan's shared table of input→display sequences exactly (including degenerate all-symbol sequences) — that table is in the cross-platform DoD** and is encoded as a table-driven test.

2. **`KeySequenceRecorder` + `PasscodeMatcher`** — in parallel with the engine, every key press appends its canonical key ID (§3.4) to a buffer; `C/AC` clears the buffer (so a mistyped passcode is recoverable without suspicion — clearing is normal calculator behavior). **The buffer is capped at 32 keys with an overflow flag:** the 33rd key sets the flag; an overflowed buffer can never match on commit (in Locked mode: silently — the calculator just calculates; in setup: a "start again" banner). On **`=` (the commit gesture)**:
   - Buffer (excluding the `=` itself) → canonical token string. Commits shorter than 4 keys or with the overflow flag set **skip the verification entirely** (no store read, no KDF).
   - Otherwise → `PasscodeMatcher.matches(tokens)` (PBKDF2 + unwrap + constant-time compare against `PasscodeStore`, off the UI path per §3.4).
   - **Match** → `AppLockManager.unlock()`; the calculator screen is replaced by the vault.
   - **No match** → nothing special: the engine evaluates as usual, buffer resets. Zero visible difference from a normal calculation.

3. **Setup / capture modes:** the same `CalculatorScreen` composable takes a `mode: CalculatorMode` — `Disguise`, `CaptureNew`, `ConfirmNew`, `VerifyCurrent` (the fourth mode exists for change-passcode; §4.5). In capture modes a discreet one-line caption above the display says "Enter a key sequence (4–32 keys), press = to set" / "Re-enter to confirm" (capture modes only ever appear on first run or from inside Settings, so the caption doesn't endanger the disguise). Rules per the idea plan's setup state machine (ENTRY → CONFIRM → store): length 4–32; the setup copy **nudges toward ≥6 keys including a symbol**; a **soft warning on trivial sequences** (single repeated key) — warning only, not a block; setup copy also notes the **accidental-unlock collision risk** (a passcode like `12+34` is real arithmetic anyone might type — the caption guidance steers toward non-obvious sequences). Mismatch on confirm restarts at ENTRY. **Backgrounding mid-setup discards both buffers and returns to ENTRY** (fail closed). The pending sequence is held in memory during setup and compared as plain sequences; the hash is computed and stored only on successful confirm, followed by the **one-time "no recovery" notice** (idea plan: forgetting the passcode means reinstalling and losing the vault). The calculator still computes in capture modes (keeps behavior consistent).

`CalculatorViewModel` composes engine + recorder + matcher; the screen is a `Column` of display + 4×5 key grid (Material 3 buttons, monospace display, dark calculator styling that looks like a plausible stock calculator). Per §2.3, the ViewModel clears engine display + recorder buffer on every `lock()` and on `onStop` while locked.

### 4.2 Gallery

- **Albums list:** `LazyVerticalGrid` of album **cards** (cover thumbnail via Coil = **first photo by `sortIndex`**, name, count — the card-grid-with-cover presentation is the pinned cross-platform album list per the idea plan). Ordered by album `sortIndex`. FAB → "New album" dialog. Long-press → rename/delete (delete cascades photos: rows **and** files, §3.3).
- **Import:** `rememberLauncherForActivityResult(PickVisualMedia /* or PickMultipleVisualMedia(maxItems) */)` with **`ImageOnly`** (still images only; media policy in the idea plan). No storage permission needed at any API level. **Launch protocol (auto-lock suppression, §2.3):** the screen calls `AppLockManager.beginExternalActivity()` immediately before launching the picker; the flag is cleared on `onStart`/result delivery. The result callback does **not** perform the import itself — it hands the URIs to `PhotoRepository.import(albumId, uris)` running in **`applicationScope`** (lock-surviving, keyed by the `albumId` captured before launch) on `Dispatchers.IO`, with a progress indicator for multi-select while unlocked. If the vault locked during the round-trip (suppression cap exceeded / process death), the import still completes at the repository level; the user unlocks via the calculator and finds the photos in the target album. The source library photos are **not** deleted (Android can't silently delete others' media; note in UI: "Originals remain in your library — delete them in Photos if desired").
- **Photo grid:** `LazyVerticalGrid(GridCells.Adaptive(110.dp))`, Coil `AsyncImage(File(thumb))` with crossfade; selection mode (long-press) for delete/move.
- **Detail:** `HorizontalPager` (Compose Foundation) across the album's photos ordered by `sortIndex, importedAt`; per-page zoom via a small custom `Modifier.pointerInput` + `graphicsLayer` (`detectTransformGestures`) with the **shared cross-platform zoom constants: double-tap toggles 1× / 2.5×, pinch max 5×**, pan clamped to bounds, zoom resets on page change — a custom ~80-line zoom modifier beats adding a library for iteration 1. Top bar: delete, move-to-album; share deliberately **omitted** (sharing a vault photo re-exposes it; revisit later).
- **Coil:** per §3.3, disk cache **disabled** for vault images (vault bytes live only under `filesDir/vault`); memory cache OK.

### 4.3 Notes

- **List:** `LazyColumn` of rows — **derived title** (bold, single line) + **derived snippet** (2 lines, secondary color) straight from the denormalized columns (§3.1), relative date, tag chips; sorted `updatedAt DESC`. **Search field** over title + body (DAO query, §3.2), debounced input → `flatMapLatest` — parity with iOS's searchable notes list. **Tag filter chips** (a `FlowRow` of filter chips atop the list, backed by the tag-filtered DAO query) are **firmly in iteration-1 scope** (idea plan: tags are in the user's core ask). **Swipe-to-delete with a confirmation dialog — no undo snackbar** (pinned cross-platform: confirmation, not undo). FAB → new note.
- **Editor (derived-title model — no separate title field):** single screen with an **Edit / Preview toggle** (segmented button). Edit = one full-screen `TextField` over the raw markdown `body` — the first line *is* the title, Apple-Notes style; there is no dedicated title box. On save, the repository recomputes the denormalized `title`/`snippet` via the shared markdown-stripping derivation (§3.1). **Autosave: 1 s debounce after the last keystroke, plus a synchronous flush on editor exit and on backgrounding** (`onStop`) — no explicit save button. (1 s + flush-on-exit is the pinned cross-platform autosave contract.)
- **Markdown rendering (Preview):** use **`mikepenz/multiplatform-markdown-renderer`** (`markdown-renderer-m3` artifact) — actively maintained, Compose-native, Material 3 theming, no WebView/View interop — but **constrained and themed to the shared cross-platform markdown subset** defined in the idea plan: headings, bold/italic, inline code, bullet/numbered lists, and **checklists rendered as non-interactive styled text** (no tap-to-toggle in iteration 1). Renderer features outside the subset (tables, block quotes, images, etc.) are disabled/unstyled so the same note renders equivalently on both platforms (fidelity note in the idea plan). Fallback position if the library is ever abandoned: a minimal custom renderer over `AnnotatedString` covering exactly the subset (~200 lines), but don't start there.
- **Tags:** chip row under the editor (colored via `Tag.colorIndex`) using `FlowRow` (Compose Foundation layout). "+ tag" chip opens a small text-field popup with autocomplete from `TagDao.observeAll()`; get-or-create by name; removal = trailing icon on chip. Tags are stored via cross-ref in one `@Transaction`.

### 4.4 Contacts

- **List:** search `TextField` pinned on top (filters via the DAO query — matching **name + organization + phone + email**, per the idea plan's shared search spec — debounced 300 ms input → `flatMapLatest`); `LazyColumn` with **sticky headers** (`stickyHeader { }`) per first letter of the **familyName-first sort key** (lastName → firstName → organization fallback), with a **`#` bucket** for entries whose sort key doesn't start with a letter; avatar = initials in a colored circle (color hashed from id). FAB → new contact.
- **Detail:** read-only card layout: display-name header (derived per the idea plan's display-name rules for name-only/org-only entries), then rows for each labeled phone and email, organization, address, notes. **No `ACTION_DIAL` / `ACTION_SENDTO` intents — no tel:/mailto handoff in iteration 1** (pinned cross-platform: handing a vault contact's number to the system dialer/mail app leaks vault data into other apps' state). Instead, **long-press on a phone/email row copies the value to the clipboard** (with the clipboard-exposure caveat noted in the idea plan — clipboard contents are readable by the user's other apps and may sync). Edit + delete (confirm dialog) in the top bar.
- **Edit form:** one screen for create + edit; **dynamic add/remove rows for labeled phones and emails** (label picker: mobile/home/work/other; `KeyboardType.Phone`/`Email`), fields for first/last name, organization, address, notes. Validation: **at least one of firstName / lastName / organization non-blank**; save → upsert → pop.

### 4.5 Settings

`LazyColumn` of preference-style rows, grouped. **Iteration-1 Settings = Change passcode, Lock now, About** (pinned in the idea plan — there is no auto-lock row; backgrounding always locks immediately per §2.3).

- **Security:**
  - *Change passcode* → the explicit state machine **VerifyCurrent → EnterNew → Confirm** (idea-plan change-passcode spec), orchestrated by `ChangePasscodeFlow` over `CalculatorScreen`. The verify step uses **`mode = VerifyCurrent` — a dedicated mode with a visible caption ("Enter current passcode") and explicit feedback on a wrong code** (shake + "Incorrect — try again"): silence is a disguise feature *only on the lock screen*; inside the already-unlocked vault it would just be a broken-feeling screen. Unlimited retries in iteration 1. EnterNew/Confirm reuse `CaptureNew`/`ConfirmNew` with the same 4–32/trivial-warning rules; mismatch on confirm restarts EnterNew; cancel at any step returns to Settings with the old passcode intact.
  - *Lock now* → `AppLockManager.lock()` immediately (drops to the calculator).
- **Biometric unlock: not in iteration 1.** No toggle, no `androidx.biometric` dependency, no prompt trigger. It is an iteration-2 roadmap item in the idea plan, where the open disguise questions (auto-prompt vs hidden gesture; a system biometric sheet appearing over a "calculator") are recorded and must be resolved before implementation.
- **About:** version (`BuildConfig.VERSION_NAME`) + the short **"how it works" blurb** (shared copy from the idea plan) + a **licenses row** (Android genuinely needs it for the third-party markdown renderer and Coil; iOS has no third-party libraries — this asymmetry is noted in the idea plan and accepted).
- Structure rows as a list of sealed `SettingsItem` models rendered generically — adding future items (biometric unlock, decoy passcode, break-in alerts, disguise themes) = adding a model, not a screen rewrite.

---

## 5. Proposed file tree under `android/`

```
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/…
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro                         # incl. release rule stripping android.util.Log (§8.6)
    ├── schemas/                                   # exported Room schemas (committed)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml                # single activity, label "Calculator+", allowBackup=false, dataExtractionRules
        │   ├── res/xml/data_extraction_rules.xml  # exclude everything from backup/transfer
        │   ├── res/…                              # neutral calculator icon, themes.xml, strings.xml
        │   └── java/com/calcplus/calculator/
        │       ├── SafeBoxApplication.kt          # Application; owns AppContainer; sync passcode-existence read (§2.3); process lifecycle observer hookup
        │       ├── MainActivity.kt                # single activity; unconditional FLAG_SECURE; setContent { SafeBoxApp() }
        │       ├── di/
        │       │   └── AppContainer.kt            # manual DI: lazily wires DB, stores, repos, engines, AppLockManager
        │       ├── core/
        │       │   ├── lock/
        │       │   │   ├── AppLockManager.kt      # StateFlow<LockState>; lock()/unlock(); immediate lock on onStop;
        │       │   │   │                          #   beginExternalActivity()/endExternalActivity() suppression flag + hard cap
        │       │   │   └── LockState.kt           # sealed: NeedsSetup / Locked / Unlocked
        │       │   ├── database/
        │       │   │   ├── SafeBoxDatabase.kt     # @Database v1, entities list, DAO accessors
        │       │   │   ├── Converters.kt          # TypeConverters: List<LabeledValue> ↔ JSON (kotlinx-serialization)
        │       │   │   ├── entity/
        │       │   │   │   ├── AlbumEntity.kt     # id, name, createdAt, sortIndex (no coverPhotoId)
        │       │   │   │   ├── PhotoEntity.kt     # required albumId FK, fileName, thumbFileName, byteCount, …
        │       │   │   │   ├── NoteEntity.kt      # body + denormalized derived title/snippet
        │       │   │   │   ├── TagEntity.kt       # name UNIQUE + colorIndex
        │       │   │   │   ├── NoteTagCrossRef.kt
        │       │   │   │   └── ContactEntity.kt   # labeled phones[]/emails[] JSON columns, organization, address
        │       │   │   └── dao/
        │       │   │       ├── AlbumDao.kt        # AlbumWithCount incl. derived cover (first photo by sortIndex)
        │       │   │       ├── PhotoDao.kt
        │       │   │       ├── NoteDao.kt         # search (title+body), tag-filter query, NoteWithTags @Relation
        │       │   │       ├── TagDao.kt
        │       │   │       └── ContactDao.kt      # search over name/organization/phones/emails
        │       │   ├── domain/
        │       │   │   ├── model/                 # UI-facing domain models (Album, Photo, Note, Tag, Contact, LabeledValue)
        │       │   │   │   ├── Album.kt
        │       │   │   │   ├── Photo.kt
        │       │   │   │   ├── Note.kt
        │       │   │   │   ├── Tag.kt
        │       │   │   │   └── Contact.kt
        │       │   │   └── repository/            # interfaces only
        │       │   │       ├── AlbumRepository.kt
        │       │   │       ├── PhotoRepository.kt
        │       │   │       ├── NoteRepository.kt
        │       │   │       ├── ContactRepository.kt
        │       │   │       └── PasscodeRepository.kt
        │       │   ├── data/
        │       │   │   ├── AlbumRepositoryImpl.kt   # DAO-backed; entity↔domain mappers
        │       │   │   ├── PhotoRepositoryImpl.kt   # DAO + PhotoFileStore orchestration (lock-surviving import in
        │       │   │   │                            #   applicationScope keyed by albumId, delete-with-files, orphan sweep)
        │       │   │   ├── NoteRepositoryImpl.kt    # recomputes derived title/snippet on save (NoteDerivation)
        │       │   │   ├── ContactRepositoryImpl.kt
        │       │   │   ├── PasscodeRepositoryImpl.kt# PBKDF2 hash/verify via PasscodeStore, off the UI path
        │       │   │   ├── PhotoFileStore.kt        # filesDir/vault I/O: byte-for-byte copy from Uri, real extension,
        │       │   │   │                            #   thumbnail generation at import, delete, list, dims
        │       │   │   ├── PasscodeStore.kt         # DataStore: Keystore-wrapped {algo,version,iterations,salt,hash} blob
        │       │   ├── crypto/
        │       │   │   ├── Pbkdf2.kt                # PBKDF2-HMAC-SHA256 600k/16B-salt derive + constantTimeEquals
        │       │   │   └── KeystoreWrapper.kt       # AES/GCM Keystore key create/encrypt/decrypt + unavailability fallback
        │       │   ├── markdown/
        │       │   │   └── NoteDerivation.kt        # shared derivation: markdown-strip → title (first non-empty line) + snippet
        │       │   ├── navigation/
        │       │   │   ├── Routes.kt                # @Serializable route keys for all screens
        │       │   │   └── VaultNavigation.kt       # tab scaffolding + per-tab back stacks (Nav3 or nav-compose impl)
        │       │   └── ui/
        │       │       ├── theme/Theme.kt           # M3 color schemes (light/dark), typography
        │       │       ├── theme/CalculatorTheme.kt # distinct stock-calculator look for the disguise screen
        │       │       └── components/
        │       │           ├── ZoomableImage.kt     # pinch(≤5x)/pan/double-tap(2.5x) zoom modifier + composable
        │       │           ├── EmptyState.kt        # shared empty-list placeholder
        │       │           └── ConfirmDialog.kt     # shared destructive-action dialog
        │       ├── feature/
        │       │   ├── calculator/
        │       │   │   ├── CalculatorEngine.kt      # pure immediate-execution calculator (BigDecimal), iOS-basic-mode semantics
        │       │   │   ├── CalcKey.kt               # sealed key tokens with canonical IDs (D0…D9, DOT, ADD, SUB, MUL, DIV, PCT, SIGN)
        │       │   │   ├── KeySequenceRecorder.kt   # buffers key IDs; 32-key cap + overflow flag; clear semantics
        │       │   │   ├── PasscodeMatcher.kt       # normalize tokens → verify via PasscodeRepository (skips <4/overflow)
        │       │   │   ├── CalculatorViewModel.kt   # engine+recorder+matcher; modes Disguise/CaptureNew/ConfirmNew/VerifyCurrent;
        │       │   │   │                            #   clears display+buffer on lock() and onStop-while-locked
        │       │   │   └── CalculatorScreen.kt      # display + key grid UI; capture/verify captions
        │       │   ├── gallery/
        │       │   │   ├── AlbumListViewModel.kt
        │       │   │   ├── AlbumListScreen.kt       # album card grid w/ derived covers, create/rename/delete
        │       │   │   ├── PhotoGridViewModel.kt    # photos flow, import launcher plumbing + lock suppression, selection mode
        │       │   │   ├── PhotoGridScreen.kt       # LazyVerticalGrid + PickVisualMedia launcher (beginExternalActivity)
        │       │   │   ├── PhotoPagerViewModel.kt
        │       │   │   └── PhotoPagerScreen.kt      # HorizontalPager + ZoomableImage, delete/move
        │       │   ├── notes/
        │       │   │   ├── NoteListViewModel.kt     # search + tag-filter state
        │       │   │   ├── NoteListScreen.kt        # rows: derived title/snippet/date, search field, tag filter chips,
        │       │   │   │                            #   swipe-delete w/ confirmation (no undo)
        │       │   │   ├── NoteEditorViewModel.kt   # 1s debounced autosave + flush on exit/background, tag ops
        │       │   │   ├── NoteEditorScreen.kt      # single markdown body field (derived title), edit/preview toggle, tag FlowRow
        │       │   │   └── MarkdownPreview.kt       # wraps multiplatform-markdown-renderer, constrained to shared subset
        │       │   ├── contacts/
        │       │   │   ├── ContactListViewModel.kt  # search debounce, familyName-first grouping incl. '#'
        │       │   │   ├── ContactListScreen.kt     # sticky headers + search field
        │       │   │   ├── ContactDetailViewModel.kt
        │       │   │   ├── ContactDetailScreen.kt   # read view; long-press copy for phone/email (no dial/mail intents)
        │       │   │   ├── ContactEditViewModel.kt
        │       │   │   └── ContactEditScreen.kt     # create/edit form, dynamic labeled phone/email rows
        │       │   └── settings/
        │       │       ├── SettingsViewModel.kt     # settings flows, lock-now, change-passcode flow state
        │       │       ├── SettingsScreen.kt        # grouped rows: Change passcode, Lock now, About (blurb+licenses)
        │       │       └── ChangePasscodeFlow.kt    # VerifyCurrent → EnterNew → Confirm orchestration over CalculatorScreen
        │       └── app/
        │           └── SafeBoxApp.kt                # root composable: LockState switch → Calculator vs VaultScaffold
        ├── test/java/com/calcplus/calculator/       # JVM + Robolectric tests (see §7)
        │   ├── calculator/CalculatorEngineTest.kt   # incl. the idea plan's shared input→display sequence table
        │   ├── calculator/KeySequenceRecorderTest.kt# incl. 32-key cap + overflow-flag behavior
        │   ├── calculator/PasscodeMatcherTest.kt
        │   ├── crypto/Pbkdf2Test.kt
        │   ├── crypto/KeystoreWrapperTest.kt        # Robolectric; incl. fallback path
        │   ├── markdown/NoteDerivationTest.kt       # idea plan's shared derivation example table
        │   ├── lock/AppLockManagerTest.kt           # incl. suppression flag + hard cap + clear-on-lock
        │   ├── data/dao/AlbumPhotoDaoTest.kt        # Robolectric + in-memory Room
        │   ├── data/dao/NoteTagDaoTest.kt           # incl. search + tag-filter queries
        │   ├── data/dao/ContactDaoTest.kt           # incl. organization/phones/emails search, sort-key ordering
        │   └── data/PhotoFileStoreTest.kt           # Robolectric (real temp filesDir; byte-fidelity, thumbs, orphan sweep)
        └── androidTest/java/…                       # placeholder only; not exercised in cloud (see §6)
```

---

## 6. Milestones

Cloud sessions **can** run `./gradlew assembleDebug`, `testDebugUnitTest` (including Robolectric) — but **no emulator**, so every milestone's acceptance = compile + JVM tests + code review; UI behavior is verified manually on-device at the checkpoints marked *[manual]*.

**M1 — Project skeleton & lock shell.** Gradle/KTS + version catalog, `:app` compiles under applicationId `com.calcplus.calculator`, theme, `MainActivity` with unconditional `FLAG_SECURE`, `AppContainer` with the **synchronous passcode-existence read at process start**, `AppLockManager` (monotonic timing, suppression flag API), `SafeBoxApp` root switch with placeholder Calculator/Vault composables, DataStore stores, manifest with `allowBackup=false` + extraction rules.
*Accept:* `assembleDebug` green; `AppLockManagerTest` green — default Locked; **first composition already resolved to NeedsSetup vs Locked (no async flip)**; `onStop` locks immediately when no suppression flag is set; suppression flag suppresses the lock within the monotonic 2-minute cap and not beyond it; `lock()` emits the clear-transient-input signal.

**M2 — Calculator engine + passcode.** `CalculatorEngine`, `CalcKey` (canonical IDs), `KeySequenceRecorder` (32-key cap + overflow flag), `Pbkdf2` (600k/16-byte salt), `KeystoreWrapper`, `PasscodeStore`, `PasscodeRepository`, `PasscodeMatcher`, `CalculatorScreen` with modes wired to `LockState` (NeedsSetup → ENTRY/CONFIRM → one-time no-recovery notice → Unlocked; Locked → disguise/unlock; verification off the UI path).
*Accept:* engine suite green (arithmetic incl. chained ops, operator replacement, `%` unary/binary, repeated `=`, `±` edge cases, divide-by-zero, clear semantics, **the idea plan's shared input→display sequence table incl. degenerate all-symbol sequences**); recorder tests (buffer, clear resets, cap + overflow flag never matches); matcher tests (round-trip set→match, near-miss fails, sub-4-key and overflowed commits skip verification, canonical-ID vs display-glyph distinction); `Pbkdf2` known-vector + constant-time-compare tests; `KeystoreWrapper` wrap/unwrap + fallback tests; **first-run cold start composes setup mode on the first frame** (Robolectric). *[manual: full disguise feel on device; no visible difference between match and non-match commits]*

**M3 — Room + repositories.** All entities (incl. contact JSON converters, derived note columns), DAOs, `SafeBoxDatabase` (schema export on), domain models + mappers, `NoteDerivation`, all repository impls, `PhotoFileStore` with thumbnail generation.
*Accept:* Robolectric in-memory DAO tests green (album CRUD + cascade photo delete incl. file enumeration; derived-cover query = first photo by sortIndex; note upsert + derived title/snippet recompute + tag cross-ref transaction + `NoteWithTags` + search/tag-filter queries; contact search over name/organization/phone/email incl. case-insensitivity; familyName-first ordering incl. `#` bucket); `NoteDerivationTest` reproduces the idea plan's example table; `PhotoFileStoreTest` (byte-for-byte copy with real extension, thumb generation, delete-with-files, orphan sweep on temp dir) green; schema JSON committed.

**M4 — Vault scaffold + Gallery.** Navigation (per §2.4 decision), `NavigationBar`, four tab stubs; full Gallery: album card grid, import via `PickVisualMedia` with **auto-lock suppression + applicationScope repository import**, grid, pager + zoom (2.5×/5×), delete/move.
*Accept:* build + M1–M3 suites still green; ViewModel unit tests for `PhotoGridViewModel` (import orchestration with faked repo: suppression flag set before launch, import keyed by albumId completes even when lock fires mid-flight) and album list state. *[manual: picker round-trip does not lock the vault despite immediate lock-on-background; forced-lock-during-import lands photos in the album after re-unlock; zoom gestures; tab back-stack behavior]*

**M5 — Notes.** List with search + tag filter chips, editor (single markdown field, derived title) with 1 s autosave + flush-on-exit/background, markdown preview constrained to the shared subset, tag chips with colors, swipe-to-delete with confirmation.
*Accept:* `NoteEditorViewModel` tests (1 s debounced autosave via `runTest` + virtual time, flush on exit/background, body → derived title/snippet mapping, tag add/remove); list search + tag-filter tests. *[manual: preview rendering fidelity against the shared-subset samples]*

**M6 — Contacts.** List with search + sticky headers (`#` bucket), detail with long-press copy, edit form with dynamic labeled phone/email rows + validation.
*Accept:* `ContactListViewModel` tests (debounce, familyName-first section grouping incl. `#` bucket); edit-form validation test (at least one of first/last/organization); DAO search tests from M3 extended if the query changed.

**M7 — Settings + lock polish.** Settings screen (Change passcode, Lock now, About with blurb + licenses), change-passcode flow (VerifyCurrent → EnterNew → Confirm with visible wrong-code feedback), immediate lock-on-background end-to-end, clear-display-and-buffer on lock/onStop-while-locked.
*Accept:* `ChangePasscodeFlow` state-machine test (verify-current wrong → **visible error, stays in VerifyCurrent**, unlimited retries; capture/confirm mismatch → restarts EnterNew; cancel → old passcode intact; success → new hash verifies, old fails); immediate-lock-on-onStop unit test; clear-on-lock test in `CalculatorViewModel`. *[manual: Lock now, backgrounding/recents lock behavior, mid-passcode background → clean display on return]*

**M8 — Hardening & release prep.** Orphan-file sweep hookup, process-death review (§8.1 checklist), R8 config incl. the release Log-stripping rule (§8.6), backup-rules verification, launcher identity finalized (**"Calculator+"** label + neutral non-trademark calculator icon per the idea plan — already fixed, not an open call), version 1.0.0.
*Accept:* `assembleRelease` (minified) green; full unit suite green; manual device pass of the §8 checklist incl. recents-thumbnail verification from every tab.

---

## 7. Testing strategy

**Unit (JVM, run in cloud):**

- `CalculatorEngine` — the highest-value suite; table-driven cases for arithmetic, formatting, error states, and the idea plan's shared cross-platform input→display sequence table (DoD item).
- `KeySequenceRecorder` / `PasscodeMatcher` / `Pbkdf2` / `KeystoreWrapper` — capture, clear, 32-key cap + overflow flag, match/no-match, sub-minimum skip, canonical-ID normalization, deterministic vectors, tampered-salt fails, wrap/unwrap round-trip + fallback path.
- `NoteDerivation` — the idea plan's shared title/snippet derivation example table (markdown-stripped first line, checklist markers, blank-body fallback).
- `AppLockManager` — default state, sync-resolved NeedsSetup/Locked at construction, lock/unlock transitions, immediate lock on onStop, suppression flag + monotonic hard-cap arithmetic, clear-transient-input on lock.
- ViewModels — coroutine tests with fake repositories, `kotlinx-coroutines-test` + Turbine for `StateFlow` assertions (incl. autosave debounce/flush and import-survives-lock orchestration).

**DAO tests — Robolectric + in-memory Room (JVM, run in cloud).** Chosen over instrumented tests precisely because cloud sessions have no emulator: `Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), …).allowMainThreadQueries()` under Robolectric runs in `testDebugUnitTest`. *Note:* Robolectric's SQLite is a real native SQLite, so query semantics are faithful; still, one instrumented mirror of the DAO suite lives in `androidTest/` to run on a physical device before release (manual step, M8).

**Stays manual (no emulator in cloud):** photo picker round-trip incl. lock-suppression behavior, pinch-zoom feel, FLAG_SECURE screenshot/recents behavior from every tab, process-death lock restore (`adb shell am kill`), immediate lock on backgrounding, markdown rendering fidelity, back-stack navigation feel.

---

## 8. Android-specific risks & gotchas

### 8.1 Process death and lock state
The OS can kill and restore the process with the vault route as the "current" UI via saved instance state. Mitigations: `LockState` is memory-only and defaults to `Locked` (resolved to `NeedsSetup` only by the synchronous passcode-existence read, §2.3); the vault `NavHost`/back stacks are composed **inside** the `Unlocked` branch, so after process death the app cold-starts into the calculator regardless of restored activity state. Deliberately do **not** put unlock state in `SavedStateHandle`/`onSaveInstanceState`. Trade-off accepted: users lose vault navigation position after process death (they land on the calculator; correct behavior for a vault). Test with "Don't keep activities" + `am kill`.

### 8.2 Screenshots, recents, and screen capture
`window.setFlags(FLAG_SECURE)` in `MainActivity.onCreate`, **unconditional for the whole activity** — blocks screenshots and screen recording and shows a blank recents thumbnail. Note: it also blanks recents (and blocks screenshots) for the *calculator*, which is mildly suspicious; **this is a deliberate, accepted divergence from iOS** (which cannot block screenshots and instead installs a calculator-face cover view on `willResignActive`) — recorded as the per-platform snapshot-protection decision in the idea plan's data-protection/disguise section. The alternative — toggling FLAG_SECURE with lock state — risks a race where a vault frame lands in recents around `onPause`, before recomposition to the calculator; do not attempt. Keep FLAG_SECURE unconditional. DoD (idea plan): the recents image is verified after backgrounding from every tab.

### 8.3 Backup and data extraction
`android:allowBackup="false"` **and** `android:dataExtractionRules` (API 31+) + legacy `fullBackupContent` (API ≤30) excluding everything — otherwise Auto Backup ships the Room DB and DataStore to Google Drive, and device-to-device transfer copies the vault to a new phone without the passcode ever being tested. Exclude `filesDir/vault/`, databases, and datastore explicitly in the rules files even with `allowBackup=false` (belt-and-braces against manifest merge surprises). Verify with `adb shell bmgr`.

### 8.4 Room migrations
Version 1 ships with `exportSchema = true` and `schemas/` committed from day one — iteration 2 features (decoy vault, encryption metadata, biometric key material) are certain to need migrations, and you cannot write a migration test without the v1 schema JSON. Never use `fallbackToDestructiveMigration` in release builds (it silently deletes the vault). Add `MigrationTest` scaffolding (Room `MigrationTestHelper`) at the first schema bump.

### 8.5 Other gotchas
- **Picker URI lifetime:** `PickVisualMedia` grants are transient — copy the bytes immediately in the result-handling coroutine; don't persist the `Uri`. Combined with §2.3's suppression design: the copy runs in `applicationScope` keyed by `albumId`, so neither the lock gate nor screen teardown cancels it.
- **Large imports:** copy on `Dispatchers.IO` with a foreground progress UI while unlocked; the lock gates UI, not the repository coroutine scope — an in-flight import always completes.
- **Keyboard/IME leakage:** notes and contacts fields can leak content via keyboard learning; IME personalization is not app-controllable — accept, but disable autofill on sensitive fields (`importantForAutofill="no"`) and avoid `AutofillHints`.
- **`%` semantics:** follow the idea plan's calculator behavior spec (iOS basic-mode reference: unary ÷100 standalone, percent-of-accumulator in binary context), encode it in the engine tests, and make sure the *passcode token* for `%` (`PCT`) is independent of its arithmetic meaning.
- **Clipboard (contacts copy):** long-press copy puts vault data on the shared clipboard; Android 13+ shows a system clipboard preview overlay and clipboard is readable by the focused app. Accepted, with the caveat surfaced in the idea plan; consider `ClipDescription.EXTRA_IS_SENSITIVE` to suppress the preview.
- **Predictive back:** enable `android:enableOnBackInvokedCallback="true"` and verify back-handling in per-tab stacks under predictive back animations (a known friction point with custom back-stack ownership).
- **R8:** Room/KSP and kotlinx-serialization (route keys + contact JSON) need keep rules — use the libraries' bundled consumer rules; verify `assembleRelease` in M8, not for the first time at ship.

### 8.6 No-logging rule
Per the idea plan's shared security rule: **never log** key tokens, recorder buffers, candidate sequences, salts, hashes, or Keystore failure details beyond a generic marker — in any build type. Additionally, release builds strip debug logging mechanically: an R8 `-assumenosideeffects` rule removes `android.util.Log` `v/d/i` (and `w/e` for lock internals are simply never written). Code review at every milestone checks that nothing lock- or passcode-related touches `Log`.
