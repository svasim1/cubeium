package cubeium.cubeium.layer;

public class ZoomLayer extends Layer {
    public ZoomLayer(Layer parent) {
        super(0);
        this.parent = parent;
    }

    @Override
    public int getMap(int[] out, int x, int z, int w, int h) {
        int pX = x >> 1;
        int pZ = z >> 1;
        int pW = ((x + w) >> 1) - pX + 2;
        int pH = ((z + h) >> 1) - pZ + 2;

        int[] parentOut = new int[pW * pH];

        int err = parent.getMap(parentOut, pX, pZ, pW, pH);
        if (err != 0) {
            return err;
        }

        int newW = pW * 2;
        int newH = pH * 2;
        int idx;
        int v00, v01, v10, v11;
        int[] buf = new int[newW * newH];

        final long st = startSalt;
        final long ss = startSeed;

        for (int j = 0; j < pH - 1; j++) {
            idx = (j * 2) * newW;

            v00 = parentOut[j * pW];
            v01 = parentOut[(j + 1) * pW];

            for (int i = 0; i < pW - 1; i++, v00 = v10, v01 = v11) {
                int i1 = Math.min(i + 1, pW - 1);
                int j1 = Math.min(j + 1, pH - 1);

                v10 = parentOut[i1 + j * pW];
                v11 = parentOut[i1 + j1 * pW];

                if (v00 == v01 && v00 == v10 && v00 == v11) {
                    buf[idx] = v00;
                    buf[idx + 1] = v00;
                    buf[idx + newW] = v00;
                    buf[idx + newW + 1] = v00;
                    idx += 2;
                    continue;
                }

                int chunkX = (i + pX) * 2;
                int chunkZ = (j + pZ) * 2;

                long cs = ss;
                cs += chunkX;
                cs *= cs * 6364136223846793005L + 1442695040888963407L;
                cs += chunkZ;
                cs *= cs * 6364136223846793005L + 1442695040888963407L;
                cs += chunkX;
                cs *= cs * 6364136223846793005L + 1442695040888963407L;
                cs += chunkZ;

                buf[idx] = v00;
                buf[idx + newW] = ((cs >> 24) & 1) != 0 ? v01 : v00;
                idx++;

                cs *= cs * 6364136223846793005L + 1442695040888963407L;
                cs += st;
                buf[idx] = ((cs >> 24) & 1) != 0 ? v10 : v00;

                cs *= cs * 6364136223846793005L + 1442695040888963407L;
                cs += st;
                int r = (int) ((cs >> 24) & 3);
                buf[idx + newW] = r == 0 ? v00 : r == 1 ? v10 : r == 2 ? v01 : v11;
                idx++;
            }
        }

        for (int j = 0; j < h; j++) {
            int srcIdx = (j + (z & 1)) * newW + (x & 1);

            if (srcIdx >= 0 && srcIdx + w <= buf.length) {
                System.arraycopy(buf, srcIdx, out, j * w, w);
            } else {
                for (int i = 0; i < w; i++) {
                    int srcPos = srcIdx + i;
                    if (srcPos >= 0 && srcPos < buf.length) {
                        out[j * w + i] = buf[srcPos];
                    } else {
                        out[j * w + i] = 0;
                    }
                }
            }
        }

        return 0;
    }
}