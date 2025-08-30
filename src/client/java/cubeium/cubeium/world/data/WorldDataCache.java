package cubeium.cubeium.world.data;

import cubeium.cubeium.world.generation.BiomeGenerator;
import cubeium.cubeium.world.generation.StructureGenerator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * Manages cached world generation data with efficient storage and retrieval.
 * Provides thread-safe access to biome and structure data with memory management.
 */
public class WorldDataCache {
    private static final int DEFAULT_MAX_REGIONS = 5000;
    private static final int DEFAULT_MAX_STRUCTURE_SEARCHES = 2000;
    private static final long CACHE_CLEANUP_INTERVAL = 30000; // 30 seconds
    
    // Cache storage
    private final Map<RegionKey, CachedBiomeRegion> biomeCache;
    private final Map<StructureSearchKey, CachedStructureSearch> structureCache;
    private final Map<ChunkKey, CachedChunkData> chunkCache;
    
    // Configuration
    private final int maxRegions;
    private final int maxStructureSearches;
    private final int maxChunks;
    
    // Thread safety
    private final ReentrantReadWriteLock biomeCacheLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock structureCacheLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock chunkCacheLock = new ReentrantReadWriteLock();
    
    // Statistics
    private volatile long totalBiomeRequests = 0;
    private volatile long biomeCacheHits = 0;
    private volatile long totalStructureRequests = 0;
    private volatile long structureCacheHits = 0;
    private volatile long totalChunkRequests = 0;
    private volatile long chunkCacheHits = 0;
    private volatile long lastCleanupTime = System.currentTimeMillis();
    
    // Current world state
    private volatile long currentSeed = Long.MIN_VALUE;
    private volatile int currentDimension = 0;
    
    /**
     * Create a WorldDataCache with default settings
     */
    public WorldDataCache() {
        this(DEFAULT_MAX_REGIONS, DEFAULT_MAX_STRUCTURE_SEARCHES, DEFAULT_MAX_REGIONS);
    }
    
    /**
     * Create a WorldDataCache with custom limits
     * @param maxRegions Maximum cached biome regions
     * @param maxStructureSearches Maximum cached structure searches
     * @param maxChunks Maximum cached chunk data
     */
    public WorldDataCache(int maxRegions, int maxStructureSearches, int maxChunks) {
        this.maxRegions = maxRegions;
        this.maxStructureSearches = maxStructureSearches;
        this.maxChunks = maxChunks;
        
        this.biomeCache = new ConcurrentHashMap<>();
        this.structureCache = new ConcurrentHashMap<>();
        this.chunkCache = new ConcurrentHashMap<>();
    }
    
    /**
     * Set current world seed and dimension
     * @param seed World seed
     * @param dimension Dimension ID
     */
    public void setWorldState(long seed, int dimension) {
        if (this.currentSeed != seed || this.currentDimension != dimension) {
            // Clear cache when world changes
            clearAll();
            this.currentSeed = seed;
            this.currentDimension = dimension;
        }
    }
    
    // ===============================
    // Biome Cache Management
    // ===============================
    
    /**
     * Get cached biome region or null if not found
     * @param x Region X coordinate
     * @param z Region Z coordinate
     * @param width Region width
     * @param height Region height
     * @param scale Scale factor
     * @return Cached BiomeRegion or null
     */
    public BiomeGenerator.BiomeRegion getCachedBiomeRegion(int x, int z, int width, int height, int scale) {
        totalBiomeRequests++;
        
        RegionKey key = new RegionKey(x, z, width, height, scale, currentDimension);
        
        biomeCacheLock.readLock().lock();
        try {
            CachedBiomeRegion cached = biomeCache.get(key);
            if (cached != null && cached.seed == currentSeed) {
                cached.lastAccess = System.currentTimeMillis();
                biomeCacheHits++;
                return cached.region;
            }
            return null;
        } finally {
            biomeCacheLock.readLock().unlock();
        }
    }
    
    /**
     * Cache a biome region
     * @param region BiomeRegion to cache
     */
    public void cacheBiomeRegion(BiomeGenerator.BiomeRegion region) {
        RegionKey key = new RegionKey(region.x, region.z, region.width, region.height, region.scale, currentDimension);
        CachedBiomeRegion cached = new CachedBiomeRegion(region, currentSeed, System.currentTimeMillis());
        
        biomeCacheLock.writeLock().lock();
        try {
            biomeCache.put(key, cached);
            
            // Cleanup if needed
            if (biomeCache.size() > maxRegions) {
                cleanupBiomeCache();
            }
        } finally {
            biomeCacheLock.writeLock().unlock();
        }
    }
    
    // ===============================
    // Structure Cache Management
    // ===============================
    
    /**
     * Get cached structure search results
     * @param structureType Structure type ID
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @param radius Search radius
     * @param maxResults Maximum results
     * @return Cached structure list or null
     */
    public List<StructureGenerator.StructurePos> getCachedStructureSearch(int structureType, int centerX, int centerZ, int radius, int maxResults) {
        totalStructureRequests++;
        
        StructureSearchKey key = new StructureSearchKey(structureType, centerX, centerZ, radius, maxResults, currentDimension);
        
        structureCacheLock.readLock().lock();
        try {
            CachedStructureSearch cached = structureCache.get(key);
            if (cached != null && cached.seed == currentSeed) {
                cached.lastAccess = System.currentTimeMillis();
                structureCacheHits++;
                return new ArrayList<>(cached.structures);
            }
            return null;
        } finally {
            structureCacheLock.readLock().unlock();
        }
    }
    
    /**
     * Cache structure search results
     * @param structureType Structure type ID
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @param radius Search radius
     * @param maxResults Maximum results
     * @param structures List of found structures
     */
    public void cacheStructureSearch(int structureType, int centerX, int centerZ, int radius, int maxResults, List<StructureGenerator.StructurePos> structures) {
        StructureSearchKey key = new StructureSearchKey(structureType, centerX, centerZ, radius, maxResults, currentDimension);
        CachedStructureSearch cached = new CachedStructureSearch(new ArrayList<>(structures), currentSeed, System.currentTimeMillis());
        
        structureCacheLock.writeLock().lock();
        try {
            structureCache.put(key, cached);
            
            // Cleanup if needed
            if (structureCache.size() > maxStructureSearches) {
                cleanupStructureCache();
            }
        } finally {
            structureCacheLock.writeLock().unlock();
        }
    }
    
    // ===============================
    // Chunk Cache Management
    // ===============================
    
    /**
     * Get cached chunk data
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return CachedChunkData or null
     */
    public CachedChunkData getCachedChunk(int chunkX, int chunkZ) {
        totalChunkRequests++;
        
        ChunkKey key = new ChunkKey(chunkX, chunkZ, currentDimension);
        
        chunkCacheLock.readLock().lock();
        try {
            CachedChunkData cached = chunkCache.get(key);
            if (cached != null && cached.seed == currentSeed) {
                cached.lastAccess = System.currentTimeMillis();
                chunkCacheHits++;
                return cached;
            }
            return null;
        } finally {
            chunkCacheLock.readLock().unlock();
        }
    }
    
    /**
     * Cache chunk data
     * @param chunkData CachedChunkData to cache
     */
    public void cacheChunk(CachedChunkData chunkData) {
        ChunkKey key = new ChunkKey(chunkData.chunkX, chunkData.chunkZ, currentDimension);
        
        chunkCacheLock.writeLock().lock();
        try {
            chunkCache.put(key, chunkData);
            
            // Cleanup if needed
            if (chunkCache.size() > maxChunks) {
                cleanupChunkCache();
            }
        } finally {
            chunkCacheLock.writeLock().unlock();
        }
    }
    
    // ===============================
    // Cache Cleanup
    // ===============================
    
    /**
     * Perform periodic cache cleanup
     */
    public void performMaintenance() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastCleanupTime > CACHE_CLEANUP_INTERVAL) {
            cleanupOldEntries();
            lastCleanupTime = currentTime;
        }
    }
    
    private void cleanupBiomeCache() {
        // Remove oldest entries if over limit
        if (biomeCache.size() <= maxRegions * 0.8) return;
        
        long currentTime = System.currentTimeMillis();
        biomeCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().lastAccess > 300000 || // 5 minutes
            Math.random() < 0.1 // Random removal for simple LRU
        );
    }
    
    private void cleanupStructureCache() {
        if (structureCache.size() <= maxStructureSearches * 0.8) return;
        
        long currentTime = System.currentTimeMillis();
        structureCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().lastAccess > 600000 || // 10 minutes
            Math.random() < 0.1
        );
    }
    
    private void cleanupChunkCache() {
        if (chunkCache.size() <= maxChunks * 0.8) return;
        
        long currentTime = System.currentTimeMillis();
        chunkCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().lastAccess > 180000 || // 3 minutes
            Math.random() < 0.1
        );
    }
    
    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();
        long oldThreshold = 600000; // 10 minutes
        
        biomeCacheLock.writeLock().lock();
        try {
            biomeCache.entrySet().removeIf(entry -> currentTime - entry.getValue().lastAccess > oldThreshold);
        } finally {
            biomeCacheLock.writeLock().unlock();
        }
        
        structureCacheLock.writeLock().lock();
        try {
            structureCache.entrySet().removeIf(entry -> currentTime - entry.getValue().lastAccess > oldThreshold);
        } finally {
            structureCacheLock.writeLock().unlock();
        }
        
        chunkCacheLock.writeLock().lock();
        try {
            chunkCache.entrySet().removeIf(entry -> currentTime - entry.getValue().lastAccess > oldThreshold);
        } finally {
            chunkCacheLock.writeLock().unlock();
        }
    }
    
    // ===============================
    // Statistics and Management
    // ===============================
    
    /**
     * Get cache statistics
     * @return CacheStats with performance data
     */
    public CacheStats getStats() {
        double biomeHitRate = totalBiomeRequests > 0 ? (double) biomeCacheHits / totalBiomeRequests : 0.0;
        double structureHitRate = totalStructureRequests > 0 ? (double) structureCacheHits / totalStructureRequests : 0.0;
        double chunkHitRate = totalChunkRequests > 0 ? (double) chunkCacheHits / totalChunkRequests : 0.0;
        
        return new CacheStats(
            biomeCache.size(), structureCache.size(), chunkCache.size(),
            biomeHitRate, structureHitRate, chunkHitRate,
            totalBiomeRequests, totalStructureRequests, totalChunkRequests,
            currentSeed, currentDimension
        );
    }
    
    /**
     * Clear all cached data
     */
    public void clearAll() {
        biomeCacheLock.writeLock().lock();
        try {
            biomeCache.clear();
        } finally {
            biomeCacheLock.writeLock().unlock();
        }
        
        structureCacheLock.writeLock().lock();
        try {
            structureCache.clear();
        } finally {
            structureCacheLock.writeLock().unlock();
        }
        
        chunkCacheLock.writeLock().lock();
        try {
            chunkCache.clear();
        } finally {
            chunkCacheLock.writeLock().unlock();
        }
        
        // Reset statistics
        totalBiomeRequests = 0;
        biomeCacheHits = 0;
        totalStructureRequests = 0;
        structureCacheHits = 0;
        totalChunkRequests = 0;
        chunkCacheHits = 0;
    }
    
    /**
     * Get estimated memory usage in bytes
     * @return Estimated memory usage
     */
    public long getEstimatedMemoryUsage() {
        long biomeMemory = biomeCache.size() * 1024; // Rough estimate
        long structureMemory = structureCache.size() * 256;
        long chunkMemory = chunkCache.size() * 512;
        return biomeMemory + structureMemory + chunkMemory;
    }
    
    // ===============================
    // Cache Key Classes
    // ===============================
    
    private static class RegionKey {
        final int x, z, width, height, scale, dimension;
        final int hashCode;
        
        RegionKey(int x, int z, int width, int height, int scale, int dimension) {
            this.x = x;
            this.z = z;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.dimension = dimension;
            this.hashCode = Objects.hash(x, z, width, height, scale, dimension);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof RegionKey)) return false;
            RegionKey other = (RegionKey) obj;
            return x == other.x && z == other.z && width == other.width && 
                   height == other.height && scale == other.scale && dimension == other.dimension;
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
    }
    
    private static class StructureSearchKey {
        final int structureType, centerX, centerZ, radius, maxResults, dimension;
        final int hashCode;
        
        StructureSearchKey(int structureType, int centerX, int centerZ, int radius, int maxResults, int dimension) {
            this.structureType = structureType;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.maxResults = maxResults;
            this.dimension = dimension;
            this.hashCode = Objects.hash(structureType, centerX, centerZ, radius, maxResults, dimension);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof StructureSearchKey)) return false;
            StructureSearchKey other = (StructureSearchKey) obj;
            return structureType == other.structureType && centerX == other.centerX && 
                   centerZ == other.centerZ && radius == other.radius && 
                   maxResults == other.maxResults && dimension == other.dimension;
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
    }
    
    private static class ChunkKey {
        final int chunkX, chunkZ, dimension;
        final int hashCode;
        
        ChunkKey(int chunkX, int chunkZ, int dimension) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.dimension = dimension;
            this.hashCode = Objects.hash(chunkX, chunkZ, dimension);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ChunkKey)) return false;
            ChunkKey other = (ChunkKey) obj;
            return chunkX == other.chunkX && chunkZ == other.chunkZ && dimension == other.dimension;
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
    }
    
    // ===============================
    // Cache Data Classes
    // ===============================
    
    private static class CachedBiomeRegion {
        final BiomeGenerator.BiomeRegion region;
        final long seed;
        volatile long lastAccess;
        
        CachedBiomeRegion(BiomeGenerator.BiomeRegion region, long seed, long lastAccess) {
            this.region = region;
            this.seed = seed;
            this.lastAccess = lastAccess;
        }
    }
    
    private static class CachedStructureSearch {
        final List<StructureGenerator.StructurePos> structures;
        final long seed;
        volatile long lastAccess;
        
        CachedStructureSearch(List<StructureGenerator.StructurePos> structures, long seed, long lastAccess) {
            this.structures = structures;
            this.seed = seed;
            this.lastAccess = lastAccess;
        }
    }
    
    /**
     * Cached chunk data with biomes and structures
     */
    public static class CachedChunkData {
        public final int chunkX, chunkZ;
        public final int[] biomes; // 16x16 biome array
        public final Set<StructureGenerator.StructurePos> structures;
        public final long seed;
        volatile long lastAccess;
        
        public CachedChunkData(int chunkX, int chunkZ, int[] biomes, Set<StructureGenerator.StructurePos> structures, long seed) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.biomes = biomes;
            this.structures = structures != null ? new HashSet<>(structures) : new HashSet<>();
            this.seed = seed;
            this.lastAccess = System.currentTimeMillis();
        }
        
        /**
         * Get biome at local chunk coordinates
         * @param localX Local X (0-15)
         * @param localZ Local Z (0-15)
         * @return Biome ID
         */
        public int getBiome(int localX, int localZ) {
            if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
                throw new IndexOutOfBoundsException("Chunk coordinates out of bounds: (" + localX + ", " + localZ + ")");
            }
            return biomes[localZ * 16 + localX];
        }
    }
    
    /**
     * Cache statistics
     */
    public static class CacheStats {
        public final int biomeCacheSize, structureCacheSize, chunkCacheSize;
        public final double biomeHitRate, structureHitRate, chunkHitRate;
        public final long totalBiomeRequests, totalStructureRequests, totalChunkRequests;
        public final long currentSeed;
        public final int currentDimension;
        
        CacheStats(int biomeCacheSize, int structureCacheSize, int chunkCacheSize,
                  double biomeHitRate, double structureHitRate, double chunkHitRate,
                  long totalBiomeRequests, long totalStructureRequests, long totalChunkRequests,
                  long currentSeed, int currentDimension) {
            this.biomeCacheSize = biomeCacheSize;
            this.structureCacheSize = structureCacheSize;
            this.chunkCacheSize = chunkCacheSize;
            this.biomeHitRate = biomeHitRate;
            this.structureHitRate = structureHitRate;
            this.chunkHitRate = chunkHitRate;
            this.totalBiomeRequests = totalBiomeRequests;
            this.totalStructureRequests = totalStructureRequests;
            this.totalChunkRequests = totalChunkRequests;
            this.currentSeed = currentSeed;
            this.currentDimension = currentDimension;
        }
        
        @Override
        public String toString() {
            return String.format("WorldDataCache: Biomes %d (%.1f%%), Structures %d (%.1f%%), Chunks %d (%.1f%%)",
                biomeCacheSize, biomeHitRate * 100,
                structureCacheSize, structureHitRate * 100,
                chunkCacheSize, chunkHitRate * 100);
        }
    }
}
