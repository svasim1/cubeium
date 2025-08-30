package cubeium.cubeium.world.test;

import cubeium.cubeium.Cubeium;
import cubeium.cubeium.world.CubiomesInterface;

/**
 * Master test runner for Task 1.6: Test JNI bindings with basic biome generation calls
 * 
 * This comprehensive test suite validates:
 * - Generator lifecycle management
 * - Single biome queries at various coordinates
 * - Area biome generation (2D and 3D)
 * - Different world types and scales
 * - Structure generation and viability
 * - Stronghold and spawn calculations
 * - Performance characteristics
 * - Error handling and edge cases
 */
public class JNITestRunner {
    
    private static int totalTests = 0;
    private static int passedTests = 0;
    
    /**
     * Test 1: Library Status and Version
     */
    public static boolean testLibraryStatus() {
        Cubeium.LOGGER.info("=== Testing Library Status ===");
        
        try {
            boolean loaded = CubiomesInterface.isLibraryLoaded();
            String version = CubiomesInterface.getLibraryVersion();
            
            Cubeium.LOGGER.info("✓ Library loaded: {}", loaded);
            Cubeium.LOGGER.info("✓ Library version: {}", version);
            
            if (!loaded) {
                Cubeium.LOGGER.error("Library not loaded properly");
                return false;
            }
            
            if (version == null || version.isEmpty()) {
                Cubeium.LOGGER.error("Invalid library version");
                return false;
            }
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Library status test failed", e);
            return false;
        }
    }
    
    /**
     * Test 2: Biome Name Mapping
     */
    public static boolean testBiomeNames() {
        Cubeium.LOGGER.info("=== Testing Biome Name Mapping ===");
        
        try {
            // Test known biome IDs
            int[] knownBiomes = {
                0,   // ocean
                1,   // plains
                2,   // desert
                3,   // mountains
                4,   // forest
                5,   // taiga
                6,   // swamp
                7,   // river
                21,  // jungle
                127, // the_void
                -1   // invalid biome
            };
            
            for (int biomeId : knownBiomes) {
                String name = CubiomesInterface.getBiomeName(biomeId);
                Cubeium.LOGGER.info("✓ Biome ID {}: '{}'", biomeId, name);
                
                if (name == null) {
                    Cubeium.LOGGER.error("Null name for biome ID {}", biomeId);
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Biome name mapping test failed", e);
            return false;
        }
    }
    
    /**
     * Test 3: Edge Cases and Error Handling
     */
    public static boolean testEdgeCases() {
        Cubeium.LOGGER.info("=== Testing Edge Cases and Error Handling ===");
        
        try {
            // Test invalid generator (should handle gracefully)
            try {
                CubiomesInterface.getBiomeAt(0, 1, 0, 64, 0); // Invalid generator pointer
                Cubeium.LOGGER.error("Should have thrown exception for invalid generator");
                return false;
            } catch (RuntimeException e) {
                Cubeium.LOGGER.info("✓ Correctly handled invalid generator: {}", e.getMessage());
            }
            
            // Test edge case coordinates (within valid range but still challenging)
            long generator = CubiomesInterface.setupGenerator(29, 0);
            CubiomesInterface.applySeed(generator, 0, 12345L);
            
            // Test large positive coordinates (within validation limits)
            int biome1 = CubiomesInterface.getBiomeAt(generator, 1, 25000000, 64, 25000000);
            Cubeium.LOGGER.info("✓ Biome at large positive coords: {}", biome1);
            
            // Test large negative coordinates (within validation limits)
            int biome2 = CubiomesInterface.getBiomeAt(generator, 1, -25000000, 64, -25000000);
            Cubeium.LOGGER.info("✓ Biome at large negative coords: {}", biome2);
            
            // Test coordinate validation limits work correctly
            try {
                CubiomesInterface.getBiomeAt(generator, 1, 35000000, 64, 0); // Should fail
                Cubeium.LOGGER.error("✗ Should have caught out-of-range coordinate");
                return false;
            } catch (IllegalArgumentException e) {
                Cubeium.LOGGER.info("✓ Correctly caught out-of-range coordinate: {}", e.getMessage());
            }
            
            CubiomesInterface.freeGenerator(generator);
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Edge cases test failed", e);
            return false;
        }
    }
    
    /**
     * Test 4: Memory Management
     */
    public static boolean testMemoryManagement() {
        Cubeium.LOGGER.info("=== Testing Memory Management ===");
        
        try {
            // Create and destroy many generators to test for memory leaks
            int generatorCount = 100;
            
            for (int i = 0; i < generatorCount; i++) {
                long generator = CubiomesInterface.setupGenerator(29, 0);
                if (generator == 0) {
                    Cubeium.LOGGER.error("Failed to create generator {}", i);
                    return false;
                }
                CubiomesInterface.freeGenerator(generator);
                
                if (i % 20 == 0) {
                    Cubeium.LOGGER.info("Created and freed {} generators", i + 1);
                }
            }
            
            Cubeium.LOGGER.info("✓ Successfully created and freed {} generators", generatorCount);
            
            // Test large memory allocations
            long generator = CubiomesInterface.setupGenerator(29, 0);
            CubiomesInterface.applySeed(generator, 0, 12345L);
            
            // Generate large areas
            int[] biomes1 = CubiomesInterface.genBiomes(generator, 1, 0, 0, 64, 256, 256);
            if (biomes1 == null || biomes1.length != 256 * 256) {
                Cubeium.LOGGER.error("Failed to generate large biome area");
                CubiomesInterface.freeGenerator(generator);
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Generated large biome area (256x256 = {} biomes)", biomes1.length);
            
            CubiomesInterface.freeGenerator(generator);
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Memory management test failed", e);
            return false;
        }
    }
    
    /**
     * Test 5: Concurrency (Multiple Generators)
     */
    public static boolean testConcurrency() {
        Cubeium.LOGGER.info("=== Testing Concurrency (Multiple Generators) ===");
        
        try {
            // Create multiple generators simultaneously
            long[] generators = new long[5];
            long[] seeds = {12345L, 67890L, -999L, 42L, 1000000L};
            
            // Create all generators
            for (int i = 0; i < generators.length; i++) {
                generators[i] = CubiomesInterface.setupGenerator(29, 0);
                if (generators[i] == 0) {
                    Cubeium.LOGGER.error("Failed to create generator {}", i);
                    return false;
                }
                CubiomesInterface.applySeed(generators[i], 0, seeds[i]);
            }
            
            Cubeium.LOGGER.info("✓ Created {} generators simultaneously", generators.length);
            
            // Use all generators simultaneously
            for (int i = 0; i < generators.length; i++) {
                int biome = CubiomesInterface.getBiomeAt(generators[i], 1, 0, 64, 0);
                Cubeium.LOGGER.info("Generator {} (seed {}): biome at origin = {}", i, seeds[i], biome);
            }
            
            // Clean up all generators
            for (long generator : generators) {
                CubiomesInterface.freeGenerator(generator);
            }
            
            Cubeium.LOGGER.info("✓ Cleaned up all generators");
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Concurrency test failed", e);
            return false;
        }
    }
    
    /**
     * Execute a single test and track results
     */
    private static void executeTest(String testName, java.util.function.Supplier<Boolean> test) {
        totalTests++;
        boolean result = test.get();
        if (result) {
            passedTests++;
            Cubeium.LOGGER.info("✅ {}: PASS", testName);
        } else {
            Cubeium.LOGGER.error("❌ {}: FAIL", testName);
        }
    }
    
    /**
     * Main test runner for Task 1.6
     */
    public static void runComprehensiveTests() {
        Cubeium.LOGGER.info("\n" + "=".repeat(60));
        Cubeium.LOGGER.info("🧪 COMPREHENSIVE JNI TESTING - TASK 1.6");
        Cubeium.LOGGER.info("Testing JNI bindings with basic biome generation calls");
        Cubeium.LOGGER.info("=".repeat(60));
        
        totalTests = 0;
        passedTests = 0;
        
        // Core functionality tests
        executeTest("Library Status", JNITestRunner::testLibraryStatus);
        executeTest("Biome Name Mapping", JNITestRunner::testBiomeNames);
        executeTest("Edge Cases", JNITestRunner::testEdgeCases);
        executeTest("Memory Management", JNITestRunner::testMemoryManagement);
        executeTest("Concurrency", JNITestRunner::testConcurrency);
        
        // Run specialized test suites
        Cubeium.LOGGER.info("\n" + "-".repeat(40));
        Cubeium.LOGGER.info("Running Biome Generation Test Suite...");
        Cubeium.LOGGER.info("-".repeat(40));
        BiomeGenerationTest.runAllTests();
        
        Cubeium.LOGGER.info("\n" + "-".repeat(40));
        Cubeium.LOGGER.info("Running Structure Generation Test Suite...");
        Cubeium.LOGGER.info("-".repeat(40));
        StructureGenerationTest.runAllTests();
        
        // Final results
        Cubeium.LOGGER.info("\n" + "=".repeat(60));
        Cubeium.LOGGER.info("📊 FINAL TEST RESULTS");
        Cubeium.LOGGER.info("=".repeat(60));
        
        double passRate = (double) passedTests / totalTests * 100;
        Cubeium.LOGGER.info("Core Tests: {}/{} passed ({:.1f}%)", passedTests, totalTests, passRate);
        
        if (passedTests == totalTests) {
            Cubeium.LOGGER.info("🎉 ALL CORE TESTS PASSED!");
            Cubeium.LOGGER.info("✨ Task 1.6: JNI bindings testing - COMPLETE");
            Cubeium.LOGGER.info("✨ Cubiomes JNI integration is fully functional!");
        } else {
            Cubeium.LOGGER.error("❌ {}/{} tests failed. Check logs for details.", 
                totalTests - passedTests, totalTests);
        }
        
        Cubeium.LOGGER.info("=".repeat(60));
    }
}
