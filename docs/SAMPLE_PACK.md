# Living World — recorded birds and frogs

Living World ships no audio files. Every sound it makes is synthesised while you play, and that includes
the birds and the frogs, which are the two sounds a synthesiser does worst. If you have recordings, a
resource pack can supply them and the mod will play them instead.

**No recordings come with the mod.** This page describes the hook and a template to fill in. You need
your own recordings, or ones whose licence lets you redistribute them.

## How it works

The mod declares two empty sound events:

| Sound event | Played |
|---|---|
| `livingworld:wildlife.birds` | By day, wherever the synthesised birdsong would be heard |
| `livingworld:wildlife.frogs` | At night near water and in swamps, wherever the synthesised frogs would be heard |

While an event has no sounds, the synthesiser does that animal's calls. As soon as a resource pack adds
at least one sound to an event:

- the synthesised layer for that animal falls silent (the rest of the soundscape carries on);
- the mod plays one recording at a time, **as often and as loudly as the synthesised animal would have
  called**. The same model still decides it: time of day, biome, weather, shelter, temperature and season;
- each call is placed in the world around you, so a mono recording is heard from a direction. Birds are
  placed 7 to 21 blocks away and 2 to 9 blocks above you, frogs 5 to 16 blocks away at about the height
  of your feet;
- each call is played at a slightly different pitch (within 6 % either way), so a small set of
  recordings does not sound like a loop.

How often: at the animal's full level a call starts about every two seconds, at its faintest about every
four to five. Birds never start less than 1.25 seconds apart, frogs 0.9 seconds.

The pack is noticed within five seconds of being switched on or off; no restart is needed. *Sound →
Recorded birds and frogs* in the mod's settings turns the recordings off without removing the pack. The
calls use the game's Ambient/Environment volume and the mod's *Ambience volume*.

## Making a pack

A ready-to-fill template is in [`docs/sample-pack-template`](sample-pack-template). Its layout:

```
pack.mcmeta
assets/livingworld/sounds.json
assets/livingworld/sounds/wildlife/birds/*.ogg
assets/livingworld/sounds/wildlife/frogs/*.ogg
```

1. Copy the folder into `resourcepacks/` and give it a name.
2. Put your recordings in the two folders.
3. List them in `assets/livingworld/sounds.json`. **Every file listed must exist**, and every name is
   written without `.ogg`:

   ```json
   {
     "wildlife.birds": {
       "sounds": [
         "livingworld:wildlife/birds/robin_01",
         "livingworld:wildlife/birds/robin_02",
         { "name": "livingworld:wildlife/birds/blackbird_01", "weight": 2 }
       ]
     },
     "wildlife.frogs": {
       "sounds": [
         "livingworld:wildlife/frogs/frog_01",
         "livingworld:wildlife/frogs/frog_02"
       ]
     }
   }
   ```

   Leave out an event you have no recordings for: the synthesiser keeps doing that animal. Do not add
   `"replace": true` (it is not needed) and do not list a file you have not added: an event that names a
   missing file counts as recorded and that animal goes silent.
4. Enable the pack in *Options → Resource Packs*.

### What the recordings should be

| | |
|---|---|
| Format | Ogg Vorbis (`.ogg`), 44.1 kHz |
| Channels | **Mono.** A stereo file is not placed in the world: it plays in both ears wherever it is. |
| Content | One call or one short phrase per file, with nothing before or after it. The mod supplies the pauses. |
| Length | About 1 to 6 seconds for birds, under 2 seconds for frogs. Two overlapping calls are fine, a 30-second file is not. |
| Background | As little as possible. Wind, traffic or other animals in a recording will switch on and off with every call. |
| Level | Similar across files, with some headroom. The mod plays them at 35 % to 100 % of their level, less with distance. |
| How many | Six or more per animal is enough for it not to sound repeated. |
| File names | Lower case, digits, `_`, `-` and `.` only. |

`pack_format` in `pack.mcmeta` is 15, the value for Minecraft 1.20.1.

## What has not been checked

The hook was written and compiled, and the game was run with no pack installed (the synthesiser keeps
working, the two empty events cause no warnings). **It has not been run with a pack of real
recordings**, so how the timing and levels sound with real birds has not been heard by anyone.
