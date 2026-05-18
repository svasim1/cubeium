package cubeium.cubeium.world.generation;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cubeium.cubeium.util.RenderMetrics;
import cubeium.cubeium.world.CubiomesInterface;

/**
 * Manages biome generation using the cubiomes library integration.
 * Provides efficient biome data generation with caching and async processing.
 */
public class BiomeGenerator {
    private static final int DEFAULT_MC_VERSION = CubiomesInterface.MC_1_21;
    private static final int CACHE_LIMIT = 10000; // Maximum cached regions
    
    // Generator state
    private long generatorHandle = 0;
    private int mcVersion;
    private long worldFlags;
    private long currentSeed = Long.MIN_VALUE;
    private int dimension = CubiomesInterface.DIM_OVERWORLD;
    
    // Threading and caching
    private final ExecutorService executor;
    private final Map<RegionKey, BiomeRegion> biomeCache;
    private final Object generatorLock = new Object();
    
    // Performance tracking
    private long totalGenerations = 0;
    private long totalGenerationTime = 0;
    private long cacheHits = 0;
    private long cacheMisses = 0;
    
    /**
     * Create a new BiomeGenerator with default settings
     */
    public BiomeGenerator() {
        this(DEFAULT_MC_VERSION, 0L);
    }
    
    /**
     * Create a BiomeGenerator for specific version and flags
     * @param mcVersion Minecraft version constant
     * @param worldFlags World generation flags (e.g., LARGE_BIOMES)
     */
    public BiomeGenerator(int mcVersion, long worldFlags) {
        this.mcVersion = mcVersion;
        this.worldFlags = worldFlags;
        this.executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2));
        this.biomeCache = new ConcurrentHashMap<>();
        
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
                throw new RuntimeException("Failed to create BiomeGenerator: " + e.getMessage(), e);
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
                // Debug logging for seed changes
                System.out.println(String.format("[BiomeGenerator] setSeed called: seed=%d, dimension=%d", seed, dimension));
                
                this.currentSeed = seed;
                this.dimension = dimension;
                
                // Clear cache when seed changes
                biomeCache.clear();
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
     * Get biome at a specific block coordinate
     * @param x Block X coordinate
     * @param y Block Y coordinate  
     * @param z Block Z coordinate
     * @return Biome ID from cubiomes
     */
    public int getBiomeAt(int x, int y, int z) {
        checkGeneratorReady();
        
        synchronized (generatorLock) {
            try {
                // Use scale = 4 for biome coordinates (standard for most biome maps)
                // This matches what most cubiomes-based websites use
                long jniStart = System.nanoTime();
                int biomeId = CubiomesInterface.getBiomeAt(generatorHandle, 4, x, y, z);
                RenderMetrics.get().recordJniCallNanos(System.nanoTime() - jniStart);
                
                // Debug logging for specific coordinates that we see in UI logs
                if ((x >= -5 && x <= 5) && (z >= -5 && z <= 5)) {
                    //System.out.println(String.format("[BiomeGenerator] getBiomeAt: seed=%d, x=%d, y=%d, z=%d, biome=%d", currentSeed, x, y, z, biomeId));
                }
                
                return biomeId;
            } catch (Exception e) {
                throw new RuntimeException("Failed to get biome at (" + x + ", " + y + ", " + z + "): " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Get biome at a specific coordinate (Y=64 default)
     * @param x Block X coordinate
     * @param z Block Z coordinate
     * @return Biome ID from cubiomes
     */
    public int getBiomeAt(int x, int z) {
        return getBiomeAt(x, 64, z);
    }
    
    /**
     * Generate biomes for a rectangular area
     * @param startX Starting X coordinate (block coordinates)
     * @param startZ Starting Z coordinate (block coordinates)
     * @param width Width in blocks
     * @param height Height in blocks
     * @param scale Scale factor (1=block, 4=biome, 16=chunk)
     * @return BiomeRegion containing the generated data
     */
    public BiomeRegion generateBiomes(int startX, int startZ, int width, int height, int scale) {
        checkGeneratorReady();
        
        // Convert to appropriate coordinate system
        int scaledX = startX / scale;
        int scaledZ = startZ / scale;
        int scaledWidth = width / scale;
        int scaledHeight = height / scale;
        
        // Check cache first
        RegionKey key = new RegionKey(scaledX, scaledZ, scaledWidth, scaledHeight, scale, dimension);
        BiomeRegion cached = biomeCache.get(key);
        if (cached != null && cached.seed == currentSeed) {
            cacheHits++;
            return cached;
        }
        
        // Generate new data
        cacheMisses++;
        long startTime = System.nanoTime();
        
        int[] biomes;
        synchronized (generatorLock) {
            try {
                long jniStart = System.nanoTime();
                biomes = CubiomesInterface.genBiomes(generatorHandle, scale, scaledX, scaledZ, 64, scaledWidth, scaledHeight);
                RenderMetrics.get().recordJniCallNanos(System.nanoTime() - jniStart);
            } catch (Exception e) {
                throw new RuntimeException("Failed to generate biomes for region: " + e.getMessage(), e);
            }
        }
        
        long endTime = System.nanoTime();
        totalGenerations++;
        totalGenerationTime += (endTime - startTime);
        
        // Create and cache result
        BiomeRegion region = new BiomeRegion(scaledX, scaledZ, scaledWidth, scaledHeight, scale, biomes, currentSeed);
        
        // Manage cache size
        if (biomeCache.size() >= CACHE_LIMIT) {
            // Remove oldest entries (simple LRU approximation)
            biomeCache.entrySet().removeIf(entry -> Math.random() < 0.1);
        }
        
        biomeCache.put(key, region);
        return region;
    }
    
    /**
     * Generate biomes for a chunk area (16x16 blocks)
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return BiomeRegion for the chunk
     */
    public BiomeRegion generateChunkBiomes(int chunkX, int chunkZ) {
        return generateBiomes(chunkX * 16, chunkZ * 16, 16, 16, 4);
    }
    
    /**
     * Generate biomes asynchronously
     * @param startX Starting X coordinate
     * @param startZ Starting Z coordinate
     * @param width Width in blocks
     * @param height Height in blocks
     * @param scale Scale factor
     * @return CompletableFuture with BiomeRegion
     */
    public CompletableFuture<BiomeRegion> generateBiomesAsync(int startX, int startZ, int width, int height, int scale) {
        return CompletableFuture.supplyAsync(() -> generateBiomes(startX, startZ, width, height, scale), executor);
    }
    
    /**
     * Get biome name for display
     * @param biomeId Biome ID from cubiomes
     * @return Human-readable biome name
     */
    public String getBiomeName(int biomeId) {
        try {
            return normalizeBiomeName(CubiomesInterface.getBiomeName(biomeId));
        } catch (Exception e) {
            return "Unknown Biome (" + biomeId + ")";
        }
    }

    private String normalizeBiomeName(String rawBiomeName) {
        if (rawBiomeName == null || rawBiomeName.isBlank()) {
            return "Unknown Biome";
        }

        String normalized = rawBiomeName.trim();

        int namespaceSeparator = normalized.lastIndexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator < normalized.length() - 1) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }

        normalized = normalized.replace('_', ' ').replace('-', ' ');
        String[] words = normalized.split("\\s+");
        StringBuilder titleCase = new StringBuilder(normalized.length());

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (!titleCase.isEmpty()) {
                titleCase.append(' ');
            }

            if (word.length() == 1) {
                titleCase.append(Character.toUpperCase(word.charAt(0)));
            } else {
                titleCase.append(Character.toUpperCase(word.charAt(0)));
                titleCase.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }

        return titleCase.isEmpty() ? "Unknown Biome" : titleCase.toString();
    }
    
    /**
     * Check if generator is ready for use
     */
    private void checkGeneratorReady() {
        if (generatorHandle == 0) {
            throw new IllegalStateException("BiomeGenerator not initialized");
        }
        if (currentSeed == Long.MIN_VALUE) {
            throw new IllegalStateException("World seed not set");
        }
    }
    
    /**
     * Get performance statistics
     * @return GenerationStats with performance data
     */
    public GenerationStats getStats() {
        double avgGenerationTime = totalGenerations > 0 ? (double) totalGenerationTime / totalGenerations / 1_000_000.0 : 0.0;
        double cacheHitRate = (cacheHits + cacheMisses) > 0 ? (double) cacheHits / (cacheHits + cacheMisses) : 0.0;
        
        return new GenerationStats(
            totalGenerations,
            avgGenerationTime,
            cacheHitRate,
            biomeCache.size(),
            currentSeed,
            dimension
        );
    }
    
    /**
     * Clear all cached data
     */
    public void clearCache() {
        biomeCache.clear();
        cacheHits = 0;
        cacheMisses = 0;
    }
    
    /**
     * Clean up resources
     */
    public void cleanup() {
        executor.shutdown();
        biomeCache.clear();
        
        synchronized (generatorLock) {
            if (generatorHandle != 0) {
                CubiomesInterface.freeGenerator(generatorHandle);
                generatorHandle = 0;
            }
        }
    }
    
    /**
     * Cache key for biome regions
     */
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
    
    /**
     * Container for generated biome data
     */
    public static class BiomeRegion {
        public final int x, z, width, height, scale;
        public final int[] biomes;
        public final long seed;
        public final long generationTime;
        
        BiomeRegion(int x, int z, int width, int height, int scale, int[] biomes, long seed) {
            this.x = x;
            this.z = z;
            this.width = width;
            this.height = height;
            this.scale = scale;
            this.biomes = biomes;
            this.seed = seed;
            this.generationTime = System.currentTimeMillis();
        }
        
        /**
         * Get biome at local coordinates within this region
         * @param localX Local X coordinate (0 to width-1)
         * @param localZ Local Z coordinate (0 to height-1)
         * @return Biome ID
         */
        public int getBiome(int localX, int localZ) {
            if (localX < 0 || localX >= width || localZ < 0 || localZ >= height) {
                throw new IndexOutOfBoundsException("Coordinates out of bounds: (" + localX + ", " + localZ + ")");
            }
            return biomes[localZ * width + localX];
        }
        
        /**
         * Get global X coordinate for a local position
         */
        public int getGlobalX(int localX) {
            return (x + localX) * scale;
        }
        
        /**
         * Get global Z coordinate for a local position  
         */
        public int getGlobalZ(int localZ) {
            return (z + localZ) * scale;
        }
    }
    
    /**
     * Performance statistics
     */
    public static class GenerationStats {
        public final long totalGenerations;
        public final double averageGenerationTimeMs;
        public final double cacheHitRate;
        public final int cacheSize;
        public final long currentSeed;
        public final int dimension;
        
        GenerationStats(long totalGenerations, double averageGenerationTimeMs, double cacheHitRate, 
                       int cacheSize, long currentSeed, int dimension) {
            this.totalGenerations = totalGenerations;
            this.averageGenerationTimeMs = averageGenerationTimeMs;
            this.cacheHitRate = cacheHitRate;
            this.cacheSize = cacheSize;
            this.currentSeed = currentSeed;
            this.dimension = dimension;
        }
        
        @Override
        public String toString() {
            return String.format("BiomeGenerator Stats: %d generations, %.2fms avg, %.1f%% cache hit rate, %d cached regions",
                totalGenerations, averageGenerationTimeMs, cacheHitRate * 100, cacheSize);
        }
    }
}
