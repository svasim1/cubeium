package cubeium.cubeium.layer;

public class HeatLayer extends Layer {
    private static final int FREEZING = 1;
    private static final int COLD = 2;
    private static final int WARM = 3;
    private static final int LUSH = 4;

    public HeatLayer(Layer parent) {
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

                if (v11 == FREEZING) {
                    int v10 = out[i + 1 + (j + 0) * pW];
                    int v21 = out[i + 2 + (j + 1) * pW];
                    int v01 = out[i + 0 + (j + 1) * pW];
                    int v12 = out[i + 1 + (j + 2) * pW];

                    if (isAny4(WARM, v10, v21, v01, v12) || isAny4(LUSH, v10, v21, v01, v12)) {
                        v11 = COLD;
                    }
                }

                out[i + j * w] = v11;
            }
        }

        return 0;
    }

    private boolean isAny4(int target, int v1, int v2, int v3, int v4) {
        return v1 == target || v2 == target || v3 == target || v4 == target;
    }
}