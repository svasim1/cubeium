package cubeium.cubeium.world;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Arrays;

import dev.xpple.cubiomes.Cubiomes;
import dev.xpple.cubiomes.Generator;
import dev.xpple.cubiomes.Piece;
import dev.xpple.cubiomes.Pos;
import dev.xpple.cubiomes.Range;
import dev.xpple.cubiomes.StrongholdIter;
import dev.xpple.cubiomes.SurfaceNoise;

/**
 * cubiomes world generation for one seed, dimension and Minecraft version.
 *
 * <p>Instances are immutable and safe to share between threads: a native cubiomes generator is
 * not thread-safe (some calls temporarily modify it), so every thread lazily gets its own. A seed
 * or dimension change creates a new instance, so results for different seeds can never mix.
 */
public final class WorldGenerator {
    /**
     * Block Y at which biomes are sampled for the map. Since 1.18 biomes are 3D; sampling above the
     * terrain yields the surface biome and keeps cave biomes off the map (cubiomes-viewer's default).
     */
    public static final int MAP_BIOME_Y = 255;

    static {
        NativeLibrary.load();
    }

    private final int mcVersion;
    private final long seed;
    private final Dimension dimension;
    private final ThreadLocal<MemorySegment> generator = ThreadLocal.withInitial(this::createGenerator);
    private final ThreadLocal<MemorySegment> endSurface = ThreadLocal.withInitial(this::createEndSurface);

    public WorldGenerator(int mcVersion, long seed, Dimension dimension) {
        this.mcVersion = mcVersion;
        this.seed = seed;
        this.dimension = dimension;
    }

    public int mcVersion() {
        return mcVersion;
    }

    public long seed() {
        return seed;
    }

    public Dimension dimension() {
        return dimension;
    }

    /**
     * Surface biome ids for a {@code width x height} area, row-major (z outer, x inner). {@code x}
     * and {@code z} are in units of {@code scale} blocks (1, 4, 16, 64 or 256).
     */
    public int[] biomes(int scale, int x, int z, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("empty area");
        }
        MemorySegment g = generator.get();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment range = Range.allocate(arena);
            Range.scale(range, scale);
            Range.x(range, x);
            Range.z(range, z);
            Range.sx(range, width);
            Range.sz(range, height);
            Range.y(range, scale == 1 ? MAP_BIOME_Y : MAP_BIOME_Y >> 2);
            Range.sy(range, 1);

            long cacheInts = Cubiomes.getMinCacheSize(g, scale, width, 1, height);
            MemorySegment cache = arena.allocate(ValueLayout.JAVA_INT, cacheInts);
            int err = Cubiomes.genBiomes(g, cache, range);
            if (err != 0) {
                throw new IllegalStateException("cubiomes genBiomes failed (" + err + ") at scale " + scale);
            }
            return Arrays.copyOf(cache.toArray(ValueLayout.JAVA_INT), width * height);
        }
    }

    /** Surface biome id at a block position. */
    public int biomeAt(int blockX, int blockZ) {
        return Cubiomes.getBiomeAt(generator.get(), 4, blockX >> 2, MAP_BIOME_Y >> 2, blockZ >> 2);
    }

    /** Biome id at an exact block position and height (scale 1). */
    public int biomeAt(int blockX, int blockY, int blockZ) {
        return Cubiomes.getBiomeAt(generator.get(), 1, blockX, blockY, blockZ);
    }

    /**
     * Whether a structure generation attempt at a block position passes the biome (and, where
     * cubiomes can tell, terrain) checks. The per-thread generator is only borrowed: cubiomes
     * restores it after the call.
     */
    boolean isViableStructure(int structureType, int blockX, int blockZ) {
        MemorySegment g = generator.get();
        if (Cubiomes.isViableStructurePos(structureType, g, blockX, blockZ, 0) == 0) {
            return false;
        }
        if (dimension == Dimension.OVERWORLD) {
            return Cubiomes.isViableStructureTerrain(structureType, g, blockX, blockZ) != 0;
        }
        return true;
    }

    /** Whether the end city starting in this block's chunk has a ship. */
    boolean endCityHasShip(int blockX, int blockZ) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pieces = Piece.allocateArray(Cubiomes.END_CITY_PIECES_MAX(), arena);
            int count = Cubiomes.getEndCityPieces(pieces, seed, blockX >> 4, blockZ >> 4);
            for (int i = 0; i < count; i++) {
                if (Piece.type(pieces.asSlice(i * Piece.sizeof(), Piece.sizeof())) == Cubiomes.END_SHIP()) {
                    return true;
                }
            }
            return false;
        }
    }

    /** End cities additionally need a sufficiently high island surface. */
    boolean isViableEndCity(int blockX, int blockZ) {
        MemorySegment g = generator.get();
        if (Cubiomes.isViableStructurePos(Cubiomes.End_City(), g, blockX, blockZ, 0) == 0) {
            return false;
        }
        return Cubiomes.isViableEndCityTerrain(g, endSurface.get(), blockX, blockZ) != 0;
    }

    /** Accurate stronghold positions {x0, z0, x1, z1, ...}, nearest ring first (Overworld only). */
    public int[] strongholds(int maxCount) {
        if (dimension != Dimension.OVERWORLD) {
            return new int[0];
        }
        MemorySegment g = generator.get();
        int[] out = new int[maxCount * 2];
        int count = 0;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment iter = StrongholdIter.allocate(arena);
            Cubiomes.initFirstStronghold(arena, iter, mcVersion, seed);
            while (count < maxCount) {
                // nextStronghold resolves the current stronghold's accurate position into iter.pos
                // and returns how many strongholds follow it.
                int remaining = Cubiomes.nextStronghold(iter, g);
                MemorySegment pos = StrongholdIter.pos(iter);
                out[count * 2] = Pos.x(pos);
                out[count * 2 + 1] = Pos.z(pos);
                count++;
                if (remaining <= 0) {
                    break;
                }
            }
        }
        return Arrays.copyOf(out, count * 2);
    }

    private MemorySegment createGenerator() {
        // Arena.ofAuto: freed by the GC once this thread and this WorldGenerator are gone.
        MemorySegment g = Generator.allocate(Arena.ofAuto());
        Cubiomes.setupGenerator(g, mcVersion, 0);
        Cubiomes.applySeed(g, dimension.cubiomesId(), seed);
        return g;
    }

    private MemorySegment createEndSurface() {
        MemorySegment surface = SurfaceNoise.allocate(Arena.ofAuto());
        Cubiomes.initSurfaceNoise(surface, Cubiomes.DIM_END(), seed);
        return surface;
    }
}
