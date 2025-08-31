cmake_minimum_required(VERSION 3.10)
project(cubiomes VERSION 1.0.0 LANGUAGES C)

# Set C standard
set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

# Default to Release build if not specified
if (NOT CMAKE_BUILD_TYPE)
	set(CMAKE_BUILD_TYPE Release)
endif()

# Platform-specific flags
if (WIN32)
	set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D_WIN32")
	# Enable Windows DLL export symbols
	set(CMAKE_WINDOWS_EXPORT_ALL_SYMBOLS ON)
endif()

# Compiler flags
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Wextra")
set(CMAKE_C_FLAGS_DEBUG "-O0 -g3 -DDEBUG")
set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG")

# Include directories
include_directories(${CMAKE_CURRENT_SOURCE_DIR})

# Find JNI for header generation (optional)
find_package(JNI QUIET)
if(JNI_FOUND)
    include_directories(${JNI_INCLUDE_DIRS})
    message(STATUS "JNI found: ${JNI_INCLUDE_DIRS}")
endif()

# Source files
set(HEADERS
	finders.h
	generator.h
	layers.h
	biomenoise.h
	biomes.h
	noise.h
	rng.h
	util.h
	quadbase.h
)

set(SOURCES
	finders.c
	generator.c
	layers.c
	biomenoise.c
	biomes.c
	noise.c
	util.c
	quadbase.c
	cubeium_jni.c
)

# Create object library for both shared and static builds
add_library(cubiomes_objects OBJECT ${SOURCES})
set_property(TARGET cubiomes_objects PROPERTY POSITION_INDEPENDENT_CODE ON)

# Shared library for JNI
add_library(cubiomes SHARED $<TARGET_OBJECTS:cubiomes_objects>)
target_compile_definitions(cubiomes PRIVATE CUBIOMES_BUILDING_DLL)

# Static library for testing
add_library(cubiomes_static STATIC $<TARGET_OBJECTS:cubiomes_objects>)

# Set library properties
set_target_properties(cubiomes PROPERTIES
    VERSION ${PROJECT_VERSION}
    SOVERSION 1
    PUBLIC_HEADER "${HEADERS}"
)

# Platform-specific library naming and output
if(WIN32)
    set_target_properties(cubiomes PROPERTIES
        OUTPUT_NAME "cubiomes"
        SUFFIX ".dll"
    )
elseif(APPLE)
    set_target_properties(cubiomes PROPERTIES
        OUTPUT_NAME "cubiomes"
        SUFFIX ".dylib"
    )
else() # Linux and other Unix-like systems
    set_target_properties(cubiomes PROPERTIES
        OUTPUT_NAME "cubiomes"
        SUFFIX ".so"
    )
endif()

# Output directories
set(CMAKE_RUNTIME_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
set(CMAKE_LIBRARY_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)
set(CMAKE_ARCHIVE_OUTPUT_DIRECTORY ${CMAKE_BINARY_DIR}/lib)

# Installation
install(TARGETS cubiomes cubiomes_static
    LIBRARY DESTINATION lib
    ARCHIVE DESTINATION lib
    RUNTIME DESTINATION lib
    PUBLIC_HEADER DESTINATION include/cubiomes
)

# Create lib directory in source for easy access
file(MAKE_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR}/lib)

# Copy built libraries to source lib directory for Gradle access
if(WIN32)
    add_custom_command(TARGET cubiomes POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:cubiomes> ${CMAKE_CURRENT_SOURCE_DIR}/lib/
        COMMENT "Copying cubiomes.dll to lib directory"
    )
else()
    add_custom_command(TARGET cubiomes POST_BUILD
        COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:cubiomes> ${CMAKE_CURRENT_SOURCE_DIR}/lib/
        COMMENT "Copying shared library to lib directory"
    )
endif()

# Build information
message(STATUS "Cubiomes library build configuration:")
message(STATUS "  Build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "  C Compiler: ${CMAKE_C_COMPILER}")
message(STATUS "  C Flags: ${CMAKE_C_FLAGS}")
message(STATUS "  Output directory: ${CMAKE_LIBRARY_OUTPUT_DIRECTORY}")
message(STATUS "  JNI support: ${JNI_FOUND}")


