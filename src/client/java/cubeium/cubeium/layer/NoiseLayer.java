package cubeium.cubeium.layer;

public class NoiseLayer extends Layer {
    private static final int MOD = 299999;

    public NoiseLayer(Layer parent) {
        super(0);
        this.parent = parent;
    }

    @Override
    public int getMap(int[] out, int x, int z, int w, int h) {
        int err = parent.getMap(out, x, z, w, h);
        if (err != 0) {
            return err;
        }

        long ss = startSeed;
        long cs;

        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                if (out[i + j * w] > 0) {
                    cs = getChunkSeed(ss, i + x, j + z);
                    out[i + j * w] = mcFirstInt(cs, MOD) + 2;
                } else {
                    out[i + j * w] = 0;
                }
            }
        }

        return 0;
    }
}