package cubeium.cubeium.noise;

import java.util.Random;

public class BiomeNoise {
    private static final int NP_TEMPERATURE = 0;
    private static final int NP_HUMIDITY = 1;
    private static final int NP_CONTINENTALNESS = 2;
    private static final int NP_EROSION = 3;
    private static final int NP_WEIRDNESS = 4;
    private static final int NP_DEPTH = 5;

    // Cubiomes' exact parameters
    private static final int[] OCTAVES = { 7, 7, 7, 7, 7, 7 };
    private static final double[] PERSISTENCE = { 0.5, 0.5, 0.5, 0.5, 0.5, 0.5 };
    private static final double[] LACUNARITY = { 2.0, 2.0, 2.0, 2.0, 2.0, 2.0 };
    private static final double[] AMPLITUDE = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0 };
    private static final double[] SCALE = { 1.0, 1.0, 1.0, 1.0, 1.0, 1.0 };

    private final DoublePerlinNoise[] climateNoise;
    private final long seed;
    private final boolean largeBiomes;

    public BiomeNoise(long seed, boolean largeBiomes) {
        this.seed = seed;
        this.largeBiomes = largeBiomes;

        Random random = new Random(seed);
        this.climateNoise = new DoublePerlinNoise[6];

        // Initialize climate noise parameters using Cubiomes' exact values
        for (int i = 0; i < 6; i++) {
            long paramSeed = random.nextLong();
            climateNoise[i] = new DoublePerlinNoise(
                    new Random(paramSeed),
                    OCTAVES[i],
                    PERSISTENCE[i],
                    LACUNARITY[i],
                    AMPLITUDE[i]);
        }
    }

    public double[] getClimateParameters(int x, int y, int z) {
        double[] params = new double[6];

        // Scale coordinates for large biomes and ensure they stay within bounds
        double scale = largeBiomes ? 4.0 : 1.0;
        // Scale coordinates and add offset to prevent precision issues
        double scaledX = (x / scale) + 10000;
        double scaledZ = (z / scale) + 10000;

        // Sample each climate parameter with scaled coordinates
        for (int i = 0; i < 6; i++) {
            // Get raw noise value with Cubiomes' exact scaling
            double noise = climateNoise[i].noise(
                    scaledX * SCALE[i] * 0.1,
                    y * SCALE[i] * 0.1,
                    scaledZ * SCALE[i] * 0.1);
            // Normalize to 0-1 range using Cubiomes' approach
            params[i] = (noise + 1.0) * 0.5;
            // Clamp to 0-1 range
            params[i] = Math.max(0.0, Math.min(1.0, params[i]));
        }

        return params;
    }

    public int getBiome(int x, int y, int z) {
        double[] params = getClimateParameters(x, y, z);
        double temperature = params[NP_TEMPERATURE];
        double humidity = params[NP_HUMIDITY];
        double continentalness = params[NP_CONTINENTALNESS];
        double erosion = params[NP_EROSION];
        double weirdness = params[NP_WEIRDNESS];
        double depth = params[NP_DEPTH];

        // First, determine the major biome category based on continentalness and
        // temperature
        if (continentalness < 0.2) {
            // Ocean biomes
            if (depth < 0.2) {
                if (temperature < 0.3)
                    return 48; // Frozen Ocean
                if (temperature < 0.5)
                    return 46; // Cold Ocean
                if (temperature < 0.7)
                    return 44; // Ocean
                if (temperature < 0.9)
                    return 42; // Lukewarm Ocean
                return 41; // Warm Ocean
            } else {
                if (temperature < 0.3)
                    return 49; // Deep Frozen Ocean
                if (temperature < 0.5)
                    return 47; // Deep Cold Ocean
                if (temperature < 0.7)
                    return 45; // Deep Ocean
                if (temperature < 0.9)
                    return 43; // Deep Lukewarm Ocean
                return 41; // Warm Ocean
            }
        } else if (continentalness < 0.4) {
            // Beach biomes
            if (temperature < 0.3)
                return 39; // Snowy Beach
            if (temperature < 0.5)
                return 40; // Stony Shore
            return 38; // Beach
        } else if (continentalness < 0.6) {
            // Inland biomes
            if (temperature < 0.2) {
                if (humidity < 0.3)
                    return 3; // Snowy Plains
                if (humidity < 0.6)
                    return 16; // Snowy Taiga
                return 4; // Ice Spikes
            } else if (temperature < 0.4) {
                if (humidity < 0.3)
                    return 5; // Desert
                if (humidity < 0.6)
                    return 15; // Taiga
                return 8; // Forest
            } else if (temperature < 0.6) {
                if (humidity < 0.3)
                    return 17; // Savanna
                if (humidity < 0.6)
                    return 1; // Plains
                return 8; // Forest
            } else if (temperature < 0.8) {
                if (humidity < 0.3)
                    return 17; // Savanna
                if (humidity < 0.6)
                    return 1; // Plains
                return 23; // Jungle
            } else {
                if (humidity < 0.3)
                    return 5; // Desert
                if (humidity < 0.6)
                    return 17; // Savanna
                return 23; // Jungle
            }
        } else {
            // Mountain biomes
            if (temperature < 0.3) {
                if (erosion < 0.3)
                    return 32; // Snowy Slopes
                if (erosion < 0.6)
                    return 33; // Frozen Peaks
                return 34; // Jagged Peaks
            } else if (temperature < 0.5) {
                if (erosion < 0.3)
                    return 31; // Grove
                if (erosion < 0.6)
                    return 35; // Stony Peaks
                return 34; // Jagged Peaks
            } else {
                if (erosion < 0.3)
                    return 29; // Meadow
                if (erosion < 0.6)
                    return 35; // Stony Peaks
                return 34; // Jagged Peaks
            }
        }
    }
}