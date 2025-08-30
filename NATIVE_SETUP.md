# Cubiomes Native Library Setup

This document explains how to set up the cubiomes native library for JNI integration with the Cubeium mod.

## Prerequisites

### CMake Installation

**Windows:**
1. Download CMake from https://cmake.org/download/
2. Install and make sure to check "Add CMake to system PATH"
3. Restart your terminal/IDE

**macOS:**
```bash
brew install cmake
```

**Linux (Ubuntu/Debian):**
```bash
sudo apt update
sudo apt install cmake build-essential
```

**Linux (CentOS/RHEL):**
```bash
sudo yum install cmake gcc gcc-c++ make
```

### Compiler Requirements

- **Windows:** Visual Studio 2019 or later, or MinGW-w64
- **macOS:** Xcode command line tools (`xcode-select --install`)
- **Linux:** GCC or Clang

## Building Native Libraries

### Automatic Build (Recommended)

The Gradle build script will automatically compile the cubiomes library:

```bash
./gradlew build
```

This will:
1. Configure CMake for the cubiomes library
2. Compile the shared library (.dll/.so/.dylib)
3. Copy the library to the appropriate resources directory
4. Include the library in the final JAR

### Manual Build

If you need to build manually or troubleshoot:

```bash
# Navigate to cubiomes directory
cd cubiomes

# Configure CMake
cmake -B build -S . -DCMAKE_BUILD_TYPE=Release

# Build the library
cmake --build build --config Release

# Libraries will be in cubiomes/lib/
```

### Individual Gradle Tasks

```bash
# Configure CMake only
./gradlew configureCubiomes

# Build native library only
./gradlew buildCubiomes

# Copy libraries to resources
./gradlew copyNativeLibraries

# Clean build artifacts
./gradlew cleanCubiomes
```

## Output Structure

After building, the native libraries will be organized as:

```
src/main/resources/natives/
├── windows/
│   └── x64/
│       └── cubiomes.dll
├── linux/
│   └── x64/
│       └── cubiomes.so
└── macos/
    └── x64/
        └── cubiomes.dylib
```

## Troubleshooting

### CMake Not Found
- Ensure CMake is installed and added to your system PATH
- Restart your terminal/IDE after installation
- Verify with `cmake --version`

### Compilation Errors
- Ensure you have the appropriate compiler toolchain installed
- Check that all source files are present in the cubiomes directory
- Review CMake output for specific error messages

### Architecture Issues
- The build automatically detects your system architecture
- For cross-compilation, modify the Gradle script or use CMake toolchain files

## JNI Integration

The compiled libraries are automatically included in the JAR and will be loaded by the JNI interface classes in the mod.
