# SafeBox — Disguise Skeleton Plan (Pluggable Disguise Abstraction)

**Document:** `docs/plans/disguise-skeleton-plan.md`
**Status:** **Design-only.** Target: **iteration 2**. Iteration 1 ships the calculator hard-wired exactly as specified in `docs/plans/idea-plan.md`, `docs/plans/ios-plan.md`, and `docs/plans/android-plan.md` — this document changes **nothing** in iteration-1 scope, code, or acceptance criteria. Where this document and the committed plans describe the same behavior, the committed plans win for iteration 1; this document then carries that behavior forward, generalized, into iteration 2.
**Relationship to mockups:** this document is the written spec and stands alone as the authority. Visual mockups are produced separately from it; §8 lists the artboards they must cover.
**Last updated:** 2026-08

---

## 1. Concept & Motivation

A **disguise** is the app's blocking front: a believable, fully functional decoy app surface that doubles as the covert passcode entry. It is what a bystander sees, what the app switcher shows, and what a wrong passcode dissolves into without a trace. In iteration 1 there is exactly one disguise — the calculator — and it is welded directly into the lock architecture (`CalculatorScreen` + `PasscodeRecorder` on iOS, `CalculatorScreen` + `KeySequenceRecorder` on Android, both feeding the host lock state machine).

The idea plan already names "alternate disguises (unit converter, clock, etc.) and disguise theming" as an iteration-2+ candidate, and reserves Settings layout room for "Appearance: disguise themes." This document designs the seam that makes that possible: a **pluggable disguise abstraction** in which the calculator becomes *disguise #1* — the first instance of a contract — so that disguise #2+ and per-user disguise switching can land **without touching the vault**, the lock state machine, the hashing scheme's parameters, or any pinned behavior.

Why a skeleton, and why design it now even though it ships in iteration 2:

1. **The refactor is cheap only if the contract is right.** The iteration-1 file trees were deliberately built with the engine, recorder, hashing, and lock coordinator as separate units. Deciding *now* what generalizes (token stream, alphabet descriptor, caption slots) and what never does (state machines, KDF, re-lock rules) prevents iteration-1 review from accidentally entangling them.
2. **The passcode blob format is the one place iteration 2 touches iteration-1 data.** The stored record needs a `tokenSetId` to be disguise-aware; designing the migration now (§3.4, §7.1) guarantees no iteration-1 user is ever forced to re-enroll on upgrade.
3. **The contract must be validated on paper before any code exists.** §5 walks three concrete candidate disguises through the contract; one of them (the unit converter) surfaces a genuine contract weakness and forces an amendment. Doing this exercise in a design doc costs a page; doing it after the interface ships costs a breaking change.

What the skeleton is **not**: it is not a theming system (a dark-vs-light calculator is a theme, not a disguise), not a plugin/download mechanism (disguises are compiled in — §9), and not a change to the launcher identity (§2.1).

---

## 2. Hard Constraints

These constraints are stated before the contract because they bound what "switching disguises" can ever mean. Both are non-negotiable consequences of decisions already pinned in the committed plans.

### 2.1 Launcher identity is static per install

The idea plan pins the launcher identity as a single product decision: display name **"Calculator+"**, a neutral calculator glyph, identifier **`com.calcplus.calculator`** on both platforms — and notes that identifiers are **permanent once shipped**. On Google Play the `applicationId` can never change for the life of the listing; on the App Store the bundle identifier is equally permanent. The icon and display name are technically mutable per release, but they are the *same for every user of that release* — they are not per-user state.

Therefore, unavoidably:

- **Switching disguise in-app changes only the lock-screen face** (and the covert entry mechanics behind it). It never changes the app's name, icon, identifier, Settings-app entry, store listing, or anything else the OS surfaces. A user who switches to the unit-converter disguise still carries an app named "Calculator+" with a calculator icon.
- **Credibility is therefore graded, and the grade is a first-class property of every disguise** (§3.2f). Under the shipped "Calculator+" identity:
  - *Identity-native:* the calculator. Perfect coherence.
  - *Identity-plausible:* disguises a real calculator app could credibly contain — a unit converter, a tip calculator. Many stock and third-party calculator apps ship these as modes; a bystander who opens "Calculator+" and finds a converter tab sees a feature, not a lie. These are the realistic disguise #2 candidates for this identity.
  - *Identity-incoherent:* a countdown timer, a notes pad, a flashlight. An app named "Calculator+" that opens to a stopwatch is *itself* a tell — the disguise face contradicts the launcher face. Such disguises are only credible under a differently-named install.
- **Per-identity SKUs are the only path to identity-incoherent disguises** — separate store listings (e.g. "Timer+", `com.calcplus.timer`) built from the same codebase with a different default disguise. That is a distribution decision with its own store-review exposure (idea plan §7 applies to each SKU independently) and is **out of scope for this design** beyond one requirement: nothing in the disguise contract may assume the calculator identity, so a future SKU flavor can select a different *default* disguise at build time without touching the seam. Recorded as an open question in §10.
- **In-app switching UI must disclose this honestly.** The disguise picker (§6.1) states plainly: *"The app's name and icon on your home screen stay 'Calculator+'. Only the screen shown when the app is locked changes."* Anything else sets the user up to believe they are more hidden than they are.

### 2.2 The passcode is bound to a disguise-specific token alphabet

The pinned hashing scheme (idea plan §2.2) computes PBKDF2-HMAC-SHA256 over a canonical serialization of *calculator key IDs* (`D0…D9, DOT, ADD, SUB, MUL, DIV, PCT, SIGN`, joined with `|`). Generalized, the KDF input is a serialization over a **disguise-specific token alphabet**. A unit converter has no `PCT` key; a tip calculator has no `SIGN`. Consequences:

- **A stored hash is meaningful only relative to the alphabet it was computed over.** The stored blob must therefore identify its alphabet (`tokenSetId` + `alphabetVersion`, §3.4).
- **Switching disguises ALWAYS enrolls a new passcode in the new disguise's alphabet.** What blocks conversion is **alphabet incompatibility**: the old sequence may contain tokens the new alphabet lacks (a `PCT` in a calculator passcode has no converter equivalent), and there is no meaningful mapping between alphabets in general. To be precise about the mechanics: the switch flow's VERIFY_CURRENT step *does* hand the host the raw sequence in memory (raw-sequence non-persistence is not the obstacle — re-hashing with a fresh salt within the *same* alphabet is exactly what a plain change-passcode does), but a raw sequence in one alphabet cannot in general be expressed in another. **Adopted policy:** switching **always re-enrolls, even between two disguises that happen to share an alphabet** — for flow uniformity (one switch flow, no special cases) and because CAPTURE_NEW/CONFIRM_NEW double as the user's first-contact training with the new surface (§2.3). By contrast, a plain iteration-2 **change-passcode** (no disguise switch) rewrites the v2 envelope with the new salt+hash while preserving `activeDisguiseId` unchanged.
- **Alphabets are versioned independently of disguise UI, and `alphabetVersion` is provenance metadata** (§3.2c): it records which token set existed at enrollment. Adding a key to a disguise's keypad that participates in passcodes is an additive alphabet change (`alphabetVersion` bump) and changes nothing for existing enrollments — the serialization rule is identical across all versions of a `tokenSetId` by rule (version bumps add tokens, never change serialization), so verification is mechanically identical whichever version an envelope names; a v1 enrollment simply cannot contain later-added tokens, so any attempt that includes one fails naturally. **Breaking changes have exactly one rule, stated identically here and in §3.2c: published tokens are never renamed or removed within a `tokenSetId`. A removal or rename is a NEW `tokenSetId`** — which, by the alphabet-binding rule above, forces re-enrollment.

### 2.3 The disguise-switch flow (atomic replace, no bad window)

Switching is a security-sensitive state change with two failure modes that must be impossible by construction: a window with **zero** valid passcodes (user locked out of their own vault) and a window with **two** valid passcodes (the abandoned old code still unlocks). Design:

- **Switching is available only from inside the unlocked vault** (Settings → Disguise, a future Appearance-section row per the idea plan's Settings layout reservation). There is no pre-unlock path of any kind — the lock screen never offers, hints at, or reacts to disguise selection.
- **Flow** (mirrors the change-passcode state machine of idea plan §2.7, with the new disguise's surface substituted for the entry steps):

```
[PICK_DISGUISE]           Settings → Disguise: list of compiled-in disguises,
                          current one marked; identity-credibility note (§2.1) shown.
   └─ select new disguise → shows the re-enrollment explainer (a TEMPLATE,
        parameterized by the CURRENT disguise's name and commit gesture):
        "Your current code is entered on the <current disguise>. The new
         disguise needs a new code entered on its own keys. Your vault
         contents are unchanged."
        (illustrative, non-normative phrasing; the parameterization is normative)
        → VERIFY_CURRENT

[VERIFY_CURRENT]          rendered on the CURRENT disguise's surface,
                          host mode VerifyCurrent (visible wrong-code feedback,
                          unlimited retries, cancel — verbatim idea plan §2.7)
   └─ match → CAPTURE_NEW

[CAPTURE_NEW]             rendered on the NEW disguise's surface, host mode CaptureNew
                          (4–32 tokens, overflow flag, clear-signal resets,
                           strength nudge — all host rules; after a CONFIRM_NEW
                           mismatch, the user is returned HERE with the pinned
                           mismatch banner visible while they re-enter)
   └─ valid commit → CONFIRM_NEW
        (the trivial-sequence soft warning, if any, shows on entry to
         CONFIRM_NEW — per idea plan §2.3; never blocks)

[CONFIRM_NEW]             NEW disguise's surface, host mode ConfirmNew
   ├─ match → ATOMIC COMMIT (below) → back to Settings, confirmation shown;
   │          lock screen face is the new disguise from this moment on
   └─ mismatch → both buffers cleared → CAPTURE_NEW, with the pinned
              "Codes didn't match — start again" banner rendered on
              re-entry to CAPTURE_NEW (idea plan §2.3/§2.7)

Cancel or backgrounding at ANY step → flow abandoned, all buffers discarded,
old passcode and old disguise fully intact (normal §2.5 re-lock rules apply).
```

- **Atomic commit:** the new passcode blob (fresh salt, new `tokenSetId`/`alphabetVersion`) and the new `activeDisguiseId` are written as **one replace of one stored envelope** (§3.4 puts `activeDisguiseId` *inside* the same secure-storage item as the hash precisely so the pair can never desync). Until that single write lands, the old blob is authoritative: old code valid, old face shown. After it lands, only the new code is valid and only the new face is shown. A crash at any earlier point leaves the old state; a crash after leaves the new state. There is no intermediate state to observe or recover. **On Android**, the plain-preferences `activeDisguiseId` mirror key (§3.4) is written **in the same switch commit**; it is a derived cache — the envelope copy is authoritative on any conflict, and the mirror is re-synced from the envelope on unlock — so a crash between the two writes is self-healing and never affects which code is valid.
- The CAPTURE_NEW / CONFIRM_NEW steps double as the user's first hands-on contact with the new disguise's covert entry (which keys count, what the commit gesture is) — the captions name the commit gesture explicitly, per §3.2d.

---

## 3. The Disguise Contract

### 3.1 Vocabulary

| Term | Meaning |
|---|---|
| **Host** | The disguise-agnostic lock subsystem: lock state machine, token recorder, hashing/storage, re-lock triggers, suppression window. One per app; owns all security decisions. |
| **Disguise** | A compiled-in implementation of the contract: a decoy surface + covert token stream + alphabet descriptor + caption slots + cover face + believability spec. |
| **Token** | An opaque, canonical identifier for one covert-input event (for the calculator: a key ID like `D7` or `ADD`). Tokens are IDs, never display glyphs — pinned by the idea plan's serialization rule, generalized here. |
| **Token stream** | The disguise→host event stream: `token(id)`, `commit`, `clear`. The generalization of "key press", "`=`", "`AC`". |
| **Alphabet** | The versioned set of token IDs a disguise emits, plus its canonical serialization rule. The KDF input domain. |
| **Mode** | The host-driven rendering mode of the disguise surface: `Disguise`, `CaptureNew`, `ConfirmNew`, `VerifyCurrent` — exactly the pinned four (Android plan §4.1's `CalculatorMode`, iOS's setup/lock/change-session split, unified). |

### 3.2 What a disguise provides

A disguise is a value the host consumes. It must provide all six of the following; a candidate that cannot provide one honestly is not a valid disguise (§5 tests this).

**(a) Decoy surface.** A full-screen UI that is a *genuinely functional* instance of the app it pretends to be. "Genuinely functional" is a hard requirement, not polish: the calculator actually calculates (idea plan §2.1 — "a broken calculator breaks the disguise"), a converter actually converts, a tip calculator actually computes tips. The surface renders in one of the four host modes and is the only thing composed/instantiated while locked — the iteration-1 rule that no vault UI exists in the locked branch is inherited unchanged.

**(b) Covert token stream.** The generalization of key presses / `=` / `AC`:

- **Token events** — `token(id)`: emitted for each covert-input interaction, carrying the canonical token ID. The disguise decides *which* of its interactions emit tokens (the calculator emits for the 17 passcode keys; `=`/`AC` are signals, not tokens — same exclusion rule generalizes: the commit and clear gestures are never themselves tokens).
- **Commit signal** — the disguise's designated commit gesture fired (calculator: `=`). Exactly one commit gesture per disguise; it must be a **natural, frequently used, zero-suspicion interaction of the decoy** (§5.4 amendment). On commit the decoy performs its normal function *first and unconditionally* (the calculator shows the arithmetic result; the timer starts) — the disguise never flinches, verbatim from the pinned unlock flow.
- **Clear signal** — the disguise's designated start-over gesture fired (calculator: `AC`). Must likewise be a natural decoy interaction; it is the user's tell-free typo recovery, so a disguise without one is invalid.

The stream is **fire-and-forget**: the disguise never learns what the host did with a token, whether a commit matched, or whether a buffer overflowed. The host reaches back into the disguise only via mode + caption slots (d). This one-way design is what guarantees the decoy cannot leak verification state even by accident.

**Host→disguise reset guarantee (contract clause, host-side).** There is deliberately no host→disguise "reset" event, because none is needed: **the host TEARS DOWN AND FRESHLY RE-INSTANTIATES the disguise surface on every lock transition and on backgrounding-while-locked.** A pristine resting state is thus a *construction guarantee*, not a disguise obligation — a freshly instantiated surface has, by definition, no half-typed entry, no pending-operator indicator, no residual unit selection. This is how the pinned display-clear rule generalizes: idea plan §2.4/§2.5 require that "on every lock transition, the calculator display state and the attempt buffer are cleared (so the resumed lock screen is a pristine calculator)" and that backgrounding while LOCKED clears both; the android-plan (§2.3) notes `FLAG_SECURE` blanks the recents thumbnail but **not** the live resumed screen, so this clear is load-bearing. The buffer half of the rule stays inside the host recorder (§3.3); the visible-display half is delivered by re-instantiation. Corollary obligation on the disguise: a disguise must keep **no decoy state outside its composed/instantiated surface** (no statics, no singletons, no disk writes of decoy state), so that re-instantiation provably yields the resting face — this is verified by the §7 acceptance checks and declared in the believability spec's entry-trace clause (f).

**(c) Token-alphabet descriptor.** A static, versioned declaration:

```
AlphabetDescriptor {
  tokenSetId:      string   // stable identifier, e.g. "calculator"
  alphabetVersion: int      // e.g. 1 — bumped on any additive alphabet change
  tokens:          [string] // the canonical token IDs, e.g. D0…D9, DOT, ADD, SUB, MUL, DIV, PCT, SIGN
  serialize:       [token] -> string   // canonical serialization; MUST be the "|"-join
                                       // of token IDs unless a future descriptor version
                                       // explicitly overrides (none planned)
}
```

The calculator's descriptor is `tokenSetId = "calculator"`, `alphabetVersion = 1` — written `calculator.v1` as shorthand throughout this document — with exactly the pinned 17 IDs and the pinned `|`-join. **The iteration-1 scheme thus becomes one instance of the general form, byte-for-byte unchanged**: `serialize(D1, D2, ADD, D3, D4) = "D1|D2|ADD|D3|D4"`, and the KDF input for an existing enrollment is identical before and after the refactor (this is what makes the §7.1 migration a metadata-only rewrite). Rules: token IDs must be unique within the alphabet, must never contain `|`, and **once published in a shipped `alphabetVersion` may never be renamed or removed within that `tokenSetId` — a removal or rename is a NEW `tokenSetId`** (the same single rule as §2.2; by §2.2's alphabet-binding logic, a new `tokenSetId` forces re-enrollment). Additions bump `alphabetVersion`. **`alphabetVersion` is provenance metadata**, recording which token set existed at enrollment: the serialization rule is identical across all versions of a `tokenSetId` by rule (version bumps add tokens, never change serialization), so verification does nothing mechanically different for a v1 versus a v2 enrollment — and a v1 enrollment cannot contain later-added tokens, so any attempt including one fails naturally (it serializes to a string no v1 enrollment ever hashed). Alphabet size and structure feed the strength guidance (§3.3, buffer rules row) — a small alphabet is a *disclosed property*, not a blocker, matching the idea plan's honest-threat-framing posture.

**(d) Caption/feedback slots.** The host drives the four modes; the disguise renders each mode's captions and feedback **in disguise-appropriate style**. The host supplies *semantic* caption states, never literal strings baked into the host (so a converter can phrase the capture prompt in its own genre and name its own commit gesture — the semantic state is the host's; the words are the disguise's), and never supplies anything at all in `Disguise` mode:

| Mode | Host supplies | Disguise renders | Pinned source |
|---|---|---|---|
| `Disguise` | **Nothing. Ever.** No caption slot exists in this mode. | Pure decoy. Silent non-match, no error, no animation, no state change. | idea plan §2.4 |
| `CaptureNew` | Semantic states: prompt-new, too-short, too-long/overflow, strength nudge, collision-risk note, **mismatch-start-again** (shown on re-entry to this mode after a failed confirm — the pinned banner is visible while the user re-enters, per idea plan §2.3) | A discreet one-line caption zone above/within the decoy, styled to match it | idea plan §2.3 |
| `ConfirmNew` | prompt-confirm; trivial-sequence soft warning (shown on entry to this mode, per idea plan §2.3; never blocks) | Same caption zone | idea plan §2.3 |
| `VerifyCurrent` | prompt-current, wrong-code (visible feedback — e.g. shake + the pinned "Incorrect code — try again"), cancel affordance | Visible feedback, disguise-styled; silence is a disguise feature only on the lock screen, and this mode never appears there | idea plan §2.7 |

`CaptureNew`/`ConfirmNew`/`VerifyCurrent` appear only in first-run setup and inside the unlocked vault's flows — never on a post-setup lock screen. That invariant is host-enforced (the host simply never puts a locked session in those modes), not trusted to the disguise. Caption copy must name the disguise's commit gesture concretely ("…then press =", "…then tap Convert").

**String authority.** The pinned calculator copy lives in the idea plan (§2.3, §2.7) and is consolidated verbatim in the **calculator design spec's §6 copy table (`docs/plans/calculator-disguise-design.md`), which is the single string authority**: "Set your secret code: type it on the keypad, then press =", "Codes didn't match — start again", "Incorrect code — try again", and the rest of that table. **The iteration-2 calculator disguise MUST render those pinned strings verbatim for each semantic state** — this is part of the behavioral-identity acceptance of §7.2/§7.3, so the refactor cannot drift user-visible copy the plans and DoD treat as specified. Other disguises author their own genre-styled equivalents, parameterized by their own commit gesture. Any caption phrasing appearing in this document that is not a direct quote of a pinned string is a **non-normative illustration**.

**(e) Snapshot cover face.** A static, full-screen rendering of the disguise's resting state (calculator: the pristine calculator face), used by the host's snapshot protection where the platform needs one. On iOS the host installs it on scene-resign-active exactly as iteration 1 installs `CalculatorCoverView` — the mechanism, timing, and independence-from-lock-decision are host-owned and unchanged; only the face's pixels come from the disguise. On Android the cover face is **unused**: unconditional activity-level `FLAG_SECURE` remains the pinned, host-owned mechanism, and the documented iOS/Android divergence (idea plan §6) carries over verbatim. The contract still requires the face from every disguise so the iOS host never has a disguise without one. Note the cover face and the reset guarantee (b) agree by construction: the face depicts the resting state, and re-instantiation returns the live surface to exactly that state.

**(f) Believability requirements.** A written spec, per disguise, of what "passes casual use" means — the generalization of the calculator's behavior spec and 17-row sequence table. It must include: a **reference model** for the decoy's behavior (the calculator pins iOS Calculator basic-mode semantics), a **shared cross-platform behavior table** the decoy must reproduce exactly on both platforms (the acceptance contract), the **degenerate-input clause** (whatever streams a symbol-heavy or unusual passcode feeds the decoy must never look broken — the calculator's all-symbol rows generalize to "every alphabet token sequence, in any order, produces sane decoy behavior"), the **accidental-unlock collision analysis** for that decoy (which passcodes coincide with plausible real use — §5 shows this generalizes beyond arithmetic), an **entry-trace clause** — an enumeration of the visible decoy state a typed code produces, **live and residual** (the calculator's pending-operator indicator tracks each operator token as it is typed; a converter leaves the passcode's unit selections showing; a tip calculator leaves the passcode's selected preset highlighted), together with confirmation that the §3.2b reset-on-lock guarantee returns the surface to the resting face so **no trace survives a lock** — the **decoy state resets to the resting face on every lock** is itself a believability obligation, verified in §7 — and the **identity-credibility grade** under the shipped launcher identity (§2.1). A disguise without a believability spec cannot ship; the spec is where a disguise earns the rigor the calculator got in the idea plan.

### 3.3 What the host owns (disguise-agnostic, forever)

The host owns everything security-relevant. None of this is delegated, duplicated, or overridable per disguise:

| Host-owned | Content (all pinned in iteration 1, carried verbatim) |
|---|---|
| **Lock state machine** | `firstRunSetup/NeedsSetup → locked ⇄ unlocked`, the setup ENTRY→CONFIRM machine, the change-passcode VERIFY→ENTER→CONFIRM machine, launch-default-locked, fail-closed everywhere. The disguise never sees lock state beyond its current mode. |
| **Token recorder & buffer rules** | One generalized recorder: buffer of token IDs, **4-token minimum, 32-token cap, overflow flag on the 33rd token, overflowed/short commits never match and skip the KDF**, clear-signal resets buffer + flag, buffer cleared on every lock transition and on backgrounding while locked, in-memory only. Identical for every disguise. The **visible-display half of the same pinned clear rule** (idea plan §2.4/§2.5: pristine calculator on resume) is delivered by the host's teardown-and-reinstantiate guarantee (§3.2b): on every lock transition and on backgrounding-while-locked the host discards the composed surface and constructs a fresh one, so the decoy's on-screen state resets to the resting face for every disguise. |
| **Hashing & storage** | PBKDF2-HMAC-SHA256 / 600,000 iterations / 16-byte salt / 256-bit output over the alphabet's canonical serialization; constant-time compare; **verification off the UI path** with byte-identical rendering for match/non-match; Keychain (`ThisDeviceOnly`) / Keystore-wrapped DataStore blob; iOS install sentinel. Blob format v2 in §3.4. |
| **Re-lock triggers** | Immediate lock on backgrounding, no grace setting; Settings → Lock now; locked-by-construction on process death; **on every lock: buffer cleared AND disguise surface torn down and freshly re-instantiated (§3.2b)** — the generalization of "clear display + buffer on every lock". |
| **Suppression window** | §2.5.1 verbatim: app-initiated system UI only, in-flight flag, 2-minute monotonic hard cap, fail closed, repository-level import completion. Entirely below the disguise seam — no disguise can widen, narrow, or observe it. |
| **No-logging rule** | No token, buffer, sequence, salt, hash, alphabet-of-enrollment, or lock-state internal in any log at any level, extended explicitly to disguise implementations: a disguise logging its own "key pressed" debug line would leak the token stream. Release-build log stripping mechanisms unchanged. |
| **Mode assignment & captions semantics** | Which mode the surface is in, which semantic caption state applies, the rule that `Disguise` mode carries no feedback channel at all. |
| **Disguise registry & switch flow** | The compiled-in disguise list, the active-disguise selection (including the §3.4 fail-closed default when resolution fails), the §2.3 atomic switch. |

The division is deliberately asymmetric: the disguise is a *renderer and input device*; the host is *everything else*. A useful review heuristic for iteration 2: **if a line of code makes a decision that would appear in a security audit, it lives in the host.**

### 3.4 Stored blob v2 and migration from iteration 1

Iteration 1 stores `{algo, version: 1, iterations, salt, hash}` (versioned envelope, pinned). Iteration 2 extends it:

```
PasscodeEnvelope v2 {
  algo:            "PBKDF2-HMAC-SHA256"
  version:         2
  iterations:      600000
  salt:            16 bytes
  hash:            32 bytes
  tokenSetId:      string     // alphabet of enrollment, e.g. "calculator"
  alphabetVersion: int        // e.g. 1
  activeDisguiseId:string     // face to show while locked, e.g. "calculator"
}
```

- `tokenSetId`/`alphabetVersion` name the alphabet the hash was computed over; verification serializes the attempt with **that** `tokenSetId`'s canonical rule (which is version-invariant — `alphabetVersion` is provenance metadata, §3.2c), regardless of anything else.
- `activeDisguiseId` lives **inside the same envelope** so face and code replace atomically (§2.3). Note it is distinct from `tokenSetId`: two disguises may share an alphabet in the future (switching between them still re-enrolls, §2.2), and the face is a UI fact while the alphabet is a crypto fact.
- **Launch read — iOS:** the envelope is a Keychain item; the existing launch-path read yields `activeDisguiseId` directly. No extra mechanism.
- **Launch read — Android (duplicate-key design):** on Android the envelope is a Keystore-AES/GCM-wrapped blob (android-plan §3.4), so reading `activeDisguiseId` out of it at process start would turn iteration 1's `runBlocking` one-key Preferences read (android-plan §2.3, justified as "acceptable at process start for a single small key") into a synchronous Keystore unwrap inside `Application.onCreate` — a materially different operation on the cold-launch budget, with its own failure modes (Keystore ops can be slow or fail; the plan documents a `wrapped=false` fallback path). Therefore `activeDisguiseId` is **also stored as a plain Preferences DataStore key**, written **in the same atomic switch commit** as the envelope (§2.3). The plain key is a derived mirror: **the envelope copy is authoritative on any conflict**, and the mirror is **re-synced from the envelope on every unlock** — the moment the envelope is decrypted anyway, at zero extra cost. **Launch reads only the plain key** — still one small key read, honoring the android-plan's `runBlocking` rationale honestly. The envelope is never unwrapped at launch.
- **Fail-closed face rule (pinned, both platforms):** whenever the active disguise cannot be resolved at launch — the plain key/envelope field is **missing** (fresh mirror, failed migration), the envelope is **unreadable or unwrappable** (Keystore breakage, corrupt blob), or the resolved ID names **no registry entry** (downgrade, removed disguise) — the host renders the **default disguise (calculator) in `Disguise` mode, with buffer rules unchanged**. This can present a face whose alphabet cannot express the enrolled passcode (effective lockout until the underlying fault clears — see §10 risk 4); fail-closed is the only acceptable direction: never an error screen, never a non-disguise surface, never a hint that resolution failed.
- **Migration (upgrade from iteration 1):** on first launch of an iteration-2 build, a v1 envelope is **interpreted as** `tokenSetId = "calculator"`, `alphabetVersion = 1`, `activeDisguiseId = "calculator"` — i.e. **absence of the new fields means calculator.v1, by definition**. The salt and hash are untouched (the calculator alphabet and serialization are byte-identical, §3.2c), so **no re-enrollment is ever forced on upgrade**: the user's existing passcode unlocks on the first try after updating. The host then rewrites the envelope as v2 with those explicit values — eagerly, at the migration read, since the rewrite needs no passcode (hash and salt are copied verbatim) — as a single atomic replace of the storage item (on Android, the plain mirror key is written in the same migration commit). If the rewrite fails, the v1-implies-calculator.v1 interpretation remains in force indefinitely; the read path supports both versions permanently (the version tag exists precisely for this).
- **Downgrade note:** iteration-1 behavior on a v2 envelope is **unspecified by the committed plans** — the idea plan (§2.2) says only that the version tag "allows future parameter upgrades" and never defines rejection behavior. An iteration-1 decoder may reject the envelope, or a tolerant decoder that ignores unknown fields may happen to parse it and — since salt/hash/serialization are byte-identical for a calculator.v1 enrollment — verify successfully. Either way, OS-level app downgrades are an unsupported path. **Forward obligation (binding on iteration 2):** the iteration-2 decoder **explicitly rejects envelope versions > 2**, so the compatibility boundary is defined behavior from iteration 2 onward rather than an accident of decoder tolerance.

---

## 4. Platform Mapping (design-only sketches)

These are refactor sketches against the committed iteration-1 file trees, not new architecture. The governing rule: **the seam is thin** (§4.3) — the calculator code moves and gains a protocol conformance; it does not get rewritten.

### 4.1 iOS

**New protocol (in a new `Disguise/` group):**

```swift
protocol DisguiseProviding {
    var id: String { get }                       // "calculator"
    var alphabet: AlphabetDescriptor { get }     // tokenSetId, alphabetVersion, tokens, serialize
    var believability: BelievabilitySpec { get } // reference to the disguise's spec doc/table id

    /// The decoy surface. Emits the covert token stream via `events`;
    /// renders per `mode` and the host-supplied semantic caption state.
    /// The host constructs a FRESH surface on every lock transition and on
    /// backgrounding-while-locked (§3.2b) — the disguise must keep no decoy
    /// state outside the returned view's lifetime.
    func makeSurface(mode: DisguiseMode,
                     caption: CaptionState?,      // always nil in .disguise
                     events: DisguiseEventSink) -> AnyView

    /// Static resting-state face for the snapshot cover (host installs it
    /// on resign-active, exactly as iteration 1 installs CalculatorCoverView).
    func makeCoverFace() -> AnyView
}

enum DisguiseEvent { case token(String), commit, clear }
enum DisguiseMode { case disguise, captureNew, confirmNew, verifyCurrent }
```

**Refactor of committed iteration-1 units:**

| Iteration-1 unit (committed ios-plan) | Iteration-2 disposition |
|---|---|
| `Calculator/PasscodeRecorder.swift` | **Generalizes → `Lock/TokenRecorder.swift`** (host-owned): same 32-cap + overflow flag + clear semantics, over `String` token IDs instead of `CalcKey`. The calculator-specific type disappears from the recorder; its tests carry over with token strings. |
| `Calculator/CalcKey.swift` | Stays in the calculator disguise; its canonical IDs + pipe-join serialization become the calculator's `AlphabetDescriptor`. The serialization function moves behind the descriptor. |
| `Calculator/CalculatorScreen.swift`, `CalcButton.swift`, `CalculatorViewModel.swift`, `CalculatorEngine.swift` | **Move under `Disguise/Calculator/` as `CalculatorDisguise`** (conforming to `DisguiseProviding`). The engine is untouched — it remains the pure, table-tested unit. The view model's "feed engine + recorder" split becomes "feed engine + emit `DisguiseEvent`s". |
| `App/CalculatorCoverView.swift` | Becomes `CalculatorDisguise.makeCoverFace()`; the host's install-on-resign-active mechanics in `RootView` are unchanged. |
| `Lock/AppLockCoordinator.swift` | **State machine unchanged.** Gains: consumes `DisguiseEvent`s instead of `CalcKey` buffers; resolves the active disguise from the v2 envelope at launch (fail-closed to the default calculator face per §3.4 when resolution fails); tears down and re-instantiates the disguise surface on every lock transition and on backgrounding-while-locked (§3.2b); hosts the switch flow of §2.3. `commit(sequence:)` becomes `commit(tokens:)` serialized via the active alphabet. |
| `Lock/PasscodeEntrySession.swift`, `Lock/PasscodeRules.swift` | Unchanged in logic; re-typed over token IDs. `PasscodeEntrySession` additionally drives the switch flow (same phases plus the disguise substitution of §2.3). |
| `Lock/PasscodeStore.swift` | Envelope v2 + migration (§3.4, §7.1), including the explicit rejection of versions > 2. `PBKDF2.swift`, `KeychainWrapper.swift` untouched. |
| **Untouched entirely** | `Vault/`, `Gallery/`, `Notes/`, `Contacts/`, `SettingsFeature/` (plus one new Disguise row), `Persistence/`, install sentinel, suppression-window logic, snapshot-cover timing, all repositories, all vault tests. |

New files: `Disguise/DisguiseProviding.swift`, `Disguise/DisguiseRegistry.swift` (the compiled-in list + active-disguise resolution), `Disguise/Calculator/CalculatorDisguise.swift`.

### 4.2 Android

**New interface (in `core/disguise/`), same shape:**

```kotlin
interface DisguiseProvider {
    val id: String
    val alphabet: AlphabetDescriptor
    val believability: BelievabilitySpec

    // The host composes a FRESH surface on every lock transition and on
    // backgrounding-while-locked (§3.2b) — no decoy state outside composition.
    @Composable
    fun Surface(mode: DisguiseMode, caption: CaptionState?, events: (DisguiseEvent) -> Unit)

    @Composable
    fun CoverFace()   // unused by the host on Android (unconditional FLAG_SECURE
                      // is the pinned mechanism); required for contract symmetry
}

sealed interface DisguiseEvent { data class Token(val id: String); object Commit; object Clear }
enum class DisguiseMode { Disguise, CaptureNew, ConfirmNew, VerifyCurrent }  // the pinned four, verbatim
```

**Refactor of committed iteration-1 units:**

| Iteration-1 unit (committed android-plan) | Iteration-2 disposition |
|---|---|
| `feature/calculator/KeySequenceRecorder.kt` | **Generalizes → `core/lock/TokenRecorder.kt`** (host): identical 32-cap/overflow/clear semantics over token-ID strings. |
| `feature/calculator/CalcKey.kt` | Stays with the calculator disguise; canonical IDs + pipe-join serialization become its `AlphabetDescriptor`. |
| `feature/calculator/CalculatorEngine.kt`, `CalculatorViewModel.kt`, `CalculatorScreen.kt` | **Move to `feature/disguise/calculator/` as `CalculatorDisguise : DisguiseProvider`**. The engine and its table-driven tests are untouched. `CalculatorScreen`'s existing `mode: CalculatorMode` parameter *is already* the contract's mode parameter — it renames to `DisguiseMode`, nothing more (the iteration-1 plans pre-paid this cost). The iteration-1 "clear display state on `lock()` / on `onStop`-while-Locked" wiring is superseded by the host's surface re-instantiation (§3.2b), which achieves the same pinned pristine-face outcome by construction. |
| `feature/calculator/PasscodeMatcher.kt` | Absorbed into the host: matching was never calculator-specific — it becomes `core/lock/` logic over the active alphabet's serialization (skip-on-short/overflow rules unchanged). |
| `core/lock/AppLockManager.kt`, `LockState.kt` | **Unchanged in state machine, lifecycle observation, suppression flag, monotonic cap.** Gains active-disguise resolution at process start via the **plain `activeDisguiseId` mirror key** (§3.4): the existing synchronous `runBlocking` read of android-plan §2.3 reads one more small Preferences key — honestly preserving that read's "single small key" rationale. **The Keystore-wrapped envelope is never unwrapped at launch.** Every cannot-resolve case (missing key, unreadable/unwrappable envelope, missing registry entry) fails closed to the default calculator face in `Disguise` mode (§3.4). |
| `core/data/PasscodeStore.kt` | Envelope v2 inside the same Keystore-wrapped DataStore blob, plus the plain `activeDisguiseId` mirror key written in the same switch/migration commit and re-synced from the envelope on unlock (§3.4); explicit rejection of versions > 2. `Pbkdf2.kt`, `KeystoreWrapper.kt` untouched. |
| `feature/settings/ChangePasscodeFlow.kt` | Unchanged logic (a plain change-passcode rewrites the v2 envelope preserving `activeDisguiseId`, §2.2); additionally reused (with disguise substitution) by the new `DisguiseSwitchFlow`. |
| **Untouched entirely** | `MainActivity` (`FLAG_SECURE` stays unconditional), all of `core/database`, `core/data` repositories, `feature/gallery` / `feature/notes` / `feature/contacts`, navigation, backup rules, R8 log stripping. |

New: `core/disguise/DisguiseProvider.kt`, `core/disguise/DisguiseRegistry.kt`, `feature/disguise/calculator/CalculatorDisguise.kt`, Settings "Disguise" row + `DisguiseSwitchFlow.kt`.

### 4.3 Keep the seam THIN — over-abstraction is an explicit risk

The single biggest risk in this design is building a framework where a seam suffices. Named failure modes and their mitigations, all binding on the iteration-2 implementation:

- **Risk: speculative interface surface.** Methods "a disguise might need someday" (theming hooks, layout negotiation, host-queryable decoy state, per-disguise lock policies). *Mitigation:* the contract is frozen at exactly §3.2's six disguise-provided items **plus the host-side teardown-and-reinstantiate reset guarantee (§3.2b)**; anything not needed by the calculator **plus** at least one §5-validated candidate is rejected in review. The `DisguiseEvent` enum has three cases; a fourth requires amending this document first. (Note the reset guarantee deliberately adds **no** event or method — it is a host construction rule, which is why the freeze can cover it without widening the interface.)
- **Risk: the abstraction leaks host internals.** A disguise that can ask "am I locked?", "did that commit match?", or "is the suppression window open?" can leak them. *Mitigation:* the event stream is one-way (§3.2b); the disguise's entire input from the host is `(mode, caption)`. This is testable: the disguise-facing API surface must contain no lock-state type.
- **Risk: security logic migrates into disguises** ("the converter recorder can cap at 20, its alphabet is small"). *Mitigation:* the recorder, rules, and KDF are host singletons (§3.3); disguises contain zero buffer or crypto code, enforced by the review heuristic in §3.3.
- **Risk: refactor churn in iteration-1 tested code.** *Mitigation:* the engine, KDF, keystore/keychain wrappers, and all vault code are move-only or untouched (§4.1/§4.2 tables); the iteration-1 unit-test suites must pass unmodified except for mechanical renames — that is an acceptance check in §7.
- **Risk: plugin thinking.** Dynamic disguise loading, disguise assets fetched at runtime, a disguise SDK. *Mitigation:* non-goal (§9); disguises are source files in this repo, compiled in, reviewed like any code.

---

## 5. Contract Validation Against Non-Calculator Disguises

Paper-walking candidates through §3.2 before any code exists. Each candidate answers: decoy function, tokens, commit gesture, clear gesture, what breaks, verdict.

### 5.1 Unit converter (identity-plausible under "Calculator+")

- **Decoy function:** converts between units (length, weight, temperature, currency-free categories — no network, per the zero-network posture). Genuinely useful; calculator apps commonly bundle one.
- **Tokens:** digit keys `D0…D9`, `DOT`, plus discrete unit-affecting interactions: category selection (`CAT_LEN`, `CAT_WT`, `CAT_TMP`, …), from-unit and to-unit selection (`U_<unit>`), and swap (`SWAP`). A rich alphabet (~20–30 tokens) with symbol-like non-digit tokens — good entropy properties.
- **Clear gesture:** a clear/`C` button on the numeric pad — natural, converters have one. Valid.
- **Commit gesture — this is where the contract breaks.** A well-made converter converts **live** as you type; it has no `=`. There is no natural, discrete "evaluate now" moment. The naive fixes are all bad: a long-press or hidden gesture as commit violates believability (undiscoverable, and a bystander who long-presses gets nothing sensible); committing on "result field tap" is an interaction no real converter user performs; auto-committing on every token (checking the buffer continuously) is worse — it abolishes the deliberate commit, means partial sequences get verified (timing structure, battery, and a typo unlocks nothing but a prefix-collision does), and breaks the pinned "commit with `=`" model's generalization entirely.
- **The fix (contract amendment, adopted in §3.2b):** the contract explicitly requires every disguise to designate **one discrete, natural, frequently-used decoy interaction as the commit gesture**, and a disguise whose decoy genre lacks one must *add a genre-plausible affordance* — here, an explicit **"Convert" button** (plenty of real converters, especially currency/multi-step ones, have one; live conversion is then triggered by the button rather than per keystroke — a believable, if slightly old-fashioned, converter design). The believability spec for the converter must then pin "button-triggered conversion" as its reference model so the decoy is coherent. If a genre cannot absorb an explicit commit affordance believably, **it is not a valid disguise** — the commit convention is a hard eligibility test, not a suggestion.
- **Collision analysis:** a passcode like `D7|D5|U_KG|U_LB` is "convert 75 kg to lb" — plausible real use, exactly analogous to the `1 2 + 3 4` arithmetic collision. The generalized setup guidance (nudge toward non-obvious sequences, e.g. mixing category switches) carries over.
- **Verdict: contract holds after the commit-convention amendment.** This candidate is the reason the amendment exists.

### 5.2 Tip calculator (identity-plausible under "Calculator+")

- **Decoy function:** bill amount in, tip percentage and split count chosen, per-person total out. Genuinely functional and simple.
- **Tokens:** `D0…D9`, `DOT`, tip-preset keys (`TIP_10`, `TIP_15`, `TIP_18`, `TIP_20`, `TIP_CUSTOM`), split stepper (`SPLIT_UP`, `SPLIT_DN`) — roughly 19 tokens, comparable to the calculator's 17.
- **Commit gesture:** a **"Calculate" / "Done" button** — natural for the genre (tip calculators routinely have one). Passes the commit convention without amendment.
- **Clear gesture:** a "Reset" button — natural. Valid.
- **Caption slots:** a one-line caption above the bill field in setup modes — fits the layout.
- **What's weak (not broken):** the token distribution is lopsided — real use is mostly digits plus one tip tap, so passcodes that *look like real tip entries* (`D4|D2|DOT|D5|D0|TIP_20`) are collision-prone, and the strength nudge must push toward sequences no diner would enter (multiple tip presets in a row, split-stepper runs). This is a believability-spec obligation (§3.2f collision analysis), not a contract gap. Degenerate-input clause: mashing `TIP_*` keys repeatedly must visibly just re-select presets — sane by construction. **Entry trace (§3.2f):** this disguise makes traces unusually legible — the passcode's last `TIP_*` preset stays visibly selected and the split count stays where the passcode left it, live during entry and residually after commit; its believability spec must enumerate exactly this, and the §3.2b reset-on-lock guarantee is what returns the surface to the resting face (default preset, split 1) after any lock.
- **Verdict: contract holds cleanly.** Strong disguise #2 candidate alongside the converter.

### 5.3 Countdown timer / stopwatch (identity-incoherent under "Calculator+")

- **Decoy function:** a timer (digit entry for duration, start/pause/reset) and stopwatch (start/lap/reset).
- **Tokens:** timer digits `D0…D9` plus perhaps `LAP` — an alphabet of ~10–11 tokens, **all digits in practice**: the effective keyspace of a typical passcode collapses toward digits-only, the exact low-entropy case the idea plan's threat framing warns about. Disclosed-property rule applies (§3.2c), but the strength nudge has little to nudge *to* — a real weakness of the genre, to be stated in its believability spec.
- **Commit gesture:** **"Start"** — natural and discrete. Note the decoy consequence: committing a passcode *starts a countdown* (e.g. entering `9|0|4|5` then Start begins a 90:45 timer). That is correct contract behavior — the decoy performs its function unconditionally on commit — but it means every unlock attempt leaves a running timer behind, and on a *successful* unlock the timer would fire a completion alert later. The believability spec must pin: the decoy timer is display-only (no notification, no sound scheduled via the OS — a scheduled notification from "Calculator+" while the app is closed is both a disguise leak and a violation of the notification-free identity rule). A silent timer is a slightly odd timer — another genre weakness. **Entry trace (§3.2f):** the typed duration is fully legible on screen during entry, and the residual trace after commit is a *running timer showing the passcode as its duration* — the most readable entry trace of any candidate; the spec must state it, and the §3.2b reset-on-lock guarantee (fresh surface, no running decoy timer) is what prevents the trace from surviving a lock.
- **Clear gesture:** "Reset" — natural. Valid.
- **Stopwatch mode is not usable for entry at all** (its only inputs are start/lap/reset — no token diversity); the disguise would designate the timer face as the entry surface, which the contract permits (tokens need not come from every screen of the decoy) but must be pinned in the believability spec.
- **Identity: incoherent under "Calculator+"** (§2.1) — viable only for a future separate SKU.
- **Verdict: the contract technically holds** (tokens, commit, clear, captions, cover face, believability spec all exist), **but the candidate fails on merit**: digits-only entropy collapse, the silent-timer oddity, and identity incoherence. Its value here is demonstrating that the contract correctly *exposes* these problems as spec obligations rather than hiding them — the eligibility bar is the believability spec plus §2.1, not the interface alone.

### 5.4 Amendments adopted from validation

1. **Commit convention (from §5.1):** every disguise must designate exactly one discrete, natural, genre-plausible decoy interaction as its commit gesture; genres without one must add a believable explicit affordance or are ineligible. Folded into §3.2b.
2. **Decoy-consequence clause (from §5.3):** the believability spec must enumerate what the decoy *does* on commit and confirm it is silent, local, and side-effect-free beyond its own screen (no notifications, no scheduled OS work, no media/DB writes). Folded into §3.2f.
3. **Entropy disclosure (from §5.2/§5.3):** the believability spec must state the alphabet size, the realistic token distribution of casual use, and the resulting collision guidance for setup copy. Folded into §3.2c/f.
4. **Entry-trace clause (from §5.2/§5.3, where lopsided-token disguises make traces most legible):** the believability spec must enumerate the visible decoy state a typed code produces, live and residual, and confirm the §3.2b reset-on-lock guarantee returns the surface to the resting face. Folded into §3.2f and the §10/Q6 spec template.

---

## 6. Interaction with Pinned Behaviors

How each pinned behavior generalizes — and what stays in the host verbatim.

| Pinned behavior | Generalization | Stays host-verbatim |
|---|---|---|
| **First-run setup state machine** (idea plan §2.3) | Runs on the **default disguise** (calculator — the shipped identity's native disguise; a future SKU flavor may set a different default at build time, §2.1). Banner/caption content is delivered via the semantic caption slots — the calculator disguise renders the idea plan's pinned strings verbatim (§3.2d string authority); every transition, buffer rule, overflow rule, and backgrounding-discards rule is unchanged (trivial-sequence warning on entry to confirm; mismatch banner shown back in the entry phase — §3.2d). | The entire ENTRY→CONFIRM machine, plain-sequence in-memory confirm, hash-once-on-confirm with fresh salt, one-time no-recovery notice. |
| **Unlock flow** (idea plan §2.4) | Token events replace key presses; commit signal replaces `=`; clear signal replaces `AC`. The decoy always performs its function first on commit. | Silent non-match, skip-KDF on short/overflow, off-UI-path verification with byte-identical rendering, buffer clear on commit/clear/backgrounding-while-locked — plus the decoy-display half of the backgrounding-while-locked clear, delivered by surface re-instantiation (§3.2b). |
| **Change passcode / VerifyCurrent** (idea plan §2.7) | VerifyCurrent renders on the current disguise's surface with disguise-styled visible feedback (caption slot; the calculator renders the pinned "Incorrect code — try again" verbatim). EnterNew/Confirm render on the current disguise — or on the *new* disguise when the flow is a §2.3 switch. A plain change-passcode rewrites the v2 envelope preserving `activeDisguiseId` (§2.2). | The three-phase machine, unlimited retries, cancel-intact, atomic replace, backgrounding-abandons. |
| **Immediate lock on backgrounding + clear-on-lock** (idea plan §2.5) | The trigger is scene/lifecycle-level and never consults the disguise. The pinned "on every lock transition, the calculator display state and the attempt buffer are cleared (pristine calculator)" rule generalizes as the **teardown-and-reinstantiate guarantee (§3.2b)**: the buffer clear stays host-internal (§3.3), and the visible display clear is delivered by discarding the composed surface and constructing a fresh one — the resumed lock screen is a pristine resting face for **every** disguise, including on backgrounding-while-locked (where `FLAG_SECURE` alone would not blank the live resumed screen, per android-plan §2.3). | The lock triggers, no-grace rule, the buffer clear, and the surface teardown itself. No disguise can observe, delay, or suppress a lock — or retain decoy state across one. |
| **Suppression window** (idea plan §2.5.1) | None — it is a property of vault flows (photo import), which live above the seam. | Entirely: in-flight flag, closed exemption list, 2-minute monotonic cap, fail closed, repository-level import completion. |
| **Snapshot cover** (idea plan §6) | iOS: the cover *face* comes from the active disguise (§3.2e); install/remove timing on resign-active/active is host-owned and unchanged. Android: `FLAG_SECURE` unconditional, disguise-independent; the documented platform divergence carries over verbatim. | All timing, the independence-from-lock-decision rule, the every-tab DoD verification. |
| **No-logging rule** (idea plan §6) | Extended to disguise implementations explicitly (§3.3). | The rule itself, release stripping mechanisms. |
| **Accessibility tension** (idea plan §6) | Generalizes per disguise: each decoy is accessible *as its genre* (converter keys labeled "kilograms", not "passcode key"), with no unlock-revealing metadata — a believability-spec obligation. | The principle and its DoD check. |
| **Store-review posture** (idea plan §7) | Unchanged: disclosure lives in the store listing; review notes include a demo passcode *for the shipped default disguise*. A build shipping disguise switching must mention it in review notes (the reviewer's demo path must work regardless of what a user could switch to — switching is post-unlock, so the demo passcode always works). | The whole §7 posture. |

**Settings (§3.4 of the idea plan):** iteration 2 adds one row — **Disguise** — under the Appearance section the idea plan already reserved layout room for. It hosts the picker + switch flow of §2.3.

---

## 7. Migration Plan (Iteration 2)

Ordered refactor steps with acceptance checks. Sequencing principle: **host seam first, calculator re-homed second, envelope migration third, switching UI last** — the app is shippable after every step.

### 7.1 Passcode-blob migration (both platforms, first)

1. Add v2 envelope encode/decode beside v1 (the v1 decoder is never deleted). The v2 decoder explicitly rejects envelope versions > 2 (§3.4 forward obligation).
2. Read path: v1 envelope ⇒ interpreted as `{tokenSetId: "calculator", alphabetVersion: 1, activeDisguiseId: "calculator"}` — **absence means calculator.v1**; v2 ⇒ read verbatim. Android additionally: the plain `activeDisguiseId` mirror key (§3.4) is the launch-path read; the envelope is never unwrapped at launch; the mirror re-syncs from the envelope on unlock.
3. Eager rewrite: on the first launch that reads a v1 envelope, re-encode as v2 (salt + hash copied byte-for-byte, new fields as above) and atomically replace the storage item (Android: the mirror key written in the same commit). Failure to rewrite is silent and harmless — step 2's interpretation remains in force forever.
4. *Accept:* unit tests prove (a) a v1 blob verifies the same passcode before and after rewrite, (b) the rewrite is byte-preserving on salt/hash, (c) a rewrite failure leaves a verifiable v1 blob, (d) an upgraded install unlocks first-try with the iteration-1 passcode — **no re-enrollment path is reachable during upgrade** — (e) a version-3 envelope is explicitly rejected, and (f, Android) a missing/stale mirror key resolves fail-closed to the calculator face and heals on the next unlock. Manual: upgrade an iteration-1 install in place and unlock.

### 7.2 iOS refactor steps

1. **Introduce the seam types** (`DisguiseProviding`, `DisguiseEvent`, `DisguiseMode`, `AlphabetDescriptor`, `DisguiseRegistry` with one entry). No behavior change. *Accept:* builds clean under Swift 6; all iteration-1 tests pass untouched.
2. **Generalize the recorder:** `PasscodeRecorder` → `Lock/TokenRecorder` over token-ID strings. *Accept:* recorder test suite passes with mechanical type renames only (32-cap, overflow-never-matches, clear semantics all green).
3. **Re-home the calculator** as `CalculatorDisguise` (engine, screen, view model, cover face move; view model emits `DisguiseEvent`s). *Accept:* the full engine table (all 17 rows) passes unmodified; the app is behaviorally identical end-to-end — including every pinned caption string rendered verbatim per §3.2d (manual disguise walkthrough from the iteration-1 `MANUAL_TESTS.md` passes verbatim); the alphabet-drift test (§10 risk 5) asserts the surface's emittable token set equals the current `alphabet.tokens` exactly; no vault file changed.
4. **Host consumes the seam:** `AppLockCoordinator` takes the active disguise from the registry via the envelope (fail-closed default per §3.4); commit path serializes via the active alphabet; the coordinator tears down and freshly re-instantiates the surface on every lock transition and on backgrounding-while-locked (§3.2b). *Accept:* coordinator test table passes; setup/unlock/change-passcode manual flows identical; snapshot cover still shows the calculator face from every tab; **decoy state resets to the resting face on every lock** — background mid-entry while locked, re-foreground: pristine calculator, no half-typed display state, fresh surface instance; same after unlock→lock round-trips.
5. **Switch flow + Settings row** per §2.3. *Accept:* switch-flow state-machine tests (cancel-intact at every phase, atomic replace, crash-before-commit leaves old code+face, crash-after leaves new); with a second disguise stubbed in tests, old code stops unlocking and new face shows immediately after commit; backgrounding mid-switch abandons cleanly; a plain change-passcode preserves `activeDisguiseId` (§2.2).

### 7.3 Android refactor steps

1. **Seam types** in `core/disguise/` (interface, events, registry with one entry). *Accept:* `assembleDebug` + full unit suite green, untouched.
2. **Generalize recorder + matcher:** `KeySequenceRecorder` → `core/lock/TokenRecorder`; `PasscodeMatcher` absorbed into host lock logic over the active alphabet. *Accept:* recorder/matcher suites pass with mechanical renames (cap, overflow, skip-on-short, canonical-ID serialization).
3. **Re-home the calculator** as `CalculatorDisguise : DisguiseProvider` (`CalculatorMode` → `DisguiseMode` rename). *Accept:* engine table-driven suite passes unmodified; Robolectric first-frame setup-mode test still passes; pinned caption strings rendered verbatim per §3.2d; alphabet-drift test (§10 risk 5) green against the current descriptor version; manual disguise feel identical.
4. **Host consumes the seam:** `AppLockManager`'s synchronous process-start read also reads the **plain `activeDisguiseId` mirror key** (§3.4 — the Keystore-wrapped envelope stays wrapped at launch; the read remains a small-keys-only Preferences read per android-plan §2.3's rationale); `SafeBoxApp`'s locked branch composes the registry's active surface, re-composed fresh on every lock transition and on `onStop`-while-Locked (§3.2b). *Accept:* `AppLockManagerTest` green including default-Locked, suppression cap; **clear-on-lock verified as buffer clear plus surface re-instantiation** — half-typed decoy state never survives a lock or a background-while-locked round-trip (pristine resting face on resume, the load-bearing clear android-plan §2.3 requires beyond `FLAG_SECURE`); missing/corrupt mirror key or unwrappable envelope resolves **fail-closed to the default calculator face in `Disguise` mode** (tested); mirror re-syncs from the envelope on unlock; process-death test still cold-starts into the (calculator) disguise.
5. **Switch flow + Settings row.** *Accept:* mirror of iOS step 5, plus: the mirror key and envelope are written in the same switch commit, and an induced conflict resolves in the envelope's favor on unlock; recents-thumbnail verification unchanged (`FLAG_SECURE` untouched).

**Cross-platform gate before any disguise #2 is built:** the believability spec for the candidate (per §3.2f, at the rigor of the calculator's §2.1 spec including a shared behavior table and the entry-trace clause) is written, reviewed, and committed to `docs/plans/` — the contract forbids building a disguise ahead of its spec.

---

## 8. Mockup Artboards (produced separately; this spec is the authority)

The visual mockups for the skeleton's user-facing surfaces cover, at minimum:

1. **Settings → Disguise picker** — compiled-in disguise cards, current selection, the §2.1 identity-honesty disclosure copy.
2. **Switch-flow explainer** — the re-enrollment explanation screen (§2.3), **shown parameterized by the current disguise's name and commit gesture** (the copy is a template, not a fixed calculator string), including the "vault contents unchanged" reassurance.
3. **VerifyCurrent on the current disguise** — calculator face with the visible wrong-code feedback treatment (pinned string "Incorrect code — try again").
4. **CaptureNew / ConfirmNew on a candidate disguise** — the unit converter face (with its "Convert" commit button per §5.1) showing the caption slot styled to the converter, the strength nudge, and the overflow "start again" state.
5. **Candidate disguise resting faces** — unit converter and tip calculator in `Disguise` mode (also serve as their iOS snapshot cover faces, and depict the state the §3.2b reset guarantee returns to).
6. **Switch success confirmation** — return-to-Settings state naming the new disguise and restating the no-recovery rule for the new code.

Mockups illustrate this spec; where they diverge from it, the spec wins.

---

## 9. Non-Goals

Explicitly out of scope for this design and for iteration 2's skeleton work:

1. **No iteration-1 code change.** Iteration 1 ships the calculator hard-wired per the committed plans; this document's only forward obligation on iteration 1 is what those plans already require (the engine/recorder/coordinator separation that makes §7 mechanical).
2. **No disguise marketplace or downloadable disguises.** Disguises are compiled into the app, written in this repo, reviewed like any code. No dynamic loading, no remote assets, no disguise SDK — the zero-network posture alone rules this out, and runtime code/asset loading is store-policy poison for a disguised app.
3. **No multiple simultaneous passcodes.** Exactly one envelope, one valid passcode, one active disguise at all times. The atomic switch of §2.3 exists precisely to preserve this invariant.
4. **Decoy passcode remains a separate roadmap item** (idea plan iteration-2+ candidate list). It is *adjacent* to this design (a decoy code would enroll in the same active alphabet) but is not designed here; nothing in the v2 envelope precludes it (the reserved `decoyPasscodeHash` slot in VaultSecurity stands).
5. **No launcher-identity switching, no dynamic icons/names, no per-identity SKU design** — §2.1 records the constraint and defers the SKU question to §10.
6. **No disguise-specific lock policies** (per-disguise timeouts, per-disguise buffer rules). The host's rules are universal by design.
7. **No biometric interaction design.** Biometrics remain governed by the idea plan's iteration-2 open questions; if built, the trigger design must be disguise-agnostic or become a host-mediated slot — decided there, not here.

---

## 10. Risks & Open Questions

**Risks**

1. **Over-abstraction of the seam.** The central risk; named failure modes and binding mitigations in §4.3. Recommendation: treat §3.2's six-item contract (plus the §3.2b host-side reset guarantee) as frozen; amendments require editing this document before code.
2. **Identity/face mismatch erodes the disguise** (§2.1). A "Calculator+" install showing a converter is *plausible*; showing a timer is a tell. Recommendation: iteration 2 ships only identity-plausible disguises under the current identity (converter and/or tip calculator per §5); gate anything else on the SKU question below.
3. **User lockout via forgotten new code after a switch.** Re-enrollment (§2.2) means a switch creates a brand-new code with the same no-recovery policy — a user who switches and forgets is wiped-and-restarted. Recommendation: the switch flow restates the no-recovery warning at CONFIRM_NEW (mockup artboard 6), verbatim in tone with the idea plan §2.6 notice.
4. **Active disguise unresolvable at launch.** The §3.4 fail-closed face rule covers every cannot-resolve case — `activeDisguiseId` names a disguise the running build lacks (app downgrade; a hypothetically removed disguise), the Android mirror key is missing or stale, or the envelope is unreadable/unwrappable — by rendering the default calculator face in `Disguise` mode, buffer rules unchanged. In the missing-registry-entry case that face's alphabet may be unable to express the enrolled passcode (effective lockout); fail-open alternatives are worse (an error screen or non-disguise surface is a tell). Recommendations: **never remove a shipped disguise from the registry** (registry entries are append-only, like the alphabet rule); treat OS-level downgrades as unsupported (consistent with §3.4's downgrade note — iteration-1 behavior on a v2 envelope is unspecified either way); the transient Android cases (missing mirror, unwrappable envelope) self-heal on the next successful unlock via the envelope re-sync. Recorded as accepted.
5. **Alphabet drift between a disguise's UI and its descriptor.** A keypad change that silently adds/removes a token-emitting control desynchronizes UI and alphabet. Recommendation: a unit test per disguise asserting the surface's emittable token set equals `alphabet.tokens` exactly — **always compared against the CURRENT descriptor version** (older enrollments need no separate drift check: serialization is version-invariant, and a v1 enrollment cannot contain later-added tokens, §3.2c) — added to the §7 acceptance checks.
6. **Refactor regression in the lock path.** Mitigated by §7's step ordering (iteration-1 suites must pass mechanically at every step) and by keeping the calculator engine byte-identical.
7. **Store-review surface widens slightly.** Disguise switching is a post-unlock feature, so the reviewer's demo passcode always works (§6); still, "the lock screen can look like other apps" belongs in the review notes. Recommendation: extend the idea plan §7 checklist when switching ships.

**Open questions (with recommendations)**

| # | Question | Recommendation |
|---|---|---|
| 1 | Which disguise #2 ships first? | Tip calculator: passes the contract with no amendments (§5.2), smallest believability spec, identity-plausible. The converter follows once its "Convert"-button reference model is spec'd. |
| 2 | Per-identity SKUs (e.g. "Timer+") — pursue or drop? | Defer until a shipped identity-plausible disguise proves demand for switching at all. Each SKU is a full store-review exposure (idea plan §7) and a permanent identifier commitment; do not spend either on speculation. |
| 3 | Should the setup default disguise be user-selectable at first run? | No for iteration 2: first run uses the identity's native disguise (calculator), keeping setup identical to iteration 1; switching is available immediately after setup. A pre-setup picker adds a non-calculator screen to the most disguise-critical moment of the app's life. |
| 4 | Does storing `activeDisguiseId` — in the envelope, and on Android also as a plain Preferences mirror key (§3.4) — leak anything? | It reveals which face the owner chose. The plain Android mirror makes this readable to anyone who can read app-private preferences — but such an attacker can also simply open the app and look at the lock screen, so the marginal information is nil; it is a UI fact, not a crypto fact. Accepted. The authoritative copy stays inside the wrapped envelope (preserving §2.3's switch atomicity and the on-unlock re-sync source); the mirror is what keeps the Android launch path an honest one-small-key read instead of a synchronous Keystore unwrap. |
| 5 | Caption slot localization — host strings or disguise strings? | Host owns the semantic states and their *meaning*; the disguise owns the rendered phrasing (it must be genre-styled — with the calculator's phrasings pinned verbatim to the committed strings, §3.2d). Localization therefore lives with the disguise, keyed by the host's semantic states. Revisit alongside the idea plan's deferred localization item. |
| 6 | Should the believability spec be a formal artifact with its own template? | Yes: iteration 2 should commit `docs/plans/disguise-spec-template.md` derived from §3.2f's required contents (reference model, shared behavior table, degenerate-input clause, collision analysis, decoy-consequence clause, **entry-trace clause — visible decoy state a typed code produces, live and residual, plus reset-on-lock confirmation**, entropy disclosure, identity grade, accessibility posture), so disguise #2's spec starts from the calculator's rigor rather than re-deriving it. |
