# Minecraft 1.11.2 JEI client checks

Packaged JEI **4.5.1.296** client checks and screenshot review passed on
27 November 2026, using Forge **13.20.1.2588** and the local **0.1.21.111021**
candidate. The checks found and corrected a recipe-panel layout problem.
Code and binaries remain local and unpublished.

| Profile | Climate entries | Result |
| --- | ---: | --- |
| Weather + JEI + vanilla content | 44 | Pass |
| Also HarvestCraft 1.11.2h, Mystical Agriculture 1.5.9 and Forestry 5.3.4.257 | 386 | Pass |
| JEI installed, weather integration disabled | 0 | Native JEI remains usable |

Each profile uses an ordinary guest player on a fresh dedicated server with no
operators. The server overrides wheat's profile; its preferred range appears as
**10.0–25.6 °C** or **50.0–78.0 °F** in JEI, rather than using the client's
default wheat rule. A synchronized API temperature zone changes between
**20 °C**, **-15 °C** and **45 °C**, exercising suitable, too-cold and too-hot
readouts and recovery.

## Client coverage

- Seed and harvested-produce lookups discover the climate category through
  JEI's native recipe registry and focus API.
- Actual JEI recipe screens render the seed, preferred range, growth limits,
  current suitability and all four local seasonal estimates.
- Celsius and Fahrenheit switch independently of server rules. Heated-zone
  values update the live reading; seasonal estimates still exclude heating.
- The compact summary fits inside JEI's real header, borders and recipe area at
  **800 x 480** and **1280 x 720**, both at GUI scale 2.
- Hovering over **?** opens the complete native tooltip, including the local
  temperature source. Ordinary body text does not trigger an obscuring tooltip.
- HarvestCraft items are indexed alongside vanilla plants. Mystical Agriculture
  crops retain their default climate exemptions. The combined catalogue has
  200 HarvestCraft and 138 Mystical Agriculture entries. An extended combined
  check also opens and renders HarvestCraft's Blackberry Seed and Mystical
  Agriculture's Inferium Seeds pages, confirming their ordinary and exempt
  readouts respectively.
- Disconnecting removes climate recipes and makes retained entries report
  unavailable climate safely. Rejoining rebuilds the catalogue without duplicate
  entries and restores the server rule and live temperature.
- With the JEI bridge disabled, its climate category stays absent while ordinary
  bread recipes open normally, including after reconnecting.

## Layout correction

The original 212 x 160 recipe background exceeded JEI 4's compact 196 x 166
window, whose dimensions also include its header and borders. Screenshots showed
text outside the panel and overlapping the header. The wrapper also opened its
full tooltip over most of the summary whenever the pointer crossed ordinary text.

The background now uses 172 x 124, with shorter summary wording and a dedicated
**?** marker for full details. The preferred range, live status, four seasonal
estimates and their weather/heating qualification remain visible. A new native
client regression fails against the previous build and passes after the fix;
it checks the actual JEI panel bounds and tooltip hit area.

## Candidate and evidence

Main jar SHA-256:

```text
ef4b2095af5199ac6e58d71822b68807285ad3b55c57518b644fc8060c2e1686
```

The build and all **57 unit tests** pass. Only `JeiClimatePlugin` and its three
nested class files differ from the multiplayer/performance candidate. All other
release entries are byte-identical, including weather, farming, world generation,
networking and heating. The [earlier generation and multiplayer report](PERFORMANCE_1_11_2.md)
keeps its original candidate hash; those workloads were not rerun for this
client-only layout correction. No third-party jar was changed.

Raw logs, screenshots, artifact/source hashes and unsuccessful attempts are
retained under ignored `build/verification/jei-client-1112`. Its `RESULT.md`
and `qualified-receipts.json` index the evidence. An early launch used incomplete
Minecraft assets and failed in JEI's language lookup; another fixture used the
wrong crop-config section. Both remain recorded. The runner now verifies the
original asset index and all 1,196 referenced asset objects before launch.

Tests ran one profile at a time. The client used a separate non-input Windows
desktop, muted sound, a 10 FPS cap and BelowNormal priority; the server and
client each used one logical CPU. No desktop switch or computer-control session
was needed. All worlds were disposable; no player world was copied or launched.
All test clients and servers have stopped.
See [reproduction instructions](BUILDING.md#jei-client-checks).

This completes the documented JEI client checks for the pinned builds and tested
English layouts. General manual gameplay and visual acceptance remain separate
from these targeted checks.
