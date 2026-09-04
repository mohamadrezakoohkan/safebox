# SafeBox

A privacy vault app for iOS and Android that hides in plain sight. On launch it presents a **lock face** — a fully working calculator by default, or a PIN pad or pattern lock if you prefer; entering your secret code on that face unlocks a local, offline vault with four tabs: **Gallery** (albums → photo grid → photo detail), **Notes** (markdown notes with tags, Apple Notes-style), **Contacts**, and **Settings**.

Everything is local and offline — no accounts, no backend, no analytics.

## Lock faces

The face is chosen during onboarding and can be changed later in Settings, which re-enters the current code and then enrols a new one on the new face (the passcode hash is bound to the face's own key alphabet, so a switch always means a new code).

| Face | Appears as | Entry | Wrong code |
| --- | --- | --- | --- |
| Calculator | Calculator+ | Type keys, press `=` | Silent — it just calculates |
| PIN pad | Notepad+ | Type digits, tap `✓` | Shakes and clears |
| Pattern | Gallery+ | Connect 4–9 dots, lift your finger | Shakes and clears |

The home-screen icon follows the face, so the two agree: a locked notes app and a locked gallery are ordinary things to find on a phone, and a PIN pad or a pattern is exactly how they lock. Android changes the app's name to match as well; iOS changes only the icon, because it does not let an app rename itself, and it shows a system alert when the icon changes. The picker states both plainly. What can never change is the underlying identifier, so a phone's app-info screen still lists the app as Calculator+.

## Status

Iterations 1, 2 and 3 have shipped on both platforms. The planning documents:

| Document | Purpose |
| --- | --- |
| [Idea plan](docs/plans/idea-plan.md) | Platform-neutral product plan — source of truth for behavior, the lock state machines, and the domain model |
| [iOS plan](docs/plans/ios-plan.md) | Swift / SwiftUI / SwiftData implementation plan for the app under `ios/` |
| [Android plan](docs/plans/android-plan.md) | Kotlin / Jetpack Compose / Room implementation plan for the app under `android/` |
| [Disguise skeleton plan](docs/plans/disguise-skeleton-plan.md) | Design rationale for the pluggable disguise contract; built in iteration 3, with amendments |
| [Iteration 3 decisions](docs/plans/iteration-3-decisions.md) | **Binding spec for the lock faces**: the disguise contract, the PIN pad and pattern specs, passcode envelope v2, the switch flow, and the shared string table |
| [Calculator disguise design](docs/plans/calculator-disguise-design.md) | Design authority for the calculator face: layout, visual system, interaction, motion, copy, accessibility, believability |

## Repository layout

```
docs/plans/   planning documents (this iteration's deliverable)
ios/          native iOS app (SwiftUI) — to be built per the iOS plan
android/      native Android app (Jetpack Compose) — to be built per the Android plan
```
