package cubeium.cubeium.noise;

import java.util.Random;

public class DoublePerlinNoise {
    private final PerlinNoise noiseA;
    private final PerlinNoise noiseB;
    private final double amplitude;
    private final double lacunarity;
    private final double persistence;
    private final int octaves;

    public DoublePerlinNoise(Random random, int octaves, double persistence, double lacunarity, double amplitude) {
        this.octaves = octaves;
        this.persistence = persistence;
        this.lacunarity = lacunarity;
        this.amplitude = amplitude;

        // Initialize both noise functions with different seeds
        long seedA = random.nextLong();
        long seedB = random.nextLong();
        this.noiseA = new PerlinNoise(new Random(seedA), octaves, persistence, lacunarity, amplitude);
        this.noiseB = new PerlinNoise(new Random(seedB), octaves, persistence, lacunarity, amplitude);
    }

    public double noise(double x, double y, double z) {
        // Scale coordinates to match Cubiomes
        double scale = 1.0 / (1 << octaves);
        x *= scale;
        y *= scale;
        z *= scale;

        // Sample both noise functions and combine
        double noiseAValue = noiseA.noise(x, y, z);
        double noiseBValue = noiseB.noise(x, y, z);

        // Combine and scale to match Cubiomes' output range
        return (noiseAValue + noiseBValue) * 0.5 * amplitude;
    }

    public void setSeed(long seed) {
        // Create two different seeds for the noise functions
        Random random = new Random(seed);
        long seedA = random.nextLong();
        long seedB = random.nextLong();

        noiseA.setSeed(seedA);
        noiseB.setSeed(seedB);
    }
}