package cubeium.cubeium.world;

import cubeium.cubeium.Cubeium;

/**
 * Simple test class to verify JNI integration works
 */
public class CubiomesTest {
    
    public static void testBasicFunctionality() {
        try {
            Cubeium.LOGGER.info("Testing cubiomes JNI integration...");
            
            // Test if library loads
            boolean loaded = CubiomesInterface.isLibraryLoaded();
            Cubeium.LOGGER.info("Library loaded: {}", loaded);
            
            // Test version info
            String version = CubiomesInterface.getLibraryVersion();
            Cubeium.LOGGER.info("Library version: {}", version);
            
            // Test biome name lookup
            String plainsBiome = CubiomesInterface.getBiomeName(1); // plains = 1
            Cubeium.LOGGER.info("Biome ID 1 name: {}", plainsBiome);
            
            String desertBiome = CubiomesInterface.getBiomeName(2); // desert = 2
            Cubeium.LOGGER.info("Biome ID 2 name: {}", desertBiome);
            
            // Test structure name lookup
            String villageStructure = CubiomesInterface.getStructureName(10); // Village = 10
            Cubeium.LOGGER.info("Structure ID 10 name: {}", villageStructure);
            
            // Test generator setup
            long generator = CubiomesInterface.setupGenerator(MC_1_21, LARGE_BIOMES);
            Cubeium.LOGGER.info("Generator created: {}", generator);
            
            if (generator != 0) {
                // Test applying seed
                CubiomesInterface.applySeed(generator, DIM_OVERWORLD, 12345L);
                Cubeium.LOGGER.info("Applied seed 12345 to generator");
                
                // Test getting single biome
                int biome = CubiomesInterface.getBiomeAt(generator, 1, 0, 64, 0);
                String biomeName = CubiomesInterface.getBiomeName(biome);
                Cubeium.LOGGER.info("Biome at (0, 64, 0): {} ({})", biome, biomeName);
                
                // Test getting spawn position
                int[] spawn = CubiomesInterface.getSpawn(generator, 12345L);
                if (spawn != null && spawn.length == 2) {
                    Cubeium.LOGGER.info("Spawn position: ({}, {})", spawn[0], spawn[1]);
                }
                
                // Cleanup
                CubiomesInterface.freeGenerator(generator);
                Cubeium.LOGGER.info("Generator freed");
            }
            
            Cubeium.LOGGER.info("Basic JNI test completed successfully!");
            
        } catch (Exception e) {
            Cubeium.LOGGER.error("JNI test failed: ", e);
        }
    }
    
    // Minecraft version constants from cubiomes
    private static final int MC_1_21 = 29;
    
    // Generator flags
    private static final long LARGE_BIOMES = 0x10;
    
    // Dimension constants
    private static final int DIM_OVERWORLD = 0;
}
