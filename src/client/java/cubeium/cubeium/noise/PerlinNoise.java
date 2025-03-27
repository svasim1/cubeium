package cubeium.cubeium.noise;

import java.util.Random;

public class PerlinNoise {
    private static final int PSIZE = 512;
    private static final int PMASK = 511;
    private final int[] p = new int[PSIZE];
    private final double amplitude;
    private final double lacunarity;
    private final double persistence;
    private final int octaves;

    public PerlinNoise(Random random, int octaves, double persistence, double lacunarity, double amplitude) {
        this.octaves = octaves;
        this.persistence = persistence;
        this.lacunarity = lacunarity;
        this.amplitude = amplitude;

        // Initialize permutation array using Cubiomes' approach
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = p[i];
            p[i] = p[j];
            p[j] = temp;
        }
        // Copy to upper half
        for (int i = 0; i < 256; i++) {
            p[i + 256] = p[i];
        }
    }

    public void setSeed(long seed) {
        Random random = new Random(seed);
        // Initialize permutation array using Cubiomes' approach
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = p[i];
            p[i] = p[j];
            p[j] = temp;
        }
        // Copy to upper half
        for (int i = 0; i < 256; i++) {
            p[i + 256] = p[i];
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
        int xi = (int) Math.floor(x) & PMASK;
        int yi = (int) Math.floor(y) & PMASK;
        int zi = (int) Math.floor(z) & PMASK;

        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double zf = z - Math.floor(z);

        double u = fade(xf);
        double v = fade(yf);
        double w = fade(zf);

        // Apply mask to all permutation indices to ensure they stay within bounds
        int aaa = p[(p[(p[xi & PMASK] & PMASK) + (yi & PMASK)] & PMASK) + (zi & PMASK)];
        int aba = p[(p[(p[xi & PMASK] & PMASK) + ((yi + 1) & PMASK)] & PMASK) + (zi & PMASK)];
        int aab = p[(p[(p[xi & PMASK] & PMASK) + (yi & PMASK)] & PMASK) + ((zi + 1) & PMASK)];
        int abb = p[(p[(p[xi & PMASK] & PMASK) + ((yi + 1) & PMASK)] & PMASK) + ((zi + 1) & PMASK)];
        int baa = p[(p[(p[(xi + 1) & PMASK] & PMASK) + (yi & PMASK)] & PMASK) + (zi & PMASK)];
        int bba = p[(p[(p[(xi + 1) & PMASK] & PMASK) + ((yi + 1) & PMASK)] & PMASK) + (zi & PMASK)];
        int bab = p[(p[(p[(xi + 1) & PMASK] & PMASK) + (yi & PMASK)] & PMASK) + ((zi + 1) & PMASK)];
        int bbb = p[(p[(p[(xi + 1) & PMASK] & PMASK) + ((yi + 1) & PMASK)] & PMASK) + ((zi + 1) & PMASK)];

        double x1 = lerp(grad(aaa, xf, yf, zf),
                grad(baa, xf - 1, yf, zf),
                u);
        double x2 = lerp(grad(aba, xf, yf - 1, zf),
                grad(bba, xf - 1, yf - 1, zf),
                u);
        double y1 = lerp(x1, x2, v);

        double x3 = lerp(grad(aab, xf, yf, zf - 1),
                grad(bab, xf - 1, yf, zf - 1),
                u);
        double x4 = lerp(grad(abb, xf, yf - 1, zf - 1),
                grad(bbb, xf - 1, yf - 1, zf - 1),
                u);
        double y2 = lerp(x3, x4, v);

        return lerp(y1, y2, w);
    }

    private double grad(int hash, double x, double y, double z) {
        // Convert hash to 0-15 range
        hash = hash & 15;

        // Get direction vector components based on hash
        double u = hash < 8 ? x : y;
        double v = hash < 4 ? y : (hash == 12 || hash == 14) ? x : z;

        // Determine signs based on lowest 2 bits
        return ((hash & 1) == 0 ? u : -u) + ((hash & 2) == 0 ? v : -v);
    }

    private double fade(double t) {
        // Improved Perlin fade function: 6t^5 - 15t^4 + 10t^3
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }
}