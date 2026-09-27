package cubeium.cubeium.world;

import static cubeium.cubeium.world.CubiomesInterface.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the JNI bridge against the real native library. Ground-truth values come from
 * cubiomes' own tests (native/cubiomes/tests), which were taken from real Minecraft worlds.
 */
class CubiomesInterfaceTest {
    private long generator;

    private long generator(int mc, int dim, long seed) {
        generator = setupGenerator(mc, 0);
        applySeed(generator, dim, seed);
        return generator;
    }

    @AfterEach
    void free() {
        freeGenerator(generator);
        generator = 0;
    }

    private static String name(int mc, int biomeId) {
        return getBiomeName(mc, biomeId);
    }

    @Test
    void biomesMatchRealWorlds_1_16_5() {
        long g = generator(MC_1_16, DIM_OVERWORLD, 1437905338718953247L);
        assertEquals("wooded_badlands_plateau", name(MC_1_16, getBiomeAt(g, 4, 3611 - 8, 0, -141)));
        assertEquals("cold_ocean", name(MC_1_16, getBiomeAt(g, 4, -54 >> 2, 0, -23 >> 2)));
        assertEquals("mushroom_fields", name(MC_1_16, getBiomeAt(g, 4, 68 >> 2, 0, 47 >> 2)));
        assertEquals("frozen_ocean", name(MC_1_16, getBiomeAt(g, 4, 186 >> 2, 0, 249 >> 2)));
        assertEquals("forest", name(MC_1_16, getBiomeAt(g, 4, 3256313 >> 2, 0, -3265404 >> 2)));

        applySeed(g, DIM_NETHER, 1551515151585454L);
        assertEquals("crimson_forest", name(MC_1_16, getBiomeAt(g, 4, 181 >> 2, 0, 209 >> 2)));
        assertEquals("soul_sand_valley", name(MC_1_16, getBiomeAt(g, 4, 404 >> 2, 0, 416 >> 2)));
        assertEquals("basalt_deltas", name(MC_1_16, getBiomeAt(g, 4, 308 >> 2, 0, 32 >> 2)));
    }

    @Test
    void biomesMatchRealWorlds_26_3() {
        long g = generator(MC_26_3, DIM_OVERWORLD, -3829811542736183482L);
        assertEquals("dappled_forest", name(MC_26_3, getBiomeAt(g, 1, 68148, 77, 80990)));
    }

    @Test
    void genBiomesIsRowMajorAndMatchesGetBiomeAt() {
        long g = generator(GAME_MC_VERSION, DIM_OVERWORLD, 12345L);
        int x = -40, z = 25, y = 15, w = 23, h = 17;
        int[] area = genBiomes(g, 4, x, z, y, w, h);
        assertEquals(w * h, area.length);
        for (int dz = 0; dz < h; dz++) {
            for (int dx = 0; dx < w; dx++) {
                assertEquals(getBiomeAt(g, 4, x + dx, y, z + dz), area[dz * w + dx], "at dx=" + dx + " dz=" + dz);
            }
        }
    }

    @Test
    void biomeNamesAreVersionAware() {
        // Biome id 3 was "mountains" before 1.18 and "windswept_hills" after.
        assertEquals("mountains", getBiomeName(MC_1_16, 3));
        assertEquals("windswept_hills", getBiomeName(MC_1_21_4, 3));
        assertNull(getBiomeName(MC_1_21_4, 255));
    }

    @Test
    void overworldBiomeListFollowsVersion() {
        int paleGarden = findBiome(MC_1_21_4, "pale_garden");
        assertTrue(isOverworldBiome(MC_1_21_4, paleGarden));
        assertFalse(isOverworldBiome(MC_1_21_3, paleGarden), "pale garden was added in 1.21.4");
        // swamp_hills (134) was removed in 1.18; gravelly_mountains (131) was only renamed.
        assertTrue(isOverworldBiome(MC_1_16, 134));
        assertFalse(isOverworldBiome(MC_1_21_4, 134), "removed in 1.18");
        assertEquals("windswept_gravelly_hills", getBiomeName(MC_1_21_4, 131));
        assertTrue(isOverworldBiome(MC_1_21_4, 131));
    }

    @Test
    void biomeColorsAreOpaque() {
        int[] colors = getBiomeColors();
        assertEquals(256, colors.length);
        assertEquals(0xFF, colors[findBiome(MC_1_21_4, "plains")] >>> 24);
    }

    @Test
    void structureConstantsMatchNames() {
        assertEquals("village", getStructureName(VILLAGE));
        assertEquals("monument", getStructureName(MONUMENT));
        assertEquals("mansion", getStructureName(MANSION));
        assertEquals("fortress", getStructureName(FORTRESS));
        assertEquals("stronghold", getStructureName(STRONGHOLD));
    }

    @Test
    void structurePositionsLieInsideTheirRegion() {
        int[] config = getStructureConfig(VILLAGE, GAME_MC_VERSION);
        assertNotNull(config);
        int regionBlocks = config[0] * 16;
        int rangeBlocks = config[1] * 16;
        assertEquals(34 * 16, regionBlocks, "villages use 34-chunk regions");

        for (int rx = -3; rx <= 3; rx++) {
            for (int rz = -3; rz <= 3; rz++) {
                int[] pos = getStructurePos(VILLAGE, GAME_MC_VERSION, 42L, rx, rz);
                assertNotNull(pos);
                assertTrue(pos[0] >= rx * regionBlocks && pos[0] < rx * regionBlocks + rangeBlocks);
                assertTrue(pos[1] >= rz * regionBlocks && pos[1] < rz * regionBlocks + rangeBlocks);
            }
        }
    }

    @Test
    void structureViabilityUsesBiomes() {
        long g = generator(GAME_MC_VERSION, DIM_OVERWORLD, 42L);
        int viable = 0, total = 0;
        for (int rx = -6; rx <= 6; rx++) {
            for (int rz = -6; rz <= 6; rz++) {
                int[] pos = getStructurePos(VILLAGE, GAME_MC_VERSION, 42L, rx, rz);
                total++;
                if (isViableStructurePos(VILLAGE, g, pos[0], pos[1], 0)) viable++;
            }
        }
        // Villages need specific biomes, so some but not all attempts succeed.
        assertTrue(viable > 0 && viable < total, "viable=" + viable + "/" + total);
    }

    @Test
    void strongholdsAreComplete() {
        long g = generator(GAME_MC_VERSION, DIM_OVERWORLD, 42L);
        int[] positions = getStrongholds(g, 128);
        assertEquals(2 * 128, positions.length);

        Set<Long> unique = new HashSet<>();
        for (int i = 0; i < positions.length; i += 2) {
            unique.add(((long) positions[i] << 32) | (positions[i + 1] & 0xFFFFFFFFL));
        }
        assertEquals(128, unique.size(), "no duplicate strongholds");

        // The first ring holds exactly 3 strongholds, 1280-2816 blocks from the origin
        // (+-112 blocks for the biome snap, plus 8 for the chunk centre).
        for (int i = 0; i < 3; i++) {
            double dist = Math.hypot(positions[2 * i], positions[2 * i + 1]);
            assertTrue(dist > 1280 - 120 && dist < 2816 + 120, "stronghold " + i + " at distance " + dist);
        }
        double fourth = Math.hypot(positions[6], positions[7]);
        assertTrue(fourth > 4352 - 120, "4th stronghold belongs to ring 2, distance " + fourth);

        int[] firstThree = getStrongholds(g, 3);
        assertArrayEquals(java.util.Arrays.copyOf(positions, 6), firstThree);
    }

    @Test
    void spawnEstimateIsNearOrigin() {
        long g = generator(GAME_MC_VERSION, DIM_OVERWORLD, 42L);
        int[] spawn = getSpawn(g, false);
        assertEquals(2, spawn.length);
        assertTrue(Math.abs(spawn[0]) < 2048 && Math.abs(spawn[1]) < 2048);
    }

    @Test
    void misuseIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> setupGenerator(MC_NEWEST + 1, 0));
        long g = generator(GAME_MC_VERSION, DIM_NETHER, 1L);
        assertThrows(IllegalStateException.class, () -> getStrongholds(g, 10));
        assertThrows(IllegalArgumentException.class, () -> applySeed(g, 5, 1L));
        assertThrows(IllegalArgumentException.class, () -> getBiomeAt(g, 3, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> genBiomes(g, 4, 0, 0, 0, 0, 10));
        assertThrows(IllegalStateException.class, () -> getBiomeAt(0L, 4, 0, 0, 0));
        assertNull(getStructureConfig(TRIAL_CHAMBERS, MC_1_20), "trial chambers did not exist in 1.20");
    }

    private static int findBiome(int mc, String name) {
        for (int id = 0; id < 256; id++) {
            if (name.equals(getBiomeName(mc, id))) return id;
        }
        throw new AssertionError("no biome " + name);
    }
}
