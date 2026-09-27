package cubeium.cubeium.screen;

import java.util.Set;
import java.util.function.IntUnaryOperator;

import cubeium.cubeium.config.CubeiumConfig;
import cubeium.cubeium.map.MapStructures;
import cubeium.cubeium.map.MapTiles;
import cubeium.cubeium.map.MapView;
import cubeium.cubeium.world.Biomes;
import cubeium.cubeium.world.Dimension;
import cubeium.cubeium.world.StructureFinder;
import cubeium.cubeium.world.WorldGenerator;
import dev.xpple.cubiomes.Cubiomes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.jspecify.annotations.Nullable;

/**
 * Map state that outlives the screen: the current world (seed + dimension), its tiles and structure
 * search, and the view. Reopening the map is instant because tiles stay cached. Render thread only.
 */
final class MapSession {
    /** The cubiomes version matching the Minecraft version this build of the mod targets. */
    static final int MC_VERSION = Cubiomes.MC_26_3();

    private static @Nullable MapSession instance;

    final MapView view = new MapView();
    Dimension dimension = Dimension.OVERWORLD;
    /** Which world the session was last opened in; the view is reset when it changes. */
    @Nullable String worldKey;
    private @Nullable Long seed;
    private @Nullable MapTiles tiles;
    private @Nullable MapStructures structures;
    /** Result of the last "find nearest", shown on the map until the world or dimension changes. */
    @Nullable FoundTarget found;
    boolean searching;

    record FoundTarget(Component label, int x, int z, int distance) {
    }

    static MapSession get() {
        if (instance == null) {
            instance = new MapSession();
        }
        return instance;
    }

    /** Switches to a seed/dimension; keeps everything cached when nothing changed. */
    void show(@Nullable Long seed, Dimension dimension) {
        this.dimension = dimension;
        if (seed == null) {
            clear();
            return;
        }
        if (tiles != null && seed.equals(this.seed) && tiles.world().dimension() == dimension) {
            return;
        }
        clear();
        this.seed = seed;
        WorldGenerator world = new WorldGenerator(MC_VERSION, seed, dimension);
        tiles = new MapTiles(world, palette());
        structures = new MapStructures(new StructureFinder(world));
    }

    @Nullable Long seed() {
        return seed;
    }

    @Nullable MapTiles tiles() {
        return tiles;
    }

    @Nullable MapStructures structures() {
        return structures;
    }

    /** Re-applies the biome highlight to all tiles. */
    void refreshPalette() {
        if (tiles != null) {
            tiles.setPalette(palette());
        }
    }

    private static IntUnaryOperator palette() {
        CubeiumConfig config = CubeiumConfig.get();
        if (!config.highlightBiomes) {
            return Biomes::color;
        }
        Set<Integer> selected = Set.copyOf(config.highlightedBiomes);
        return id -> selected.contains(id) ? Biomes.color(id) : dimmed(Biomes.color(id));
    }

    /** Non-highlighted biomes: desaturated and dark, but still recognisable. */
    private static int dimmed(int color) {
        int gray = (ARGB.red(color) * 30 + ARGB.green(color) * 59 + ARGB.blue(color) * 11) / 100;
        int value = 24 + gray / 6;
        return ARGB.color(255, value, value, value);
    }

    private void clear() {
        if (tiles != null) {
            tiles.close();
        }
        tiles = null;
        structures = null;
        seed = null;
        found = null;
        searching = false;
    }
}
