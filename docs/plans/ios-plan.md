# SafeBox — iOS Build Plan (Iteration 1)

**Document:** `docs/plans/ios-plan.md`
**Scope:** How to build the iteration-1 SafeBox iOS app under `ios/`. Product behavior (calculator disguise, passcode-as-key-sequence, four tabs, full local persistence) is defined in the **idea plan** (`docs/plans/idea-plan.md`) — that document is the source of truth for behavior, the domain model, the calculator semantics table, and the definition of done. This document covers the iOS implementation only; wherever the two disagree, the idea plan wins.
**Toolchain assumption (2026-08):** Xcode 17.x / iOS 18 SDK era, deployment target iOS 17.0, Swift 6 language mode, latest stable SwiftLint (optional).

---

## 1. Project setup

### 1.1 XcodeGen over raw `.xcodeproj` — **decision: XcodeGen**

This repo is edited by both humans and coding agents. Raw `.xcodeproj` files are a merge-conflict and hallucination hazard: `project.pbxproj` is a semi-opaque plist with UUID references that agents routinely corrupt when adding files, and that produces unreadable diffs in review.

With XcodeGen:

- `ios/project.yml` is the committed source of truth — small, declarative, diff-friendly, safe for agents to edit.
- `ios/*.xcodeproj` is generated and **gitignored**.
- Adding a Swift file requires no project edit at all (targets use directory-based `sources:` globs), which is exactly the operation agents perform most.
- Humans run `xcodegen generate` (installed via Homebrew or Mint) before opening Xcode; a tiny `ios/Makefile` target (`make project`) documents this.

**`project.yml` sketch (key settings, not exhaustive):**

```yaml
name: SafeBox
options:
  deploymentTarget:
    iOS: "17.0"
settings:
  SWIFT_VERSION: "6.0"   # Swift 6 language mode already implies complete concurrency checking;
                         # do NOT also set SWIFT_STRICT_CONCURRENCY (redundant under Swift 6)
targets:
  SafeBox:
    type: application
    platform: iOS
    sources: [SafeBox]
    settings:
      PRODUCT_BUNDLE_IDENTIFIER: com.calcplus.calculator   # disguise identity — see §1.2
    info:
      path: SafeBox/Info.plist
      properties:
        UILaunchScreen: {}
        CFBundleDisplayName: Calculator+     # disguise: home-screen name (idea-plan decision)
  SafeBoxTests:
    type: bundle.unit-test
    platform: iOS
    sources: [SafeBoxTests]
    settings:
      PRODUCT_BUNDLE_IDENTIFIER: com.calcplus.calculator.tests
    dependencies: [{ target: SafeBox }]
```

### 1.2 Decisions

| Item | Decision |
|---|---|
| Bundle id | **`com.calcplus.calculator`** (tests: `com.calcplus.calculator.tests`). Fixed by the idea plan's disguise-identity rule: identifiers are externally visible (Settings, provisioning, crash logs) and must pass the "looks like a calculator" test — they must never reference SafeBox or the owner, and they are **permanent once shipped** |
| Display name / icon | **"Calculator+"** + a neutral, flat, non-trademark calculator glyph — the single product-wide disguise identity from the idea plan (same on Android). Deliberately not Apple's Calculator icon (App Store rejection / trademark risk) |
| Deployment target | **iOS 17.0** — minimum for SwiftData and `@Observable`. Consequence: **no iOS 18-only SwiftData features anywhere** — no `#Unique`, no `#Index`, no history-tracking API; uniqueness is expressed with the iOS 17-compatible `@Attribute(.unique)` (§3.2) |
| Swift language mode | **Swift 6** from day one (complete concurrency checking is implied by the language mode — no separate flag). Cheaper to start strict than migrate later; view models are `@MainActor`, repositories are actors or `Sendable` structs |
| Dependencies | **Zero third-party runtime dependencies** in iteration 1. SwiftUI, SwiftData, PhotosUI, CryptoKit (random bytes), and **CommonCrypto** (PBKDF2 — a system library, not a new dependency) cover everything. No LocalAuthentication in iteration 1 — biometric unlock is out of scope (§4.5). SwiftLint as an optional build-phase/CI tool only |
| SwiftLint | Optional but recommended: `.swiftlint.yml` committed, run in CI; **not** a hard local build phase (agents' environments may lack it) |
| Logging | **No-logging rule** (idea plan): never log key tokens, key buffers, candidate sequences, salts, or hashes — not via `os_log`, `print`, or assertion messages. Lock-subsystem internals are never logged at any level; any debug logging elsewhere is compiled out of release builds (`#if DEBUG`). This is the iOS counterpart of Android's R8 log-stripping rule |

---

## 2. Architecture

### 2.1 Overall shape

**MVVM with `@Observable` view models**, repository protocols in front of persistence, and a top-level lock-state coordinator.

```
SwiftUI Views  →  @Observable ViewModels  →  Repository protocols  →  SwiftData / FileStore / Keychain
                         ↑
              AppContainer (DI, constructed at launch; view models built by parents)
```

- **Views** are dumb; every screen with logic gets a view model.
- **View models** are `@MainActor @Observable` classes constructed with their repository dependencies; no singletons inside them.
- **Repositories** are protocols (`PhotoRepository`, `NoteRepository`, `ContactRepository`, `PasscodeStore`) with production implementations backed by SwiftData/files/Keychain and in-memory fakes for tests and previews. (No `SettingsStore` in iteration 1 — nothing user-configurable is persisted: auto-lock is always immediate and the only Settings actions are change-passcode and Lock now.)

### 2.2 Dependency injection — parent-constructed view models

No DI framework. One `AppContainer` struct built at app launch:

```swift
@MainActor
struct AppContainer {
    let modelContainer: ModelContainer
    let photoRepository: any PhotoRepository
    let noteRepository: any NoteRepository
    let contactRepository: any ContactRepository
    let passcodeStore: any PasscodeStore
    let lockCoordinator: AppLockCoordinator

    static func live() -> AppContainer { ... }
    static func preview() -> AppContainer { ... }  // in-memory everything
}
```

**The DI pattern is: the parent constructs the child's view model and passes it into the child's `init`.** `RootView` holds the container and builds the top-level screens' view models; each screen that pushes another screen builds that screen's view model from the dependencies it already holds (or from the container passed down as a plain `let`).

```swift
// parent
NavigationLink("Edit") {
    ContactEditScreen(viewModel: ContactEditViewModel(contact: contact,
                                                      repository: contactRepository))
}

// child
struct ContactEditScreen: View {
    @State private var viewModel: ContactEditViewModel
    init(viewModel: ContactEditViewModel) {
        _viewModel = State(initialValue: viewModel)
    }
}
```

**Explicitly forbidden:** reading `@Environment` values inside a `View.init` to build the view model there — environment values are not available in `init`, and this exact pattern is where agent-written SwiftUI most often goes wrong. If a screen genuinely must self-construct (rare), the pattern is `@State private var viewModel: X?` populated in `.task` from the environment — but the parent-constructs pattern is the default and is used uniformly. `preview()` powers all `#Preview` blocks and UI iteration without a device.

### 2.3 App lock state machine — `AppLockCoordinator`

A single `@MainActor @Observable` class owning the lock state; the root view is a pure switch over it.

```swift
enum LockState {
    case firstRunSetup(SetupPhase)   // SetupPhase: .enterNew, .confirm(pending: [CalcKey])
    case locked
    case unlocked
}
```

**Launch sequence (fresh-install detection first):**

1. Read the **install sentinel** from `UserDefaults`. If absent → this is a fresh install (or first launch after reinstall): **delete all SafeBox Keychain items**, write the sentinel, and continue as if no passcode exists. This fixes the Keychain-survives-uninstall bug: without it a reinstalled app would boot `.locked` behind a stale hash nobody knows (see Risk 6).
2. `passcodeStore.hasPasscode ? .locked : .firstRunSetup(.enterNew)`.

**Setup transitions (first run):**

- `firstRunSetup(.enterNew)` + commit of a valid sequence (4–32 keys, no overflow) → **hold the pending plain key sequence in memory** and go to `.confirm(pending:)`. No hashing yet — the sequence was just typed, keeping it in memory for the confirm step is fine, and it avoids the salt-coordination problem of hashing twice.
- `firstRunSetup(.confirm)` + commit → compare **plain sequences**; match ⇒ `passcodeStore.set(sequence:)` (salt generated and PBKDF2 hash computed exactly once, here) → show the one-time "there is no recovery — a forgotten passcode means starting over" notice → `.unlocked`. Mismatch ⇒ discard both, back to `.enterNew` with a visible cue (setup is the one lock-screen phase where feedback is allowed).
- Sub-minimum commit (<4 keys) during setup: stay in the current phase with a caption nudge ("use at least 4 keys"); nothing is stored or compared.
- Overflow (>32 keys, §4.1) during setup: "start again" banner, buffer discarded, stay in phase.
- **Backgrounding mid-setup discards both buffers** (pending and current) and returns to `.enterNew` — fail closed, same as a process kill.
- Setup copy nudges toward **≥6 keys including at least one symbol**, and a **soft warning fires for trivial sequences** (a single repeated key, e.g. `7777`) — warn, don't block. Setup copy also notes the accidental-unlock collision risk: a passcode like `1 2 + 3 4` is real arithmetic that a borrower of the "calculator" could type by accident (see idea plan for the shared copy guidance).

**Locked transitions:**

- Calculator commits a sequence (`=` pressed): the arithmetic result has **already rendered** (§4.1 — engine first, always). If the buffer is sub-minimum (<4 keys) or the overflow flag is set, **skip the compare entirely** — no Keychain read, no KDF. Otherwise verification runs **off the UI path** (background task: PBKDF2 600k + timing-safe compare, §3.4); match ⇒ `.unlocked`; non-match ⇒ do nothing, forever, silently. Rendering is byte-identical for match and non-match until the unlock transition fires — no spinner, no delay difference on the display path.
- `.unlocked` → `.locked` on: auto-lock policy firing (below), or explicit "Lock now" in Settings.
- **Locking clears the calculator display and the recorder buffer.** Both are also cleared on backgrounding while `.locked`, so a half-typed passcode never survives a background/foreground cycle for the next person holding the phone.

**Auto-lock policy (idea plan §re-lock model — single model):**

- **Backgrounding locks immediately, always.** There is no grace period, no timeout picker, and nothing persisted: on scene phase `.background`, call `lock()` right there — unless `systemUIInFlight` is set (below). There is **no foreground idle timer** in iteration 1 (documented simplification in the idea plan).
- **Auto-lock suppression for app-initiated system UI:** presenting `PhotosPicker` (and any permission dialog we ever add) sets an `systemUIInFlight` flag on the coordinator; while set, the background transition does not lock. The flag is cleared on picker dismissal/result, and carries a **hard cap** (2 minutes of backgrounded time, pinned in idea plan §2.5.1 and shared with Android) measured with a **monotonic clock** — on `.background` with the flag set, record `ProcessInfo.processInfo.systemUptime`; on `.active`, lock iff `now − recorded > cap`. Never wall-clock (`Date`) — the user can change it; if the uptime baseline is missing or inconsistent (process restart, reboot), **fail closed: lock**. Beyond the cap the app locks anyway. If the lock fired despite the exemption (cap exceeded, process death), the in-flight **import still completes at the repository level, keyed by albumId** — `PhotoImporter` is owned by the container, not by a vault view, so the returning picker result is consumed and persisted even though the user resurfaces on the calculator.

**Root view:**

```swift
struct RootView: View {
    var body: some View {
        ZStack {
            switch coordinator.state {
            case .firstRunSetup, .locked: CalculatorScreen(...)   // same screen, mode flag
            case .unlocked: MainTabView()
            }
            if showCover { CalculatorCoverView() }   // §8 Risk 3
        }
    }
}
```

**Snapshot cover (deliberate iOS mechanism, per idea plan §disguise):** a full-screen calculator-face cover view is installed the moment the scene **resigns active** (`scenePhase == .inactive` — which fires for the app switcher, notification shade, incoming calls, not just backgrounding) and removed on `.active`. This is **independent of the lock decision**: even during a §2.5.1 suppression window where the vault stays unlocked, the app-switcher snapshot must never show vault content. iOS cannot block screenshots; Android's counterpart is unconditional `FLAG_SECURE` — a documented, deliberate divergence recorded in the idea plan.

### 2.4 Change passcode — `PasscodeEntrySession`

The `LockState` machine above intentionally has no change-passcode states: the change flow happens entirely **while `.unlocked`**, inside Settings. It is modeled by a separate `@MainActor @Observable` **`PasscodeEntrySession`** object that `CalculatorScreen` consumes when presented from Settings:

```swift
enum PasscodeSessionPhase {
    case verifyCurrent
    case enterNew
    case confirm(pending: [CalcKey])
    case done
}
```

- **VerifyCurrent:** the user re-enters the current passcode on the calculator screen (caption: "Enter your current passcode, then press ="). Wrong current code ⇒ **visible feedback** — a shake animation plus "Incorrect passcode — try again" caption. Silence is a disguise feature **only on the lock screen**; inside the already-unlocked vault it would just be broken UX. Unlimited retries in iteration 1 (matches Android; idea-plan decision). Verification itself uses the same off-main PBKDF2 + timing-safe compare path as unlock.
- **EnterNew → Confirm:** same rules as first-run setup — 4–32 keys, plain-sequence in-memory comparison, ≥6-keys-with-symbol nudge, trivial-sequence soft warning, overflow "start again" banner. **Mismatch on confirm** ⇒ visible "Sequences didn't match" message, back to `.enterNew`. Hash is computed only on successful confirm, with a **fresh salt**.
- **Cancel:** a Cancel button is visible in every phase (this is a Settings flow, not the disguise); it dismisses the session with no state change — the old passcode remains valid.
- On success (`.done`): the Keychain item is replaced atomically (old hash fails from that moment, new one works); the app **stays unlocked**.

First-run setup remains driven by `AppLockCoordinator.SetupPhase` (it must exist before Settings does); both flows share their validation logic through a small `PasscodeRules` helper (min/max length, overflow, triviality check) so the rules cannot drift apart.

---

## 3. Persistence

### 3.1 SwiftData vs Core Data — **decision: SwiftData**

- Deployment target is already iOS 17+ (chosen for `@Observable` anyway), so SwiftData's floor costs nothing.
- The schema is small and simple (5 entities, shallow relationships) — well inside SwiftData's comfort zone; none of Core Data's remaining advantages (complex migrations, derived attributes, `NSFetchedResultsController` edge cases, CloudKit fine-tuning) apply to a local-only iteration-1 app.
- SwiftData models are plain annotated Swift — far friendlier to agent edits than `.xcdatamodeld` (which is another opaque bundle format, same problem as `.pbxproj`).
- Mitigation for SwiftData's weaker migration story is in §8 (Risks). **Constraint restated:** only iOS 17-era SwiftData API is allowed — `@Attribute(.unique)`, `@Relationship`, `#Predicate`, `FetchDescriptor`; the iOS 18-only `#Unique`/`#Index` macros and history API are off-limits at this deployment target.

Repositories fully hide SwiftData; if a wall is hit, swapping an implementation to Core Data does not touch views or view models.

### 3.2 Schema (SwiftData `@Model` classes — mirrors the idea plan's authoritative domain model)

| Model | Fields | Relationships |
|---|---|---|
| `Album` | `id: UUID`, `name: String`, `createdAt: Date`, `sortIndex: Int` | `photos: [Photo]` (cascade delete — but see file-deletion rule, §3.3). **No `coverPhotoId`**: the album cover is *derived* — first photo by `sortIndex` — so there is no dangling-FK invariant to maintain when the cover photo is deleted or moved |
| `Photo` | `id: UUID`, `fileName: String` (`<uuid>.<real extension>`), `thumbFileName: String`, `mimeType: String`, `width/height: Int`, `byteCount: Int`, `importedAt: Date`, `sortIndex: Int` (import order; grid ordering key) | `album: Album` — **required** (every photo belongs to exactly one album; import always targets an album; album-less photos would be unreachable orphans) |
| `Note` | `id: UUID`, `title: String` (**derived, denormalized** — first non-empty line of `body`, markdown-stripped; recomputed on every save), `snippet: String` (**derived, denormalized** — the lines *after* the title line, markdown-stripped; recomputed on every save), `body: String` (raw markdown), `createdAt: Date`, `updatedAt: Date` | `tags: [Tag]` (many-to-many, nullify) |
| `Tag` | `id: UUID`, `name: String` — unique via **`@Attribute(.unique)`** (iOS 17-compatible; the `#Unique` macro is iOS 18-only and must not be used), `colorIndex: Int` | `notes: [Note]` |
| `Contact` | `id: UUID`, `givenName: String?`, `familyName: String?`, `organization: String?` (**at least one of the three required** — enforced in the edit view model and repository), `phones: [LabeledValue]`, `emails: [LabeledValue]` (multi-value, labeled), `address: String?`, `notes: String?`, `createdAt/updatedAt: Date` | — (`LabeledValue` is a small `Codable` struct `{label, value}` stored as a codable attribute) |

**Codable-attribute caveat (stated so nobody burns time on it):** SwiftData `#Predicate` cannot query *into* codable attributes like `phones`/`emails` — therefore **contact search is in-memory** over the fetched contact list (§4.4). Fine at vault scale; documented as an accepted iteration-1 implementation.

`ModelContainer` is created with a custom store URL inside Application Support (see below) so **all** persistent data lives in one relocatable directory. That directory is flagged `isExcludedFromBackup` (backup exclusion) and its files carry per-file protection (§3.3) — two separate mechanisms for two separate threats, per the idea plan's data-protection section.

### 3.3 Photo binary storage — files, not blobs

```
<Application Support>/SafeBox/
  store.sqlite (+ -wal/-shm)     (SwiftData)
  Photos/<uuid>.<ext>            (ORIGINAL bytes, byte-for-byte; real extension: .heic/.jpg/.png/…)
  Thumbnails/<uuid>.jpg          (~300pt max edge JPEG, generated at import)
```

- DB stores metadata + file names only. Blobs in SQLite would bloat the store and slow every fetch.
- **Import preserves original bytes** — no JPEG re-encode, ever (idea-plan parity decision: recompression is silent, lossy, and would be asymmetric with Android's byte-copy). The real extension and MIME type are detected via `CGImageSource`/UTType and stored (`fileName`, `mimeType`); HEIC stays HEIC. **EXIF (including location) is retained** — noted explicitly; metadata stripping is an iteration-2 option, recorded in the idea plan.
- **Thumbnails are a separate generated file per photo** (both platforms do this), created at import (§4.2); the master is never decoded at grid time.
- A `PhotoFileStore` actor owns all file IO (write, read, delete, orphan sweep).
- **Deletion deletes bytes, not just rows:** deleting a photo removes the DB row *and* both files; deleting an album **first enumerates its photos and deletes each photo's full-size + thumbnail file, then deletes the rows** (the SwiftData cascade must never be the only mechanism — cascade removes rows without touching `PhotoFileStore`, silently leaving "deleted" vault photos on disk). The **startup orphan sweep is a backstop only** (crash recovery), not the deletion mechanism. DoD: no orphan files after album/photo delete.
- **File protection — applied per-file at write time**, not as a directory attribute (directory attributes don't reliably propagate to files created later, including SQLite's `-wal`/`-shm` sidecars): every photo/thumbnail write uses `Data.write(…, options: [.completeFileProtectionUnlessOpen])`, and the same class is set via `FileManager` attributes on `store.sqlite`, `-wal`, and `-shm` after container creation. **`.completeUnlessOpen`, not `.complete`**: complete protection makes writes fail once the device locks, which breaks debounced note autosaves and background import completion; unless-open still gives at-rest encryption for everything closed. Pending note autosaves are additionally **flushed synchronously on scene-inactive** (§4.3) rather than trusting the debounce timer.
- **Picker staging cleanup:** `PhotosPicker` stages transferred data in `tmp/`; the app **cleans `tmp/` on every lock transition and on launch** so no vault-bound image bytes linger outside `SafeBox/`.

### 3.4 Passcode storage — Keychain, PBKDF2

Never store the key sequence in plaintext. The passcode is the **ordered sequence of canonical calculator key IDs** — `D0…D9, DOT, ADD, SUB, MUL, DIV, PCT, SIGN` — **serialized by joining the IDs with `|`** (e.g. `D7|ADD|D3|DOT`). This serialization is the cross-platform contract (same on Android): the same key sequence must produce the same input bytes to the KDF on both platforms.

- **KDF (pinned by the idea plan for both platforms):** **PBKDF2-HMAC-SHA256, 600,000 iterations** (current OWASP figure for SHA-256), **16-byte random salt** (`SecRandomCopyBytes`), 256-bit output. Implemented via **CommonCrypto `CCKeyDerivationPBKDF`** — a system library, so still zero third-party dependencies. (CryptoKit's HKDF is *not* an alternative: HKDF is a key-expansion function, not a stretching KDF, and provides no brute-force cost.)
- **Stored blob:** `{algo: "PBKDF2-HMAC-SHA256", version: 1, iterations: 600000, salt, hash}`, encoded and stored as a single Keychain item (service `com.calcplus.calculator`) with **`kSecAttrAccessibleWhenUnlockedThisDeviceOnly`** — never migrates to another device via backup. The versioned envelope allows raising iterations or swapping algorithms later without a breaking change.
- **Install sentinel:** a `UserDefaults` flag written on first launch. Keychain items survive app deletion; UserDefaults does not. Sentinel absent at launch ⇒ delete all SafeBox Keychain items ⇒ fresh-install setup (§2.3). `hasPasscode` is therefore *Keychain item present AND sentinel present*.
- **Verification runs off the UI path.** On `=` while locked, the arithmetic result renders immediately (identically for match and non-match); the PBKDF2 (~100–300 ms) + compare runs in a background task and, on match, flips the lock state. No latency or rendering difference is observable on the display path. Sub-minimum/overflowed buffers never reach this code (§2.3).
- **Timing-safe compare:** the derived hash and stored hash are compared with a **constant-time XOR-accumulate loop** over the 32 bytes. `Data`'s `==` is **not** constant-time and must not be used here (and must not be *called* constant-time in comments).
- **Honest threat framing (from the idea plan, restated):** this hash gate is a **UI lock, not encryption**. The vault contents are protected by the sandbox + file protection, not by the passcode. A short digits-only key sequence is low-entropy; an attacker who extracts the Keychain blob can brute-force the realistic keyspace offline even at 600k iterations. Silent, unlimited guessing on the calculator is an accepted iteration-1 risk — the disguise is the control.
- **No-logging rule applies with full force here:** no token, buffer, sequence, salt, or hash ever reaches any log, assertion message, or error description.
- `PasscodeStore` protocol: `hasPasscode`, `set(sequence:)`, `matches(sequence:) async -> Bool`, `clear()`. A `KeychainWrapper` helper isolates the `SecItem*` C API and is injectable (tests use `FakeKeychain`).

---

## 4. Feature build notes

### 4.1 Calculator engine + passcode capture

**Two consumers of one key stream.** Every key press produces a `CalcKey` token using the canonical IDs (`D0…D9, DOT, ADD, SUB, MUL, DIV, PCT, SIGN`, plus the non-passcode keys `CLEAR` and `EQUALS`). The `CalculatorViewModel` forwards each token to:

1. **`CalculatorEngine`** (pure struct, fully unit-testable): a small state machine — `enteringFirst`, `operatorPending`, `enteringSecond`, `result` — implementing **iOS Calculator basic-mode semantics as specified in the idea plan's calculator behavior section**: immediate execution (no precedence), operator replacement on consecutive operators, unary/binary `%`, repeated `=` repeating the last operation, `±` edge cases on empty/result, decimal dedup, `C/AC`, division by zero (`Error` display), display precision/overflow rules. Display math uses `Decimal`, not `Double` (avoids `0.1+0.2` artifacts). **The idea plan's shared input→display sequence table — including degenerate all-symbol sequences — is the acceptance contract**: the iOS engine must reproduce it exactly, and it is in the DoD (Android reproduces the same table). Display formatting details (9-significant-digit entry cap, grouping separators, scientific/overflow presentation) are specified in `docs/plans/calculator-disguise-design.md` §4 as a coordinated amendment — adopt them in the engine's formatting and extend the table-driven tests accordingly.
2. **`PasscodeRecorder`**: appends each token to a buffer of canonical IDs. **The buffer is capped at 32 tokens; the 33rd key sets an overflow flag** — an overflowed buffer can never match on commit (setup: "start again" banner; locked: silently no-match, compare skipped). `C`/`AC` clears the buffer *and* the flag (natural "start over" gesture). On `=`: first pass the token to the engine (so the display shows a real result — the disguise never flinches), then hand the buffer *excluding the trailing `=`* to `AppLockCoordinator.commit(sequence:)`, then reset buffer + flag. The buffer is also cleared on every lock transition and on backgrounding while locked (§2.3), so it never grows or lingers across sessions.

**Commit gesture:** `=`. Documented rule (idea plan): *a passcode is any sequence of 4–32 non-`=` keys ended by `=`*. Commits shorter than 4 keys are discarded without any store compare. During first-run setup the same screen runs in setup mode with a discreet one-line caption above the display ("Enter a key sequence, then press =" / "Repeat it to confirm") — visible only in setup and in the Settings change flow, never when locked. Setup copy nudges ≥6 keys including a symbol and soft-warns on trivial sequences (§2.3).

**UI:** `CalculatorScreen` = display area (right-aligned monospaced-digit text, auto-shrinking) + `Grid` of `CalcButton`s styled like a standard 4×5 calculator, dark background, orange operators. Landscape/scientific mode is out of scope. The same screen serves three consumers via a mode flag: lock screen (`AppLockCoordinator`), first-run setup (`SetupPhase`), Settings change flow (`PasscodeEntrySession`, §2.4).

### 4.2 Gallery

- **Album list:** `AlbumListScreen` is a **card grid** — each card shows a cover thumbnail (the album's **first photo by `sortIndex`**, derived; blank placeholder card when empty), the album name, and the photo count. Create/rename/delete via alert/confirmation dialog. (Card-grid-with-cover is the pinned cross-platform presentation.) Albums themselves are ordered by their own `sortIndex`.
- **Import:** SwiftUI **`PhotosPicker`** (PhotosUI), `matching: .images` — **still images only** (no video in iteration 1; Live Photos import their still representation), multi-select. No photo-library permission dialog ever appears (§8 Risk 2). Presenting the picker sets the coordinator's `systemUIInFlight` flag (§2.3) so the round-trip cannot auto-lock the vault; import runs at repository level keyed by `albumId`, so it completes even if the lock fired anyway. Per item: `loadTransferable(type: Data.self)` → **write the original bytes untouched** with the real extension detected via `CGImageSource`/UTType → generate the thumbnail file → insert the `Photo` row (`sortIndex` = append order). Progress overlay for multi-select batches.
- **Thumbnails:** generated at import with **`CGImageSourceCreateThumbnailAtIndex`** (`kCGImageSourceCreateThumbnailFromImageAlways`, max pixel edge ≈ 600px for 300pt @2x) — cheap and memory-safe versus decoding full `UIImage`s; the master file is read but never re-encoded. Grid cells load thumbnail files only, via a small async image loader with an `NSCache`.
- **Grid:** `AlbumGridScreen` (`LazyVGrid`, 3 columns, ordered by `sortIndex`), select-mode for delete/move-to-album. Deletion removes rows *and* files (§3.3).
- **Pager — gesture strategy specified (this is the hardest Gallery UI):** `PhotoPagerScreen` uses `TabView(selection:)` with `.tabViewStyle(.page(indexDisplayMode: .never))` for horizontal paging; each page hosts `ZoomableImageView`. **Rule: page-swiping is disabled whenever the zoom scale > 1** — while zoomed, horizontal drags pan the image, and the page gesture is not in play (gesture disambiguation via disabling the `TabView` swipe, e.g. a simultaneous high-priority drag consuming horizontal drags when zoomed). **Pan is clamped to the image bounds** (no dragging the image off-screen). **Zoom resets to 1× on page change.** **Double-tap toggles 1× ↔ 2.5×; pinch zoom max 5×** (pinned shared constants — identical on Android). If pure-SwiftUI gesture composition proves unreliable in practice, the sanctioned fallback is a **`UIScrollView`-backed `UIViewRepresentable`** zoom container (UIScrollView solves zoom/pan/paging arbitration natively); the plan allows this swap without renegotiation. Pan clamping and zoom-reset-on-page-change are in the manual test script.
- **Memory:** full-size image decoded per page with `CGImageSource` downsampling to screen scale; at most 3 pages' images alive (§8 Risk 10).
- **Deleting from system library:** out of scope for iteration 1 (PhotosPicker gives no delete capability without full library authorization). The import UI copy says "Copied into SafeBox — you can remove the originals in Photos."

### 4.3 Notes

- **Derived-title model (idea plan, Apple-Notes-style — the editor has no separate title field):** on every save, `title` = first non-empty line of `body`, markdown-stripped (leading `#`, list/checklist markers, emphasis syntax removed); `snippet` = the following lines, markdown-stripped, **excluding the title line** (so list rows never show the title twice). Both are recomputed on every save and stored denormalized on `Note` for cheap list rendering. Fallback title for an empty body: "New note". The derivation rules and shared example table live in the idea plan; `NoteDerivation` implements them and its mapper tests assert the table.
- **List:** `NotesListScreen` — rows show derived title, 2-line snippet, relative date; sorted by `updatedAt` desc; swipe-to-delete **with a confirmation dialog and no undo** (pinned parity decision — Android drops its undo snackbar); searchable (`.searchable`, contains-match over title + body). **Tag filtering via a filter menu is firmly in iteration-1 scope** (pinned; tags are part of the core ask).
- **Editor:** `NoteEditorScreen` — a plain `TextEditor` bound to the raw markdown `body`, with an **Edit / Preview toggle** in the toolbar. Preview renders via `AttributedString(markdown:)` inside a scrollable `Text`, with a thin per-line block-level pre-pass. **Supported markdown subset (the shared cross-platform contract from the idea plan):** headings, bold/italic, inline code, bullet and numbered lists, and **checklists rendered as non-interactive styled text** (tap-to-toggle is iteration 2). Android's renderer is constrained to the same subset; the fidelity note lives in the idea plan. A live rich-text editor is explicitly out of scope.
- **Tags:** chip row in the editor (`TagChipsView`): existing tags shown as toggleable chips + a "+" chip opening a small add-tag sheet; tag creation dedupes by case-insensitive name (backed by `@Attribute(.unique)` on `Tag.name`); chips tinted by `colorIndex`.
- **Autosave (pinned contract):** **1 s debounce** after the last keystroke **plus a synchronous flush on editor exit and on scene-inactive** — the flush on `scenePhase != .active` is mandatory, not an optimization, because a debounced write racing the device lock could otherwise be lost (§3.3 file-protection interaction). `updatedAt` bumps only on real change; title/snippet recompute on the same save path.

### 4.4 Contacts

Entirely self-contained — **no** `CNContactStore`, no Contacts permission; these are vault-private contacts.

- **List:** `ContactsListScreen` — alphabetical sections with a **familyName-first sort key** and defined fallbacks (familyName → givenName → organization), non-letter/empty keys bucketed under **`#`** (pinned cross-platform rule); `.searchable` with **search over name + organization + phone numbers + email addresses**. **Search is in-memory** over the fetched contacts (SwiftData predicates cannot reach into the codable `phones`/`emails` attributes — stated here so no one attempts a `#Predicate`); fine at vault scale. Plain sections in iteration 1; an index bar is nice-to-have. `+` toolbar button.
- **Detail:** `ContactDetailScreen` — name header, rows for labeled phones and emails, address, organization, notes; Edit button. **No `tel:`/`mailto:` links** (pinned decision: handing a vault-private number to the system dialer/mail app leaks vault data outside the disguise and was contradicting the idea plan). Instead, **long-press on a phone/email/address row copies the value to the clipboard**, with a brief "Copied" confirmation. Clipboard caveat (universal clipboard / clipboard managers can expose the value) is noted in the idea plan.
- **Edit form:** `ContactEditScreen` — `Form` with given/family/organization fields, dynamic labeled phone/email rows (add/remove, label picker: mobile/home/work/other), address, notes; used for both create and edit; delete with confirmation from detail. Validation: **at least one of givenName / familyName / organization** must be non-empty (org-only contacts are legal).

### 4.5 Settings

`SettingsScreen` — a `Form` with clearly grouped sections. **Iteration-1 contents are exactly: Change passcode, Lock now, About** (pinned by the idea plan — there is no auto-lock setting; backgrounding always locks immediately per §2.3).

- **Security:**
  - **Change passcode** — presents `CalculatorScreen` driven by a `PasscodeEntrySession` (§2.4): VerifyCurrent → EnterNew → Confirm, with visible wrong-current feedback, cancel, and mismatch handling.
  - **Lock now** button — calls `lockCoordinator.lock()` immediately (both platforms expose this).
- **About:** version/build (from bundle) + a short "how it works" blurb. No licenses row — iteration 1 has zero third-party dependencies (Android's About *does* list licenses for its markdown/Coil libraries; the asymmetry is recorded in the idea plan).
- **Biometric unlock is NOT in iteration 1.** Removed from scope per the idea plan's decision register: it was scope creep with unresolved disguise questions (auto-prompt vs hidden gesture diverged across platforms). It is a **roadmap item for iteration 2**, with the open questions recorded in the idea plan. Consequences here: no toggle in Settings, no `BiometricUnlocker`, no LocalAuthentication import, and **no `NSFaceIDUsageDescription` in Info.plist** (its very presence in the binary would out the "calculator").
- **Future placeholders** (decoy passcode, break-in alerts, disguise themes): commented in code, not shown.

---

## 5. Proposed file tree under `ios/`

```
ios/
  project.yml                          # XcodeGen spec (source of truth)
  Makefile                             # make project / make lint
  .swiftlint.yml                       # optional lint config
  MANUAL_TESTS.md                      # manual test script executed at M8 (§7.2)
  SafeBox/
    App/
      SafeBoxApp.swift                 # @main; builds AppContainer.live(); install-sentinel check
      AppContainer.swift               # DI container: live() and preview() factories
      RootView.swift                   # switch over LockState; immediate lock on scenePhase .background; cover view
      CalculatorCoverView.swift        # calculator-face snapshot cover (willResignActive)
    Lock/
      AppLockCoordinator.swift         # LockState machine; commit(sequence:), lock(), setup flow,
                                       # immediate lock-on-background, systemUIInFlight suppression + monotonic hard cap
      LockState.swift                  # LockState + SetupPhase enums
      PasscodeEntrySession.swift       # Settings change-passcode flow: VerifyCurrent/EnterNew/Confirm
      PasscodeRules.swift              # shared validation: 4–32 keys, overflow, trivial-sequence check
      PasscodeStore.swift              # protocol + KeychainPasscodeStore ({algo,version,iters,salt,hash})
      PBKDF2.swift                     # CommonCrypto CCKeyDerivationPBKDF wrapper + XOR-accumulate compare
      KeychainWrapper.swift            # thin typed wrapper over SecItem APIs (injectable)
    Calculator/
      CalcKey.swift                    # canonical key IDs (D0…D9, DOT, ADD, SUB, MUL, DIV, PCT, SIGN)
                                       # + "|"-joined serialization
      CalculatorEngine.swift           # pure Decimal-based basic-calculator state machine (idea-plan table)
      PasscodeRecorder.swift           # capped (32) key buffer + overflow flag; commit/clear semantics
      CalculatorViewModel.swift        # feeds keys to engine + recorder; display state; screen mode
      CalculatorScreen.swift           # display + button grid; setup/verify/change captions
      CalcButton.swift                 # reusable calculator key view
    Vault/
      MainTabView.swift                # 4-tab TabView shown when unlocked
    Gallery/
      PhotoRepository.swift            # protocol + SwiftDataPhotoRepository (file-deleting deletes)
      PhotoFileStore.swift             # actor: file IO for Photos/ + Thumbnails/, per-file protection,
                                       # orphan sweep (backstop), tmp/ staging cleanup
      PhotoImporter.swift              # PhotosPickerItem -> original bytes + real ext -> thumb + DB row;
                                       # container-owned, survives lock (keyed by albumId)
      ThumbnailGenerator.swift         # CGImageSource downsampling helpers
      ImageLoader.swift                # async file->UIImage with NSCache
      AlbumListViewModel.swift         # albums CRUD + counts + derived covers (first photo by sortIndex)
      AlbumListScreen.swift            # album CARD GRID: cover thumbnail + name + count
      AlbumGridViewModel.swift         # photos in album; selection; delete/move; import trigger
      AlbumGridScreen.swift            # LazyVGrid + PhotosPicker + select mode
      PhotoPagerScreen.swift           # paged TabView; zoom disables page-swipe; zoom reset on page change
      ZoomableImageView.swift          # double-tap 2.5x / pinch max 5x / clamped pan
                                       # (UIScrollView-backed representable = sanctioned fallback)
    Notes/
      NoteRepository.swift             # protocol + SwiftDataNoteRepository (notes + tags)
      NoteDerivation.swift             # title/snippet derivation (markdown-stripped; idea-plan table)
      NotesListViewModel.swift         # sorted/filtered/searchable note rows
      NotesListScreen.swift            # list UI + tag filter menu; swipe-delete w/ confirmation, no undo
      NoteEditorViewModel.swift        # draft state, 1s-debounce autosave + scene-inactive flush, tags
      NoteEditorScreen.swift           # TextEditor + preview toggle
      MarkdownPreview.swift            # AttributedString(markdown:) + block pre-pass, shared subset only
      TagChipsView.swift               # chip row + add-tag sheet (colorIndex tints)
    Contacts/
      ContactRepository.swift          # protocol + SwiftDataContactRepository
      ContactsListViewModel.swift      # familyName-first sections + '#' bucket; in-memory search
      ContactsListScreen.swift         # sectioned searchable list
      ContactDetailScreen.swift        # read-only detail; long-press copy (no tel:/mailto)
      ContactEditViewModel.swift       # form state + validation (≥1 of given/family/org)
      ContactEditScreen.swift          # create/edit form with labeled multi-value phones/emails
    SettingsFeature/
      SettingsViewModel.swift          # settings state + actions
      SettingsScreen.swift             # Change passcode / Lock now / About
      ChangePasscodeFlow.swift         # hosts CalculatorScreen driven by PasscodeEntrySession
    Persistence/
      Models.swift                     # @Model Album, Photo, Note, Tag, Contact (+ LabeledValue)
      ModelContainerFactory.swift      # container at App Support/SafeBox/store.sqlite;
                                       # per-file protection incl. -wal/-shm; backup exclusion
    Support/
      Assets.xcassets/                 # neutral calculator app icon, accent color
      Info.plist                       # CFBundleDisplayName "Calculator+"; launch screen;
                                       # NO NSFaceIDUsageDescription (no biometrics in iteration 1)
      PrivacyInfo.xcprivacy            # privacy manifest (no tracking, UserDefaults reason code)
  SafeBoxTests/
    CalculatorEngineTests.swift        # idea-plan input→display table incl. degenerate sequences
    PasscodeRecorderTests.swift        # buffer/commit/clear + 32-cap overflow-never-matches semantics
    PasscodeStoreTests.swift           # PBKDF2 vectors, salt uniqueness, wrong-seq reject, versioned blob
    AppLockCoordinatorTests.swift      # setup->confirm->lock->unlock table; sentinel wipe; monotonic relock
    PasscodeEntrySessionTests.swift    # verify-current wrong/right, mismatch, cancel, replace-on-confirm
    NoteDerivationTests.swift          # title/snippet mapper vs shared example table
    NoteRepositoryTests.swift          # CRUD + tags on in-memory ModelContainer
    ContactRepositoryTests.swift       # CRUD + in-memory search (name/org/phone/email), sort-key buckets
    PhotoRepositoryTests.swift         # metadata CRUD + file-store deletes (album delete removes files)
    Fakes/
      InMemoryPasscodeStore.swift      # test/preview fake
      FakeKeychain.swift               # dictionary-backed keychain for tests
```

---

## 6. Milestones

Build order minimizes rework: lock shell first (it's the app's front door), then persistence, then features.

**M1 — Project skeleton & lock shell.** XcodeGen project builds; `SafeBoxApp` → `RootView` → hardcoded `.locked` calculator placeholder and `.unlocked` empty 4-tab `TabView`; install-sentinel check stubbed in.
*Accept:* project generates and builds clean under Swift 6; tab bar shows 4 empty tabs when state is forced unlocked; display name resolves to "Calculator+".

**M2 — Calculator engine + passcode capture + Keychain.** Full `CalculatorEngine`, `PasscodeRecorder` (32-cap + overflow flag), `PBKDF2`/`KeychainPasscodeStore` (600k iterations, versioned blob, XOR compare), `AppLockCoordinator` with first-run setup (plain-sequence confirm, trivial-sequence warning, no-recovery notice) and off-UI-path unlock, install sentinel wired.
*Accept:* unit tests green for engine (against the idea-plan sequence table), recorder (incl. overflow-never-matches), store, coordinator; on simulator: fresh install → set a `D1|D2|ADD|D3|D4`-style passcode with confirm → relaunch → wrong sequences just calculate with no observable difference → correct sequence + `=` unlocks; sub-4-key commits provably skip the compare; delete + reinstall lands in setup, not locked.

**M3 — Persistence layer.** SwiftData models (`@Attribute(.unique)` on `Tag.name`; required `Photo.album`; derived title/snippet fields), `ModelContainerFactory` (custom URL, per-file protection incl. `-wal`/`-shm`, backup exclusion), all repositories + in-memory previews/fakes.
*Accept:* repository unit tests green on in-memory containers; app boots with a live container; data survives relaunch (verified via a temporary debug screen or Notes in M4).

**M4 — Notes.** List (title/snippet rows), editor, markdown preview (shared subset), tags + tag filter, search, autosave.
*Accept:* create/edit/delete (delete confirms, no undo); title = first non-empty line markdown-stripped and snippet excludes the title line (derivation tests match the shared table); preview renders the shared subset incl. checklists as styled text; tag add/filter works; autosave persists within 1 s of the last keystroke and synchronously on exit/backgrounding; all survives relaunch.

**M5 — Gallery.** Albums (card grid with derived covers), PhotosPicker import (originals + thumbnails, lock suppression), grid, pager with specified zoom/paging gestures, delete/move.
*Accept:* import 20+ mixed HEIC/JPEG photos with **no permission dialog** and **no vault lock during the picker round-trip**; imported bytes are byte-identical to the source (spot-check checksums) with real extensions; album cards show first-photo covers; grid scrolls smoothly on thumbnails only; pager: double-tap 2.5×, pinch to 5×, pan clamped, page-swipe disabled while zoomed, zoom resets on page change; deleting a photo removes the DB row and both files; deleting an album removes all its photo/thumb files; force-quit mid-import leaves no inconsistency after the startup sweep.

**M6 — Contacts.** Sectioned searchable list, detail with long-press copy, create/edit/delete form with labeled multi-value phones/emails + address.
*Accept:* full CRUD; org-only contact allowed; search hits name/org/phone/email; familyName-first sections with `#` bucket correct; long-press copies values, and no `tel:`/`mailto:` handoff exists anywhere; survives relaunch.

**M7 — Settings + lock polish + change passcode.** `PasscodeEntrySession` flow, immediate lock-on-background enforcement, Lock now, About.
*Accept:* change passcode requires the current one with visible wrong-current feedback and cancel; mismatch-on-confirm returns to EnterNew; after change, old fails and new works; backgrounding relocks to the calculator immediately (except during a picker suppression window, whose monotonic hard cap is not defeated by changing the device wall clock); "Lock now" locks instantly and clears display/buffer; version shown.

**M8 — Hardening & polish.** Privacy manifest, snapshot cover view verified from every tab, icon + display name, orphan sweep, tmp/ cleanup, SwiftLint pass, empty states, dynamic-type sanity check.
*Accept:* app switcher shows the calculator cover — never vault content — from every tab and in the inactive (peek) phase; `PrivacyInfo.xcprivacy` present and accurate; no `NSFaceIDUsageDescription` or other vault-revealing strings in Info.plist; lint clean; `MANUAL_TESTS.md` fully passes on device.

---

## 7. Testing

### 7.1 Unit tests (Swift Testing framework, `SafeBoxTests` target)

Highest-value, fully automatable targets:

- **CalculatorEngine** — the largest test surface: the idea plan's shared input→display sequence table verbatim (this is the cross-platform contract), plus operator chaining, immediate-execution semantics, decimal entry edge cases (`.` first, double `.`), unary/binary `%`, `±` on empty/result, `C` vs `AC`, repeated `=`, division by zero, display formatting of `Decimal`, and the degenerate all-symbol sequences a symbol-heavy passcode produces.
- **PasscodeRecorder + AppLockCoordinator + PasscodeEntrySession** — the security-critical state tables: setup/confirm/mismatch, background-mid-setup discard, unlock, wrong-sequence-no-feedback, sub-minimum commit skips compare, 32-cap overflow-never-matches, `C` resets buffer and flag, lock clears display/buffer, monotonic relock decision (injected clock), install-sentinel wipe path, verify-current feedback/cancel/mismatch in the change flow.
- **PasscodeStore/PBKDF2** — derivation determinism per salt (fixed test vectors at reduced iteration count for speed, plus one full-cost 600k spot check), distinct salts per set, `|`-joined token serialization, versioned-blob round-trip, mismatch rejection, `clear()`; runs against `FakeKeychain` (the real Keychain isn't available in plain unit test contexts without host-app entitlements — the wrapper is injectable by design).
- **NoteDerivation** — title/snippet mapper against the idea plan's shared example table (headings, checklist first lines, blank leading lines, title-line exclusion from snippet).
- **Repositories** — CRUD, in-memory contact search over name/org/phone/email, familyName-first sort keys + `#` bucket, tag relationships and unique-name dedupe, photo metadata + `PhotoFileStore` against a temp directory (including album-delete-removes-files), all on `ModelContainer(isStoredInMemoryOnly: true)`.

### 7.2 Manual in iteration 1

UI tests (XCUITest) are deliberately skipped — poor cost/benefit at this stage. A written manual test script (checked in as `ios/MANUAL_TESTS.md`, executed at M8) covers: full disguise walkthrough, photo import at scale (incl. picker round-trip without locking), zoom/swipe feel (clamped pan, zoom reset), immediate lock on backgrounding (incl. the picker suppression window and its 2-minute cap), change-passcode paths (wrong current, mismatch, cancel), app-switcher snapshot check from every tab, long-press copy on contacts, backup-exclusion spot check.

### 7.3 CI reality check

This repo's cloud/agent sessions **cannot run Xcode** — no `xcodebuild`, no simulator. Consequences, planned for:

- Agents verify by **reading + reasoning + keeping logic in pure Swift** (engine, recorder, hashing, derivation are all UIKit-free by design so they're reviewable in isolation).
- A GitHub Actions workflow on the **latest macOS runner image that includes Xcode 17.x** (verify the image's Xcode list rather than pinning a runner label blindly) runs:
  - `xcodegen generate`
  - `xcodebuild test -scheme SafeBox -destination 'platform=iOS Simulator,name=iPhone 16'`
  - (For a build-only job, the correct generic form is `-destination 'generic/platform=iOS Simulator'` — note that `generic/` destinations take **no** device name and cannot run tests; the draft's mixed form was malformed.)
  - Optionally split into `build-for-testing` / `test-without-building` for caching.
- CI is the compile/test authority; agents open PRs and let CI validate. Keep the unit-test suite fast (<30s) so CI stays cheap; no test depends on Keychain, network, or photo library.

---

## 8. Risks & gotchas (iOS-specific)

1. **SwiftData migrations.** SwiftData's lightweight migration works for additive changes only; anything structural needs `SchemaMigrationPlan`/`VersionedSchema`. Mitigation: define `SchemaV1` as a `VersionedSchema` **from day one** and route the container through a `MigrationPlan`, even while trivial — retrofitting versioning after users have stores is the painful part. Never rename/retype fields casually in review. **Related version trap:** the deployment target is iOS 17, so iOS 18-only SwiftData surface (`#Unique`, `#Index`, history tracking) must not appear anywhere; uniqueness uses `@Attribute(.unique)` (§3.2). This audit has been applied to the whole plan; keep applying it in review.
2. **Photo permission scope — avoided entirely.** `PhotosPicker` runs out-of-process; the app never requests photo-library authorization and needs **no** `NSPhotoLibraryUsageDescription`. Do not "improve" import with `PHPickerViewController` + `PHAsset` fetches or auto-delete-originals — both drag in the permission dialog and undermine the disguise (Settings → Privacy would list the "calculator" as wanting photo access).
3. **Vault content in app-switcher snapshots.** iOS screenshots the UI on backgrounding, and screenshots themselves cannot be blocked on iOS. Mitigation (the pinned iOS mechanism, deliberately divergent from Android's `FLAG_SECURE` — recorded in the idea plan): install the calculator-face cover view **when the scene resigns active** (`.inactive` — fires for app switcher, notification shade, Control Center, incoming calls) and remove it on `.active`, **independent of the lock decision** — even during a picker suppression window, where the vault stays unlocked, no vault pixels reach the switcher. The lock transition alone is too late: the snapshot can be taken before the locked branch renders.
4. **Privacy manifest (`PrivacyInfo.xcprivacy`) is mandatory** for App Store submission. Declare: no tracking, no data collection, required-reason API categories actually used — at minimum `UserDefaults` (CA92.1) and file-timestamp APIs if touched. Keep it accurate; it's also nice supporting evidence that the app is genuinely offline.
5. **Decrypted data at rest.** Photos/DB live within the sandbox protected by per-file `.completeUnlessOpen` (applied at write time, covering `-wal`/`-shm` — directory attributes are not trusted to propagate) plus backup exclusion via `isExcludedFromBackup` — two separate mechanisms for two separate threats. Real content-layer encryption (per-file keys derived from the passcode) is an iteration-2 candidate — the `PhotoFileStore`/repository seams are where it would slot in. EXIF/location metadata in imported originals is retained in iteration 1 (documented; stripping is an iteration-2 option). `tmp/` picker staging is cleaned on lock and launch so plaintext image copies don't linger outside the vault directory.
6. **Keychain persists across app deletion.** After uninstall/reinstall, the old passcode hash would still be in the Keychain → the app would boot `.locked` behind a passcode the "new" user doesn't know. Mitigation (pinned): the `UserDefaults` **install sentinel** — absent at launch ⇒ wipe all SafeBox Keychain items ⇒ first-run setup (§2.3, §3.4). Keychain items additionally use `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` so the hash never restores to another device via backup.
7. **Swift 6 strict concurrency + SwiftData.** `@Model` objects are not `Sendable`; never pass them across actors — repositories return value-type DTOs or are `@MainActor` themselves (iteration 1: `@MainActor` repositories with the main-actor `modelContext` is the simple, correct choice; only `PhotoFileStore`/import IO and PBKDF2 derivation go off-main via actors working on `Data`/paths/tokens, not models).
8. **App Review / disguise tension.** Apple guideline 2.3.1 targets hidden/undocumented features, and "calculator that is secretly a vault" is the canonical category. The strategy is pinned in the idea plan's store-review-risk section: the disguise is from bystanders, **not** from the reviewer — the App Store listing discloses the vault functionality, App Review notes include a demo passcode, and the icon/name avoid Apple-trademark mimicry. Not a build blocker for iteration 1 (TestFlight/dev builds), but mandatory reading before any submission.
9. **`AttributedString(markdown:)` limits.** No block-level rendering (headings, lists render as plain text) — hence the line-aware pre-pass (§4.3) and, more importantly, the **shared markdown subset pinned in the idea plan** (headings, bold/italic, inline code, bullet/numbered lists, checklists-as-styled-text). The Android renderer is constrained to the same subset, so iteration-1 fidelity is aligned by contract, not by hoping two renderers agree.
10. **Large-image memory.** Never decode full-resolution images for the grid; always thumbnails. In the pager, downsample to screen size via `CGImageSource` rather than `UIImage(contentsOfFile:)` on 48MP originals; keep at most 3 pages' images alive.
11. **Gesture arbitration in the photo pager** is the single most fiddly UI item (§4.2): if the SwiftUI-native "disable page-swipe while zoomed" composition misbehaves across OS point releases, switch to the sanctioned `UIScrollView`-backed representable early rather than fighting gesture recognizers — the spec (2.5×/5×, clamped pan, reset on page change) stays identical either way.
