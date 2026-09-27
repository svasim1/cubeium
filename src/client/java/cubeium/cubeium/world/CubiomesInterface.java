package cubeium.cubeium.world;

/**
 * JNI bindings to the cubiomes C library (native/cubiomes, bridged by native/cubeium_jni.c).
 *
 * <p>The constants below mirror cubiomes' C enums. They are compile-time checked:
 * cubeium_jni.c static-asserts every constant against the C enum value, so a
 * mismatch fails the native build instead of silently producing a wrong map.
 *
 * <p>A generator handle is not thread-safe (some calls temporarily modify it);
 * use one handle per thread.
 */
public final class CubiomesInterface {

    static {
        NativeLibraryLoader.load();
    }

    private CubiomesInterface() {
    }

    // ========================================
    // Generator management
    // ========================================

    /** Allocates and initializes a generator. Free it with {@link #freeGenerator}. */
    public static native long setupGenerator(int mcVersion, int flags);

    public static native void freeGenerator(long generator);

    /** Applies a world seed for a dimension ({@link #DIM_OVERWORLD}, {@link #DIM_NETHER}, {@link #DIM_END}). */
    public static native void applySeed(long generator, int dimension, long seed);

    // ========================================
    // Biomes
    // ========================================

    /**
     * Biome at a position. Coordinates are in units of {@code scale}: scale 1 means
     * block coordinates, scale 4 means biome coordinates (x/4, y/4, z/4).
     */
    public static native int getBiomeAt(long generator, int scale, int x, int y, int z);

    /**
     * Biomes for a {@code width x height} area on one horizontal layer, row-major (z outer, x inner).
     * x, z, y are in units of {@code scale} (1, 4, 16, 64 or 256).
     */
    public static native int[] genBiomes(long generator, int scale, int x, int z, int y, int width, int height);

    /** Version-specific biome id name, e.g. "windswept_hills", or null for an unknown id. */
    public static native String getBiomeName(int mcVersion, int biomeId);

    /** True if the biome id generates in the Overworld of the given version. */
    public static native boolean isOverworldBiome(int mcVersion, int biomeId);

    /** cubiomes' default biome palette: 256 entries of 0xAARRGGBB, indexed by biome id. */
    public static native int[] getBiomeColors();

    // ========================================
    // Structures
    // ========================================

    /**
     * Structure placement config for a version: {regionSize (chunks), chunkRange, dimension},
     * or null if the structure does not exist in that version.
     */
    public static native int[] getStructureConfig(int structureType, int mcVersion);

    /**
     * Block position {x, z} of the generation attempt in a region, or null if the region has none.
     * The attempt only becomes a structure if {@link #isViableStructurePos} also passes.
     */
    public static native int[] getStructurePos(int structureType, int mcVersion, long seed, int regionX, int regionZ);

    /**
     * Biome check for a generation attempt. The generator must be seeded for the structure's
     * dimension. {@code flags} is structure-specific (e.g. village biome variant); pass 0 by default.
     */
    public static native boolean isViableStructurePos(int structureType, long generator, int x, int z, int flags);

    /**
     * Accurate stronghold positions {x1, z1, x2, z2, ...}, nearest ring first.
     * The generator must be seeded for the Overworld.
     */
    public static native int[] getStrongholds(long generator, int maxCount);

    /**
     * World spawn {x, z}. The generator must be seeded for the Overworld.
     * {@code exact} runs the full (slow) search; otherwise returns cubiomes' fast estimate.
     */
    public static native int[] getSpawn(long generator, boolean exact);

    /** cubiomes' name for a structure type, e.g. "village", or null for an unknown type. */
    public static native String getStructureName(int structureType);

    // ========================================
    // Constants (checked against cubiomes in cubeium_jni.c)
    // ========================================

    // enum MCVersion (biomes.h)
    public static final int MC_UNDEF = 0;
    public static final int MC_B1_7 = 1;
    public static final int MC_B1_8 = 2;
    public static final int MC_1_0 = 3;
    public static final int MC_1_1 = 4;
    public static final int MC_1_2 = 5;
    public static final int MC_1_3 = 6;
    public static final int MC_1_4 = 7;
    public static final int MC_1_5 = 8;
    public static final int MC_1_6 = 9;
    public static final int MC_1_7 = 10;
    public static final int MC_1_8 = 11;
    public static final int MC_1_9 = 12;
    public static final int MC_1_10 = 13;
    public static final int MC_1_11 = 14;
    public static final int MC_1_12 = 15;
    public static final int MC_1_13 = 16;
    public static final int MC_1_14 = 17;
    public static final int MC_1_15 = 18;
    public static final int MC_1_16_1 = 19;
    public static final int MC_1_16 = 20;
    public static final int MC_1_17 = 21;
    public static final int MC_1_18 = 22;
    public static final int MC_1_19_2 = 23;
    public static final int MC_1_19 = 24;
    public static final int MC_1_20 = 25;
    public static final int MC_1_21_1 = 26;
    public static final int MC_1_21_3 = 27;
    public static final int MC_1_21_4 = 28;
    public static final int MC_1_21_5 = 29;
    public static final int MC_1_21_6 = 30;
    public static final int MC_1_21_9 = 31;
    public static final int MC_1_21_11 = 32;
    public static final int MC_26_1 = 33;
    public static final int MC_26_2 = 34;
    public static final int MC_26_3 = 35;
    public static final int MC_NEWEST = MC_26_3;

    /** The cubiomes version matching the Minecraft version this build of the mod targets. */
    public static final int GAME_MC_VERSION = MC_1_21_4;

    // enum Dimension (biomes.h)
    public static final int DIM_NETHER = -1;
    public static final int DIM_OVERWORLD = 0;
    public static final int DIM_END = 1;

    // Generator flags (generator.h)
    public static final int LARGE_BIOMES = 0x1;
    public static final int NO_BETA_OCEAN = 0x2;
    public static final int FORCE_OCEAN_VARIANTS = 0x4;

    // enum StructureType (finders.h)
    public static final int FEATURE = 0;
    public static final int DESERT_PYRAMID = 1;
    public static final int JUNGLE_TEMPLE = 2;
    public static final int SWAMP_HUT = 3;
    public static final int IGLOO = 4;
    public static final int VILLAGE = 5;
    public static final int OCEAN_RUIN = 6;
    public static final int SHIPWRECK = 7;
    public static final int MONUMENT = 8;
    public static final int MANSION = 9;
    public static final int OUTPOST = 10;
    public static final int RUINED_PORTAL = 11;
    public static final int RUINED_PORTAL_NETHER = 12;
    public static final int ANCIENT_CITY = 13;
    public static final int TREASURE = 14;
    public static final int MINESHAFT = 15;
    public static final int DESERT_WELL = 16;
    public static final int GEODE = 17;
    public static final int FORTRESS = 18;
    public static final int BASTION = 19;
    public static final int NETHER_FOSSIL = 20;
    public static final int END_CITY = 21;
    public static final int END_GATEWAY = 22;
    public static final int END_ISLAND = 23;
    public static final int TRAIL_RUINS = 24;
    public static final int TRIAL_CHAMBERS = 25;
    public static final int ABANDONED_CAMP = 26;
    /** Not placed via regions: use {@link #getStrongholds}. */
    public static final int STRONGHOLD = 27;
    public static final int FEATURE_NUM = 28;
}
