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

## Repository layout

```
docs/plans/   planning documents (this iteration's deliverable)
ios/          native iOS app (SwiftUI) — to be built per the iOS plan
android/      native Android app (Jetpack Compose) — to be built per the Android plan
```
