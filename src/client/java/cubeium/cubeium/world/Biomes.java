package cubeium.cubeium.world;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

import dev.xpple.cubiomes.Cubiomes;

/** Biome names, colors and per-dimension biome lists from cubiomes. */
public final class Biomes {
    /** Biome ids are below 256 in every supported version. */
    public static final int ID_LIMIT = 256;

    private static final int[] COLORS = loadColors();

    private Biomes() {
    }

    /** cubiomes' map color for a biome id as 0xAARRGGBB; opaque black for unknown ids. */
    public static int color(int biomeId) {
        return biomeId >= 0 && biomeId < ID_LIMIT ? COLORS[biomeId] : 0xFF000000;
    }

    /** Vanilla biome id path for a version, e.g. "windswept_hills", or null for an unknown id. */
    public static String name(int mcVersion, int biomeId) {
        MemorySegment str = Cubiomes.biome2str(mcVersion, biomeId);
        return str.equals(MemorySegment.NULL) ? null : str.reinterpret(Long.MAX_VALUE).getString(0);
    }

    /**
     * Biomes that generate in a dimension for a version, excluding underground-only biomes the
     * map never shows (it samples at {@link WorldGenerator#MAP_BIOME_Y}).
     */
    public static List<Integer> mapBiomes(int mcVersion, Dimension dimension) {
        List<Integer> ids = new ArrayList<>();
        for (int id = 0; id < ID_LIMIT; id++) {
            String name = name(mcVersion, id);
            if (name == null || isUnderground(name)) {
                continue;
            }
            boolean generates = switch (dimension) {
                case OVERWORLD -> Cubiomes.isOverworld(mcVersion, id) != 0;
                case NETHER, END -> Cubiomes.getDimension(id) == dimension.cubiomesId();
            };
            if (generates) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static boolean isUnderground(String name) {
        return name.endsWith("_caves") || name.equals("deep_dark");
    }

    private static int[] loadColors() {
        NativeLibrary.load();
        int[] colors = new int[ID_LIMIT];
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rgb = arena.allocate(ID_LIMIT * 3L);
            Cubiomes.initBiomeColors(rgb);
            for (int i = 0; i < ID_LIMIT; i++) {
                int r = Byte.toUnsignedInt(rgb.get(ValueLayout.JAVA_BYTE, i * 3L));
                int g = Byte.toUnsignedInt(rgb.get(ValueLayout.JAVA_BYTE, i * 3L + 1));
                int b = Byte.toUnsignedInt(rgb.get(ValueLayout.JAVA_BYTE, i * 3L + 2));
                colors[i] = 0xFF000000 | r << 16 | g << 8 | b;
            }
        }
        return colors;
    }
}
