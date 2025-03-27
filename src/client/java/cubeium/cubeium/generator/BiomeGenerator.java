package cubeium.cubeium.generator;

import cubeium.cubeium.noise.BiomeNoise;

public class BiomeGenerator {
    private final long seed;
    private final boolean largeBiomes;
    private final BiomeNoise biomeNoise;

    public BiomeGenerator(long seed, boolean largeBiomes) {
        this.seed = seed;
        this.largeBiomes = largeBiomes;
        this.biomeNoise = new BiomeNoise(seed, largeBiomes);
    }

    public int getBiome(int x, int y, int z) {
        return biomeNoise.getBiome(x, y, z);
    }

    public int[] generateBiomeMap(int startX, int startZ, int width, int height) {
        int[] biomes = new int[width * height];

        for (int z = 0; z < height; z++) {
            for (int x = 0; x < width; x++) {
                biomes[z * width + x] = getBiome(startX + x, 64, startZ + z);
            }
        }

        return biomes;
    }
}