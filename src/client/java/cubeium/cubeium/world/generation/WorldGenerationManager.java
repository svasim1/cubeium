package cubeium.cubeium.world.generation;

import cubeium.cubeium.world.CubiomesInterface;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/**
 * Manages version-specific world generation features and compatibility.
 * Handles differences between Minecraft versions for accurate world generation.
 */
public class WorldGenerationManager {
    
    // Version metadata
    private static final Map<Integer, VersionInfo> VERSION_INFO = new HashMap<>();
    
    static {
        // Initialize version information
        VERSION_INFO.put(CubiomesInterface.MC_1_7_2, new VersionInfo("1.7.2", "Legacy", 
            Set.of(CubiomesInterface.DESERT_PYRAMID, CubiomesInterface.JUNGLE_TEMPLE, CubiomesInterface.SWAMP_HUT, CubiomesInterface.STRONGHOLD, CubiomesInterface.MINESHAFT)));
        
        VERSION_INFO.put(CubiomesInterface.MC_1_8_1, new VersionInfo("1.8.1", "Ocean Monuments", 
            Set.of(CubiomesInterface.DESERT_PYRAMID, CubiomesInterface.JUNGLE_TEMPLE, CubiomesInterface.SWAMP_HUT, CubiomesInterface.OCEAN_MONUMENT, CubiomesInterface.STRONGHOLD, CubiomesInterface.MINESHAFT)));
        
        VERSION_INFO.put(CubiomesInterface.MC_1_9_4, new VersionInfo("1.9.4", "End Cities", 
            Set.of(CubiomesInterface.DESERT_PYRAMID, CubiomesInterface.JUNGLE_TEMPLE, CubiomesInterface.SWAMP_HUT, CubiomesInterface.OCEAN_MONUMENT, CubiomesInterface.END_CITY, CubiomesInterface.STRONGHOLD, CubiomesInterface.MINESHAFT)));
        
        VERSION_INFO.put(CubiomesInterface.MC_1_11_2, new VersionInfo("1.11.2", "Woodland Mansions", 
            Set.of(CubiomesInterface.DESERT_PYRAMID, CubiomesInterface.JUNGLE_TEMPLE, CubiomesInterface.SWAMP_HUT, CubiomesInterface.OCEAN_MONUMENT, CubiomesInterface.END_CITY, CubiomesInterface.WOODLAND_MANSION, CubiomesInterface.STRONGHOLD, CubiomesInterface.MINESHAFT)));
        
        VERSION_INFO.put(CubiomesInterface.MC_1_14_4, new VersionInfo("1.14.4", "Villages & Pillagers", 
            Set.of(CubiomesInterface.DESERT_PYRAMID, CubiomesInterface.JUNGLE_TEMPLE, CubiomesInterface.SWAMP_HUT, CubiomesInterface.OCEAN_MONUMENT, CubiomesInterface.END_CITY, CubiomesInterface.WOODLAND_MANSION, CubiomesInterface.VILLAGE, CubiomesInterface.OUTPOST, CubiomesInterface.STRONGHOLD, CubiomesInterface.MINESHAFT)));
        
        VERSION_INFO.put(CubiomesInterface.MC_1_16_1, new VersionInfo("1.16.1", "Nether Update", 
            Set.of(CubiomesInterface.DESERT_PYRAMID, CubiomesInterface.JUNGLE_TEMPLE, CubiomesInterface.SWAMP_HUT, CubiomesInterface.OCEAN_MONUMENT, CubiomesInterface.END_CITY, CubiomesInterface.WOODLAND_MANSION, CubiomesInterface.VILLAGE, CubiomesInterface.OUTPOST, CubiomesInterface.NETHER_FORTRESS, CubiomesInterface.BASTION, CubiomesInterface.RUINED_PORTAL, CubiomesInterface.STRONGHOLD, CubiomesInterface.MINESHAFT)));
        
        VERSION_INFO.put(CubiomesInterface.MC_1_18_2, new VersionInfo("1.18.2", "Caves & Cliffs Part II", 
            Set.of(CubiomesInterface.DESERT_PYRAMID, CubiomesInterface.JUNGLE_TEMPLE, CubiomesInterface.SWAMP_HUT, CubiomesInterface.OCEAN_MONUMENT, CubiomesInterface.END_CITY, CubiomesInterface.WOODLAND_MANSION, CubiomesInterface.VILLAGE, CubiomesInterface.OUTPOST, CubiomesInterface.NETHER_FORTRESS, CubiomesInterface.BASTION, CubiomesInterface.RUINED_PORTAL, CubiomesInterface.STRONGHOLD, CubiomesInterface.MINESHAFT)));
        
        VERSION_INFO.put(CubiomesInterface.MC_1_21_4, new VersionInfo("1.21.4", "Current", 
            Set.of(CubiomesInterface.DESERT_PYRAMID, CubiomesInterface.IGLOO, CubiomesInterface.JUNGLE_TEMPLE, CubiomesInterface.SWAMP_HUT, CubiomesInterface.OUTPOST, CubiomesInterface.VILLAGE, CubiomesInterface.OCEAN_MONUMENT, CubiomesInterface.WOODLAND_MANSION, CubiomesInterface.STRONGHOLD, CubiomesInterface.MINESHAFT, CubiomesInterface.DUNGEON, CubiomesInterface.END_CITY, CubiomesInterface.END_GATEWAY, CubiomesInterface.NETHER_FORTRESS, CubiomesInterface.BASTION, CubiomesInterface.RUINED_PORTAL)));
    }
    
    // World type support
    public enum WorldType {
        DEFAULT(0L, "Default"),
        LARGE_BIOMES(CubiomesInterface.LARGE_BIOMES, "Large Biomes"),
        AMPLIFIED(CubiomesInterface.AMPLIFIED, "Amplified");
        
        public final long flags;
        public final String displayName;
        
        WorldType(long flags, String displayName) {
            this.flags = flags;
            this.displayName = displayName;
        }
    }
    
    private final int mcVersion;
    private final WorldType worldType;
    private final BiomeGenerator biomeGenerator;
    private final StructureGenerator structureGenerator;
    
    /**
     * Create a WorldGenerationManager for the current version
     */
    public WorldGenerationManager() {
        this(CubiomesInterface.MC_1_21_4, WorldType.DEFAULT);
    }
    
    /**
     * Create a WorldGenerationManager for specific version and world type
     * @param mcVersion Minecraft version constant
     * @param worldType World type (default, large biomes, amplified)
     */
    public WorldGenerationManager(int mcVersion, WorldType worldType) {
        this.mcVersion = mcVersion;
        this.worldType = worldType;
        
        // Validate version support
        if (!VERSION_INFO.containsKey(mcVersion)) {
            throw new IllegalArgumentException("Unsupported Minecraft version: " + mcVersion);
        }
        
        // Initialize generators
        this.biomeGenerator = new BiomeGenerator(mcVersion, worldType.flags);
        this.structureGenerator = new StructureGenerator(mcVersion, worldType.flags);
    }
    
    /**
     * Set the world seed for both generators
     * @param seed World seed
     * @param dimension Dimension
     */
    public void setSeed(long seed, int dimension) {
        biomeGenerator.setSeed(seed, dimension);
        structureGenerator.setSeed(seed, dimension);
    }
    
    /**
     * Set the world seed (Overworld dimension)
     * @param seed World seed
     */
    public void setSeed(long seed) {
        setSeed(seed, CubiomesInterface.DIM_OVERWORLD);
    }
    
    /**
     * Get the biome generator
     * @return BiomeGenerator instance
     */
    public BiomeGenerator getBiomeGenerator() {
        return biomeGenerator;
    }
    
    /**
     * Get the structure generator
     * @return StructureGenerator instance
     */
    public StructureGenerator getStructureGenerator() {
        return structureGenerator;
    }
    
    /**
     * Check if a structure type is supported in this version
     * @param structureType Structure type ID
     * @return True if supported
     */
    public boolean isStructureSupported(int structureType) {
        VersionInfo info = VERSION_INFO.get(mcVersion);
        return info != null && info.supportedStructures.contains(structureType);
    }
    
    /**
     * Get all supported structure types for this version
     * @return Set of supported structure type IDs
     */
    public Set<Integer> getSupportedStructures() {
        VersionInfo info = VERSION_INFO.get(mcVersion);
        return info != null ? new HashSet<>(info.supportedStructures) : new HashSet<>();
    }
    
    /**
     * Get version information
     * @return VersionInfo for current version
     */
    public VersionInfo getVersionInfo() {
        return VERSION_INFO.get(mcVersion);
    }
    
    /**
     * Get the Minecraft version constant
     * @return MC version ID
     */
    public int getMcVersion() {
        return mcVersion;
    }
    
    /**
     * Get the world type
     * @return WorldType enum
     */
    public WorldType getWorldType() {
        return worldType;
    }
    
    /**
     * Get version-specific biome adjustments
     * This handles biome ID changes between versions
     * @param biomeId Raw biome ID from cubiomes
     * @return Adjusted biome ID for the current version
     */
    public int getVersionAdjustedBiomeId(int biomeId) {
        // For now, return as-is since cubiomes handles version differences
        // This method can be extended if manual adjustments are needed
        return biomeId;
    }
    
    /**
     * Get version-specific structure adjustments
     * This handles structure availability changes between versions
     * @param structureType Structure type ID
     * @return Adjusted structure type or -1 if not available in this version
     */
    public int getVersionAdjustedStructureType(int structureType) {
        if (isStructureSupported(structureType)) {
            return structureType;
        }
        return -1; // Structure not available in this version
    }
    
    /**
     * Get recommended search parameters for structures in this version
     * @param structureType Structure type ID
     * @return SearchParameters with recommended values
     */
    public SearchParameters getRecommendedSearchParameters(int structureType) {
        // Version-specific recommendations
        switch (structureType) {
            case CubiomesInterface.VILLAGE:
                return new SearchParameters(2000, 20); // Villages are common
            case CubiomesInterface.STRONGHOLD:
                return new SearchParameters(10000, 3); // Strongholds are rare, limited count
            case CubiomesInterface.WOODLAND_MANSION:
                return new SearchParameters(20000, 5); // Mansions are very rare
            case CubiomesInterface.OCEAN_MONUMENT:
                return new SearchParameters(5000, 10); // Monuments in ocean biomes
            case CubiomesInterface.NETHER_FORTRESS:
                return new SearchParameters(3000, 15); // Nether structures
            case CubiomesInterface.END_CITY:
                return new SearchParameters(2000, 25); // End dimension structures
            default:
                return new SearchParameters(3000, 15); // Default parameters
        }
    }
    
    /**
     * Check if large biomes affect structure generation in this version
     * @return True if large biomes affects structures
     */
    public boolean doesLargeBiomesAffectStructures() {
        // Large biomes primarily affects biome generation, not structure placement
        // However, since structures depend on biomes, there can be indirect effects
        return worldType == WorldType.LARGE_BIOMES && mcVersion >= CubiomesInterface.MC_1_8_1;
    }
    
    /**
     * Clean up all generators
     */
    public void cleanup() {
        biomeGenerator.cleanup();
        structureGenerator.cleanup();
    }
    
    /**
     * Get all available versions
     * @return Map of version ID to VersionInfo
     */
    public static Map<Integer, VersionInfo> getAllVersions() {
        return new HashMap<>(VERSION_INFO);
    }
    
    /**
     * Get latest supported version
     * @return Latest MC version constant
     */
    public static int getLatestVersion() {
        return CubiomesInterface.MC_1_21_4;
    }
    
    /**
     * Information about a Minecraft version
     */
    public static class VersionInfo {
        public final String versionString;
        public final String description;
        public final Set<Integer> supportedStructures;
        
        VersionInfo(String versionString, String description, Set<Integer> supportedStructures) {
            this.versionString = versionString;
            this.description = description;
            this.supportedStructures = new HashSet<>(supportedStructures);
        }
        
        @Override
        public String toString() {
            return "Minecraft " + versionString + " (" + description + ")";
        }
    }
    
    /**
     * Search parameters for structure finding
     */
    public static class SearchParameters {
        public final int recommendedRadius;
        public final int recommendedMaxResults;
        
        SearchParameters(int recommendedRadius, int recommendedMaxResults) {
            this.recommendedRadius = recommendedRadius;
            this.recommendedMaxResults = recommendedMaxResults;
        }
        
        @Override
        public String toString() {
            return String.format("Search radius: %d blocks, Max results: %d", recommendedRadius, recommendedMaxResults);
        }
    }
}
