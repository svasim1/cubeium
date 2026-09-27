# Builds the Cubeium JNI library: the xpple/cubiomes sources (git submodule in
# ./cubiomes) plus our JNI bridge, linked into a single shared library named
# libcubeium.{so,dll,dylib}. Normally invoked by Gradle (see build.gradle).
#
# Inputs:
#   CUBEIUM_JNI_HEADER_DIR  directory with the javac-generated JNI header (required)
#   CUBEIUM_JNI_INCLUDE     the JDK's include/ directory; when unset, FindJNI is used.
#                           Set it when cross-compiling, pointing at a JDK for the
#                           target platform (its include/<os>/jni_md.h is used).
#   CUBEIUM_JNI_PLATFORM    the include/<os> subdirectory name (linux, win32, darwin);
#                           defaults to the target platform.

cmake_minimum_required(VERSION 3.15)
project(cubeium_native C)

if (MSVC)
	message(FATAL_ERROR "MSVC is not supported by cubiomes. Use MinGW (UCRT64), Clang or GCC.")
endif()

if (NOT CMAKE_BUILD_TYPE)
	set(CMAKE_BUILD_TYPE Release)
endif()

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(CUBIOMES_DIR "${CMAKE_CURRENT_SOURCE_DIR}/cubiomes")
if (NOT EXISTS "${CUBIOMES_DIR}/generator.h")
	message(FATAL_ERROR "cubiomes sources not found in ${CUBIOMES_DIR}.\n"
		"Run: git submodule update --init --recursive")
endif()

if (NOT CUBEIUM_JNI_HEADER_DIR)
	message(FATAL_ERROR "CUBEIUM_JNI_HEADER_DIR is not set (the javac -h output directory).")
endif()

# JNI headers
if (CUBEIUM_JNI_INCLUDE)
	if (NOT CUBEIUM_JNI_PLATFORM)
		if (WIN32)
			set(CUBEIUM_JNI_PLATFORM win32)
		elseif (APPLE)
			set(CUBEIUM_JNI_PLATFORM darwin)
		else()
			set(CUBEIUM_JNI_PLATFORM linux)
		endif()
	endif()
	set(JNI_INCLUDE_DIRS "${CUBEIUM_JNI_INCLUDE}" "${CUBEIUM_JNI_INCLUDE}/${CUBEIUM_JNI_PLATFORM}")
else()
	find_package(JNI REQUIRED)
endif()

# Only the parts of cubiomes that Cubeium needs (no loot tables / cJSON).
set(CUBIOMES_SOURCES
	${CUBIOMES_DIR}/biomenoise.c
	${CUBIOMES_DIR}/biomes.c
	${CUBIOMES_DIR}/carver.c
	${CUBIOMES_DIR}/finders.c
	${CUBIOMES_DIR}/generator.c
	${CUBIOMES_DIR}/layers.c
	${CUBIOMES_DIR}/noise.c
	${CUBIOMES_DIR}/quadbase.c
	${CUBIOMES_DIR}/terrainnoise.c
	${CUBIOMES_DIR}/util.c
	${CUBIOMES_DIR}/xradv.c
	${CUBIOMES_DIR}/features/abandoned_camp.c
	${CUBIOMES_DIR}/features/end_city.c
	${CUBIOMES_DIR}/features/fortress.c
	${CUBIOMES_DIR}/features/ore.c
	${CUBIOMES_DIR}/features/stronghold.c
)

add_library(cubeium SHARED ${CUBIOMES_SOURCES} cubeium_jni.c)
target_include_directories(cubeium PRIVATE ${CUBIOMES_DIR} ${CUBEIUM_JNI_HEADER_DIR} ${JNI_INCLUDE_DIRS})
target_compile_options(cubeium PRIVATE $<$<CONFIG:Release>:-O3>)
set_source_files_properties(cubeium_jni.c PROPERTIES COMPILE_OPTIONS "-Wall;-Wextra")

# Same file name on every platform: libcubeium.{so,dll,dylib}, no soname versioning.
set_target_properties(cubeium PROPERTIES
	PREFIX "lib"
	C_VISIBILITY_PRESET default
)

if (WIN32)
	# Do not depend on MinGW runtime DLLs (libgcc_s, libwinpthread).
	target_link_options(cubeium PRIVATE -static -static-libgcc)
elseif (NOT APPLE)
	target_link_libraries(cubeium PRIVATE m)
endif()
