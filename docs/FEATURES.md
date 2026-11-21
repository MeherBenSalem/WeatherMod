# Living World — feature checklist

Status of every item in the original brief. The rule used here: a feature is **Done** only if its
behaviour is implemented, connected to the game and was exercised in a running client (the automated
scene run described in the README under "Testing"). **Partial** means a real implementation that covers less than the
brief asked for — the gap is stated. **Not implemented** means exactly that.

Things nobody could verify from this environment are called out separately at the end.

Legend: ✅ Done · 🟡 Partial · ❌ Not implemented

## 1. Climate and temperature

| Feature | Status | Notes |
|---|---|---|
| Temperature from biome, time of day, altitude, weather, sun exposure, heat sources, water/wetness, wind, humidity, shelter | ✅ | `ClimateMath` + `ClimateSampler`; identical on client and server. |
| Smooth transitions between biomes | ✅ | 13-point weighted biome stencil, then time smoothing. Every derived effect inherits it. |
| Heat shimmer that scales with temperature, weaker in shade/at night | ✅ | Screen-space refraction in the atmosphere pass; masked so near objects are not dragged. |
| Warm colour cast, dry haze, dust lifting, blowing sand | ✅ | |
| Dust devils | ✅ | One wandering whirl at a time on hot, dry, calm afternoons. Spawn logic verified, not caught in a screenshot. |
| Heat feedback on the player (camera sway, sweat) | 🟡 | Slow camera sway and vignette done. Sweat is a third-person droplet only. No exhaustion *animation* of the player model. |
| Visible breath, scaled by temperature and activity; other players too | ✅ | |
| Frost on the lens (optional, subtle) | ✅ | Grows in from the edges with cold, melts as you warm up. |
| Shivering | 🟡 | Camera shiver in bursts. No shiver animation on the third-person model. |
| Wind-driven snow, ice crystals, powder displaced by movement, freezing fog | ✅ | |
| Clothing / exposed-body visual feedback | ❌ | Not attempted. |
| Snow crunch, frozen ambience, ice cracking audio | ✅ | Synthesised. |
| Wind chill | ✅ | In the feels-like model; drives frost, shiver and (optionally) survival rules. |
| Optional survival temperature rules, fully separable from visuals | ✅ | Server-side, off by default, never deals damage. |

## 1b. Seasons

Not part of the original brief. A season lasts 8 Minecraft days by default; the calendar is a pure
function of the world's day counter (see `Seasons`), so clients agree on it with no packets.

| Feature | Status | Notes |
|---|---|---|
| Four seasons from the world's day counter, configurable length, commands to jump to a season | ✅ | `/livingworld season ...`; the calendar shift is saved with the world. `/time set` moves the calendar, because it moves the day counter. |
| Seasonal temperature | ✅ | Up to 7 °C warmer in midsummer and 17 °C colder in midwinter where seasons are strongest, scaled per biome family (deserts have weak seasons, jungles almost none, caves and other dimensions none). Feeds everything temperature already drove: breath, frost, wildlife, the optional survival rules. |
| Winter showers fall as snow | ✅ | Decided by the air temperature in the cold part of the year. Never the reverse: biomes where the world keeps snow on the ground keep snowing. |
| Snow lying through the winter | 🟡 | The screen-space snow cover stays between snowfalls on cold ground, and grass and leaves are whitened at any distance. Other distant surfaces (stone, roofs, sand) only whiten within about 40 blocks. This is a look only; real snow and ice are the next row. |
| Real snow build-up and freezing lakes | ✅ | Opt-in, off by default (`/livingworld season snow on`). Where the season brings the mean temperature below 3 °C, snow layers settle while it snows and build up to `maxSnowLayers` (2); below 0 °C still water that touches a bank or other ice freezes, so lakes close from the edges inward. Above 5 °C the thaw removes them again, a layer at a time. Dry biomes get neither, light sources (block light 10 or more) keep both away, and biomes where the game itself snows are left to the game. Every block placed is recorded, and only recorded blocks are ever removed: the player's own snow and ice and the snow of always-snowy biomes are left alone (checked in the test run). The record holds 400,000 blocks; past that, winter stops placing more. Works near players only (within 64 blocks), a few columns per tick, on the topmost block of each column, so flowing water and water under a roof do not freeze. |
| Foliage and grass change colour | 🟡 | Spring green with the odd tree in blossom, autumn gold/orange/red tree by tree, brown and bleached in winter; conifers stay green. Done in the terrain vertex shader: on the vanilla renderer and with Sodium 0.5, **not under a shader pack**; on the vanilla renderer leaves only change on Fancy graphics. Trees do not lose their leaves, they only look bare. Dry-biome grass (savanna, badlands, swamp) is left as it is. |
| More leaves fall in autumn, in the colours the trees have turned | ✅ | |
| Seasonal crop growth | ✅ | Opt-in, off by default (`/livingworld season crops on`). Crops, stems, saplings, sweet berry bushes and cocoa grow at 125 % of the vanilla rate in spring, 100 % in summer, 70 % falling to 60 % in autumn and 20 % in winter, scaled by how strong the seasons are in that biome. Plants with less than 5 sky light (indoors, underground) are left alone. It works by skipping or repeating the block's own random tick, so modded crops that extend the vanilla crop classes follow it and others do not. The rates are unit-tested; a field has not been grown through a year in the game. |
| Calendar on the HUD | ✅ | Optional (*Climate & Temperature → Calendar*): the season, the day within it and a bar for the year, top left. |
| Animal behaviour by season | ❌ | |

## 2. Footprints and terrain interaction

| Feature | Status | Notes |
|---|---|---|
| Oriented left/right footprints in sand | ✅ | Blocky pixel-art prints at the block textures' own density (16 texels per block), snapped to the texel grid and turned in 45° steps, alternating left and right of the walking line. Each texel is shaded against the real sun/moon direction and prints wear away texel by texel. The left and right print share one symmetric sprite. |
| Sprint / sneak / landing variations | ✅ | Different sprite, depth, stride; landing leaves a crater. |
| Sand grains thrown by steps | ✅ | |
| Wind, rain, snowfall and sandstorms erase prints | ✅ | Per-material sensitivities; only exposed prints are affected. |
| Snow prints (deeper in deeper snow), powder bursts, snow thrown when running | ✅ | |
| Mud prints, mud splashes, stronger in rain | ✅ | Dirt and grass turn muddy once the ground is soaked. |
| Wet prints on hard floors after swimming / rain | ✅ | Fade as feet dry, faster in heat. |
| Grass flattens / bends around footsteps | 🟡 | A subtle flattened decal, plus plants bending away from the *local player* on the GPU. Mobs do not bend grass. |
| Dry dirt dust vs. wet mud | ✅ | |
| Creature tracks (hoof, paw, bird, heavy, horseshoe, small) | ✅ | Chosen by entity class; modded mobs fall back by size. |
| Density limits, distance culling, batching, bounded memory | ✅ | Fixed arrays, one draw call, eviction of the most worn. Unit-tested. |
| No world modification | ✅ | Purely client-side geometry. |
| Multiplayer consistency | 🟡 | Derived locally from entity motion, so every client sees prints for every creature — but two clients do not see pixel-identical prints. Not tested with real remote players. |

## 3. Weather

| Feature | Status | Notes |
|---|---|---|
| Six rain intensities from drizzle to extreme storm | ✅ | A continuous intensity; the named levels are thresholds on it. |
| Rain slanted and gusting with the wind | ✅ | |
| Splashes at impact, material-specific | ✅ | Hard, soft, leaves, water differ. Splashes are statistical, not tied to individual streaks. |
| Ripples where rain hits water | ✅ | |
| Leaves drip during and after storms | ✅ | |
| Roofs block rain; canopies let a little through | ✅ | Via the terrain column cache. |
| Wet terrain darkens | ✅ | Screen-space, only on surfaces the rain can reach, soft edge under cover. Up-facing surfaces only (walls stay dry). |
| Stone/dirt/wood/sand/leaves react differently | 🟡 | Different splashes, puddle capacity and audio. The *darkening* itself is the same for all materials. |
| Puddles that form, ripple, react, and evaporate | 🟡 | Screen-space puddles with rain ripples; they grow and evaporate. They mirror the sky with its clouds and, on Balanced and up, whatever on screen stands beside them. Each level of the ground has its own puddles, they stop short of a drop to lower ground, and their outline follows a change of material smoothly instead of the block grid. Walking through one makes a splash and ripple particle, the puddle itself does not deform. |
| Heavy rain reduces visibility, rain haze toward the horizon | ✅ | |
| Rain audio changes with the surface | ✅ | Mix of leaves / stone / water / wood / soft from the surfaces around you. |
| Thunderstorm: dark cloud deck, wind gusts, dense rain | ✅ | |
| Distance-aware lightning flash, delayed thunder | ✅ | Vanilla bolts are delayed and dulled by distance; distant sheet lightning is added. |
| Lightning illuminating the environment | 🟡 | A stroke is a light at the place it struck: terrain that faces it brightens, less with distance, and the cloud above it glows from inside. Distant sheet lightning has a bearing too. It is added in the atmosphere pass, so it is not a real light source: nothing casts a shadow, and block and entity lighting do not change. Seen in one staged scene (a stroke 70 blocks off at dusk). |
| Storm progression (clouds gather first, rain builds) | ✅ | With the mod on the server, clouds start gathering up to two minutes before vanilla rain. |
| Post-storm mist, lingering drips | ✅ | |
| Blizzards: horizontal snow, depth, whiteout, wind audio | ✅ | |
| Snow swirling around obstacles | ❌ | Flakes do not react to geometry beyond being hidden under cover. |
| Snow accumulation visuals | 🟡 | Screen-space whitening of exposed surfaces during snowfall; no geometry. |
| Sandstorms: dust wall, low visibility, sand streaks, ground-level streaming, audio, gradual onset | ✅ | |
| Sand gathering around obstacles | ❌ | |
| Sandstorm intensity levels | ✅ | Continuous; `/livingworld weather sandstorm <intensity>`. |

## 4. Wind

| Feature | Status | Notes |
|---|---|---|
| Global direction, base speed, gusts, smooth variation | ✅ | Deterministic in world time, so all clients agree with zero packets. |
| Biome, altitude and storm influence; shelter approximation | ✅ | |
| Affects rain, snow, sand, particles, leaves, mist, audio, footprints | ✅ | |
| Affects foliage, grass, flowers | 🟡 | GPU sway in the terrain vertex shader, confirmed in-game on the vanilla renderer and with Sodium 0.5 (grass fields lean and roll with the wind, leaves shift). Gusts are a pattern carried by the wind's accumulated travel, so they cross a field at the wind's pace and stop when it does. **Not active under a shader pack**; on the vanilla renderer leaves only sway on Fancy graphics. |
| Affects smoke | 🟡 | The mod's own smoke/ash only; vanilla particles are untouched. |
| Affects water surface motion | 🟡 | Still air leaves water nearly smooth and slow; wind speeds the waves up, steepens the ones heading downwind, carries the ripples along and raises surf at the shore (see Water). It is a procedural wave model, not a fluid simulation. |

## 5. Biome identity

| Biome | Status | Notes |
|---|---|---|
| Desert | ✅ | Shimmer, blowing sand, dust devils, haze, day/night contrast, sandstorms, dry wind. |
| Snowy mountains | ✅ | Spindrift, breath, prints, ice crystals, frost, blizzards, altitude cold, freezing fog. |
| Forest | 🟡 | Falling leaves, pollen, morning mist, birdsong, rustle, drips. Light shafts are Ultra/Cinematic only. Tree sway depends on the renderer (see Wind). |
| Jungle | ✅ | Humidity haze, downpours, spores, mist, insects. |
| Swamp | ✅ | Low fog, fireflies, frogs, crickets, mud. "Distant bubbling" is not a dedicated sound. |
| Plains | 🟡 | Grass waves (renderer permitting), seeds, morning mist, dust, cloud shadows drifting over the grass. No dew. |
| Ocean and beaches | 🟡 | See-through, reflective water (see Water), spray, ripples, cold-water mist, damp-sand prints, surf audio, foam and surf at the shore, damp sand along the waterline. The surf is drawn on the flat water surface: nothing rises or curls. |
| Caves | ✅ | Drips with sound, echo, dust in lamplight, damp fog, depth temperature, eye adaptation, exterior audio fades. |
| Nether | ✅ | Heat shimmer scaling with lava, embers, ash, smoke, spores, rumble, per-biome haze. |
| End | ✅ | Drifting motes on a curling current, faint waver, drone. |
| Modded biomes | ✅ | Classified automatically from tags/temperature/precipitation; registrable through the API. |

## 6. Sky, light, fog

| Feature | Status | Notes |
|---|---|---|
| Unified atmosphere controller | ✅ | `Atmosphere`. |
| Fog from humidity, rain, altitude, temperature, time, biome, storms | ✅ | Density-summed components with density-weighted colour. |
| Morning valley fog, jungle mist, desert haze, swamp ground fog | ✅ | Analytic height fog. |
| Fog never blocks basic visibility | ✅ | Opacity cap and a visibility floor; "Reduce fog" raises both. |
| Storm cloud states, gathering, clearing | ✅ | The cloud layer's coverage follows the weather: fair-weather cumulus that comes and goes, closing over before rain, towering and dark in a storm, lit from within by lightning. |
| Volumetric / depth-aware clouds | 🟡 | A three-dimensional cloud layer replaces the game's flat clouds on Balanced and up: lit by the sun or moon with self-shadowing, drifting with the wind aloft, hidden behind terrain and in front of what lies beyond it. The layer is cut into 8 to 32 horizontal slices (by preset) at fixed heights and each pixel samples every slice once; that makes the picture smooth without dither or grain, at the price of soft rather than crisp detail and, at the lower slice counts, faint terracing on the flanks of tall clouds. On Performance and Balanced the clouds over open sky are drawn at half resolution (a quarter of the pixels) and enlarged. It is one layer at the game's cloud height with shapes from a tiling noise texture; flying through it shows a plain veil, not the inside of a cloud. Seen through glass or water the sky shows no clouds (as with the game's own on Fancy graphics). With "Clouds: Off" in the video settings or with the option off, the game's clouds stay, with the old flat overcast deck behind them. |
| Cloud shadows on the ground | 🟡 | The same cloud field shades the land (and stops under roofs near the camera). A darkening only: no light is actually blocked, and beyond about 40 blocks every surface is assumed to be under open sky. |
| Night sky: stars, Milky Way | ✅ | Thousands of small twinkling stars and a dusty band, turning with the sun and moon, added to the game's own stars. |
| Northern lights | 🟡 | Curtains over clear, cold nights when the night is "active". Seen in one staged test scene only. |
| Rainbows | 🟡 | Opposite a low sun while rain is falling or the ground is still wet. A single primary bow; see "Not verified". |
| Richer sky colours | ✅ | Deeper blue overhead, a paler horizon, gold-to-rose sunsets toward the sun and a dusky pink band opposite it, layered on the game's own sky so biome sky colours still show. |
| Sunrise/sunset warmth, haze | ✅ | |
| Exposure adaptation | ✅ | Estimated from light levels at the camera, not from the image. |
| Colour grading per climate | ✅ | |
| Bloom | ✅ | Quarter-resolution bright pass and blur, Balanced preset and up. Eager in the dark (torches, lava, lanterns glow), restrained in daylight (sunlit snow, glitter on water). The game renders in low dynamic range, so "bright" is judged from the final colour: a white block in full sun blooms a little too. By day the open sky is mostly kept out of the bloom (its blue channel is at full all over, and letting it bloom laid a white veil over the horizon); at night the moon and stars keep their glow. |
| Glow around the sun and moon | ✅ | A warm halo that is strongest when the sun is low, a faint cool one for the moon; hidden by overcast. |
| Film-style colour response, vignette | ✅ | Optional (*Graphics → Film look*, *Vignette*): vibrance, highlight roll-off, mid-tone contrast, cool shadows and warm highlights. Drawn under the hand and HUD. |
| Light shafts | 🟡 | Screen-space, Ultra/Cinematic presets only. Seen working in the Ultra sunrise scene. |
| Shader-pack-compatible wetness/reflection | ❌ | With a shader pack active the atmosphere pass switches off and the pack is left in charge. |

## 7. Water

The water itself is shaded by the mod on the vanilla renderer and, through a patch to Sodium's terrain
shader, with Sodium 0.5 (see COMPATIBILITY.md). **Under an Iris shader pack the rows marked "terrain
shader" are inactive** and water keeps the look the pack gives it.

| Feature | Status | Notes |
|---|---|---|
| See-through water: the bed refracted by the ripples, fading to blue-green with depth | ✅ | Terrain shader. Replaces the animated water texture on still water. Uses a copy of the frame taken just before translucent terrain, so other translucent things *behind* water (stained glass, ice, a second water surface) are not visible through it. |
| Reflections of terrain, trees and builds | 🟡 | Terrain shader, Balanced preset and up. Traced in screen space: only what is on screen can be mirrored, and the mirror image fades toward the screen edges; everything else mirrors the sky. Performance preset mirrors the sky only. |
| Reflections of the clouds | ✅ | Terrain shader. The mirror image is taken from the cloud field itself, so it is there whether or not the cloud is on screen; it is a flat picture of the layer without its lighting. |
| Sun and moon glitter, sky mirrored at glancing angles | ✅ | Terrain shader. Hard glints on ripples nearby, a long sparkling path farther out. |
| Caustics on the bed, from above and when diving | ✅ | From above: terrain shader. Under water they are part of the atmosphere pass. |
| Wind-driven swell and ripples | 🟡 | Terrain shader. Six wave trains on fixed headings with noise-bent crests plus five layers of small wavelets, all with analytic normals (no facets), faded with distance instead of aliasing. The surface mesh only moves at block corners (a gentle bob); the visible ripples are shading. Measured in the test run from a clip of six frames a quarter of a second apart: in a dead calm about 6% of the picture changes from frame to frame (the pond's banks have surf and the desert air shimmers), in a gale between a quarter and three quarters of it, depending on the view. |
| Shoreline: foam, surf, damp sand | 🟡 | Terrain shader for the foam and surf, atmosphere pass for the sand. A soft edge with a foam line where water meets land; in wind, bands of surf that run in toward the shore, stronger the harder it blows and nearly absent in a calm; sand and other ground within a block or two of the water is darkened as if damp. *Water Effects → Shoreline* switches all three off. The surf is a pattern on the flat surface, the damp ground reaches about 32 blocks from the camera, and how far "the shore" is comes from a 64×64 map of the columns around the player. |
| Rain rings on the water surface | 🟡 | Terrain shader. Implemented, **not yet looked at in a running client**. |
| Waterfalls and flowing sides | 🟡 | Terrain shader. A see-through sheet with bright streaks taken from the flow animation; no spray geometry beyond the existing mist particles. Seen in one test scene only; it reads as a fairly opaque blue sheet. |
| The surface from below | 🟡 | A rippling window onto the world above, mirror-dark beyond the critical angle. No refraction of the sun through the surface. |
| Rain ripples, ripples around moving creatures, entry splashes, speed-dependent spray | ✅ | |
| Dripping after leaving water, lens droplets | ✅ | |
| Boat wakes | 🟡 | Ripples behind moving boats, no shaped wake. |
| Waterfall spray, cold-water mist | ✅ | |
| Wetness on the entity model | ❌ | Drips only. |

## 8. Player immersion, accessibility

| Feature | Status | Notes |
|---|---|---|
| Breath, shimmer, shiver, lens rain, post-swim droplets, frost, wind buffeting, eye adaptation | ✅ | |
| Storm readable without UI (clouds, wind, sound, then rain) | ✅ | |
| Dropped items behave like objects | ✅ | They tumble in flight, come to rest lying flat (blocks on a face, stacks as a pile), rock on water and flutter in strong wind. Only how the item entity is drawn changes: it does not roll, slide or collide differently, and pick-up is untouched. Not part of the original brief. |
| Options to reduce camera motion, overlays, flashes, distortion, fog, particles | ✅ | |
| Motion blur toggle | ❌ | The mod adds no motion blur, so there is nothing to toggle. |

## 9. Audio

| Feature | Status | Notes |
|---|---|---|
| Layered, procedurally mixed soundscape reacting to weather, shelter, biome | ✅ | Everything is synthesised at runtime; there are no audio files. |
| Desert, forest, mountain, rain (per surface, muffled indoors), cave layers | ✅ | Levels and spectra were measured; **nobody has listened to it** (see below). |
| Material footstep layers | ❌ | Removed: footsteps are the game's own sounds only. Footprints are still left, silently. |
| Cave reverb | 🟡 | A discrete two-tap echo of nearby sounds plus reverb on the mod's own drips. Not a true reverb on all game audio. |
| Recorded sound assets | 🟡 | None are shipped, by design. Birds and frogs are simple synthesis and may sound artificial, so a resource pack can supply recordings for those two (`docs/SAMPLE_PACK.md`, with a template pack): the mod then plays them, placed around the listener, whenever the synthesised animal would have called. **Not run with a pack of real recordings.** |

## 10. Engine, architecture, tooling

| Feature | Status | Notes |
|---|---|---|
| Presets: Performance / Balanced / Ultra / Cinematic | ✅ | Hard budgets. |
| Batching, pooling, culling, LOD, bounded memory, adaptive density | ✅ | |
| GPU instancing | ❌ | Batched CPU-built quads instead (one buffer upload per frame). |
| Client/server separation enforced | ✅ | All client code is under `dev.livingworld.client`, and the build fails if a class outside those packages mentions one inside them (`:common:checkClientIsolation`, part of `gradlew check`). |
| One code base for Fabric and Forge | ✅ | A `common` module compiled against plain Minecraft with no loader, and one thin module per loader (the MultiLoader-Template layout). Both jars come out of `gradlew build`. |
| Lightweight networking | ✅ | One small packet per dimension, only on change. Works with no packets at all on vanilla servers. |
| Settings screen with 14 categories, tooltips | ✅ | Generated from annotated config fields. |
| Separate server config | ✅ | `config/livingworld-server.json`. |
| `/livingworld` and `/lw` commands | ✅ | Simulated vs. real weather kept explicit. |
| Extension API | ✅ | `LivingWorldApi`. |
| Settings button in the mod list | 🟡 | Fabric: Mod Menu integration, compiled against Mod Menu; not run with Mod Menu installed. Forge: the *Config* button of the built-in mod list; implemented, not clicked in a run. F9 and `/lw config` are what the scene run uses. |
| Unit tests | ✅ | 37 tests on the pure simulation (including the calendar, seasonal growth and when winter freezes and thaws), the decal store, the synthesiser and the patch applied to Sodium's and Embeddium's shader. |
| Automated in-game scene run with screenshots | ✅ | `gradlew :fabric:runAutotest` and `gradlew :forge:runAutotest`: the same scenes on either loader. |
| Frame sequences in the scene run | ✅ | A scene can record a short run of frames and measure how much of the picture changes from one to the next, and how much changes and changes straight back (flicker). Used on the water in a calm and in a gale, on surf, and on leaves in a breeze, where the run fails if more than 2% of the picture flickers. It tells motion from stillness and flicker from motion; it does not say whether the motion looks right. |
| Sodium support for water, sway and seasons | 🟡 | Sodium 0.5.x on Fabric and Embeddium 0.3.x on Forge only. See COMPATIBILITY.md for how it works and what was run. |
| Half-resolution buffers | 🟡 | The cloud layer over open sky, on Performance and Balanced. **Water reflections are not half-resolution:** water is shaded while the terrain is drawn, so there is no separate reflection pass to shrink; that would need the reflections moved into a pass of their own. The Performance preset skips the reflection ray instead. |
| In-game A/B benchmark | ✅ | `/livingworld benchmark`. |

## Not verified from this environment

- **How it sounds.** Audio was validated numerically only.
- **How it moves.** Verification was by still screenshots and by short frame sequences that measure how
  much of the picture changes (water, surf, leaves). Animation quality (sway, shimmer, gusts, camera
  feedback, drifting clouds, the northern lights) has not been watched by a person.
- **Multiplayer with real remote players**, and a **dedicated server launch** (starting one requires
  accepting Mojang's EULA, which was not done on your behalf). On Fabric a dedicated server was started
  as far as the EULA prompt, by which point the mod had initialised without error; Forge
  loads mods after that prompt, so there dedicated-server safety rests on the build check that fails
  when server-safe code mentions a client class, and on the server half having run inside the Forge
  client's single-player world.
- **Either jar in a real installation.** Both loaders were run from the development environment only.
- **Shader packs.** The yield path is implemented, but no shader pack was loaded. Oculus was not run.
- **Sodium versions other than 0.5.11 and Embeddium versions other than 0.3.31**, and the opt-in winter
  and crop rules over a whole season of play.
- **Hardware other than the development machine** (Intel UHD 770 integrated graphics), Fabulous
  graphics (the water shading in particular has only been run on Fancy), Mod Menu, and the Config button
  of Forge's mod list.
- **Water against other mods' translucent blocks and fluids.** Only vanilla water, glass and ice were looked at.
