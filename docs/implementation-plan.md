# Implementation Plan — *Fuse* MVP (v1.0)

Agile build plan · Kotlin Multiplatform + Compose Multiplatform · solo developer · local-only

| | |
|---|---|
| **Status** | Draft v0.1 |
| **Scope** | MVP (v1.0) only |
| **Stack** | KMP + Compose Multiplatform (Android + iOS) |
| **Backend** | None in v1.0 (local-only) |
| **Monetization** | Ads + Remove-Ads IAP, fully integrated |
| **Last updated** | 2026-06-19 |

---

## 1. Decisions & assumptions (locked)

| Decision | Choice | Consequence for the build |
|---|---|---|
| Platform | KMP + Compose Multiplatform | Game logic + UI shared in `commonMain`; only platform services use `expect/actual` |
| Team | Solo | Stories sized small (mostly 1–3 pts); sequential sprints; one vertical slice at a time |
| Scope | MVP v1.0 | Powerups, gems, board variants, leaderboards, season pass, modes beyond Classic+Daily are **out** |
| Backend | Local-only | No cloud save, no remote config, no server validation in v1.0 — all deferred to fast-follow |
| Daily Challenge | Seeded **no-spawn puzzle**, client-derived from the UTC date | Deterministic; no server. Same seed → same start board for everyone on a given UTC day; **reach a per-day target tile in the fewest moves** (no random spawns). Solver computes "par". One-shot until solved; free undo/restart; own save slot. **Reworked from the original "endless seeded run" — see Sprint 5.** |
| Economy | Single soft currency (Coins) | Earned by play/daily/streaks/rewarded ads; spent on cosmetics. No gems, no powerups |
| Ads | AdMob (or mediation) via `expect/actual` | Rewarded (revive / coins / double-daily) + capped interstitial. **Riskiest native work** |
| IAP | Remove Ads (anchor) | Wrap Play Billing / StoreKit, or use RevenueCat to abstract both. Restore-purchases required |
| Rendering | Compose MP animation + Canvas | No game engine; "juice" comes from animation/haptics/sound tuning |
| Analytics | Client SDK only | Vendor SDK (e.g. Firebase Analytics via a KMP wrapper) — sends to vendor, needs no backend of ours |
| Architecture | Clean Architecture + MVI | Pure domain/engine, reactive state, testable everywhere |
| Process | 2-week sprints, trunk-based | Risky features behind flags; CI green on every PR |

**Deferred to fast-follow (explicitly not in this plan):** cloud save & account sign-in, remote config, gem/powerup economy, leaderboards, season pass, Blitz/Zen modes, board-size variants, live-ops events.

---

## 2. Architecture at a glance (KMP)

```
:shared  (commonMain — everything portable)
 ├─ engine/         Pure game logic. No Compose, no platform. 100% unit-tested.
 │                   Board, Tile, Move, MergeResolver, Spawner(RNG), GameOverEval, Scorer, SeededRng
 ├─ domain/         Entities + use cases (PlayMove, StartDaily, GrantDailyReward, …)
 ├─ data/           Repositories + local sources (SQLDelight, multiplatform-settings)
 ├─ presentation/   MVI: state holders / reducers / intents (GameStore, HomeStore, …)
 └─ ui/             Compose Multiplatform composables + design tokens (theme, colors, type, spacing)

 expect/actual platform services (thin):
   Haptics · SoundPlayer · AdProvider · BillingProvider · AnalyticsLogger · NotificationScheduler · Sharer

:androidApp   thin shell → provides Android actuals (Google Mobile Ads, Play Billing, …)
:iosApp       thin shell → provides iOS actuals (StoreKit, AVFoundation, UNUserNotificationCenter, …)
```

**Candidate libraries to evaluate (not prescriptions):** Koin (DI), SQLDelight (structured local data), multiplatform-settings (key-values), Decompose or Compose-Nav (navigation), RevenueCat (cross-platform IAP), GitLive Firebase wrapper (analytics/crashlytics). Pick during Sprint 0.

**The KMP-specific risk** is concentrated in the four `expect/actual` service boundaries that wrap native SDKs — ads, billing, analytics, notifications. Everything else (engine, domain, UI) is pure shared code and low-risk. The plan front-loads the pure work and isolates the native work into clearly-bounded later sprints.

---

## 3. Working agreements (solo-appropriate)

**Definition of Ready (story can start):** acceptance criteria are clear and testable; dependencies merged; needed design tokens exist; flag created if risky.

**Definition of Done (every story):** behaviour matches AC; unit and/or Compose UI tests added and green; CI passing; lint/format clean; builds and runs on **both** Android and iOS; risky paths behind a feature flag; no analytics regressions. *(Because DoD covers tests/CI/both-platforms globally, individual story AC below focus on behaviour, not boilerplate.)*

**Estimation:** Fibonacci points (1/2/3/5), relative not absolute. Solo velocity is unknown at start — calibrate after Sprints 1–2, then forecast the remaining backlog. Treat the sprint grouping below as **backlog ordering**, not fixed dates.

**Checkpoint discipline:** every story is independently verifiable. The engine sprint is pure logic (assert-level checkable). UI stories are Compose-UI-test or visually checkable. Native-service stories include a manual verification step on a real device.

---

## 4. Epic overview

| # | Epic | Goal | Risk |
|---|---|---|---|
| E0 | Foundation & tooling | Empty app runs on both platforms, CI green | Low |
| E1 | Game engine (headless) | Fully-tested pure game logic, no UI | Low |
| E2 | Board UI & input | Playable Classic board (**First Playable**) | Med |
| E3 | Game feel | Animations, haptics, sound, milestones | Med |
| E4 | App shell & settings | Home, nav, settings, resume-on-launch | Low |
| E5 | Daily Challenge | Seeded daily **puzzle** (no-spawn, fewest-moves) + streak + shareable result | Med |
| E6 | Retention economy | Coins, daily reward, streaks, notifications | Med |
| E7 | Cosmetics | Themes/skins + collection screen | Low |
| E8 | Ads | Rewarded + interstitial (native wrap) | **High** |
| E9 | IAP & analytics | Remove-ads + restore + event tracking | **High** |
| E10 | FTUE & onboarding | Frictionless first run, Daily hook | Low |
| E11 | A11y & localization | Colorblind, dynamic type, EN/HU | Low |
| E12 | Release readiness | Store assets, privacy, beta, soft-launch | Med |

---

## 5. Sprint backlog (stories with acceptance criteria)

> Story IDs are stable handles. Points in `code`. AC = acceptance criteria (the checkable bit).

### Sprint 0 — Foundation & tooling
*Goal: an empty KMP app launches on Android and iOS, CI is green, architecture skeleton in place.*

- **`FND-1` KMP project scaffold — `3`**
  AC: `:shared`, `:androidApp`, `:iosApp` build and launch a blank Compose MP screen on both platforms.
- **`FND-2` CI pipeline — `3`**
  AC: PR triggers build + lint + unit tests for Android and iOS targets; red build blocks merge.
- **`FND-3` DI + module skeleton — `2`**
  AC: Koin graph wires empty layers (engine/domain/data/presentation/ui); app resolves a sample dependency.
- **`FND-4` Design tokens & theme — `3`**
  AC: centralized color/type/spacing tokens; light/dark; a swatch preview screen renders from tokens (foundation for cosmetics + colorblind mode).
- **`FND-5` Test harness — `2`**
  AC: unit-test module runs in CI; one Compose UI test runs headless; coverage report generated.

### Sprint 1 — Game engine (pure, TDD)
*Goal: a complete, deterministic, fully unit-tested 2048 engine with zero UI. Highest-confidence sprint — every story is assert-checkable.*

- **`ENG-1` Board & tile model — `2`**
  AC: immutable 4×4 board; tile = value + stable id; equality + copy; serializable.
- **`ENG-2` Seeded RNG — `2`**
  AC: deterministic PRNG; same seed → same sequence across platforms; injectable.
- **`ENG-3` Line compaction — `3`**
  AC: pure function slides one line toward an edge, order preserved, gaps removed, no merging yet; idempotent on compact input.
- **`ENG-4` Merge resolution — `3`**
  AC: equal neighbours merge once per move, wall-side first; produces summed tile with new id; emits merge events for animation/scoring.
- **`ENG-5` Four-direction move — `3`**
  AC: up/down/left/right reuse compaction+merge; `move()` returns new board + whether anything changed + merges list.
- **`ENG-6` Spawn — `2`**
  AC: spawns one tile in a random empty cell, value 2 @ 90% / 4 @ 10% via injected RNG; only spawns when board changed.
- **`ENG-7` Scoring — `1`**
  AC: each merge adds resulting value to score; running + best tracked.
- **`ENG-8` Win / lose evaluation — `2`**
  AC: win flag on first 2048; lose only when board full **and** no move in any direction changes it.
- **`ENG-9` Game state machine — `2`**
  AC: states Playing → Won(canContinue) → Lost; `applyMove` is a pure transition; full game replayable from seed + move list (enables Daily + anti-cheat later).

### Sprint 2 — Board UI & input · **FIRST PLAYABLE**
*Goal: a real, playable Classic game you can swipe, lose, restart, and resume.*

- **`UIB-1` Board renderer — `3`**
  AC: Compose MP grid renders any engine board state; tile colors/numerals from tokens; recomposes on state change.
- **`UIB-2` Swipe input → direction — `3`**
  AC: drag/flick resolves to dominant axis direction; low distance threshold; debounced; ignores ambiguous diagonals.
- **`UIB-3` Engine ↔ UI binding (MVI) — `3`**
  AC: GameStore exposes UI state + accepts Move intents; swipe drives engine; invalid move is a visible no-op.
- **`UIB-4` Score & best display — `1`**
  AC: live score + best shown; best persists within session.
- **`UIB-5` Game-over & win overlays — `2`**
  AC: lose overlay (final score, restart); win overlay (celebrate, "keep going" / restart).
- **`UIB-6` Persist & resume current game — `3`**
  AC: in-progress board + score saved locally (SQLDelight/settings); relaunch restores exact state; best score persists across launches.

> ✅ **Milestone: First Playable** — Classic 2048 is fully playable and survives app restart. This is the earliest end-to-end checkpoint.

### Sprint 3 — Game feel
*Goal: it feels great to touch.*

- **`FEL-1` Tile slide animation — `3`**
  AC: tiles animate from old to new position (~80–120 ms ease-out); ids drive continuity; no flicker on spawn.
- **`FEL-2` Merge pop — `2`**
  AC: merged tile scale-bounces + brief glow; intensity scales with tier.
- **`FEL-3` Spawn animation — `1`**
  AC: new tile fades/scales in after movement settles.
- **`FEL-4` Haptics — `2`** *(expect/actual)*
  AC: light tick per merge, heavier on milestone, distinct buzz on invalid move; respects OS/setting toggle.
- **`FEL-5` Sound — `3`** *(expect/actual)*
  AC: per-merge tone rises in pitch with value; milestone + win stings; mute toggle.
- **`FEL-6` Milestone effects — `2`**
  AC: particle/flash at 512/1024/2048; honored by reduced-motion flag.
- **`FEL-7` Combo feedback — `1`**
  AC: multi-merge move shows combo count + escalating cue.
- **`FEL-8` Reduced-motion plumbing — `1`**
  AC: a single setting disables shake/particles/overshoot app-wide.

### Sprint 4 — App shell & settings
*Goal: a coherent app around the game.*

- **`SHL-1` Home screen — `3`**
  AC: entry points to Classic, Daily (placeholder ok), Settings; shows best score; thumb-zone layout.
- **`SHL-2` Navigation — `2`**
  AC: Compose MP nav between Home/Game/Settings; back handling correct on both platforms.
- **`SHL-3` Settings screen — `3`**
  AC: toggles for sound, haptics, reduced motion, colorblind mode; persisted; applied live.
- **`SHL-4` Resume-on-launch UX — `1`**
  AC: launching with a saved game offers Resume vs New.

### Sprint 5 — Daily Challenge ✅ *(DONE — as-built; reworked from the original design)*
*Goal: the daily habit + viral share loop.*

> **Design change (agreed with product, mid-sprint):** the Daily is **not** an endless seeded run scored by best-tile/score. It is a **deterministic puzzle**: every player gets the **same seed-derived START board** on a given UTC day and must **reach a per-day TARGET tile in the fewest moves**. There are **no random spawns** (only the preset tiles slide/merge), so the board is bounded and a **solver computes the optimal move count ("par")**. The shared start board is what makes results comparable/shareable (the Wordle insight, applied to a puzzle). This reframe is *more* differentiated from Classic than an endless run.
>
> **Rules as built:**
> - **No-spawn puzzle**, 4×4. Win = a tile reaches the day's target. The target **varies per day** (seed-derived, currently weighted over {32, 64, 128, 256}); difficulty is solver-banded (par 3–10).
> - **One-shot until solved:** **free undo (one move) and restart (to the start board)** while playing; the recorded result is the **move count of the run in which you reach the target**; once solved it locks until tomorrow.
> - **Single daily save slot** (NOT keyed per-date): holds the day it belongs to + the in-progress move list; **resets when a new UTC day starts**, **write-through after each move** so you resume exactly. Separate from the Classic save blob and from the streak record.
> - **UTC day boundary.** Local-only: trusts the device clock (server-side replay validation is fast-follow). The engine's seed+move-list replayability (`ENG-9`) keeps the door open for that.

Implemented as **DLY-1…DLY-7** (the solver and generator are genuinely new engine work, so each got its own story):

- **`DLY-1` Date → seed + day number — `2`**
  AC: pure `dateToSeed(LocalDate): Long` + `dailyDayNumber(date)` ("Daily #N"); same UTC day → same seed everywhere (golden cross-platform tests); tested across timezones/day boundaries. Adds kotlinx-datetime + an injectable `DailyClock`.
- **`DLY-2` No-spawn puzzle step + solver — `3`**
  AC: a no-spawn move step (`Board.puzzleStep`, slide+merge, no new tile); a BFS **solver** (`solve(board, target)`) that returns solvability + minimum moves (**par**) + an optimal path; value-canonical (ignores tile ids), bounded. Pure, fully unit-tested.
- **`DLY-3` Daily puzzle generator — `2`**
  AC: deterministic `generateDailyPuzzle(seed)` → a **solvable** start board + per-day target + par, within a difficulty band; same seed → same puzzle on every platform (golden); guaranteed solvable + terminating (sweeps: 100% solvable in band).
- **`DLY-4` Daily mode (play + single save slot) — `3`**
  AC: playable `DailyStore`/`DailyScreen` — renders the day's start board, no-spawn moves, move counter, win-on-target, **undo/restart**; the **single save slot** (new-day reset + write-through, resume mid-run, one-shot lock after solve); Home's Daily entry enabled + `DAILY` route. Emits a one-shot `Solved(dayNumber, moves)` signal.
- **`DLY-5` Completion + streak — `2`**
  AC: solving is recorded; **current + longest** daily streak maintained (pure `recordCompletion`/`liveCurrent`), persisted in a **separate** slot (`fuse.daily.streak`); rolls across the UTC boundary; a **missed day breaks** the streak; shown on the solved overlay + Home.
- **`DLY-6` Reset countdown — `1`**
  AC: Home shows a **live** countdown to the next Daily (next UTC midnight), ticking each second; pure duration/format helpers + a `now()` clock seam.
- **`DLY-7` Shareable result card — `3`** *(Sharer expect/actual)*
  AC: pure `buildDailyShareCard(...)` → an **emoji mini-grid** of the shared start board + a result line ("Fuse Daily #N · 🎯 target · solved in M moves (par P)") + optional streak; opens the native share sheet via a `Sharer` `expect/actual` (Android `ACTION_SEND` / iOS `UIActivityViewController`); **no PII**, copy-paste friendly, local-only (no share analytics).

> **As-built notes:** 511 tests green on Android + iOS Native at sprint close (`main` @ `de20386`). The whole daily pipeline (seed → generate → solve → play → streak → share) is deterministic and golden-tested across both platforms.

### Sprint 6 — Retention economy
*Goal: reasons to come back.*

- **`ECO-1` Coins currency — `2`**
  AC: balance persisted; earn/spend API; transactions auditable for tests.
- **`ECO-2` Coin earning rules — `2`**
  AC: coins from game milestones + daily completion; balanced, deterministic, tested.
- **`ECO-3` Daily login reward — `2`**
  AC: escalating 7-day cycle; once per day; resets on miss; claim UI.
- **`ECO-4` Streak rewards / saver — `2`**
  AC: milestone rewards at 3/7/14/30; one free streak-freeze per cycle.
- **`ECO-5` Stats screen — `1`**
  AC: shows best tile, games, win rate, current/longest streaks.
- **`ECO-6` Local notifications — `3`** *(NotificationScheduler expect/actual)*
  AC: schedules "Daily ready" + "streak about to break"; opt-in; frequency-capped; permission flow on both platforms.

### Sprint 7 — Cosmetics
*Goal: identity + a coin sink.*

- **`COS-1` Cosmetic model — `2`**
  AC: tile-skin + board-theme definitions; owned/equipped state persisted.
- **`COS-2` Theming application — `3`**
  AC: equipping a cosmetic restyles board live via tokens; default always available.
- **`COS-3` Collection / store screen — `3`**
  AC: browse cosmetics; unlock with coins; owned vs locked; equip.
- **`COS-4` Starter cosmetic set — `1`**
  AC: 3–4 ship-ready skins/themes wired end-to-end.

### Sprint 8 — Ads (native, highest risk)
*Goal: rewarded + interstitial revenue. Spike first.*

- **`ADS-0` Ad SDK spike — `3`**
  AC: AdMob (or mediation) test ad shows on both platforms via a minimal `AdProvider` `expect/actual`; documents integration gotchas.
- **`ADS-1` AdProvider abstraction — `2`**
  AC: shared interface (load/show/result) hides platform SDKs; testable with a fake.
- **`ADS-2` Rewarded: revive — `3`**
  AC: on game-over, optional rewarded continue; only on verified completion; graceful no-fill fallback.
- **`ADS-3` Rewarded: earn coins / double daily — `2`**
  AC: opt-in rewarded grants coins or doubles the daily reward; idempotent; abuse-guarded.
- **`ADS-4` Interstitial (capped) — `3`**
  AC: on game-over→replay only, never mid-run; hard frequency cap; suppressed in first session and for entitled users.

### Sprint 9 — IAP & analytics (native, high risk)
*Goal: the anchor purchase + measurement.*

- **`IAP-0` Billing spike — `3`**
  AC: sandbox purchase round-trips on both stores via `BillingProvider` `expect/actual` (evaluate RevenueCat to abstract both).
- **`IAP-1` Remove-Ads product — `2`**
  AC: store-configured product loads with localized price; purchase flow completes in sandbox.
- **`IAP-2` Entitlement + gating — `2`**
  AC: owning Remove-Ads disables interstitials/banners, **keeps rewarded**; entitlement persists and is checked at every ad call site.
- **`IAP-3` Restore purchases — `2`**
  AC: restore re-grants entitlement on a fresh install/sign-in-less device; required for store review.
- **`IAP-4` Store / paywall UI — `2`**
  AC: simple screen presents Remove-Ads value + price + buy/restore.
- **`ANL-1` Analytics SDK — `2`** *(AnalyticsLogger expect/actual)*
  AC: client analytics initialized on both platforms; debug-verifiable; no PII.
- **`ANL-2` Core event taxonomy — `3`**
  AC: instruments game_start, game_over, daily_completed, share_tapped, ad_impression, ad_reward_granted, iap_purchase, ftue_step; high-frequency events sampled/aggregated.

### Sprint 10 — FTUE, a11y, localization, release
*Goal: frictionless first run and a shippable build.*

- **`FTU-1` Guided first moves — `3`**
  AC: first run shows 1–2 swipe hints, then stops; only on very first session.
- **`FTU-2` Favorable first seed — `1`**
  AC: first-ever game uses a seed that yields an early satisfying merge.
- **`FTU-3` Daily soft-introduction — `1`**
  AC: end of first session surfaces tomorrow's Daily + countdown; no forced login, rating prompt deferred.
- **`ACC-1` Colorblind + numerals + patterns — `2`**
  AC: colorblind-safe palette; numerals always shown; optional pattern-on-tile; verified against common CVD types.
- **`ACC-2` Dynamic type & one-handed — `2`**
  AC: UI scales with OS text size without clipping; controls reachable one-handed on large devices.
- **`LOC-1` String externalization (EN/HU) — `3`**
  AC: all user-facing strings externalized; EN + HU complete; locale switch verified.
- **`REL-1` Crash reporting — `1`**
  AC: crash/non-fatal reporting live on both platforms.
- **`REL-2` Store assets — `3`**
  AC: icon, screenshots, preview video showing the *juice*; ASO copy drafted.
- **`REL-3` Privacy & compliance — `3`**
  AC: privacy policy; Play Data-Safety + iOS Privacy Nutrition labels reflecting ads/analytics; iOS ATT prompt wired for ad tracking.
- **`REL-4` Beta + soft-launch — `3`**
  AC: TestFlight + Play internal-testing builds signed and distributed; pre-launch checklist passed; soft-launch in 1 representative market.

---

## 6. Build sequence & key checkpoints

```
S0 Foundation ─▶ S1 Engine ─▶ S2 First Playable ★ ─▶ S3 Feel ─▶ S4 Shell
   ─▶ S5 Daily ─▶ S6 Retention ─▶ S7 Cosmetics ─▶ S8 Ads ⚠ ─▶ S9 IAP+Analytics ⚠ ─▶ S10 Release ★
```

- **★ First Playable (end S2):** earliest end-to-end proof the core loop is fun. Validate game feel direction here before investing further.
- **⚠ Native-risk window (S8–S9):** ads + billing are the only genuinely uncertain work. Each opens with a *spike* story so the risk is retired before feature stories depend on it. If a spike reveals trouble, RevenueCat (IAP) and a single mediation SDK (ads) are the fallbacks.
- **★ Release (end S10):** soft-launch to start collecting the real retention/monetization numbers the PRD's targets must be validated against.

**Suggested early validation gate:** after First Playable + Feel (S2–S3), put the build in front of 5–10 testers. If the touch feel doesn't land, fix it before building economy/monetization on top.

---

## 7. Risks & mitigations (build-specific)

| Risk | Mitigation |
|---|---|
| Ad/IAP SDKs lack first-class KMP support → `expect/actual` friction | Isolate behind thin providers; spike-first (ADS-0, IAP-0); RevenueCat / single mediation as fallbacks |
| Game feel underwhelms (the whole differentiation) | Validate at First Playable; dedicate S3 entirely to feel; tester gate before proceeding |
| Solo velocity unknown → schedule slips | Plan is backlog-ordered, not date-bound; calibrate after S1–S2; cut cosmetics depth (S7) first if needed |
| iOS-side surprises (Compose MP maturity, StoreKit, ATT) | Build + run on iOS every story (DoD); never let iOS drift to the end |
| Store review rejection (privacy/ATT/restore) | REL-3 + IAP-3 are explicit, non-optional stories |
| Daily seed mismatch across devices/timezones | DLY-1 tested across timezone + midnight boundaries; engine replayable from seed |

---

## 8. What "later" looks like (out of this plan, for context)

Fast-follow once v1.0 data exists: cloud save + sign-in, remote config (move tunables server-side), gem/powerup economy, leaderboards (with server-side replay validation), season pass + first live-ops event, Blitz/Zen modes, board variants. The architecture above (pure replayable engine, provider abstractions, token-driven theming) is deliberately shaped so these slot in without rework.

---

*Appendix — quick reference*
**MVP modes:** Classic, Daily. **MVP currency:** Coins only. **MVP IAP:** Remove Ads. **MVP ads:** rewarded (revive / coins / double-daily) + capped interstitial. **No backend, no cloud save, no remote config in v1.0.**
