# Living World — compatibility

**Target:** Minecraft 1.20.1, Fabric Loader ≥ 0.15, Fabric API, Java 17.

## Client-only, server-only, or both

| Installed on | What you get |
|---|---|
| Client only (any server, including vanilla) | Every visual and audio effect. Wind, weather intensity and the season are derived from the synchronised world time and vanilla weather, so they still match between players who have the mod (seasons then last the default 8 days). `/lw` gives you private test weather and a calendar of your own. No rain forecast (clouds gather when the rain starts rather than before). |
| Client and server | The above, plus `/livingworld` test commands that everyone sees, the rain forecast, a season length and calendar the server decides, and the three opt-in rules: survival temperature, seasonal crops, and winters that place real snow and ice. |
| Server only | Commands and the opt-in rules. Vanilla clients can join; they are never sent the mod's packets, and they see the snow and ice the winter rule places like any other blocks. |

Players with different presets and settings can share a world: every option in the settings screen is
local. Only `config/livingworld-server.json` affects gameplay.

## How the mod hooks into rendering

Knowing this makes conflicts predictable.

| Hook | Used for | If another mod interferes |
|---|---|---|
| `WorldRenderEvents` (Fabric API) | Decals after entities; atmosphere pass, precipitation and particles at the end of the world pass | — |
| Mixin: `WorldRenderer.renderWeather` (cancel) | Replaces vanilla rain/snow sheets | Turn off *Rain & Storms → Wind-driven precipitation* to give vanilla (or another weather mod) its rendering back. |
| Mixin: `WorldRenderer.tickRainSplashing` (cancel) | Replaces vanilla splash particles and rain loop | Only cancelled while the mod's own audio is actually playing. |
| Mixin: `BackgroundRenderer.render` / `applyFog` (tail) | Tints fog colour, caps fog distance in storms | Runs after vanilla and only ever *shortens* fog. |
| Mixin: `ClientWorld.getSkyColor` / `getCloudsColor` (return) | Storm-coloured sky | *Compatibility → Storm-coloured sky* turns it off. |
| Mixin: `WorldRenderer.renderClouds` (cancel, optional, applied late) | The game's flat clouds are skipped while the mod draws its own cloud layer in the atmosphere pass | *Fog & Atmosphere → Volumetric clouds* off gives the game (or another cloud mod) its clouds back. The game's own "Clouds: Off" also switches the mod's clouds off. |
| Mixin: `GameRenderer.tiltViewWhenHurt` (tail), `render` (after world) | Camera feedback, lens overlay | *Accessibility* options remove both. |
| Mixin: `SoundSystem.play` (head) | Delayed thunder, cave echo | Individual toggles under *Sound*. |
| Resource override: `minecraft:shaders/core/rendertype_cutout(_mipped)` | Vegetation sway and seasonal foliage colours on the vanilla renderer | A resource pack that replaces the same shaders wins; both then switch themselves off. |
| Resource addition: `minecraft:shaders/include/lw_water.glsl`, `lw_sky.glsl`, `lw_clouds.glsl`, `lw_water_surface.glsl`, `lw_foliage.glsl` | The wave model, the cloud field, and the water, cloud and foliage shading. Imported by the shaders above, by the atmosphere pass and by the patched Sodium shader | Names are prefixed to stay out of the way of other packs. |
| Model wrapping (Fabric Model Loading + Renderer API) | Tags plant vertices for sway on the vanilla renderer | Not used when Sodium is installed; the mesher hook in the next row does the tagging there. |
| Mixins into Sodium (optional, applied only when Sodium 0.5.x is installed): `ShaderLoader.getShaderSource` (return), `BlockRenderer.writeGeometry`, `FluidRenderer.render` / `writeQuad`, `ShaderChunkRenderer.begin` (tail) | Water shading, vegetation sway and seasonal colours under Sodium. See "The Sodium bridge" below. | Every injection is optional. If Sodium's terrain shader does not contain the lines the patch hangs on, it is left untouched, one warning is logged, and those three features are off. Another mod that rewrites the same shader, or that uses the high byte of Sodium's light coordinate, will conflict. |
| Resource override: `minecraft:shaders/core/rendertype_translucent` | Water shading (refraction, depth colour, reflections, glitter, caustics, swell). Glass, ice and everything else in the layer take the unchanged vanilla path. | A resource pack that replaces the same shader wins; water then keeps that pack's look. *Water Effects → Realistic water* off restores the vanilla look. |
| Mixin: `FluidRenderer.render` (head) / `vertex` (argument), both optional | Marks water vertices with a vertex alpha of 250/255 so the shader can tell water from glass | If another mod rewrites the fluid mesher the injection is skipped and water stays vanilla. Without the mod's shader the mark is a 2% change in water opacity. |
| `WorldRenderEvents.BEFORE_DEBUG_RENDER` | Copies the frame (colour and depth) just before translucent terrain, for the water shader to refract and mirror | Skipped with a shader pack, and when realistic water is off. |
| Saved data: `data/livingworld_seasons.dat` in the overworld's folder | The calendar shift set by `/livingworld season set` | Removing the file puts the calendar back on the world's day counter. |
| Mixin: `CropBlock`, `StemBlock`, `SaplingBlock`, `SweetBerryBushBlock`, `CocoaBlock` `.randomTick` (head, cancellable; tail) | Seasonal crop growth, **opt-in** (`seasonalCrops`) | Does nothing while the rule is off. Skips some of a plant's random ticks in the slow seasons and repeats some in spring; a mod's crop that extends one of these classes follows the seasons, one that does not is unaffected. |
| Server tick + saved data: `data/livingworld_seasonal_blocks.dat` per dimension | Real snow and ice in winter, **opt-in** (`seasonalSnowAndIce`): the list of blocks the rule has placed, so that the thaw removes those and nothing else | Does nothing while the rule is off. Removing the file makes the mod forget what it placed: that snow and ice then stay until something else removes them. |
| Mixin: `ItemEntityRenderer.render` (head, optional) | Dropped items that tumble and lie flat | *General → Dropped items lie on the ground* off returns the vanilla hover-and-spin. If another item-physics mod is installed, turn one of the two off. |

With the default settings no blocks, block states, block entities or chunks are ever modified, and the only
thing the mod saves with a world is the calendar shift above. The winter rule is the one exception, and it is
off until an operator turns it on: it places snow layers and ice, and removes only the ones it placed.

## The Sodium bridge

Sodium draws terrain with its own shader, so the mod's overrides of the vanilla terrain shaders do nothing
there. With Sodium 0.5.x installed the mod instead:

1. **Adds to Sodium's terrain shader as it is loaded.** The text of `blocks/block_layer_opaque.vsh` and `.fsh`
   is patched at six lines it expects to find. If any is missing or appears twice the stage is left as Sodium
   wrote it. The added code is the same GLSL the vanilla overrides use.
2. **Tags vertices while Sodium meshes a chunk.** Sodium's vertex has no spare attribute, but its light
   coordinate is two 16-bit numbers that only ever hold 0 to 240. The mod writes a tag into the high byte of
   the first (plant and how far up it, moves as a whole, water, faces the sky) and the patched shader masks it
   off before the coordinate is used.
3. **Hands the shader this frame's values** each time Sodium binds its terrain program: wind, season, the
   camera, the water's clock, and the copies of the frame that water refracts and mirrors.

It applies only to Sodium versions that start with `0.5.` (the class names it hooks moved in 0.6). With any
other version nothing is hooked, and water, sway and seasonal colours stay off as they did before.

## Rendering mods

| Setup | Status | Behaviour |
|---|---|---|
| Vanilla renderer (Fabric API's Indigo) | **Tested** | Everything. |
| Fabulous graphics | Not tested | Expected to work; translucent ordering of the mod's particles relative to water may differ slightly because they are drawn after the transparency composite, and the water shading has only been run on Fancy. |
| Sodium 0.5.x | **Tested** (0.5.11) | Everything, including realistic water, vegetation sway and seasonal colours through the bridge above. |
| Sodium + Iris, no shader pack | **Tested** (Sodium 0.5.11 + Iris 1.7.2) | As Sodium: the bridge patched both shader stages, and water, sway and seasonal colours appeared as they do without Iris. |
| Sodium 0.6 and newer | Not tested | The bridge does not apply, so water keeps Sodium's look and plants do not sway or change colour. Everything that is not part of the terrain (sky, clouds, weather, footprints, the atmosphere pass, sound) does not depend on it. |
| Iris with a shader pack | **Not tested** | By default (*Compatibility → Yield to shader packs*) the mod stops using its own programs: no atmosphere pass (no heat shimmer, mist, wet ground, puddles, cloud layer, eye adaptation, bloom, film look), no realistic water (the pack shades water), no sway or seasonal colours, no lit footprints (simple pre-shaded prints instead), no lens droplets (frost falls back to a plain overlay). Precipitation, particles and footprints are drawn with the stock particle program, which the pack shades. Fog distance and colour still work. |
| Embeddium / Oculus | Not applicable | Those are Forge/NeoForge ports. This is a Fabric mod. |

What "Tested" means here: the automated scene run with those mods added to the dev client
(`gradlew runAutotest -PlwCompat=sodium -PlwScenes=plains,water,seasons`, and the same with
`-PlwCompat=sodium,iris`) completed with every expectation passing and no OpenGL errors, and the
screenshots were compared with the vanilla renderer's: see-through water with reflections and caustics, grass
leaning in the wind, and the four seasons' colours all appear. It is a smoke test of three scenes on one
machine (NVIDIA RTX 3050), not a soak test. The other scenes (desert, snow, jungle and the rest) were last run
with Sodium before the water, sky and season work, and not since. The newest 1.20.1 builds of Sodium (0.5.13)
and Iris (1.7.6) could not be loaded into this project's dev environment (they need a newer Loom to remap), so
those exact versions are untested.

## Other mods

- **Biome mods** (Terralith, Biomes O' Plenty, …): biomes are classified automatically from their
  tags, temperature and precipitation, so they get sensible weather, fog and ambience with no
  integration. For exact control register a profile: `LivingWorldApi.registerBiome(key, profile)`.
- **Block mods**: ground materials are resolved from block tags and, failing that, the block's step
  sound, so modded sand/snow/mud take footprints automatically. Override with
  `LivingWorldApi.registerSurface(block or tag, material)`.
- **Other weather / particle / footprint mods**: likely to double up rather than break. Disable the
  overlapping feature on one side.
- **Sound physics / reverb mods**: turn off *Sound → Cave echo* to avoid a double echo.
- **Resource packs**: all textures live under `assets/livingworld/textures` and can be replaced. The
  particle sheet is an 8×8 grid, the decal sheets a 4×4 grid (layout documented in
  `src/assetgen/java/dev/livingworld/assetgen`).

## Known limitations

- Vegetation sway, seasonal foliage colours and realistic water need the vanilla terrain shaders or
  Sodium 0.5 (not a shader pack). On the vanilla renderer leaves additionally need Fancy graphics to sway
  and to change colour.
- Lightning lights the picture, not the world: the glow on terrain and cloud is added in the atmosphere
  pass, so nothing casts a shadow and block light does not change.
- The winter rule works on the topmost block of columns within 64 blocks of a player, a few columns per
  tick, and remembers at most 400,000 blocks per dimension. In a large, busy world winter can therefore be
  patchy, and past that number it places no more until the thaw has removed some.
- Recorded birds and frogs need a resource pack that the mod does not provide (`docs/SAMPLE_PACK.md`).
- The cloud layer is drawn in the atmosphere pass, after the world, wherever nothing nearer has written
  depth. Through glass, ice or a water surface the sky therefore shows no clouds (the game's own clouds
  behave the same way on Fancy graphics), and particles and precipitation, which are drawn after the
  pass, are never hidden by cloud.
- `/time set` moves the season, because the calendar follows the world's day counter. Use
  `/time add`, or put the calendar back with `/livingworld season set`.
- Water is shaded from a copy of the frame taken before translucent terrain is drawn. Translucent things
  behind a water surface (stained glass, ice, slime, another water surface) are therefore not visible
  through it, and reflections can only show what is on screen.
- Bloom works on the final low-dynamic-range image, so very bright ordinary surfaces (white blocks in
  full sun) glow slightly as well as true light sources.
- The atmosphere pass reads the depth buffer after the world is drawn. Mods that render into the world
  after `WorldRenderEvents.LAST` will not be fogged by it.
- Wet darkening, puddles and snow on the ground are screen-space and limited to about 40 blocks from the
  camera. In winter, grass and leaves are whitened at any distance, other surfaces only within that range.
- Test weather set with `/livingworld weather` is not saved across a server restart.
