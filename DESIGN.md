# KWeather design direction

Dial: ENERGY 3 / RHYTHM 2 / MOTION 2

## Identity

- Keep the current design: one fixed home layout, colors taken from the live weather scene, art drawn in code.
- Energy is bold: a confident hero and strong scene color that carries into the cards below.
- The motif is the wind. The scene moves with the real wind (rain slant, snow drift, cloud speed, grass lean). Scene parts that move or exist without weather data behind them don't belong.

## Typography

- One distinctive typeface for figures only (temperatures, wind values, tile values, chart labels), with tabular numbers.
- System Roboto for words.

## Layout rhythm

- Card order is fixed (see CLAUDE.md), but cards carry different visual weight. The wind panel leads.

## Scene craft

- One light source per scene. The sun (or the top of the sky when covered or at night) sets where every cloud is lit and where it falls into shadow; the sun also spreads a broad, faint bloom into the air around it.
- Cumulus are painted in three tones: lit crown, body, shadowed base. Each lump keeps a thin shadow crescent on the side away from the light, and lower lumps overlap the ones above them.
- A covered sky is layered bands with lumpy soft lower edges and a light rim where the edge thins, darkest overhead and hazier toward the horizon. Scud under it is soft and low contrast.
- Depth comes from air: farther clouds are smaller, hazier and slower; a distant ridge and a milky horizon band follow visibility.
- Cloud count follows cloud cover; rain shafts, splashes, fog banks and snow drift follow precipitation and wind.
- A grain film of 2% or less is allowed only to break up gradient banding. No visible texture, no sketchy lines.
- Soft shapes are rendered once into small bitmaps at runtime and only moved per frame. That is a cache of code-drawn art, not a bitmap asset.
