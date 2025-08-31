# Cubiomes Build Directory

This directory contains a copy of the essential files from the [Cubiomes](https://github.com/Cubitect/cubiomes) library, which is used by Cubeium for biome generation and analysis.

## License

Cubiomes is licensed under the MIT License (see `LICENSE` file). This permissive license allows us to copy, modify, and distribute the library as part of Cubeium.

**Original Copyright:** Copyright (c) 2020 Cubitect

## Purpose

This directory exists to ensure that:
1. **GitHub Actions can build Cubeium** without requiring external dependencies
2. **Build reproducibility** - specific version is locked to ensure consistent builds
3. **Offline development** - no need for separate cubiomes checkout
4. **CI/CD reliability** - builds won't fail due to external repository issues

## Files Included

### Core Library Files
- **Source files (*.c)**: Core cubiomes implementation
  - `biomenoise.c/h` - Biome noise generation
  - `biomes.c/h` - Biome definitions and utilities
  - `finders.c/h` - Structure finding algorithms  
  - `generator.c/h` - World generator interface
  - `layers.c/h` - Biome generation layers
  - `noise.c/h` - Noise generation functions
  - `quadbase.c/h` - Quad-tree based optimizations
  - `util.c/h` - Utility functions
  - `rng.h` - Random number generation

### JNI Integration
- **cubeium_jni.c** - Custom JNI wrapper for Cubeium integration
- **cubeium_cubeium_world_CubiomesInterface.h** - Generated JNI headers

### Build Configuration
- **CMakeLists.txt** - CMake build configuration
- **tables/** - Essential data tables for biome generation

### Documentation
- **LICENSE** - MIT License from original cubiomes project
- **README.md** - This file

## Building Cubiomes

### Prerequisites
- **CMake** 3.10 or higher
- **C Compiler** (GCC, Clang, or MSVC)
- **JDK** (for JNI header generation)

### Build Steps

#### Using Gradle (Automated and recommended)
Cubeium's Gradle build automatically handles building cubiomes:
```bash
# From Cubeium root directory
./gradlew build
```

#### Windows (with Visual Studio)
```batch
# Navigate to cubiomesBuild directory
cd cubiomesBuild

# Create build directory
mkdir build
cd build

# Configure with CMake
cmake .. -G "Visual Studio 17 2022"

# Build the library
cmake --build . --config Release

# Built library will be in build/lib/
```

#### Linux/macOS
```bash
# Navigate to cubiomesBuild directory
cd cubiomesBuild

# Create and enter build directory
mkdir -p build && cd build

# Configure with CMake
cmake .. -DCMAKE_BUILD_TYPE=Release

# Build the library
make -j$(nproc)

# Built library will be in build/lib/
```

## Integration with Cubeium

The built cubiomes library is automatically:
1. **Built by Gradle** during the normal build process
2. **Linked with Java** through JNI interface
3. **Loaded at runtime** by the CubiomesInterface class

## Updating Cubiomes

When the upstream cubiomes library is updated:

### Manual Update Process
1. **Clone/update** the official cubiomes repository
2. **Compare versions** to identify changes
3. **Copy files** to this directory:
   ```bash
   # From cubiomes source directory
   cp *.c *.h ../cubeium/cubiomesBuild/
   cp CMakeLists.txt ../cubeium/cubiomesBuild/
   cp -r tables/ ../cubeium/cubiomesBuild/
   ```
4. **Update version** information in this README
5. **Test the build** to ensure compatibility
6. **Update JNI wrapper** if API changes occurred

### Compatibility Notes
- **API changes** may require updates to `cubeium_jni.c`
- **New biomes** may need color mappings in Cubeium
- **Structure changes** may affect the CubiomesInterface

## Version Information

**Current Cubiomes Version:** Latest from master branch (as of integration)
**Last Updated:** December 2024
**Compatible MC Versions:** 1.7 - 1.21+

## File Structure
```
cubiomesBuild/
├── README.md                    # This file
├── LICENSE                      # MIT License
├── CMakeLists.txt              # Build configuration
├── cubeium_jni.c               # JNI wrapper
├── cubeium_cubeium_world_CubiomesInterface.h  # JNI headers
├── *.c *.h                     # Core cubiomes source files
└── tables/                     # Essential data tables
    ├── btree18.h
    ├── btree19.h
    ├── btree192.h
    ├── btree20.h
    └── btree21wd.h
```

## Build Troubleshooting

### Common Issues

**"JNI headers not found"**
- Ensure JDK is installed and `JAVA_HOME` is set
- Install JDK development packages on Linux

**"CMake not found"**
- Install CMake from https://cmake.org/
- Ensure CMake is in your system PATH

**"Compiler not found"**
- Windows: Install Visual Studio Build Tools
- Linux: Install build-essential package
- macOS: Install Xcode Command Line Tools

**"Library not loading in Java"**
- Check that library is built for correct architecture (x64/ARM)
- Verify library is in expected location (`src/main/resources/natives/`)
- Check Java library path settings

### Debug Build
For debugging cubiomes issues:
```bash
cmake .. -DCMAKE_BUILD_TYPE=Debug
```

## Contributing

When modifying cubiomes integration:
1. **Test thoroughly** with different seeds and MC versions
2. **Update JNI wrapper** if changing API
3. **Document changes** in this README
4. **Verify compatibility** with existing Cubeium features

## Support

For issues related to:
- **Cubiomes library**: See [Cubitect/cubiomes](https://github.com/Cubitect/cubiomes)
- **JNI integration**: Check Cubeium's CubiomesInterface class
- **Build problems**: Refer to troubleshooting section above

---

*This is a snapshot of cubiomes integrated into Cubeium for build reliability. The original project can be found at https://github.com/Cubitect/cubiomes*
