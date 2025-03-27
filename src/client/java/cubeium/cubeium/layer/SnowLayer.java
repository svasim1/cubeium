package cubeium.cubeium.layer;

public class SnowLayer extends Layer {
    private static final int OCEAN = 0;
    private static final int SNOWY_TUNDRA = 12;
    private static final int PLAINS = 1;

    public SnowLayer(Layer parent) {
        super(0);
        this.parent = parent;
    }

    @Override
    public int getMap(int[] out, int x, int z, int w, int h) {
        int pX = x - 1;
        int pZ = z - 1;
        int pW = w + 2;
        int pH = h + 2;

        int err = parent.getMap(out, pX, pZ, pW, pH);
        if (err != 0) {
            return err;
        }

        long ss = startSeed;
        long cs;

        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int v11 = out[i + 1 + (j + 1) * pW];
                if (v11 != OCEAN) {
                    cs = getChunkSeed(ss, i + x, j + z);
                    v11 = mcFirstIsZero(cs, 5) ? SNOWY_TUNDRA : PLAINS;
                }
                out[i + j * w] = v11;
            }
        }

        return 0;
    }
}