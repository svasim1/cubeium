package cubeium.cubeium.world.test;

import cubeium.cubeium.Cubeium;
import cubeium.cubeium.world.CubiomesInterface;

/**
 * Comprehensive test suite for JNI bindings with basic biome generation calls.
 * Task 1.6: Test JNI bindings with basic biome generation calls
 */
public class BiomeGenerationTest {
    
    // Minecraft version constants
    private static final int MC_1_21 = 29; // Minecraft 1.21 version ID from cubiomes
    
    // Generator flags
    private static final long NORMAL_WORLD = 0x00;
    private static final long LARGE_BIOMES = 0x10;
    
    // Dimension constants
    private static final int DIM_OVERWORLD = 0;
    private static final int DIM_NETHER = -1;
    private static final int DIM_END = 1;
    
    // Common test seeds
    private static final long SEED_1 = 12345L;
    private static final long SEED_2 = 67890L;
    private static final long SEED_3 = -1234567890L;
    
    /**
     * Test 1: Basic Generator Lifecycle
     */
    public static boolean testGeneratorLifecycle() {
        Cubeium.LOGGER.info("=== Testing Generator Lifecycle ===");
        
        try {
            // Test generator creation
            long generator = CubiomesInterface.setupGenerator(MC_1_21, NORMAL_WORLD);
            if (generator == 0) {
                Cubeium.LOGGER.error("Failed to create generator");
                return false;
            }
            Cubeium.LOGGER.info("✓ Generator created successfully: {}", generator);
            
            // Test seed application
            CubiomesInterface.applySeed(generator, DIM_OVERWORLD, SEED_1);
            Cubeium.LOGGER.info("✓ Seed {} applied to generator", SEED_1);
            
            // Test generator cleanup
            CubiomesInterface.freeGenerator(generator);
            Cubeium.LOGGER.info("✓ Generator freed successfully");
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Generator lifecycle test failed", e);
            return false;
        }
    }
    
    /**
     * Test 2: Single Biome Queries
     */
    public static boolean testSingleBiomeQueries() {
        Cubeium.LOGGER.info("=== Testing Single Biome Queries ===");
        
        long generator = 0;
        try {
            // Setup generator
            generator = CubiomesInterface.setupGenerator(MC_1_21, NORMAL_WORLD);
            CubiomesInterface.applySeed(generator, DIM_OVERWORLD, SEED_1);
            
            // Test biome queries at various coordinates
            int[][] testCoords = {
                {0, 64, 0},     // Origin
                {100, 64, 100}, // Positive coordinates
                {-50, 64, -50}, // Negative coordinates
                {1000, 64, 1000}, // Far coordinates
            };
            
            for (int[] coord : testCoords) {
                int biome = CubiomesInterface.getBiomeAt(generator, 1, coord[0], coord[1], coord[2]);
                String biomeName = CubiomesInterface.getBiomeName(biome);
                Cubeium.LOGGER.info("✓ Biome at ({}, {}, {}): {} ({})", 
                    coord[0], coord[1], coord[2], biome, biomeName);
            }
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Single biome query test failed", e);
            return false;
        } finally {
            if (generator != 0) {
                CubiomesInterface.freeGenerator(generator);
            }
        }
    }
    
    /**
     * Test 3: Area Biome Generation
     */
    public static boolean testAreaBiomeGeneration() {
        Cubeium.LOGGER.info("=== Testing Area Biome Generation ===");
        
        long generator = 0;
        try {
            // Setup generator
            generator = CubiomesInterface.setupGenerator(MC_1_21, NORMAL_WORLD);
            CubiomesInterface.applySeed(generator, DIM_OVERWORLD, SEED_2);
            
            // Test small area generation (8x8)
            int scale = 1; // Block scale
            int x = 0, z = 0, y = 64;
            int width = 8, height = 8;
            
            int[] biomes = CubiomesInterface.genBiomes(generator, scale, x, z, y, width, height);
            
            if (biomes == null) {
                Cubeium.LOGGER.error("Failed to generate biome area");
                return false;
            }
            
            if (biomes.length != width * height) {
                Cubeium.LOGGER.error("Invalid biome array size. Expected: {}, Got: {}", 
                    width * height, biomes.length);
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Generated {}x{} biome area at ({}, {}, {})", 
                width, height, x, y, z);
            
            // Log the biome grid
            StringBuilder gridLog = new StringBuilder("\nBiome Grid:\n");
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    int biomeId = biomes[row * width + col];
                    gridLog.append(String.format("%3d ", biomeId));
                }
                gridLog.append("\n");
            }
            Cubeium.LOGGER.info(gridLog.toString());
            
            // Test larger area (32x32)
            width = 32;
            height = 32;
            biomes = CubiomesInterface.genBiomes(generator, scale, x, z, y, width, height);
            
            if (biomes == null || biomes.length != width * height) {
                Cubeium.LOGGER.error("Failed to generate large biome area");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Generated {}x{} large biome area", width, height);
            
            // Count unique biomes in large area
            java.util.Set<Integer> uniqueBiomes = new java.util.HashSet<>();
            for (int biome : biomes) {
                uniqueBiomes.add(biome);
            }
            Cubeium.LOGGER.info("✓ Found {} unique biomes in large area", uniqueBiomes.size());
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Area biome generation test failed", e);
            return false;
        } finally {
            if (generator != 0) {
                CubiomesInterface.freeGenerator(generator);
            }
        }
    }
    
    /**
     * Test 4: Different Scales and World Types
     */
    public static boolean testScalesAndWorldTypes() {
        Cubeium.LOGGER.info("=== Testing Different Scales and World Types ===");
        
        try {
            // Test different world types
            long[] worldTypes = {NORMAL_WORLD, LARGE_BIOMES};
            String[] worldTypeNames = {"Normal", "Large Biomes"};
            
            for (int i = 0; i < worldTypes.length; i++) {
                long generator = 0;
                try {
                    generator = CubiomesInterface.setupGenerator(MC_1_21, worldTypes[i]);
                    CubiomesInterface.applySeed(generator, DIM_OVERWORLD, SEED_1);
                    
                    // Test different scales
                    int[] scales = {1, 4, 16}; // Block, chunk, region scales
                    
                    for (int scale : scales) {
                        int biome = CubiomesInterface.getBiomeAt(generator, scale, 0, 64, 0);
                        String biomeName = CubiomesInterface.getBiomeName(biome);
                        Cubeium.LOGGER.info("✓ {} world, scale {}: {} ({})", 
                            worldTypeNames[i], scale, biome, biomeName);
                    }
                } finally {
                    if (generator != 0) {
                        CubiomesInterface.freeGenerator(generator);
                    }
                }
            }
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Scales and world types test failed", e);
            return false;
        }
    }
    
    /**
     * Test 5: Multiple Seeds Comparison
     */
    public static boolean testMultipleSeeds() {
        Cubeium.LOGGER.info("=== Testing Multiple Seeds ===");
        
        long generator = 0;
        try {
            generator = CubiomesInterface.setupGenerator(MC_1_21, NORMAL_WORLD);
            
            long[] testSeeds = {SEED_1, SEED_2, SEED_3};
            
            for (long seed : testSeeds) {
                CubiomesInterface.applySeed(generator, DIM_OVERWORLD, seed);
                
                // Test same coordinate with different seeds
                int biome = CubiomesInterface.getBiomeAt(generator, 1, 0, 64, 0);
                String biomeName = CubiomesInterface.getBiomeName(biome);
                
                Cubeium.LOGGER.info("✓ Seed {}: biome at origin = {} ({})", 
                    seed, biome, biomeName);
            }
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Multiple seeds test failed", e);
            return false;
        } finally {
            if (generator != 0) {
                CubiomesInterface.freeGenerator(generator);
            }
        }
    }
    
    /**
     * Test 6: 3D Biome Generation
     */
    public static boolean test3DBiomeGeneration() {
        Cubeium.LOGGER.info("=== Testing 3D Biome Generation ===");
        
        long generator = 0;
        try {
            generator = CubiomesInterface.setupGenerator(MC_1_21, NORMAL_WORLD);
            CubiomesInterface.applySeed(generator, DIM_OVERWORLD, SEED_1);
            
            // Test 3D generation (4x4x4)
            int scale = 1;
            int x = 0, z = 0, y = 0;
            int width = 4, height = 4, depth = 4;
            
            int[] biomes3D = CubiomesInterface.genBiomes3D(generator, scale, x, z, y, 
                                                          width, height, depth);
            
            if (biomes3D == null) {
                Cubeium.LOGGER.error("Failed to generate 3D biomes");
                return false;
            }
            
            int expectedSize = width * height * depth;
            if (biomes3D.length != expectedSize) {
                Cubeium.LOGGER.error("Invalid 3D biome array size. Expected: {}, Got: {}", 
                    expectedSize, biomes3D.length);
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Generated {}x{}x{} 3D biome volume", width, height, depth);
            
            // Count unique biomes in 3D volume
            java.util.Set<Integer> uniqueBiomes = new java.util.HashSet<>();
            for (int biome : biomes3D) {
                uniqueBiomes.add(biome);
            }
            Cubeium.LOGGER.info("✓ Found {} unique biomes in 3D volume", uniqueBiomes.size());
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("3D biome generation test failed", e);
            return false;
        } finally {
            if (generator != 0) {
                CubiomesInterface.freeGenerator(generator);
            }
        }
    }
    
    /**
     * Test 7: Performance and Stress Test
     */
    public static boolean testPerformance() {
        Cubeium.LOGGER.info("=== Testing Performance ===");
        
        long generator = 0;
        try {
            generator = CubiomesInterface.setupGenerator(MC_1_21, NORMAL_WORLD);
            CubiomesInterface.applySeed(generator, DIM_OVERWORLD, SEED_1);
            
            // Stress test: many single biome queries
            long startTime = System.currentTimeMillis();
            int queryCount = 1000;
            
            for (int i = 0; i < queryCount; i++) {
                int x = (int) (Math.random() * 2000 - 1000); // Random coordinates
                int z = (int) (Math.random() * 2000 - 1000);
                CubiomesInterface.getBiomeAt(generator, 1, x, 64, z);
            }
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            Cubeium.LOGGER.info("✓ Completed {} biome queries in {} ms ({} queries/sec)", 
                queryCount, duration, queryCount * 1000.0 / duration);
            
            // Performance test: large area generation
            startTime = System.currentTimeMillis();
            int[] biomes = CubiomesInterface.genBiomes(generator, 1, 0, 0, 64, 128, 128);
            endTime = System.currentTimeMillis();
            
            if (biomes == null) {
                Cubeium.LOGGER.error("Failed to generate large area for performance test");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Generated 128x128 area in {} ms ({} blocks/ms)", 
                endTime - startTime, biomes.length / (double)(endTime - startTime));
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Performance test failed", e);
            return false;
        } finally {
            if (generator != 0) {
                CubiomesInterface.freeGenerator(generator);
            }
        }
    }
    
    /**
     * Run all biome generation tests
     */
    public static void runAllTests() {
        Cubeium.LOGGER.info("Starting comprehensive JNI biome generation tests...");
        
        boolean[] results = {
            testGeneratorLifecycle(),
            testSingleBiomeQueries(),
            testAreaBiomeGeneration(),
            testScalesAndWorldTypes(),
            testMultipleSeeds(),
            test3DBiomeGeneration(),
            testPerformance()
        };
        
        String[] testNames = {
            "Generator Lifecycle",
            "Single Biome Queries",
            "Area Biome Generation", 
            "Scales and World Types",
            "Multiple Seeds",
            "3D Biome Generation",
            "Performance"
        };
        
        int passed = 0;
        int total = results.length;
        
        Cubeium.LOGGER.info("\n=== TEST RESULTS ===");
        for (int i = 0; i < results.length; i++) {
            String status = results[i] ? "PASS" : "FAIL";
            Cubeium.LOGGER.info("{}: {}", testNames[i], status);
            if (results[i]) passed++;
        }
        
        Cubeium.LOGGER.info("\nOverall: {}/{} tests passed", passed, total);
        
        if (passed == total) {
            Cubeium.LOGGER.info("🎉 ALL BIOME GENERATION TESTS PASSED! 🎉");
            Cubeium.LOGGER.info("Task 1.6: JNI biome generation testing - COMPLETE");
        } else {
            Cubeium.LOGGER.error("❌ Some tests failed. Review the logs above.");
        }
    }
}
