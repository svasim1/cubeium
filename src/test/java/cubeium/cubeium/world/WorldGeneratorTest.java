package cubeium.cubeium.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

import dev.xpple.cubiomes.Cubiomes;

/**
 * Runs the cubiomes bindings natively. Ground-truth values come from cubiomes' own tests
 * (xpple/cubiomes tests/), which were taken from real Minecraft worlds.
 */
class WorldGeneratorTest {
    private static final int MC = Cubiomes.MC_26_3();

    private static String biome(WorldGenerator world, int x, int y, int z) {
        return Biomes.name(world.mcVersion(), world.biomeAt(x, y, z));
    }

    @Test
    void biomesMatchRealWorlds() {
        WorldGenerator world = new WorldGenerator(MC, -3829811542736183482L, Dimension.OVERWORLD);
        assertEquals("dappled_forest", biome(world, 68148, 77, 80990));

        WorldGenerator old = new WorldGenerator(Cubiomes.MC_1_16_5(), 1437905338718953247L, Dimension.OVERWORLD);
        // 1.16 biomes are 2D, so the surface lookup samples the same 1:4 cell as cubiomes' test.
        assertEquals("mushroom_fields", Biomes.name(old.mcVersion(), old.biomeAt(68, 47)));
    }

    @Test
    void areasAreRowMajorAndMatchPointSamples() {
        WorldGenerator world = new WorldGenerator(MC, 12345L, Dimension.OVERWORLD);
        int x = -40, z = 25, w = 23, h = 17;
        int[] cells = world.biomes(4, x, z, w, h);
        assertEquals(w * h, cells.length);
        for (int dz = 0; dz < h; dz++) {
            for (int dx = 0; dx < w; dx++) {
                int blockX = (x + dx) * 4, blockZ = (z + dz) * 4;
                assertEquals(world.biomeAt(blockX, blockZ), cells[dz * w + dx], "cell " + dx + "," + dz);
            }
        }
    }

    @Test
    void coarseScalesWork() {
        WorldGenerator world = new WorldGenerator(MC, 42L, Dimension.OVERWORLD);
        for (int scale : new int[] {16, 64, 256}) {
            int[] cells = world.biomes(scale, -8, -8, 16, 16);
            assertEquals(256, cells.length);
            for (int id : cells) {
                assertNotNull(Biomes.name(MC, id), "scale " + scale + " id " + id);
            }
        }
    }

    @Test
    void netherAndEndUseTheirBiomes() {
        List<Integer> nether = Biomes.mapBiomes(MC, Dimension.NETHER);
        WorldGenerator world = new WorldGenerator(MC, 1551515151585454L, Dimension.NETHER);
        assertEquals("crimson_forest", Biomes.name(MC, world.biomeAt(181, 209)));
        for (int id : world.biomes(16, -32, -32, 64, 64)) {
            assertTrue(nether.contains(id), "unexpected nether biome " + Biomes.name(MC, id));
        }
        List<Integer> end = Biomes.mapBiomes(MC, Dimension.END);
        WorldGenerator endWorld = new WorldGenerator(MC, 1551515151585454L, Dimension.END);
        assertTrue(end.contains(endWorld.biomeAt(10000, 10000)));
    }

    @Test
    void biomeListsFollowVersionAndSkipCaves() {
        List<Integer> overworld = Biomes.mapBiomes(MC, Dimension.OVERWORLD);
        Set<String> names = new HashSet<>();
        overworld.forEach(id -> names.add(Biomes.name(MC, id)));
        assertTrue(names.contains("dappled_forest"), "added in 26.3");
        assertTrue(names.contains("pale_garden"));
        assertTrue(names.contains("savanna_plateau"));
        assertFalse(names.contains("lush_caves"));
        assertFalse(names.contains("deep_dark"));
        assertFalse(Biomes.mapBiomes(Cubiomes.MC_1_21_1(), Dimension.OVERWORLD).stream()
                .map(id -> Biomes.name(Cubiomes.MC_1_21_1(), id)).anyMatch("pale_garden"::equals));
        assertEquals(0xFF, Biomes.color(overworld.getFirst()) >>> 24);
    }

    @Test
    void strongholdsAreCompleteAndDistinct() {
        WorldGenerator world = new WorldGenerator(MC, 42L, Dimension.OVERWORLD);
        int[] positions = world.strongholds(128);
        assertEquals(256, positions.length);
        Set<Long> unique = new HashSet<>();
        for (int i = 0; i < positions.length; i += 2) {
            unique.add(((long) positions[i] << 32) | (positions[i + 1] & 0xFFFFFFFFL));
        }
        assertEquals(128, unique.size());
        // The first ring holds 3 strongholds 1280-2816 blocks out (+-112 biome snap, +8 chunk centre).
        for (int i = 0; i < 3; i++) {
            double dist = Math.hypot(positions[2 * i], positions[2 * i + 1]);
            assertTrue(dist > 1160 && dist < 2936, "stronghold " + i + " at " + dist);
        }
    }

    @Test
    void structuresLieInTheirRegionAndNeedBiomes() {
        StructureFinder finder = new StructureFinder(new WorldGenerator(MC, 42L, Dimension.OVERWORLD));
        assertEquals(34 * 16, finder.regionBlocks(StructureKind.VILLAGE));
        List<StructureFinder.Found> villages = finder.find(StructureKind.VILLAGE, -8000, -8000, 8000, 8000);
        int regionsInArea = (int) Math.pow(Math.ceil(16000.0 / (34 * 16)), 2);
        assertFalse(villages.isEmpty());
        assertTrue(villages.size() < regionsInArea, "biome checks must reject some attempts");
        for (StructureFinder.Found v : villages) {
            assertTrue(Math.abs(v.x()) <= 8000 && Math.abs(v.z()) <= 8000);
        }
        // Cached: a second query returns the same result.
        assertEquals(villages, finder.find(StructureKind.VILLAGE, -8000, -8000, 8000, 8000));
    }

    @Test
    void everyStructureKindIsSupportedIn26_3() {
        for (Dimension dimension : Dimension.values()) {
            StructureFinder finder = new StructureFinder(new WorldGenerator(MC, 7L, dimension));
            for (StructureKind kind : StructureKind.in(dimension)) {
                assertTrue(finder.supports(kind), kind + " should exist in 26.3");
                // Buried treasure is rolled at ~1% per chunk and needs a beach, so search wider.
                int radius = kind == StructureKind.BURIED_TREASURE ? 3000 : kind.isDense() ? 256 : 20_000;
                if (kind != StructureKind.STRONGHOLD) {
                    assertFalse(finder.find(kind, -radius, -radius, radius, radius).isEmpty(), "no " + kind + " found");
                }
            }
        }
        StructureFinder old = new StructureFinder(new WorldGenerator(Cubiomes.MC_1_20(), 7L, Dimension.OVERWORLD));
        assertFalse(old.supports(StructureKind.ABANDONED_CAMP), "abandoned camps were added in 26.3");
    }

    @Test
    void endCitiesSplitByShip() {
        StructureFinder finder = new StructureFinder(new WorldGenerator(MC, 7L, Dimension.END));
        List<StructureFinder.Found> plain = finder.find(StructureKind.END_CITY, -20_000, -20_000, 20_000, 20_000);
        List<StructureFinder.Found> ships = finder.find(StructureKind.END_CITY_SHIP, -20_000, -20_000, 20_000, 20_000);
        assertFalse(plain.isEmpty());
        assertFalse(ships.isEmpty());
        Set<Long> plainPositions = new HashSet<>();
        plain.forEach(f -> plainPositions.add(((long) f.x() << 32) | (f.z() & 0xFFFFFFFFL)));
        for (StructureFinder.Found ship : ships) {
            assertFalse(plainPositions.contains(((long) ship.x() << 32) | (ship.z() & 0xFFFFFFFFL)), "a city is either with or without a ship");
        }
    }

    @Test
    void generatorsAreSafeAcrossThreads() throws Exception {
        WorldGenerator a = new WorldGenerator(MC, 1L, Dimension.OVERWORLD);
        WorldGenerator b = new WorldGenerator(MC, 2L, Dimension.OVERWORLD);
        int[] expectedA = a.biomes(4, 0, 0, 64, 64), expectedB = b.biomes(4, 0, 0, 64, 64);
        assertFalse(java.util.Arrays.equals(expectedA, expectedB));

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<int[]>> results = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                WorldGenerator world = i % 2 == 0 ? a : b;
                results.add(pool.submit(() -> world.biomes(4, 0, 0, 64, 64)));
            }
            for (int i = 0; i < results.size(); i++) {
                assertArrayEquals(i % 2 == 0 ? expectedA : expectedB, results.get(i).get(), "task " + i);
            }
        } finally {
            pool.shutdown();
        }
    }
}
