package cubeium.cubeium.layer;

import cubeium.cubeium.util.SHA256;

public class VoronoiLayer extends Layer {
    private static final long LAYER_INIT_SHA = 1000L;

    public VoronoiLayer(Layer parent) {
        super(LAYER_INIT_SHA);
        this.parent = parent;
    }

    @Override
    public void setSeed(long worldSeed) {
        if (layerSalt == LAYER_INIT_SHA) {
            startSalt = SHA256.getVoronoiSHA(worldSeed);
            startSeed = 0;
        } else {
            super.setSeed(worldSeed);
        }
    }

    @Override
    public int getMap(int[] out, int x, int z, int w, int h) {
        x -= 2;
        z -= 2;
        int pX = x >> 2;
        int pZ = z >> 2;
        int pW = ((x + w) >> 2) - pX + 2;
        int pH = ((z + h) >> 2) - pZ + 2;

        if (parent != null) {
            int err = parent.getMap(out, pX, pZ, pW, pH);
            if (err != 0)
                return err;
        }

        int i, j, pi, pj, pix, pjz;
        int v00, v01, v10, v11, v;
        long da1, da2, db1, db2, dc1, dc2, dd1, dd2;
        long sja, sjb, sjc, sjd, da, db, dc, dd;

        // Create a buffer to hold the parent layer's output
        int[] buf = new int[pW * pH];
        System.arraycopy(out, 0, buf, 0, pW * pH);

        long st = startSalt;
        long ss = startSeed;
        long cs;

        for (j = 0; j < h; j++) {
            for (i = 0; i < w; i++) {
                pi = (i + x) >> 2;
                pj = (j + z) >> 2;
                pix = (i + x) & 3;
                pjz = (j + z) & 3;

                // Calculate indices with boundary checking
                int idx00 = (pi - pX) + (pj - pZ) * pW;
                int idx01 = (pi - pX) + (pj + 1 - pZ) * pW;
                int idx10 = (pi + 1 - pX) + (pj - pZ) * pW;
                int idx11 = (pi + 1 - pX) + (pj + 1 - pZ) * pW;

                // Ensure indices are within bounds
                if (idx00 >= 0 && idx00 < buf.length &&
                        idx01 >= 0 && idx01 < buf.length &&
                        idx10 >= 0 && idx10 < buf.length &&
                        idx11 >= 0 && idx11 < buf.length) {

                    // Get values from parent layer
                    v00 = buf[idx00];
                    v01 = buf[idx01];
                    v10 = buf[idx10];
                    v11 = buf[idx11];

                    // Optimization for same values
                    if (v00 == v01 && v00 == v10 && v00 == v11) {
                        out[i + j * w] = v00;
                        continue;
                    }

                    // Calculate Voronoi cell
                    cs = getChunkSeed(ss, pi, pj);
                    da1 = (cs >> 24) & 0xFF;
                    da2 = (cs >> 16) & 0xFF;
                    cs = mcStepSeed(cs, st);
                    db1 = (cs >> 24) & 0xFF;
                    db2 = (cs >> 16) & 0xFF;
                    cs = mcStepSeed(cs, st);
                    dc1 = (cs >> 24) & 0xFF;
                    dc2 = (cs >> 16) & 0xFF;
                    cs = mcStepSeed(cs, st);
                    dd1 = (cs >> 24) & 0xFF;
                    dd2 = (cs >> 16) & 0xFF;

                    sja = (da1 + pix) * (da1 + pix) + (da2 + pjz) * (da2 + pjz);
                    sjb = (db1 + pix - 4) * (db1 + pix - 4) + (db2 + pjz) * (db2 + pjz);
                    sjc = (dc1 + pix) * (dc1 + pix) + (dc2 + pjz - 4) * (dc2 + pjz - 4);
                    sjd = (dd1 + pix - 4) * (dd1 + pix - 4) + (dd2 + pjz - 4) * (dd2 + pjz - 4);

                    da = sja;
                    db = sjb;
                    dc = sjc;
                    dd = sjd;

                    v = v00;
                    if (db < da) {
                        da = db;
                        v = v10;
                    }
                    if (dc < da) {
                        da = dc;
                        v = v01;
                    }
                    if (dd < da) {
                        v = v11;
                    }

                    out[i + j * w] = v;
                } else {
                    // Fallback if indices are out of bounds
                    out[i + j * w] = 0; // Use a default biome ID (0 is The Void)
                }
            }
        }

        return 0;
    }
}