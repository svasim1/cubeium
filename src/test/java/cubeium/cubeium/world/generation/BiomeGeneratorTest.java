package cubeium.cubeium.world.generation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import cubeium.cubeium.world.CubiomesInterface;

class BiomeGeneratorTest {
    private final BiomeGenerator generator = new BiomeGenerator();

    @AfterEach
    void cleanup() {
        generator.cleanup();
    }

    @Test
    void pointSamplesMatchAreaCellsIncludingNegativeCoordinates() {
        long seed = 123456789L;
        int x0 = -256, z0 = -128, size = 128;
        int[] cells = generator.generateCells(seed, x0, z0, size, size);
        int cellsWide = size / BiomeGenerator.CELL_SIZE;

        for (int z = z0; z < z0 + size; z += 7) {
            for (int x = x0; x < x0 + size; x += 5) {
                int cell = cells[((z - z0) / 4) * cellsWide + (x - x0) / 4];
                assertEquals(cell, generator.getBiomeAt(seed, x, z), "block " + x + ", " + z);
            }
        }
    }

    @Test
    void pointSamplesUseBlockCoordinates() {
        // Regression: block coordinates used to be passed as biome-cell coordinates (4x off).
        long seed = 42L;
        long handle = CubiomesInterface.setupGenerator(CubiomesInterface.GAME_MC_VERSION, 0);
        try {
            CubiomesInterface.applySeed(handle, CubiomesInterface.DIM_OVERWORLD, seed);
            int y = BiomeGenerator.MAP_BIOME_Y;
            for (int x = -3001; x <= 3000; x += 250) {
                // The 1:4 cell containing block (x, 1000).
                int expected = CubiomesInterface.getBiomeAt(handle, 4, Math.floorDiv(x, 4), y / 4, 1000 / 4);
                assertEquals(expected, generator.getBiomeAt(seed, x, 1000), "block x=" + x);
            }
        } finally {
            CubiomesInterface.freeGenerator(handle);
        }
    }

    @Test
    void interleavedSeedsDoNotMix() throws Exception {
        long seedA = 1L, seedB = 2L;
        int[] a = generator.generateCells(seedA, 0, 0, 256, 256);
        int[] b = generator.generateCells(seedB, 0, 0, 256, 256);
        assertFalse(java.util.Arrays.equals(a, b), "different seeds should differ");

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<int[]>> results = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                long seed = (i % 2 == 0) ? seedA : seedB;
                results.add(pool.submit(() -> generator.generateCells(seed, 0, 0, 256, 256)));
            }
            for (int i = 0; i < results.size(); i++) {
                assertArrayEquals(i % 2 == 0 ? a : b, results.get(i).get(), "task " + i);
            }
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void namesAndColors() {
        assertEquals("Windswept Hills", generator.getBiomeName(3));
        assertEquals(0xFF, BiomeGenerator.getBiomeColor(14) >>> 24, "mushroom fields has a color");
        assertEquals(0xFF000000, BiomeGenerator.getBiomeColor(-1));
    }

    @Test
    void rejectsUnalignedAreas() {
        assertThrows(IllegalArgumentException.class, () -> generator.generateCells(1L, 1, 0, 64, 64));
    }
}
