package cubeium.cubeium.rendering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import cubeium.cubeium.Cubeium;
import cubeium.cubeium.seedmap.CubeiumSeedMapScreen;
import cubeium.cubeium.util.RenderMetrics;
import cubeium.cubeium.world.MapCache;
import net.minecraft.client.gui.DrawContext;

/**
 * High-performance tile-based map renderer similar to OpenLayers/Google Maps.
 * Uses cached biome data tiles for smooth panning and zooming.
 */
public class MapTileRenderer {
    
    // Tile configuration
    private static final int TILE_SIZE = 128; // 128x128 blocks per tile (reasonable for Minecraft)
    private static final int MAX_CACHED_TILES = 1200; // Keep enough tiles for nearby pan/zoom reuse
    private static final int TARGET_CACHED_TILES = 1050;
    private static final long MAX_TILE_CACHE_BYTES = 96L * 1024L * 1024L;
    private static final long TARGET_TILE_CACHE_BYTES = 88L * 1024L * 1024L;
    private static final int MAX_VISIBLE_TILES_PER_FRAME = 12000; // Safety guard for invalid view math
    private static final int MAX_PENDING_TILE_REQUESTS = 5000; // Global scheduler back-pressure cap
    private static final int MAX_ENQUEUE_PER_FRAME = 300; // Avoid queue spikes on fast camera movement
    private static final long REVISION_GRACE_MILLIS = 220L; // Keep prior revision briefly to smooth transitions
    private static final int[] RESOLUTION_LEVELS = {1, 2, 4, 8, 16, 32, 64};
    
    // Tile cache: stores pre-computed biome data
    private final Map<TileKey, CachedTile> tileCache = new ConcurrentHashMap<>();
    private final Map<TileKey, Long> scheduledTileRevisions = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<TileRequest> requestQueue = new PriorityBlockingQueue<>();
    private final ExecutorService generationExecutor = Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() / 3));
    private final AtomicLong viewportRevision = new AtomicLong();
    private final AtomicLong requestSequence = new AtomicLong();
    private final AtomicLong tileCacheBytes = new AtomicLong();
    private final Object tileEvictionLock = new Object();
    private final MapCache mapCache;
    private volatile ViewportState currentViewport;
    private volatile long previousRevision = -1L;
    private volatile long previousRevisionValidUntilMillis = 0L;
    private volatile long lastClampWarningMillis = 0L;
    private volatile int lastResolutionScale = 1;
    
    public MapTileRenderer(MapCache mapCache) {
        this.mapCache = mapCache;
        int workerCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 3);
        for (int i = 0; i < workerCount; i++) {
            generationExecutor.submit(this::runWorkerLoop);
        }
    }
    
    /**
     * Render the visible map area using tiles.
     * Only renders tiles that are actually visible in viewport.
     */
    public void renderMap(DrawContext context, long seed, 
                         int screenX, int screenY, int screenWidth, int screenHeight,
                         int mapCenterX, int mapCenterZ, int zoomLevel,
                         CubeiumSeedMapScreen.SeedMapSession session) {
        
        // Calculate which tiles are visible
        int blocksPerPixel = zoomLevel;
        int resolutionScale = selectResolutionScale(zoomLevel);
        lastResolutionScale = resolutionScale;
        int tileWorldSize = TILE_SIZE * resolutionScale;
        int viewWidthBlocks = screenWidth * blocksPerPixel;
        int viewHeightBlocks = screenHeight * blocksPerPixel;
        
        // World coordinates of viewport corners
        int worldLeft = mapCenterX - viewWidthBlocks / 2;
        int worldTop = mapCenterZ - viewHeightBlocks / 2;
        int worldRight = worldLeft + viewWidthBlocks;
        int worldBottom = worldTop + viewHeightBlocks;
        
        // Convert to tile coordinates
        int tileLeft = Math.floorDiv(worldLeft, tileWorldSize);
        int tileTop = Math.floorDiv(worldTop, tileWorldSize);
        int tileRight = Math.floorDiv(worldRight, tileWorldSize);
        int tileBottom = Math.floorDiv(worldBottom, tileWorldSize);
        
        int centerTileX = Math.floorDiv(mapCenterX, tileWorldSize);
        int centerTileZ = Math.floorDiv(mapCenterZ, tileWorldSize);
        long visibleTileCount = (long) (tileRight - tileLeft + 1) * (tileBottom - tileTop + 1);
        if (visibleTileCount <= 0) {
            Cubeium.LOGGER.error("[MapTileRenderer] Invalid visible tile count={} (zoom={}, viewport={}x{})",
                    visibleTileCount, zoomLevel, screenWidth, screenHeight);
            return;
        }
        if (visibleTileCount > MAX_VISIBLE_TILES_PER_FRAME) {
            int tilesWide = tileRight - tileLeft + 1;
            int tilesHigh = tileBottom - tileTop + 1;
            double scale = Math.sqrt((double) MAX_VISIBLE_TILES_PER_FRAME / (double) visibleTileCount);
            int clampedWide = Math.max(1, (int) Math.floor(tilesWide * scale));
            int clampedHigh = Math.max(1, (int) Math.floor(tilesHigh * scale));

            while ((long) clampedWide * clampedHigh > MAX_VISIBLE_TILES_PER_FRAME) {
                if (clampedWide >= clampedHigh && clampedWide > 1) {
                    clampedWide--;
                } else if (clampedHigh > 1) {
                    clampedHigh--;
                } else {
                    break;
                }
            }

            tileLeft = centerTileX - clampedWide / 2;
            tileRight = tileLeft + clampedWide - 1;
            tileTop = centerTileZ - clampedHigh / 2;
            tileBottom = tileTop + clampedHigh - 1;

            long now = System.currentTimeMillis();
            if (now - lastClampWarningMillis >= 500L) {
                lastClampWarningMillis = now;
                Cubeium.LOGGER.warn("[MapTileRenderer] Clamped visible tile window from {} to {} (zoom={}, res={}, viewport={}x{})",
                        visibleTileCount, (long) clampedWide * clampedHigh, zoomLevel, resolutionScale, screenWidth, screenHeight);
            }
        }

        long revision = updateViewport(seed, resolutionScale, tileLeft, tileTop, tileRight, tileBottom, centerTileX, centerTileZ);
        scheduleVisibleTiles(seed, resolutionScale, tileLeft, tileTop, tileRight, tileBottom, centerTileX, centerTileZ, revision);
        
        // Render visible tiles only
        for (int tileZ = tileTop; tileZ <= tileBottom; tileZ++) {
            for (int tileX = tileLeft; tileX <= tileRight; tileX++) {
                renderTile(context, seed, tileX, tileZ, resolutionScale,
                          screenX, screenY, screenWidth, screenHeight, 
                          mapCenterX, mapCenterZ, blocksPerPixel, session);
            }
        }
        
        // Clean up old tiles if cache is too full
        if (tileCache.size() > MAX_CACHED_TILES) {
            cleanupOldTiles();
        }
    }
    
    /**
     * Render a single tile, using cached data if available.
     */
    private void renderTile(DrawContext context, long seed, int tileX, int tileZ, int resolutionScale,
                           int screenX, int screenY, int screenWidth, int screenHeight,
                           int mapCenterX, int mapCenterZ, int blocksPerPixel,
                           CubeiumSeedMapScreen.SeedMapSession session) {
        
        TileKey key = new TileKey(seed, tileX, tileZ, resolutionScale);
        CachedTile tile = tileCache.get(key);
        boolean tileMissing = tile == null;
        boolean tileExpired = tile != null && tile.isExpired();
        boolean cacheMiss = tileMissing || tileExpired;
        if (cacheMiss) {
            RenderMetrics.get().recordTileCacheMiss();
        } else {
            RenderMetrics.get().recordTileCacheHit();
        }

        if (tileMissing) {
            FallbackTile fallbackTile = findFallbackTile(seed, tileX, tileZ, resolutionScale);
            if (fallbackTile != null && fallbackTile.tile != null && fallbackTile.tile.biomeData != null) {
                CachedTile synthesizedTile = trySynthesizeTileFromFallback(fallbackTile, tileX, tileZ, resolutionScale);
                if (synthesizedTile == null && fallbackTile.resolutionScale < resolutionScale) {
                    synthesizedTile = trySynthesizeTileFromFinerChildren(seed, tileX, tileZ, resolutionScale, fallbackTile.resolutionScale);
                }
                if (synthesizedTile != null) {
                    putTileInCache(key, synthesizedTile);
                    synthesizedTile.touch();
                    RenderMetrics.get().recordTileDraw();
                    renderTileData(context, synthesizedTile, tileX, tileZ, resolutionScale,
                        screenX, screenY, screenWidth, screenHeight, mapCenterX, mapCenterZ, blocksPerPixel, session);
                    return;
                }

                // Never draw a finer fallback tile directly for a coarser target tile, because
                // it only covers a fraction of the target footprint and produces grid artifacts.
                if (fallbackTile.resolutionScale >= resolutionScale) {
                    fallbackTile.tile.touch();
                    RenderMetrics.get().recordTileDraw();
                    renderTileData(context, fallbackTile.tile, fallbackTile.tileX, fallbackTile.tileZ, fallbackTile.resolutionScale,
                        screenX, screenY,
                        screenWidth, screenHeight, mapCenterX, mapCenterZ, blocksPerPixel, session);
                    return;
                }
            }

            renderPlaceholderTile(context, tileX, tileZ, resolutionScale, screenX, screenY,
                screenWidth, screenHeight, mapCenterX, mapCenterZ, blocksPerPixel);
            return;
        }
        
        // Touch tile last-access time when used
        if (tile != null) {
            tile.touch();
        }

        // Render tile if ready
        if (tile != null && tile.biomeData != null) {
            RenderMetrics.get().recordTileDraw();
            renderTileData(context, tile, tileX, tileZ, resolutionScale, screenX, screenY,
                          screenWidth, screenHeight, mapCenterX, mapCenterZ, blocksPerPixel, session);
        }
    }
    
    /**
     * Render the biome data from a cached tile
     */
    private void renderTileData(DrawContext context, CachedTile tile, int tileX, int tileZ, int resolutionScale,
                               int screenX, int screenY, int screenWidth, int screenHeight,
                               int mapCenterX, int mapCenterZ, int blocksPerPixel,
                               CubeiumSeedMapScreen.SeedMapSession session) {
        int tileWorldSize = TILE_SIZE * resolutionScale;
        int tileWorldX = tileX * tileWorldSize;
        int tileWorldZ = tileZ * tileWorldSize;

        int viewWorldLeft = mapCenterX - (screenWidth * blocksPerPixel) / 2;
        int viewWorldTop = mapCenterZ - (screenHeight * blocksPerPixel) / 2;

        int tileScreenStartX = screenX + Math.floorDiv(tileWorldX - viewWorldLeft, blocksPerPixel);
        int tileScreenStartY = screenY + Math.floorDiv(tileWorldZ - viewWorldTop, blocksPerPixel);
        int tileScreenEndX = screenX + Math.floorDiv(tileWorldX + tileWorldSize - viewWorldLeft, blocksPerPixel);
        int tileScreenEndY = screenY + Math.floorDiv(tileWorldZ + tileWorldSize - viewWorldTop, blocksPerPixel);

        int drawStartX = Math.max(screenX, tileScreenStartX);
        int drawStartY = Math.max(screenY, tileScreenStartY);
        int drawEndX = Math.min(screenX + screenWidth, tileScreenEndX);
        int drawEndY = Math.min(screenY + screenHeight, tileScreenEndY);

        if (drawStartX >= drawEndX || drawStartY >= drawEndY) {
            return;
        }

        int drawWidth = Math.max(1, tileScreenEndX - tileScreenStartX);
        int drawHeight = Math.max(1, tileScreenEndY - tileScreenStartY);

        for (int pixelY = drawStartY; pixelY < drawEndY; pixelY++) {
            int localScreenY = pixelY - tileScreenStartY;
            int sampleY = Math.min(tile.sampleHeight - 1, (localScreenY * tile.sampleHeight) / drawHeight);

            for (int pixelX = drawStartX; pixelX < drawEndX; pixelX++) {
                int localScreenX = pixelX - tileScreenStartX;
                int sampleX = Math.min(tile.sampleWidth - 1, (localScreenX * tile.sampleWidth) / drawWidth);
                int biomeIndex = sampleY * tile.sampleWidth + sampleX;
                if (biomeIndex < 0 || biomeIndex >= tile.biomeData.length) {
                    continue;
                }

                int biomeId = tile.biomeData[biomeIndex];
                int color = getBiomeColor(biomeId);
                if (session != null && !session.isBiomeVisible(biomeId)) {
                    color = 0xFF262626;
                }
                context.fill(pixelX, pixelY, pixelX + 1, pixelY + 1, color);
            }
        }
    }

    private void renderPlaceholderTile(DrawContext context, int tileX, int tileZ, int resolutionScale,
                                       int screenX, int screenY, int screenWidth, int screenHeight,
                                       int mapCenterX, int mapCenterZ, int blocksPerPixel) {
        int tileWorldSize = TILE_SIZE * resolutionScale;
        int tileWorldX = tileX * tileWorldSize;
        int tileWorldZ = tileZ * tileWorldSize;

        int viewWorldLeft = mapCenterX - (screenWidth * blocksPerPixel) / 2;
        int viewWorldTop = mapCenterZ - (screenHeight * blocksPerPixel) / 2;

        int drawX0 = Math.max(screenX, screenX + Math.floorDiv(tileWorldX - viewWorldLeft, blocksPerPixel));
        int drawY0 = Math.max(screenY, screenY + Math.floorDiv(tileWorldZ - viewWorldTop, blocksPerPixel));
        int drawX1 = Math.min(screenX + screenWidth, screenX + Math.floorDiv(tileWorldX + tileWorldSize - viewWorldLeft, blocksPerPixel));
        int drawY1 = Math.min(screenY + screenHeight, screenY + Math.floorDiv(tileWorldZ + tileWorldSize - viewWorldTop, blocksPerPixel));

        if (drawX0 >= drawX1 || drawY0 >= drawY1) {
            return;
        }

        context.fill(drawX0, drawY0, drawX1, drawY1, 0x55222222);
    }

    private long updateViewport(long seed, int zoomLevel, int tileLeft, int tileTop, int tileRight, int tileBottom,
                                int centerTileX, int centerTileZ) {
        ViewportState nextViewport = new ViewportState(seed, zoomLevel, tileLeft, tileTop, tileRight, tileBottom, centerTileX, centerTileZ);
        ViewportState previousViewport = currentViewport;
        if (nextViewport.equals(previousViewport)) {
            return viewportRevision.get();
        }

        currentViewport = nextViewport;
        long revision = viewportRevision.incrementAndGet();
        previousRevision = revision - 1;
        previousRevisionValidUntilMillis = System.currentTimeMillis() + REVISION_GRACE_MILLIS;
        discardStaleRequests(revision);
        return revision;
    }

    private void discardStaleRequests(long revision) {
        long minimumAllowedRevision = getMinimumAllowedRevision(revision);
        requestQueue.removeIf(request -> request.revision < minimumAllowedRevision);
        scheduledTileRevisions.entrySet().removeIf(entry -> entry.getValue() < minimumAllowedRevision);
    }

    private void scheduleVisibleTiles(long seed, int zoomLevel, int tileLeft, int tileTop, int tileRight, int tileBottom,
                                      int centerTileX, int centerTileZ, long revision) {
        int currentPending = scheduledTileRevisions.size();
        int queueHeadroom = Math.max(0, MAX_PENDING_TILE_REQUESTS - currentPending);
        if (queueHeadroom == 0) {
            return;
        }

        int enqueueBudget = Math.min(queueHeadroom, MAX_ENQUEUE_PER_FRAME);
        if (enqueueBudget == 0) {
            return;
        }

        List<TileRequest> visibleRequests = new ArrayList<>((tileRight - tileLeft + 1) * (tileBottom - tileTop + 1));
        for (int tileZ = tileTop; tileZ <= tileBottom; tileZ++) {
            for (int tileX = tileLeft; tileX <= tileRight; tileX++) {
                TileKey key = new TileKey(seed, tileX, tileZ, zoomLevel);
                CachedTile cachedTile = tileCache.get(key);
                if (cachedTile != null && !cachedTile.isExpired()) {
                    continue;
                }

                long distance = priorityDistance(tileX, tileZ, centerTileX, centerTileZ);
                visibleRequests.add(new TileRequest(key, revision, distance, requestSequence.getAndIncrement()));
            }
        }

        visibleRequests.sort(null);
        for (TileRequest request : visibleRequests) {
            if (enqueueBudget <= 0) {
                break;
            }
            if (requestTileGeneration(request)) {
                enqueueBudget--;
            }
        }
    }

    private long priorityDistance(int tileX, int tileZ, int centerTileX, int centerTileZ) {
        long deltaX = (long) tileX - centerTileX;
        long deltaZ = (long) tileZ - centerTileZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private long getMinimumAllowedRevision(long currentRevision) {
        long now = System.currentTimeMillis();
        if (now <= previousRevisionValidUntilMillis && previousRevision >= 0) {
            return Math.max(0L, currentRevision - 1);
        }
        return currentRevision;
    }

    private boolean requestTileGeneration(TileRequest request) {
        Long existingRevision = scheduledTileRevisions.putIfAbsent(request.key, request.revision);
        if (existingRevision != null) {
            if (existingRevision >= request.revision) {
                return false;
            }
            if (!scheduledTileRevisions.replace(request.key, existingRevision, request.revision)) {
                return false;
            }
        }

        requestQueue.offer(request);
        return true;
    }

    private void runWorkerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                processTileRequest(requestQueue.take());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processTileRequest(TileRequest request) {
        Long scheduledRevision = scheduledTileRevisions.get(request.key);
        if (scheduledRevision == null || scheduledRevision != request.revision) {
            return;
        }

        long currentRevision = viewportRevision.get();
        long minimumAllowedRevision = getMinimumAllowedRevision(currentRevision);
        if (request.revision < minimumAllowedRevision || request.revision > currentRevision) {
            scheduledTileRevisions.remove(request.key, request.revision);
            return;
        }

        try {
            CachedTile generated = generateTile(request.key.seed, request.key.tileX, request.key.tileZ, request.key.zoomLevel);
            Long latestRevision = scheduledTileRevisions.get(request.key);
            if (generated != null
                && latestRevision != null
                && latestRevision == request.revision
                && request.revision >= getMinimumAllowedRevision(viewportRevision.get())) {
                putTileInCache(request.key, generated);
            }
        } catch (Exception exception) {
            Cubeium.LOGGER.warn("Tile generation failed for seed={}, tile=({}, {}), zoom={}",
                request.key.seed, request.key.tileX, request.key.tileZ, request.key.zoomLevel, exception);
        } finally {
            scheduledTileRevisions.remove(request.key, request.revision);
        }
    }

    private FallbackTile findFallbackTile(long seed, int tileX, int tileZ, int requestedResolutionScale) {
        int requestedTileWorldSize = TILE_SIZE * requestedResolutionScale;
        int requestedCenterX = tileX * requestedTileWorldSize + requestedTileWorldSize / 2;
        int requestedCenterZ = tileZ * requestedTileWorldSize + requestedTileWorldSize / 2;

        int requestedIndex = 0;
        for (int i = 0; i < RESOLUTION_LEVELS.length; i++) {
            if (RESOLUTION_LEVELS[i] == requestedResolutionScale) {
                requestedIndex = i;
                break;
            }
        }

        FallbackTile bestExpired = null;
        for (int delta = 1; delta < RESOLUTION_LEVELS.length; delta++) {
            int higherIndex = requestedIndex + delta;
            if (higherIndex < RESOLUTION_LEVELS.length) {
                FallbackTile higherMatch = getFallbackTile(seed, requestedCenterX, requestedCenterZ, RESOLUTION_LEVELS[higherIndex]);
                if (higherMatch != null) {
                    if (!higherMatch.tile.isExpired()) {
                        return higherMatch;
                    }
                    if (bestExpired == null) {
                        bestExpired = higherMatch;
                    }
                }
            }

            int lowerIndex = requestedIndex - delta;
            if (lowerIndex >= 0) {
                FallbackTile lowerMatch = getFallbackTile(seed, requestedCenterX, requestedCenterZ, RESOLUTION_LEVELS[lowerIndex]);
                if (lowerMatch != null) {
                    if (!lowerMatch.tile.isExpired()) {
                        return lowerMatch;
                    }
                    if (bestExpired == null) {
                        bestExpired = lowerMatch;
                    }
                }
            }
        }

        return bestExpired;
    }

    private FallbackTile getFallbackTile(long seed, int worldCenterX, int worldCenterZ, int resolutionScale) {
        int tileWorldSize = TILE_SIZE * resolutionScale;
        int fallbackTileX = Math.floorDiv(worldCenterX, tileWorldSize);
        int fallbackTileZ = Math.floorDiv(worldCenterZ, tileWorldSize);
        CachedTile fallbackTile = tileCache.get(new TileKey(seed, fallbackTileX, fallbackTileZ, resolutionScale));
        if (fallbackTile == null) {
            return null;
        }
        return new FallbackTile(fallbackTile, fallbackTileX, fallbackTileZ, resolutionScale);
    }

    private CachedTile trySynthesizeTileFromFallback(FallbackTile fallbackTile, int targetTileX, int targetTileZ, int targetResolutionScale) {
        if (fallbackTile == null || fallbackTile.tile == null) {
            return null;
        }

        // A finer fallback tile cannot fully cover a coarser target tile.
        if (fallbackTile.resolutionScale < targetResolutionScale) {
            return null;
        }

        int targetTileWorldSize = TILE_SIZE * targetResolutionScale;
        int fallbackTileWorldSize = TILE_SIZE * fallbackTile.resolutionScale;

        long targetStartX = (long) targetTileX * targetTileWorldSize;
        long targetStartZ = (long) targetTileZ * targetTileWorldSize;
        long targetEndX = targetStartX + targetTileWorldSize;
        long targetEndZ = targetStartZ + targetTileWorldSize;

        long fallbackStartX = (long) fallbackTile.tileX * fallbackTileWorldSize;
        long fallbackStartZ = (long) fallbackTile.tileZ * fallbackTileWorldSize;
        long fallbackEndX = fallbackStartX + fallbackTileWorldSize;
        long fallbackEndZ = fallbackStartZ + fallbackTileWorldSize;

        if (targetStartX < fallbackStartX || targetEndX > fallbackEndX
            || targetStartZ < fallbackStartZ || targetEndZ > fallbackEndZ) {
            return null;
        }

        int[] synthesized = new int[TILE_SIZE * TILE_SIZE];
        CachedTile source = fallbackTile.tile;

        for (int y = 0; y < TILE_SIZE; y++) {
            long worldZ = targetStartZ + (((long) y * targetTileWorldSize + targetTileWorldSize / 2L) / TILE_SIZE);
            int localWorldZ = (int) (worldZ - fallbackStartZ);
            int sourceY = Math.min(source.sampleHeight - 1,
                Math.max(0, (int) (((long) localWorldZ * source.sampleHeight) / fallbackTileWorldSize)));

            for (int x = 0; x < TILE_SIZE; x++) {
                long worldX = targetStartX + (((long) x * targetTileWorldSize + targetTileWorldSize / 2L) / TILE_SIZE);
                int localWorldX = (int) (worldX - fallbackStartX);
                int sourceX = Math.min(source.sampleWidth - 1,
                    Math.max(0, (int) (((long) localWorldX * source.sampleWidth) / fallbackTileWorldSize)));

                int sourceIndex = sourceY * source.sampleWidth + sourceX;
                if (sourceIndex >= 0 && sourceIndex < source.biomeData.length) {
                    synthesized[y * TILE_SIZE + x] = source.biomeData[sourceIndex];
                }
            }
        }

        return new CachedTile(synthesized, TILE_SIZE, TILE_SIZE, System.currentTimeMillis());
    }

    private CachedTile trySynthesizeTileFromFinerChildren(long seed, int targetTileX, int targetTileZ,
                                                           int targetResolutionScale, int finerResolutionScale) {
        if (finerResolutionScale <= 0 || targetResolutionScale <= finerResolutionScale) {
            return null;
        }
        if (targetResolutionScale % finerResolutionScale != 0) {
            return null;
        }

        int factor = targetResolutionScale / finerResolutionScale;
        int targetTileWorldSize = TILE_SIZE * targetResolutionScale;
        int childTileWorldSize = TILE_SIZE * finerResolutionScale;

        long targetStartX = (long) targetTileX * targetTileWorldSize;
        long targetStartZ = (long) targetTileZ * targetTileWorldSize;

        long childStartTileXLong = Math.floorDiv(targetStartX, childTileWorldSize);
        long childStartTileZLong = Math.floorDiv(targetStartZ, childTileWorldSize);
        int childStartTileX = (int) childStartTileXLong;
        int childStartTileZ = (int) childStartTileZLong;

        CachedTile[][] children = new CachedTile[factor][factor];
        for (int childZ = 0; childZ < factor; childZ++) {
            for (int childX = 0; childX < factor; childX++) {
                int currentChildTileX = childStartTileX + childX;
                int currentChildTileZ = childStartTileZ + childZ;
                CachedTile childTile = tileCache.get(new TileKey(seed, currentChildTileX, currentChildTileZ, finerResolutionScale));
                if (childTile == null || childTile.biomeData == null) {
                    return null;
                }
                children[childZ][childX] = childTile;
            }
        }

        int[] synthesized = new int[TILE_SIZE * TILE_SIZE];
        for (int y = 0; y < TILE_SIZE; y++) {
            long worldZ = targetStartZ + (((long) y * targetTileWorldSize + targetTileWorldSize / 2L) / TILE_SIZE);
            long childTileZLong = Math.floorDiv(worldZ, childTileWorldSize);
            int childIndexZ = (int) (childTileZLong - childStartTileZLong);
            if (childIndexZ < 0 || childIndexZ >= factor) {
                return null;
            }

            long childTileStartZ = childTileZLong * childTileWorldSize;
            int childLocalZ = (int) (worldZ - childTileStartZ);

            for (int x = 0; x < TILE_SIZE; x++) {
                long worldX = targetStartX + (((long) x * targetTileWorldSize + targetTileWorldSize / 2L) / TILE_SIZE);
                long childTileXLong = Math.floorDiv(worldX, childTileWorldSize);
                int childIndexX = (int) (childTileXLong - childStartTileXLong);
                if (childIndexX < 0 || childIndexX >= factor) {
                    return null;
                }

                long childTileStartX = childTileXLong * childTileWorldSize;
                int childLocalX = (int) (worldX - childTileStartX);

                CachedTile child = children[childIndexZ][childIndexX];
                int sourceX = Math.min(child.sampleWidth - 1,
                    Math.max(0, (int) (((long) childLocalX * child.sampleWidth) / childTileWorldSize)));
                int sourceY = Math.min(child.sampleHeight - 1,
                    Math.max(0, (int) (((long) childLocalZ * child.sampleHeight) / childTileWorldSize)));

                int sourceIndex = sourceY * child.sampleWidth + sourceX;
                if (sourceIndex >= 0 && sourceIndex < child.biomeData.length) {
                    synthesized[y * TILE_SIZE + x] = child.biomeData[sourceIndex];
                }
            }
        }

        return new CachedTile(synthesized, TILE_SIZE, TILE_SIZE, System.currentTimeMillis());
    }
    
    /**
     * Generate a tile by loading biome data from cache.
     */
    private CachedTile generateTile(long seed, int tileX, int tileZ, int resolutionScale) {
        if (resolutionScale < 1 || resolutionScale > 64) {
            Cubeium.LOGGER.warn("[MapTileRenderer] Invalid resolution scale {}, skipping tile generation", resolutionScale);
            return null;
        }

        int tileWorldSize = TILE_SIZE * resolutionScale;
        int tileWorldX = tileX * tileWorldSize + tileWorldSize / 2; // Center of tile
        int tileWorldZ = tileZ * tileWorldSize + tileWorldSize / 2;

        int pixelsPerTile = TILE_SIZE;
        
        // Get biome data for this tile area
        long generationStart = System.nanoTime();
        int[] biomeData = mapCache.getBiomeArea(seed, tileWorldX, tileWorldZ,
                                               pixelsPerTile, pixelsPerTile, resolutionScale);
        RenderMetrics.get().recordTileGenerationNanos(System.nanoTime() - generationStart);
        
        if (biomeData == null) {
            return null; // Data not ready yet
        }
        
        return new CachedTile(biomeData, pixelsPerTile, pixelsPerTile, System.currentTimeMillis());
    }

    /**
     * Samples the exact biome ID currently used to draw a given on-screen map pixel.
     * Returns null when no tile/fallback data is currently available for that pixel.
     */
    public Integer sampleBiomeAtScreen(long seed,
                                       int screenX, int screenY, int screenWidth, int screenHeight,
                                       int mapCenterX, int mapCenterZ, int zoomLevel,
                                       int pixelX, int pixelY) {
        if (pixelX < screenX || pixelX >= screenX + screenWidth || pixelY < screenY || pixelY >= screenY + screenHeight) {
            return null;
        }

        int blocksPerPixel = Math.max(1, zoomLevel);
        int resolutionScale = selectResolutionScale(zoomLevel);
        int tileWorldSize = TILE_SIZE * resolutionScale;

        int viewWorldLeft = mapCenterX - (screenWidth * blocksPerPixel) / 2;
        int viewWorldTop = mapCenterZ - (screenHeight * blocksPerPixel) / 2;

        int worldX = viewWorldLeft + (pixelX - screenX) * blocksPerPixel;
        int worldZ = viewWorldTop + (pixelY - screenY) * blocksPerPixel;

        int tileX = Math.floorDiv(worldX, tileWorldSize);
        int tileZ = Math.floorDiv(worldZ, tileWorldSize);

        ResolvedTile resolvedTile = resolveRenderableTile(seed, tileX, tileZ, resolutionScale);
        if (resolvedTile == null || resolvedTile.tile == null || resolvedTile.tile.biomeData == null) {
            return null;
        }

        int resolvedTileWorldSize = TILE_SIZE * resolvedTile.resolutionScale;
        int tileWorldX = resolvedTile.tileX * resolvedTileWorldSize;
        int tileWorldZ = resolvedTile.tileZ * resolvedTileWorldSize;

        int tileScreenStartX = screenX + Math.floorDiv(tileWorldX - viewWorldLeft, blocksPerPixel);
        int tileScreenStartY = screenY + Math.floorDiv(tileWorldZ - viewWorldTop, blocksPerPixel);
        int tileScreenEndX = screenX + Math.floorDiv(tileWorldX + resolvedTileWorldSize - viewWorldLeft, blocksPerPixel);
        int tileScreenEndY = screenY + Math.floorDiv(tileWorldZ + resolvedTileWorldSize - viewWorldTop, blocksPerPixel);

        if (pixelX < tileScreenStartX || pixelX >= tileScreenEndX || pixelY < tileScreenStartY || pixelY >= tileScreenEndY) {
            return null;
        }

        int drawWidth = Math.max(1, tileScreenEndX - tileScreenStartX);
        int drawHeight = Math.max(1, tileScreenEndY - tileScreenStartY);

        int localScreenX = pixelX - tileScreenStartX;
        int localScreenY = pixelY - tileScreenStartY;
        int sampleX = Math.min(resolvedTile.tile.sampleWidth - 1, (localScreenX * resolvedTile.tile.sampleWidth) / drawWidth);
        int sampleY = Math.min(resolvedTile.tile.sampleHeight - 1, (localScreenY * resolvedTile.tile.sampleHeight) / drawHeight);
        int biomeIndex = sampleY * resolvedTile.tile.sampleWidth + sampleX;

        if (biomeIndex < 0 || biomeIndex >= resolvedTile.tile.biomeData.length) {
            return null;
        }

        return resolvedTile.tile.biomeData[biomeIndex];
    }

    private ResolvedTile resolveRenderableTile(long seed, int tileX, int tileZ, int resolutionScale) {
        TileKey key = new TileKey(seed, tileX, tileZ, resolutionScale);
        CachedTile tile = tileCache.get(key);
        if (tile != null && tile.biomeData != null) {
            tile.touch();
            return new ResolvedTile(tile, tileX, tileZ, resolutionScale);
        }

        FallbackTile fallbackTile = findFallbackTile(seed, tileX, tileZ, resolutionScale);
        if (fallbackTile != null && fallbackTile.tile != null && fallbackTile.tile.biomeData != null) {
            CachedTile synthesizedTile = trySynthesizeTileFromFallback(fallbackTile, tileX, tileZ, resolutionScale);
            if (synthesizedTile == null && fallbackTile.resolutionScale < resolutionScale) {
                synthesizedTile = trySynthesizeTileFromFinerChildren(seed, tileX, tileZ, resolutionScale, fallbackTile.resolutionScale);
            }
            if (synthesizedTile != null) {
                putTileInCache(key, synthesizedTile);
                synthesizedTile.touch();
                return new ResolvedTile(synthesizedTile, tileX, tileZ, resolutionScale);
            }

            if (fallbackTile.resolutionScale >= resolutionScale) {
                fallbackTile.tile.touch();
                return new ResolvedTile(fallbackTile.tile, fallbackTile.tileX, fallbackTile.tileZ, fallbackTile.resolutionScale);
            }
        }

        return null;
    }
    
    /**
     * Get biome color (optimized lookup)
     */
    public static int getBiomeColor(int biomeId) {
        return switch (biomeId) {
            case 0 -> 0xFF000070;
            case 1 -> 0xFF8DB360;
            case 2 -> 0xFFFA9418;
            case 3 -> 0xFF606060;
            case 4 -> 0xFF056621;
            case 5 -> 0xFF0B6A5F;
            case 6 -> 0xFF07F9B2;
            case 7 -> 0xFF0000FF;
            case 8 -> 0xFFFF0000;
            case 9 -> 0xFF8080FF;
            case 10 -> 0xFF7070D6;
            case 11 -> 0xFFA0A0FF;
            case 12 -> 0xFFE0E0E0;
            case 13 -> 0xFFA0A0A0;
            case 14 -> 0xFFFF00FF;
            case 15 -> 0xFFA000FF;
            case 16 -> 0xFFFFDE55;
            case 17 -> 0xFFD25F12;
            case 18 -> 0xFF22551C;
            case 19 -> 0xFF163933;
            case 20 -> 0xFF72789A;
            case 21 -> 0xFF537B09;
            case 22 -> 0xFF2C4205;
            case 23 -> 0xFF628B17;
            case 24 -> 0xFF000030;
            case 25 -> 0xFFA2A284;
            case 26 -> 0xFFFAF0C0;
            case 27 -> 0xFF307444;
            case 28 -> 0xFF1F0532;
            case 29 -> 0xFF40511A;
            case 30 -> 0xFF31554A;
            case 31 -> 0xFF243F36;
            case 32 -> 0xFF596651;
            case 33 -> 0xFF45073E;
            case 34 -> 0xFF507050;
            case 35 -> 0xFFBDB25F;
            case 36 -> 0xFFA79D64;
            case 37 -> 0xFFD94515;
            case 38 -> 0xFFB09765;
            case 39 -> 0xFFCA8C65;
            case 40, 41, 42, 43 -> 0xFF8080FF;
            case 44 -> 0xFF0000AC;
            case 45 -> 0xFF000090;
            case 46 -> 0xFF202070;
            case 47 -> 0xFF000050;
            case 48 -> 0xFF000040;
            case 49 -> 0xFF202038;
            case 50 -> 0xFF404090;
            case 127 -> 0xFF000000;
            case 129 -> 0xFFB5DB88;
            case 130 -> 0xFFFFBC40;
            case 131 -> 0xFF888888;
            case 132 -> 0xFF2D8E49;
            case 133 -> 0xFF338E13;
            case 134 -> 0xFF2FFF12;
            case 140 -> 0xFFB4DCDC;
            case 149 -> 0xFF7B0D31;
            case 151 -> 0xFF8AB33F;
            case 155 -> 0xFF589C6C;
            case 156 -> 0xFF470F5A;
            case 157 -> 0xFF687942;
            case 158 -> 0xFF597D72;
            case 160 -> 0xFF818E79;
            case 161 -> 0xFF6D7766;
            case 162 -> 0xFF783478;
            case 163 -> 0xFFE5DA87;
            case 165 -> 0xFFFF6D3D;
            case 168 -> 0xFF849500;
            case 169 -> 0xFFCFC58C;
            case 170 -> 0xFFFF6D3D;
            case 171 -> 0xFFD8BF8D;
            case 172 -> 0xFFF2B48D;
            case 173 -> 0xFF768E14;
            case 174 -> 0xFF3B470A;
            case 175 -> 0xFF522921;
            case 177 -> 0xFF60A445;
            case 178 -> 0xFF47726C;
            case 179 -> 0xFFC4C4C4;
            case 180 -> 0xFFDCDCC8;
            case 181 -> 0xFFB0B3CE;
            case 182 -> 0xFF7B8F74;
            case 183 -> 0xFFDD0808;
            case 184 -> 0xFF2CCC8E;
            case 185 -> 0xFFFF91C8;
            case 186 -> 0xFF696D95;
            default -> 0xFFFF00FF;
        };
    }
    
    /**
     * Clear all cached tiles (call when changing seeds)
     */
    public void clearCache() {
        tileCache.clear();
        tileCacheBytes.set(0L);
        requestQueue.clear();
        scheduledTileRevisions.clear();
        currentViewport = null;
        viewportRevision.incrementAndGet();
    }

    /**
     * Pre-generate and cache tiles covering the given viewport in the background.
     * This is a best-effort warm-up and will not block the calling thread.
    * Parameters match the call-site in CubeiumSeedMapScreen: width/height are in
     * screen pixels, zoomLevel is blocks-per-pixel.
     */
    public void prewarmTiles(long seed, int centerX, int centerZ, int widthPx, int heightPx, int zoomLevel) {
        int blocksPerPixel = Math.max(1, zoomLevel);
        int resolutionScale = selectResolutionScale(zoomLevel);
        int tileWorldSize = TILE_SIZE * resolutionScale;
        int viewWidthBlocks = Math.max(1, widthPx * blocksPerPixel);
        int viewHeightBlocks = Math.max(1, heightPx * blocksPerPixel);

        int worldLeft = centerX - viewWidthBlocks / 2;
        int worldTop = centerZ - viewHeightBlocks / 2;
        int worldRight = worldLeft + viewWidthBlocks;
        int worldBottom = worldTop + viewHeightBlocks;

        int tileLeft = Math.floorDiv(worldLeft, tileWorldSize);
        int tileTop = Math.floorDiv(worldTop, tileWorldSize);
        int tileRight = Math.floorDiv(worldRight, tileWorldSize);
        int tileBottom = Math.floorDiv(worldBottom, tileWorldSize);

        final int MAX_PREWARM_RADIUS = 256;
        tileLeft = Math.max(tileLeft, -MAX_PREWARM_RADIUS);
        tileTop = Math.max(tileTop, -MAX_PREWARM_RADIUS);
        tileRight = Math.min(tileRight, MAX_PREWARM_RADIUS);
        tileBottom = Math.min(tileBottom, MAX_PREWARM_RADIUS);

        int centerTileX = Math.floorDiv(centerX, tileWorldSize);
        int centerTileZ = Math.floorDiv(centerZ, tileWorldSize);
        long revision = updateViewport(seed, resolutionScale, tileLeft, tileTop, tileRight, tileBottom, centerTileX, centerTileZ);
        scheduleVisibleTiles(seed, resolutionScale, tileLeft, tileTop, tileRight, tileBottom, centerTileX, centerTileZ, revision);
    }

    private int selectResolutionScale(int zoomLevel) {
        int safeZoom = Math.max(1, zoomLevel);
        int scale = 1;
        while (scale < RESOLUTION_LEVELS[RESOLUTION_LEVELS.length - 1] && scale * 8 < safeZoom) {
            scale *= 2;
        }

        int previousScale = lastResolutionScale;
        if (!isResolutionScaleSupported(previousScale)) {
            return scale;
        }

        // Hysteresis prevents rapid LOD bouncing on tiny zoom changes.
        if (scale > previousScale) {
            int promoteThreshold = previousScale * 10;
            if (safeZoom < promoteThreshold) {
                return previousScale;
            }
        } else if (scale < previousScale) {
            int demoteThreshold = previousScale * 6;
            if (safeZoom > demoteThreshold) {
                return previousScale;
            }
        }

        return scale;
    }

    private boolean isResolutionScaleSupported(int scale) {
        for (int candidate : RESOLUTION_LEVELS) {
            if (candidate == scale) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get cache statistics
     */
    public int getCachedTileCount() {
        return tileCache.size();
    }

    public long getCachedTileBytes() {
        return tileCacheBytes.get();
    }

    public int getPendingTileCount() {
        return scheduledTileRevisions.size() + requestQueue.size();
    }

    /**
     * Stop background tile generation workers.
     */
    public void shutdown() {
        generationExecutor.shutdownNow();
        try {
            generationExecutor.awaitTermination(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        requestQueue.clear();
        scheduledTileRevisions.clear();
        tileCache.clear();
        tileCacheBytes.set(0L);
        currentViewport = null;
    }
    
    /**
     * Unique identifier for a tile
     */
    private static class TileKey {
        final long seed;
        final int tileX, tileZ, zoomLevel;
        final int hashCode;
        
        TileKey(long seed, int tileX, int tileZ, int zoomLevel) {
            this.seed = seed;
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.zoomLevel = zoomLevel;
            this.hashCode = java.util.Objects.hash(seed, tileX, tileZ, zoomLevel);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof TileKey other)) return false;
            return seed == other.seed && tileX == other.tileX && 
                   tileZ == other.tileZ && zoomLevel == other.zoomLevel;
        }
        
        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static class TileRequest implements Comparable<TileRequest> {
        final TileKey key;
        final long revision;
        final long priorityDistance;
        final long sequence;

        TileRequest(TileKey key, long revision, long priorityDistance, long sequence) {
            this.key = key;
            this.revision = revision;
            this.priorityDistance = priorityDistance;
            this.sequence = sequence;
        }

        @Override
        public int compareTo(TileRequest other) {
            int distanceComparison = Long.compare(priorityDistance, other.priorityDistance);
            if (distanceComparison != 0) {
                return distanceComparison;
            }
            return Long.compare(sequence, other.sequence);
        }
    }

    private static class FallbackTile {
        final CachedTile tile;
        final int tileX;
        final int tileZ;
        final int resolutionScale;

        FallbackTile(CachedTile tile, int tileX, int tileZ, int resolutionScale) {
            this.tile = tile;
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.resolutionScale = resolutionScale;
        }
    }

    private static class ResolvedTile {
        final CachedTile tile;
        final int tileX;
        final int tileZ;
        final int resolutionScale;

        ResolvedTile(CachedTile tile, int tileX, int tileZ, int resolutionScale) {
            this.tile = tile;
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.resolutionScale = resolutionScale;
        }
    }

    private static class ViewportState {
        final long seed;
        final int resolutionScale;
        final int tileLeft;
        final int tileTop;
        final int tileRight;
        final int tileBottom;
        final int centerTileX;
        final int centerTileZ;

        ViewportState(long seed, int resolutionScale, int tileLeft, int tileTop, int tileRight, int tileBottom,
                      int centerTileX, int centerTileZ) {
            this.seed = seed;
            this.resolutionScale = resolutionScale;
            this.tileLeft = tileLeft;
            this.tileTop = tileTop;
            this.tileRight = tileRight;
            this.tileBottom = tileBottom;
            this.centerTileX = centerTileX;
            this.centerTileZ = centerTileZ;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ViewportState other)) {
                return false;
            }
            return seed == other.seed
                && resolutionScale == other.resolutionScale
                && tileLeft == other.tileLeft
                && tileTop == other.tileTop
                && tileRight == other.tileRight
                && tileBottom == other.tileBottom
                && centerTileX == other.centerTileX
                && centerTileZ == other.centerTileZ;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(seed, resolutionScale, tileLeft, tileTop, tileRight, tileBottom, centerTileX, centerTileZ);
        }
    }
    
    /**
     * Cached tile data
     */
    private static class CachedTile {
        final int[] biomeData;
        final int sampleWidth;
        final int sampleHeight;
        final long estimatedBytes;
        final long createdAt;
        volatile long lastAccess;
        
        CachedTile(int[] biomeData, int sampleWidth, int sampleHeight, long createdAt) {
            this.biomeData = biomeData;
            this.sampleWidth = sampleWidth;
            this.sampleHeight = sampleHeight;
            this.estimatedBytes = estimateTileBytes(biomeData);
            this.createdAt = createdAt;
            this.lastAccess = createdAt;
        }
        
        void touch() {
            this.lastAccess = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > 900000; // 15 minutes
        }
    }

    /**
     * Evict least-recently-used tiles until the cache returns to its configured cap.
     */
    private void cleanupOldTiles() {
        synchronized (tileEvictionLock) {
            cleanupOldTilesLocked();
        }
    }

    private void cleanupOldTilesLocked() {
        // LRU-only eviction avoids hard viewport pruning that causes visual "forgetting"
        // when panning back to recently viewed regions.
        if (tileCache.size() <= MAX_CACHED_TILES && tileCacheBytes.get() <= MAX_TILE_CACHE_BYTES) return;

        java.util.List<TileEvictionCandidate> entries = new java.util.ArrayList<>(tileCache.size());
        for (Map.Entry<TileKey, CachedTile> entry : tileCache.entrySet()) {
            CachedTile tile = entry.getValue();
            if (tile != null) {
                entries.add(new TileEvictionCandidate(entry.getKey(), tile.lastAccess));
            }
        }

        entries.sort((a, b) -> {
            int compare = Long.compare(a.lastAccess, b.lastAccess);
            if (compare != 0) {
                return compare;
            }
            compare = Long.compare(a.key.seed, b.key.seed);
            if (compare != 0) {
                return compare;
            }
            compare = Integer.compare(a.key.tileX, b.key.tileX);
            if (compare != 0) {
                return compare;
            }
            compare = Integer.compare(a.key.tileZ, b.key.tileZ);
            if (compare != 0) {
                return compare;
            }
            return Integer.compare(a.key.zoomLevel, b.key.zoomLevel);
        });

        int idx = 0;
        while ((tileCache.size() > TARGET_CACHED_TILES || tileCacheBytes.get() > TARGET_TILE_CACHE_BYTES) && idx < entries.size()) {
            CachedTile removed = tileCache.remove(entries.get(idx).key);
            if (removed != null) {
                tileCacheBytes.addAndGet(-removed.estimatedBytes);
            }
            idx++;
        }

        if (tileCacheBytes.get() < 0L) {
            tileCacheBytes.set(0L);
        }
    }

    private void putTileInCache(TileKey key, CachedTile tile) {
        synchronized (tileEvictionLock) {
            CachedTile previous = tileCache.put(key, tile);
            long byteDelta = tile.estimatedBytes - (previous != null ? previous.estimatedBytes : 0L);
            tileCacheBytes.addAndGet(byteDelta);

            if (tileCache.size() > MAX_CACHED_TILES || tileCacheBytes.get() > MAX_TILE_CACHE_BYTES) {
                cleanupOldTilesLocked();
            }
        }
    }

    private static long estimateTileBytes(int[] biomeData) {
        if (biomeData == null) {
            return 0L;
        }
        return (long) biomeData.length * Integer.BYTES;
    }

    private static class TileEvictionCandidate {
        final TileKey key;
        final long lastAccess;

        TileEvictionCandidate(TileKey key, long lastAccess) {
            this.key = key;
            this.lastAccess = lastAccess;
        }
    }
}
