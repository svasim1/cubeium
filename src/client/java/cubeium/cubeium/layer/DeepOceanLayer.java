package cubeium.cubeium.layer;

public class DeepOceanLayer extends Layer {
    private static final int OCEAN = 0;
    private static final int DEEP_OCEAN = 24;

    public DeepOceanLayer(Layer parent) {
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

        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int v11 = out[i + 1 + (j + 1) * pW];

                if (v11 == OCEAN) {
                    int oceans = 0;
                    if (out[i + 1 + (j + 0) * pW] == OCEAN)
                        oceans++;
                    if (out[i + 2 + (j + 1) * pW] == OCEAN)
                        oceans++;
                    if (out[i + 0 + (j + 1) * pW] == OCEAN)
                        oceans++;
                    if (out[i + 1 + (j + 2) * pW] == OCEAN)
                        oceans++;

                    if (oceans > 3) {
                        v11 = DEEP_OCEAN;
                    }
                }

                out[i + j * w] = v11;
            }
        }

        return 0;
    }
}