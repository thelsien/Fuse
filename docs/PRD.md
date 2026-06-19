# Product Requirements Document — *Fuse* (working title)

A 2048-style merge-puzzle game for mobile

| | |
|---|---|
| **Document status** | Draft v0.1 — for review |
| **Owner** | Product (TBD) |
| **Last updated** | 2026-06-19 |
| **Platforms** | iOS, Android |
| **Audience for this doc** | Product, design, engineering, growth, monetization |

> **A note on assumptions.** This is a spec for a *new* product, not a teardown of the existing 2048. Numeric targets (retention, ARPDAU, eCPM, etc.) are stated as **goals to validate**, not measured facts — they should be replaced with real benchmarks from a soft-launch. Anywhere a default is proposed (currency prices, ad caps, spawn rates), treat it as a starting point to tune, not a final decision.

---

## 1. Executive summary

*Fuse* is a free-to-play merge-puzzle game built on the familiar 2048 mechanic (swipe to combine matching tiles into ever-larger numbers), wrapped in a modern, highly polished mobile experience. The original 2048 is open-source, free, and abundantly cloned — so the product thesis is **not** "another 2048." It is: *take the proven, instantly-understood core loop and win on polish, habit, and a shareable daily ritual* that the dozens of dated clones don't offer.

Three pillars carry the product:

1. **A best-in-class core loop** — exceptional game feel, instant onboarding, satisfying merges.
2. **A daily habit + shareable result** — a Wordle-style seeded Daily Challenge that drives organic, viral, zero-CAC growth.
3. **A respectful, cosmetic-led monetization model** — rewarded ads, a "remove ads" purchase, optional cosmetics, and a seasonal pass, balanced so the game still *feels* premium.

The MVP ships Classic + Daily modes, a clean economy, ads + remove-ads, cloud save, and analytics. Live-ops, powerup economy, leaderboards, and a season pass follow in fast iterations.

---

## 2. Vision & strategy

### 2.1 Problem / opportunity

The number-merge genre is one of the most *understood* mechanics in mobile gaming — a player needs zero tutorial to grasp "swipe, matching numbers combine, get a bigger number." That comprehension is the genre's superpower. The weakness: the category is dominated by aging, ad-stuffed clones with poor game feel, no live content, and no reason to return tomorrow.

The opportunity is to **own the "premium-feeling, habit-forming" slice** of a genre everyone already knows how to play.

### 2.2 Product vision

> *The merge game you actually look forward to opening every day — instantly playable, beautiful to touch, and impossible to put down for "one more run."*

### 2.3 Positioning & differentiation

| Lever | Typical 2048 clone | *Fuse* |
|---|---|---|
| Game feel | Stiff, instant tile snaps | Eased motion, merge "juice," haptics, sound design |
| Reason to return | None | Seeded **Daily Challenge** + streaks |
| Growth | Paid UA only | Shareable daily result (emoji board) drives organic installs |
| Monetization | Banner + aggressive interstitials | Rewarded-first, remove-ads, cosmetics, season pass |
| Content | Static | Live-ops events, rotating themes |
| Identity | Generic | Distinct brand, art direction, collectible tile skins |

**Defensibility** comes from execution + habit + brand, not the mechanic (which can't be owned). The daily ritual and cosmetic collection create switching cost; polish creates word-of-mouth.

> ⚠️ **Naming/IP note:** Avoid shipping under "2048" as the primary brand — the number is widely used and the original is attributed to Gabriele Cirulli (2014). Ship under an original brand ("Fuse" or similar) and describe the genre, not the trademark.

### 2.4 Target audience & personas

**Primary — "The Commuter" (casual habit player)**
- 25–45, plays in short bursts (queues, commutes, before bed).
- Wants a quick mental palate-cleanser, low commitment.
- Will return daily *if* there's a tiny ritual and a streak to protect.
- Monetizes via rewarded ads and the occasional remove-ads / pass purchase.

**Secondary — "The Optimizer" (score chaser)**
- Enjoys mastery, leaderboards, beating their own best.
- Drives competitive sharing and retention depth.
- Higher tolerance for powerups; a candidate for IAP.

**Tertiary — "The Sharer" (social/viral node)**
- Posts daily results to friends/group chats.
- Low monetization value, high *acquisition* value.

### 2.5 Success metrics (North Star + KPI tree)

**North Star:** *Daily active players who complete the Daily Challenge.* It captures habit (returns), engagement (completes), and growth (shareable), and correlates with both retention and monetization.

Targets to validate in soft-launch (genre-typical aspirations, not promises):

| Metric | Target | Why it matters |
|---|---|---|
| D1 retention | ≥ 40% | Habit forms early or not at all |
| D7 retention | ≥ 18% | Genre-strong; daily loop should lift this |
| D30 retention | ≥ 8% | Long-term habit |
| Sessions / DAU / day | 3–5 | Burst play pattern |
| Avg session length | 4–6 min | Healthy for casual puzzle |
| Daily Challenge completion (of DAU) | ≥ 50% | North-star input |
| Share rate (of daily completers) | ≥ 8% | Viral coefficient input |
| Blended ARPDAU | $0.05–0.15 | Mix of ads + IAP |
| k-factor (viral) | aim toward 0.1–0.3 | Offsets CAC |

---

## 3. Core gameplay & mechanics

### 3.1 Core loop

```
Open app ─▶ Pick mode (or land on Daily) ─▶ Swipe to merge tiles
   ▲                                                │
   │                                                ▼
Reward / streak / share ◀── Game over (or win) ◀── Climb the board
```

The micro-loop (one swipe) must feel *complete on its own*: input → motion → merge → score pop → haptic → sound. That single interaction is the product; everything else is scaffolding around making it feel great.

### 3.2 Rules (canonical baseline)

- **Board:** 4×4 grid (default). Variants (5×5, 6×6) are post-MVP.
- **Start:** 2 tiles spawn. Spawn value: **2** at 90%, **4** at 10%.
- **Move:** A swipe (up/down/left/right) slides all tiles as far as possible in that direction. Tiles do not pass through each other.
- **Merge:** Two adjacent tiles of equal value merging in the swipe direction combine into one tile of their sum. **Each tile merges at most once per move.** Merges resolve in the direction of travel (the tile closest to the wall merges first).
- **Spawn after move:** If the board changed, one new tile spawns (2 @ 90%, 4 @ 10%) in a random empty cell.
- **Invalid move:** A swipe that changes nothing does not spawn a tile and is a no-op (with a gentle "blocked" feedback).
- **Score:** Each merge adds the value of the *resulting* tile to score. Track current score + best score.
- **Win:** Creating a **2048** tile triggers a win celebration; player may continue ("Keep going") to chase higher tiles (4096, 8192, …).
- **Lose:** Board is full **and** no merge is possible in any direction → game over.

### 3.3 Difficulty & pacing

2048's difficulty is emergent (no explicit levels), which is elegant but flat. Add **pacing tools** that don't break the purity:

- **Spawn weighting** stays classic in Classic mode (no rubber-banding — players notice).
- **Mode-based difficulty** instead of per-run difficulty: Blitz (time pressure), bigger boards (more sprawl), modifiers in events.
- **Milestone celebration curve:** escalate feedback at 256 / 512 / 1024 / 2048 / beyond so the player always has a "next tier" to chase.

### 3.4 Game modes

| Mode | Description | Role |
|---|---|---|
| **Classic** | Endless 4×4, reach 2048 and beyond. The default "comfort" mode. | Core retention |
| **Daily Challenge** | One **seeded** board per day, identical for all players worldwide. Result is shareable (see §5/§6). | Habit + virality |
| **Blitz** (post-MVP) | 3-minute timed run, maximize score. | Competitive / leaderboard fuel |
| **Zen** (post-MVP) | Relaxed, generous undo, no pressure framing. | Soothing / accessibility-leaning |
| **Event boards** (live-ops) | Modifier runs (e.g., obstacle tiles, x3 spawns, themed). | Novelty / monetization hooks |

> **Why seeded Daily matters:** identical-board-for-everyone is what makes the *result* comparable and therefore *shareable* (the Wordle insight). It is the single highest-leverage growth + retention feature and should be in the MVP.

### 3.5 Progression & meta-systems

- **Best score & personal stats:** highest tile reached, games played, win streak, longest daily streak.
- **Player level / XP:** earn XP per run; levels unlock cosmetic rewards (tile skins, board themes). Keeps progression even when a run ends badly.
- **Collectible cosmetics:** tile number skins, board backgrounds, merge-effect packs, sound packs. The collection is the long-term meta and the primary IAP surface.
- **Streaks:** daily-play streak and daily-challenge streak, each with milestone rewards and loss-aversion pressure (see §6).

### 3.6 Economy

Two currencies (keep it simple; resist a third):

| Currency | Earned via | Spent on |
|---|---|---|
| **Coins** (soft) | Playing, daily reward, watching rewarded ads, milestones | Powerups, some cosmetics |
| **Gems** (hard) | IAP, occasional rewarded/event grants | Premium cosmetics, powerup bundles, season pass tier skips |

**Powerups** (consumable, post-MVP economy):
- **Undo** — revert last move.
- **Swap** — swap two adjacent tiles.
- **Hammer** — delete one tile.
- **Shuffle** — randomize board positions.

Powerups are the pressure-relief valve and a natural rewarded-ad / coin sink. In **Classic**, allow them but track "clean" (no-powerup) bests separately to preserve leaderboard integrity. In **Daily Challenge**, restrict or surface powerup use in the shared result so competition stays fair.

---

## 4. UX & design

### 4.1 Design principles

1. **Touch is the product.** Every swipe must feel physical, weighted, and responsive (<16ms input latency target).
2. **Zero friction to first merge.** No login wall, no splash gauntlet — board on screen and playable within seconds of first open.
3. **Premium restraint.** Clean layout, generous spacing, no clutter, no ad in the player's face mid-run.
4. **Readable always.** Numbers + color, never color alone.

### 4.2 Controls & input

- **Primary input:** directional swipe (up/down/left/right). Detect dominant axis from swipe vector; ignore diagonal ambiguity by snapping to the larger component.
- **Swipe tuning:** low distance threshold (fast flicks count), generous angle tolerance, debounce to prevent double-moves. This tuning *is* the feel — budget real time for it.
- **One-handed reachability:** all interactive UI (menus, buttons) reachable in the thumb zone; board centered/lower-biased on tall devices.
- **Optional:** arrow-button overlay (accessibility) and keyboard support (for tablet/desktop ports later).

### 4.3 Game feel ("juice")

This is the differentiator. Budget for it explicitly:

- **Tile motion:** ease-out slide (not linear snap), ~80–120ms, with subtle overshoot on the merged tile.
- **Merge pop:** scale-bounce + brief glow on the resulting tile; intensity scales with tile tier.
- **Milestone moments:** particle burst + screen flash + distinct sound at 512 / 1024 / 2048 (respect reduced-motion toggle).
- **Haptics:** light tick per merge, heavier thunk on milestone, "blocked" buzz on invalid move.
- **Audio:** per-merge tone that **rises in pitch** with tile value (creates an audible "climbing" feeling); ambient bed per theme; a satisfying win sting.
- **Combos:** when multiple merges resolve in one swipe, a brief combo counter + escalating sound.
- **Optional screen shake** on big merges (default subtle, toggleable).

### 4.4 Information architecture / screen flow

```
Launch ─▶ Home
              ├─▶ Daily Challenge (featured, with streak + countdown)
              ├─▶ Classic
              ├─▶ Modes (Blitz, Zen, Events)   [post-MVP]
              ├─▶ Store (cosmetics, remove ads, gems, pass)
              ├─▶ Collection / Cosmetics
              ├─▶ Leaderboards               [post-MVP]
              └─▶ Settings
Game screen ─▶ (pause) ─▶ Resume / Restart / Quit
Game over ─▶ Score + best + "continue via rewarded ad?" + Share + Play again
```

- **Home defaults to surfacing the Daily** (with streak and a countdown to tomorrow's board) to reinforce the habit.
- **Game-over screen** is the key monetization + growth moment: rewarded "continue," share, replay.

### 4.5 First-time user experience (FTUE)

- **No forced account.** Play immediately; offer optional cloud-save sign-in *after* the first satisfying run.
- **Guided first moves:** a hand/arrow hint for the first 1–2 swipes, then get out of the way. The mechanic is self-evident — over-tutorializing insults the player.
- **Engineer an early "aha":** ensure the first run produces a satisfying merge chain quickly (e.g., a lightly favorable opening seed for the very first game only).
- **Soft-introduce the Daily** at the end of the first session ("Come back tomorrow — today's challenge resets in …").

### 4.6 Accessibility

- **Colorblind-safe palette** with always-visible numerals; optional high-contrast and pattern-on-tile modes.
- **Reduced-motion** toggle (disables shake/particles/overshoot).
- **Dynamic type / scalable UI** respecting OS text-size settings.
- **Haptics + sound** as redundant feedback channels (each independently toggleable).
- **One-handed mode** for large devices.

---

## 5. Monetization & live-ops

### 5.1 Model overview

Hybrid, **rewarded-first**, designed to preserve a premium feel:

- **Ads** — primary revenue at scale, weighted toward *opt-in* rewarded formats.
- **IAP** — Remove Ads (the anchor purchase), gem packs, cosmetic bundles, season pass.
- **Principle:** the player should always be able to *choose* to watch an ad for benefit, and should rarely have one forced on them mid-experience.

### 5.2 Advertising

| Format | Placement | Cap / rule |
|---|---|---|
| **Rewarded video** | "Continue after game over," free Undo, double the daily reward, earn coins on demand | Always opt-in; generous availability |
| **Interstitial** | Between runs (on game-over → replay), **never mid-run** | Hard frequency cap (e.g., ≤1 per N minutes / every 3rd game-over), skip first session, suppress for paying users |
| **Banner** | Menus/store only (optional) — consider omitting for premium feel | Never on the active game board |

- **Remove Ads** disables interstitials + banners but **keeps rewarded available** (players who removed ads still opt into rewarded for benefits — preserves that revenue).
- **Mediation:** use an ad mediation layer for eCPM optimization; instrument fill rate and per-placement eCPM.

### 5.3 In-app purchases

| Product | Indicative price (USD, tune per market) | Notes |
|---|---|---|
| **Remove Ads** | $3.99–4.99 | Anchor IAP; highest take rate; strong value framing |
| **Gem packs** | $0.99 / $4.99 / $9.99 / $19.99 | Standard tiered ladder |
| **Cosmetic bundles** | $1.99–4.99 | Themes, tile-skin sets, effect packs |
| **Season Pass** | $4.99 / season | Free + premium track; ties to live-ops |
| **Starter bundle** | $1.99 (one-time, first 24–72h) | Remove-ads-lite + cosmetic + gems; strong first-purchase converter |

> Pricing must be localized with store price tiers (PPP-aware), not a flat USD conversion.

### 5.4 Live-ops

A lightweight but real live-ops cadence sustains engagement and revenue:

- **Daily Challenge** (always on) — the heartbeat.
- **Weekly themed events** — modifier boards, limited-time cosmetics, leaderboards.
- **Seasonal pass** (~4-week seasons) — free + premium tracks; progress via daily play; rewards = cosmetics + currency. The premium track is a major IAP driver.
- **Limited cosmetic drops** — rotating store, seasonal/holiday themes (scarcity + freshness).
- **Calendar & remote config:** all events, prices, ad caps, and economy values must be **server-driven** so the team can tune without a client release.

### 5.5 Economy balancing

- **Sources & sinks must net out** so coins feel earnable but powerups/cosmetics retain value.
- Model the rewarded-ad → coin → powerup loop carefully: ads should accelerate, never trivialize.
- Track an **economy dashboard** (currency in/out per DAU) from day one; rebalance via remote config.

---

## 6. Retention & growth

### 6.1 Retention mechanics

- **Daily Challenge + streak:** the core habit. A visible streak counter creates loss aversion; a milestone reward ladder (3/7/14/30-day) rewards persistence.
- **Streak-saver:** one free streak-freeze per period, or a rewarded-ad/gem option to restore a broken streak (monetizable loss-aversion — used sparingly).
- **Daily login reward:** escalating coins/gems over a 7-day cycle.
- **"One more run" framing:** the game-over screen makes replaying the path of least resistance (single tap to replay, best-score taunt).
- **Personal bests & near-misses:** "You were 40 points from your best!" nudges another attempt.

### 6.2 Onboarding-to-habit

The first session must do three things: deliver one satisfying run, plant the Daily-tomorrow hook, and *not* ask for anything (no signup, no rating prompt, no ad). Defer all asks until trust is earned (post first or second satisfying session).

### 6.3 Social & virality

- **Shareable Daily result:** an emoji/board snapshot card — e.g., a colored mini-grid + "*Fuse* Daily #142 · best tile 2048 · 187 moves" — designed to be copy-pasteable into chats and posts (the Wordle growth engine). **This is the primary organic-growth lever.**
- **Friend leaderboards** (post-MVP): compare Daily results with friends; comparison is the retention glue.
- **Invite reward:** modest cosmetic/currency for referrals (keep it clean to avoid spammy patterns and store-policy risk).
- **Optional share of personal milestones** ("Hit 4096!").

### 6.4 Lifecycle messaging (push notifications)

- **Daily Challenge ready / resets soon** (timezone-aware, sent at the user's typical play time).
- **Streak about to break** (loss-aversion nudge).
- **Event live / pass ending soon.**
- All opt-in, frequency-capped, and personalized to play time — over-notifying churns the very players you're trying to keep.

### 6.5 UA & ASO (overview)

- **ASO:** lean into the recognizable genre in keywords/screenshots while branding distinctly; show *juice* in the video preview (the differentiator is visible motion).
- **Creatives:** highlight the satisfying merge feel and the daily/social hook.
- **Soft-launch** in 1–2 representative markets to tune retention/monetization before scaling paid UA.
- **Organic-first thesis:** the shareable Daily should lower blended CAC; measure k-factor and lean into organic before heavy paid spend.

---

## 7. Metrics & analytics

### 7.1 KPI tree (recap)

- **North Star:** Daily Challenge completers (DAU who finish the Daily).
- **Inputs:** installs → FTUE completion → D1 → Daily adoption → streak length → share rate → revenue (ad + IAP).

### 7.2 Event taxonomy (minimum viable instrumentation)

| Event | Key properties |
|---|---|
| `app_open` | source, session_id, is_first |
| `ftue_step` / `ftue_complete` | step_index |
| `game_start` | mode, seed (for daily), board_size |
| `move` | direction, did_change, merges_count |
| `merge` | result_value (sampled or aggregated to avoid spam) |
| `milestone_reached` | tile_value (512/1024/2048/…) |
| `game_over` | mode, score, best_tile, moves, duration, used_powerups |
| `powerup_used` | type, currency_spent |
| `ad_request` / `ad_impression` / `ad_reward_granted` | format, placement, eCPM (where available) |
| `iap_initiated` / `iap_purchase` | product_id, price, currency |
| `daily_started` / `daily_completed` | seed, result, streak_len |
| `share_tapped` | surface (daily / milestone) |
| `notification_received` / `_opened` | type |

> Sample high-frequency events (`move`, `merge`) or aggregate per-run to control event volume and cost.

### 7.3 Funnels to watch

- Install → first run → first merge → first game-over → D1 return.
- DAU → Daily started → Daily completed → shared.
- Game-over → rewarded-continue → replay (the monetization + engagement junction).

---

## 8. Technical overview (light)

*Engineering depth intentionally out of scope for this doc — captured here only as constraints that affect product decisions.*

- **Platforms:** iOS + Android. Engine/stack TBD by engineering (native vs. cross-platform such as a lightweight game framework — chosen for buttery animation/haptics, since *feel* is the product).
- **Offline-first:** Classic mode fully playable offline; sync when connectivity returns. Daily Challenge needs the day's seed (cache ahead where possible).
- **Save & cloud sync:** local-first save; optional account-based cloud sync so progress/cosmetics/streaks survive device changes. Conflict resolution favors highest progress.
- **Determinism:** Daily Challenge uses a **shared daily seed** so every player gets an identical board — the merge/spawn RNG must be seed-deterministic and validated for fairness.
- **Remote config / server-driven:** economy values, ad caps, prices, event calendar all server-controlled (no client release to tune).
- **Third-party stack:** ad mediation SDK, analytics SDK, attribution SDK, store billing — selection deferred to engineering.
- **Anti-cheat (leaderboards):** server-side validation of Daily/Blitz results (move-log replay) before they hit competitive boards.

---

## 9. Scope & roadmap

### 9.1 MVP (v1.0) — "It feels great and I'll come back tomorrow"

**In scope:**
- Classic mode (4×4, full ruleset, best score).
- **Daily Challenge** (seeded, shareable result).
- Polished game feel (motion, merge juice, haptics, audio).
- FTUE (frictionless, no forced login).
- Streaks + daily login reward.
- Ads: rewarded (continue / coins / double daily) + capped interstitial on game-over.
- IAP: **Remove Ads** + a small gem pack + starter bundle.
- Basic cosmetics (a few tile skins / board themes) + simple collection.
- Cloud save (optional sign-in).
- Analytics instrumentation + remote config.
- Settings (audio, haptics, reduced motion, colorblind mode).

**Explicitly out of MVP:** Blitz/Zen, powerup economy, gem store depth, leaderboards, season pass, friend social, board-size variants, events.

### 9.2 Fast-follow (v1.1–v1.3)

- Powerup economy (Undo/Swap/Hammer/Shuffle) + coin/gem sinks.
- Blitz + Zen modes.
- Gem store + expanded cosmetics + rotating store.
- Global + friend **leaderboards** (with server validation).
- **Season Pass** + first live-ops event.

### 9.3 Later (v2+)

- Board variants (5×5, 6×6) and modifier event boards.
- Deeper social (friend feeds, async challenges).
- New mechanics/modes informed by data.
- Tablet/desktop ports (keyboard support).

### 9.4 Out of scope (for now)

- Real-money / wagering mechanics.
- Multiplayer real-time merge battles (high complexity; revisit only if data demands).
- Gacha-style randomized paid loot (regulatory/store risk; cosmetics are sold directly).

---

## 10. Risks & open questions

| Risk | Impact | Mitigation |
|---|---|---|
| **Genre saturation** — "just another 2048" | High | Win on feel + Daily + brand; lead marketing with the differentiators |
| **Free open-source original** sets price expectation at $0 | High | Don't gate the core; monetize ads + cosmetics + convenience, not access |
| **Ad fatigue vs. premium feel** tension | Med | Rewarded-first, hard interstitial caps, never mid-run; remove-ads anchor |
| **Daily share doesn't go viral** | Med | A/B the share card design; the loop still aids retention even if k-factor is low |
| **Naming/IP around "2048"** | Med | Ship under original brand; describe genre, not trademark |
| **Economy imbalance** (currency inflation) | Med | Server-driven values + economy dashboard from day one |
| **Notification over-send churns users** | Med | Time-of-day personalization, strict caps, opt-in |
| **Leaderboard cheating** | Med | Server-side replay validation for competitive modes |

**Open questions for the team:**
1. Native vs. cross-platform engine — what gives the best haptic/animation fidelity for the budget?
2. Two currencies from MVP, or introduce gems only when the powerup/cosmetic depth lands?
3. Should remove-ads be a one-time IAP, a subscription, or both?
4. Which 1–2 soft-launch markets best represent the target audience for tuning?
5. How aggressive should the streak-restore monetization be before it feels predatory?

---

## Appendix

### A. Glossary

- **Merge:** combining two equal tiles into one of double value.
- **Run:** a single play session from new board to game-over (or quit).
- **Daily Challenge:** a seeded, identical-for-everyone board released once per day.
- **Juice / game feel:** the layered audiovisual + haptic feedback that makes interaction satisfying.
- **Rewarded ad:** an opt-in video the player watches in exchange for a benefit.
- **ARPDAU:** average revenue per daily active user.
- **k-factor:** average number of new users each existing user brings in (virality).
- **Remote config:** server-controlled values changeable without a client update.

### B. Reference notes

- Core mechanic derives from the well-known 2048 (Gabriele Cirulli, 2014), itself inspired by *Threes* and *1024*. The mechanic is genre-standard and not proprietary.
- The "seeded daily + shareable emoji result" growth pattern is modeled on the daily-puzzle viral loop popularized by *Wordle*.
- All benchmark targets in this document are aspirational and **must be validated** against soft-launch data before being treated as commitments.
