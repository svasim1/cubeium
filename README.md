# Cubeium

An in-game seed map for Minecraft (Fabric), so you can explore a seed's biomes without leaving
the game. World generation is computed by [cubiomes](https://github.com/xpple/cubiomes)
(the actively maintained xpple fork), called through JNI.

Press **M** in game to open the map. In singleplayer the current world's seed is filled in
automatically; otherwise type a seed (text seeds work exactly like in the Create World screen).

Currently targets Minecraft 1.21.4 (Overworld biomes).

## Building

```sh
git clone --recurse-submodules https://github.com/svasim1/cubeium.git
# existing clone: git submodule update --init --recursive
./gradlew build
```

The jar ends up in `build/libs/`.

### Native library

cubiomes and the JNI bridge (`native/`) are compiled into `libcubeium.{so,dll,dylib}`:

- If **CMake** and a C compiler are installed (gcc/clang; on Windows MinGW-w64 UCRT64 — MSVC is
  not supported by cubiomes), Gradle builds the library for your platform automatically.
- The Windows library is also committed prebuilt in `native/prebuilt/`, so Windows builds work
  without a C toolchain. After changing `native/`, the cubiomes submodule, or
  `CubiomesInterface.java`, rebuild it (the build warns when it is out of date):

  ```sh
  ./gradlew compileClientJava
  WINDOWS_JDK_INCLUDE="/path/to/windows-jdk/include" native/build-windows-dll.sh
  ```

  This cross-compiles from Linux/WSL with [llvm-mingw](https://github.com/mstorsjo/llvm-mingw).

### Tests

- `./gradlew test` checks the JNI bridge and biome sampling against the native library, including
  biome positions taken from real Minecraft worlds.
- `./gradlew runClientGameTest` launches the real client, compares Cubeium's biomes with
  Minecraft's own biome source for a test world, and screenshots the map (`run/screenshots/`).

## License

MIT (see `LICENSE`). cubiomes is MIT licensed as well; its license ships in the jar as
`LICENSE_cubiomes`.
