package cubeium.cubeium.noise;

import java.util.Random;

public class DoublePerlinNoise {
    private final PerlinNoise noiseA;
    private final PerlinNoise noiseB;
    private final double amplitude;

    public DoublePerlinNoise(Random random, int octaves, double persistence, double lacunarity, double amplitude) {
        this.noiseA = new PerlinNoise(random, octaves, persistence, lacunarity, amplitude);
        this.noiseB = new PerlinNoise(random, octaves, persistence, lacunarity, amplitude);
        this.amplitude = amplitude;
    }

    public double noise(double x, double y, double z) {
        return (noiseA.noise(x, y, z) + noiseB.noise(x, y, z)) * amplitude;
    }
}