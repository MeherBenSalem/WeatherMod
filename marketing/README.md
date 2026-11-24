# Marketing

Listing copy and artwork for CurseForge (NightBeam publishes to CurseForge only). Nothing here ships in the mod jar.

| Path | What it is |
|------|------------|
| `curseforge/description.md` | The project description (Markdown). Images load from this repo's `main` branch. |
| `curseforge/summary.txt` | The one-line summary (the limit is 250 characters). |
| `images/banner.png` | 1800×600 header banner. |
| `images/icon.png` | 512×512 project icon: the mod's in-jar icon (`common/src/main/resources/assets/livingworld/icon.png`, 128×128) redrawn at listing size. |
| `images/card-*.png` | 1600×900 feature cards used in the description and as gallery images. |
| `images/header-*.png`, `images/divider.png` | Transparent section headers (1400×120) and a divider (1400×44) for the description. |
| `images/screenshots/*.jpg` | Real in-game captures, 1920×1080, used by the cards and the gallery. |
| `images/gifs/*.gif` | Short animations, 640×360, made from runs of in-game frames. |
| `source/render.cjs` | Renders every PNG above from the screenshots and the bundled fonts. |
| `source/collect.py` | Turns the output of the capture run into `images/screenshots` and `images/gifs`. |
| `source/fonts/` | Playfair Display, Barlow and Barlow Condensed, all under the SIL Open Font License (licence files included). |

The look: a sky through a day. Night blue behind everything with the faint rings of a weather map, a warm glow where
the sun would be, white-to-gold serif titles in Playfair Display (the second line in italics) over clean Barlow text,
a thin "horizon" rule that runs from sun gold through dusk violet to water teal, and one colour per subject: teal for
water, gold for the sky and the settings, orange for the seasons, violet for storms, green for wind and tracks, ice
blue for winter.

## Before the listing goes live

- **The images load from `raw.githubusercontent.com/MeherBenSalem/WeatherMod/main/marketing/images/...`.** That only
  works for a **public** repository with this folder on `main`. While the repository is private, every image in the
  description is a broken link: either make the repository public, or upload the images to the CurseForge
  gallery and replace each URL in `description.md`.
- The **Docs** link in the description points at `https://nightbeam.dev/docs/living-world`. That page has to exist,
  or the link has to go.
- The description says the mod is **MIT**-licensed (it is: see `LICENSE`) and needs only Fabric API on Fabric (nothing on Forge and NeoForge).
- Every number in the description and on the cards comes from the code: the presets from `QualityPreset`, the
  seasons from `Seasons` and `ServerConfig`, the growth rates from `Seasons.growth`. If one of those changes, the
  copy and the card that quotes it have to follow.

## Regenerating the images

Needs Node.js. With Playwright and its Chromium installed it uses that; without it, it starts a headless Edge or
Chrome from the command line, so nothing has to be installed on a machine that has either browser:

```bash
node marketing/source/render.cjs               # everything
node marketing/source/render.cjs card-water    # one asset
NODE_PATH="$(npm root -g)" node marketing/source/render.cjs   # with a globally installed Playwright
```

The version and what the mod runs on are at the top of `render.cjs`. A new Minecraft version or loader is one line
there, plus the platform line in `description.md`.

## Retaking the screenshots and clips

```bash
./gradlew :fabric:runAutotest -PlwScenes=marketing     # everything, about twenty minutes
./gradlew :fabric:runAutotest -PlwScenes=mk_water,mk_sky    # or a few groups
python marketing/source/collect.py             # PNG -> JPEG, frame runs -> GIF (needs Pillow)
node marketing/source/render.cjs
```

The capture run is the mod's own scripted test client (`common/src/main/java/dev/livingworld/client/debug/Marketing.java`):
a dev client on the vanilla renderer, a new world with the test seed `8765432123456`, the **Ultra** preset, render
distance 14, HUD hidden, in a borderless 1920×1080 window. The window is placed just past the right edge of the
primary monitor so that it neither covers the screen nor takes clicks; add
`-Dlivingworld.autotest.onscreen=true` to the JVM arguments to watch it. Output goes to
`run/livingworld-autotest/screenshots/mk_*.png` and `run/livingworld-autotest/clips/mk_*/`.

Groups: `mk_water`, `mk_sky`, `mk_storm`, `mk_seasons`, `mk_calendar`, `mk_winter`, `mk_weather`, `mk_world`,
`mk_puddles`, `mk_ui`.

**Every screenshot is a single unedited frame** (converted from PNG to JPEG, nothing else). What is staged is the
moment: the time of day, the weather, the wind, the season and the amount of fair-weather cloud are set with commands
so that each picture happens on cue, and the camera is put where the view is good. Everything the mod draws in them
is what it draws in normal play. The table says what was set up for each.

| Screenshot | Scene | Staged |
|------------|-------|--------|
| `lake_noon.jpg`, `lake_shallows.jpg` | A lake in a taiga from its beach, facing west, at noon in a light breeze. | Time, wind 0.3, fair cloud. The spot is found by the script: the beach near the first one found with the most water to its west. |
| `lake_wind.jpg` | The same shore in a strong wind: waves, surf running in, damp sand. | Wind 0.85. |
| `lake_sunset.jpg`, `lake_dusk.jpg` | The same shore with the sun going down over the water, and a little later. | Time 12150 and 12600. |
| `lake_rain.jpg` | The same shore in rain. | `/livingworld weather rain`. |
| `sky_noon.jpg`, `sky_cloud_shadows.jpg` | The cloud layer over plains at noon, and its shadows on the land below. | Taken from the top of a 32-block pillar built under the camera. Fair cloud set to 0.36. |
| `sky_sunset.jpg` | Sunset from the same pillar, facing west. | Time 12250. |
| `sky_night.jpg` | The Milky Way and the stars overhead at midnight. | Time 18000, fair cloud 0.14. |
| `sky_aurora.jpg` | Northern lights over snowy plains, facing north. | Taken from a 10-block pillar. **The aurora is forced on** with the test hook `Atmosphere.forceAurora`; in play it appears on some cold, clear nights. |
| `storm.jpg` | A thunderstorm over the plains, from the same kind of pillar. | `/livingworld weather thunderstorm`. |
| `lightning.jpg` | A stroke at dusk beside a lake, lighting the land around it. | The bolt is a real `lightning_bolt` summoned 58 blocks ahead; time 13300. |
| `season_spring.jpg` … `season_winter.jpg` | One view over a forest in the four seasons. | `/livingworld season set`, from a 4-block pillar on a hilltop the script picks for its view. |
| `calendar_hud.jpg` | The same forest in autumn with the HUD on and the calendar enabled. | *Climate & Temperature → Calendar* on. |
| `winter_before.jpg`, `winter_frozen.jpg`, `winter_lake.jpg` | A river in a taiga in summer, then frozen and snowed on by the winter rule. | `/livingworld season snow on`, winter, real rain for 40 seconds, and `seasonalChangeRate` raised from 16 to 256 so that it happens in a minute instead of a day. The frost overlay on the lens is switched off, because in this cold it frames the picture in white. |
| `dawn_mist.jpg` | A forest just before sunrise. | Time 23600. |
| `rain_forest.jpg` | Heavy rain in the forest. | `/livingworld weather heavy_rain`. |
| `stony_cove.jpg` | A stony shore after heavy rain: damp rock, clear water, foam at the rock's edge. | `/livingworld weather heavy_rain` for 35 seconds, then clear. |
| `puddles.jpg` | Puddles on paving after heavy rain. | **The yard is built by the script**: see the note below the table. |
| `desert_heat.jpg` | A desert at noon. | Nothing but the time. |
| `sandstorm.jpg` | A sandstorm at full strength. | `/livingworld weather sandstorm 1.0`. |
| `snowy_plains.jpg`, `blizzard.jpg` | Snowy plains in the morning, then in a blizzard. | `/livingworld weather blizzard`. |
| `meadow_wind.jpg` | A meadow in a strong wind. | Grass and flowers are planted by the script on the grass in front of the camera; wind 1.0; taken from a 3-block pillar. |
| `footprints_walk.jpg`, `footprints_sand.jpg` | The player walking across the desert, and the trail left behind. | The player is walked 70 ticks by the script. |
| `snow_trail.jpg` | A trail across the snow. | As above. |
| `settings_*.jpg` | Four pages of the settings screen. | Opened by the script. |

Puddles collect on ground that holds water, and grass does not hold much, so a meadow after rain shows almost none.
For `puddles.jpg` the script paves a yard in front of the camera (stone bricks with patches of cobblestone, andesite
and mossy and cracked bricks, two lantern posts and a low wall), clears the air above it, and then lets the mod rain
on it. The spot the script lands on is a hillside, so the yard ends up cut into it. The puddles, their outlines and
their reflections are the mod's.

| Animation | What it is |
|-----------|------------|
| `gifs/lake_wind.gif` | 3.6 s of the lake in a strong wind, at the speed it was recorded (one frame every two ticks). |
| `gifs/dusk.gif` | A time-lapse: 60 frames while the clock is moved from 11300 to 13700, late afternoon to the first stars. The clouds drift at their real speed, so they barely move. |
| `gifs/aurora.gif` | 5 s of the northern lights (forced on, as for the screenshot). |
| `gifs/rain.gif` | 3.6 s of heavy rain in the forest. |
| `gifs/meadow.gif` | 3.4 s of the meadow in the wind. |

A GIF has at most 256 colours, so the animations are coarser than the game: skies are dithered, and fine detail in
the water is lost. They show how things move, the screenshots show how they look.

## What is not shown

- **Rainbows.** They are in the mod and in the description, but both attempts to photograph one came out too faint
  to be worth a picture, so there is none here.
- **Rain rings on water.** `lake_rain.jpg` shows rain over the lake; the rings on the surface do not read in a still.
- **Sodium.** Every picture is from the vanilla renderer. With Sodium 0.5.11 the water, sway and seasonal colours were
  compared with the vanilla renderer in the test run and look the same, but none of those captures are used here.
