package cubeium.cubeium.layer;

public class OceanMixLayer extends Layer {
    private static final int WARM_OCEAN = 44;
    private static final int LUKEWARM_OCEAN = 45;
    private static final int OCEAN = 0;
    private static final int COLD_OCEAN = 46;
    private static final int FROZEN_OCEAN = 10;
    private static final int DEEP_OCEAN = 24;
    private static final int DEEP_LUKEWARM_OCEAN = 48;
    private static final int DEEP_COLD_OCEAN = 49;
    private static final int DEEP_FROZEN_OCEAN = 50;

    private final Layer oceanLayer;

    public OceanMixLayer(Layer parent, Layer oceanLayer) {
        super(1000L); // Using a constant salt value like other layers
        this.parent = parent;
        this.oceanLayer = oceanLayer;
    }

    private boolean isOceanic(int biome) {
        return biome == OCEAN || biome == DEEP_OCEAN || biome == FROZEN_OCEAN || biome == DEEP_FROZEN_OCEAN ||
                biome == COLD_OCEAN || biome == DEEP_COLD_OCEAN || biome == LUKEWARM_OCEAN
                || biome == DEEP_LUKEWARM_OCEAN ||
                biome == WARM_OCEAN;
    }

    @Override
    public int getMap(int[] out, int x, int z, int width, int height) {
        // Get the ocean temperature map
        int[] oceanMap = new int[width * height];
        oceanLayer.getMap(oceanMap, x, z, width, height);

        // Get the biome map
        parent.getMap(out, x, z, width, height);

        // Mix the ocean temperatures with the biomes
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int idx = i + j * width;
                int landId = out[idx];
                int oceanId = oceanMap[idx];

                if (!isOceanic(landId)) {
                    continue;
                }

                // Replace warm and frozen oceans near land with their temperate variants
                if (oceanId == WARM_OCEAN || oceanId == FROZEN_OCEAN) {
                    boolean nearLand = false;
                    for (int dy = -8; dy <= 8 && !nearLand; dy += 4) {
                        for (int dx = -8; dx <= 8 && !nearLand; dx += 4) {
                            int nx = i + dx;
                            int nz = j + dy;
                            if (nx >= 0 && nx < width && nz >= 0 && nz < height) {
                                if (!isOceanic(out[nx + nz * width])) {
                                    nearLand = true;
                                    break;
                                }
                            }
                        }
                    }
                    if (nearLand) {
                        oceanId = oceanId == WARM_OCEAN ? LUKEWARM_OCEAN : COLD_OCEAN;
                    }
                }

                // Convert to deep ocean variants if the biome is deep ocean
                if (landId == DEEP_OCEAN) {
                    switch (oceanId) {
                        case LUKEWARM_OCEAN:
                            oceanId = DEEP_LUKEWARM_OCEAN;
                            break;
                        case OCEAN:
                            oceanId = DEEP_OCEAN;
                            break;
                        case COLD_OCEAN:
                            oceanId = DEEP_COLD_OCEAN;
                            break;
                        case FROZEN_OCEAN:
                            oceanId = DEEP_FROZEN_OCEAN;
                            break;
                    }
                }

                out[idx] = oceanId;
            }
        }

        return 0;
    }
}