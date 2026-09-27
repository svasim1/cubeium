package cubeium.cubeium.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import cubeium.cubeium.Cubeium;
import cubeium.cubeium.world.generation.BiomeGenerator;

/**
 * Advanced map caching system with progressive circular loading
 */
public class MapCache {
    
    // Cache structure: seed -> coordinate -> biome data
    private final Map<Long, Map<ChunkCoord, int[]>> seedCaches = new ConcurrentHashMap<>();
    private final Map<Long, Map<ChunkCoord, Long>> seedAccessTimes = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<Void>> generationTasks = new ConcurrentHashMap<>();
    
    // Current generation state
    private volatile long currentSeed = 0L;
    private volatile int currentRadius = 0;
    private volatile boolean isGenerating = false;
    
    // Threading
    // Use more aggressive parallelism by default (leave one core free)
    private final ExecutorService generationExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 1));
    private BiomeGenerator biomeGenerator;
    
    // Prioritized task queue for chunk generation and worker threads
    private final PriorityBlockingQueue<ChunkTask> taskQueue = new PriorityBlockingQueue<>();
    private final List<Thread> workers = new ArrayList<>();
    private final AtomicBoolean workersRunning = new AtomicBoolean(false);
    private final int workerCount = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
    
    // Current viewport center in chunk coordinates (used for prioritization)
    private volatile int viewportCenterChunkX = 0;
    private volatile int viewportCenterChunkZ = 0;
    
    // Constants
    private static final int CHUNK_SIZE = 64; // 64x64 blocks per chunk
    private static final int MAX_RADIUS = 100; // Maximum chunks from center
    private static final int MAX_CHUNKS_PER_SEED = 12000;
    private static final int TARGET_CHUNKS_PER_SEED = 11000;
    private static final int CHUNK_BYTES = CHUNK_SIZE * CHUNK_SIZE * Integer.BYTES;
    
    public MapCache(BiomeGenerator biomeGenerator) {
        this.biomeGenerator = biomeGenerator;
        startWorkers();
    }

    private void startWorkers() {
        if (workersRunning.compareAndSet(false, true)) {
            for (int i = 0; i < workerCount; i++) {
                Thread t = new Thread(() -> {
                    while (workersRunning.get()) {
                        try {
                            ChunkTask task = taskQueue.take();
                            // Only execute if generation is active and seed matches
                            if (!isGenerating) continue;
                            if (task.seed != currentSeed) continue;
                            generateChunk(task.seed, seedCaches.get(task.seed), task.chunkX, task.chunkZ);
                        } catch (InterruptedException e) {
                            // Thread interrupted - exit if we are shutting down
                            Thread.currentThread().interrupt();
                            break;
                        } catch (Exception e) {
                            Cubeium.LOGGER.warn("[MapCache] Worker error", e);
                        }
                    }
                }, "MapCache-Worker-" + i);
                t.setDaemon(true);
                t.start();
                workers.add(t);
            }
        }
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
     * Task for generating a specific chunk, ordered by distance to viewport center.
     */
    private static class ChunkTask implements Comparable<ChunkTask> {
        final long seed;
        final int chunkX;
        final int chunkZ;
        final int centerChunkX;
        final int centerChunkZ;
        final long created;

        ChunkTask(long seed, int chunkX, int chunkZ, int centerChunkX, int centerChunkZ) {
            this.seed = seed;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.centerChunkX = centerChunkX;
            this.centerChunkZ = centerChunkZ;
            this.created = System.currentTimeMillis();
        }

        private long distanceSqToCenter() {
            long dx = chunkX - centerChunkX;
            long dz = chunkZ - centerChunkZ;
            return dx * dx + dz * dz;
        }

        @Override
        public int compareTo(ChunkTask other) {
            long d1 = this.distanceSqToCenter();
            long d2 = other.distanceSqToCenter();
            int cmp = Long.compare(d1, d2);
            if (cmp != 0) return cmp;
            return Long.compare(this.created, other.created);
        }
    }
    
    /**
     * Start generating map for a seed - progressive circular loading
     */
    /**
     * Start prioritized, viewport-first generation for a seed.
     * viewport size is provided in blocks.
     */
    public CompletableFuture<Void> generateMapAsync(long seed, int centerX, int centerZ, int viewWidthBlocks, int viewHeightBlocks) {
        // Cancel any existing generation
        cancelGeneration();

        currentSeed = seed;
        currentRadius = 0;
        isGenerating = true;

        // Compute viewport center in chunk coordinates
        this.viewportCenterChunkX = Math.floorDiv(centerX, CHUNK_SIZE);
        this.viewportCenterChunkZ = Math.floorDiv(centerZ, CHUNK_SIZE);

        // Create cache for this seed if it doesn't exist
        seedCaches.computeIfAbsent(seed, k -> new ConcurrentHashMap<>());
        seedAccessTimes.computeIfAbsent(seed, k -> new ConcurrentHashMap<>());

        // Enqueue viewport-first chunk tasks (with small buffer)
        int worldLeft = centerX - viewWidthBlocks / 2;
        int worldTop = centerZ - viewHeightBlocks / 2;
        int worldRight = worldLeft + viewWidthBlocks - 1;
        int worldBottom = worldTop + viewHeightBlocks - 1;

        int chunkLeft = Math.floorDiv(worldLeft, CHUNK_SIZE);
        int chunkTop = Math.floorDiv(worldTop, CHUNK_SIZE);
        int chunkRight = Math.floorDiv(worldRight, CHUNK_SIZE);
        int chunkBottom = Math.floorDiv(worldBottom, CHUNK_SIZE);

    final int buffer = 2; // keep a slightly larger buffer around viewport to reduce visible streaming
        for (int cz = chunkTop - buffer; cz <= chunkBottom + buffer; cz++) {
            for (int cx = chunkLeft - buffer; cx <= chunkRight + buffer; cx++) {
                taskQueue.offer(new ChunkTask(seed, cx, cz, viewportCenterChunkX, viewportCenterChunkZ));
            }
        }

    // Asynchronously enqueue the rest of the ring-based tasks with priority by distance
        CompletableFuture<Void> enqueuer = CompletableFuture.runAsync(() -> {
            try {
                for (int radius = 0; radius <= MAX_RADIUS && isGenerating; radius++) {
                    currentRadius = radius;
                    // enqueue ring boundary
                    for (int x = -radius; x <= radius && isGenerating; x++) {
                        for (int z = -radius; z <= radius && isGenerating; z++) {
                            int distanceSquared = x * x + z * z;
                            int currentRadiusSquared = radius * radius;
                            int prevRadiusSquared = (radius - 1) * (radius - 1);
                            if (distanceSquared <= currentRadiusSquared && distanceSquared > prevRadiusSquared) {
                                int cx = x;
                                int cz = z;
                                // Do not enqueue if already in cache (skip duplicates)
                                Map<ChunkCoord, int[]> cache = seedCaches.get(seed);
                                if (cache != null && cache.containsKey(new ChunkCoord(cx, cz))) continue;
                                taskQueue.offer(new ChunkTask(seed, cx, cz, viewportCenterChunkX, viewportCenterChunkZ));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Cubeium.LOGGER.warn("[MapCache] Enqueuer error", e);
            } finally {
                // mark generation finished once enqueuer completes (workers may still be processing)
                isGenerating = false;
            }
        }, generationExecutor);

    generationTasks.put(seed, enqueuer);
        return enqueuer;
    }

    /**
     * Backwards-compatible overload for existing callers that don't supply viewport info.
     * Uses center (0,0) and a reasonable viewport size.
     */
    public CompletableFuture<Void> generateMapAsync(long seed) {
        int defaultViewBlocks = 1024; // conservative default viewport
        return generateMapAsync(seed, 0, 0, defaultViewBlocks, defaultViewBlocks);
    }
    
    /**
     * Generate a single chunk (64x64 blocks)
     */
    private void generateChunk(long seed, Map<ChunkCoord, int[]> cache, int chunkX, int chunkZ) {
        if (cache == null) {
            return;
        }

        ChunkCoord coord = new ChunkCoord(chunkX, chunkZ);
        
        // Skip if already cached
        if (cache.containsKey(coord)) {
            touchChunkAccess(seed, coord);
            return;
        }
        
        int[] chunkData = generateChunkData(seed, chunkX, chunkZ);
        if (chunkData != null) {
            putChunkWithEviction(seed, cache, coord, chunkData);
        }
    }
    
    /**
     * Generate data for a single chunk
     */
    private int[] generateChunkData(long seed, int chunkX, int chunkZ) {
        final int cellSize = BiomeGenerator.CELL_SIZE;
        try {
            int[] cells = biomeGenerator.generateCells(seed, chunkX * CHUNK_SIZE, chunkZ * CHUNK_SIZE, CHUNK_SIZE, CHUNK_SIZE);

            // Expand each biome cell to its cellSize x cellSize blocks.
            int[] chunkData = new int[CHUNK_SIZE * CHUNK_SIZE];
            int cells1d = CHUNK_SIZE / cellSize;
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int cellRow = (z / cellSize) * cells1d;
                int row = z * CHUNK_SIZE;
                for (int x = 0; x < CHUNK_SIZE; x++) {
                    chunkData[row + x] = cells[cellRow + x / cellSize];
                }
            }
            return chunkData;
        } catch (Exception e) {
            Cubeium.LOGGER.warn("[MapCache] Error generating chunk ({}, {})", chunkX, chunkZ, e);
            return null;
        }
    }

    /**
     * Get biome data for a specific area from cache
     */
    public int[] getBiomeArea(long seed, int centerX, int centerZ, int width, int height, int zoomLevel) {
    // Reduced logging: removed noisy prints for performance
        
        Map<ChunkCoord, int[]> cache = seedCaches.get(seed);
        if (cache == null) {
            cache = new ConcurrentHashMap<>();
            seedCaches.put(seed, cache);
        }
        seedAccessTimes.computeIfAbsent(seed, k -> new ConcurrentHashMap<>());
        
        int[] result = new int[width * height];
        
        // Calculate world coordinates based on zoom
        int blocksWidth = width * zoomLevel;
        int blocksHeight = height * zoomLevel;
        
        // Ensure we have the biome generator set up for this seed
    // BiomeGenerator seed is set once during generation; leave unchanged here for performance
        
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
                    // Generate chunk on-demand (synchronous fallback)
                    chunkData = generateChunkData(seed, chunkX, chunkZ);
                    if (chunkData != null) {
                        putChunkWithEviction(seed, cache, coord, chunkData);
                    }
                } else {
                    touchChunkAccess(seed, coord);
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
                    // Phase 3: bulk generation in generateChunkData should now handle all cases
                    result[pixelZ * width + pixelX] = -1; // no data
                }
            }
        }
        
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
     * Get generation progress focused on a viewport (returns 0.0 to 1.0).
     * This computes how many chunks covering the viewport are currently cached.
     * viewport dimensions must be provided in world blocks.
     */
    public float getGenerationProgressForViewport(long seed, int centerX, int centerZ, int widthBlocks, int heightBlocks) {
        Map<ChunkCoord, int[]> cache = seedCaches.get(seed);
        if (cache == null || cache.isEmpty()) return 0.0f;

        // Determine chunk range covering the viewport
        int worldLeft = centerX - widthBlocks / 2;
        int worldTop = centerZ - heightBlocks / 2;
        int worldRight = worldLeft + widthBlocks - 1;
        int worldBottom = worldTop + heightBlocks - 1;

        int chunkLeft = Math.floorDiv(worldLeft, CHUNK_SIZE);
        int chunkTop = Math.floorDiv(worldTop, CHUNK_SIZE);
        int chunkRight = Math.floorDiv(worldRight, CHUNK_SIZE);
        int chunkBottom = Math.floorDiv(worldBottom, CHUNK_SIZE);

        int totalChunks = (chunkRight - chunkLeft + 1) * (chunkBottom - chunkTop + 1);
        if (totalChunks <= 0) return 0.0f;

        int present = 0;
        for (int cz = chunkTop; cz <= chunkBottom; cz++) {
            for (int cx = chunkLeft; cx <= chunkRight; cx++) {
                ChunkCoord coord = new ChunkCoord(cx, cz);
                if (cache.containsKey(coord)) present++;
            }
        }

        return Math.min(1.0f, (float) present / (float) totalChunks);
    }
    
    /**
     * Get number of chunks loaded for a seed
     */
    public int getChunkCount(long seed) {
        Map<ChunkCoord, int[]> cache = seedCaches.get(seed);
        return cache != null ? cache.size() : 0;
    }

    /**
     * Get current pending generation tasks in the queue.
     */
    public int getPendingTaskCount() {
        return taskQueue.size();
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
    taskQueue.clear();
    }
    
    /**
     * Clear cache for a specific seed
     */
    public void clearSeed(long seed) {
        cancelGeneration();
        seedCaches.remove(seed);
        seedAccessTimes.remove(seed);
        generationTasks.remove(seed);
    }
    
    /**
     * Clear all cached data
     */
    public void clearAll() {
        cancelGeneration();
        seedCaches.clear();
        seedAccessTimes.clear();
        generationTasks.clear();
    }
    
    /**
     * Get cache statistics
     */
    public String getCacheStats() {
        int totalChunks = seedCaches.values().stream()
                .mapToInt(Map::size)
                .sum();
        double chunkCacheMb = (totalChunks * (double) CHUNK_BYTES) / (1024.0 * 1024.0);
        
        return String.format("Seeds: %d, Chunks: %d (~%.1f MB), cap/seed=%d, Generating: %s",
                seedCaches.size(), totalChunks, chunkCacheMb, MAX_CHUNKS_PER_SEED, isGenerating);
    }

    private void putChunkWithEviction(long seed, Map<ChunkCoord, int[]> cache, ChunkCoord coord, int[] chunkData) {
        int[] existing = cache.putIfAbsent(coord, chunkData);
        if (existing == null) {
            touchChunkAccess(seed, coord);
            enforceSeedCacheBounds(seed, cache);
        } else {
            touchChunkAccess(seed, coord);
        }
    }

    private void touchChunkAccess(long seed, ChunkCoord coord) {
        Map<ChunkCoord, Long> accessTimes = seedAccessTimes.computeIfAbsent(seed, k -> new ConcurrentHashMap<>());
        accessTimes.put(coord, System.currentTimeMillis());
    }

    private void enforceSeedCacheBounds(long seed, Map<ChunkCoord, int[]> cache) {
        if (cache.size() <= MAX_CHUNKS_PER_SEED) {
            return;
        }

        Map<ChunkCoord, Long> accessTimes = seedAccessTimes.computeIfAbsent(seed, k -> new ConcurrentHashMap<>());
        synchronized (accessTimes) {
            if (cache.size() <= MAX_CHUNKS_PER_SEED) {
                return;
            }

            List<Map.Entry<ChunkCoord, Long>> entries = new ArrayList<>(accessTimes.entrySet());
            entries.sort(Map.Entry.comparingByValue());

            int idx = 0;
            while (cache.size() > TARGET_CHUNKS_PER_SEED && idx < entries.size()) {
                ChunkCoord evictCoord = entries.get(idx).getKey();
                cache.remove(evictCoord);
                accessTimes.remove(evictCoord);
                idx++;
            }
        }
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
    }
}
