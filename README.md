# SafeBox

A privacy vault app for iOS and Android that hides in plain sight. On launch it presents a fully working calculator; entering a secret sequence of calculator keys unlocks a local, offline vault with four tabs: **Gallery** (albums → photo grid → photo detail), **Notes** (markdown notes with tags, Apple Notes-style), **Contacts**, and **Settings**.

Everything is local and offline — no accounts, no backend, no analytics.

## Status

Planning. Iteration 1 (native skeleton with full local persistence) is specified in three documents:

| Document | Purpose |
| --- | --- |
| [Idea plan](docs/plans/idea-plan.md) | Platform-neutral product plan — source of truth for behavior, the calculator-lock state machines, and the domain model |
| [iOS plan](docs/plans/ios-plan.md) | Swift / SwiftUI / SwiftData implementation plan for the app under `ios/` |
| [Android plan](docs/plans/android-plan.md) | Kotlin / Jetpack Compose / Room implementation plan for the app under `android/` |
| [Disguise skeleton plan](docs/plans/disguise-skeleton-plan.md) | Design of the pluggable disguise abstraction (design-only; the seam lands in iteration 2 with the calculator as disguise #1) |
| [Calculator disguise design](docs/plans/calculator-disguise-design.md) | Design authority for the iteration-1 calculator lock screen: layout, visual system, interaction, motion, copy, accessibility, believability |

## Repository layout

```
docs/plans/   planning documents (this iteration's deliverable)
ios/          native iOS app (SwiftUI) — to be built per the iOS plan
android/      native Android app (Jetpack Compose) — to be built per the Android plan
```
