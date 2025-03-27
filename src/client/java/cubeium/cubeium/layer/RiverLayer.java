package cubeium.cubeium.layer;

public class RiverLayer extends Layer {
    private static final int RIVER = 7;

    public RiverLayer(Layer parent) {
        super(1000L); // Using a constant salt value like other layers
        this.parent = parent;
    }

    private int reduceID(int id) {
        return id >= 2 ? 2 + (id & 1) : id;
    }

    @Override
    public int getMap(int[] out, int x, int z, int width, int height) {
        int pX = x - 1;
        int pZ = z - 1;
        int pW = width + 2;
        int pH = height + 2;

        int[] buffer = new int[pW * pH];
        parent.getMap(buffer, pX, pZ, pW, pH);

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int v01 = buffer[(j + 1) * pW + (i + 0)];
                int v11 = buffer[(j + 1) * pW + (i + 1)];
                int v21 = buffer[(j + 1) * pW + (i + 2)];
                int v10 = buffer[(j + 0) * pW + (i + 1)];
                int v12 = buffer[(j + 2) * pW + (i + 1)];

                // Reduce IDs for MC 1.7+
                v01 = reduceID(v01);
                v11 = reduceID(v11);
                v21 = reduceID(v21);
                v10 = reduceID(v10);
                v12 = reduceID(v12);

                if (v11 == v01 && v11 == v10 && v11 == v12 && v11 == v21) {
                    out[i + j * width] = -1;
                } else {
                    out[i + j * width] = RIVER;
                }
            }
        }

        return 0;
    }
}