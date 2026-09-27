package cubeium.cubeium.world.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cubeium.cubeium.util.RenderMetrics;
import cubeium.cubeium.world.CubiomesInterface;

/**
 * Overworld biome sampling for the seed map.
 *
 * <p>Calls are stateless with respect to the seed: every call names the seed it wants, so a
 * seed change can never mix results from two seeds. Native generators are not thread-safe, so
 * each thread lazily gets its own and re-seeds it only when the requested seed changes.
 */
public class BiomeGenerator {
    /**
     * Block Y at which the map samples biomes. Since 1.18 biomes are 3D; sampling high above
     * the terrain yields the surface biome and keeps cave biomes out of the map. This matches
     * cubiomes-viewer's default of y=255.
     */
    public static final int MAP_BIOME_Y = 255;

    /** Blocks per biome cell at the map's sampling scale. */
    public static final int CELL_SIZE = 4;

    private static final int[] BIOME_COLORS = CubiomesInterface.getBiomeColors();

    private final int mcVersion;
    private final int flags;
    private final List<ThreadGenerator> generators = new ArrayList<>();
    private final ThreadLocal<ThreadGenerator> threadGenerator = ThreadLocal.withInitial(this::createThreadGenerator);
    private volatile boolean closed;

    public BiomeGenerator() {
        this(CubiomesInterface.GAME_MC_VERSION, 0);
    }

    public BiomeGenerator(int mcVersion, int flags) {
        this.mcVersion = mcVersion;
        this.flags = flags;
    }

    public int getMcVersion() {
        return mcVersion;
    }

    /** Surface biome id at a block position. */
    public int getBiomeAt(long seed, int blockX, int blockZ) {
        long handle = generatorFor(seed);
        long start = System.nanoTime();
        int biome = CubiomesInterface.getBiomeAt(handle, CELL_SIZE,
                Math.floorDiv(blockX, CELL_SIZE), MAP_BIOME_Y / CELL_SIZE, Math.floorDiv(blockZ, CELL_SIZE));
        RenderMetrics.get().recordJniCallNanos(System.nanoTime() - start);
        return biome;
    }

    /**
     * Surface biome ids for a block-aligned area, one id per {@link #CELL_SIZE}x{@link #CELL_SIZE}
     * cell, row-major. {@code blockX}, {@code blockZ}, {@code widthBlocks} and {@code heightBlocks}
     * must be multiples of {@link #CELL_SIZE}.
     */
    public int[] generateCells(long seed, int blockX, int blockZ, int widthBlocks, int heightBlocks) {
        if (((blockX | blockZ | widthBlocks | heightBlocks) & (CELL_SIZE - 1)) != 0) {
            throw new IllegalArgumentException("area must be aligned to " + CELL_SIZE + " blocks");
        }
        long handle = generatorFor(seed);
        long start = System.nanoTime();
        int[] cells = CubiomesInterface.genBiomes(handle, CELL_SIZE,
                blockX / CELL_SIZE, blockZ / CELL_SIZE, MAP_BIOME_Y / CELL_SIZE,
                widthBlocks / CELL_SIZE, heightBlocks / CELL_SIZE);
        RenderMetrics.get().recordJniCallNanos(System.nanoTime() - start);
        return cells;
    }

    /** True if the biome id generates in this version's Overworld. */
    public boolean isOverworldBiome(int biomeId) {
        return CubiomesInterface.isOverworldBiome(mcVersion, biomeId);
    }

    /** Human-readable biome name for this version, e.g. "Windswept Hills". */
    public String getBiomeName(int biomeId) {
        String id = CubiomesInterface.getBiomeName(mcVersion, biomeId);
        if (id == null || id.isBlank()) {
            return "Unknown Biome (" + biomeId + ")";
        }

        StringBuilder name = new StringBuilder(id.length());
        for (String word : id.split("_")) {
            if (word.isEmpty()) continue;
            if (!name.isEmpty()) name.append(' ');
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return name.toString();
    }

    /** Map color (0xAARRGGBB) for a biome id, using cubiomes' palette. */
    public static int getBiomeColor(int biomeId) {
        return biomeId >= 0 && biomeId < BIOME_COLORS.length ? BIOME_COLORS[biomeId] : 0xFF000000;
    }

    /**
     * Frees all native generators. Callers must ensure no other thread is still generating.
     */
    public void cleanup() {
        closed = true;
        synchronized (generators) {
            for (ThreadGenerator generator : generators) {
                CubiomesInterface.freeGenerator(generator.handle);
            }
            generators.clear();
        }
    }

    private long generatorFor(long seed) {
        if (closed) {
            throw new IllegalStateException("BiomeGenerator has been cleaned up");
        }
        ThreadGenerator generator = threadGenerator.get();
        if (!generator.seeded || generator.seed != seed) {
            CubiomesInterface.applySeed(generator.handle, CubiomesInterface.DIM_OVERWORLD, seed);
            generator.seed = seed;
            generator.seeded = true;
        }
        return generator.handle;
    }

    private ThreadGenerator createThreadGenerator() {
        ThreadGenerator generator = new ThreadGenerator(CubiomesInterface.setupGenerator(mcVersion, flags));
        synchronized (generators) {
            generators.add(generator);
        }
        return generator;
    }

    private static final class ThreadGenerator {
        final long handle;
        long seed;
        boolean seeded;

        ThreadGenerator(long handle) {
            this.handle = handle;
        }
    }
}
