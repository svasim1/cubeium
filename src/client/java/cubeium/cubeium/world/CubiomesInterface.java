package cubeium.cubeium.world;

import cubeium.cubeium.Cubeium;

/**
 * JNI interface to the cubiomes C library for Minecraft world generation.
 * This class provides native methods to interface with the cubiomes library
 * for calculating biomes, structures, and other world generation features.
 */
public class CubiomesInterface {
    
    // Static initialization block to load the native library
    static {
        loadNativeLibrary();
    }
    
    /**
     * Loads the appropriate native library for the current platform
     */
    private static void loadNativeLibrary() {
        String osName = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        
        // Determine platform-specific library name
        String platformName;
        
        if (osName.contains("windows")) {
            platformName = "windows";
        } else if (osName.contains("linux")) {
            platformName = "linux";
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            platformName = "macos";
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + osName);
        }
        
        // Determine architecture
        String archName;
        if (arch.contains("amd64") || arch.contains("x86_64")) {
            archName = "x64";
        } else if (arch.contains("aarch64") || arch.contains("arm64")) {
            archName = "arm64";
        } else if (arch.contains("x86") || arch.contains("i386")) {
            archName = "x86";
        } else {
            archName = arch;
        }
        
        // Construct library path and filename
        String libraryFileName;
        if (platformName.equals("windows")) {
            libraryFileName = "libcubiomes.dll";  // CMake generates libcubiomes.dll on Windows
        } else if (platformName.equals("macos")) {
            libraryFileName = "libcubiomes.dylib";
        } else {
            libraryFileName = "libcubiomes.so";
        }
        
        String libraryPath = "/natives/" + platformName + "/" + archName + "/" + libraryFileName;
        
        try {
            // Try to load from resources first
            NativeLibraryLoader.loadLibraryFromResources("cubiomes", libraryPath);
            Cubeium.LOGGER.info("Successfully loaded cubiomes native library from: " + libraryPath);
        } catch (Exception e) {
            Cubeium.LOGGER.error("Failed to load cubiomes native library from: " + libraryPath, e);
            
            // Fallback: try system library path
            try {
                System.loadLibrary("cubiomes");
                Cubeium.LOGGER.info("Successfully loaded cubiomes from system library path");
            } catch (Exception e2) {
                Cubeium.LOGGER.error("Failed to load cubiomes from system library path", e2);
                throw new RuntimeException("Cannot load cubiomes native library", e2);
            }
        }
    }
    
    // ========================================
    // Generator Management
    // ========================================
    
    /**
     * Initialize a new generator for the specified Minecraft version
     * @param mcVersion Minecraft version (e.g., MC_1_21_4)
     * @param flags Generator flags (0 for default)
     * @return Generator handle (pointer as long)
     */
    public static native long setupGenerator(int mcVersion, long flags);
    
    /**
     * Clean up and free a generator
     * @param generator Generator handle
     */
    public static native void freeGenerator(long generator);
    
    /**
     * Apply a seed to the generator
     * @param generator Generator handle
     * @param dimension Dimension (0 = Overworld, -1 = Nether, 1 = End)
     * @param seed World seed
     */
    public static native void applySeed(long generator, int dimension, long seed);
    
    // ========================================
    // Biome Generation
    // ========================================
    
    /**
     * Get the biome at a specific coordinate
     * @param generator Generator handle
     * @param scale Scale factor (1 = block, 4 = biome coordinate)
     * @param x X coordinate
     * @param y Y coordinate  
     * @param z Z coordinate
     * @return Biome ID
     */
    public static native int getBiomeAt(long generator, int scale, int x, int y, int z);
    
    /**
     * Generate biomes for a rectangular area
     * @param generator Generator handle
     * @param scale Scale factor (1, 4, 16, 64, or 256)
     * @param x Starting X coordinate
     * @param z Starting Z coordinate
     * @param y Y coordinate (height layer)
     * @param width Width of the area
     * @param height Height of the area
     * @return Array of biome IDs (width * height elements)
     */
    public static native int[] genBiomes(long generator, int scale, int x, int z, int y, int width, int height);
    
    /**
     * Generate biomes for a 3D volume
     * @param generator Generator handle
     * @param scale Scale factor
     * @param x Starting X coordinate
     * @param z Starting Z coordinate
     * @param y Starting Y coordinate
     * @param width Width of the volume
     * @param height Height of the volume
     * @param depth Depth of the volume
     * @return Array of biome IDs (width * height * depth elements)
     */
    public static native int[] genBiomes3D(long generator, int scale, int x, int z, int y, int width, int height, int depth);
    
    // ========================================
    // Structure Generation
    // ========================================
    
    /**
     * Get the position of a structure in a region
     * @param structureType Structure type ID
     * @param mcVersion Minecraft version
     * @param seed World seed (lower 48 bits)
     * @param regionX Region X coordinate
     * @param regionZ Region Z coordinate
     * @return Array with [x, z] coordinates, or null if no structure
     */
    public static native int[] getStructurePos(int structureType, int mcVersion, long seed, int regionX, int regionZ);
    
    /**
     * Check if a structure can generate at the given position (biome check)
     * @param structureType Structure type ID
     * @param generator Generator handle
     * @param x X coordinate
     * @param z Z coordinate
     * @param flags Additional flags
     * @return True if structure is viable at this position
     */
    public static native boolean isViableStructurePos(int structureType, long generator, int x, int z, int flags);
    
    /**
     * Get stronghold positions
     * @param generator Generator handle
     * @param seed World seed
     * @param maxCount Maximum number of strongholds to find
     * @return Array of stronghold positions [x1, z1, x2, z2, ...]
     */
    public static native int[] getStrongholds(long generator, long seed, int maxCount);
    
    /**
     * Get spawn point for a world
     * @param generator Generator handle
     * @param seed World seed
     * @return Array with [x, z] spawn coordinates
     */
    public static native int[] getSpawn(long generator, long seed);
    
    // ========================================
    // Utility Methods
    // ========================================
    
    /**
     * Get the display name for a biome ID
     * @param biomeId Biome ID
     * @return Biome name string
     */
    public static native String getBiomeName(int biomeId);
    
    /**
     * Get the display name for a structure type
     * @param structureType Structure type ID
     * @return Structure name string
     */
    public static native String getStructureName(int structureType);
    
    /**
     * Check if the native library is loaded and functional
     * @return True if library is working
     */
    public static native boolean isLibraryLoaded();
    
    /**
     * Get version information about the cubiomes library
     * @return Version string
     */
    public static native String getLibraryVersion();
    
    // ========================================
    // Constants (from cubiomes)
    // ========================================
    
    // Minecraft versions
    public static final int MC_1_7_2 = 0;
    public static final int MC_1_8_1 = 1;
    public static final int MC_1_9_4 = 2;
    public static final int MC_1_10_2 = 3;
    public static final int MC_1_11_2 = 4;
    public static final int MC_1_12_2 = 5;
    public static final int MC_1_13_2 = 6;
    public static final int MC_1_14_4 = 7;
    public static final int MC_1_15_2 = 8;
    public static final int MC_1_16_1 = 9;
    public static final int MC_1_16_5 = 10;
    public static final int MC_1_17_1 = 11;
    public static final int MC_1_18_2 = 12;
    public static final int MC_1_19_2 = 13;
    public static final int MC_1_20_2 = 14;
    public static final int MC_1_21_4 = 15; // Latest supported version
    
    // Dimensions
    public static final int DIM_OVERWORLD = 0;
    public static final int DIM_NETHER = -1;
    public static final int DIM_END = 1;
    
    // Structure types (from cubiomes finders.h)
    public static final int DESERT_PYRAMID = 0;
    public static final int IGLOO = 1;
    public static final int JUNGLE_TEMPLE = 2;
    public static final int SWAMP_HUT = 3;
    public static final int OUTPOST = 4;
    public static final int VILLAGE = 5;
    public static final int OCEAN_MONUMENT = 6;
    public static final int WOODLAND_MANSION = 7;
    public static final int STRONGHOLD = 8;
    public static final int MINESHAFT = 9;
    public static final int DUNGEON = 10;
    public static final int END_CITY = 11;
    public static final int END_GATEWAY = 12;
    public static final int NETHER_FORTRESS = 13;
    public static final int BASTION = 14;
    public static final int RUINED_PORTAL = 15;
    
    // Generator flags
    public static final long LARGE_BIOMES = 1L;
    public static final long AMPLIFIED = 2L;
}
