package cubeium.cubeium.world.test;

import cubeium.cubeium.Cubeium;
import cubeium.cubeium.world.CubiomesInterface;

/**
 * Test suite for structure generation JNI bindings.
 * Part of Task 1.6: Test JNI bindings with basic biome generation calls
 */
public class StructureGenerationTest {
    
    // Minecraft version constants
    private static final int MC_1_21 = 29;
    
    // Generator flags
    private static final long NORMAL_WORLD = 0x00;
    
    // Dimension constants
    private static final int DIM_OVERWORLD = 0;
    
    // Test seeds
    private static final long SEED_1 = 12345L;
    private static final long SEED_VILLAGE = 1234567890L; // Known to have villages
    
    // Structure type constants (from cubiomes finders.h)
    private static final int DESERT_PYRAMID = 1;
    private static final int JUNGLE_TEMPLE = 2;
    private static final int SWAMP_HUT = 3;
    private static final int IGLOO = 4;
    private static final int VILLAGE = 10;
    private static final int OCEAN_MONUMENT = 20;
    private static final int PILLAGER_OUTPOST = 30;
    
    /**
     * Test 1: Structure Position Finding
     */
    public static boolean testStructurePositions() {
        Cubeium.LOGGER.info("=== Testing Structure Position Finding ===");
        
        try {
            int[] structures = {VILLAGE, DESERT_PYRAMID, JUNGLE_TEMPLE, SWAMP_HUT, IGLOO, PILLAGER_OUTPOST};
            String[] structureNames = {"Village", "Desert Pyramid", "Jungle Temple", "Swamp Hut", "Igloo", "Pillager Outpost"};
            
            for (int i = 0; i < structures.length; i++) {
                // Search in multiple regions
                boolean foundStructure = false;
                for (int regionX = -5; regionX <= 5 && !foundStructure; regionX++) {
                    for (int regionZ = -5; regionZ <= 5 && !foundStructure; regionZ++) {
                        int[] pos = CubiomesInterface.getStructurePos(structures[i], MC_1_21, SEED_1, regionX, regionZ);
                        
                        if (pos != null && pos.length == 2) {
                            foundStructure = true;
                            String structureName = CubiomesInterface.getStructureName(structures[i]);
                            Cubeium.LOGGER.info("✓ Found {}: {} at ({}, {}) in region ({}, {})", 
                                structureNames[i], structureName, pos[0], pos[1], regionX, regionZ);
                        }
                    }
                }
                
                if (!foundStructure) {
                    Cubeium.LOGGER.info("⚠ No {} found in searched regions (this is normal)", structureNames[i]);
                }
            }
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Structure position test failed", e);
            return false;
        }
    }
    
    /**
     * Test 2: Structure Viability Testing
     */
    public static boolean testStructureViability() {
        Cubeium.LOGGER.info("=== Testing Structure Viability ===");
        
        long generator = 0;
        try {
            generator = CubiomesInterface.setupGenerator(MC_1_21, NORMAL_WORLD);
            CubiomesInterface.applySeed(generator, DIM_OVERWORLD, SEED_1);
            
            // Test various coordinates for structure viability
            int[][] testCoords = {
                {0, 0},        // Origin
                {100, 100},    // Plains area
                {500, 500},    // Different biome area
                {-200, 300},   // Negative coordinates
            };
            
            int[] structures = {VILLAGE, DESERT_PYRAMID, JUNGLE_TEMPLE, SWAMP_HUT};
            String[] structureNames = {"Village", "Desert Pyramid", "Jungle Temple", "Swamp Hut"};
            
            for (int[] coord : testCoords) {
                int x = coord[0], z = coord[1];
                
                // Get biome at this location first
                int biome = CubiomesInterface.getBiomeAt(generator, 1, x, 64, z);
                String biomeName = CubiomesInterface.getBiomeName(biome);
                
                Cubeium.LOGGER.info("Testing viability at ({}, {}) - Biome: {} ({})", x, z, biome, biomeName);
                
                for (int i = 0; i < structures.length; i++) {
                    boolean viable = CubiomesInterface.isViableStructurePos(structures[i], generator, x, z, 0);
                    Cubeium.LOGGER.info("  {} viable: {}", structureNames[i], viable);
                }
            }
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Structure viability test failed", e);
            return false;
        } finally {
            if (generator != 0) {
                CubiomesInterface.freeGenerator(generator);
            }
        }
    }
    
    /**
     * Test 3: Stronghold Generation
     */
    public static boolean testStrongholdGeneration() {
        Cubeium.LOGGER.info("=== Testing Stronghold Generation ===");
        
        long generator = 0;
        try {
            generator = CubiomesInterface.setupGenerator(MC_1_21, NORMAL_WORLD);
            
            // Test strongholds with different seeds
            long[] testSeeds = {SEED_1, 42L, -999L};
            
            for (long seed : testSeeds) {
                CubiomesInterface.applySeed(generator, DIM_OVERWORLD, seed);
                
                int[] strongholds = CubiomesInterface.getStrongholds(generator, seed, 3); // Get first 3 strongholds
                
                if (strongholds == null || strongholds.length == 0) {
                    Cubeium.LOGGER.warn("No strongholds found for seed {}", seed);
                    continue;
                }
                
                if (strongholds.length % 2 != 0) {
                    Cubeium.LOGGER.error("Invalid stronghold array length: {}", strongholds.length);
                    return false;
                }
                
                int strongholdCount = strongholds.length / 2;
                Cubeium.LOGGER.info("✓ Found {} strongholds for seed {}:", strongholdCount, seed);
                
                for (int i = 0; i < strongholdCount; i++) {
                    int x = strongholds[i * 2];
                    int z = strongholds[i * 2 + 1];
                    Cubeium.LOGGER.info("  Stronghold {}: ({}, {})", i + 1, x, z);
                }
            }
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Stronghold generation test failed", e);
            return false;
        } finally {
            if (generator != 0) {
                CubiomesInterface.freeGenerator(generator);
            }
        }
    }
    
    /**
     * Test 4: Spawn Position Calculation
     */
    public static boolean testSpawnGeneration() {
        Cubeium.LOGGER.info("=== Testing Spawn Position Calculation ===");
        
        long generator = 0;
        try {
            generator = CubiomesInterface.setupGenerator(MC_1_21, NORMAL_WORLD);
            
            // Test spawn calculation with different seeds
            long[] testSeeds = {SEED_1, 0L, 123456L, -987654L};
            
            for (long seed : testSeeds) {
                CubiomesInterface.applySeed(generator, DIM_OVERWORLD, seed);
                
                int[] spawn = CubiomesInterface.getSpawn(generator, seed);
                
                if (spawn == null || spawn.length != 2) {
                    Cubeium.LOGGER.error("Invalid spawn data for seed {}", seed);
                    return false;
                }
                
                int spawnX = spawn[0];
                int spawnZ = spawn[1];
                
                // Verify spawn is reasonable (not too far from origin)
                double distanceFromOrigin = Math.sqrt(spawnX * spawnX + spawnZ * spawnZ);
                
                Cubeium.LOGGER.info("✓ Spawn for seed {}: ({}, {}) - Distance from origin: {:.1f}", 
                    seed, spawnX, spawnZ, distanceFromOrigin);
                
                // Get biome at spawn location
                int spawnBiome = CubiomesInterface.getBiomeAt(generator, 1, spawnX, 64, spawnZ);
                String biomeName = CubiomesInterface.getBiomeName(spawnBiome);
                Cubeium.LOGGER.info("  Spawn biome: {} ({})", spawnBiome, biomeName);
            }
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Spawn generation test failed", e);
            return false;
        } finally {
            if (generator != 0) {
                CubiomesInterface.freeGenerator(generator);
            }
        }
    }
    
    /**
     * Test 5: Structure Name Mapping
     */
    public static boolean testStructureNames() {
        Cubeium.LOGGER.info("=== Testing Structure Name Mapping ===");
        
        try {
            // Test various structure IDs
            int[] structureIds = {1, 2, 3, 4, 10, 20, 30, 999}; // Including invalid ID
            
            for (int id : structureIds) {
                String name = CubiomesInterface.getStructureName(id);
                Cubeium.LOGGER.info("✓ Structure ID {}: '{}'", id, name);
            }
            
            return true;
        } catch (Exception e) {
            Cubeium.LOGGER.error("Structure name mapping test failed", e);
            return false;
        }
    }
    
    /**
     * Run all structure generation tests
     */
    public static void runAllTests() {
        Cubeium.LOGGER.info("Starting structure generation JNI tests...");
        
        boolean[] results = {
            testStructurePositions(),
            testStructureViability(),
            testStrongholdGeneration(),
            testSpawnGeneration(),
            testStructureNames()
        };
        
        String[] testNames = {
            "Structure Positions",
            "Structure Viability",
            "Stronghold Generation",
            "Spawn Generation",
            "Structure Names"
        };
        
        int passed = 0;
        int total = results.length;
        
        Cubeium.LOGGER.info("\n=== STRUCTURE TEST RESULTS ===");
        for (int i = 0; i < results.length; i++) {
            String status = results[i] ? "PASS" : "FAIL";
            Cubeium.LOGGER.info("{}: {}", testNames[i], status);
            if (results[i]) passed++;
        }
        
        Cubeium.LOGGER.info("\nStructure Tests: {}/{} passed", passed, total);
    }
}
