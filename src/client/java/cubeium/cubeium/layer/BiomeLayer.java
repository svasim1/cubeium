package cubeium.cubeium.layer;

public class BiomeLayer extends Layer {
    private static final int OCEAN = 0;
    private static final int MUSHROOM_FIELDS = 14;
    private static final int WARM = 3;
    private static final int LUSH = 4;
    private static final int COLD = 2;
    private static final int FREEZING = 1;

    // Biome IDs
    private static final int DESERT = 2;
    private static final int SAVANNA = 35;
    private static final int PLAINS = 1;
    private static final int FOREST = 4;
    private static final int DARK_FOREST = 29;
    private static final int MOUNTAINS = 3;
    private static final int BIRCH_FOREST = 27;
    private static final int SWAMP = 6;
    private static final int TAIGA = 5;
    private static final int SNOWY_TUNDRA = 12;
    private static final int SNOWY_TAIGA = 30;
    private static final int JUNGLE = 21;
    private static final int BADLANDS_PLATEAU = 39;
    private static final int WOODED_BADLANDS_PLATEAU = 38;
    private static final int GIANT_TREE_TAIGA = 32;

    private static final int[] WARM_BIOMES = { DESERT, DESERT, DESERT, SAVANNA, SAVANNA, PLAINS };
    private static final int[] LUSH_BIOMES = { FOREST, DARK_FOREST, MOUNTAINS, PLAINS, BIRCH_FOREST, SWAMP };
    private static final int[] COLD_BIOMES = { FOREST, MOUNTAINS, TAIGA, PLAINS };
    private static final int[] SNOW_BIOMES = { SNOWY_TUNDRA, SNOWY_TUNDRA, SNOWY_TUNDRA, SNOWY_TAIGA };

    public BiomeLayer(Layer parent) {
        super(0);
        this.parent = parent;
    }

    @Override
    public int getMap(int[] out, int x, int z, int w, int h) {
        int err = parent.getMap(out, x, z, w, h);
        if (err != 0) {
            return err;
        }

        long ss = startSeed;
        long cs;

        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int idx = i + j * w;
                int id = out[idx];
                int v;
                int hasHighBit = (id & 0xf00);
                id &= ~0xf00;

                if (isOceanic(id) || id == MUSHROOM_FIELDS) {
                    out[idx] = id;
                    continue;
                }

                cs = getChunkSeed(ss, i + x, j + z);

                switch (id) {
                    case WARM:
                        if (hasHighBit != 0) {
                            v = mcFirstIsZero(cs, 3) ? BADLANDS_PLATEAU : WOODED_BADLANDS_PLATEAU;
                        } else {
                            v = WARM_BIOMES[mcFirstInt(cs, 6)];
                        }
                        break;
                    case LUSH:
                        if (hasHighBit != 0) {
                            v = JUNGLE;
                        } else {
                            v = LUSH_BIOMES[mcFirstInt(cs, 6)];
                        }
                        break;
                    case COLD:
                        if (hasHighBit != 0) {
                            v = GIANT_TREE_TAIGA;
                        } else {
                            v = COLD_BIOMES[mcFirstInt(cs, 4)];
                        }
                        break;
                    case FREEZING:
                        v = SNOW_BIOMES[mcFirstInt(cs, 4)];
                        break;
                    default:
                        v = MUSHROOM_FIELDS;
                }

                out[idx] = v;
            }
        }

        return 0;
    }

    private boolean isOceanic(int id) {
        return id == OCEAN;
    }
}