package cubeium.cubeium.generator;

import cubeium.cubeium.layer.LayerStack;

public class BiomeGenerator {
    private final long seed;
    private final boolean largeBiomes;
    private final LayerStack layerStack;

    public BiomeGenerator(long seed, boolean largeBiomes) {
        this.seed = seed;
        this.largeBiomes = largeBiomes;
        this.layerStack = new LayerStack(seed, largeBiomes);
    }

    public int getBiome(int x, int y, int z) {
        // Get a 1x1 biome map centered on the requested coordinates
        int[] biomes = layerStack.getBiomeMap(x, z, 1, 1);
        return biomes[0];
    }

    public int[] generateBiomeMap(int startX, int startZ, int width, int height) {
        return layerStack.getBiomeMap(startX, startZ, width, height);
    }
}