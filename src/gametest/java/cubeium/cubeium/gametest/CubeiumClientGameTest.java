package cubeium.cubeium.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import cubeium.cubeium.Cubeium;
import cubeium.cubeium.seedmap.CubeiumBiomeFilterScreen;
import cubeium.cubeium.seedmap.CubeiumSeedMapScreen;
import cubeium.cubeium.world.CubiomesInterface;
import cubeium.cubeium.world.generation.BiomeGenerator;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.GeneratorOptions;

/**
 * Runs inside the real client: compares Cubeium's biomes with Minecraft's own biome source for
 * the same seed, then opens the seed map and biome filter and takes screenshots
 * (run/screenshots/).
 */
public class CubeiumClientGameTest implements FabricClientGameTest {
    private static final String SEED = "Cubeium";
    private static final int SAMPLES = 4000;
    private static final int RANGE = 30_000;

    @Override
    public void runTest(ClientGameTestContext context) {
        // Consistent settings would create a superflat world; we need real terrain generation.
        try (TestSingleplayerContext singleplayer = context.worldBuilder()
                .setUseConsistentSettings(false)
                .adjustSettings(creator -> creator.setSeed(SEED))
                .create()) {
            singleplayer.getClientWorld().waitForChunksRender();

            List<String> mismatches = singleplayer.getServer().computeOnServer(CubeiumClientGameTest::compareWithMinecraft);
            if (!mismatches.isEmpty()) {
                throw new AssertionError(mismatches.size() + " biome mismatches, first: " + mismatches.subList(0, Math.min(10, mismatches.size())));
            }

            context.setScreen(CubeiumSeedMapScreen::new);
            context.waitTicks(100);
            context.takeScreenshot("cubeium-seedmap");

            context.clickScreenButton("cubeium.ui.filter");
            context.waitForScreen(CubeiumBiomeFilterScreen.class);
            context.waitTicks(5);
            context.takeScreenshot("cubeium-biome-filter");
            context.setScreen(() -> null);
        }
    }

    private static List<String> compareWithMinecraft(MinecraftServer server) {
        ServerWorld overworld = server.getOverworld();
        long seed = overworld.getSeed();
        if (seed != GeneratorOptions.parseSeed(SEED).orElseThrow()) {
            return List.of("world seed " + seed + " differs from the parsed seed text");
        }

        ServerChunkManager chunks = overworld.getChunkManager();
        BiomeSource biomeSource = chunks.getChunkGenerator().getBiomeSource();
        MultiNoiseUtil.MultiNoiseSampler sampler = chunks.getNoiseConfig().getMultiNoiseSampler();

        int mc = CubiomesInterface.GAME_MC_VERSION;
        BiomeGenerator mapGenerator = new BiomeGenerator();
        long handle = CubiomesInterface.setupGenerator(mc, 0);
        List<String> mismatches = new ArrayList<>();
        List<String> undergroundMismatches = new ArrayList<>();
        Set<String> seenBiomes = new HashSet<>();
        try {
            CubiomesInterface.applySeed(handle, CubiomesInterface.DIM_OVERWORLD, seed);
            Random random = new Random(1);
            int[] layers = {BiomeGenerator.MAP_BIOME_Y, 64, -20};
            for (int i = 0; i < SAMPLES; i++) {
                int x = random.nextInt(2 * RANGE) - RANGE;
                int z = random.nextInt(2 * RANGE) - RANGE;
                for (int y : layers) {
                    String expected = minecraftBiome(biomeSource, sampler, x, y, z);
                    seenBiomes.add(expected);
                    String actual = CubiomesInterface.getBiomeName(mc,
                            CubiomesInterface.getBiomeAt(handle, 4, x >> 2, y >> 2, z >> 2));
                    if (!expected.equals(actual)) {
                        (y < 0 ? undergroundMismatches : mismatches).add("(" + x + ", " + y + ", " + z + ") minecraft=" + expected + " cubiomes=" + actual);
                    }
                }
                // The map pipeline itself (BiomeGenerator at the map's sampling height).
                String expectedMap = minecraftBiome(biomeSource, sampler, x, BiomeGenerator.MAP_BIOME_Y, z);
                String actualMap = CubiomesInterface.getBiomeName(mc, mapGenerator.getBiomeAt(seed, x, z));
                if (!expectedMap.equals(actualMap)) {
                    mismatches.add("map (" + x + ", " + z + ") minecraft=" + expectedMap + " cubeium=" + actualMap);
                }
            }
        } finally {
            CubiomesInterface.freeGenerator(handle);
            mapGenerator.cleanup();
        }

        Cubeium.LOGGER.info("[GameTest] Compared {} positions x {} heights + map layer with Minecraft: {} mismatches, "
                + "{} underground mismatches {}, {} distinct biomes",
                SAMPLES, 3, mismatches.size(), undergroundMismatches.size(), undergroundMismatches, seenBiomes.size());
        // The map samples at MAP_BIOME_Y, which must match exactly. cubiomes has rare edge cases in
        // underground cave biomes (seen: lush_caves under an ocean at y=-20); tolerate up to 0.1% there.
        if (undergroundMismatches.size() > SAMPLES / 1000) {
            mismatches.addAll(undergroundMismatches);
        }
        if (seenBiomes.size() < 10) {
            // Guards against comparing with a flat or otherwise non-default world.
            mismatches.add(0, "only " + seenBiomes.size() + " distinct biomes in the test world: " + seenBiomes);
        }
        return mismatches;
    }

    private static String minecraftBiome(BiomeSource source, MultiNoiseUtil.MultiNoiseSampler sampler, int x, int y, int z) {
        return source.getBiome(x >> 2, y >> 2, z >> 2, sampler).getKey().orElseThrow().getValue().getPath();
    }
}
