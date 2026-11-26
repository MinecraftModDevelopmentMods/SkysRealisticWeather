# Distant weather

Rain and snow can be seen over the landscape from outside the affected biome.
The native nearby precipitation remains, with distant curtains added beyond it.
Each curtain uses the climate, altitude, storm coverage and precipitation type
at its own location. A cold mountain can show snow while a warmer valley shows
rain during the same event. Existing Arctic temperature and surface exceptions
continue to apply.

Choose **Mods → Sky's Realistic Weather → Config → Display / units**, or the
**Display and Climate HUD** section under Edit Settings, to change
`distantWeatherDistance`. It applies immediately to your client. The default is
192 blocks; the maximum is 256. Set it to 0 to keep only the nearby renderer.
Minecraft's terrain view distance limits the result, and only loaded terrain
is sampled. Fast graphics uses coarser sampling and caps the distance at 96.
Clients can choose different distances on the same server.

The renderer samples four-block cells nearby, eight-block cells at medium
distance and sixteen-block cells farther away on Fancy graphics. Fast doubles
those cell sizes. It refreshes the cached field at most every five game ticks
while stationary, with earlier updates after movement, setting changes or a
weather/time jump. Samples reuse column objects. A hard limit of 2,048 columns,
terrain checks, view culling, distance fades and at most two batched draw calls
bound the work. It adds no particle entities, chunk requests or server ticks.
Clear carriers and disabled rendering skip the terrain scan entirely. A clear
local biome still needs the periodic scan if another climate has wet weather.

This is an approximation for distant falling precipitation. A coarse cell can
soften a biome or storm edge by several blocks. Clouds retain their existing
rendering; dust storms and stand-alone fog do not gain distant curtains. Sky
colour, immersion fog and ambient sounds continue to follow the observer's
location. Worlds with custom weather renderers, passive weather and unsupported
dimensions keep their existing rendering. Resource packs supply the usual rain
and snow textures.

The local verification report includes native-world sampling timings, offscreen
OpenGL measurements and previews:
`build/verification/distant-weather/RESULT.md`. These measure the added work;
they are not a whole-game FPS benchmark or a full modpack qualification.
