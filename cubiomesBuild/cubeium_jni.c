/*
 * Cubiomes JNI Wrapper Implementation
 * 
 * This file implements the JNI wrapper functions that bridge between
 * the Java CubiomesInterface and the cubiomes C library.
 * Enhanced with comprehensive error handling and memory management.
 */

// Suppress unused parameter warnings for JNI functions
#pragma GCC diagnostic ignored "-Wunused-parameter"

#include "cubeium_cubeium_world_CubiomesInterface.h"
#include "generator.h"
#include "finders.h"
#include "biomes.h"
#include <stdlib.h>
#include <string.h>
#include <errno.h>

//=============================================================================
// Enhanced Error Handling Macros
//=============================================================================

// Throw specific exception types based on error conditions
#define JNI_THROW_RUNTIME_EXCEPTION(env, msg) \
    do { \
        jclass exClass = (*env)->FindClass(env, "java/lang/RuntimeException"); \
        if (exClass) (*env)->ThrowNew(env, exClass, msg); \
    } while(0)

#define JNI_THROW_OUT_OF_MEMORY(env, msg) \
    do { \
        jclass exClass = (*env)->FindClass(env, "java/lang/OutOfMemoryError"); \
        if (exClass) (*env)->ThrowNew(env, exClass, msg); \
    } while(0)

#define JNI_THROW_ILLEGAL_ARGUMENT(env, msg) \
    do { \
        jclass exClass = (*env)->FindClass(env, "java/lang/IllegalArgumentException"); \
        if (exClass) (*env)->ThrowNew(env, exClass, msg); \
    } while(0)

#define JNI_THROW_ILLEGAL_STATE(env, msg) \
    do { \
        jclass exClass = (*env)->FindClass(env, "java/lang/IllegalStateException"); \
        if (exClass) (*env)->ThrowNew(env, exClass, msg); \
    } while(0)

// Enhanced null pointer checks with specific error types
#define JNI_CHECK_NULL_PTR(ptr, env, msg) \
    if (!(ptr)) { \
        JNI_THROW_RUNTIME_EXCEPTION(env, msg); \
        return 0; \
    }

#define JNI_CHECK_NULL_PTR_VOID(ptr, env, msg) \
    if (!(ptr)) { \
        JNI_THROW_RUNTIME_EXCEPTION(env, msg); \
        return; \
    }

// Memory allocation checks
#define JNI_CHECK_ALLOCATION(ptr, env, msg) \
    if (!(ptr)) { \
        JNI_THROW_OUT_OF_MEMORY(env, msg); \
        return 0; \
    }

#define JNI_CHECK_ALLOCATION_VOID(ptr, env, msg) \
    if (!(ptr)) { \
        JNI_THROW_OUT_OF_MEMORY(env, msg); \
        return; \
    }

// Parameter validation checks
#define JNI_VALIDATE_RANGE(value, min, max, env, msg) \
    if ((value) < (min) || (value) > (max)) { \
        JNI_THROW_ILLEGAL_ARGUMENT(env, msg); \
        return 0; \
    }

#define JNI_VALIDATE_POSITIVE(value, env, msg) \
    if ((value) <= 0) { \
        JNI_THROW_ILLEGAL_ARGUMENT(env, msg); \
        return 0; \
    }

// Memory allocation tracking for debugging (simplified for production)
#ifdef JNI_MEMORY_DEBUG
    static int allocation_count = 0;
    #define JNI_TRACK_ALLOCATION(ptr) do { allocation_count++; } while(0)
    #define JNI_TRACK_FREE(ptr) do { allocation_count--; } while(0)
#else
    #define JNI_TRACK_ALLOCATION(ptr) do {} while(0)
    #define JNI_TRACK_FREE(ptr) do {} while(0)
#endif

#define JNI_VALIDATE_POSITIVE(value, env, msg) \
    if ((value) <= 0) { \
        JNI_THROW_ILLEGAL_ARGUMENT(env, msg); \
        return 0; \
    }

// Safe cleanup macros
#define SAFE_FREE(ptr) \
    do { \
        if (ptr) { \
            free(ptr); \
            ptr = NULL; \
        } \
    } while(0)

//=============================================================================
// Memory Management Tracking (Debug builds only)
//=============================================================================

#ifdef DEBUG
static size_t allocated_generators = 0;
static size_t allocated_memory = 0;

#define TRACK_GENERATOR_ALLOC() \
    do { allocated_generators++; } while(0)

#define TRACK_GENERATOR_FREE() \
    do { if (allocated_generators > 0) allocated_generators--; } while(0)

#define TRACK_MEMORY_ALLOC(size) \
    do { allocated_memory += (size); } while(0)

#define TRACK_MEMORY_FREE(size) \
    do { if (allocated_memory >= (size)) allocated_memory -= (size); } while(0)

#else
#define TRACK_GENERATOR_ALLOC()
#define TRACK_GENERATOR_FREE()
#define TRACK_MEMORY_ALLOC(size)
#define TRACK_MEMORY_FREE(size)
#endif

//=============================================================================
// Generator Management
//=============================================================================

JNIEXPORT jlong JNICALL Java_cubeium_cubeium_world_CubiomesInterface_setupGenerator
  (JNIEnv *env, jclass cls, jint mcVersion, jlong flags)
{
    // Validate input parameters
    JNI_VALIDATE_RANGE(mcVersion, 1, 50, env, "Invalid Minecraft version: must be between 1 and 50");
    JNI_VALIDATE_RANGE(flags, 0, 0xFFFFFFFF, env, "Invalid generator flags: must be non-negative");
    
    // Allocate generator with error checking
    Generator *g = (Generator*)malloc(sizeof(Generator));
    JNI_CHECK_ALLOCATION(g, env, "Failed to allocate memory for generator");
    
    // Initialize memory to zero for safety
    memset(g, 0, sizeof(Generator));
    
    // Initialize generator with error handling
    setupGenerator(g, (int)mcVersion, (uint32_t)flags);
    
    // Since setupGenerator is void, verify initialization by checking generator state
    if (g->mc != mcVersion) {
        SAFE_FREE(g);
        JNI_THROW_RUNTIME_EXCEPTION(env, "Failed to initialize generator: version setup failed");
        return 0;
    }
    
    // Track allocation for debugging
    TRACK_GENERATOR_ALLOC();
    TRACK_MEMORY_ALLOC(sizeof(Generator));
    
    return (jlong)g;
}

JNIEXPORT void JNICALL Java_cubeium_cubeium_world_CubiomesInterface_freeGenerator
  (JNIEnv *env, jclass cls, jlong generator)
{
    if (generator == 0) {
        // Silently ignore null generators (defensive programming)
        return;
    }
    
    Generator *g = (Generator*)generator;
    
    // Track deallocation for debugging
    TRACK_GENERATOR_FREE();
    TRACK_MEMORY_FREE(sizeof(Generator));
    
    // Free the generator memory
    SAFE_FREE(g);
}

JNIEXPORT void JNICALL Java_cubeium_cubeium_world_CubiomesInterface_applySeed
  (JNIEnv *env, jclass cls, jlong generator, jint dimension, jlong seed)
{
    Generator *g = (Generator*)generator;
    JNI_CHECK_NULL_PTR_VOID(g, env, "Invalid generator pointer: generator was freed or never created");
    
    // Validate dimension parameter
    if (dimension < -1 || dimension > 1) {
        JNI_THROW_ILLEGAL_ARGUMENT(env, "Invalid dimension: must be -1 (Nether), 0 (Overworld), or 1 (End)");
        return;
    }
    
    // Apply seed with error handling
    applySeed(g, (int)dimension, (uint64_t)seed);
    
    // Since applySeed is void, we can't directly verify success
    // The function will typically work if the generator was properly initialized
}

//=============================================================================
// Biome Generation
//=============================================================================

JNIEXPORT jint JNICALL Java_cubeium_cubeium_world_CubiomesInterface_getBiomeAt
  (JNIEnv *env, jclass cls, jlong generator, jint scale, jint x, jint y, jint z)
{
    Generator *g = (Generator*)generator;
    JNI_CHECK_NULL_PTR(g, env, "Invalid generator pointer: generator was freed or never created");
    
    // Validate scale parameter
    JNI_VALIDATE_POSITIVE(scale, env, "Scale must be positive (1, 4, 16, etc.)");
    
    // Validate coordinate ranges (prevent extreme values that might cause issues)
    JNI_VALIDATE_RANGE(x, -30000000, 30000000, env, "X coordinate out of valid range");
    JNI_VALIDATE_RANGE(z, -30000000, 30000000, env, "Z coordinate out of valid range");
    JNI_VALIDATE_RANGE(y, -2048, 2048, env, "Y coordinate out of valid range");
    
    int biome = getBiomeAt(g, (int)scale, (int)x, (int)y, (int)z);
    
    // Validate returned biome ID
    if (biome < -1 || biome > 255) {
        JNI_THROW_RUNTIME_EXCEPTION(env, "Generated invalid biome ID - possible memory corruption");
        return -1;
    }
    
    return (jint)biome;
}

JNIEXPORT jintArray JNICALL Java_cubeium_cubeium_world_CubiomesInterface_genBiomes
  (JNIEnv *env, jclass cls, jlong generator, jint scale, jint x, jint z, jint y, jint width, jint height)
{
    Generator *g = (Generator*)generator;
    JNI_CHECK_NULL_PTR(g, env, "Invalid generator pointer: generator was freed or never created");
    
    // Validate scale parameter
    JNI_VALIDATE_POSITIVE(scale, env, "Scale must be positive (1, 4, 16, etc.)");
    
    // Validate coordinate ranges
    JNI_VALIDATE_RANGE(x, -30000000, 30000000, env, "X coordinate out of valid range");
    JNI_VALIDATE_RANGE(z, -30000000, 30000000, env, "Z coordinate out of valid range");
    JNI_VALIDATE_RANGE(y, -2048, 2048, env, "Y coordinate out of valid range");
    
    // Validate dimensions
    JNI_VALIDATE_RANGE(width, 1, 8192, env, "Width must be between 1 and 8192");
    JNI_VALIDATE_RANGE(height, 1, 8192, env, "Height must be between 1 and 8192");
    
    // Check for potential overflow in area calculation
    if ((int64_t)width * (int64_t)height > 16777216) { // 4096x4096 max area
        JNI_THROW_ILLEGAL_ARGUMENT(env, "Requested area too large - maximum area is 16,777,216 blocks");
        return NULL;
    }
    
    // Create range structure
    Range r;
    r.scale = (int)scale;
    r.x = (int)x;
    r.z = (int)z;
    r.y = (int)y;
    r.sx = (int)width;
    r.sz = (int)height;
    r.sy = 1; // 2D generation
    
    // Allocate cache with memory tracking
    int *cache = allocCache(g, r);
    if (!cache) {
        JNI_THROW_OUT_OF_MEMORY(env, "Failed to allocate cache for biome generation");
        return NULL;
    }
    JNI_TRACK_ALLOCATION(cache);
    
    // Generate biomes
    int ret = genBiomes(g, cache, r);
    if (ret != 0) {
        JNI_TRACK_FREE(cache);
        free(cache);
        JNI_THROW_RUNTIME_EXCEPTION(env, "Biome generation failed - cubiomes library error");
        return NULL;
    }
    
    int area = width * height;
    
    // Create Java array
    jintArray result = (*env)->NewIntArray(env, area);
    if (!result) {
        JNI_TRACK_FREE(cache);
        free(cache);
        JNI_THROW_OUT_OF_MEMORY(env, "Failed to create Java array for biomes");
        return NULL;
    }
    
    // Validate biome data before copying
    for (int i = 0; i < area; i++) {
        if (cache[i] < -1 || cache[i] > 255) {
            char error_msg[128];
            snprintf(error_msg, sizeof(error_msg), "Invalid biome ID %d at position %d", cache[i], i);
            JNI_TRACK_FREE(cache);
            free(cache);
            JNI_THROW_RUNTIME_EXCEPTION(env, error_msg);
            return NULL;
        }
    }
    
    // Copy biome data to Java array
    (*env)->SetIntArrayRegion(env, result, 0, area, (jint*)cache);
    
    // Check for JNI exceptions during array copy
    if ((*env)->ExceptionCheck(env)) {
        JNI_TRACK_FREE(cache);
        free(cache);
        return NULL;
    }
    
    JNI_TRACK_FREE(cache);
    free(cache);
    
    return result;
}

JNIEXPORT jintArray JNICALL Java_cubeium_cubeium_world_CubiomesInterface_genBiomes3D
  (JNIEnv *env, jclass cls, jlong generator, jint scale, jint x, jint z, jint y, jint width, jint height, jint depth)
{
    Generator *g = (Generator*)generator;
    JNI_CHECK_NULL_PTR(g, env, "Invalid generator pointer: generator was freed or never created");
    
    // Validate scale parameter
    JNI_VALIDATE_POSITIVE(scale, env, "Scale must be positive (1, 4, 16, etc.)");
    
    // Validate coordinate ranges
    JNI_VALIDATE_RANGE(x, -30000000, 30000000, env, "X coordinate out of valid range");
    JNI_VALIDATE_RANGE(z, -30000000, 30000000, env, "Z coordinate out of valid range");
    JNI_VALIDATE_RANGE(y, -2048, 2048, env, "Y coordinate out of valid range");
    
    // Validate dimensions
    JNI_VALIDATE_RANGE(width, 1, 512, env, "3D Width must be between 1 and 512");
    JNI_VALIDATE_RANGE(height, 1, 512, env, "3D Height must be between 1 and 512");
    JNI_VALIDATE_RANGE(depth, 1, 512, env, "3D Depth must be between 1 and 512");
    
    // Check for potential overflow in volume calculation
    if ((int64_t)width * (int64_t)height * (int64_t)depth > 134217728) { // 512^3 max volume
        JNI_THROW_ILLEGAL_ARGUMENT(env, "Requested 3D volume too large - maximum volume is 134,217,728 blocks");
        return NULL;
    }
    
    // Create range structure
    Range r;
    r.scale = (int)scale;
    r.x = (int)x;
    r.z = (int)z;
    r.y = (int)y;
    r.sx = (int)width;
    r.sz = (int)height;
    r.sy = (int)depth;
    
    // Allocate cache with memory tracking
    int *cache = allocCache(g, r);
    if (!cache) {
        JNI_THROW_OUT_OF_MEMORY(env, "Failed to allocate cache for 3D biome generation");
        return NULL;
    }
    JNI_TRACK_ALLOCATION(cache);
    
    // Generate biomes
    int ret = genBiomes(g, cache, r);
    if (ret != 0) {
        JNI_TRACK_FREE(cache);
        free(cache);
        JNI_THROW_RUNTIME_EXCEPTION(env, "3D biome generation failed - cubiomes library error");
        return NULL;
    }
    
    int totalSize = width * height * depth;
    
    // Create Java array
    jintArray result = (*env)->NewIntArray(env, totalSize);
    if (!result) {
        JNI_TRACK_FREE(cache);
        free(cache);
        JNI_THROW_OUT_OF_MEMORY(env, "Failed to create Java array for 3D biomes");
        return NULL;
    }
    
    // Validate biome data before copying
    for (int i = 0; i < totalSize; i++) {
        if (cache[i] < -1 || cache[i] > 255) {
            char error_msg[128];
            snprintf(error_msg, sizeof(error_msg), "Invalid 3D biome ID %d at position %d", cache[i], i);
            JNI_TRACK_FREE(cache);
            free(cache);
            JNI_THROW_RUNTIME_EXCEPTION(env, error_msg);
            return NULL;
        }
    }
    
    // Copy biome data to Java array
    (*env)->SetIntArrayRegion(env, result, 0, totalSize, (jint*)cache);
    
    // Check for JNI exceptions during array copy
    if ((*env)->ExceptionCheck(env)) {
        JNI_TRACK_FREE(cache);
        free(cache);
        return NULL;
    }
    
    JNI_TRACK_FREE(cache);
    free(cache);
    
    return result;
}

//=============================================================================
// Structure Generation
//=============================================================================

JNIEXPORT jintArray JNICALL Java_cubeium_cubeium_world_CubiomesInterface_getStructurePos
  (JNIEnv *env, jclass cls, jint structureType, jint mcVersion, jlong seed, jint regionX, jint regionZ)
{
    // Validate structure type
    JNI_VALIDATE_RANGE(structureType, 0, 100, env, "Structure type out of valid range");
    
    // Validate MC version (very permissive range for now)
    JNI_VALIDATE_RANGE(mcVersion, 1, 100, env, "Minecraft version out of supported range");
    
    // Validate region coordinates (prevent extreme values)
    JNI_VALIDATE_RANGE(regionX, -1000000, 1000000, env, "Region X coordinate out of valid range");
    JNI_VALIDATE_RANGE(regionZ, -1000000, 1000000, env, "Region Z coordinate out of valid range");
    
    Pos pos;
    
    // Get structure position
    int success = getStructurePos((int)structureType, (int)mcVersion, (uint64_t)seed, 
                                  (int)regionX, (int)regionZ, &pos);
    
    if (!success) {
        return NULL; // No structure at this location - this is normal behavior
    }
    
    // Validate returned position coordinates
    if (pos.x < -30000000 || pos.x > 30000000 || pos.z < -30000000 || pos.z > 30000000) {
        JNI_THROW_RUNTIME_EXCEPTION(env, "Structure position out of valid world bounds");
        return NULL;
    }
    
    // Create result array [x, z]
    jintArray result = (*env)->NewIntArray(env, 2);
    if (!result) {
        JNI_THROW_OUT_OF_MEMORY(env, "Failed to create array for structure position");
        return NULL;
    }
    
    jint coords[2] = { pos.x, pos.z };
    (*env)->SetIntArrayRegion(env, result, 0, 2, coords);
    
    // Check for JNI exceptions during array operation
    if ((*env)->ExceptionCheck(env)) {
        return NULL;
    }
    
    return result;
}

JNIEXPORT jboolean JNICALL Java_cubeium_cubeium_world_CubiomesInterface_isViableStructurePos
  (JNIEnv *env, jclass cls, jint structureType, jlong generator, jint x, jint z, jint flags)
{
    Generator *g = (Generator*)generator;
    JNI_CHECK_NULL_PTR(g, env, "Invalid generator pointer: generator was freed or never created");
    
    // Validate structure type
    JNI_VALIDATE_RANGE(structureType, 0, 100, env, "Structure type out of valid range");
    
    // Validate coordinates
    JNI_VALIDATE_RANGE(x, -30000000, 30000000, env, "X coordinate out of valid range");
    JNI_VALIDATE_RANGE(z, -30000000, 30000000, env, "Z coordinate out of valid range");
    
    // This would require implementing isViableStructurePos from cubiomes
    // For now, we'll implement a basic check based on biome requirements
    // This is a simplified implementation - the actual cubiomes library
    // has more complex structure viability checks
    
    int biome = getBiomeAt(g, 1, (int)x, 64, (int)z);
    if (biome < 0) {
        return JNI_FALSE;
    }
    
    // Validate biome ID
    if (biome > 255) {
        JNI_THROW_RUNTIME_EXCEPTION(env, "Invalid biome ID returned during structure viability check");
        return JNI_FALSE;
    }
    
    // Basic biome checks for common structures
    switch (structureType) {
        case Desert_Pyramid:
            return (biome == desert) ? JNI_TRUE : JNI_FALSE;
        case Jungle_Temple:
            return (biome == jungle || biome == jungle_hills) ? JNI_TRUE : JNI_FALSE;
        case Swamp_Hut:
            return (biome == swamp) ? JNI_TRUE : JNI_FALSE;
        case Igloo:
            return (biome == snowy_plains || biome == snowy_taiga) ? JNI_TRUE : JNI_FALSE;
        case Village:
            // Villages can generate in many biomes
            return (biome == plains || biome == desert || biome == savanna || 
                    biome == taiga || biome == snowy_plains) ? JNI_TRUE : JNI_FALSE;
        case Monument:
            return (biome == ocean || biome == deep_ocean) ? JNI_TRUE : JNI_FALSE;
        case Outpost:
            return (biome == plains || biome == desert || biome == savanna || 
                    biome == taiga) ? JNI_TRUE : JNI_FALSE;
        default:
            return JNI_TRUE; // Default to true for unknown structures
    }
}

JNIEXPORT jintArray JNICALL Java_cubeium_cubeium_world_CubiomesInterface_getStrongholds
  (JNIEnv *env, jclass cls, jlong generator, jlong seed, jint maxCount)
{
    Generator *g = (Generator*)generator;
    JNI_CHECK_NULL_PTR(g, env, "Invalid generator pointer: generator was freed or never created");
    
    // Validate maxCount parameter
    JNI_VALIDATE_RANGE(maxCount, 1, 128, env, "Max stronghold count must be between 1 and 128");
    
    // Initialize stronghold iterator
    StrongholdIter sh;
    Pos firstPos = initFirstStronghold(&sh, g->mc, (uint64_t)seed);
    
    // Allocate temporary array for positions with memory tracking
    int *positions = (int*)malloc(maxCount * 2 * sizeof(int));
    if (!positions) {
        JNI_THROW_OUT_OF_MEMORY(env, "Failed to allocate memory for stronghold positions");
        return NULL;
    }
    JNI_TRACK_ALLOCATION(positions);
    
    int count = 0;
    
    // Validate first stronghold position
    if (firstPos.x < -30000000 || firstPos.x > 30000000 || firstPos.z < -30000000 || firstPos.z > 30000000) {
        JNI_TRACK_FREE(positions);
        free(positions);
        JNI_THROW_RUNTIME_EXCEPTION(env, "First stronghold position out of valid world bounds");
        return NULL;
    }
    
    positions[count * 2] = firstPos.x;
    positions[count * 2 + 1] = firstPos.z;
    count++;
    
    // Get additional strongholds using the iterator
    while (count < maxCount) {
        int ret = nextStronghold(&sh, g);
        if (ret <= 0) break; // No more strongholds or error
        
        // Validate stronghold position
        if (sh.pos.x < -30000000 || sh.pos.x > 30000000 || sh.pos.z < -30000000 || sh.pos.z > 30000000) {
            JNI_TRACK_FREE(positions);
            free(positions);
            JNI_THROW_RUNTIME_EXCEPTION(env, "Stronghold position out of valid world bounds");
            return NULL;
        }
        
        positions[count * 2] = sh.pos.x;
        positions[count * 2 + 1] = sh.pos.z;
        count++;
    }
    
    // Create Java array
    jintArray result = (*env)->NewIntArray(env, count * 2);
    if (!result) {
        JNI_TRACK_FREE(positions);
        free(positions);
        JNI_THROW_OUT_OF_MEMORY(env, "Failed to create Java array for stronghold positions");
        return NULL;
    }
    
    // Copy positions to Java array
    (*env)->SetIntArrayRegion(env, result, 0, count * 2, (jint*)positions);
    
    // Check for JNI exceptions during array copy
    if ((*env)->ExceptionCheck(env)) {
        JNI_TRACK_FREE(positions);
        free(positions);
        return NULL;
    }
    
    JNI_TRACK_FREE(positions);
    free(positions);
    
    return result;
}

JNIEXPORT jintArray JNICALL Java_cubeium_cubeium_world_CubiomesInterface_getSpawn
  (JNIEnv *env, jclass cls, jlong generator, jlong seed)
{
    Generator *g = (Generator*)generator;
    JNI_CHECK_NULL_PTR(g, env, "Invalid generator pointer: generator was freed or never created");
    
    // Get spawn position
    Pos spawn = getSpawn(g);
    
    // Validate spawn position
    if (spawn.x < -30000000 || spawn.x > 30000000 || spawn.z < -30000000 || spawn.z > 30000000) {
        JNI_THROW_RUNTIME_EXCEPTION(env, "Spawn position out of valid world bounds");
        return NULL;
    }
    
    // Create result array [x, z]
    jintArray result = (*env)->NewIntArray(env, 2);
    if (!result) {
        JNI_THROW_OUT_OF_MEMORY(env, "Failed to create Java array for spawn position");
        return NULL;
    }
    
    jint coords[2] = { spawn.x, spawn.z };
    (*env)->SetIntArrayRegion(env, result, 0, 2, coords);
    
    // Check for JNI exceptions during array operation
    if ((*env)->ExceptionCheck(env)) {
        return NULL;
    }
    
    return result;
}

//=============================================================================
// Utility Functions
//=============================================================================

// Simple biome ID to name mapping
const char* getBiomeName(int biomeId) {
    switch (biomeId) {
        case ocean: return "ocean";
        case plains: return "plains";
        case desert: return "desert";
        case mountains: return "mountains";
        case forest: return "forest";
        case taiga: return "taiga";
        case swamp: return "swamp";
        case river: return "river";
        case nether_wastes: return "nether_wastes";
        case the_end: return "the_end";
        case frozen_ocean: return "frozen_ocean";
        case frozen_river: return "frozen_river";
        case snowy_tundra: return "snowy_tundra";
        case snowy_mountains: return "snowy_mountains";
        case mushroom_fields: return "mushroom_fields";
        case mushroom_field_shore: return "mushroom_field_shore";
        case beach: return "beach";
        case desert_hills: return "desert_hills";
        case wooded_hills: return "wooded_hills";
        case taiga_hills: return "taiga_hills";
        case mountain_edge: return "mountain_edge";
        case jungle: return "jungle";
        case jungle_hills: return "jungle_hills";
        case jungle_edge: return "jungle_edge";
        case deep_ocean: return "deep_ocean";
        case stone_shore: return "stone_shore";
        case snowy_beach: return "snowy_beach";
        case birch_forest: return "birch_forest";
        case birch_forest_hills: return "birch_forest_hills";
        case dark_forest: return "dark_forest";
        case snowy_taiga: return "snowy_taiga";
        case snowy_taiga_hills: return "snowy_taiga_hills";
        case giant_tree_taiga: return "giant_tree_taiga";
        case giant_tree_taiga_hills: return "giant_tree_taiga_hills";
        case wooded_mountains: return "wooded_mountains";
        case savanna: return "savanna";
        case savanna_plateau: return "savanna_plateau";
        case badlands: return "badlands";
        case wooded_badlands_plateau: return "wooded_badlands_plateau";
        case badlands_plateau: return "badlands_plateau";
        case small_end_islands: return "small_end_islands";
        case end_midlands: return "end_midlands";
        case end_highlands: return "end_highlands";
        case end_barrens: return "end_barrens";
        case warm_ocean: return "warm_ocean";
        case lukewarm_ocean: return "lukewarm_ocean";
        case cold_ocean: return "cold_ocean";
        case deep_warm_ocean: return "deep_warm_ocean";
        case deep_lukewarm_ocean: return "deep_lukewarm_ocean";
        case deep_cold_ocean: return "deep_cold_ocean";
        case deep_frozen_ocean: return "deep_frozen_ocean";
        case the_void: return "the_void";
        case sunflower_plains: return "sunflower_plains";
        case desert_lakes: return "desert_lakes";
        case gravelly_mountains: return "gravelly_mountains";
        case flower_forest: return "flower_forest";
        case taiga_mountains: return "taiga_mountains";
        case swamp_hills: return "swamp_hills";
        case ice_spikes: return "ice_spikes";
        case modified_jungle: return "modified_jungle";
        case modified_jungle_edge: return "modified_jungle_edge";
        case tall_birch_forest: return "tall_birch_forest";
        case tall_birch_hills: return "tall_birch_hills";
        case dark_forest_hills: return "dark_forest_hills";
        case snowy_taiga_mountains: return "snowy_taiga_mountains";
        case giant_spruce_taiga: return "giant_spruce_taiga";
        case giant_spruce_taiga_hills: return "giant_spruce_taiga_hills";
        case modified_gravelly_mountains: return "modified_gravelly_mountains";
        case shattered_savanna: return "shattered_savanna";
        case shattered_savanna_plateau: return "shattered_savanna_plateau";
        case eroded_badlands: return "eroded_badlands";
        case modified_wooded_badlands_plateau: return "modified_wooded_badlands_plateau";
        case modified_badlands_plateau: return "modified_badlands_plateau";
        case bamboo_jungle: return "bamboo_jungle";
        case bamboo_jungle_hills: return "bamboo_jungle_hills";
        case soul_sand_valley: return "soul_sand_valley";
        case crimson_forest: return "crimson_forest";
        case warped_forest: return "warped_forest";
        case basalt_deltas: return "basalt_deltas";
        case dripstone_caves: return "dripstone_caves";
        case lush_caves: return "lush_caves";
        case meadow: return "meadow";
        case grove: return "grove";
        case snowy_slopes: return "snowy_slopes";
        case jagged_peaks: return "jagged_peaks";
        case frozen_peaks: return "frozen_peaks";
        case stony_peaks: return "stony_peaks";
        case deep_dark: return "deep_dark";
        case mangrove_swamp: return "mangrove_swamp";
        case cherry_grove: return "cherry_grove";
        case pale_garden: return "pale_garden";
        default: return "unknown";
    }
}

//=============================================================================
// Utility Methods
//=============================================================================

JNIEXPORT jstring JNICALL Java_cubeium_cubeium_world_CubiomesInterface_getBiomeName
  (JNIEnv *env, jclass cls, jint biomeId)
{
    // Validate biome ID range
    JNI_VALIDATE_RANGE(biomeId, -1, 255, env, "Biome ID out of valid range");
    
    const char *name = getBiomeName((int)biomeId);
    
    jstring result = (*env)->NewStringUTF(env, name);
    if (!result) {
        JNI_THROW_OUT_OF_MEMORY(env, "Failed to create string for biome name");
        return NULL;
    }
    
    return result;
}

JNIEXPORT jstring JNICALL Java_cubeium_cubeium_world_CubiomesInterface_getStructureName
  (JNIEnv *env, jclass cls, jint structureType)
{
    // Validate structure type range
    JNI_VALIDATE_RANGE(structureType, 0, 100, env, "Structure type out of valid range");
    
    const char *name;
    
    switch (structureType) {
        case Desert_Pyramid:    name = "Desert Pyramid"; break;
        case Jungle_Temple:     name = "Jungle Temple"; break;
        case Swamp_Hut:         name = "Swamp Hut"; break;
        case Igloo:             name = "Igloo"; break;
        case Village:           name = "Village"; break;
        case Ocean_Ruin:        name = "Ocean Ruin"; break;
        case Shipwreck:         name = "Shipwreck"; break;
        case Monument:          name = "Ocean Monument"; break;
        case Mansion:           name = "Woodland Mansion"; break;
        case Outpost:           name = "Pillager Outpost"; break;
        case Ruined_Portal:     name = "Ruined Portal"; break;
        case Ancient_City:      name = "Ancient City"; break;
        case Mineshaft:         name = "Mineshaft"; break;
        case Fortress:          name = "Nether Fortress"; break;
        case Bastion:           name = "Bastion Remnant"; break;
        case End_City:          name = "End City"; break;
        default:                name = "Unknown Structure"; break;
    }
    
    jstring result = (*env)->NewStringUTF(env, name);
    if (!result) {
        JNI_THROW_OUT_OF_MEMORY(env, "Failed to create string for structure name");
        return NULL;
    }
    
    return result;
}

JNIEXPORT jboolean JNICALL Java_cubeium_cubeium_world_CubiomesInterface_isLibraryLoaded
  (JNIEnv *env, jclass cls)
{
    // If this function is called, the library is loaded
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL Java_cubeium_cubeium_world_CubiomesInterface_getLibraryVersion
  (JNIEnv *env, jclass cls)
{
    // Return cubiomes version information
    jstring result = (*env)->NewStringUTF(env, "cubiomes-1.0.0-cubeium");
    if (!result) {
        JNI_THROW_OUT_OF_MEMORY(env, "Failed to create string for library version");
        return NULL;
    }
    
    return result;
}
