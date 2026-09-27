#!/usr/bin/env bash
# Cross-compiles native/prebuilt/windows/x64/libcubeium.dll from Linux or WSL,
# so the jar can ship a Windows library even when it is built elsewhere.
#
# Requirements:
#   - cmake
#   - a MinGW-w64 UCRT cross compiler: x86_64-w64-mingw32-gcc on PATH, e.g.
#     llvm-mingw (https://github.com/mstorsjo/llvm-mingw, "ucrt-ubuntu" release)
#   - WINDOWS_JDK_INCLUDE: the include/ directory of any Windows JDK (needs win32/jni_md.h),
#     e.g. "/mnt/c/Program Files/Eclipse Adoptium/jdk-25.0.4.7-hotspot/include"
#   - the javac-generated JNI header: run ./gradlew compileClientJava first
#
# Usage: WINDOWS_JDK_INCLUDE=... native/build-windows-dll.sh

set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
cc="${CC_WINDOWS:-x86_64-w64-mingw32-gcc}"
header_dir="$root/build/generated/jni"
build_dir="$root/build/native/windows-x64-cross"
out_dir="$root/native/prebuilt/windows/x64"

: "${WINDOWS_JDK_INCLUDE:?set WINDOWS_JDK_INCLUDE to a Windows JDK include/ directory}"
[[ -f "$WINDOWS_JDK_INCLUDE/win32/jni_md.h" ]] || { echo "no win32/jni_md.h in $WINDOWS_JDK_INCLUDE" >&2; exit 1; }
[[ -f "$header_dir/cubeium_cubeium_world_CubiomesInterface.h" ]] || { echo "JNI header missing: run ./gradlew compileClientJava" >&2; exit 1; }
command -v "$cc" >/dev/null || { echo "$cc not found on PATH" >&2; exit 1; }

cmake -S "$root/native" -B "$build_dir" \
    -DCMAKE_SYSTEM_NAME=Windows \
    -DCMAKE_C_COMPILER="$cc" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCUBEIUM_JNI_HEADER_DIR="$header_dir" \
    -DCUBEIUM_JNI_INCLUDE="$WINDOWS_JDK_INCLUDE" \
    -DCUBEIUM_JNI_PLATFORM=win32
cmake --build "$build_dir" --parallel

mkdir -p "$out_dir"
cp "$build_dir/libcubeium.dll" "$out_dir/libcubeium.dll"

# Must match nativeSourceFingerprint() in build.gradle.
submodule_commit="$(git -C "$root/native/cubiomes" rev-parse HEAD)"
fingerprint="$(
    cd "$root"
    { for f in native/cubeium_jni.c native/CMakeLists.txt src/client/java/cubeium/cubeium/world/CubiomesInterface.java; do
          tr -d '\r' < "$f"
      done
      printf '%s' "$submodule_commit"
    } | sha256sum | cut -d' ' -f1
)"

cat > "$out_dir/BUILD_INFO.txt" <<EOF
cubiomes=$submodule_commit
compiler=$("$cc" --version | head -1)
sources=$fingerprint
EOF

echo "Wrote $out_dir/libcubeium.dll"
cat "$out_dir/BUILD_INFO.txt"
