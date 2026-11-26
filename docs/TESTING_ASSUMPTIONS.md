# Climate testing assumptions

## Intended balance

Weather is inspired by climate, expressed through Minecraft mechanics and
balanced for noticeable encounters. Clear intervals, seasonal contrast, cold
spells with snow, and persistent cover matter more than matching a station to
an invented millimetre conversion. The 365-day calendar remains the default;
Short Seasons is optional. Per Biome Climate remains the default mode.

The acceptance envelopes in `scripts/climate-objectives.json` are explicit
design choices, set before the new pilots. They cover all 28 starter entries.
Natural climates aim toward an expressive plausible range; fantasy climates
extend their defining conditions. They are not confidence intervals for real
weather. Each preset represents a broad environment rather than every place
in that climate class. British Isles now represents northern British lowlands,
retaining the `oxford` registry/config ID for compatibility. A literal Oxford
or milder southern climate can be saved as an edited/duplicated preset.

## Rechecked evidence

The [Met Office North East England climate guide](https://www.metoffice.gov.uk/api/assets/file/north-east-england_-climate-met-officepdf?prefix=assets)
describes roughly 20 snow-falling days in low areas and up to 50 on the higher
Pennines, with approximately 10 versus over 40 snow-lying days. Its charts
include sleet with snow; these are not identical to Minecraft's pure-snow
observations. The new British Isles goals of 18–35 lowland snow dates and
35–85 at Y=200 deliberately favour an expressive northern winter. A Minecraft
block of elevation is not a calibrated metre or fixed lapse rate in Celsius.

[Singapore's Meteorological Service](https://www.weather.gov.sg/climate-climate-of-singapore/)
distinguishes brief afternoon/evening convection from widespread monsoon
surges, with rain-day counts defined by a 0.2 mm threshold. These motivate
separate shower and burst behaviours; generic rainforest wet dates are not
claimed to reproduce its rain gauge. [Darwin station data](https://www.bom.gov.au/climate/averages/tables/cw_014015.shtml)
show a pronounced wet/dry contrast. Our monsoon and savanna phases use that
pattern, without imposing southern-hemisphere dates on the shared calendar.
[JMA normals](https://www.data.jma.go.jp/stats/en/normal/normal.html)
anchor the contrast between snowy continental winters and warm summers.
[Australian Antarctic Program guidance](https://www.antarctica.gov.au/about-antarctica/weather-and-climate/weather/)
distinguishes new snowfall from drifting/blowing existing snow. Ice Cap stays
cold and relatively dry while allowing frequent windblown snow.

These sources anchor mechanisms and broad contrasts. There is no claim that
all 18 natural climates have been independently fitted to station records.
Fantasy targets are intentional gameplay choices.

## Earlier assumptions replaced

- The former 0.2–10 annual UK snow-day gate could accept barely any winter
  weather. It was a smoke check, not an adequate balance baseline.
- Primary weather and cold/heat originally competed for one daily episode.
  A cold snap could suppress the rain needed for snow. They now have separate
  deterministic lanes. The strongest same-sign anomaly applies, rather than
  double-counting the front and its associated cold air.
- A mean seasonal temperature alone hid cold nights and warm afternoons. A
  smooth daily cycle now changes the current temperature, while the API's
  seasonal temperature remains independent of transient conditions.
- Counts previously used dawn-to-dawn windows, which split an overnight fog
  encounter over two dates. Frequency now uses civil midnight windows.
- Rain/snow obscuration was included in a metric named fog. Named fog now has
  its own threshold; general obscuration remains a separate diagnostic.
- Scheduled event lifetime overstated the time a player actually saw moving
  rain cells. Contiguous local encounters now include coverage gaps, onset,
  dissipation and replacement by another event.
- An hourly surface check did not represent native column selection. Surface
  verification now uses reproducible geometric waits with p=1/4096 per tick
  for each observed column (1/16 weather chance times 1/256 columns).
- End-of-day presence was not a snow-cover observation. A separate 09:00
  reading measures more than half of a 3x3 exposed patch covered by snow.
- The estimated liquid-equivalent millimetres had no independent physical
  validation. The old station-fit pass criteria are retired. The proxy stays
  in raw CSVs for diagnostics; it cannot establish rainfall accuracy.
- Native test world seeds formerly stayed at the fixture seed. They now follow
  the requested seed, so independent-seed native tests really differ.
- Failed/stale probe launches could misattribute results. The runner now binds
  compiled source hashes, main/probe jars and packaged tables to its receipts,
  rejects stale source and preserves existing outputs. A successful server
  process is separate from the analyzer's climate-balance verdict.

## Observable definitions

The sweep uses an open Plains reference at Y=81, with a paired Y=200 sample,
one full profile year of warm-up, 20 measured profile years on each of three
seeds, and five years at strengths zero and two. Frequencies are normalised
to 365 Minecraft days, including the 96-day alternative. Phases are weighted
by their actual lengths. Seed/year distributions supplement averages.

A wet/snow date has at least one sampled qualifying precipitation observation;
it does not mean an entire day of continuous weather. Named fog requires
local fog above 0.15; this is visible mist, not a WMO visibility measurement.
Thunder dates require liquid/hail precipitation, a thunder-capable event and
intensity of at least 0.3. Local strong encounters use the same intensity
threshold, and their mean must exceed 45 real seconds when at least 20
segments are observed. This is a minimum perceptible encounter, not a
requirement that every event last the same time. Cold/heat dates refer to
an active measurable thermal anomaly, rather than a Celsius threshold.
Windblown-snow sweep dates represent exposure potential; physical effects
still require snow-covered ground. Afternoon convection and predawn fog
onsets have separate timing checks.

The ordinary sweep samples every 100 ticks (5 real seconds), with a finer
40-tick run checking representative climates. It cannot detect every
sub-sample burst. Daily timeline and yearly CSVs retain variation; synthetic
rainfall estimates and planned durations are labelled separately from the
observed encounters used for acceptance.

## Native and compatibility scope

Native runs advance actual server weather handlers and vanilla timers every
tick. The default is 2,190 Minecraft days in all 28 presets plus Per Biome
Climate, with 365 warm-up days and five scored 365-day blocks. Short Seasons
cycles more often during that same observation interval. Eight fixed sites cover
Plains, Jungle, Desert, Cold Taiga, native Ice Plains, high Plains, Ocean and
high Extreme Hills. Each site has water and a 3x3 snow surface. Weather
column sampling uses its own deterministic random stream. Native Arctic
fixtures begin with their native untracked snow/ice, as normal generation
does. Warm biomes must stay clear of inappropriate deposits, Cold Taiga must
thaw in summer, and the native Arctic cover must survive every season.

Runtime reconstruction compares primary event, background thermal event and
temperature. Dedicated process restart checks saved snow/ice and later thaw.
The optional live feed has a 30-day synthetic observation replay; it does not
call a live provider. Supplied-biome API queries are checked on unloaded
terrain; client packet reconstruction must match server temperatures.
Fresh polar/taiga generation and actual Biomes O' Plenty Glacier population
have separate regression cases, including the native-protection opt-out.

These are accelerated handler and hook tests. They do not run a player,
entity AI, every random block tick, client rendering, a full modpack or
human gameplay acceptance. They do not establish a new whole-world-generation
performance baseline. Existing generation benchmarks retain their original
scope. No desktop control is required: servers are hidden, sequential, below
normal priority, one logical CPU and a 1 GiB heap. Full candidate-specific
results and limitations are in `build/verification/climate-balance/RESULT.md`.
