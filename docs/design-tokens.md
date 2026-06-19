# Fuse — Design Tokens

Extracted verbatim from the interactive prototype (`Fuse MVP standalone.html`).
These are the source of truth for **FND-4 (Design tokens & theme)**. The Compose
Multiplatform theme should reproduce these values exactly so the native build
matches the prototype's look and feel.

> Source: the prototype's `theme()` and `voltColor()` functions plus inline styles.

---

## Brand

| Token | Value | Use |
|---|---|---|
| Brand navy | `#0A0E26` | Logo background, dark app bg |
| Brand mint | `#34F5C5` | Logo mark, "good"/success, 2048 tile |
| Gold gradient | `linear-gradient(135deg, #FFD84D, #E8A800)` | Coins, daily reward, currency chips |
| Accent gradient | `linear-gradient(150deg, #5B6EF5 0%, #8B5CF6 55%, #D946EF 100%)` | Primary CTAs / hero |

---

## Semantic palette

### Dark theme (default)
| Token | Value |
|---|---|
| `bg` | `#0A0E26` |
| `card` | `#141A38` |
| `card2` | `#1E2750` |
| `navBg` | `rgba(10,14,38,.85)` |
| `line` (divider) | `rgba(255,255,255,.08)` |
| `text` | `#DCE6FF` |
| `sub` (secondary text) | `#8A97D6` |
| `accent` | `#6D7DFF` |
| `accentSoft` | `#1E2750` |
| `good` (success) | `#34F5C5` |
| `gold` | `#FACC15` |

### Light theme
| Token | Value |
|---|---|
| `bg` | `#EEF2FF` |
| `card` | `#FFFFFF` |
| `card2` | `#E9EEFF` |
| `navBg` | `rgba(255,255,255,.9)` |
| `line` (divider) | `rgba(20,30,80,.09)` |
| `text` | `#1B2559` |
| `sub` | `#6B7BB5` |
| `accent` | `#5B6EF5` |
| `accentSoft` | `#E7ECFF` |
| `good` | `#0FB99A` |
| `gold` | `#E8A800` |

---

## Tile color ramp (`voltColor`)

Each tile value maps to a background (`bg`) and foreground/numeral (`fg`) color.
The 2048 tile additionally carries a glow. Values above 2048 fall back to the
last entry.

| Value | bg | fg | Note |
|---|---|---|---|
| 2 | `#D7E6FF` | `#1E3A8A` | |
| 4 | `#AFCBFF` | `#1E3A8A` | |
| 8 | `#5B9DFF` | `#FFFFFF` | |
| 16 | `#3B82F6` | `#FFFFFF` | |
| 32 | `#22D3EE` | `#06324A` | |
| 64 | `#14B8A6` | `#FFFFFF` | |
| 128 | `#8B5CF6` | `#FFFFFF` | |
| 256 | `#A855F7` | `#FFFFFF` | |
| 512 | `#D946EF` | `#FFFFFF` | |
| 1024 | `#EC4899` | `#FFFFFF` | |
| 2048 | `#34F5C5` | `#06324A` | glow: `0 0 0 3px rgba(52,245,197,.3), 0 0 30px rgba(52,245,197,.8)` |
| > 2048 (fallback) | `#06324A` | `#34F5C5` | |

The ramp reads as a journey: pale blue → blue → cyan → teal → violet → magenta →
pink, culminating in the mint "2048" brand moment.

---

## Board geometry

From `renderBoard()` (prototype uses a 4×4 grid):

| Token | Value |
|---|---|
| Board padding | `12` |
| Cell size | `72` |
| Cell gap | `11` |
| Board side | `pad*2 + 4*cell + 3*gap` = `345` |
| Board bg (dark) | `#141A38` |
| Board bg (light) | `#DCE7FF` |

> Treat absolute px as ratios — scale the board to fit device width, preserving
> the pad:cell:gap proportions (12 : 72 : 11).

---

## Typography

System font stack: `-apple-system, BlinkMacSystemFont, sans-serif`
(Compose: use the platform default / SF on iOS, Roboto on Android).

| Weight | Use |
|---|---|
| 700 (Bold) | Dominant — titles, tile numerals, scores |
| 600 (SemiBold) | Buttons, labels |
| 500 / 400 | Body, secondary |

Size scale observed (px): `84, 52, 34` (display) · `28, 26, 24, 22` (titles) ·
`20, 19, 18, 17, 16` (headings) · `15, 14, 13, 12, 11, 10` (body / caption).

---

## Shape (corner radius)

| Radius | Use |
|---|---|
| `16` | Cards (most common) |
| `12`, `11` | Tiles, inner cards |
| `10`, `9` | Chips, small controls |
| `18`, `14` | Large cards / sheets |
| `999` / `50%` | Pills, circular badges |
| `30 30 0 0` | Bottom sheets |

---

## Motion

| Token | Value |
|---|---|
| Tile slide | `110ms`, `cubic-bezier(.2,.7,.3,1)`, ease-out |
| Standard easing | `cubic-bezier(.2,.8,.2,1)` |
| Generic transition | `.15s` (transform / background) |
| Other durations seen | `150ms`, `170ms`, `230ms` |
| Reduced motion | duration → `none` / `1ms` (disable slide, overshoot, particles) |

Matches the PRD's "ease-out slide, ~80–120ms, subtle overshoot on merge" target
(§4.3). Reduced-motion plumbing (`FEL-8`) maps to the `reduced` branch above.
