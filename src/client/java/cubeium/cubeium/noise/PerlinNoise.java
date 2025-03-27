package cubeium.cubeium.noise;

import java.util.Random;

public class PerlinNoise {
    private final int[] p = new int[256];
    private final double amplitude;
    private final double lacunarity;
    private final double persistence;
    private final int octaves;

    public PerlinNoise(Random random, int octaves, double persistence, double lacunarity, double amplitude) {
        this.octaves = octaves;
        this.persistence = persistence;
        this.lacunarity = lacunarity;
        this.amplitude = amplitude;

        // Initialize permutation array
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            int j = random.nextInt(256 - i) + i;
            int temp = p[i];
            p[i] = p[j];
            p[j] = temp;
        }
    }

    public double noise(double x, double y, double z) {
        double total = 0;
        double frequency = 1;
        double amplitude = this.amplitude;
        double maxValue = 0;

        for (int i = 0; i < octaves; i++) {
            total += interpolatedNoise(x * frequency, y * frequency, z * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= lacunarity;
        }

        return total / maxValue;
    }

    private double interpolatedNoise(double x, double y, double z) {
        int intX = (int) Math.floor(x) & 255;
        int intY = (int) Math.floor(y) & 255;
        int intZ = (int) Math.floor(z) & 255;

        double fracX = x - Math.floor(x);
        double fracY = y - Math.floor(y);
        double fracZ = z - Math.floor(z);

        // Ensure all array accesses are properly bounded
        int A = p[intX] & 255;
        int AA = p[(A + intY) & 255] & 255;
        int AB = p[(A + ((intY + 1) & 255)) & 255] & 255;
        int B = p[(intX + 1) & 255] & 255;
        int BA = p[(B + intY) & 255] & 255;
        int BB = p[(B + ((intY + 1) & 255)) & 255] & 255;

        // Ensure all array accesses are properly bounded for the Z dimension
        double v1 = grad(p[(AA + intZ) & 255], fracX, fracY, fracZ);
        double v2 = grad(p[(BA + intZ) & 255], fracX - 1, fracY, fracZ);
        double v3 = grad(p[(AB + intZ) & 255], fracX, fracY - 1, fracZ);
        double v4 = grad(p[(BB + intZ) & 255], fracX - 1, fracY - 1, fracZ);
        double v5 = grad(p[(AA + ((intZ + 1) & 255)) & 255], fracX, fracY, fracZ - 1);
        double v6 = grad(p[(BA + ((intZ + 1) & 255)) & 255], fracX - 1, fracY, fracZ - 1);
        double v7 = grad(p[(AB + ((intZ + 1) & 255)) & 255], fracX, fracY - 1, fracZ - 1);
        double v8 = grad(p[(BB + ((intZ + 1) & 255)) & 255], fracX - 1, fracY - 1, fracZ - 1);

        double i1 = interpolate(v1, v2, fade(fracX));
        double i2 = interpolate(v3, v4, fade(fracX));
        double i3 = interpolate(v5, v6, fade(fracX));
        double i4 = interpolate(v7, v8, fade(fracX));

        double j1 = interpolate(i1, i2, fade(fracY));
        double j2 = interpolate(i3, i4, fade(fracY));

        return interpolate(j1, j2, fade(fracZ));
    }

    private double grad(int hash, double x, double y, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : y;
        double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private double interpolate(double a, double b, double t) {
        return a + t * (b - a);
    }
}