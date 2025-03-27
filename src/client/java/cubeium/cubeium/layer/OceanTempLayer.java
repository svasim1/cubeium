package cubeium.cubeium.layer;

import cubeium.cubeium.noise.PerlinNoise;
import java.util.Random;

public class OceanTempLayer extends Layer {
    private static final int WARM_OCEAN = 44;
    private static final int LUKEWARM_OCEAN = 45;
    private static final int OCEAN = 0;
    private static final int COLD_OCEAN = 46;
    private static final int FROZEN_OCEAN = 10;

    public OceanTempLayer(Layer parent) {
        super(1000L); // Using a constant salt value like other layers
        this.parent = parent;
        this.noise = new PerlinNoise(new Random(), 1, 1.0, 1.0, 1.0);
    }

    @Override
    public int getMap(int[] out, int x, int z, int width, int height) {
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                double temp = ((PerlinNoise) noise).noise((i + x) / 8.0, (j + z) / 8.0, 0);

                if (temp > 0.4) {
                    out[i + j * width] = WARM_OCEAN;
                } else if (temp > 0.2) {
                    out[i + j * width] = LUKEWARM_OCEAN;
                } else if (temp < -0.4) {
                    out[i + j * width] = FROZEN_OCEAN;
                } else if (temp < -0.2) {
                    out[i + j * width] = COLD_OCEAN;
                } else {
                    out[i + j * width] = OCEAN;
                }
            }
        }

        return 0;
    }
}