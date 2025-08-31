package cubeium.cubeium.world;

import cubeium.cubeium.world.generation.BiomeGenerator;
import java.util.concurrent.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced map caching system with progressive circular loading
 */
public class MapCache {
    
    // Cache structure: seed -> coordinate -> biome data
    private final Map<Long, Map<ChunkCoord, int[]>> seedCaches = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Void>> generationTasks = new ConcurrentHashMap<>();
    
    // Current generation state
    private volatile long currentSeed = 0L;
    private volatile int currentRadius = 0;
    private volatile boolean isGenerating = false;
    
    // Threading
    private final ExecutorService generationExecutor = Executors.newFixedThreadPool(2);
    private BiomeGenerator biomeGenerator;
    
    // Constants
    private static final int CHUNK_SIZE = 64; // 64x64 blocks per chunk
    private static final int MAX_RADIUS = 100; // Maximum chunks from center
    
    public MapCache(BiomeGenerator biomeGenerator) {
        this.biomeGenerator = biomeGenerator;
    }
    
    /**
     * Coordinate key for chunk caching
     */
    private static class ChunkCoord {
        final int x, z;
        
        ChunkCoord(int x, int z) {
            this.x = x;
            this.z = z;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ChunkCoord)) return false;
            ChunkCoord other = (ChunkCoord) obj;
            return x == other.x && z == other.z;
        }
        
        @Override
        public int hashCode() {
            return x * 31 + z;
        }
    }
    
    /**
     * Start generating map for a seed - progressive circular loading
     */
    public CompletableFuture<Void> generateMapAsync(long seed) {
        // Cancel any existing generation
        cancelGeneration();
        
        currentSeed = seed;
        currentRadius = 0;
        isGenerating = true;
        
        // Create cache for this seed if it doesn't exist
        seedCaches.computeIfAbsent(seed, k -> new ConcurrentHashMap<>());
        
        System.out.println("[MapCache] Starting circular generation for seed: " + seed);
        
        // Start progressive generation
        CompletableFuture<Void> task = CompletableFuture.runAsync(() -> {
            try {
                System.out.println("[MapCache] Setting BiomeGenerator seed to: " + seed);
                biomeGenerator.setSeed(seed, 0); // Set seed for overworld
                System.out.println("[MapCache] BiomeGenerator seed set successfully to: " + seed);
                
                // Generate in expanding circles
                for (int radius = 0; radius <= MAX_RADIUS && isGenerating; radius++) {
                    currentRadius = radius;
                    generateRadius(seed, radius);
                    
                    // Small delay to allow UI updates
                    Thread.sleep(radius == 0 ? 0 : 10);
                }
                
                System.out.println("[MapCache] Completed generation for seed: " + seed);
            } catch (InterruptedException e) {
                System.out.println("[MapCache] Generation interrupted for seed: " + seed);
            } catch (Exception e) {
                System.err.println("[MapCache] Error generating seed " + seed + ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                isGenerating = false;
            }
        }, generationExecutor);
        
        generationTasks.put(seed, task);
        return task;
    }
    
    /**
     * Generate all chunks at a specific radius from center (0,0)
     */
    private void generateRadius(long seed, int radius) {
        Map<ChunkCoord, int[]> cache = seedCaches.get(seed);
        if (cache == null) return;
        
        if (radius == 0) {
            // Generate center chunk
            generateChunk(cache, 0, 0);
            return;
        }
        
        // Generate chunks in a circle at this radius
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!isGenerating) return;
                
                // Only generate chunks at exactly this radius (circle boundary)
                int distanceSquared = x * x + z * z;
                int currentRadiusSquared = radius * radius;
                int prevRadiusSquared = (radius - 1) * (radius - 1);
                
                if (distanceSquared <= currentRadiusSquared && distanceSquared > prevRadiusSquared) {
                    generateChunk(cache, x, z);
                }
            }
        }
        
        System.out.println("[MapCache] Completed radius " + radius + " for seed " + seed + 
                          " (" + cache.size() + " chunks cached)");
    }
    
    /**
     * Generate a single chunk (64x64 blocks)
     */
    private void generateChunk(Map<ChunkCoord, int[]> cache, int chunkX, int chunkZ) {
        ChunkCoord coord = new ChunkCoord(chunkX, chunkZ);
        
        // Skip if already cached
        if (cache.containsKey(coord)) return;
        
        int[] chunkData = generateChunkData(chunkX, chunkZ);
        cache.put(coord, chunkData);
    }
    
    /**
     * Generate data for a single chunk
     */
    private int[] generateChunkData(int chunkX, int chunkZ) {
        int[] chunkData = new int[CHUNK_SIZE * CHUNK_SIZE];
        
        // Generate biomes for this chunk
        for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
            for (int localX = 0; localX < CHUNK_SIZE; localX++) {
                int worldX = chunkX * CHUNK_SIZE + localX;
                int worldZ = chunkZ * CHUNK_SIZE + localZ;
                
                try {
                    // Convert to biome coordinates (scale 4)
                    int biomeX = worldX >> 2;
                    int biomeZ = worldZ >> 2;
                    
                    int biomeId = biomeGenerator.getBiomeAt(biomeX, 64, biomeZ);
                    chunkData[localZ * CHUNK_SIZE + localX] = biomeId;
                } catch (Exception e) {
                    chunkData[localZ * CHUNK_SIZE + localX] = 0; // Default to ocean
                }
            }
        }
        
        return chunkData;
    }
    
    /**
     * Get biome data for a specific area from cache
     */
    public int[] getBiomeArea(long seed, int centerX, int centerZ, int width, int height, int zoomLevel) {
        System.out.println("[MapCache] getBiomeArea called - seed: " + seed + ", center: (" + centerX + ", " + centerZ + "), size: " + width + "x" + height + ", zoom: " + zoomLevel);
        
        Map<ChunkCoord, int[]> cache = seedCaches.get(seed);
        if (cache == null) {
            cache = new ConcurrentHashMap<>();
            seedCaches.put(seed, cache);
        }
        
        System.out.println("[MapCache] Cache found for seed: " + seed + " with " + cache.size() + " chunks");
        
        int[] result = new int[width * height];
        
        // Calculate world coordinates based on zoom
        int blocksWidth = width * zoomLevel;
        int blocksHeight = height * zoomLevel;
        
        // Ensure we have the biome generator set up for this seed
        if (biomeGenerator != null) {
            try {
                biomeGenerator.setSeed(seed, 0);
            } catch (Exception e) {
                System.err.println("[MapCache] Error setting seed: " + e.getMessage());
            }
        }
        
        for (int pixelZ = 0; pixelZ < height; pixelZ++) {
            for (int pixelX = 0; pixelX < width; pixelX++) {
                // Calculate world position for this pixel
                int worldX = centerX + (pixelX * zoomLevel - blocksWidth / 2);
                int worldZ = centerZ + (pixelZ * zoomLevel - blocksHeight / 2);
                
                // Find which chunk this world position is in
                int chunkX = Math.floorDiv(worldX, CHUNK_SIZE);
                int chunkZ = Math.floorDiv(worldZ, CHUNK_SIZE);
                
                ChunkCoord coord = new ChunkCoord(chunkX, chunkZ);
                int[] chunkData = cache.get(coord);
                
                if (chunkData == null) {
                    // Generate chunk on-demand
                    chunkData = generateChunkData(chunkX, chunkZ);
                    cache.put(coord, chunkData);
                }
                
                if (chunkData != null) {
                    // Calculate local position within chunk
                    int localX = worldX - chunkX * CHUNK_SIZE;
                    int localZ = worldZ - chunkZ * CHUNK_SIZE;
                    
                    // Clamp to chunk bounds
                    localX = Math.max(0, Math.min(CHUNK_SIZE - 1, localX));
                    localZ = Math.max(0, Math.min(CHUNK_SIZE - 1, localZ));
                    
                    int biomeId = chunkData[localZ * CHUNK_SIZE + localX];
                    result[pixelZ * width + pixelX] = biomeId;
                } else {
                    // Fallback to direct biome generation
                    try {
                        int biomeX = worldX >> 2;
                        int biomeZ = worldZ >> 2;
                        int biomeId = biomeGenerator.getBiomeAt(biomeX, 64, biomeZ);
                        result[pixelZ * width + pixelX] = biomeId;
                    } catch (Exception e) {
                        result[pixelZ * width + pixelX] = 0;
                    }
                }
            }
        }
        
        System.out.println("[MapCache] getBiomeArea returning array of " + result.length + " elements");
        return result;
    }
    
    /**
     * Check if a seed has any cached data
     */
    public boolean hasCachedData(long seed) {
        Map<ChunkCoord, int[]> cache = seedCaches.get(seed);
        return cache != null && !cache.isEmpty();
    }
    
    /**
     * Get current generation progress (0.0 to 1.0)
     */
    public float getGenerationProgress(long seed) {
        if (!isGenerating || seed != currentSeed) {
            return hasCachedData(seed) ? 1.0f : 0.0f;
        }
        
        return Math.min(1.0f, (float) currentRadius / MAX_RADIUS);
    }
    
    /**
     * Get number of chunks loaded for a seed
     */
    public int getChunkCount(long seed) {
        Map<ChunkCoord, int[]> cache = seedCaches.get(seed);
        return cache != null ? cache.size() : 0;
    }
    
    /**
     * Cancel current generation
     */
    public void cancelGeneration() {
        isGenerating = false;
        CompletableFuture<Void> currentTask = generationTasks.get(currentSeed);
        if (currentTask != null) {
            currentTask.cancel(true);
        }
    }
    
    /**
     * Clear cache for a specific seed
     */
    public void clearSeed(long seed) {
        cancelGeneration();
        seedCaches.remove(seed);
        generationTasks.remove(seed);
        System.out.println("[MapCache] Cleared cache for seed: " + seed);
    }
    
    /**
     * Clear all cached data
     */
    public void clearAll() {
        cancelGeneration();
        seedCaches.clear();
        generationTasks.clear();
        System.out.println("[MapCache] Cleared all cached data");
    }
    
    /**
     * Get cache statistics
     */
    public String getCacheStats() {
        int totalChunks = seedCaches.values().stream()
                .mapToInt(Map::size)
                .sum();
        
        return String.format("Seeds: %d, Chunks: %d, Generating: %s", 
                seedCaches.size(), totalChunks, isGenerating);
    }
    
    /**
     * Shutdown the cache system
     */
    public void shutdown() {
        cancelGeneration();
        generationExecutor.shutdown();
        try {
            if (!generationExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                generationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            generationExecutor.shutdownNow();
        }
        clearAll();
        System.out.println("[MapCache] Shutdown complete");
    }
}
