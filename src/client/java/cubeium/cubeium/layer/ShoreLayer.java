package cubeium.cubeium.layer;

public class ShoreLayer extends Layer {
    private static final int MUSHROOM_FIELDS = 14;
    private static final int MUSHROOM_FIELD_SHORE = 15;
    private static final int OCEAN = 0;
    private static final int MOUNTAINS = 3;
    private static final int MOUNTAIN_EDGE = 20;
    private static final int RIVER = 7;
    private static final int SWAMP = 6;
    private static final int BEACH = 16;
    private static final int JUNGLE = 21;
    private static final int JUNGLE_EDGE = 23;
    private static final int WOODED_MOUNTAINS = 34;
    private static final int STONE_SHORE = 25;
    private static final int SNOWY_BEACH = 26;
    private static final int BADLANDS = 37;
    private static final int WOODED_BADLANDS_PLATEAU = 39;
    private static final int DESERT = 2;
    private static final int DEEP_OCEAN = 24;
    private static final int FOREST = 4;
    private static final int TAIGA = 5;

    public ShoreLayer(Layer parent) {
        super(1000L); // Using a constant salt value like other layers
        this.parent = parent;
    }

    private boolean isOceanic(int biome) {
        return biome == OCEAN || biome == DEEP_OCEAN;
    }

    private boolean isAny4(int target, int v1, int v2, int v3, int v4) {
        return v1 == target || v2 == target || v3 == target || v4 == target;
    }

    private boolean isAny4Oceanic(int v1, int v2, int v3, int v4) {
        return isOceanic(v1) || isOceanic(v2) || isOceanic(v3) || isOceanic(v4);
    }

    private boolean isAll4JFTO(int v1, int v2, int v3, int v4) {
        return (isJungleOrForestOrTaigaOrOcean(v1) &&
                isJungleOrForestOrTaigaOrOcean(v2) &&
                isJungleOrForestOrTaigaOrOcean(v3) &&
                isJungleOrForestOrTaigaOrOcean(v4));
    }

    private boolean isJungleOrForestOrTaigaOrOcean(int biome) {
        return isJungleCategory(biome) || biome == FOREST || biome == TAIGA || isOceanic(biome);
    }

    private boolean isJungleCategory(int biome) {
        return biome == JUNGLE;
    }

    private boolean isSnowy(int biome) {
        // Add more snowy biomes if needed
        return false;
    }

    private boolean isMesa(int biome) {
        return biome == BADLANDS || biome == WOODED_BADLANDS_PLATEAU;
    }

    private boolean replaceOcean(int[] out, int idx, int v10, int v21, int v01, int v12, int id, int replaceId) {
        if (isOceanic(id))
            return false;

        if (isOceanic(v10) || isOceanic(v21) || isOceanic(v01) || isOceanic(v12)) {
            out[idx] = replaceId;
        } else {
            out[idx] = id;
        }

        return true;
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

                if (v11 == MUSHROOM_FIELDS) {
                    if (isAny4(OCEAN, v10, v21, v01, v12)) {
                        out[i + j * width] = MUSHROOM_FIELD_SHORE;
                    } else {
                        out[i + j * width] = v11;
                    }
                    continue;
                }

                if (isJungleCategory(v11)) {
                    if (isAll4JFTO(v10, v21, v01, v12)) {
                        if (isAny4Oceanic(v10, v21, v01, v12)) {
                            out[i + j * width] = BEACH;
                        } else {
                            out[i + j * width] = v11;
                        }
                    } else {
                        out[i + j * width] = JUNGLE_EDGE;
                    }
                } else if (v11 == MOUNTAINS || v11 == WOODED_MOUNTAINS) {
                    replaceOcean(out, i + j * width, v10, v21, v01, v12, v11, STONE_SHORE);
                } else if (isSnowy(v11)) {
                    replaceOcean(out, i + j * width, v10, v21, v01, v12, v11, SNOWY_BEACH);
                } else if (v11 == BADLANDS || v11 == WOODED_BADLANDS_PLATEAU) {
                    if (!isAny4Oceanic(v10, v21, v01, v12)) {
                        if (isMesa(v10) && isMesa(v21) && isMesa(v01) && isMesa(v12)) {
                            out[i + j * width] = v11;
                        } else {
                            out[i + j * width] = DESERT;
                        }
                    } else {
                        out[i + j * width] = v11;
                    }
                } else {
                    if (v11 != OCEAN && v11 != DEEP_OCEAN && v11 != RIVER && v11 != SWAMP) {
                        if (isAny4Oceanic(v10, v21, v01, v12)) {
                            out[i + j * width] = BEACH;
                        } else {
                            out[i + j * width] = v11;
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