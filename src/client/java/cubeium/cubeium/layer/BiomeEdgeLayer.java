package cubeium.cubeium.layer;

public class BiomeEdgeLayer extends Layer {
    private static final int WOODED_BADLANDS_PLATEAU = 39;
    private static final int BADLANDS_PLATEAU = 36;
    private static final int BADLANDS = 37;
    private static final int GIANT_TREE_TAIGA = 32;
    private static final int TAIGA = 5;
    private static final int DESERT = 2;
    private static final int SNOWY_TUNDRA = 12;
    private static final int WOODED_MOUNTAINS = 34;
    private static final int SWAMP = 6;
    private static final int PLAINS = 1;
    private static final int SNOWY_TAIGA = 30;
    private static final int JUNGLE = 21;
    private static final int BAMBOO_JUNGLE = 168;
    private static final int JUNGLE_EDGE = 23;

    public BiomeEdgeLayer(Layer parent) {
        super(1000L);
        this.parent = parent;
    }

    private boolean replaceEdge(int[] out, int idx, int v10, int v21, int v01, int v12, int id, int baseId,
            int edgeId) {
        if (id != baseId)
            return false;

        if (areSimilar(v10, baseId) && areSimilar(v21, baseId) &&
                areSimilar(v01, baseId) && areSimilar(v12, baseId)) {
            out[idx] = id;
        } else {
            out[idx] = edgeId;
        }

        return true;
    }

    private boolean areSimilar(int biome1, int biome2) {
        if (biome1 == biome2)
            return true;
        return false;
    }

    private boolean isAny4(int target, int v1, int v2, int v3, int v4) {
        return v1 == target || v2 == target || v3 == target || v4 == target;
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
                int v11 = buffer[(j + 1) * pW + (i + 1)];
                int v10 = buffer[(j + 0) * pW + (i + 1)];
                int v21 = buffer[(j + 1) * pW + (i + 2)];
                int v01 = buffer[(j + 1) * pW + (i + 0)];
                int v12 = buffer[(j + 2) * pW + (i + 1)];

                if (!replaceEdge(out, i + j * width, v10, v21, v01, v12, v11, WOODED_BADLANDS_PLATEAU, BADLANDS) &&
                        !replaceEdge(out, i + j * width, v10, v21, v01, v12, v11, BADLANDS_PLATEAU, BADLANDS) &&
                        !replaceEdge(out, i + j * width, v10, v21, v01, v12, v11, GIANT_TREE_TAIGA, TAIGA)) {

                    if (v11 == DESERT) {
                        if (!isAny4(SNOWY_TUNDRA, v10, v21, v01, v12)) {
                            out[i + j * width] = v11;
                        } else {
                            out[i + j * width] = WOODED_MOUNTAINS;
                        }
                    } else if (v11 == SWAMP) {
                        if (!isAny4(DESERT, v10, v21, v01, v12) &&
                                !isAny4(SNOWY_TAIGA, v10, v21, v01, v12) &&
                                !isAny4(SNOWY_TUNDRA, v10, v21, v01, v12)) {
                            if (!isAny4(JUNGLE, v10, v21, v01, v12) &&
                                    !isAny4(BAMBOO_JUNGLE, v10, v21, v01, v12)) {
                                out[i + j * width] = v11;
                            } else {
                                out[i + j * width] = JUNGLE_EDGE;
                            }
                        } else {
                            out[i + j * width] = PLAINS;
                        }
                    } else {
                        out[i + j * width] = v11;
                    }
                }
            }
        }

        return 0;
    }
}