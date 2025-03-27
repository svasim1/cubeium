package cubeium.cubeium.layer;

public class SpecialLayer extends Layer {
    private static final int OCEANIC = 0;

    public SpecialLayer(Layer parent) {
        super(0);
        this.parent = parent;
    }

    @Override
    public int getMap(int[] out, int x, int z, int w, int h) {
        int err = parent.getMap(out, x, z, w, h);
        if (err != 0) {
            return err;
        }

        long st = startSalt;
        long ss = startSeed;
        long cs;

        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int v = out[i + j * w];
                if (v == OCEANIC)
                    continue;

                cs = getChunkSeed(ss, i + x, j + z);

                if (mcFirstIsZero(cs, 13)) {
                    cs = mcStepSeed(cs, st);
                    v |= (int) ((1 + mcFirstInt(cs, 15)) << 8) & 0xf00;
                    out[i + j * w] = v;
                }
            }
        }

        return 0;
    }
}