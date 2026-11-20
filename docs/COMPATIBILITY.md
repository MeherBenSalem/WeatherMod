# Living World — compatibility

**Target:** Minecraft 1.20.1, Fabric Loader ≥ 0.15, Fabric API, Java 17.

## Client-only, server-only, or both

| Installed on | What you get |
|---|---|
| Client only (any server, including vanilla) | Every visual and audio effect. Wind and weather intensity are derived from the synchronised world time and vanilla weather, so they still match between players who have the mod. `/lw` gives you private test weather. No rain forecast (clouds gather when the rain starts rather than before). |
| Client and server | The above, plus `/livingworld` test commands that everyone sees, the rain forecast, and the optional survival temperature rules. |
| Server only | Commands and optional survival rules. Vanilla clients can join; they are never sent the mod's packets. |

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
| Mixin: `GameRenderer.tiltViewWhenHurt` (tail), `render` (after world) | Camera feedback, lens overlay | *Accessibility* options remove both. |
| Mixin: `SoundSystem.play` (head) | Delayed thunder, cave echo | Individual toggles under *Sound*. |
| Resource override: `minecraft:shaders/core/rendertype_cutout(_mipped)` | Vegetation sway | A resource pack that replaces the same shaders wins; sway then switches itself off. |
| Model wrapping (Fabric Model Loading + Renderer API) | Tags plant vertices for sway | Skipped entirely when Sodium is installed. |
| Resource override: `minecraft:shaders/core/rendertype_translucent` | Water shading (refraction, depth colour, reflections, glitter, caustics, swell). Glass, ice and everything else in the layer take the unchanged vanilla path. | A resource pack that replaces the same shader wins; water then keeps that pack's look. *Water Effects → Realistic water* off restores the vanilla look. |
| Mixin: `FluidRenderer.render` (head) / `vertex` (argument), both optional | Marks water vertices with a vertex alpha of 250/255 so the shader can tell water from glass | If another mod rewrites the fluid mesher the injection is skipped and water stays vanilla. Without the mod's shader the mark is a 2% change in water opacity. |
| `WorldRenderEvents.BEFORE_DEBUG_RENDER` | Copies the frame (colour and depth) just before translucent terrain, for the water shader to refract and mirror | Skipped with Sodium, with a shader pack, and when realistic water is off. |
| Mixin: `ItemEntityRenderer.render` (head, optional) | Dropped items that tumble and lie flat | *General → Dropped items lie on the ground* off returns the vanilla hover-and-spin. If another item-physics mod is installed, turn one of the two off. |

No blocks, block states, block entities or world data are ever modified.

## Rendering mods

| Setup | Status | Behaviour |
|---|---|---|
| Vanilla renderer (Fabric API's Indigo) | **Tested** | Everything. |
| Fabulous graphics | Not tested | Expected to work; translucent ordering of the mod's particles relative to water may differ slightly because they are drawn after the transparency composite, and the water shading has only been run on Fancy. |
| Sodium 0.5.x | **Tested** (0.5.11, together with Iris) | Everything except the two features that ride on the vanilla terrain shaders: **vegetation sway** and **realistic water** (Sodium has its own terrain shaders and fluid mesher, so water keeps Sodium's vanilla look). Bloom, the film look, sun glow, underwater caustics, footprints, weather, item physics and audio are unaffected. |
| Sodium + Iris, no shader pack | **Tested** (Sodium 0.5.11 + Iris 1.7.2) | As Sodium. Lit footprints, precipitation, particles, the atmosphere pass and the lens overlay all rendered correctly. |
| Iris with a shader pack | **Not tested** | By default (*Compatibility → Yield to shader packs*) the mod stops using its own programs: no atmosphere pass (no heat shimmer, mist, wet ground, puddles, cloud deck, eye adaptation, bloom, film look), no realistic water (the pack shades water), no lit footprints (simple pre-shaded prints instead), no lens droplets (frost falls back to a plain overlay). Precipitation, particles and footprints are drawn with the stock particle program, which the pack shades. Fog distance and colour still work. |
| Embeddium / Oculus | Not applicable | Those are Forge/NeoForge ports. This is a Fabric mod. |

What "Tested" means here: the automated scene run (`gradlew runAutotest -PlwCompat=sodium,iris`,
desert, snowy plains and jungle scenes) completed with every expectation passing and no OpenGL errors,
and the screenshots were checked. **That Sodium/Iris run predates the water shading, bloom, film look
and item physics**: those were added afterwards and have only been run on the vanilla renderer. The
water code is written to stand down under Sodium, but that has not been re-run. It is a smoke test on one machine, not a soak test. The newest
1.20.1 builds of Sodium (0.5.13) and Iris (1.7.6) could not be loaded into this project's dev
environment (they need a newer Loom to remap), so those exact versions are untested.

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

- Vegetation sway and realistic water need the vanilla terrain shaders (not Sodium, not shader packs);
  leaves additionally need Fancy graphics to sway.
- Water is shaded from a copy of the frame taken before translucent terrain is drawn. Translucent things
  behind a water surface (stained glass, ice, slime, another water surface) are therefore not visible
  through it, and reflections can only show what is on screen.
- Bloom works on the final low-dynamic-range image, so very bright ordinary surfaces (white blocks in
  full sun) glow slightly as well as true light sources.
- The atmosphere pass reads the depth buffer after the world is drawn. Mods that render into the world
  after `WorldRenderEvents.LAST` will not be fogged by it.
- Wet darkening, puddles and fresh snow are screen-space and limited to about 40 blocks from the camera.
- Test weather set with `/livingworld weather` is not saved across a server restart.
