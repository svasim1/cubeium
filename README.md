# Cubeium

An in-game seed map for Minecraft (Fabric): explore a seed's biomes and structures without leaving
the game. World generation is computed by [cubiomes](https://github.com/xpple/cubiomes) (the actively
maintained xpple fork) through its Java bindings.

Targets **Minecraft 26.3** (Java 25, Fabric Loader 0.19.5+, Fabric API).

## Using it

Press **M** in game to open the map. In singleplayer the world's seed is filled in automatically;
elsewhere, type it in (text seeds work exactly like on the Create World screen). Each world
remembers its own seed.

- Drag to pan, scroll or use **+ / −** to zoom, arrow keys to move, **Ctrl+1/2/3** to switch tabs.
- Hover for the biome and coordinates; hover a marker for the structure; right-click for a menu
  (copy coordinates, center here, and teleport when enabled and permitted).
- Switch between Overworld, Nether and End with the dimension button.
- **Map** tab: region grid, coordinate axes, go to coordinates.
- **Structures** tab: every structure cubiomes supports for the dimension, shown with vanilla item
  icons. Very common ones (ruined portals, shipwrecks, ocean ruins, trial chambers, mineshafts,
  buried treasure) start switched off.
- **Biomes** tab: highlight chosen biomes; everything else is dimmed.
- **Settings** (gear): dark mode, floating tooltip, marker labels, teleport in menu, keep map
  position, render metrics.

## Accuracy

`./gradlew runClientGameTest` starts the real 26.3 client and compares Cubeium with Minecraft's
own world generation for a test world:

- Biomes: identical at the map's sampling height in all three dimensions (20,000 samples).
  cubiomes has rare edge cases for underground cave biomes, which the map does not show.
- Strongholds: all 128 match.
- Structures: per structure, every generation attempt near spawn is checked against Minecraft's
  placement and structure-start rules. Biome-only structures match exactly; those that also
  depend on terrain height (villages, pyramids, jungle temples, trail ruins, abandoned camps,
  mansions) agree for 97-99.6% of attempts because cubiomes can only estimate terrain.

## Building

```sh
./gradlew build
```

The jar ends up in `build/libs/`. The cubiomes bindings (with native libraries for Windows x64,
Linux x64 and macOS x64/arm64) are bundled inside it; no C toolchain is needed. The bindings
version in `gradle.properties` pins the cubiomes commit after the `+`.

### Tests

- `./gradlew test`: world generation (biome ground truth from real worlds, strongholds,
  structures, thread safety) and map view math.
- `./gradlew runClientGameTest`: the in-game comparison above, plus screenshots of every screen
  in `build/run/clientGameTest/screenshots/`.

## License

MIT (see `LICENSE`). cubiomes is MIT licensed as well; its license ships inside the bundled
bindings jar.
