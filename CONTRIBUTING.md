# Contributing

Thanks for helping improve Living World.

## How to contribute

1. Fork the repository and create a branch from `main`.
2. Make focused changes in the relevant Minecraft version workspace (`1.20.1/`, `1.21.1/`, `26.2/` or `26.3/`).
   A gameplay change usually belongs in all four: the `common/` code is nearly identical between them, and only the
   version-specific APIs differ (26.2 and 26.3 have no shader-based visuals, see the README).
3. Build the workspace you changed (`./gradlew build` from that folder: Gradle on Java 21 for 1.20.1 and 1.21.1,
   Java 25 for 26.x), or all four with `./gradlew buildAll` from the root (the 8 loader jars land in `releases/`).
   This also runs the unit tests and the client-isolation check.
4. Open a pull request describing the change and how you tested it.

## Layout

Each workspace is a MultiLoader Gradle build:

- `common/`: the simulation (climate, wind, weather, seasons), server sync, commands and seasonal gameplay rules, and
  the client code (environment state, renderers, particles, footprints, synthesised audio, settings screen, HUD).
  Nearly all changes happen here.
- `fabric/` and `neoforge/` (`forge/` on 1.20.1): entry points that forward the loader's events, networking, the sound
  stream hook and the config-screen button to the common code.

Version-specific code is kept in a few places: the mixins and renderers in `client/`, the GUI calls of the settings
screen and HUD, the saved-data classes, and the loader modules.

## Testing in game

On 1.20.1 and 1.21.1, `gradlew :fabric:runAutotest` (and the Forge/NeoForge twin) boots a dev client, walks through
scripted scenes and writes screenshots and a report; see "Testing" in the README. On 26.2 and 26.3 use
`gradlew :fabric:runClient` / `:neoforge:runClient` and check by hand. If you change gameplay, say which
versions and loaders you ran.

## Coding expectations

- Prefer small, reviewable PRs over large mixed changes.
- Match existing naming, package layout, and MultiLoader patterns.
- Keep anything that names rendering or audio inside the client packages: a dedicated server must never load it.
- By default the mod changes nothing in the world; a rule that places or removes blocks must stay opt-in.
- Do not commit build output, IDE metadata, or local secrets.
- When fixing a bug, say which version/loaders you verified.

## Issues

Use GitHub Issues for bugs and feature requests. Include Minecraft version, mod loader, mod version, and steps to
reproduce when reporting bugs.

## Licensing

By contributing, you agree that your contributions are licensed under the MIT License, unless you state otherwise in
the pull request.
