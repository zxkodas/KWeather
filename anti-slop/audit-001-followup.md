# antislop audit 001: follow-up (2026-11-22)

All 14 approved findings are fixed. Checked with `./gradlew testDebugUnitTest` (30 tests, 0 failures, including the new `ContrastTest`) and on a phone (1080x2388) through the demo gallery.

| # | Status | What changed | Evidence |
|---|---|---|---|
| 1 | Fixed | `ScenePalette.heroInk` / `heroScrim`: white text over a veil tinted from the sky top, or dark text over a light veil on fog and snow days. Veil is drawn in the scene under the sun and moon, solid to the bottom of the hero text, then fading. | `ContrastTest.heroTextReadsOverTheWholeSky`, all 32 scenes ≥4.5:1 over sky, cloud deck and fog. Phone: fog and snow days show dark text. |
| 2 | Fixed | Light-mode `gust` `#B0640A` → `#7A3F00`, `rain` `#2F64A8` → `#234F8C`. | `ContrastTest.cardTextAndMarks` (worst gust 4.84, worst rain 4.57). |
| 3 | Fixed | New `sunInk` (deep amber on light cards) used by `ConditionIcon`, the storm bolt and the sun arc. | `ContrastTest`: worst 3.12:1. Phone: daily sun icons and arc visible on a light card. |
| 4 | Fixed | Hero is 60% of window height, clamped 340–520 dp. | Phone portrait: 520 dp hero, first card visible. Landscape checked by code only (I didn't change the phone's rotation setting). |
| 5 | Fixed | Sun is still: one halo, no pulse, no spinning rays. Dawn/dusk sun sits half set on the horizon. | Phone screenshots: Buenos Aires, Santiago del Estero, Córdoba. |
| 6 | Fixed | Shooting star: about half of all 40 s windows, at a random moment and place. Lightning: 2.5 s slots with a 30% chance (45% heavy), random bolt position. | Code; storm demo flashes irregularly. |
| 7 | Fixed | Hills replaced by a horizon of 150 grass blades (70 snow stubs). Lean follows mean wind and direction, sway follows gusts above the mean, in waves travelling downwind. | Phone: gale demo (65 km/h from W) bends hard right, calm demo stands upright; snow shows stubs on white ground; night shows silhouettes. |
| 8 | Fixed | Seeded puff clouds (3–6 puffs, flat base). Overcast/drizzle/rain/storm/snow get a three-layer scalloped deck plus faster scud. Partly cloudy and clear clouds stay below the hero text. | Phone: Ushuaia, Bahía Blanca, Bariloche (deck); Comodoro Rivadavia, Mendoza (puffs). |
| 9 | Fixed | Archivo (variable, SIL OFL) for figures with tabular digits: hero (semi-expanded 700), wind values, tiles, hourly and daily figures. License shipped at `assets/licenses/Archivo-OFL.txt`. | Phone screenshots. |
| 10 | Fixed | Wind panel uses a dark slice of the sky (`windCard`/`windCardEnd`) with white figures; six detail tiles merged into one panel with dividers. Order unchanged. | `ContrastTest.windPanel`; phone screenshots on light and dark scenes. |
| 11 | Fixed | Light page/card white mix 0.86/0.66 → 0.72/0.52. Dark `onCardVariant` `#AEB7C4` → `#BFC7D2` (Rain/Day was 4.23:1). | `ContrastTest.cardTextAndMarks`. |
| 12 | Fixed | Sky has a lighter horizon band (`ScenePalette.skyAt`), plus a warm glow on the horizon under the dawn/dusk sun. | Phone: Córdoba dawn, Ushuaia dusk. |
| 13 | Fixed | `ContrastTest` (4 tests) over every `SkyType` × `TimeOfDay`; contrast helpers in `ui/scene/Contrast.kt`. | Test report. |
| 14 | Fixed | Loading/error/permission page draws a still clear-sky scene behind a navy veil (white text ≥4.5:1 over sun, halo, horizon and grass). | Code and computed contrast; no live error state reproduced on the phone. |

## Found during verification

- The pager composes neighbouring pages, and every page set the status bar icon color, so the last one composed won (white icons on the snow page). `WeatherPage` now takes `active`, and only the current page sets the status bar. Phone: dark icons on fog and snow, white on clear sky.
- "Full moon in 1 days" now reads "Full moon tomorrow".

## Delivery Gate (Android app; web-only items such as `cursor`, `<html lang>` and `fetchpriority` don't apply)

- R-02 PASS: no em dash in UI strings, DESIGN.md or these reports (`grep` count 0).
- R-03 PASS: portrait verified on the phone; landscape hero clamp checked by code only.
- R-17/R-18/R-36/R-38 PASS: no new numbers or claims; demo data stays labelled.
- R-25 PASS: `ContrastTest` covers hero, card, wind panel and marks in all 32 scenes.
- R-27 PASS: loading, error and no-permission states unchanged in content, now with the scene behind them.
- R-35 PASS: built, installed and walked through: Settings → Demo gallery → every scene by swipe; real forecast page scrolled through all cards; Animated scenes toggled off (two frames 0.7 s apart identical, 0% difference vs 1.2% with motion on) and back on.
- R-19 PASS: everything that moves is driven by wind, gusts or precipitation, except star twinkle, the rare shooting star and lightning, which are part of their conditions.
- R-37 PASS: direction in DESIGN.md, dials ENERGY 3 / RHYTHM 2 / MOTION 2.
