# Weather administration

Use operator permission level 2 or higher. In Creative mode, open
**Sky's Weather Admin**, take a **Weather Controller**, place it in the
Overworld and right-click it. Operators can also open the same panel with
`/skyweather gui`. The panel leaves the world visible and keeps the game
running while it is open.

| Panel | Controls |
| --- | --- |
| Weather | Return to Auto, clear the sky, start rain or thunder, or choose one of the 18 named events. Set a duration, peak intensity and optional dry-biome coverage. |
| Calendar | Step to the previous or next phase, jump to a phase and day, add Minecraft days, or choose dawn, noon, dusk or midnight. |
| Climate | Select a preset from the server's library. Adjust seasonal strength and each phase's length, temperature, wetness, thunder and fog. |
| Effects | Toggle passive mode, seasonal snow and ice, foliage tint, fog, scheduled events, or climate-aware plant growth. Reset saved world overrides. |

Controls apply to the whole Overworld. Edits are previews until **Save to world**;
**Discard** restores the last saved settings. Only edited fields are stored,
allowing other values to inherit future pack defaults. Set pack defaults through
the normal Mods configuration screen or server config. Calendar jumps change
a seasonal offset while preserving the daylight clock. Presets with different phase lengths can map the same
world tick to a different phase. Weather may place snow or freeze water during a preview
if seasonal surface effects are enabled.

Forced events use elapsed server ticks. They keep their remaining duration
when the daylight clock is paused or moved to another season. A forced event
covers the Overworld so it can be tested on the spot. Local temperature,
biome and shelter still apply. A subsequent vanilla `/weather` command
can take over, and sleep can end a preview.

## Commands

| Command | Action |
| --- | --- |
| `/skyweather status` | Show the phase, day, weather and current preset. |
| `/skyweather climate [x y z]` | Inspect temperatures, precipitation, fog, wind, biome rainfall, wetness and thunder multipliers, and the native or seasonal surface policy. With no coordinates, uses the player's eye position or the command source's position. Accepts relative coordinates and requires a loaded chunk. |
| `/skyweather gui` | Open the controller for the issuing player. |
| `/skyweather season next` or `previous` | Step to day 1 of the adjacent phase and keep the time of day. |
| `/skyweather season set winter 45` | Jump to winter day 45 of the current calendar year. Phase slots are `spring`, `summer`, `autumn` and `winter`, even for presets with custom phase labels. |
| `/skyweather season advance 7` | Advance seven Minecraft days. |
| `/skyweather time dusk` | Set the time of day without changing the calendar day. Accepts `dawn`, `noon`, `dusk`, `midnight` or a tick from 0 to 23999. |
| `/skyweather weather blizzard 90 0.9` | Start a blizzard preview for 90 seconds at a peak intensity of 0.9. Omit both values to use 90 seconds and 0.85. |
| `/skyweather weather rain 60 0.6 dry` | Start a rain preview and allow precipitation in dry biomes. Explicit biome overrides still take precedence. |
| `/skyweather weather auto` | End a preview and return control to the configured weather. |
| `/skyweather presets` | List the preset IDs available on the server. |
| `/skyweather preset tropical_monsoon` | Preview the Tropical Monsoon preset in Universal Climate mode. |
| `/skyweather mode per_biome` | Preview biome climates. Also accepts `universal` and `real_world`. Real-world preview requires a configured startup location. |
| `/skyweather strength 1.5` | Change overall seasonal strength for the session. |
| `/skyweather phase winter 45 -0.8 1.2 0.4 0.5` | Edit one phase in the order days, temperature, wetness, thunder and fog. |
| `/skyweather option surface false` | Change an effect for the session. Options are `passive`, `surface`, `foliage`, `fog`, `scheduled` and `plants`. |
| `/skyweather reset` | Discard unsaved edits and end forced weather. |
| `/skyweather world save` | Save explicitly edited fields to this world. |
| `/skyweather world discard` | Restore the last saved world settings. |
| `/skyweather world reset` | Remove world overrides and inherit pack defaults. |
| `/skyweather validate` | Summarise pack rule and integration issues. |
| `/skyweather dump` | Write resolved rules and status to a JSON report inside the world. |

`/skyweather crop [x y z]` reports local plant suitability and the resolved
profile. Without coordinates it uses the operator's crosshair.
`/skyweather option plants false` leaves natural growth native for the session.
See [farming](FARMING.md).

Use Tab in chat to see preset and event IDs. Weather previews accept 1–2400
seconds and intensity 0.01–1. The vanilla `/weather` command remains available.

## Climate HUD

Open **F3** for detailed readings at the camera's position, including the active
mode and resolved climate preset. In **Mods →
Sky's Realistic Weather → Config → Configure selected mode → Display and Climate HUD**, you can turn
F3 readings off or enable a compact season, temperature and weather display.
Assign **Toggle Climate HUD** in Minecraft's Controls menu to switch the
compact display in game. Both readouts respect reduced debug info.

Temperature readouts follow the viewer's **Display / units** preference. The
controller's phase temperature field is a Celsius/Fahrenheit **adjustment**;
its label uses Δ. The raw `/skyweather phase` command keeps its Minecraft-unit
arguments for script compatibility. See [display units](CONFIGURATION.md#temperature-display-units).
