# SafeBox — Calculator Disguise Design Spec

**Document:** `docs/plans/calculator-disguise-design.md`
**Status:** Design authority for the iteration-1 calculator lock screen on both platforms. Behavior (engine semantics, key IDs, lock modes, banner state machines, verification) is pinned by `docs/plans/idea-plan.md` (§2, §6, §8) and the engine/recorder sections of `docs/plans/ios-plan.md` (§2.3, §2.4, §4.1) and `docs/plans/android-plan.md` (§2.3, §4.1) — those documents win on logic. This document owns everything visual and interactive: layout, tokens, type, motion, copy consolidation, accessibility, and believability. Where a platform plan sketched styling in passing (e.g. "dark background, orange operators" in ios-plan §4.1), this document refines that sketch into the binding spec.
**Scope note (product-owner decision):** the pluggable disguise abstraction is design-only and lands in iteration 2. Iteration 1 builds the calculator hard-wired exactly as the committed plans describe; nothing in this document changes iteration-1 scope. The iteration-2 skeleton design will assess which parts of this spec are disguise-generic and which are calculator-bound; this document does not pre-certify itself against that abstraction.
**Mockups:** visual mockups are produced separately (artboard list in §9). They are directional; where a mockup and this spec disagree, **this spec is authoritative**.
**Reference-verification items:** several presentation facts in this spec are pinned provisionally against the reference model (iOS Calculator basic mode, per idea-plan §2.1) and must be verified on a physical reference device during the M8 hardening pass. They are marked **[R1]…[R5]** inline and collected in §8.4.
**Last updated:** 2026-08

---

## 1. Design goals & anti-goals

### Goals

1. **Instantly recognizable as a calculator.** A person handed the phone must parse the screen as "a calculator" in under a second: display on top, 4×5 keypad below, familiar row order, familiar glyphs. We borrow the *functional conventions* every calculator shares (layout grammar, operator column on the right, wide zero, `=` in the corner) because those conventions are generic and unprotectable.
2. **Credible as a real product.** "Calculator+" must look like a competent, minimal third-party calculator someone chose to install — clean, current, slightly opinionated. A visibly cheap or broken-looking calculator invites exactly the scrutiny the disguise exists to avoid.
3. **Neutral identity.** The visual system is original: its palette, key shape, and surface treatment belong to "Calculator+", not to any platform vendor.
4. **Zero tells.** Nothing on the lock screen may hint at a second function: no lock/shield/eye iconography, no unusual affordances, no delays or spinners on `=`, no differential feedback of any kind between arithmetic and passcode entry (idea-plan §2.4: verification is silent and off the UI path).

### Anti-goals

1. **Do not clone Apple's Calculator.** Apple's trade dress — true-black background, circular keys, the specific orange / light-gray / dark-gray triad, inverted white-fill active-operator treatment — is off limits (idea-plan §1 launcher-identity rule and §7 store-review risk). We may share a warm operator accent (calculators conventionally accent operators) but not Apple's hue-on-black-circles combination.
2. **Do not clone Google's Calculator.** Material-You dynamic pastel pills, the two-tone expression/result display, and the expandable side rail are equally off limits.
3. **Do not over-design.** Gradients, glassmorphism, mascots, animated numerals, sound effects — all rejected. A flashy calculator draws attention and dates quickly.
4. **Do not add calculator features beyond the pinned key set.** No history tape, no memory keys (M+/MR), no scientific toggle, no themes menu. The key set is fixed by idea-plan §2.1; extra features enlarge the surface where the disguise can crack and are out of iteration-1 scope regardless.

---

## 2. Layout spec (portrait, phones first)

### 2.1 Grid

The keypad is a 4-column × 5-row grid, rows top to bottom:

| Row | Keys |
|---|---|
| 1 | `AC/C` · `±` · `%` · `÷` |
| 2 | `7` · `8` · `9` · `×` |
| 3 | `4` · `5` · `6` · `−` |
| 4 | `1` · `2` · `3` · `+` |
| 5 | `0` (double width) · `.` · `=` |

Canonical key IDs per idea-plan §2.2 (`D0…D9, DOT, ADD, SUB, MUL, DIV, PCT, SIGN`, plus non-passcode `CLEAR`, `EQUALS`) are unchanged and unaffected by anything visual in this document.

### 2.2 Sizing rules (platform-neutral, pt on iOS ≡ dp on Android)

The **height band is the driver**: the keypad block targets **65% of usable height** (acceptance band **62–68%**), and width supplies a ceiling, never the target. Definitions:

- `S` = screen width inside horizontal safe-area insets; `H` = screen height inside vertical safe-area insets.
- **Side margin** `m` = 4% of `S`, clamped to [12, 20]. Content width `W = S − 2m`.
- **Horizontal gutter** `g` = 3% of `W`, clamped to [8, 14].
- **Key width** `k = (W − 3g) / 4`.
- **Usable height** `U = H − g` (the keypad block is bottom-anchored with a bottom margin of `g` above the bottom safe-area inset; everything above the block, up to the top safe-area inset, is the display region — §2.3).
- **Vertical gutter** `g_v` starts equal to `g` and may grow only per the aspect rule below, capped at **1.5 × g**.
- **Key height (height-driven):** `h = (0.65 × U − 4g) / 5`, then clamped by:
  1. **Aspect ceiling:** `h ≤ k` — keys are never taller than they are wide.
  2. **Touch floor:** `h ≥ 44pt / 48dp` in both dimensions — this is the minimum *touch target*; if a pathological layout would push the visual key below the floor, the visual key may shrink to no less than 40 but its hit area must remain ≥ 44pt/48dp.
- **Aspect rule (tall screens):** when the aspect ceiling clamps `h` and the resulting block `5h + 4g_v` falls below the 62% band floor, grow `g_v` (vertical gutter only; the horizontal gutter is unchanged) until the block reaches the floor or `g_v` hits its 1.5×g cap, whichever comes first. On ultra-tall screens (aspect > ~2.2:1) even the cap may leave the block below the band; that shortfall is **accepted and recorded** — the display region absorbs the remainder, and no further rule fires.
- **Keypad block height** = `5h + 4g_v`; the display region takes the remainder of `U`.
- **Double-width zero**: spans exactly `2k + g` (two cells plus the swallowed gutter). Its glyph is centered on the center of the first column (i.e., at `k/2` from the key's leading edge), so the `0` optically aligns with the digit column above it.

**Worked example — 390 × 844 (iPhone 14-class; safe insets 47 top / 34 bottom):**
`m` = 4% × 390 = 15.6 → 16; `W` = 358. `g` = 3% × 358 = 10.7 → 11. `k` = (358 − 33) / 4 = 81.25 → **81**. `H` = 844 − 47 − 34 = 763; `U` = 763 − 11 = **752**. Height-driven `h` = (0.65 × 752 − 44) / 5 = (488.8 − 44) / 5 ≈ **89** → the aspect ceiling binds (89 > k = 81) → `h = 81` (square keys). Block at `g_v` = 11: 5×81 + 44 = 449 → 449 / 752 = **59.7%**, below the band floor → aspect rule: need 4`g_v` ≥ 0.62 × 752 − 405 = 61.2 → `g_v` = **16** (≤ cap 1.5 × 11 = 16.5). Block = 405 + 64 = **469** → 469 / 752 = **62.4%** — inside the band. Display region = 752 − 469 = **283** (~37.6%). `h` = 81 ≥ 44 ✓.

**Worked example — 320 × 568 (320pt-wide small-screen class; safe insets 20 top / 0 bottom):**
`m` = 12.8 → 13; `W` = 294. `g` = 8.8 → 9. `k` = (294 − 27) / 4 = **66.75**. `H` = 548; `U` = 548 − 9 = **539**. Height-driven `h` = (0.65 × 539 − 36) / 5 = (350.4 − 36) / 5 ≈ **63** → neither clamp binds (63 ≤ 66.75; 63 ≥ 44) → `h = 63`, `g_v = g = 9`. Block = 5×63 + 36 = **351** → 351 / 539 = **65.1%** — inside the band, near the 65% target. Display region = **188** (~34.9%).

### 2.3 Display region

- Everything above the keypad block is the **display region** — the remainder of `U` after the height-driven keypad block (target 32–38% of usable height, the complement of the keypad band; on ultra-tall screens where the aspect rule's cap binds, it may run larger, per §2.2's recorded acceptance).
- The numeric readout is a single line, **right-aligned**, bottom-anchored inside the display region with padding `m` horizontally and `g` vertically. Empty space above the readout is just background — no expression line, no history (anti-goal 4).
- The **caption strip** (setup/verify banners, §5.5) sits at the top of the display region. In Disguise mode the strip does not exist and its space belongs to the display region (Disguise mode never shows it, so there is no layout shift a bystander could ever observe — the strip is only present in the CaptureNew/ConfirmNew/VerifyCurrent modes defined by the platform plans).

### 2.4 Safe areas

- Background color paints edge-to-edge (behind status bar and home indicator / gesture nav).
- All interactive content respects safe-area insets. The `=` key must clear the home-indicator/gesture region by the bottom margin rule above.
- Status bar style follows the theme (light content on dark theme, dark content on light).

### 2.5 Small-screen floor (320pt-width class)

Per the §2.2 worked example: at `S = 320`, `m = 13`, `W = 294`, `g = 9`, `k ≈ 67`, `h = 63` — comfortably above the touch floor, and the keypad block lands at 65.1% of usable height, inside the band. The display base type size steps down per §3.3 if needed. No layout variant is required; the same rules degrade gracefully. Verify on the smallest supported device class as part of §8.

### 2.6 Tablets / large screens

- **Bounded keypad:** content width `W` is capped at **480pt/dp**; the keypad + display column is centered horizontally. Key height capped at **96**.
- **The 62–68% band does not apply on tablets** — the bounded-centered-column rule governs. Re-verified under the §2.2 rules: at `W` = 480, `g` = 14, `k` = (480 − 42) / 4 = 109.5; the height-driven `h` would exceed both the aspect ceiling and the 96 cap, so `h = 96` (keys wider than tall — acceptable), block = 5×96 + 4×14 = 536, bottom-anchored inside the centered column. On a tall tablet that is well under 62% of usable height; accepted — idea plan scopes tablets to "does not crash, is usable."
- The area outside the column is plain background. No two-pane layout, no scientific expansion.

### 2.7 Landscape — decision: portrait-locked (phones)

**The lock screen (and, in iteration 1, the whole phone app) is portrait-locked.**

Justification against credibility: a rotating basic calculator invites the expectation of a scientific landscape mode (Apple's behavior); shipping a stretched 4×5 grid in landscape looks broken — a worse tell than not rotating. Portrait-locked is a fully credible posture for a minimal calculator app on its own terms: a 4×5 keypad is a portrait artifact, and locking to it reads as a deliberate, focused product choice. It also halves the layout surface that must be verified tell-free.

Two platforms cannot fully honor the lock, and both get the same fallback:

- **iPad:** orientation locking is unreliable under multitasking; the bounded-centered-column rule of §2.6 applies in any orientation, which remains credible because the keypad column simply stays centered.
- **Android large screens / foldables:** modern Android may **ignore or letterbox** an activity's `android:screenOrientation="portrait"` request on large screens. Where the request is not honored, §2.6's bounded-centered-column rule is the **required** behavior in whatever orientation results — mirroring the iPad paragraph above. The app must never render a stretched full-width keypad.

Revisit only if iteration 2 adds a landscape disguise deliberately.

---

## 3. Visual system

### 3.1 Token palette

Named tokens, with concrete values for both themes. **Dark is the default/primary theme** (follows system setting; a calculator that ignores dark mode reads as abandoned).

| Token | Role | Dark | Light |
|---|---|---|---|
| `disguise/bg` | screen background | `#17191C` | `#F2F3F5` |
| `disguise/displayText` | readout digits, "Error" | `#F5F6F7` | `#1A1C1F` |
| `disguise/keyDigit` | digit + `.` key fill | `#2A2D33` | `#FFFFFF` |
| `disguise/keyDigitPressed` | pressed fill | `#3A3E46` | `#E2E5EA` |
| `disguise/keyFn` | top row (`AC/C`, `±`, `%`) fill | `#43484F` | `#D9DDE3` |
| `disguise/keyFnPressed` | pressed fill | `#565B63` | `#C4C9D1` |
| `disguise/keyOp` | operator column (`÷ × − +`) and `=` fill | `#B45309` | `#B45309` |
| `disguise/keyOpPressed` | pressed fill | `#D97706` | `#92400E` |
| `disguise/keyOpActiveRing` | pending-operator ring (§4.9) — outline only, both themes | `#F7C77E` | `#F7C77E` |
| `disguise/keyLabel` | glyphs on digit/fn keys | `#F5F6F7` | `#1A1C1F` |
| `disguise/keyLabelOnOp` | glyphs on operator keys | `#FFFFFF` | `#FFFFFF` |
| `disguise/caption` | setup/verify banner text | `#A9AFB8` | `#5A6069` |
| `disguise/captionError` | VerifyCurrent error text (**Settings only**, never on the lock screen — §5.6) | `#E5484D` | `#B3261E` |

Distinctiveness check against the anti-goals: graphite-blue background (not Apple's pure black, not Google's dynamic surface), rounded-rectangle keys (not circles, not pills), burnt-amber operators `#B45309` (warm like convention dictates, but a visibly different, deeper hue than Apple's `#FF9F0A`-family orange, on a different key shape and background), and a ring—not an inverted fill—for the active operator.

### 3.2 Contrast requirements (WCAG AA)

- **Display readout:** ≥ 7:1 against `disguise/bg` (achieved: ~15:1 dark, ~14:1 light).
- **Key glyphs:** all key glyphs qualify as large text (≥ 24pt), so the AA floor is 3:1 — but the **target is ≥ 4.5:1** on every key in both themes. White on `#B45309` ≈ 5.0:1; white on `#2A2D33` and `#43484F` ≥ 9:1; `#1A1C1F` on the light key fills ≥ 12:1. All pass.
- **Caption text:** ≥ 4.5:1 (caption is body-size). `#A9AFB8` on `#17191C` ≈ 8:1; `#5A6069` on `#F2F3F5` ≈ 5.6:1.
- **Pending-operator ring (non-text indicator, WCAG 1.4.11):** `disguise/keyOpActiveRing` `#F7C77E` against its resting `disguise/keyOp` fill `#B45309` ≈ 3.2:1 — meets the 3:1 non-text floor in both themes (the token is theme-invariant, as is the fill it sits on). This pair is part of the verification set below.
- **Pressed states** must keep their glyph contrast ≥ 3:1 for the press duration (all listed pressed fills do).
- Verification: every token pair above — including `keyOpActiveRing`-on-`keyOp` — is re-checked with a contrast tool whenever a value changes; this is a review gate, not a one-time check (see §7 and §8).

### 3.3 Typography

- **System fonts only**: SF Pro (iOS), Roboto / platform default (Android). No bundled fonts (a custom font in a "calculator" binary is inventory a reviewer or forensic bystander can notice, and adds weight for nothing).
- **Display readout:** regular weight, **tabular/monospaced digits mandatory** (SF Pro `monospacedDigit`, Roboto `tabular-nums`/`font-feature-settings "tnum"`), so digits don't jitter horizontally while typing. Base size: the largest size at which the reference string `-888,888,888` fits `W` minus padding — in practice ~72–80pt on a 390pt-wide phone; compute, don't hardcode.
- **Auto-shrink (derived, not asserted):** single line, never wraps, never truncates. The maximum display string follows from the §4 rules — the entry cap (§4.2), 9-significant-digit precision (§4.1), separators (§4.4), and the scientific format (§4.6):
  - longest entry form: `-88,888,888.8` — 13 characters (sign + 9 significant digits + 2 grouping separators + decimal point); the all-integer `-888,888,888` (12 characters) is kept as the base-size fitting reference below;
  - longest plain result form: the same 13-character mixed-decimal shape (`-88,888,888.8`-class), fitting at ≈ 12/13 ≈ **0.92× base**;
  - longest scientific form: `-9.99999999e-308` — 16 characters (sign + 9-significant-digit mantissa with decimal point + `e` + negative 3-digit exponent);
  - `Error` — 5 characters.
  The base size fits the 12-character reference string; the 16-character scientific worst case therefore fits at ≈ 12/16 = **0.75× base** (tabular digits make character count a faithful width proxy; separators and the point are narrower, giving slack). The auto-shrink floor is pinned at **0.70×** base — derived headroom, not a guess. Because the §4 rules bound every reachable string to ≤ 16 characters, the floor always suffices; if a formatting bug ever produces a longer string, clip trailing (never leading) content — but treat that as a defect.
- **Key glyphs:** digits and `.` at **32pt/sp** medium; operator glyphs (`÷ × − + =`) at **34pt/sp** medium (operators need ~2pt optical compensation); top-row functions (`AC`/`C`, `±`, `%`) at **26pt/sp** medium. Use the proper Unicode glyphs `÷ × − ± %` — never ASCII `x`, `/`, or hyphen for minus.
- **Caption strip:** system caption style, **13pt/sp**, regular, single line preferred, two lines maximum (for the hint captions), centered.

### 3.4 Key shape, radii, elevation

- **Shape:** rounded rectangle, corner radius = **24% of key height** (iOS: continuous/squircle corner style; Android: `RoundedCornerShape` — the platform-native corner curve difference is acceptable). Deliberately neither Apple's circle nor Material You's near-pill.
- **Elevation:** **flat**. No shadows, no strokes, no gradients — fill color alone separates keys from background (the sole exception is the pending-operator ring, §4.9, an outline that is itself part of the calculator's grammar). Flat is current, renders identically on both platforms, and gives the pressed-state fill change maximum clarity.
- The `AC/C` label swap (§4.8) is a plain text swap, no animation.

---

## 4. Display behavior spec

The engine (ios-plan §4.1, android-plan §4.1) computes the *value*; this section pins how values are *presented*. The idea-plan §2.1 shared input→display table (all 17 rows) is the acceptance contract and every rule below is consistent with it: `5`, `8`, `6`, `7.49`, `0.07`, `4`, `1`, `Error`, `0.3`, `0`, `-0`, `46` must render exactly as written there.

> **Coordinated amendment (status of this section):** the display-formatting rules below — the entry cap (§4.2), grouping separators (§4.4), and the scientific presentation (§4.6) — go beyond what the platform plans' engine sections currently specify ("cap display length", trailing-zero stripping). They are **proposed as a coordinated amendment** that the platform plans' engine/formatting sections and their test tables adopt at implementation time, not a unilateral design-doc pin. None of them conflicts with the 17-row table; each is additionally gated on reference-device verification where marked.

1. **Precision:** up to **9 significant digits**, rounded (idea-plan §2.1). Trailing zeros after the decimal point are stripped (`0.50` renders `0.5`); a bare integer shows no decimal point (`5`, not `5.`).
2. **Entry length cap — pinned. [R1]** Digit entry into the display stops at **9 digits** (all typed digits count toward the cap, including zeros after the decimal point; the synthesized leading `0` before a bare `.` does not). This is reference-model behavior; **verify the exact reference behavior on device (including whether leading zeros count) is an M8 verification item (§8.4 R1)**. A `.` press that could no longer be followed by any digit is likewise ignored by the entry; `±` still applies at any time (it adds no digit). **Keys pressed beyond the display cap MUST produce identical press feedback and haptics, and are STILL appended to the recorder buffer** — pinned: the recorder captures every allowed key (idea-plan §2.4); the engine ignoring a key for *display entry* changes nothing about capture. Consequence, stated as the requirement it is: a 32-key digits-only passcode types completely normally with **zero tell** while the display sits at its 9-digit cap, and unlocks.
3. **Decimal display:** while entering, the display echoes entry literally — `0.` after pressing `.` (leading zero synthesized), `0.5` etc. A second `.` in one operand is ignored by the engine (no visual response beyond the normal key press feedback).
4. **Thousands separators:** grouping separators (`,`) are inserted in the integer part of any displayed number with more than 3 integer digits, during entry and in results (`1,234`, `46` stays `46`). **Iteration 1 formats en-US style everywhere** (dot decimal, comma grouping): the shared table pins literal strings (`0.07`, `7.49`), and localization is an explicit iteration-2+ roadmap item (idea-plan §5). **Recorded believability gap:** in comma-decimal locales this reads as an unlocalized (but common) calculator app; locale-aware formatting joins the iteration-2 localization work, at which point the shared table gains a locale annotation. Separators are part of the coordinated amendment above — the platform plans' formatting code and test tables adopt them together with this spec.
5. **Negative numbers:** leading `-` immediately before the first digit (`-0`, `-12.5`). The `-0` and `± ±` behaviors follow the table rows 12–13.
6. **Overflow / scientific presentation. [R5]** The trigger is stated in **display-width / decimal-place terms** (never "nonzero rounds to zero at significant digits" — significant-digit rounding cannot send a nonzero value to zero): switch to scientific when the value cannot be rendered in plain decimal within the display's digit budget — i.e. when the integer part needs more than 9 digits (|value| ≥ 1e9), or when a nonzero value's leading significant digit sits so far right of the decimal point that plain decimal rendering within the budget would show no significant digit at all (|value| < 1e-8 territory; exact reference threshold is verification item §8.4 R5). Format: **mantissa keeping as many significant digits as fit the display — up to the full 9-significant-digit precision of §4.1, trailing zeros stripped — lowercase `e`, exponent with `-` for negative and no `+` for positive.** With the §3.3 derivation, a full 9-digit mantissa always fits at ≥ 0.75× base, so in practice the mantissa carries the value's significant digits up to 9. Examples: `1e9`, `2.5e-9`, `8.99999999e9` — the checklist case `999999999 × 9 =` (= 8,999,999,991) **must not silently render as `9e9`**; it renders `8.99999999e9`. No grouping separators in scientific mode. Identical format on both platforms (idea-plan §2.1 requires identical scientific presentation; this pins the glyph-level format, subject to R5 verification against the reference and adoption via the coordinated amendment).
7. **Error state:** division by zero renders the literal string **`Error`** in `disguise/displayText` — same size, same color, **no red, no icon, no shake**. Recovery per the engine spec (next digit or `AC`).
8. **`AC` vs `C` label — pinned rule, flagged for verification. [R2]** One key, engine-state-driven: shows **`AC`** in the cleared state and after `=` while a committed result shows; shows **`C`** during operand entry; pressing `C` clears the current entry (display → `0`) and the label reverts to `AC`; pressing `AC` clears everything. This rule is pinned so both platforms build the same thing, but it is a **verify-against-reference fact, not an assumption** — the reference's label behavior (especially the after-`=` state) is M8 verification item §8.4 R2, and the pinned rule is corrected to match the reference if they diverge. **Both presses clear the passcode recorder buffer and overflow flag** (pinned in ios-plan §4.1 / android-plan §4.1 — "C/AC clears the buffer and the flag"); the label swap is presentation only and changes nothing in recorder semantics.
9. **Pending-operator indication — decision: show it, via a ring. [R4]** While an operator is pending — i.e. after an operator press, before second-operand entry begins — the active operator key shows a **2pt inner ring** in `disguise/keyOpActiveRing`. **Outline ring only, no fill change** — the treatment is theme-symmetric by construction (the ring and the fill it sits on are the same in both themes, so there is no per-theme blend direction to get wrong). Lifecycle: the ring **appears** when an operator becomes pending; it **clears** when second-operand entry begins, on `=`, and on `AC`; it **hops keys** on operator replacement (a second operator pressed before any second-operand digit replaces the pending one). This lifecycle is pinned for build purposes but the reference's own indicator lifecycle — in particular whether it clears when second-operand entry begins or persists until commit — is M8 verification item §8.4 R4, and the pinned lifecycle is corrected to match. **Pressed-while-active:** while the ringed key is being pressed, the pressed fill (`disguise/keyOpPressed`) takes over as the fill for the press duration; the ring persists after release for as long as the operator remains pending.
   **Disguise-safety resolution (the tension, resolved explicitly):** the ring is driven *solely* by the calculator engine's arithmetic state, which is byte-identical whether the user is doing math or covertly typing a symbol-containing passcode — a bystander watching the owner type `7 + 7 %` sees exactly what they'd see if anyone computed that expression. It leaks nothing about the recorder, the buffer, or verification. Conversely, *omitting* the indication would be a mild tell in itself: the pinned reference model (iOS Calculator basic mode) indicates the pending operator, and users notice its absence when chaining. The one residual consideration — an onlooker could partially reconstruct which operators the owner pressed by watching rings — is identical to their ability to watch fingers on any keypad and is an accepted shoulder-surfing risk, not a disguise break. And no ring state ever survives a lock: **locking recreates a pristine calculator** (idea-plan §2.5 — every lock transition clears the calculator display state and attempt buffer), which iteration 1 already pins. **Ship the ring.**

---

## 5. Interaction & motion

Principle: every animation on this screen must be something a calculator would do. Nothing eases, springs, or celebrates in a way that says "app with a secret."

### 5.1 Key press feedback & activation

- **Down:** fill switches to the pressed token **instantly** (0ms — any ramp-in reads as lag).
- **Up:** fill fades back to rest over **180ms, ease-out**. No scale transform, no shadow change (scale on 19 keys risks dropped frames on low-end hardware and adds nothing).
- **Activation: the key action fires on touch-down**, synchronously with the visual press — this is also what makes the ≤50ms key-response budget of idea-plan §6 easy to meet. The belief that the reference calculator registers on press rather than release is **a verify-on-device item, not an asserted fact** (M8 item §8.4 R3); if the reference registers on release, this spec's activation rule is revisited against the response-budget rationale, and the outcome recorded.
- **Consequence, stated explicitly: touch-down firing removes slide-off-to-cancel.** A finger that lands on a key has already fired it; dragging off before lifting cancels nothing. Whether this matches or diverges from the reference is part of the same M8 verification item (R3) — record it as a divergence-or-match.
- **Assistive-technology activation is normative:** a VoiceOver/TalkBack synthesized activate (the screen-reader double-tap) **must fire the key identically** to a touch-down — same action, same haptic path, same single firing. Keys are therefore **real accessible buttons** (platform button components with button role/trait), never inert gesture surfaces that only a raw touch can reach. A screen-reader user who knows the code must be able to unlock (idea-plan §6, DoD 24). This requirement is on the §8.3 audit list (item 22).
- Rapid typing: each press restarts its own key's fade; overlapping fades on different keys are independent.

### 5.2 Haptics — identical for every key, always

- **One haptic, every key, every mode, every outcome.** `AC`, digits, operators, and `=` produce the *same* haptic whether the commit matches, doesn't match, is sub-minimum, or overflows — a differential haptic is a tell exactly as a differential animation is. Keys pressed beyond the §4.2 display cap produce the same haptic too.
- **iOS:** `UIImpactFeedbackGenerator(style: .light)`, `impactOccurred(intensity: 0.7)`, generator kept prepared. **Android:** `View.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)` (respects the user's system haptics setting, as a real calculator would).
- Fired on touch-down, synchronously with the visual press. **No haptic on unlock** — the vault appearing is the only feedback (a success buzz is a tell in pocket-adjacent situations and a celebratory flourish besides).

### 5.3 Key repeat

**None.** Press-and-hold on any key (including `AC` and backspace-less digits) does exactly one input and holds the pressed visual until release. No long-press menus, no hidden gestures anywhere on the keypad or display (long-press on the display also does nothing in iteration 1 — no copy affordance; accepted minimalism, noted in §8's tell audit).

### 5.4 Unlock transition

- When async verification succeeds (idea-plan §2.2: off the UI path), the vault replaces the calculator with a **plain crossfade, 150ms, ease-in-out**. No zoom, no slide, no spring, no sound.
- **Budget definition (measurement point pinned):** the idea-plan §6 "≤ 300 ms perceived" unlock budget is measured **from the `=` press to the FIRST crossfade frame** — "vault visible" = fade start. The 150ms fade tail is excluded from the budget.
- **Honest arithmetic:** the KDF dominates the budget — PBKDF2 at 600k iterations is **~100–300 ms on a mid-range device** (ios-plan §3.4). First-frame latency = KDF (~100–300 ms) + scheduling the crossfade's first frame (≤ ~1 frame, ~17 ms at 60Hz). At the mid-range of the KDF estimate the budget is met with room; **at the KDF's upper bound the KDF alone consumes essentially the entire budget** and the first frame can land slightly past 300 ms. No compliance is claimed here that the numbers don't support: whether the budget is met is decided by the **measured** KDF on the DoD's mid-range reference device (idea-plan §8 criterion 4, measured to the first-frame definition above). If the measured KDF pushes the first frame past the budget on the reference device, that is a plan-level tension between idea-plan §6's budget and §2.2's pinned 600k iterations, to be resolved against the idea plan — not papered over by this document.
- Until the fade's first frame, the display shows the arithmetic result of the committed expression, pixel-identical to a non-match (pinned requirement).
- The reverse transition (lock) is **instant** — a cut, not a fade: locking is fail-closed and must never show vault pixels a frame longer than necessary (and on iOS the snapshot cover per ios-plan §2.3 is already in place before any transition would render).

### 5.5 Setup banner (caption strip)

- **Placement:** thin strip at the top of the display region (§2.3): full content width, **28pt/dp min height** (grows to fit two lines of 13pt caption text), text centered, background transparent (`disguise/bg` shows through).
- **Show/hide:** the strip exists for the whole duration of a capture mode (CaptureNew / ConfirmNew / VerifyCurrent). It appears with a **150ms fade + 4pt downward slide** when the mode is entered and reverses on exit. Within a mode, text changes (e.g. entry banner → too-short message → entry banner) crossfade in place over **120ms** with no layout shift.
- **Two-tier text:** the primary line carries the state banner (§6 copy table); an optional second line at the same caption size carries the strength/collision hint or the trivial-sequence warning (shown on entry to SETUP_CONFIRM, per idea-plan §2.3). Hints never blink, pulse, or use color other than `disguise/caption`.
- The strip is never rendered in Disguise mode — not hidden, not transparent: not composed (matching the platform plans' rule that nothing setup-related exists on the pure lock screen).

### 5.6 VerifyCurrent error feedback (inside Settings only)

Per idea-plan §2.7, wrong-current-code feedback is *visible* here — silence is a disguise feature only on the lock screen.

- **Failure rule, pinned: ANY commit that is not the exact current code shows `verify_error`** ("Incorrect code — try again") — including sub-4-key commits and overflowed (>32-key) commits. There is no separate too-short/too-long messaging in VerifyCurrent (those strings belong to *setting* a code, not proving one); visible feedback is allowed here, and a uniform "incorrect" is both simpler and leaks nothing about the stored code's length. Buffer cleared, stay in VERIFY_CURRENT, unlimited retries (idea-plan §2.7).
- **Shake:** the display readout (not the whole screen) translates horizontally **±8pt, 3 cycles, 300ms total**, ease-in-out, on any failed VerifyCurrent commit. Respect the platform reduce-motion setting: with reduce-motion on, skip the shake and rely on the caption change alone.
- **Caption:** the strip switches to `verify_error` copy in `disguise/captionError`, then reverts to the standard VerifyCurrent caption on the next key press.
- The Settings-hosted calculator (Change passcode flow) carries a navigation bar with title `settings_change_title` and a **Cancel** button visible in every phase (ios-plan §2.4; android-plan §4.5). The keypad, tokens, metrics, haptics, and motion are otherwise identical to the lock screen — same component, different mode, per the platform plans' single-screen/mode-flag architecture.

### 5.7 One-time no-recovery notice

- Shown once, immediately after a successful first-run confirm, **as a platform-native modal alert over the just-revealed vault** (idea-plan §2.3: store passcode → UNLOCKED → notice). Native alert styling (SwiftUI `alert` / Material 3 `AlertDialog`) — deliberately not a custom-designed sheet: a system dialog reads as serious and final, which is the point.
- Title, body, and single confirming button per the copy table (§6). Modal and blocking; no "don't show again" checkbox (it already shows only once); dismissal requires the explicit button tap.
- The same body text is restated in Settings → About (idea-plan §2.6) — About screen design is out of this document's scope (it lives inside the vault).

---

## 6. Copy table

Consolidated user-visible strings for the lock screen and passcode flows. String IDs are shared verbatim by both platforms (iOS string catalog keys ≡ Android `strings.xml` names). Copy is pulled from idea-plan §2.3/§2.6/§2.7 verbatim where the idea plan pins it; where the idea plan describes intent without exact words, this table pins the words. Where a platform plan paraphrased ("Enter your current passcode…", "Sequences didn't match" in ios-plan §2.4), **this table supersedes the paraphrase** — the idea plan's "code" vocabulary wins, and no lock-screen string ever contains "passcode", "vault", "unlock", or "SafeBox".

| String ID | Where | Text |
|---|---|---|
| `setup_entry_banner` | SETUP_ENTRY, primary line | `Set your secret code: type it on the keypad, then press =` |
| `setup_entry_hint` | SETUP_ENTRY, second line | `Best: 6+ keys with a symbol (+ − × ÷ % ± .), and not a sum someone might really type.` |
| `setup_too_short` | SETUP_ENTRY after short commit | `Too short — use at least 4 keys` |
| `setup_too_long` | SETUP_ENTRY after overflow commit | `Too long — start again (max 32 keys)` |
| `setup_confirm_banner` | SETUP_CONFIRM, primary line | `Re-enter the same code, then press =` |
| `setup_mismatch` | back in SETUP_ENTRY after mismatch | `Codes didn't match — start again` |
| `setup_trivial_warning` | SETUP_CONFIRM, second line, shown on entry to SETUP_CONFIRM (soft, never blocks) | `Easy to guess — re-enter it to keep it anyway, or enter a different code and press = to start over.` |
| `setup_no_recovery_title` | one-time notice title | `Remember your code` |
| `setup_no_recovery_body` | one-time notice body | `There is no way to recover this code. If you forget it, your vault contents cannot be retrieved.` |
| `setup_no_recovery_button` | one-time notice button | `I understand` |
| `verify_current_caption` | VerifyCurrent (Settings) | `Enter your current code, then press =` |
| `verify_error` | VerifyCurrent after ANY non-matching commit (wrong, sub-4-key, or overflowed — §5.6) | `Incorrect code — try again` |
| `change_enter_new_caption` | ENTER_NEW (Settings) | `Enter your new code, then press =` |
| `change_confirm_caption` | CONFIRM_NEW (Settings) | `Re-enter the new code, then press =` |
| `change_success` | Settings confirmation after change | `Code changed` |
| `change_cancel` | Cancel button, all change-flow phases | `Cancel` |
| `settings_change_title` | Change-flow navigation title | `Change passcode` |
| `key_label_all_clear` / `key_label_clear` | AC/C key visible label | `AC` / `C` |

Rules: the `setup_no_recovery_body` string is verbatim from idea-plan §2.6 and must not be edited independently. The `setup_trivial_warning` copy is written for where it renders — SETUP_CONFIRM, where `AC` only clears the confirm buffer and stays in confirm (idea-plan §2.3): re-entering the warned code keeps it; committing a *different* sequence takes the mismatch path back to SETUP_ENTRY, which is the "start over" the copy describes. `ENTER_NEW`/`CONFIRM_NEW` reuse `setup_too_short`, `setup_too_long`, `setup_mismatch`, `setup_trivial_warning`, and `setup_entry_hint` unchanged (idea-plan §2.7: same rules as SETUP_ENTRY). `verify_error` is the sole failure string in VerifyCurrent and covers sub-minimum and overflowed commits too (§5.6). The word "vault" appears in exactly one place — inside the no-recovery notice, which renders only after the user is already inside the vault; it never renders on a locked screen. The pure LOCKED state has **zero strings** beyond key labels and the numeric display.

---

## 7. Accessibility spec

Restating the pinned tension (idea-plan §6) operationally: **the lock screen is fully accessible *as a calculator*, and its accessibility tree must be indistinguishable from a real calculator's.** A screen-reader user who knows the code can unlock (keys are labeled, `=` commits, and synthesized activation fires keys identically per §5.1); assistive tooling must never be able to discover that unlocking exists.

### 7.1 Per-key labels (plain calculator vocabulary only)

| Key | Accessibility label |
|---|---|
| `D0…D9` | `zero` … `nine` |
| `DOT` | `decimal point` |
| `ADD` / `SUB` | `plus` / `minus` |
| `MUL` / `DIV` | `multiply` / `divide` |
| `PCT` | `percent` |
| `SIGN` | `plus minus` |
| `CLEAR` | `all clear` when showing AC, `clear` when showing C |
| `EQUALS` | `equals` |

- All keys are **real buttons** with plain button traits/roles (§5.1) — never inert gesture surfaces. A VoiceOver/TalkBack activate fires the key exactly like a touch. **No accessibility hints on any key**, no custom actions, no accessibility identifiers containing `passcode`, `vault`, `unlock`, `secret`, `lock`, or `safebox` — identifiers use calculator terms (`calc_key_d7`, `calc_display`). This applies to *test* identifiers too, since they ship in the binary.
- No state announcement fires on commit, match, or non-match. The only announced changes are the display value and (in setup/Settings modes) the caption text.

### 7.2 Display

- The readout is one accessibility element, label `result` (localized later with the iteration-2 localization work), value = the displayed string read naturally (`negative zero`, `error`, `one point two three e twelve` per platform number-reading defaults). It announces value changes politely (non-interruptive live region / `UIAccessibility` value change), exactly as a calculator display should. A key pressed beyond the §4.2 entry cap produces no value-change announcement — because the value did not change; this is identical to how a real calculator's display reads and is itself part of the zero-tell posture.
- The caption strip, when present, is a static-text element read in order before the display.

### 7.3 Dynamic type within the fixed keypad

- **Keys clamp:** key glyph sizes (§3.3) scale with the system text-size setting up to **1.15×** and clamp there — the grid geometry is fixed (a keypad that reflows destroys both usability and the calculator gestalt). Key hit targets already exceed minimums independently of text size.
- **Display scales:** the readout base size follows the width-fit rule (§3.3), not text settings. Its auto-shrink floor is the derived **0.70×** (§3.3) at all text sizes — the derivation shows the 16-character worst case fits at 0.75×, so the floor already exceeds the larger-minimum consideration for low-vision users; no separate accessibility floor is needed.
- **Caption follows Dynamic Type** up to the XL step, then clamps (it may wrap to its second line).

### 7.4 Contrast & verification

All token pairs meet the §3.2 thresholds in both themes; the believability review (§8) includes re-running a contrast checker over the final implemented values on-device (true rendering, not design-file values), including the `keyOpActiveRing`-on-`keyOp` non-text pair. Increased-contrast / high-contrast OS settings: acceptable to inherit system behavior; no custom high-contrast palette in iteration 1.

### 7.5 What we deliberately do not do

No unlock-specific assistive affordance of any kind (no magic rotor action, no hidden accessibility element). Consequence, accepted and documented in the idea plan: symbol-heavy passcodes are harder for some assistive-tech users; digits-only codes remain fully supported for this reason.

---

## 8. Believability checklist (testable)

Run on physical devices, both platforms, dark and light themes, before any iteration-1 sign-off. Every item is pass/fail.

### 8.1 Casual-use gauntlet (hand the phone to someone; it must behave)

1. All **17 rows of the idea-plan §2.1 shared input→display table** reproduce exactly, typed by hand at natural speed — including the degenerate all-symbol rows 14–16 and the `±`/`-0` rows 12–13.
2. `2 + 2 =` → `4`; chained `2 + 3 × 4 =` → `20` (immediate execution, no precedence); operator replacement `2 + × 3 =` → `6` with the ring hopping from `+` to `×`.
3. Percent both ways: `7 %` → `0.07`; `8 × 50 % =` → `4`; `7 + 7 % =` → `7.49`.
4. Decimal edges: `. 5 + . 5 =` → `1`; double `.` ignored; `0.1 + 0.2 =` → `0.3`.
5. `8 ÷ 0 =` → `Error`, plain styling; next digit recovers; `AC` recovers.
6. Repeated `=`: `2 + 3 = =` → `8`; `2 × =` → `4`.
7. **AC/C label** behaves per the R2-verified rule (§4.8): `AC` cleared, `C` during entry, `C` clears entry only, then `AC` clears all — checked against the reference device's verified behavior, not this spec's provisional wording.
8. Thousands separators appear at 4+ integer digits; 9-significant-digit rounding; scientific format per §4.6 — `999999999 × 9 =` renders `8.99999999e9` (never `9e9`).
9. **Entry cap (§4.2):** the 10th digit of an operand produces the identical press visual and haptic with no display change; typing a 32-key digits-only passcode feels completely normal while the display sits at its cap, and the code still unlocks (the recorder captured every key).
10. Key response feels instant (≤50ms budget); no key ever repeats on hold; rapid two-thumb typing drops no presses.
11. Committing any wrong/short/overflowed sequence with `=` produces the arithmetic result with **zero** observable difference — timed side-by-side against a correct-code commit on a second device if available (display path must be indistinguishable until the crossfade).

### 8.2 Visual gauntlet

12. App switcher / recents after backgrounding from every vault tab shows the calculator cover face (iOS, per ios-plan §2.3) or a blank card (Android FLAG_SECURE, per android-plan §8.2 — the blank card is the accepted, documented divergence). The iOS cover face uses this spec's tokens and is pixel-consistent with the live locked screen.
13. Home-screen icon, launch screen, and first rendered frame are consistent: launch screen is plain `disguise/bg` (no logo flash), and the transition from launch to calculator is seamless.
14. Both themes render correctly; switching system theme while locked updates the calculator like any normal app.
15. Small-screen device (320pt-width class) and one large phone verified against §2 metrics — keypad-block share inside the 62–68% band where §2.2 says it must be; tablet shows the centered bounded column.
16. Side-by-side with Apple Calculator (on iOS) and Google Calculator (on Android): a reviewer confirms "clearly a calculator, clearly **not** that calculator" — shape, palette, and background all visibly distinct (anti-goals §1).

### 8.3 Tell audit (verify ABSENT / verify identical)

17. No lock, shield, eye, key, or safe iconography anywhere in shipped assets (audit the asset catalogs, not just screens).
18. No string from the forbidden vocabulary (`vault`, `unlock`, `passcode`, `secret`, `safebox`, `hidden`) reachable while LOCKED; setup strings appear only in setup/Settings modes; the sole "vault" occurrence is the post-unlock no-recovery notice (§6).
19. No spinner, progress, delay, flash, or haptic difference on `=` for any commit outcome; no attempt counter, no cooldown, no error toast, ever, while locked.
20. Haptic and press animation identical across all 19 keys and all modes — including keys pressed beyond the §4.2 entry cap.
21. No hidden gestures respond on the locked screen: long-press display, long-press keys, swipes, two-finger taps, shake — all inert (long-press-copy on the display is deliberately absent; accepted minimalism). Slide-off behavior is identical on every key and matches the R3-recorded posture (§5.1).
22. Accessibility tree audited with VoiceOver/TalkBack: labels per §7.1, no hints, no revealing identifiers, no announcement on commit — **and synthesized activation fires every key identically to a touch (§5.1): a screen-reader user can type and commit a code end-to-end**, with no double-firing and no inert key.
23. Banner strip is never composed in Disguise mode (verify via view/semantics tree, not just visually).
24. No log line related to keys, buffers, or lock state (no-logging rule, idea-plan §6 — audited at review, restated here because logs are a tell to forensic bystanders).

### 8.4 Reference-verification items (M8)

Executed on a physical reference device (iOS Calculator basic mode, per the idea-plan §2.1 reference model) during each platform's M8 hardening pass; each item's outcome is recorded, and this spec's provisional pin is corrected to match where marked:

- **R1 — Entry cap (§4.2):** exact digit-entry cap behavior — cap count, whether leading zeros count toward it, `.`-at-cap handling.
- **R2 — AC/C label (§4.8):** label state machine, especially the after-`=` state while a committed result shows.
- **R3 — Activation (§5.1):** press-vs-release registration, and whether slide-off-to-cancel exists; record match-or-divergence for our touch-down rule.
- **R4 — Pending-ring lifecycle (§4.9):** whether the reference's indicator clears when second-operand entry begins or persists until commit; operator-replacement hop.
- **R5 — Scientific presentation (§4.6):** exact small-magnitude trigger threshold and mantissa digit count.

---

## 9. Mockup artboard list

The visual mockups are produced separately from this spec. They are **directional** — exploration and stakeholder communication — while this document remains the authority; any conflict resolves to this spec (or to a deliberate spec amendment, never a silent mockup override). Each artboard below names the states and tokens it must demonstrate. Produce phone-size (390pt-class) frames unless noted. **Theme coverage:** artboards 1+2 are the default-state light/dark pair; in addition, artboards **3 (mid-calculation/ring), 4 (pressed-state), and 5 (setup ENTRY)** must each be produced in **both light and dark** variants — these are the states where theme parity is most likely to break (ring on operator fill, pressed fills, caption contrast). Other artboards may be single-theme (dark default) unless a finding during review says otherwise.

1. **Lock screen — dark (default).** Disguise mode, display `0`, `AC` label. Demonstrates: full token palette dark, §2 grid metrics, display/keypad band, safe-area behavior. This is the canonical artboard.
2. **Lock screen — light.** Same state, light tokens. Demonstrates: light palette, contrast parity.
3. **Mid-calculation frame (both themes).** Display `1,234.5`, pending `+` with active-operator ring (outline only, §4.9), `C` label showing. Demonstrates: separators, tabular digits, theme-symmetric ring treatment, AC→C swap.
4. **Pressed-state frame (both themes).** One digit key and one operator key in pressed fill (captured mid-press); include a pressed-while-ringed operator variant (§4.9). Demonstrates: all three pressed tokens, flat elevation, ring-under-press behavior.
5. **Setup — ENTRY (both themes).** Caption strip with `setup_entry_banner` + `setup_entry_hint` second line; plus **two banner-variant frames: `setup_too_short` and `setup_too_long`** shown after a rejected commit. Demonstrates: strip layout, caption type, two-tier text, rejection copy in place.
6. **Setup — CONFIRM.** Strip with `setup_confirm_banner`; include the `setup_trivial_warning` second-line variant (shown on entry to SETUP_CONFIRM). Demonstrates: in-place caption change, warning tone (still `disguise/caption`, no red).
7. **Setup — mismatch.** Back in ENTRY with `setup_mismatch`. Demonstrates: rejection copy without alarm styling.
8. **No-recovery notice.** Native alert over the vault's first frame (vault may be a gray placeholder). Demonstrates: §5.7 presentation and §6 copy.
9. **VerifyCurrent in Settings.** Calculator hosted under a nav bar (`settings_change_title`, Cancel), strip showing `verify_current_caption`; a second variant frame with `verify_error` in `disguise/captionError` and shake mid-offset indicated — noting that this same treatment covers sub-minimum and overflowed commits (§5.6). Demonstrates: Settings-hosted chrome, the only red on any calculator surface.
10. **App icon concept.** Neutral, original calculator glyph on `disguise/bg`-family field, per idea-plan §1 — explicitly not resembling Apple's or Google's calculator icons; shown at home-screen and settings sizes on both platforms.
11. **Small-screen frame (320pt class)** and **tablet frame (centered 480 column).** Demonstrates: §2.5/§2.6 rules, including the in-band keypad share on the small screen and the band-exempt bounded column on the tablet.
12. **Display extremes strip.** A row of display crops: `-0`, `Error`, `-888,888,888` (12-character base-fit reference), `8.99999999e9`, `-9.99999999e-308` at the 0.70× floor, and a capped entry sitting at 9 digits. Demonstrates: §4 formatting, the §3.3 derivation, and the auto-shrink floor.

---

## 10. Platform implementation notes

Short mapping only — engine, recorder, verification, lock coordination, and mode plumbing are specified in the platform plans and are not restated here (ios-plan §2.3–§2.4, §3.4, §4.1; android-plan §2.3, §3.4, §4.1).

### iOS (SwiftUI — slots into ios-plan §4.1 `CalculatorScreen` / `CalcButton`)

- **Grid: use a manual `Grid` (or nested `VStack`/`HStack`), not `LazyVGrid`.** Nineteen fixed keys need no laziness; `Grid` gives `gridCellColumns(2)` for the double-width zero with exact column alignment, which `LazyVGrid` makes awkward. This matches ios-plan §4.1's `Grid` choice.
- Derive `m`, `g`, `g_v`, `k`, `h` from `GeometryReader` at the screen root per §2.2 (height-band driver, aspect ceiling, aspect rule); don't hardcode.
- **Display auto-shrink:** `Text(display).font(.system(size: base, weight: .regular)).monospacedDigit().lineLimit(1).minimumScaleFactor(0.7)` — the 0.7 floor is derived in §3.3 and applies at all Dynamic Type sizes (§7.3).
- **Keys are real `Button`s** with a custom `ButtonStyle` reading `configuration.isPressed`: instant fill swap on press, 180ms ease-out on release, and the key action fired on the press-begin edge of `isPressed` (touch-down activation, §5.1) — while the `Button` remains a genuine accessible button so VoiceOver's synthesized activate fires the same action exactly once (guard against double-fire: a pointer-initiated press marks the activation consumed so the button's action closure from the same gesture is a no-op; an AT activation takes the action closure path directly). Haptics per §5.2 with a prepared `UIImpactFeedbackGenerator`.
- The `CalculatorCoverView` (ios-plan §2.3 snapshot cover) must render from these same tokens/metrics so the app-switcher face matches the live screen (checklist item 12).
- Portrait lock via supported-orientations (phone); iPad follows §2.6/§2.7.

### Android (Jetpack Compose — slots into android-plan §4.1 `CalculatorScreen` / theme `CalculatorTheme.kt`)

- **Grid: `Column` of `Row`s with explicit sizes, measured via `BoxWithConstraints`.** Compute `k`, `h`, `g_v` per §2.2 and size cells explicitly rather than relying purely on `Modifier.weight` — with `Arrangement.spacedBy(g)`, a `weight(2f)` zero cell comes out `g/2` narrower than the required `2k + g`, misaligning the `.` and `=` columns. Give the zero key an explicit `width(2k + g)` (or equivalent weight+spacer arithmetic) so columns align exactly.
- **Display auto-size:** prefer `BasicText` with `autoSize` (Compose text auto-sizing, min = 0.7× base per §3.3, single line); on an older Compose BOM, fall back to a measure-and-scale approach (binary-search the font size against the measured width). Apply `fontFeatureSettings = "tnum"` for tabular digits.
- Tokens live in `CalculatorTheme.kt` (android-plan file tree) as a dedicated disguise color set — deliberately *not* Material dynamic color: the calculator's palette is fixed by §3.1 and must not re-tint per user wallpaper (dynamic color would also diverge from iOS and from the mockups).
- **Keys keep real button semantics** (`clickable`/`Button` with `Role.Button`): pressed fill driven from `InteractionSource` (instant on, 180ms `animateColorAsState` release); the key input fires on the press interaction (touch-down, §5.1) while `onClick` remains the assistive-technology activation path firing the identical action — guard against double-fire (a pointer-driven press marks the event consumed so the `onClick` from the same gesture is a no-op; a TalkBack activation arrives as `onClick` alone and fires the key). Haptics via `performHapticFeedback(KEYBOARD_TAP)`.
- Portrait via `android:screenOrientation="portrait"` on the single activity — noting that **large screens and some foldables may ignore or letterbox this request**; where the OS does not honor it, §2.6's bounded-centered-column rule is the required rendering in the resulting orientation (§2.7). FLAG_SECURE stays unconditional per android-plan §8.2 — this spec adds nothing to that mechanism.

### Both

- All strings from §6 land under the shared string IDs; the copy table is the single source — platform paraphrases in earlier plan sketches are superseded (§6 preamble).
- The §4 display-formatting rules (entry cap, separators, scientific format) land via the **coordinated amendment** (§4 preamble): the platform plans' engine/formatting sections and their test tables adopt them at implementation time, together with the §8.4 reference-verification outcomes.
- The believability checklist (§8) — including the §8.4 reference-verification items — is executed as part of each platform's M8 hardening pass (ios-plan §6 M8, android-plan §6 M8) alongside the existing DoD items; it adds design acceptance to, and never replaces, idea-plan §8.
