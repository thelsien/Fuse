# Fuse

A 2048-style merge-puzzle game for mobile. Win on polish, a daily habit, and a
shareable result — not on the (un-ownable) core mechanic.

## Stack

- **Kotlin Multiplatform + Compose Multiplatform** — game logic and UI shared in
  `commonMain`; only platform services use `expect/actual`.
- **Targets:** Android + iOS.
- **Backend:** none in v1.0 (local-only). Daily Challenge is client-seeded from
  the UTC date.
- **Architecture:** Clean Architecture + MVI; pure, replayable engine.

See the source docs in this repo's planning files:
- Product requirements (PRD)
- Implementation plan (MVP, sprint backlog with story IDs)
- [`docs/design-tokens.md`](docs/design-tokens.md) — colors, type, motion, tile
  ramp extracted from the interactive prototype.

## Current status

**Pre-scaffold.** Repo initialized; design tokens extracted from the prototype.

The first story — **`FND-1` KMP project scaffold** (`:shared`, `:androidApp`,
`:iosApp` launching a blank Compose MP screen on both platforms) — is **paused
pending Xcode installation**, so every story can be verified on Android *and*
iOS as the implementation plan's Definition of Done requires.

### Toolchain

| Tool | Status |
|---|---|
| JDK | ✅ installed |
| Android SDK | ✅ installed |
| **Xcode** | ❌ not installed (only Command Line Tools) — **required before FND-1** |

To unblock: install Xcode from the App Store, then run
`sudo xcode-select -s /Applications/Xcode.app/Contents/Developer` and accept the
license (`sudo xcodebuild -license accept`).

## Build process

All coding is driven through the **coding-orchestrator**, fed the current state
of the app each time so it does not start from scratch. Stories are implemented,
tested, reviewed, and merged one vertical slice at a time, in the backlog order
defined by the implementation plan.
