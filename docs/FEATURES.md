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
| Puddles that form, ripple, react, and evaporate | 🟡 | Screen-space puddles with rain ripples and sky-colour Fresnel reflection; they grow and evaporate. They reflect the sky colour, **not** the scene. Walking through one makes a splash and ripple particle, the puddle itself does not deform. |
| Heavy rain reduces visibility, rain haze toward the horizon | ✅ | |
| Rain audio changes with the surface | ✅ | Mix of leaves / stone / water / wood / soft from the surfaces around you. |
| Thunderstorm: dark cloud deck, wind gusts, dense rain | ✅ | |
| Distance-aware lightning flash, delayed thunder | ✅ | Vanilla bolts are delayed and dulled by distance; distant sheet lightning is added. |
| Lightning illuminating the environment | 🟡 | Screen flash and clouds lit from within. No real light cast by the bolt. |
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
| Affects foliage, grass, flowers | 🟡 | GPU sway through the vanilla terrain shaders, confirmed in-game (grass fields lean and roll with the wind, leaves shift). **Not active with Sodium or under a shader pack**, and leaves only sway on Fancy graphics. |
| Affects smoke | 🟡 | The mod's own smoke/ash only; vanilla particles are untouched. |
| Affects water surface motion | 🟡 | The swell and the ripples on water run with the wind and roughen as it rises (see Water). Vanilla renderer only; it is a procedural wave sum, not a fluid simulation. |

## 5. Biome identity

| Biome | Status | Notes |
|---|---|---|
| Desert | ✅ | Shimmer, blowing sand, dust devils, haze, day/night contrast, sandstorms, dry wind. |
| Snowy mountains | ✅ | Spindrift, breath, prints, ice crystals, frost, blizzards, altitude cold, freezing fog. |
| Forest | 🟡 | Falling leaves, pollen, morning mist, birdsong, rustle, drips. Light shafts are Ultra/Cinematic only. Tree sway depends on the renderer (see Wind). |
| Jungle | ✅ | Humidity haze, downpours, spores, mist, insects. |
| Swamp | ✅ | Low fog, fireflies, frogs, crickets, mud. "Distant bubbling" is not a dedicated sound. |
| Plains | 🟡 | Grass waves (renderer permitting), seeds, morning mist, dust. No dew, no cloud shadows. |
| Ocean and beaches | 🟡 | See-through, reflective water (see Water), spray, ripples, cold-water mist, damp-sand prints, surf audio. Only a faint foam line at the shore; no breaking waves, no wet-sand shading at the waterline. |
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
| Storm cloud states, gathering, clearing | ✅ | Procedural overcast deck behind the vanilla clouds. |
| Volumetric / depth-aware clouds | ❌ | The deck is a 2D layer on sky pixels. |
| Sunrise/sunset warmth, haze | ✅ | |
| Exposure adaptation | ✅ | Estimated from light levels at the camera, not from the image. |
| Colour grading per climate | ✅ | |
| Bloom | ✅ | Quarter-resolution bright pass and blur, Balanced preset and up. Eager in the dark (torches, lava, lanterns glow), restrained in daylight (sun, sunlit snow, glitter on water). The game renders in low dynamic range, so "bright" is judged from the final colour: a white block in full sun blooms a little too. |
| Glow around the sun and moon | ✅ | A warm halo that is strongest when the sun is low, a faint cool one for the moon; hidden by overcast. |
| Film-style colour response, vignette | ✅ | Optional (*Graphics → Film look*, *Vignette*): vibrance, highlight roll-off, mid-tone contrast, cool shadows and warm highlights. Drawn under the hand and HUD. |
| Light shafts | 🟡 | Screen-space, Ultra/Cinematic presets only. Seen working in the Ultra sunrise scene. |
| Shader-pack-compatible wetness/reflection | ❌ | With a shader pack active the atmosphere pass switches off and the pack is left in charge. |

## 7. Water

The water itself is shaded by the mod on the vanilla renderer. **With Sodium or an Iris shader pack the
rows marked "vanilla renderer" are inactive** and water keeps the look those give it.

| Feature | Status | Notes |
|---|---|---|
| See-through water: the bed refracted by the ripples, fading to blue-green with depth | ✅ | Vanilla renderer. Replaces the animated water texture on still water. Uses a copy of the frame taken just before translucent terrain, so other translucent things *behind* water (stained glass, ice, a second water surface) are not visible through it. |
| Reflections of terrain, trees and builds | 🟡 | Vanilla renderer, Balanced preset and up. Traced in screen space: only what is on screen can be mirrored, and the mirror image fades toward the screen edges; everything else mirrors the sky. Performance preset mirrors the sky only. |
| Sun and moon glitter, sky mirrored at glancing angles | ✅ | Vanilla renderer. |
| Caustics on the bed, from above and when diving | ✅ | From above: vanilla renderer. Under water they are part of the atmosphere pass and also work with Sodium. |
| Wind-driven swell and ripples | 🟡 | Vanilla renderer. The surface mesh only moves at block corners (a gentle bob); the visible ripples are shading. |
| Soft shoreline with a faint foam line | ✅ | Vanilla renderer. |
| Rain rings on the water surface | 🟡 | Vanilla renderer. Implemented, **not yet looked at in a running client**. |
| Waterfalls and flowing sides | 🟡 | Vanilla renderer. A see-through sheet with bright streaks taken from the flow animation; no spray geometry beyond the existing mist particles. Seen in one test scene only; it reads as a fairly opaque blue sheet. |
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
| Material footstep layers | ✅ | Snow, sand, mud, puddle, wet grass, dust, ice. |
| Cave reverb | 🟡 | A discrete two-tap echo of nearby sounds plus reverb on the mod's own drips. Not a true reverb on all game audio. |
| Recorded sound assets | ❌ | By design. Birds and frogs in particular are simple synthesis and may sound artificial. |

## 10. Engine, architecture, tooling

| Feature | Status | Notes |
|---|---|---|
| Presets: Performance / Balanced / Ultra / Cinematic | ✅ | Hard budgets. |
| Batching, pooling, culling, LOD, bounded memory, adaptive density | ✅ | |
| GPU instancing | ❌ | Batched CPU-built quads instead (one buffer upload per frame). |
| Client/server separation enforced | ✅ | Split source sets: common code cannot reference client code at compile time. |
| Lightweight networking | ✅ | One small packet per dimension, only on change. Works with no packets at all on vanilla servers. |
| Settings screen with 14 categories, tooltips | ✅ | Generated from annotated config fields. |
| Separate server config | ✅ | `config/livingworld-server.json`. |
| `/livingworld` and `/lw` commands | ✅ | Simulated vs. real weather kept explicit. |
| Extension API | ✅ | `LivingWorldApi`. |
| Mod Menu integration | 🟡 | Implemented, compiled against Mod Menu; not run with Mod Menu installed. |
| Unit tests | ✅ | 26 tests on the pure simulation, decal store and synthesiser. |
| Automated in-game scene run with screenshots | ✅ | `gradlew runAutotest`. |
| In-game A/B benchmark | ✅ | `/livingworld benchmark`. |

## Not verified from this environment

- **How it sounds.** Audio was validated numerically only.
- **How it moves.** Verification was by still screenshots; animation quality (sway, shimmer, gusts,
  camera feedback) has not been watched by a person.
- **Multiplayer with real remote players**, and a **dedicated server launch** (starting one requires
  accepting Mojang's EULA, which was not done on your behalf). Dedicated-server safety rests on the
  compile-time source-set split.
- **Shader packs.** The yield path is implemented and Iris was tested installed, but no shader pack was loaded.
- **Hardware other than the development machine** (Intel UHD 770 integrated graphics), Fabulous
  graphics (the water shading in particular has only been run on Fancy), and Mod Menu.
- **Water against other mods' translucent blocks and fluids.** Only vanilla water, glass and ice were looked at.
