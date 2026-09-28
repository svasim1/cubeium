<p align="center"><img src="docs/banner.png" alt="Cubeium" width="600"></p>

# Cubeium

An in-game seed map for Minecraft. Press **M** to see your world's biomes, structures and slime
chunks without leaving the game.

Client-side Fabric mod for **Minecraft 26.3** (Fabric API required).

## Features

- Biome map for the Overworld, Nether and End, with search and highlighting
- Structure markers with vanilla icons, including End City ships and End gateways
- Waypoints, slime chunks, region grid and go-to-coordinates
- Your position shown on the linked Overworld/Nether map (coordinates / 8)
- Seed filled in automatically in singleplayer; type it in on servers
- Vanilla look, with an optional dark mode

Don't know the seed of a server? [SeedCrackerX](https://github.com/19MisterX98/SeedcrackerX) can
find it for you, and pairs well with Cubeium.

## Accuracy

An automated in-game test compares the map with Minecraft's own world generation: biomes,
strongholds and slime chunks match exactly. Structures that depend on terrain height, like
villages, match for 97-99% of the checked spots.

Worlds using the Large Biomes preset are not supported.

## Background

Cubeium started in November 2024 as an idea for my final school project. I wanted something like [mcseedmap.net](https://mcseedmap.net) inside the
game, and it has slowly grown from there into this mod.

About the name: there is a different mod called [Cubium](https://github.com/HenriTom/Cubium), an
in-game web browser. The two are unrelated, and I'm not trying to borrow from it. I've just liked
the name Cubeium since I started this project and wanted to keep it.

## About AI use

I've been a developer for years, but I don't have the time or energy to write a project like
this by hand anymore. Most of Cubeium's code was written with AI.
I decided what the mod should do and how it should look, and tested it in game. The accuracy
tests above are there so you don't have to take the AI's word for it.

## Thanks

- [Cubitect](https://github.com/Cubitect/cubiomes) for cubiomes, which does the world generation.
- [xpple](https://github.com/xpple) for keeping cubiomes up to date with new Minecraft versions
  and for its Java bindings, which Cubeium uses. xpple also makes
  [SeedMapper](https://github.com/xpple/SeedMapper), a more feature-packed seed map mod that I
  only found recently. Check it out if you want more than a map.
- [mcseedmap.net](https://mcseedmap.net) for the idea in the first place.

## Building

```sh
./gradlew build
```

The jar ends up in `build/libs/`. `./gradlew runClientGameTest` runs the in-game accuracy test.

## License

Cubeium is MIT licensed (see `LICENSE`).

The jar bundles, unmodified, xpple's [cubiomes Java bindings](https://github.com/xpple/cubiomes/tree/java-bindings)
(LGPL-3.0), which include [cubiomes](https://github.com/Cubitect/cubiomes) by Cubitect (MIT).
Their license texts ship inside the bundled jar.

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
