package cubeium.cubeium.layer;

public class ContinentLayer extends Layer {
    public ContinentLayer() {
        super(0);
    }

    @Override
    public int getMap(int[] out, int x, int z, int w, int h) {
        long ss = startSeed;
        long cs;

        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                cs = getChunkSeed(ss, i + x, j + z);
                out[j * w + i] = mcFirstIsZero(cs, 10) ? 1 : 0;
            }
        }

        // Ensure the origin chunk is land
        if (x > -w && x <= 0 && z > -h && z <= 0) {
            out[-z * w - x] = 1;
        }

        return 0;
    }
}