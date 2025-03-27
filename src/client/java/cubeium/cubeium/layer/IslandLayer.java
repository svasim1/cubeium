package cubeium.cubeium.layer;

public class IslandLayer extends Layer {
    private static final int OCEANIC = 0;

    public IslandLayer(Layer parent) {
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
                out[i + j * w] = v11;

                if (v11 == OCEANIC) {
                    if (out[i + 1 + (j + 0) * pW] != OCEANIC)
                        continue;
                    if (out[i + 2 + (j + 1) * pW] != OCEANIC)
                        continue;
                    if (out[i + 0 + (j + 1) * pW] != OCEANIC)
                        continue;
                    if (out[i + 1 + (j + 2) * pW] != OCEANIC)
                        continue;

                    cs = getChunkSeed(ss, i + x, j + z);
                    if (mcFirstIsZero(cs, 2)) {
                        out[i + j * w] = 1;
                    }
                }
            }
        }

        return 0;
    }
}