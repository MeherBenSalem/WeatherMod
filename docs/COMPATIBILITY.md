# Living World — compatibility

**Target:** Minecraft 1.20.1 and Java 17, on either loader:

- **Fabric:** Fabric Loader ≥ 0.15 and Fabric API.
- **Forge:** Forge ≥ 47.2.0, nothing else.

The two jars are built from the same code and behave the same. Where the loaders differ in how the mod
plugs in, the tables below say so. A Fabric client and a Forge server (or the other way round) cannot talk
to each other in any case, so the mod's packets are only ever exchanged between two sides on one loader.

## Client-only, server-only, or both

| Installed on | What you get |
|---|---|
| Client only (any server, including vanilla) | Every visual and audio effect. Wind, weather intensity and the season are derived from the synchronised world time and vanilla weather, so they still match between players who have the mod (seasons then last the default 8 days). `/lw` gives you private test weather and a calendar of your own. No rain forecast (clouds gather when the rain starts rather than before). |
| Client and server | The above, plus `/livingworld` test commands that everyone sees, the rain forecast, a season length and calendar the server decides, and the three opt-in rules: survival temperature, seasonal crops, and winters that place real snow and ice. |
| Server only | Commands and the opt-in rules. Vanilla clients can join; they are never sent the mod's packets, and they see the snow and ice the winter rule places like any other blocks. |

Players with different presets and settings can share a world: every option in the settings screen is
local. Only `config/livingworld-server.json` affects gameplay.

## How the mod hooks into rendering

Knowing this makes conflicts predictable. Class and method names are the official (Mojang) ones, which is
what the source uses. The mixins are the same on both loaders.

| Hook | Used for | If another mod interferes |
|---|---|---|
| Fabric: `WorldRenderEvents` (`START`, `AFTER_ENTITIES`, `BEFORE_DEBUG_RENDER`, `LAST`). Forge: `RenderLevelStageEvent` (`AFTER_SKY`, `AFTER_ENTITIES`, `AFTER_BLOCK_ENTITIES`, `AFTER_LEVEL`) | This frame's values for the terrain shaders before terrain is drawn; decals after entities; the copy of the frame for the water; atmosphere pass, precipitation and particles at the end of the world pass | — |
| Mixin: `LevelRenderer.renderSnowAndRain` (cancel) | Replaces vanilla rain/snow sheets | Turn off *Rain & Storms → Wind-driven precipitation* to give vanilla (or another weather mod) its rendering back. |
| Mixin: `LevelRenderer.tickRain` (cancel) | Replaces vanilla splash particles and rain loop | Only cancelled while the mod's own audio is actually playing. |
| Mixin: `FogRenderer.setupColor` / `setupFog` (tail) | Tints fog colour, caps fog distance in storms | Runs after vanilla (and after Forge's fog events) and only ever *shortens* fog. |
| Mixin: `ClientLevel.getSkyColor` / `getCloudColor` (return) | Storm-coloured sky | *Compatibility → Storm-coloured sky* turns it off. |
| Mixin: `LevelRenderer.renderClouds` (cancel, optional, applied late) | The game's flat clouds are skipped while the mod draws its own cloud layer in the atmosphere pass | *Fog & Atmosphere → Volumetric clouds* off gives the game (or another cloud mod) its clouds back. The game's own "Clouds: Off" also switches the mod's clouds off. |
| Mixin: `GameRenderer.bobHurt` (tail), `render` (after the level) | Camera feedback, lens overlay | *Accessibility* options remove both. |
| Mixin: `SoundEngine.play` (head) | Delayed thunder, cave echo | Individual toggles under *Sound*. |
| Custom audio streams: Fabric API's `FabricSoundInstance.getAudioStream`, Forge's `SoundInstance.getStream` | The synthesised ambience and weather beds are ordinary sound instances that hand the engine their own audio | The game's volume sliders, pausing and device switching apply as to any sound. |
| Resource override: `minecraft:shaders/core/rendertype_cutout(_mipped)` | Vegetation sway and seasonal foliage colours on the vanilla renderer | A resource pack that replaces the same shaders wins; both then switch themselves off. |
| Resource addition: `minecraft:shaders/include/lw_water.glsl`, `lw_sky.glsl`, `lw_clouds.glsl`, `lw_water_surface.glsl`, `lw_foliage.glsl` | The wave model, the cloud field, and the water, cloud and foliage shading. Imported by the shaders above, by the atmosphere pass and by the patched Sodium shader | Names are prefixed to stay out of the way of other packs. |
| Model wrapping. Fabric: Model Loading + Renderer API (a quad transform while the chunk is meshed). Forge: `ModelEvent.ModifyBakingResult` (the wrapper hands out copies of the plant's quads with the tag in their vertex alpha) | Tags plant vertices for sway on the vanilla renderer | Not used when Sodium or Embeddium is installed; the mesher hook in the next row does the tagging there. On Forge, a mod that replaces the baked model of a plant after this one leaves that plant without sway. |
| Mixins into Sodium on Fabric and into Embeddium on Forge (optional, applied only when Sodium 0.5.x / Embeddium 0.3.x is installed): `ShaderLoader.getShaderSource` (return), `BlockRenderer.writeGeometry`, `FluidRenderer.render` / `writeQuad`, `ShaderChunkRenderer.begin` (tail) | Water shading, vegetation sway and seasonal colours under Sodium and Embeddium. See "The Sodium bridge" below. | Every injection is optional. If the terrain shader does not contain the lines the patch hangs on, it is left untouched, one warning is logged, and those three features are off. Another mod that rewrites the same shader, or that uses the high byte of the light coordinate, will conflict. |
| Resource override: `minecraft:shaders/core/rendertype_translucent` | Water shading (refraction, depth colour, reflections, glitter, caustics, swell). Glass, ice and everything else in the layer take the unchanged vanilla path. | A resource pack that replaces the same shader wins; water then keeps that pack's look. *Water Effects → Realistic water* off restores the vanilla look. |
| Mixin: `LiquidBlockRenderer.tesselate` (head) / `vertex` (argument), both optional | Marks water vertices with a vertex alpha of 250/255 so the shader can tell water from glass | If another mod rewrites the fluid mesher the injection is skipped and water stays vanilla. Without the mod's shader the mark is a 2% change in water opacity. |
| Fabric: `WorldRenderEvents.BEFORE_DEBUG_RENDER`. Forge: `RenderLevelStageEvent` at `AFTER_BLOCK_ENTITIES` | Copies the frame (colour and depth) just before translucent terrain, for the water shader to refract and mirror | Skipped with a shader pack, and when realistic water is off. |
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

### Embeddium (Forge)

Embeddium 0.3.x is Sodium 0.5 ported to Forge. It keeps Sodium's class names, its vertex layout and, but for
two additions, its terrain shader, so on Forge the same bridge is hooked into Embeddium at the same four
places, by mixins of the Forge module's own. The shader patch, the tags and the per-frame values are shared
code. The two additions are allowed for:

- Embeddium's fragment shader applies the vertex colour in one of two ways. The water branch is put ahead of
  the choice, so it is taken whichever is compiled in.
- Embeddium has a second vertex format, laid out like the game's own, that it uses when *Use Compact Vertex
  Format* is switched off in its performance options. That format has no spare byte for the tags, so with it
  nothing is tagged and water, sway and seasonal colours stay off under Embeddium until the option is back on
  (it is on by default). The debug overlay says so.

It applies only to Embeddium versions that start with `0.3.`. Oculus is to Embeddium what Iris is to Sodium,
and is asked whether a shader pack is in use through the same API.

## Rendering mods

On Fabric:

| Setup | Status | Behaviour |
|---|---|---|
| Vanilla renderer (Fabric API's Indigo) | **Tested** | Everything. |
| Fabulous graphics | Not tested | Expected to work; translucent ordering of the mod's particles relative to water may differ slightly because they are drawn after the transparency composite, and the water shading has only been run on Fancy. |
| Sodium 0.5.x | **Tested** (0.5.11) | Everything, including realistic water, vegetation sway and seasonal colours through the bridge above. |
| Sodium + Iris, no shader pack | **Tested** (Sodium 0.5.11 + Iris 1.7.2) | As Sodium: the bridge patched both shader stages, and water, sway and seasonal colours appeared as they do without Iris. |
| Sodium 0.6 and newer | Not tested | The bridge does not apply, so water keeps Sodium's look and plants do not sway or change colour. Everything that is not part of the terrain (sky, clouds, weather, footprints, the atmosphere pass, sound) does not depend on it. |
| Iris with a shader pack | **Not tested** | By default (*Compatibility → Yield to shader packs*) the mod stops using its own programs: no atmosphere pass (no heat shimmer, mist, wet ground, puddles, cloud layer, eye adaptation, bloom, film look), no realistic water (the pack shades water), no sway or seasonal colours, no lit footprints (simple pre-shaded prints instead), no lens droplets (frost falls back to a plain overlay). Precipitation, particles and footprints are drawn with the stock particle program, which the pack shades. Fog distance and colour still work. |

On Forge:

| Setup | Status | Behaviour |
|---|---|---|
| Vanilla renderer (Forge's own) | **Tested** | Everything. The whole scene run gives the same results and the same pictures as on Fabric. |
| Fabulous graphics | Not tested | As on Fabric. |
| Embeddium 0.3.x | **Tested** (0.3.31) | Everything, including realistic water, vegetation sway and seasonal colours through the bridge above. Embeddium logs a warning that this mod modifies three of its classes ("may cause instability"): that is its notice about any mod that hooks it, and the hooks are the ones described above. |
| Embeddium with *Use Compact Vertex Format* off | Not tested | By design the mod then leaves Embeddium's terrain alone: water keeps Embeddium's look and plants do not sway or change colour, until the option is switched back on. |
| Rubidium, and Embeddium versions other than 0.3.x | Not tested | The bridge does not apply, as with Sodium 0.6 on Fabric. |
| Embeddium + Oculus, no shader pack | Not tested | Expected to behave as Sodium + Iris does on Fabric. |
| Oculus with a shader pack | **Not tested** | The mod asks Oculus whether a pack is in use through the same API as Iris, and yields in the same way. |

What "Tested" means here: the automated scene run with those mods added to the dev client
(`gradlew :fabric:runAutotest -PlwCompat=sodium -PlwScenes=plains,water,seasons` and
`gradlew :forge:runAutotest -PlwCompat=embeddium -PlwScenes=plains,water,seasons`) completed with every
expectation passing and no OpenGL errors, and the screenshots were compared with the vanilla renderer's:
see-through water with reflections and caustics, grass leaning in the wind, and the four seasons' colours all
appear. It is a smoke test of three scenes on one machine (NVIDIA RTX 3050), not a soak test. The other scenes
(desert, snow, jungle and the rest) were last run with Sodium before the water, sky and season work, and not
since; they have never been run with Embeddium. Sodium + Iris was run before the project was split into
modules for the two loaders and has not been run again since. The newest 1.20.1 builds of Sodium (0.5.13) and
Iris (1.7.6) have not been tried.

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
  `common/src/assetgen/java/dev/livingworld/assetgen`).

## Known limitations

- Vegetation sway, seasonal foliage colours and realistic water need the vanilla terrain shaders, Sodium
  0.5 (Fabric) or Embeddium 0.3 with its compact vertex format (Forge), and no shader pack. On the vanilla
  renderer leaves additionally need Fancy graphics to sway and to change colour.
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
  after `WorldRenderEvents.LAST` (Fabric) or the `AFTER_LEVEL` stage (Forge) will not be fogged by it.
- Wet darkening, puddles and snow on the ground are screen-space and limited to about 40 blocks from the
  camera. In winter, grass and leaves are whitened at any distance, other surfaces only within that range.
- Test weather set with `/livingworld weather` is not saved across a server restart.
