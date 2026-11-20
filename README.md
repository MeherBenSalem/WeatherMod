# Living World

An environmental realism engine for Minecraft 1.20.1 (Fabric). It replaces "weather as a toggle" with one
shared simulation — temperature, humidity, wind, precipitation, ground wetness, shelter — and lets every
visual and audible effect read from it, so the world reacts as a whole:

- Walk across a desert at noon: the horizon shimmers, sand streams past your feet, every step leaves a
  blocky, sun-shaded print that the wind slowly erases. At night the same desert is cold and still.
- Water is water: you see the bed through it, bent by the ripples and fading to blue-green with depth;
  it mirrors the trees on the far bank, glitters under a low sun, carries caustics across the sand and
  rings under rain. Dive, and the light dances on everything around you.
- Rain arrives the way weather does: clouds close in, the wind picks up, the first drops fall, it builds.
  The ground darkens where the rain can reach it and stays dry under roofs and trees. Puddles collect,
  leaves drip, dirt turns to mud that takes your footprints.
- Climb into the snow: your breath shows, flakes fly sideways, a blizzard closes the world down to a few
  dozen blocks, frost creeps over the edge of your view.
- Step into a cave and the storm outside becomes a muffled thrum behind dripping water and echoes.
- Torches, lava and lanterns bloom in the dark, the low sun wears a halo, and the things you drop tumble
  through the air and lie where they land instead of hovering and spinning.

Nothing in the world is ever modified. Every effect is drawn or synthesised on the client.

No shader pack is needed. Two features ride on the vanilla terrain shaders and are therefore inactive
with Sodium or under an Iris shader pack: the water shading and vegetation sway. Everything else works
with them — see [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

> Status: a complete, buildable first release. What is done, partial and missing is listed honestly in
> [docs/FEATURES.md](docs/FEATURES.md). Read "What has and has not been verified" below before judging it.

## Requirements

| | |
|---|---|
| Minecraft | 1.20.1 |
| Loader | Fabric Loader 0.15 or newer |
| Required | Fabric API |
| Optional | Mod Menu (adds a settings button) |
| Java | 17 |

Why 1.20.1 on Fabric: it is the most widely modded modern version, it runs on Java 17, and Fabric's
rendering, sound and model-loading APIs give clean hooks for everything this mod needs without
replacing the renderer.

## Install

Build the jar (see [Building](#building); it is written to `build/libs/livingworld-1.0.0.jar`) and drop
it and Fabric API into `mods/`. It can be installed on the client only (works on any server, including
vanilla), on both sides, or on a server only — see [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

## Using it

- **Settings:** press **F9** in-game (rebindable under Controls → Living World), run `/lw config`, or use
  Mod Menu. Fourteen categories; every option has a description.
- **Presets** (Graphics & Performance): *Performance*, *Balanced* (default), *Ultra*, *Cinematic*. They
  are hard budgets for particles, precipitation and footprints, plus which screen-space features run.
- **Survival rules are off by default.** Heat and cold are feedback only. A server (or you, in
  single-player: Climate & Temperature → *Survival temperature rules*) can enable mild hunger drain and
  slowness in extremes. They never deal damage.
- **Accessibility:** one page to remove camera motion, flashes, distortion, heavy fog, dense particles and
  screen overlays.

### Commands

`/livingworld ...` needs the mod on the server (or single-player). Weather, temperature and wind
sub-commands need permission level 2 and affect everyone in the dimension.

| Command | Effect |
|---|---|
| `/livingworld weather <type> [intensity] [seconds]` | **Simulated** weather: `drizzle`, `rain`, `heavy_rain`, `torrential`, `thunderstorm`, `extreme_storm`, `snow`, `blizzard`, `sandstorm`, `clear_sky`. Changes what Living World renders for everyone in the dimension. The real Minecraft weather is not touched. |
| `/livingworld weather clear` | End the simulation and follow the real weather again. |
| `/livingworld realweather <clear\|rain\|thunder> [seconds]` | Change the **real** world weather, like `/weather`. |
| `/livingworld temperature set <°C>` / `clear` | Simulated ambient temperature. |
| `/livingworld wind set <0..1.6>` / `clear` | Simulated sustained wind (0 calm, 1 gale). |
| `/livingworld status` | Climate where you stand, as the server computes it. |
| `/livingworld debug <atmosphere\|footprints\|audio\|off>` | Overlay on your own client. |
| `/livingworld benchmark [seconds]` | A/B frame-time measurement on your own client. |
| `/livingworld config` | Open the settings screen. |

`/lw ...` is the client-only twin (`weather`, `temperature`, `wind`, `debug`, `benchmark`, `config`,
`status`). It works on any server; whatever you set with it is visible only to you.

### Configuration files

- `config/livingworld-client.json` — everything in the settings screen. Purely local.
- `config/livingworld-server.json` — gameplay rules: `temperatureEffects`, `exhaustionScale`,
  `extremeSlowness`, `syncForecast`, `stormFrequency` (how often real storms occur relative to vanilla),
  `commandPermissionLevel`.

## How it works

```
                      vanilla world state (time, weather gradients, biomes, light, blocks)
                                              │
        server (optional)                     ▼
  ServerClimateSynchronizer ──packet──▶  ClientEnvironment  ◀── ColumnCache, ShelterProbe
  (test overrides, forecast,             one smoothed description of "here, now":
   survival switch)                      biome blend · temperature · wind field · precipitation
                                         · sand · clouds · ground wetness · shelter · light
                                              │
            ┌─────────────┬─────────────┬─────┴───────┬──────────────┬──────────────┐
            ▼             ▼             ▼             ▼              ▼              ▼
       Atmosphere   Precipitation  ParticleEngine  Footprint     PlayerExposure  EnvironmentAudio
       fog, mist,   Renderer       + AmbientSpawner Tracker       breath, frost,  synthesised beds,
       grade, haze  rain/snow/sand splashes, leaves, + DecalStore shiver, lens    footsteps, thunder
            │                      dust, embers…    prints, tracks
            ▼
       PostProcessor (atmosphere pass + bloom) · SkyAndFog (vanilla fog hooks)
       VegetationWind (GPU sway) · WaterShading (GPU water) · ItemPhysics (dropped items)
```

Design decisions worth knowing:

- **One state, many readers.** No effect looks at the world on its own. Rain intensity, the wind
  vector, wetness and shelter are computed once per tick in `ClientEnvironment`; that is what makes
  "wind + desert = blowing sand that erases prints" fall out of the model instead of being a special case.
- **Deterministic wind and weather intensity.** Both are pure functions of the synchronised world time
  and a per-dimension salt, so every player sees the same gust at the same moment with zero packets,
  even on a vanilla server.
- **Stateless precipitation.** A raindrop is a function of its index and two accumulated offsets (how
  far the rain has fallen, how far the wind has carried it). Thousands of streaks cost one hash and one
  quad each, lean with the wind, and are hidden under whatever would have stopped them.
- **A terrain column cache** (64×64, wrap-around) answers "where does rain land here and on what" for
  rendering, splashes, audio and the screen-space wetness pass, without scanning chunks.
- **Water without touching water.** While chunks are meshed, water vertices get a vertex alpha of
  250/255 (vanilla always writes 255). Just before the translucent terrain layer the frame so far is
  copied, and an override of the vanilla translucent shader uses that copy: it looks through the surface
  at the bed (refraction, absorption by depth), marches the mirrored ray through the copied depth for
  reflections, and adds glitter, caustics, rain rings and a soft shoreline. Glass and ice in the same
  layer take the untouched vanilla path.
- **Blocky where the game is blocky.** Footprints are pixel art on the block textures' own texel grid;
  water, light and air are smooth, like the sky above them.
- **Hard budgets.** Particles live in fixed primitive arrays, decals in a bounded store, drops in a
  per-preset count. A storm cannot allocate or spawn its way into a stutter; under load an adaptive
  scale sheds density first.
- **Server safety by construction.** Client code lives in a separate source set that common code cannot
  reference at compile time.

Source layout:

| Path | Contents |
|---|---|
| `src/main/java/dev/livingworld` | Common: climate maths, wind field, weather model, biome and surface registries, server sync, commands, survival rules, API |
| `src/client/java/dev/livingworld/client` | Environment state, atmosphere, renderers, particles, footprints, audio, settings screen, debug tools, mixins |
| `src/client/resources/assets/livingworld` | Shaders, textures, language file, `sounds.json` |
| `src/client/resources/assets/minecraft/shaders/core` | Overrides of two vanilla terrain shaders: cut-out (vegetation sway) and translucent (water) |
| `src/assetgen` | The program that generates every texture |
| `src/test` | Unit tests |

### For other mods

```java
LivingWorldApi.registerBiome(MyBiomes.SALT_FLATS,
        BiomeProfile.Kind.DESERT.profile().withDustiness(0.6f).withWindiness(1.3f));
LivingWorldApi.registerSurface(MyBlocks.VOLCANIC_ASH, SurfaceMaterial.ASH);
```

Unregistered biomes and blocks are classified automatically (tags, temperature, precipitation, step sound).

## Assets

There are no third-party assets. Every texture is generated by `src/assetgen` (`gradlew generateAssets`
regenerates them) and every sound is synthesised at runtime by
`dev.livingworld.client.audio.synth` — there are no audio files in the jar.

## Building

```bash
./gradlew build
```

The jar is written to `build/libs/`. Gradle needs a JDK 17.

| Task | What it does |
|---|---|
| `gradlew build` | Compile, run the unit tests, package the jar |
| `gradlew test` | Unit tests only |
| `gradlew runClient` | Dev client |
| `gradlew runAutotest [-PlwScenes=desert,snow]` | Scripted in-game scene run (see Testing) |
| `gradlew runAutotest -PlwCompat=sodium[,iris]` | The same with Sodium (and Iris) added to the dev client |
| `gradlew generateAssets` | Regenerate the textures |

## Testing

**Unit tests** (26) cover the parts that are pure logic: the temperature model, wind field, weather
intensity, noise, the sync packet, the decal store's budget/ageing/eviction rules, and the synthesiser
(silence at zero, no clipping, indoor muffling, footstep variety).

**In-game scene run.** Visual effects cannot be asserted from a unit test, so `gradlew runAutotest`
boots a real client, creates a throw-away world, teleports to each biome, sets conditions with the
mod's own commands, physically walks the player around, and writes screenshots plus a report (state of
every system, GL errors, pass/fail expectations) to `run/livingworld-autotest/`. Scenes: settings
screen, desert (noon, footprints, sandstorm, night), snowy plains (footprints, creature tracks,
blizzard), plains (a field of grass in strong wind), forest (morning mist, wind, heavy rain, mud,
thunderstorm with a real lightning strike, after-rain), jungle, swamp at night, cave under a storm,
Nether, End, Ultra preset at sunrise, the newer visuals (dropped items, lamps at night, a pond at noon,
sunset, moonrise and in wind, the view under water), and a 1080p benchmark. The window it opens takes
keyboard focus while it runs.

`gradlew runAutotest -PlwScenes=showcase` runs a ten-minute cinematic tour instead, at 1080p on the
Ultra preset with sound on, and saves a screenshot of every moment as `sc_*.png`.

Last results on the development machine: every scene completed with no OpenGL errors and every
expectation passing, both on the vanilla renderer and with Sodium 0.5.11 + Iris 1.7.2 installed (no
shader pack; desert, snow and jungle scenes).

## Performance

Measured with the in-game A/B benchmark (`/livingworld benchmark`, 16 s per run): frame times with the
mod's effects on, then with the master switch off, same place and camera.

- **Hardware:** Intel Core i7-13700 with its **integrated Intel UHD Graphics 770** (driver 32.0.101.7088) — no discrete GPU.
- **Settings:** 1920×1061 window, render distance 8, Fancy graphics, vanilla renderer, *Balanced* preset, VSync off.

| Scene | Effects on: avg (1% low) | Effects off: avg (1% low) | Cost per frame |
|---|---|---|---|
| Forest, clear midday | 102 fps (49) | 170 fps (71) | 4.0 ms |
| Forest, thunderstorm | 91 fps (52) | 174 fps (74) | 5.3 ms |
| Snowy plains, blizzard | 118 fps (69) | 318 fps (111) | 5.3 ms |
| Desert, sandstorm | 101 fps (62) | 249 fps (103) | 5.9 ms |
| Desert pond, clear midday (water over the lower half of the view) | 74 fps (31) | 307 fps (80) | 10.3 ms |

How to read this:

- "Effects off" is plain vanilla in clear weather (the storms are simulated by the mod), so the storm
  rows compare against a cheaper frame than a vanilla rainstorm would be.
- Most of the cost is GPU work on this integrated chip. The clear-weather row (4.0 ms) is essentially the
  full-screen atmosphere pass, bloom (four quarter-resolution passes) and vegetation sway; storms add
  fill from precipitation and, in the sandstorm, large soft dust sprites. GPU time per pass was not
  measured separately — this split is inferred from the CPU timers, which are small: under 0.4 ms per
  tick for all simulation, and 0.5–0.8 ms per frame to build up to 2,000 precipitation and particle quads.
- **Water is the most expensive thing the mod draws.** Every water pixel refracts, tints by depth and
  marches a reflection ray, so the cost grows with how much of the screen is water — the pond row is
  close to a worst case for this chip. *Water Effects → Water reflections* off (or the Performance
  preset) removes the ray march; *Realistic water* off removes all of it.
- The rows without water averaged 90 fps or more with 1% lows of 49–69 fps (vanilla's own were 71–111).
  That is one machine and one render distance, not a guarantee. A discrete GPU should make all of the
  screen-space work a much smaller share; that has not been measured.

If you need frames back: *Water Effects → Water reflections* and *Graphics → Bloom* are the cheapest
things to give up; *Compatibility → Atmosphere pass* off removes the largest fixed cost (you keep
precipitation, footprints, particles, audio and vanilla-fog storms); the *Performance* preset cuts
particle and precipitation budgets and skips bloom, reflections and the screen-space wetness; *Adaptive
quality* sheds density automatically when you fall below your target frame rate.

Optimisation notes: one draw call for all precipitation and particles and one for all decals;
allocation-free particle and decal stores with hard caps; stateless precipitation (no per-drop
simulation); a 64×64 terrain column cache refreshed incrementally instead of chunk scans; biome sampling
at 2 Hz and heat-source scans at 1 Hz; all per-frame uniforms computed once per tick.

## What has and has not been verified

Verified in a running client on the development machine: every scene above, by report and by still
screenshot. The Sodium + Iris run predates the water shading, bloom, film look and item physics; those
were only run on the vanilla renderer.

Not verified, and you should check before relying on it:

- **Sound.** The synthesiser was validated by measurement (levels, spectra, clipping, CPU), never by
  ear. Expect to want to adjust volumes; the synthetic birds and frogs are the likeliest to disappoint.
- **Motion.** Screenshots do not show whether sway, shimmer, gusts and camera feedback *feel* right.
- **Multiplayer with real remote players** and **a dedicated server launch** (starting one means
  accepting Mojang's EULA, which is yours to do).
- **Shader packs.** Iris was tested installed but without a pack loaded.
- **Any GPU other than the integrated one above**, Fabulous graphics, and Mod Menu.
- **Rain rings on water** are implemented but were not looked at in a screenshot; the still-water
  surface, reflections, glitter, caustics, a waterfall and the underwater view were.

## Licence

MIT — see [LICENSE](LICENSE).
