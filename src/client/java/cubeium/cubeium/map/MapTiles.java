package cubeium.cubeium.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntUnaryOperator;

import com.mojang.blaze3d.platform.NativeImage;

import cubeium.cubeium.Cubeium;
import cubeium.cubeium.world.WorldGenerator;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Biome map tiles for one {@link WorldGenerator}: generated off-thread, uploaded as textures on
 * the render thread and drawn with a level-of-detail fallback.
 *
 * <p>A tile is {@value #TILE_TEXELS}x{@value #TILE_TEXELS} texels at one cubiomes scale (4, 16, 64
 * or 256 blocks per texel). A tile that is not ready yet is drawn from its nearest ready ancestor
 * at a coarser scale, and the coarsest scale is always requested for the whole view, so the map
 * never shows holes and detail only ever improves: nothing is drawn transparent or flickers.
 */
public final class MapTiles implements AutoCloseable {
    public static final int TILE_TEXELS = 128;
    private static final int[] SCALES = {4, 16, 64, 256};
    private static final int BASE_SCALE = 256;
    private static final int MAX_TEXTURES = 768;
    private static final int UPLOADS_PER_FRAME = 24;
    private static final int PLACEHOLDER = 0xFF1B1B1B;

    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final ExecutorService WORKERS = createWorkers();

    private final WorldGenerator world;
    private final Map<TileKey, Tile> tiles = new ConcurrentHashMap<>();
    private final Set<TileKey> pending = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<Finished> finished = new ConcurrentLinkedQueue<>();
    private volatile Set<TileKey> wanted = Set.of();
    private volatile IntUnaryOperator palette;
    private volatile int paletteVersion;
    private volatile boolean closed;
    private long frame;

    private final AtomicLong generatedTiles = new AtomicLong();
    private final AtomicLong generationNanos = new AtomicLong();

    public MapTiles(WorldGenerator world, IntUnaryOperator palette) {
        this.world = world;
        this.palette = palette;
    }

    public WorldGenerator world() {
        return world;
    }

    /** Changes how biome ids map to colors; existing tiles are recolored in the background. */
    public void setPalette(IntUnaryOperator palette) {
        this.palette = palette;
        paletteVersion++;
    }

    /** Draws the view into the GUI rectangle. Render thread only. */
    public void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, MapView view) {
        frame++;
        uploadFinished();

        double bpp = view.blocksPerPixel();
        int scale = scaleFor(bpp);
        double left = view.centerX() - width / 2.0 * bpp;
        double top = view.centerZ() - height / 2.0 * bpp;
        double right = left + width * bpp;
        double bottom = top + height * bpp;

        List<TileKey> visible = tilesIn(scale, left, top, right, bottom);
        Set<TileKey> nowWanted = new HashSet<>(visible);
        nowWanted.addAll(tilesIn(BASE_SCALE, left, top, right, bottom));
        wanted = nowWanted;
        double cx = view.centerX(), cz = view.centerZ();
        for (TileKey key : nowWanted) {
            request(key, key.distanceSq(cx, cz));
        }

        graphics.enableScissor(x, y, x + width, y + height);
        for (TileKey key : visible) {
            drawTile(graphics, key, x, y, left, top, bpp);
        }
        graphics.disableScissor();
        evict();
    }

    private void drawTile(GuiGraphicsExtractor graphics, TileKey key, int x, int y, double left, double top, double bpp) {
        int sx0 = screen(x, key.minX(), left, bpp), sx1 = screen(x, key.maxX(), left, bpp);
        int sy0 = screen(y, key.minZ(), top, bpp), sy1 = screen(y, key.maxZ(), top, bpp);
        if (sx0 >= sx1 || sy0 >= sy1) {
            return;
        }

        Tile tile = tiles.get(key);
        if (tile != null && tile.texture != null) {
            tile.lastUsed = frame;
            blit(graphics, tile, sx0, sy0, sx1, sy1, 0, 0, 1, 1);
            return;
        }

        // Not ready: draw the matching part of the nearest ready coarser tile.
        for (int coarser : SCALES) {
            if (coarser <= key.scale()) {
                continue;
            }
            TileKey ancestor = TileKey.containing(coarser, key.minX(), key.minZ());
            Tile fallback = tiles.get(ancestor);
            if (fallback != null && fallback.texture != null) {
                fallback.lastUsed = frame;
                double span = ancestor.blocks();
                float u0 = (float) ((key.minX() - ancestor.minX()) / span), u1 = (float) ((key.maxX() - ancestor.minX()) / span);
                float v0 = (float) ((key.minZ() - ancestor.minZ()) / span), v1 = (float) ((key.maxZ() - ancestor.minZ()) / span);
                blit(graphics, fallback, sx0, sy0, sx1, sy1, u0, v0, u1, v1);
                return;
            }
        }
        graphics.fill(sx0, sy0, sx1, sy1, PLACEHOLDER);
    }

    private static void blit(GuiGraphicsExtractor graphics, Tile tile, int x0, int y0, int x1, int y1, float u0, float v0, float u1, float v1) {
        graphics.blit(tile.texture.getTextureView(), tile.texture.getSampler(), x0, y0, x1, y1, u0, u1, v0, v1);
    }

    /** Screen coordinate of a world coordinate; shared tile edges round identically, so no seams. */
    private static int screen(int origin, long world, double viewStart, double bpp) {
        return origin + (int) Math.floor((world - viewStart) / bpp + 0.5);
    }

    /** Biome id at a block from the finest ready tile, or -1 when none covers it yet. */
    public int cachedBiomeAt(int blockX, int blockZ) {
        for (int scale : SCALES) {
            Tile tile = tiles.get(TileKey.containing(scale, blockX, blockZ));
            if (tile != null && tile.ids != null) {
                TileKey key = tile.key;
                int tx = (int) ((blockX - key.minX()) / scale), tz = (int) ((blockZ - key.minZ()) / scale);
                return tile.ids[tz * TILE_TEXELS + tx];
            }
        }
        return -1;
    }

    public int textureCount() {
        return (int) tiles.values().stream().filter(t -> t.texture != null).count();
    }

    public int pendingCount() {
        return pending.size();
    }

    /** Average generation time per tile in milliseconds. */
    public double averageGenerationMillis() {
        long count = generatedTiles.get();
        return count == 0 ? 0 : generationNanos.get() / 1e6 / count;
    }

    /** The cubiomes scale used at a zoom: the coarsest whose texels are not larger than a pixel (min 4). */
    static int scaleFor(double blocksPerPixel) {
        int best = SCALES[0];
        for (int scale : SCALES) {
            if (scale <= blocksPerPixel) {
                best = scale;
            }
        }
        return best;
    }

    private static List<TileKey> tilesIn(int scale, double left, double top, double right, double bottom) {
        long blocks = (long) TILE_TEXELS * scale;
        long tx0 = Math.floorDiv((long) Math.floor(left), blocks), tx1 = Math.floorDiv((long) Math.ceil(right) - 1, blocks);
        long tz0 = Math.floorDiv((long) Math.floor(top), blocks), tz1 = Math.floorDiv((long) Math.ceil(bottom) - 1, blocks);
        List<TileKey> keys = new ArrayList<>();
        for (long tz = tz0; tz <= tz1; tz++) {
            for (long tx = tx0; tx <= tx1; tx++) {
                keys.add(new TileKey(scale, (int) tx, (int) tz));
            }
        }
        return keys;
    }

    private void request(TileKey key, double priority) {
        Tile tile = tiles.get(key);
        boolean upToDate = tile != null && tile.paletteVersion == paletteVersion;
        if (upToDate || !pending.add(key)) {
            return;
        }
        int[] ids = tile != null ? tile.ids : null;
        WORKERS.execute(new Job(this, key, ids, priority));
    }

    /** Runs on a worker thread. */
    private void generate(TileKey key, int[] knownIds) {
        try {
            if (closed || !wanted.contains(key)) {
                pending.remove(key); // scrolled out of view before we got to it; request again later
                return;
            }
            int version = paletteVersion;
            IntUnaryOperator colors = palette;
            int[] ids = knownIds;
            if (ids == null) {
                long start = System.nanoTime();
                ids = world.biomes(key.scale(), key.tx() * TILE_TEXELS, key.tz() * TILE_TEXELS, TILE_TEXELS, TILE_TEXELS);
                generationNanos.addAndGet(System.nanoTime() - start);
                generatedTiles.incrementAndGet();
            }
            NativeImage image = new NativeImage(TILE_TEXELS, TILE_TEXELS, false);
            for (int tz = 0; tz < TILE_TEXELS; tz++) {
                for (int tx = 0; tx < TILE_TEXELS; tx++) {
                    image.setPixel(tx, tz, colors.applyAsInt(ids[tz * TILE_TEXELS + tx]));
                }
            }
            finished.add(new Finished(key, ids, image, version));
        } catch (RuntimeException e) {
            Cubeium.LOGGER.warn("Failed to generate map tile {}", key, e);
            pending.remove(key);
        }
    }

    private void uploadFinished() {
        for (int i = 0; i < UPLOADS_PER_FRAME; i++) {
            Finished done = finished.poll();
            if (done == null) {
                return;
            }
            pending.remove(done.key);
            if (closed) {
                done.image.close();
                continue;
            }
            Tile tile = tiles.computeIfAbsent(done.key, Tile::new);
            if (tile.texture != null) {
                tile.texture.close();
            }
            tile.texture = new DynamicTexture(() -> "cubeium map tile " + done.key, done.image);
            tile.ids = done.ids;
            tile.paletteVersion = done.paletteVersion;
            tile.lastUsed = frame;
        }
    }

    private void evict() {
        if (tiles.size() <= MAX_TEXTURES) {
            return;
        }
        List<Tile> candidates = new ArrayList<>(tiles.values());
        candidates.removeIf(t -> t.lastUsed >= frame);
        candidates.sort(Comparator.comparingLong(t -> t.lastUsed));
        for (int i = 0; i < candidates.size() && tiles.size() > MAX_TEXTURES * 3 / 4; i++) {
            Tile tile = candidates.get(i);
            tiles.remove(tile.key);
            if (tile.texture != null) {
                tile.texture.close();
            }
        }
    }

    /** Releases all textures. Render thread only. */
    @Override
    public void close() {
        closed = true;
        wanted = Set.of();
        for (Tile tile : tiles.values()) {
            if (tile.texture != null) {
                tile.texture.close();
            }
        }
        tiles.clear();
        Finished done;
        while ((done = finished.poll()) != null) {
            done.image.close();
        }
    }

    private static ExecutorService createWorkers() {
        int threads = Math.clamp(Runtime.getRuntime().availableProcessors() - 2, 1, 6);
        return new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS, new PriorityBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable, "Cubeium map worker " + THREAD_ID.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
    }

    /** A tile: scale in blocks per texel, tile coordinates in units of TILE_TEXELS * scale blocks. */
    record TileKey(int scale, int tx, int tz) {
        static TileKey containing(int scale, long blockX, long blockZ) {
            long blocks = (long) TILE_TEXELS * scale;
            return new TileKey(scale, (int) Math.floorDiv(blockX, blocks), (int) Math.floorDiv(blockZ, blocks));
        }

        long blocks() {
            return (long) TILE_TEXELS * scale;
        }

        long minX() {
            return tx * blocks();
        }

        long maxX() {
            return minX() + blocks();
        }

        long minZ() {
            return tz * blocks();
        }

        long maxZ() {
            return minZ() + blocks();
        }

        double distanceSq(double x, double z) {
            double dx = minX() + blocks() / 2.0 - x, dz = minZ() + blocks() / 2.0 - z;
            // Coarse tiles first (they fill the view fastest), then nearest first.
            return (dx * dx + dz * dz) / blocks() / blocks() + (scale == BASE_SCALE ? -1e9 : 0);
        }
    }

    private static final class Tile {
        final TileKey key;
        volatile int[] ids;
        DynamicTexture texture;
        int paletteVersion = -1;
        long lastUsed;

        Tile(TileKey key) {
            this.key = key;
        }
    }

    private record Finished(TileKey key, int[] ids, NativeImage image, int paletteVersion) {
    }

    private record Job(MapTiles owner, TileKey key, int[] ids, double priority) implements Runnable, Comparable<Job> {
        @Override
        public void run() {
            owner.generate(key, ids);
        }

        @Override
        public int compareTo(Job other) {
            return Double.compare(priority, other.priority);
        }
    }
}
