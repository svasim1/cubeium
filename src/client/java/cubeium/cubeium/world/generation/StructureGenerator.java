package cubeium.cubeium.world.generation;

import cubeium.cubeium.world.CubiomesInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Objects;

/**
 * Manages structure generation using the cubiomes library integration.
 * Provides efficient structure finding with caching and validation.
 */
public class StructureGenerator {
    private static final int DEFAULT_MC_VERSION = CubiomesInterface.MC_1_21_4;
    private static final int CACHE_LIMIT = 5000; // Maximum cached structure searches
    
    // Generator state
    private long generatorHandle = 0;
    private int mcVersion;
    private long worldFlags;
    private long currentSeed = Long.MIN_VALUE;
    private int dimension = CubiomesInterface.DIM_OVERWORLD;
    
    // Threading and caching
    private final ExecutorService executor;
    private final Map<StructureSearchKey, List<StructurePos>> structureCache;
    private final Object generatorLock = new Object();
    
    // Performance tracking
    private long totalSearches = 0;
    private long cacheHits = 0;
    private long cacheMisses = 0;
    
    /**
     * Create a new StructureGenerator with default settings
     */
    public StructureGenerator() {
        this(DEFAULT_MC_VERSION, 0L);
    }
    
    /**
     * Create a StructureGenerator for specific version and flags
     * @param mcVersion Minecraft version constant
     * @param worldFlags World generation flags
     */
    public StructureGenerator(int mcVersion, long worldFlags) {
        this.mcVersion = mcVersion;
        this.worldFlags = worldFlags;
        this.executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        this.structureCache = new ConcurrentHashMap<>();
        
        initializeGenerator();
    }
    
    /**
     * Initialize the native generator
     */
    private void initializeGenerator() {
        synchronized (generatorLock) {
            if (generatorHandle != 0) {
                CubiomesInterface.freeGenerator(generatorHandle);
            }
            
            try {
                generatorHandle = CubiomesInterface.setupGenerator(mcVersion, worldFlags);
                if (generatorHandle == 0) {
                    throw new IllegalStateException("Failed to initialize cubiomes generator");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to create StructureGenerator: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Set the world seed and dimension
     * @param seed World seed
     * @param dimension Dimension (OVERWORLD, NETHER, END)
     */
    public void setSeed(long seed, int dimension) {
        synchronized (generatorLock) {
            if (this.currentSeed != seed || this.dimension != dimension) {
                this.currentSeed = seed;
                this.dimension = dimension;
                
                // Clear cache when seed changes
                structureCache.clear();
                cacheHits = 0;
                cacheMisses = 0;
                
                // Apply seed to native generator
                try {
                    CubiomesInterface.applySeed(generatorHandle, dimension, seed);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to apply seed to generator: " + e.getMessage(), e);
                }
            }
        }
    }
    
    /**
     * Set the world seed (Overworld dimension)
     * @param seed World seed
     */
    public void setSeed(long seed) {
        setSeed(seed, CubiomesInterface.DIM_OVERWORLD);
    }
    
    /**
     * Find structures in a region
     * @param structureType Structure type ID
     * @param centerX Center X coordinate (in blocks)
     * @param centerZ Center Z coordinate (in blocks)
     * @param searchRadius Search radius (in blocks)
     * @param maxResults Maximum number of structures to find
     * @return List of structure positions
     */
    public List<StructurePos> findStructures(int structureType, int centerX, int centerZ, int searchRadius, int maxResults) {
        checkGeneratorReady();
        
        // Check cache first
        StructureSearchKey key = new StructureSearchKey(structureType, centerX, centerZ, searchRadius, maxResults, dimension);
        List<StructurePos> cached = structureCache.get(key);
        if (cached != null) {
            cacheHits++;
            return new ArrayList<>(cached);
        }
        
        // Perform new search
        cacheMisses++;
        totalSearches++;
        List<StructurePos> results = new ArrayList<>();
        
        // Convert to region coordinates
        int centerRegionX = centerX >> 9; // Divide by 512 (region size)
        int centerRegionZ = centerZ >> 9;
        int searchRadiusRegions = (searchRadius >> 9) + 2; // Add buffer
        
        for (int regionX = centerRegionX - searchRadiusRegions; regionX <= centerRegionX + searchRadiusRegions; regionX++) {
            for (int regionZ = centerRegionZ - searchRadiusRegions; regionZ <= centerRegionZ + searchRadiusRegions; regionZ++) {
                try {
                    StructurePos structure = getStructureInRegion(structureType, regionX, regionZ);
                    if (structure != null) {
                        // Check if within search radius
                        double distance = Math.sqrt(Math.pow(structure.x - centerX, 2) + Math.pow(structure.z - centerZ, 2));
                        if (distance <= searchRadius) {
                            results.add(structure);
                            
                            if (results.size() >= maxResults) {
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Log error but continue searching
                    System.err.println("Error searching region (" + regionX + ", " + regionZ + "): " + e.getMessage());
                }
            }
            
            if (results.size() >= maxResults) {
                break;
            }
        }
        
        // Cache results
        if (structureCache.size() >= CACHE_LIMIT) {
            // Remove oldest entries (simple LRU approximation)
            structureCache.entrySet().removeIf(entry -> Math.random() < 0.1);
        }
        
        List<StructurePos> cachedResults = new ArrayList<>(results);
        structureCache.put(key, cachedResults);
        
        return results;
    }
    
    /**
     * Get structure position in a specific region
     * @param structureType Structure type ID
     * @param regionX Region X coordinate
     * @param regionZ Region Z coordinate
     * @return StructurePos or null if no structure
     */
    public StructurePos getStructureInRegion(int structureType, int regionX, int regionZ) {
        checkGeneratorReady();
        
        synchronized (generatorLock) {
            try {
                int[] pos = CubiomesInterface.getStructurePos(structureType, mcVersion, currentSeed, regionX, regionZ);
                if (pos != null && pos.length >= 2) {
                    // Validate the structure position
                    boolean isViable = CubiomesInterface.isViableStructurePos(structureType, generatorHandle, pos[0], pos[1], 0);
                    if (isViable) {
                        return new StructurePos(pos[0], pos[1], structureType, regionX, regionZ, true);
                    } else {
                        return new StructurePos(pos[0], pos[1], structureType, regionX, regionZ, false);
                    }
                }
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get structure in region (" + regionX + ", " + regionZ + "): " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Check if a structure can generate at the given position
     * @param structureType Structure type ID
     * @param x X coordinate
     * @param z Z coordinate
     * @return True if structure is viable at this position
     */
    public boolean isViableStructurePos(int structureType, int x, int z) {
        checkGeneratorReady();
        
        synchronized (generatorLock) {
            try {
                return CubiomesInterface.isViableStructurePos(structureType, generatorHandle, x, z, 0);
            } catch (Exception e) {
                return false;
            }
        }
    }
    
    /**
     * Find strongholds for the current seed
     * @param maxCount Maximum number of strongholds to find
     * @return List of stronghold positions
     */
    public List<StructurePos> findStrongholds(int maxCount) {
        checkGeneratorReady();
        
        synchronized (generatorLock) {
            try {
                int[] positions = CubiomesInterface.getStrongholds(generatorHandle, currentSeed, maxCount);
                List<StructurePos> strongholds = new ArrayList<>();
                
                if (positions != null) {
                    for (int i = 0; i < positions.length; i += 2) {
                        if (i + 1 < positions.length) {
                            strongholds.add(new StructurePos(
                                positions[i], positions[i + 1], 
                                CubiomesInterface.STRONGHOLD, 
                                positions[i] >> 9, positions[i + 1] >> 9, 
                                true
                            ));
                        }
                    }
                }
                
                return strongholds;
            } catch (Exception e) {
                throw new RuntimeException("Failed to find strongholds: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Get spawn point for the current seed
     * @return StructurePos representing the spawn point
     */
    public StructurePos getSpawnPoint() {
        checkGeneratorReady();
        
        synchronized (generatorLock) {
            try {
                int[] spawn = CubiomesInterface.getSpawn(generatorHandle, currentSeed);
                if (spawn != null && spawn.length >= 2) {
                    return new StructurePos(spawn[0], spawn[1], -1, spawn[0] >> 9, spawn[1] >> 9, true) {
                        @Override
                        public String getTypeName() {
                            return "Spawn Point";
                        }
                    };
                }
                return null;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get spawn point: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Find structures asynchronously
     * @param structureType Structure type ID
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @param searchRadius Search radius
     * @param maxResults Maximum results
     * @return CompletableFuture with structure list
     */
    public CompletableFuture<List<StructurePos>> findStructuresAsync(int structureType, int centerX, int centerZ, int searchRadius, int maxResults) {
        return CompletableFuture.supplyAsync(() -> findStructures(structureType, centerX, centerZ, searchRadius, maxResults), executor);
    }
    
    /**
     * Get structure name for display
     * @param structureType Structure type ID
     * @return Human-readable structure name
     */
    public String getStructureName(int structureType) {
        try {
            return CubiomesInterface.getStructureName(structureType);
        } catch (Exception e) {
            return "Unknown Structure (" + structureType + ")";
        }
    }
    
    /**
     * Check if generator is ready for use
     */
    private void checkGeneratorReady() {
        if (generatorHandle == 0) {
            throw new IllegalStateException("StructureGenerator not initialized");
        }
        if (currentSeed == Long.MIN_VALUE) {
            throw new IllegalStateException("World seed not set");
        }
    }
    
    /**
     * Get performance statistics
     * @return StructureStats with performance data
     */
    public StructureStats getStats() {
        double cacheHitRate = (cacheHits + cacheMisses) > 0 ? (double) cacheHits / (cacheHits + cacheMisses) : 0.0;
        
        return new StructureStats(
            totalSearches,
            cacheHitRate,
            structureCache.size(),
            currentSeed,
            dimension
        );
    }
    
    /**
     * Clear all cached data
     */
    public void clearCache() {
        structureCache.clear();
        cacheHits = 0;
        cacheMisses = 0;
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        executor.shutdown();
        structureCache.clear();
        
        synchronized (generatorLock) {
            if (generatorHandle != 0) {
                CubiomesInterface.freeGenerator(generatorHandle);
                generatorHandle = 0;
            }
        }
    }
    
    /**
     * Cache key for structure searches
     */
    private static class StructureSearchKey {
        final int structureType, centerX, centerZ, searchRadius, maxResults, dimension;
        final int hashCode;
        
        StructureSearchKey(int structureType, int centerX, int centerZ, int searchRadius, int maxResults, int dimension) {
            this.structureType = structureType;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.searchRadius = searchRadius;
            this.maxResults = maxResults;
            this.dimension = dimension;
            this.hashCode = Objects.hash(structureType, centerX, centerZ, searchRadius, maxResults, dimension);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof StructureSearchKey)) return false;
            StructureSearchKey other = (StructureSearchKey) obj;
            return structureType == other.structureType && centerX == other.centerX && 
                   centerZ == other.centerZ && searchRadius == other.searchRadius && 
                   maxResults == other.maxResults && dimension == other.dimension;
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
    }
    
    /**
     * Represents a structure position with metadata
     */
    public static class StructurePos {
        public final int x, z;
        public final int structureType;
        public final int regionX, regionZ;
        public final boolean isViable;
        
        public StructurePos(int x, int z, int structureType, int regionX, int regionZ, boolean isViable) {
            this.x = x;
            this.z = z;
            this.structureType = structureType;
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.isViable = isViable;
        }
        
        /**
         * Get the structure type name
         * @return Human-readable structure name
         */
        public String getTypeName() {
            try {
                return CubiomesInterface.getStructureName(structureType);
            } catch (Exception e) {
                return "Unknown Structure";
            }
        }
        
        /**
         * Get distance to another position
         * @param x Target X coordinate
         * @param z Target Z coordinate
         * @return Distance in blocks
         */
        public double getDistanceTo(int x, int z) {
            return Math.sqrt(Math.pow(this.x - x, 2) + Math.pow(this.z - z, 2));
        }
        
        @Override
        public String toString() {
            return String.format("%s at (%d, %d) [%s]", getTypeName(), x, z, isViable ? "viable" : "invalid");
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof StructurePos)) return false;
            StructurePos other = (StructurePos) obj;
            return x == other.x && z == other.z && structureType == other.structureType;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(x, z, structureType);
        }
    }
    
    /**
     * Performance statistics for structure generation
     */
    public static class StructureStats {
        public final long totalSearches;
        public final double cacheHitRate;
        public final int cacheSize;
        public final long currentSeed;
        public final int dimension;
        
        StructureStats(long totalSearches, double cacheHitRate, int cacheSize, long currentSeed, int dimension) {
            this.totalSearches = totalSearches;
            this.cacheHitRate = cacheHitRate;
            this.cacheSize = cacheSize;
            this.currentSeed = currentSeed;
            this.dimension = dimension;
        }
        
        @Override
        public String toString() {
            return String.format("StructureGenerator Stats: %d searches, %.1f%% cache hit rate, %d cached searches",
                totalSearches, cacheHitRate * 100, cacheSize);
        }
    }
}
