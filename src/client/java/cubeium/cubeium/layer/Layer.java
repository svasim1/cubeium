package cubeium.cubeium.layer;

import cubeium.cubeium.noise.PerlinNoise;
import cubeium.cubeium.util.SHA256;

public abstract class Layer {
    protected Layer parent;
    protected Layer child;
    protected PerlinNoise noise;
    protected long layerSalt;
    protected long startSalt;
    protected long startSeed;

    public static final long LAYER_INIT_SHA = 1000L;

    public Layer(long layerSalt) {
        this.layerSalt = layerSalt;
    }

    public void setSeed(long worldSeed) {
        // Only propagate to child, not parent
        if (child != null) {
            child.setSeed(worldSeed);
        }

        if (noise != null) {
            noise.setSeed(worldSeed);
        }

        if (layerSalt == 0) {
            // Pre 1.13 the Hills branch stays zero-initialized
            startSalt = 0;
            startSeed = 0;
        } else if (layerSalt == LAYER_INIT_SHA) {
            // Post 1.14 Voronoi uses SHA256 for initialization
            startSalt = SHA256.getVoronoiSHA(worldSeed);
            startSeed = 0;
        } else {
            long st = worldSeed;
            st = mcStepSeed(st, layerSalt);
            st = mcStepSeed(st, layerSalt);
            st = mcStepSeed(st, layerSalt);

            startSalt = st;
            startSeed = mcStepSeed(st, 0);
        }
    }

    public abstract int getMap(int[] out, int x, int z, int w, int h);

    protected static long mcStepSeed(long seed, long salt) {
        seed *= seed * 6364136223846793005L + 1442695040888963407L;
        seed += salt;
        return seed;
    }

    protected static long getChunkSeed(long seed, int x, int z) {
        long cs = seed;
        cs += x;
        cs *= cs * 6364136223846793005L + 1442695040888963407L;
        cs += z;
        cs *= cs * 6364136223846793005L + 1442695040888963407L;
        cs += x;
        cs *= cs * 6364136223846793005L + 1442695040888963407L;
        cs += z;
        return cs;
    }

    protected static boolean mcFirstIsZero(long seed, int mod) {
        return (mcFirstInt(seed, mod) == 0);
    }

    protected static int mcFirstInt(long seed, int mod) {
        int val = (int) ((seed >> 24) % mod);
        if (val < 0)
            val += mod;
        return val;
    }

    protected static int select(long seed, int a, int b) {
        return mcFirstIsZero(seed, 2) ? a : b;
    }

    private void getVoronoiCell(long sha, int x, int y, int z, int[] result) {
        // Update to match Cubiomes' 3D implementation
        // This affects how biomes are distributed
    }
}