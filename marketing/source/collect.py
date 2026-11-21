#!/usr/bin/env python3
"""Turns the output of the capture run into the listing's screenshots and animations.

  ./gradlew :fabric:runAutotest -PlwScenes=marketing   # captures to run/livingworld-autotest (see marketing/README.md)
  python marketing/source/collect.py                 # -> marketing/images/screenshots/*.jpg, marketing/images/gifs/*.gif
  python marketing/source/collect.py run/a run/b     # take each capture from the first folder that has it

A screenshot is one frame of the game, converted from PNG to JPEG and otherwise untouched. An animation is a run of
such frames, scaled down and reduced to a GIF palette. Needs Pillow.
"""
from __future__ import annotations

import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'marketing' / 'images'

# listing name -> capture name (run/livingworld-autotest/screenshots/<capture>.png)
SCREENSHOTS = {
    'lake_noon': 'mk_water_noon',
    'lake_wind': 'mk_water_surf',
    'lake_shallows': 'mk_water_shallows',
    'lake_sunset': 'mk_water_sunset',
    'lake_dusk': 'mk_water_dusk',
    'lake_rain': 'mk_water_rain',
    'sky_noon': 'mk_sky_noon',
    'sky_cloud_shadows': 'mk_sky_cloud_shadows',
    'sky_sunset': 'mk_sky_sunset',
    'sky_night': 'mk_sky_night_overhead',
    'sky_aurora': 'mk_sky_aurora_north',
    'storm': 'mk_storm',
    'lightning': 'mk_lightning_1b',
    'season_spring': 'mk_season_spring',
    'season_summer': 'mk_season_summer',
    'season_autumn': 'mk_season_autumn',
    'season_winter': 'mk_season_winter',
    'calendar_hud': 'mk_calendar_hud',
    'winter_before': 'mk_winter_before',
    'winter_frozen': 'mk_winter_frozen',
    'winter_lake': 'mk_winter_frozen_left',
    'dawn_mist': 'mk_dawn_mist',
    'rain_forest': 'mk_rain_forest',
    'puddles': 'mk_puddles_noon',
    'stony_cove': 'mk_puddles_shore',
    'desert_heat': 'mk_desert_heat',
    'sandstorm': 'mk_sandstorm',
    'snowy_plains': 'mk_snowy_plains',
    'blizzard': 'mk_blizzard',
    'meadow_wind': 'mk_meadow_wind',
    'footprints_walk': 'mk_footprints_walk',
    'footprints_sand': 'mk_footprints_sand',
    'snow_trail': 'mk_snow_trail',
    'settings_water': 'mk_settings_water',
    'settings_sky': 'mk_settings_fog',
    'settings_climate': 'mk_settings_climate',
    'settings_graphics': 'mk_settings_graphics',
}

# listing name -> (clip, first frame, frames, milliseconds per frame, colours, dither)
# The clips are recorded one frame every two ticks (a tenth of a second), so 100 ms plays them at their own speed.
# The time-lapse covers 2,400 ticks of the day in its 60 frames. Dither is for skies: without it a smooth gradient
# breaks into bands; with it the file is larger.
GIFS = {
    'lake_wind': ('mk_water_wind', 0, 36, 100, 128, False),
    'dusk': ('mk_dusk', 0, 60, 110, 128, True),
    'aurora': ('mk_aurora', 0, 50, 100, 96, True),
    'rain': ('mk_rain', 0, 36, 100, 128, False),
    'meadow': ('mk_meadow', 0, 34, 100, 128, False),
}
GIF_SIZE = (640, 360)


def find(sources: list[Path], relative: str) -> Path | None:
    for source in sources:
        candidate = source / relative
        if candidate.exists():
            return candidate
    return None


def screenshots(sources: list[Path], wanted: set[str]) -> None:
    target = OUT / 'screenshots'
    target.mkdir(parents=True, exist_ok=True)
    for name, capture in SCREENSHOTS.items():
        if wanted and name not in wanted:
            continue
        png = find(sources, f'screenshots/{capture}.png')
        if png is None:
            print(f'missing  {capture}.png (for {name}.jpg)')
            continue
        image = Image.open(png).convert('RGB')
        image.save(target / f'{name}.jpg', quality=92, optimize=True)
        print(f'saved    screenshots/{name}.jpg  {image.width}x{image.height}  from {png.parent.parent.name}/{capture}.png')


def gifs(sources: list[Path], wanted: set[str]) -> None:
    target = OUT / 'gifs'
    target.mkdir(parents=True, exist_ok=True)
    for name, (clip, first, count, duration, colours, dither) in GIFS.items():
        if wanted and name not in wanted:
            continue
        folder = find(sources, f'clips/{clip}')
        if folder is None:
            print(f'missing  clip {clip} (for {name}.gif)')
            continue
        files = sorted(folder.glob('f*.png'))[first:first + count]
        frames = [Image.open(f).convert('RGB').resize(GIF_SIZE, Image.LANCZOS) for f in files]
        # One palette for the whole clip (taken from four frames across it), so colours do not shimmer.
        strip = Image.new('RGB', (GIF_SIZE[0], GIF_SIZE[1] * 4))
        for row, index in enumerate((0, len(frames) // 3, 2 * len(frames) // 3, len(frames) - 1)):
            strip.paste(frames[index], (0, row * GIF_SIZE[1]))
        palette = strip.quantize(colors=colours, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        mode = Image.Dither.FLOYDSTEINBERG if dither else Image.Dither.NONE
        reduced = [frame.quantize(palette=palette, dither=mode) for frame in frames]
        path = target / f'{name}.gif'
        reduced[0].save(path, save_all=True, append_images=reduced[1:], duration=duration, loop=0, disposal=1)
        seconds = len(reduced) * duration / 1000
        print(f'saved    gifs/{name}.gif  {GIF_SIZE[0]}x{GIF_SIZE[1]}  {len(reduced)} frames  {seconds:.1f} s  '
              f'{path.stat().st_size / 1e6:.1f} MB')


def main() -> None:
    args = sys.argv[1:]
    wanted = {a for a in args if not Path(a).is_dir()}
    sources = [Path(a) for a in args if Path(a).is_dir()] or [ROOT / 'run' / 'livingworld-autotest']
    screenshots(sources, wanted)
    gifs(sources, wanted)


if __name__ == '__main__':
    main()
