# Living World

An environmental realism engine for Minecraft 1.20.1 (Fabric). It replaces "weather as a toggle" with one
shared simulation — temperature, humidity, wind, precipitation, ground wetness, shelter — and lets every
visual and audible effect read from it, so the world reacts as a whole:

- Walk across a desert at noon: the horizon shimmers, sand streams past your feet, every step leaves a
  blocky, sun-shaded print that the wind slowly erases. At night the same desert is cold and still.
- Water is water: you see the bed through it, bent by the ripples and fading to blue-green with depth;
  it mirrors the trees on the far bank and the clouds overhead, glitters under a low sun, carries
  caustics across the sand and rings under rain. In still air it lies almost smooth under slow ripples;
  wind raises waves that run downwind and sends surf up the beach, where the sand at the waterline stays
  damp. Dive, and the light dances on everything around you.
- The sky is a sky: real, three-dimensional clouds drift with the wind, catch the sunset, thicken before
  rain and tower in a storm, and their shadows cross the land. Night brings a crowd of twinkling stars
  and the Milky Way, cold clear nights the northern lights, and sun on wet air a rainbow.
- The year turns: spring's pale green, hot summers, an autumn of gold and red with leaves coming down,
  and winters cold enough that showers fall as snow and the ground keeps a covering of it. A server can
  go further and let winter lay real snow, freeze the lakes and slow the crops.
- Rain arrives the way weather does: clouds close in, the wind picks up, the first drops fall, it builds.
  The ground darkens where the rain can reach it and stays dry under roofs and trees. Puddles collect,
  leaves drip, dirt turns to mud that takes your footprints. Lightning lights the land and the clouds
  from the place it struck.
- Climb into the snow: your breath shows, flakes fly sideways, a blizzard closes the world down to a few
  dozen blocks, frost creeps over the edge of your view.
- Step into a cave and the storm outside becomes a muffled thrum behind dripping water and echoes.
- Torches, lava and lanterns bloom in the dark, the low sun wears a halo, and the things you drop tumble
  through the air and lie where they land instead of hovering and spinning.

Every effect is drawn or synthesised on the client, and by default nothing in the world is ever modified.
Two rules that a server can switch on are the exceptions: winters that place real snow and ice, and crops
that grow with the seasons (see [Seasonal gameplay](#seasonal-gameplay)).

No shader pack is needed. The water shading, vegetation sway and seasonal foliage colours work on the
vanilla renderer and with Sodium 0.5, whose terrain shader the mod extends to do the same. Under an Iris
shader pack those three are off and the pack is left in charge. See
[docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).

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

- **Seasons:** a season lasts 8 Minecraft days by default and follows the world's day counter (the one
  F3 shows, so sleeping moves the year on). `/livingworld season` tells you where you are in the year;
  see the commands below to jump to a season or change its length. *Climate & Temperature → Calendar*
  puts the season, the day within it and a bar for the year in the corner of the screen.
- **Settings:** press **F9** in-game (rebindable under Controls → Living World), run `/lw config`, or use
  Mod Menu. Fourteen categories; every option has a description.
- **Presets** (Graphics & Performance): *Performance*, *Balanced* (default), *Ultra*, *Cinematic*. They
  are hard budgets for particles, precipitation and footprints, plus which screen-space features run.
- **Survival rules are off by default.** Heat and cold are feedback only. A server (or you, in
  single-player: Climate & Temperature → *Survival temperature rules*) can enable mild hunger drain and
  slowness in extremes. They never deal damage.
- **Accessibility:** one page to remove camera motion, flashes, distortion, heavy fog, dense particles and
  screen overlays.
- **Recorded birds and frogs:** the mod synthesises every sound and ships no recordings, but a resource
  pack can supply them for those two animals. See [docs/SAMPLE_PACK.md](docs/SAMPLE_PACK.md).

### Seasonal gameplay

Two rules reach past looks into the world. Both are **off by default**, both are decided by the server
(or by you, in single-player), and both need seasons to be on.

| Rule | Switch | What it does |
|---|---|---|
| Crops follow the year | `/livingworld season crops on` (`seasonalCrops`) | Crops, melon and pumpkin stems, saplings, sweet berry bushes and cocoa grow 25 % faster in spring, as in vanilla in summer, at 70 % falling to 60 % through autumn and at 20 % in winter. The effect is scaled by how strong the seasons are where the plant stands (weak in deserts, almost absent in jungles), and plants in the dark (sky light below 5: greenhouses, caves) are left alone. |
| Winter changes the world | `/livingworld season snow on` (`seasonalSnowAndIce`) | Where the season makes it cold enough, snow settles while it snows and builds up to `maxSnowLayers` (2 by default), and still water freezes from its banks inward. The thaw takes away what the season placed, and nothing else: snow and ice you put down yourself, and the snow of biomes that are always snowy, are never touched. This is the only setting that makes the mod place and remove blocks. |

`seasonalChangeRate` (16) is how many columns near each player the seasons look at per tick; raise it for
a faster freeze and thaw.

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
| `/livingworld season` | The season and the day within it. |
| `/livingworld season set <spring\|summer\|autumn\|winter>` | Move the calendar to the middle of that season; the year carries on from there. Saved with the world. |
| `/livingworld season length <days>` | How many Minecraft days a season lasts (default 8). |
| `/livingworld season on` / `off` | Switch seasons on or off for everyone. |
| `/livingworld season crops on` / `off` | Crops grow with the seasons (off by default). |
| `/livingworld season snow on` / `off` | Winter lays real snow and ice, and the thaw removes them (off by default). |
| `/livingworld status` | Climate where you stand, as the server computes it. |
| `/livingworld debug <atmosphere\|footprints\|audio\|off>` | Overlay on your own client. |
| `/livingworld benchmark [seconds]` | A/B frame-time measurement on your own client. |
| `/livingworld config` | Open the settings screen. |

`/lw ...` is the client-only twin (`weather`, `temperature`, `wind`, `season`, `debug`, `benchmark`,
`config`, `status`). It works on any server; whatever you set with it is visible only to you.
`/lw season set <season>` gives you a calendar of your own, `/lw season clear` drops it again.

### Configuration files

- `config/livingworld-client.json` — everything in the settings screen. Purely local.
- `config/livingworld-server.json` — gameplay rules: `temperatureEffects`, `exhaustionScale`,
  `extremeSlowness`, `syncForecast`, `stormFrequency` (how often real storms occur relative to vanilla),
  `seasons`, `seasonLengthDays`, `seasonalCrops`, `seasonalSnowAndIce`, `maxSnowLayers`,
  `seasonalChangeRate`, `commandPermissionLevel`.

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
       grade, haze  rain/snow/sand splashes, leaves, + DecalStore shiver, lens    thunder, cave echo
            │                      dust, embers…    prints, tracks
            ▼
       PostProcessor (atmosphere pass: sky, clouds, wet ground + bloom) · SkyAndFog (vanilla fog hooks)
       VegetationWind (GPU sway, seasonal colours) · WaterShading (GPU water) · ItemPhysics (dropped items)
```

Design decisions worth knowing:

- **One state, many readers.** No effect looks at the world on its own. Rain intensity, the wind
  vector, wetness and shelter are computed once per tick in `ClientEnvironment`; that is what makes
  "wind + desert = blowing sand that erases prints" fall out of the model instead of being a special case.
- **Deterministic wind and weather intensity.** Both are pure functions of the synchronised world time
  and a per-dimension salt, so every player sees the same gust at the same moment with zero packets,
  even on a vanilla server.
- **Nothing is aimed along the wind.** A pattern that is re-aimed whenever the wind's direction wobbles
  swings around the world origin: a degree of wobble moves water a thousand blocks out by seventeen
  blocks. So waves have fixed headings (the wind only decides which of them grow), and everything that
  travels with the wind — ripples, gusts over grass, clouds — is moved by a running total of how far the
  air has gone, kept in `ClientEnvironment`. Still air means still water.
- **A calendar with nothing to store.** The season is a pure function of the world's day counter, which
  the server already synchronises, so every client agrees on it with no packets. The only saved state is
  the calendar shift set by `/livingworld season set`.
- **Seasons without rebuilding a chunk.** Foliage is recoloured in the terrain vertex shader from the
  biome tint the game already baked into each vertex, so the year turns smoothly and for free.
- **One cloud field.** The clouds you see, the shadows on the ground and the mirror images in water and
  puddles all read the same function (`lw_sky.glsl`), so they always belong together.
- **Clouds in slices, not steps.** The cloud layer is sampled where each view ray crosses a stack of
  horizontal slices that stay put in the world, instead of at steps along the ray. Stepping needs
  per-pixel dither to hide its banding, and without temporal smoothing dither shows as grain; slices
  give a smooth picture from the same number of samples.
- **Clouds at half resolution where it does not show.** On Performance and Balanced the cloud layer for
  open sky is drawn into a buffer a quarter of the screen's size and enlarged; clouds are soft, so this
  costs a quarter as much and looks the same. Clouds in front of terrain are still worked out per pixel.
- **Lightning is a light with a position.** A stroke lights the terrain that faces it, less with
  distance, and the cloud above it from inside, instead of flashing the whole screen evenly.
- **Sodium without replacing Sodium.** Sodium draws terrain with its own shader and has no spare vertex
  attribute. The mod writes a one-byte tag into the unused high byte of each vertex's light coordinate
  while Sodium meshes a chunk (plant and how far up it, water, facing the sky), adds a few lines to
  Sodium's terrain shader as it is loaded, and hands that shader the same per-frame values the vanilla
  overrides get. The water, sway and season code itself is one set of GLSL files used by both. If a
  line the patch hangs on is missing (another Sodium version), the patch is not applied and Sodium is
  left exactly as it was.
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
| `src/client/resources/assets/minecraft/shaders/core` | Overrides of two vanilla terrain shaders: cut-out (vegetation sway, seasonal colours) and translucent (water) |
| `src/client/resources/assets/minecraft/shaders/include` | The shape of the water surface and of the cloud field, and the water, cloud and foliage shading, shared by the shaders that need them (the vanilla overrides, the atmosphere pass and the patched Sodium shader) |
| `src/client/java/dev/livingworld/client/compat/sodium` | The Sodium bridge: shader patch, vertex tags, per-frame values |
| `src/main/java/dev/livingworld/server` | Server sync, commands, and the two seasonal gameplay rules (`SeasonalGrowth`, `SeasonalWorld`) |
| `docs/sample-pack-template` | A resource pack to fill with recorded birds and frogs |
| `marketing` | Listing pages and artwork for CurseForge and Modrinth, and how they were made |
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
`dev.livingworld.client.audio.synth` — there are no audio files in the jar. A resource pack can add
recordings for the birds and frogs ([docs/SAMPLE_PACK.md](docs/SAMPLE_PACK.md)); none are provided.

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
| `gradlew runAutotest -PlwScenes=marketing` | Captures the listing screenshots and clips (see [marketing/README.md](marketing/README.md)) |
| `gradlew generateAssets` | Regenerate the textures |

## Testing

**Unit tests** (35) cover the parts that are pure logic: the temperature model, wind field, weather
intensity, noise, the sync packet, the calendar (the order of the seasons, where a new world starts in
the year, which looks belong to which season, jumping to a season), how fast crops grow in each season,
at what temperatures winter freezes and thaws, the decal store's budget/ageing/eviction rules, the
synthesiser (silence at zero, no clipping, indoor muffling), and the patch applied to Sodium's shader
(that it goes in at the right places, and that an unfamiliar shader is left alone).

**In-game scene run.** Visual effects cannot be asserted from a unit test, so `gradlew runAutotest`
boots a real client, creates a throw-away world, teleports to each biome, sets conditions with the
mod's own commands, physically walks the player around, and writes screenshots plus a report (state of
every system, GL errors, pass/fail expectations) to `run/livingworld-autotest/`. Scenes: settings
screen, desert (noon, footprints, sandstorm, night), snowy plains (footprints, creature tracks,
blizzard), plains (a field of grass in strong wind), forest (morning mist, wind, heavy rain, mud,
thunderstorm with a real lightning strike, after-rain), jungle, swamp at night, cave under a storm,
Nether, End, Ultra preset at sunrise, the newer visuals (dropped items, lamps at night, a pond at noon,
sunset, moonrise and in wind, the view under water), and a 1080p benchmark. Five more scenes cover the
water, sky and seasons work: `water` (a pond in a dead calm and in a gale, and a natural shoreline in a
breeze with the shoreline effects on and off), `sky` (the cloud layer at noon, its shadows on the ground
switched on and off, sunset, the night sky, a storm, a stroke of lightning at dusk that must be a light
at the place it struck, a rainbow and the northern lights), `seasons` (a forest through the four seasons
and a winter shower), `realwinter` (the opt-in rule: winter must place snow and ice of its own, and the
thaw must take nearly all of it away and leave the player's own snow and ice alone) and `puddles` (rain
on stepped ground of several materials). The window it opens takes keyboard focus while it runs; the run
releases the mouse cursor every tick, so moving the mouse does not turn the view.

**Frame sequences.** A still shows that an effect is drawn; only a run of frames shows that it moves the
way it should (water that races in a calm looks fine in any single frame). A scene can therefore record a
clip (`AutoTest.clip`), which saves the frames to `run/livingworld-autotest/clips/<name>/` and measures
two things: how much of the picture changes from one frame to the next, and how much changes and then
changes straight back, which is flicker. The `water` scene requires still water to change less than 8%
per frame and a gale more than 12%; the `seasons` scene fails if more than 2% of a view of leaves in a
breeze flickers. It does not say whether motion looks right, only whether there is any and whether it
flickers.

`gradlew runAutotest -PlwScenes=showcase` runs a ten-minute cinematic tour instead, at 1080p on the
Ultra preset with sound on, and saves a screenshot of every moment as `sc_*.png`.

Last results (NVIDIA RTX 3050):

- **Vanilla renderer, every scene:** completed with no OpenGL errors, and 49 of 50 expectations passed.
  The one that failed, "the terrain column cache follows terrain edits", depends on where a teleport
  lands: the test pond is dug 40 blocks east of wherever the desert scene put the player, and in this run
  that was in the sea. The other check of that kind, "jungle air is humid" (it fails when the jungle
  teleport comes down on its beach), passed this time. The version of the mod from before the water, sky
  and seasons work fails checks in the same scenes when run here, so these are weaknesses of the tests,
  not of what they test. For the same reason the newer scenes run last: each scene starts from where the
  previous one ended, so a scene added in the middle moves every later one.
- **Frame sequences** across the three runs listed here: still water changed 5.7% to 6.3% per frame (the
  pond's banks have surf and the desert air shimmers), water in a gale 27% to 78% depending on the view,
  and leaves in a breeze flickered over 0.00% to 0.04% of the picture.
- **Sodium 0.5.11** (`-PlwCompat=sodium -PlwScenes=plains,water,seasons`): every expectation passed, the
  bridge patched both stages of Sodium's shader, and the screenshots show the same see-through water,
  leaning grass and seasonal colours as the vanilla renderer's.
- **Sodium 0.5.11 + Iris 1.7.2**, no shader pack, the same three scenes: every expectation passed, with
  the same pictures.

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

**The table above predates the cloud layer, the cloud shadows, puddle reflections and the present wave
model, and has not been measured again on that chip.** Expect the clouds in particular to add to it
there (16 slices through the layer on Balanced, drawn at half resolution over open sky). The same
benchmark on a second machine, with everything in this version:

- **Hardware:** NVIDIA GeForce RTX 3050, 12-thread AMD CPU.
- **Settings:** a window 1920 wide and 1009 to 1061 high, render distance 8, Fancy graphics, vanilla renderer, *Balanced* preset.

| Scene | Effects on: avg (1% low) | Effects off: avg (1% low) | Cost per frame |
|---|---|---|---|
| Forest, clear midday | 374 fps (210) | 593 fps (65) | 1.0 ms |
| Forest, thunderstorm | 392 fps (232) | 679 fps (348) | 1.1 ms |
| Snowy plains, blizzard | 686 fps (328) | 1398 fps (509) | 0.7 ms |
| Desert, sandstorm | 549 fps (273) | 1253 fps (458) | 1.0 ms |
| Desert pond, clear midday (water over the lower half of the view) | 406 fps (250) | 1104 fps (476) | 1.6 ms |

One run of 16 seconds each way per scene, so read the costs as "about a millisecond", not to the
decimal. Water is still the dearest thing on screen.

If you need frames back: *Fog & Atmosphere → Volumetric clouds* off returns the game's own clouds;
*Water Effects → Water reflections* and *Graphics → Bloom* are the cheapest other things to give up; *Compatibility → Atmosphere pass* off removes the largest fixed cost (you keep
precipitation, footprints, particles, audio and vanilla-fog storms); the *Performance* preset cuts
particle and precipitation budgets, skips bloom, reflections and the screen-space wetness, and draws the
cloud layer with half as many slices; *Adaptive
quality* sheds density automatically when you fall below your target frame rate.

Optimisation notes: one draw call for all precipitation and particles and one for all decals;
allocation-free particle and decal stores with hard caps; stateless precipitation (no per-drop
simulation); a 64×64 terrain column cache refreshed incrementally instead of chunk scans; biome sampling
at 2 Hz and heat-source scans at 1 Hz; all per-frame uniforms computed once per tick.

## What has and has not been verified

Verified in a running client, by report, still screenshot and (for water, surf and leaves) frame
sequences: every scene above on the vanilla renderer, and the `plains`, `water` and `seasons` scenes with
Sodium 0.5.11 and with Sodium 0.5.11 + Iris 1.7.2 (no shader pack). The listing screenshots under
`marketing/images/screenshots` are a second look at most of it at 1920×1080 on the Ultra preset.

Not verified, and you should check before relying on it:

- **Sound.** The synthesiser was validated by measurement (levels, spectra, clipping, CPU), never by
  ear. Expect to want to adjust volumes; the synthetic birds and frogs are the likeliest to disappoint.
  The hook for recorded birds and frogs has not been run with a pack of real recordings.
- **How motion looks.** The frame sequences show that still water is nearly still, that a gale moves it,
  and that leaves in a breeze do not flicker. Whether sway, shimmer, gusts, surf, drifting clouds, the
  northern lights and camera feedback *feel* right is for a person to judge.
- **A whole year in real time.** The seasons were looked at by jumping the calendar, not by playing
  through 32 days. The winter rule was run for a minute with its rate turned up sixteen-fold, and the
  crop rule only in unit tests: no field has been grown through a year.
- **Sodium beyond three scenes and one version.** The other scenes were last run with Sodium before the
  water, sky and season work. It is not known whether Sodium's own cloud renderer is switched off while
  the mod draws its cloud layer; nothing doubled up in the three scenes, but if you see both, turn
  *Volumetric clouds* off.
- **Multiplayer with real remote players** and **a dedicated server launch** (starting one means
  accepting Mojang's EULA, which is yours to do).
- **Shader packs.** Iris was tested installed but without a pack loaded.
- **Fabulous graphics**, **Fast graphics under Sodium** and **Mod Menu**. Everything since the water work
  was only run on an NVIDIA RTX 3050; everything older than that only on the integrated chip above.
- **Rain rings on water** are implemented, but in the screenshots of rain over a lake they cannot be made
  out. **Rainbows** were seen once, faintly, in a staged scene.
- **Half-resolution reflections** were asked for and not done: water is shaded while the terrain is
  drawn, so there is no reflection pass to shrink. Only the cloud layer has a half-resolution buffer.

## Licence

MIT — see [LICENSE](LICENSE).
