package cubeium.cubeium.rendering.cache;

import cubeium.cubeium.world.generation.BiomeGenerator;
import cubeium.cubeium.world.generation.StructureGenerator;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import java.awt.image.BufferedImage;

/**
 * Advanced map cache system optimized for rendering performance.
 * Manages map tiles, biome data, and structure overlays with efficient storage.
 */
public class MapCache {
    private static final int DEFAULT_TILE_SIZE = 256; // 256x256 pixels per tile
    private static final int DEFAULT_MAX_TILES = 10000;
    private static final int DEFAULT_BIOME_SCALE = 4; // 4 blocks per biome coordinate
    private static final long CACHE_CLEANUP_INTERVAL = 30000; // 30 seconds
    
    // Cache storage
    private final Map<TileKey, CachedMapTile> tileCache;
    private final Map<BiomeRegionKey, CachedBiomeData> biomeDataCache;
    private final Map<StructureRegionKey, CachedStructureData> structureDataCache;
    
    // Configuration
    private final int tileSize;
    private final int maxTiles;
    private final int biomeScale;
    
    // Thread safety
    private final ReentrantReadWriteLock tileCacheLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock biomeCacheLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock structureCacheLock = new ReentrantReadWriteLock();
    
    // Statistics
    private volatile long totalTileRequests = 0;
    private volatile long tileCacheHits = 0;
    private volatile long totalBiomeRequests = 0;
    private volatile long biomeCacheHits = 0;
    private volatile long lastCleanupTime = System.currentTimeMillis();
    
    // Current world state
    private volatile long currentSeed = Long.MIN_VALUE;
    private volatile int currentDimension = 0;
    private volatile int zoomLevel = 0; // 0 = 1:1 scale, positive = zoomed out, negative = zoomed in
    
    /**
     * Create a MapCache with default settings
     */
    public MapCache() {
        this(DEFAULT_TILE_SIZE, DEFAULT_MAX_TILES, DEFAULT_BIOME_SCALE);
    }
    
    /**
     * Create a MapCache with custom settings
     * @param tileSize Size of each tile in pixels (power of 2 recommended)
     * @param maxTiles Maximum number of cached tiles
     * @param biomeScale Scale factor for biome coordinates (4 = standard)
     */
    public MapCache(int tileSize, int maxTiles, int biomeScale) {
        this.tileSize = tileSize;
        this.maxTiles = maxTiles;
        this.biomeScale = biomeScale;
        
        this.tileCache = new ConcurrentHashMap<>();
        this.biomeDataCache = new ConcurrentHashMap<>();
        this.structureDataCache = new ConcurrentHashMap<>();
    }
    
    /**
     * Set current world state and clear cache if changed
     * @param seed World seed
     * @param dimension Dimension ID
     * @param zoomLevel Current zoom level
     */
    public void setWorldState(long seed, int dimension, int zoomLevel) {
        boolean worldChanged = this.currentSeed != seed || this.currentDimension != dimension;
        boolean zoomChanged = this.zoomLevel != zoomLevel;
        
        if (worldChanged) {
            clearAll();
        } else if (zoomChanged) {
            clearTiles(); // Only clear rendered tiles, keep biome data
        }
        
        this.currentSeed = seed;
        this.currentDimension = dimension;
        this.zoomLevel = zoomLevel;
    }
    
    // ===============================
    // Tile Cache Management
    // ===============================
    
    /**
     * Get cached map tile or null if not found
     * @param tileX Tile X coordinate
     * @param tileZ Tile Z coordinate
     * @param zoomLevel Zoom level
     * @param renderFlags Rendering flags (biomes, structures, etc.)
     * @return CachedMapTile or null
     */
    public CachedMapTile getCachedTile(int tileX, int tileZ, int zoomLevel, int renderFlags) {
        totalTileRequests++;
        
        TileKey key = new TileKey(tileX, tileZ, zoomLevel, renderFlags, currentDimension);
        
        tileCacheLock.readLock().lock();
        try {
            CachedMapTile cached = tileCache.get(key);
            if (cached != null && cached.seed == currentSeed && cached.isValid()) {
                cached.lastAccess = System.currentTimeMillis();
                tileCacheHits++;
                return cached;
            }
            return null;
        } finally {
            tileCacheLock.readLock().unlock();
        }
    }
    
    /**
     * Cache a map tile
     * @param tileX Tile X coordinate
     * @param tileZ Tile Z coordinate
     * @param zoomLevel Zoom level
     * @param renderFlags Rendering flags
     * @param tileImage Rendered tile image
     */
    public void cacheTile(int tileX, int tileZ, int zoomLevel, int renderFlags, BufferedImage tileImage) {
        TileKey key = new TileKey(tileX, tileZ, zoomLevel, renderFlags, currentDimension);
        CachedMapTile cached = new CachedMapTile(tileImage, currentSeed, System.currentTimeMillis());
        
        tileCacheLock.writeLock().lock();
        try {
            tileCache.put(key, cached);
            
            // Cleanup if needed
            if (tileCache.size() > maxTiles) {
                cleanupTileCache();
            }
        } finally {
            tileCacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Get tiles in a viewport area
     * @param viewportX Viewport center X (world coordinates)
     * @param viewportZ Viewport center Z (world coordinates)
     * @param viewportWidth Viewport width in pixels
     * @param viewportHeight Viewport height in pixels
     * @param zoomLevel Zoom level
     * @param renderFlags Rendering flags
     * @return List of TileRequest objects for missing tiles
     */
    public List<TileRequest> getTilesForViewport(double viewportX, double viewportZ, 
                                               int viewportWidth, int viewportHeight, 
                                               int zoomLevel, int renderFlags) {
        List<TileRequest> missingTiles = new ArrayList<>();
        
        // Calculate scale factor for zoom level
        double scale = Math.pow(2.0, zoomLevel);
        double pixelsPerBlock = 1.0 / scale;
        
        // Calculate tile coverage
        double blocksPerTile = tileSize / pixelsPerBlock;
        
        int startTileX = (int) Math.floor((viewportX - viewportWidth * scale / 2) / blocksPerTile);
        int endTileX = (int) Math.ceil((viewportX + viewportWidth * scale / 2) / blocksPerTile);
        int startTileZ = (int) Math.floor((viewportZ - viewportHeight * scale / 2) / blocksPerTile);
        int endTileZ = (int) Math.ceil((viewportZ + viewportHeight * scale / 2) / blocksPerTile);
        
        // Check each tile in the viewport
        for (int tileX = startTileX; tileX <= endTileX; tileX++) {
            for (int tileZ = startTileZ; tileZ <= endTileZ; tileZ++) {
                CachedMapTile cached = getCachedTile(tileX, tileZ, zoomLevel, renderFlags);
                if (cached == null) {
                    // Calculate world coordinates for this tile
                    double worldX = tileX * blocksPerTile;
                    double worldZ = tileZ * blocksPerTile;
                    int worldWidth = (int) Math.ceil(blocksPerTile);
                    int worldHeight = (int) Math.ceil(blocksPerTile);
                    
                    missingTiles.add(new TileRequest(tileX, tileZ, zoomLevel, renderFlags, 
                                                   worldX, worldZ, worldWidth, worldHeight, scale));
                }
            }
        }
        
        return missingTiles;
    }
    
    // ===============================
    // Biome Data Cache
    // ===============================
    
    /**
     * Get cached biome data for a region
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @param width Region width
     * @param height Region height
     * @return CachedBiomeData or null
     */
    public CachedBiomeData getCachedBiomeData(int worldX, int worldZ, int width, int height) {
        totalBiomeRequests++;
        
        BiomeRegionKey key = new BiomeRegionKey(worldX, worldZ, width, height, biomeScale, currentDimension);
        
        biomeCacheLock.readLock().lock();
        try {
            CachedBiomeData cached = biomeDataCache.get(key);
            if (cached != null && cached.seed == currentSeed) {
                cached.lastAccess = System.currentTimeMillis();
                biomeCacheHits++;
                return cached;
            }
            return null;
        } finally {
            biomeCacheLock.readLock().unlock();
        }
    }
    
    /**
     * Cache biome data
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @param width Region width
     * @param height Region height
     * @param biomeRegion BiomeRegion data
     */
    public void cacheBiomeData(int worldX, int worldZ, int width, int height, BiomeGenerator.BiomeRegion biomeRegion) {
        BiomeRegionKey key = new BiomeRegionKey(worldX, worldZ, width, height, biomeScale, currentDimension);
        CachedBiomeData cached = new CachedBiomeData(biomeRegion, currentSeed, System.currentTimeMillis());
        
        biomeCacheLock.writeLock().lock();
        try {
            biomeDataCache.put(key, cached);
            
            // Simple size-based cleanup
            if (biomeDataCache.size() > maxTiles / 2) {
                cleanupBiomeCache();
            }
        } finally {
            biomeCacheLock.writeLock().unlock();
        }
    }
    
    // ===============================
    // Structure Data Cache
    // ===============================
    
    /**
     * Get cached structure data for a region
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @param width Region width
     * @param height Region height
     * @return CachedStructureData or null
     */
    public CachedStructureData getCachedStructureData(int worldX, int worldZ, int width, int height) {
        StructureRegionKey key = new StructureRegionKey(worldX, worldZ, width, height, currentDimension);
        
        structureCacheLock.readLock().lock();
        try {
            CachedStructureData cached = structureDataCache.get(key);
            if (cached != null && cached.seed == currentSeed) {
                cached.lastAccess = System.currentTimeMillis();
                return cached;
            }
            return null;
        } finally {
            structureCacheLock.readLock().unlock();
        }
    }
    
    /**
     * Cache structure data
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @param width Region width
     * @param height Region height
     * @param structures List of structures in the region
     */
    public void cacheStructureData(int worldX, int worldZ, int width, int height, 
                                  List<StructureGenerator.StructurePos> structures) {
        StructureRegionKey key = new StructureRegionKey(worldX, worldZ, width, height, currentDimension);
        CachedStructureData cached = new CachedStructureData(structures, currentSeed, System.currentTimeMillis());
        
        structureCacheLock.writeLock().lock();
        try {
            structureDataCache.put(key, cached);
            
            // Simple size-based cleanup
            if (structureDataCache.size() > maxTiles / 4) {
                cleanupStructureCache();
            }
        } finally {
            structureCacheLock.writeLock().unlock();
        }
    }
    
    // ===============================
    // Cache Maintenance
    // ===============================
    
    /**
     * Perform periodic cache maintenance
     */
    public void performMaintenance() {
        long currentTime = System.currentTimeMillis();
        
        if (currentTime - lastCleanupTime > CACHE_CLEANUP_INTERVAL) {
            cleanupOldEntries();
            lastCleanupTime = currentTime;
        }
    }
    
    private void cleanupTileCache() {
        if (tileCache.size() <= maxTiles * 0.8) return;
        
        long currentTime = System.currentTimeMillis();
        tileCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().lastAccess > 300000 || // 5 minutes
            !entry.getValue().isValid() ||
            Math.random() < 0.1 // Random removal for simple LRU
        );
    }
    
    private void cleanupBiomeCache() {
        long currentTime = System.currentTimeMillis();
        biomeDataCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().lastAccess > 600000 || // 10 minutes
            Math.random() < 0.1
        );
    }
    
    private void cleanupStructureCache() {
        long currentTime = System.currentTimeMillis();
        structureDataCache.entrySet().removeIf(entry -> 
            currentTime - entry.getValue().lastAccess > 600000 || // 10 minutes
            Math.random() < 0.1
        );
    }
    
    private void cleanupOldEntries() {
        long currentTime = System.currentTimeMillis();
        long oldThreshold = 900000; // 15 minutes
        
        tileCacheLock.writeLock().lock();
        try {
            tileCache.entrySet().removeIf(entry -> 
                currentTime - entry.getValue().lastAccess > oldThreshold || 
                !entry.getValue().isValid());
        } finally {
            tileCacheLock.writeLock().unlock();
        }
        
        biomeCacheLock.writeLock().lock();
        try {
            biomeDataCache.entrySet().removeIf(entry -> currentTime - entry.getValue().lastAccess > oldThreshold);
        } finally {
            biomeCacheLock.writeLock().unlock();
        }
        
        structureCacheLock.writeLock().lock();
        try {
            structureDataCache.entrySet().removeIf(entry -> currentTime - entry.getValue().lastAccess > oldThreshold);
        } finally {
            structureCacheLock.writeLock().unlock();
        }
    }
    
    // ===============================
    // Statistics and Management
    // ===============================
    
    /**
     * Get cache statistics
     * @return MapCacheStats with performance data
     */
    public MapCacheStats getStats() {
        double tileHitRate = totalTileRequests > 0 ? (double) tileCacheHits / totalTileRequests : 0.0;
        double biomeHitRate = totalBiomeRequests > 0 ? (double) biomeCacheHits / totalBiomeRequests : 0.0;
        
        return new MapCacheStats(
            tileCache.size(), biomeDataCache.size(), structureDataCache.size(),
            tileHitRate, biomeHitRate,
            totalTileRequests, totalBiomeRequests,
            getEstimatedMemoryUsage(),
            currentSeed, currentDimension, zoomLevel
        );
    }
    
    /**
     * Clear all cached data
     */
    public void clearAll() {
        clearTiles();
        clearBiomeData();
        clearStructureData();
        
        // Reset statistics
        totalTileRequests = 0;
        tileCacheHits = 0;
        totalBiomeRequests = 0;
        biomeCacheHits = 0;
    }
    
    /**
     * Clear only rendered tiles (keep world data)
     */
    public void clearTiles() {
        tileCacheLock.writeLock().lock();
        try {
            tileCache.clear();
        } finally {
            tileCacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Clear biome data cache
     */
    public void clearBiomeData() {
        biomeCacheLock.writeLock().lock();
        try {
            biomeDataCache.clear();
        } finally {
            biomeCacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Clear structure data cache
     */
    public void clearStructureData() {
        structureCacheLock.writeLock().lock();
        try {
            structureDataCache.clear();
        } finally {
            structureCacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Get estimated memory usage in bytes
     * @return Estimated memory usage
     */
    public long getEstimatedMemoryUsage() {
        long tileMemory = tileCache.size() * tileSize * tileSize * 4; // 4 bytes per pixel (ARGB)
        long biomeMemory = biomeDataCache.size() * 1024; // Rough estimate
        long structureMemory = structureDataCache.size() * 256;
        return tileMemory + biomeMemory + structureMemory;
    }
    
    // ===============================
    // Cache Key Classes
    // ===============================
    
    private static class TileKey {
        final int tileX, tileZ, zoomLevel, renderFlags, dimension;
        final int hashCode;
        
        TileKey(int tileX, int tileZ, int zoomLevel, int renderFlags, int dimension) {
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.zoomLevel = zoomLevel;
            this.renderFlags = renderFlags;
            this.dimension = dimension;
            this.hashCode = Objects.hash(tileX, tileZ, zoomLevel, renderFlags, dimension);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof TileKey)) return false;
            TileKey other = (TileKey) obj;
            return tileX == other.tileX && tileZ == other.tileZ && zoomLevel == other.zoomLevel && 
                   renderFlags == other.renderFlags && dimension == other.dimension;
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
    }
    
    private static class BiomeRegionKey {
        final int worldX, worldZ, width, height, scale, dimension;
        final int hashCode;
        
        BiomeRegionKey(int worldX, int worldZ, int width, int height, int scale, int dimension) {
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.dimension = dimension;
            this.hashCode = Objects.hash(worldX, worldZ, width, height, scale, dimension);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof BiomeRegionKey)) return false;
            BiomeRegionKey other = (BiomeRegionKey) obj;
            return worldX == other.worldX && worldZ == other.worldZ && width == other.width && 
                   height == other.height && scale == other.scale && dimension == other.dimension;
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
    }
    
    private static class StructureRegionKey {
        final int worldX, worldZ, width, height, dimension;
        final int hashCode;
        
        StructureRegionKey(int worldX, int worldZ, int width, int height, int dimension) {
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.width = width;
            this.height = height;
            this.dimension = dimension;
            this.hashCode = Objects.hash(worldX, worldZ, width, height, dimension);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof StructureRegionKey)) return false;
            StructureRegionKey other = (StructureRegionKey) obj;
            return worldX == other.worldX && worldZ == other.worldZ && width == other.width && 
                   height == other.height && dimension == other.dimension;
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
    }
    
    // ===============================
    // Cache Data Classes
    // ===============================
    
    /**
     * Cached map tile with rendered image
     */
    public static class CachedMapTile {
        public final BufferedImage image;
        public final long seed;
        public final long creationTime;
        volatile long lastAccess;
        
        CachedMapTile(BufferedImage image, long seed, long lastAccess) {
            this.image = image;
            this.seed = seed;
            this.creationTime = System.currentTimeMillis();
            this.lastAccess = lastAccess;
        }
        
        /**
         * Check if tile is still valid (not corrupted)
         * @return True if valid
         */
        public boolean isValid() {
            return image != null && image.getWidth() > 0 && image.getHeight() > 0;
        }
    }
    
    /**
     * Cached biome data
     */
    public static class CachedBiomeData {
        public final BiomeGenerator.BiomeRegion biomeRegion;
        public final long seed;
        volatile long lastAccess;
        
        CachedBiomeData(BiomeGenerator.BiomeRegion biomeRegion, long seed, long lastAccess) {
            this.biomeRegion = biomeRegion;
            this.seed = seed;
            this.lastAccess = lastAccess;
        }
    }
    
    /**
     * Cached structure data
     */
    public static class CachedStructureData {
        public final List<StructureGenerator.StructurePos> structures;
        public final long seed;
        volatile long lastAccess;
        
        CachedStructureData(List<StructureGenerator.StructurePos> structures, long seed, long lastAccess) {
            this.structures = new ArrayList<>(structures);
            this.seed = seed;
            this.lastAccess = lastAccess;
        }
    }
    
    /**
     * Request for a missing tile
     */
    public static class TileRequest {
        public final int tileX, tileZ, zoomLevel, renderFlags;
        public final double worldX, worldZ;
        public final int worldWidth, worldHeight;
        public final double scale;
        
        public TileRequest(int tileX, int tileZ, int zoomLevel, int renderFlags,
                          double worldX, double worldZ, int worldWidth, int worldHeight, double scale) {
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.zoomLevel = zoomLevel;
            this.renderFlags = renderFlags;
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.worldWidth = worldWidth;
            this.worldHeight = worldHeight;
            this.scale = scale;
        }
        
        @Override
        public String toString() {
            return String.format("TileRequest[%d,%d @ zoom %d, world (%.0f,%.0f) %dx%d]", 
                tileX, tileZ, zoomLevel, worldX, worldZ, worldWidth, worldHeight);
        }
    }
    
    /**
     * Map cache statistics
     */
    public static class MapCacheStats {
        public final int tileCacheSize, biomeDataCacheSize, structureDataCacheSize;
        public final double tileHitRate, biomeHitRate;
        public final long totalTileRequests, totalBiomeRequests;
        public final long estimatedMemoryUsage;
        public final long currentSeed;
        public final int currentDimension, zoomLevel;
        
        MapCacheStats(int tileCacheSize, int biomeDataCacheSize, int structureDataCacheSize,
                     double tileHitRate, double biomeHitRate,
                     long totalTileRequests, long totalBiomeRequests,
                     long estimatedMemoryUsage,
                     long currentSeed, int currentDimension, int zoomLevel) {
            this.tileCacheSize = tileCacheSize;
            this.biomeDataCacheSize = biomeDataCacheSize;
            this.structureDataCacheSize = structureDataCacheSize;
            this.tileHitRate = tileHitRate;
            this.biomeHitRate = biomeHitRate;
            this.totalTileRequests = totalTileRequests;
            this.totalBiomeRequests = totalBiomeRequests;
            this.estimatedMemoryUsage = estimatedMemoryUsage;
            this.currentSeed = currentSeed;
            this.currentDimension = currentDimension;
            this.zoomLevel = zoomLevel;
        }
        
        @Override
        public String toString() {
            return String.format("MapCache: %d tiles (%.1f%%), %d biomes (%.1f%%), %dMB memory",
                tileCacheSize, tileHitRate * 100, biomeDataCacheSize, biomeHitRate * 100,
                estimatedMemoryUsage / (1024 * 1024));
        }
    }
}
