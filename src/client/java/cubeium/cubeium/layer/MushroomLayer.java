package cubeium.cubeium.layer;

public class MushroomLayer extends Layer {
    private static final int MUSHROOM_FIELDS = 14;

    public MushroomLayer(Layer parent) {
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

                // surrounded by ocean?
                if (v11 == 0 &&
                        out[i + 0 + (j + 0) * pW] == 0 && out[i + 2 + (j + 0) * pW] == 0 &&
                        out[i + 0 + (j + 2) * pW] == 0 && out[i + 2 + (j + 2) * pW] == 0) {
                    cs = getChunkSeed(ss, i + x, j + z);
                    if (mcFirstIsZero(cs, 100)) {
                        v11 = MUSHROOM_FIELDS;
                    }
                }

                out[i + j * w] = v11;
            }
        }

        return 0;
    }
}