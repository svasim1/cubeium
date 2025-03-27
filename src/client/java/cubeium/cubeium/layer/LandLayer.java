package cubeium.cubeium.layer;

public class LandLayer extends Layer {
    private static final int OCEAN = 0;
    private static final int FOREST = 4;

    public LandLayer(Layer parent) {
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

        long st = startSalt;
        long ss = startSeed;
        long cs;

        for (int j = 0; j < h; j++) {
            int[] vz0 = new int[pW];
            int[] vz1 = new int[pW];
            int[] vz2 = new int[pW];
            System.arraycopy(out, j * pW, vz0, 0, pW);
            System.arraycopy(out, (j + 1) * pW, vz1, 0, pW);
            System.arraycopy(out, (j + 2) * pW, vz2, 0, pW);

            int v00 = vz0[0], vt0 = vz0[1];
            int v02 = vz2[0], vt2 = vz2[1];
            int v20, v22;
            int v11, v;

            for (int i = 0; i < w; i++) {
                v11 = vz1[i + 1];
                v20 = vz0[i + 2];
                v22 = vz2[i + 2];
                v = v11;

                switch (v11) {
                    case OCEAN:
                        if (v00 != OCEAN || v20 != OCEAN || v02 != OCEAN || v22 != OCEAN) {
                            cs = getChunkSeed(ss, i + x, j + z);
                            int inc = 0;
                            v = 1;

                            if (v00 != OCEAN) {
                                ++inc;
                                v = v00;
                                cs = mcStepSeed(cs, st);
                            }
                            if (v20 != OCEAN) {
                                if (++inc == 1 || mcFirstIsZero(cs, 2))
                                    v = v20;
                                cs = mcStepSeed(cs, st);
                            }
                            if (v02 != OCEAN) {
                                switch (++inc) {
                                    case 1:
                                        v = v02;
                                        break;
                                    case 2:
                                        if (mcFirstIsZero(cs, 2))
                                            v = v02;
                                        break;
                                    default:
                                        if (mcFirstIsZero(cs, 3))
                                            v = v02;
                                }
                                cs = mcStepSeed(cs, st);
                            }
                            if (v22 != OCEAN) {
                                switch (++inc) {
                                    case 1:
                                        v = v22;
                                        break;
                                    case 2:
                                        if (mcFirstIsZero(cs, 2))
                                            v = v22;
                                        break;
                                    case 3:
                                        if (mcFirstIsZero(cs, 3))
                                            v = v22;
                                        break;
                                    default:
                                        if (mcFirstIsZero(cs, 4))
                                            v = v22;
                                }
                                cs = mcStepSeed(cs, st);
                            }

                            if (v != FOREST) {
                                if (!mcFirstIsZero(cs, 3))
                                    v = OCEAN;
                            }
                        }
                        break;

                    case FOREST:
                        break;

                    default:
                        if (v00 == OCEAN || v20 == OCEAN || v02 == OCEAN || v22 == OCEAN) {
                            cs = getChunkSeed(ss, i + x, j + z);
                            if (mcFirstIsZero(cs, 5))
                                v = OCEAN;
                        }
                }

                out[i + j * w] = v;
                v00 = vt0;
                vt0 = v20;
                v02 = vt2;
                vt2 = v22;
            }
        }

        return 0;
    }
}