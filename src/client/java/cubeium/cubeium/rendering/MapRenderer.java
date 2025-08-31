package cubeium.cubeium.rendering;

import cubeium.cubeium.world.generation.BiomeGenerator;
import cubeium.cubeium.world.generation.StructureGenerator;
import cubeium.cubeium.rendering.cache.MapCache;
import cubeium.cubeium.rendering.tile.TileRenderer;
import cubeium.cubeium.util.ClientColorPalette;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.Map;

/**
 * Main map rendering system that coordinates tile rendering and viewport management.
 * Handles dynamic loading of tiles based on viewport and provides smooth zoom/pan.
 */
public class MapRenderer {
    
    // Default viewport settings
    private static final int DEFAULT_VIEWPORT_WIDTH = 800;
    private static final int DEFAULT_VIEWPORT_HEIGHT = 600;
    private static final double DEFAULT_SCALE = 1.0;
    private static final int MIN_ZOOM_LEVEL = -4; // Very zoomed in
    private static final int MAX_ZOOM_LEVEL = 8;  // Very zoomed out
    
    // Core components
    private final BiomeGenerator biomeGenerator;
    private final StructureGenerator structureGenerator;
    private final MapCache mapCache;
    private final TileRenderer tileRenderer;
    private final ClientColorPalette colorPalette;
    private final ExecutorService renderExecutor;
    
    // Viewport state
    private volatile double viewportCenterX = 0;
    private volatile double viewportCenterZ = 0;
    private volatile int viewportWidth = DEFAULT_VIEWPORT_WIDTH;
    private volatile int viewportHeight = DEFAULT_VIEWPORT_HEIGHT;
    private volatile int zoomLevel = 0; // 0 = 1:1 scale
    private volatile double scale = DEFAULT_SCALE;
    
    // Rendering state
    private volatile int renderFlags = TileRenderer.RENDER_BIOMES | TileRenderer.RENDER_STRUCTURES;
    private volatile long currentSeed = 0;
    private volatile int currentDimension = 0;
    
    // Async tile requests
    private final Map<String, CompletableFuture<TileRenderer.RenderedTile>> pendingTiles = new ConcurrentHashMap<>();
    
    // Performance tracking
    private volatile long totalRenderRequests = 0;
    private volatile long totalRenderTime = 0;
    private volatile long lastFrameTime = 0;
    
    /**
     * Create a MapRenderer
     * @param biomeGenerator Biome generation system
     * @param structureGenerator Structure generation system
     * @param colorPalette Color palette for rendering
     * @param threadCount Number of rendering threads
     */
    public MapRenderer(BiomeGenerator biomeGenerator, StructureGenerator structureGenerator, 
                      ClientColorPalette colorPalette, int threadCount) {
        this.biomeGenerator = biomeGenerator;
        this.structureGenerator = structureGenerator;
        this.colorPalette = colorPalette;
        
        // Initialize map cache
        this.mapCache = new MapCache();
        
        // Initialize tile renderer
        this.tileRenderer = new TileRenderer(biomeGenerator, structureGenerator, 
                                           colorPalette, mapCache, threadCount);
        
        // Create render executor
        this.renderExecutor = Executors.newFixedThreadPool(threadCount, new ThreadFactory() {
            private int threadIndex = 0;
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "MapRenderer-" + threadIndex++);
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            }
        });
    }
    
    /**
     * Set world state
     * @param seed World seed
     * @param dimension Dimension ID
     */
    public void setWorldState(long seed, int dimension) {
        if (this.currentSeed != seed || this.currentDimension != dimension) {
            this.currentSeed = seed;
            this.currentDimension = dimension;
            
            // Update cache and generators
            mapCache.setWorldState(seed, dimension, zoomLevel);
            biomeGenerator.setSeed(seed, dimension);
            structureGenerator.setSeed(seed, dimension);
            
            // Clear pending tiles
            pendingTiles.clear();
        }
    }
    
    /**
     * Set viewport position
     * @param centerX Center X coordinate (world coordinates)
     * @param centerZ Center Z coordinate (world coordinates)
     */
    public void setViewportCenter(double centerX, double centerZ) {
        this.viewportCenterX = centerX;
        this.viewportCenterZ = centerZ;
    }
    
    /**
     * Set viewport size
     * @param width Viewport width in pixels
     * @param height Viewport height in pixels
     */
    public void setViewportSize(int width, int height) {
        this.viewportWidth = Math.max(1, width);
        this.viewportHeight = Math.max(1, height);
    }
    
    /**
     * Set zoom level
     * @param zoomLevel Zoom level (0 = 1:1, positive = zoomed out, negative = zoomed in)
     */
    public void setZoomLevel(int zoomLevel) {
        int clampedZoom = Math.max(MIN_ZOOM_LEVEL, Math.min(MAX_ZOOM_LEVEL, zoomLevel));
        if (this.zoomLevel != clampedZoom) {
            this.zoomLevel = clampedZoom;
            this.scale = Math.pow(2.0, clampedZoom);
            
            // Update cache for new zoom level
            mapCache.setWorldState(currentSeed, currentDimension, zoomLevel);
            
            // Clear pending tiles
            pendingTiles.clear();
        }
    }
    
    /**
     * Zoom in by one level
     */
    public void zoomIn() {
        setZoomLevel(zoomLevel - 1);
    }
    
    /**
     * Zoom out by one level
     */
    public void zoomOut() {
        setZoomLevel(zoomLevel + 1);
    }
    
    /**
     * Set rendering flags
     * @param flags Combination of TileRenderer.RENDER_* flags
     */
    public void setRenderFlags(int flags) {
        if (this.renderFlags != flags) {
            this.renderFlags = flags;
            // Clear tile cache since rendering has changed
            mapCache.clearTiles();
            pendingTiles.clear();
        }
    }
    
    /**
     * Pan the viewport
     * @param deltaX X offset in world coordinates
     * @param deltaZ Z offset in world coordinates
     */
    public void pan(double deltaX, double deltaZ) {
        setViewportCenter(viewportCenterX + deltaX, viewportCenterZ + deltaZ);
    }
    
    /**
     * Render the current viewport
     * @return BufferedImage with the rendered map
     */
    public BufferedImage renderViewport() {
        long startTime = System.nanoTime();
        totalRenderRequests++;
        
        try {
            // Create output image
            BufferedImage viewportImage = new BufferedImage(viewportWidth, viewportHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = viewportImage.createGraphics();
            
            // Fill with background color
            g2d.setColor(colorPalette.getOceanColor());
            g2d.fillRect(0, 0, viewportWidth, viewportHeight);
            
            // Get required tiles
            List<MapCache.TileRequest> missingTiles = mapCache.getTilesForViewport(
                viewportCenterX, viewportCenterZ, viewportWidth, viewportHeight, 
                zoomLevel, renderFlags);
            
            // Start async rendering of missing tiles
            for (MapCache.TileRequest tileRequest : missingTiles) {
                String tileKey = getTileKey(tileRequest);
                
                if (!pendingTiles.containsKey(tileKey)) {
                    CompletableFuture<TileRenderer.RenderedTile> future = tileRenderer.renderTileAsync(tileRequest);
                    pendingTiles.put(tileKey, future);
                }
            }
            
            // Render all available tiles
            renderAvailableTiles(g2d, viewportCenterX, viewportCenterZ, 
                               viewportWidth, viewportHeight, zoomLevel, renderFlags);
            
            // Cleanup completed tiles
            cleanupPendingTiles();
            
            g2d.dispose();
            
            long endTime = System.nanoTime();
            long renderTime = (endTime - startTime) / 1_000_000;
            totalRenderTime += renderTime;
            lastFrameTime = renderTime;
            
            return viewportImage;
            
        } catch (Exception e) {
            System.err.println("Error rendering viewport: " + e.getMessage());
            e.printStackTrace();
            return createErrorImage();
        }
    }
    
    /**
     * Render all available tiles in the viewport
     */
    private void renderAvailableTiles(Graphics2D g2d, double centerX, double centerZ,
                                    int width, int height, int zoom, int flags) {
        // Calculate tile coverage
        double scale = Math.pow(2.0, zoom);
        double pixelsPerBlock = 1.0 / scale;
        int tileSize = 256; // Standard tile size
        double blocksPerTile = tileSize / pixelsPerBlock;
        
        // Calculate tile range
        int startTileX = (int) Math.floor((centerX - width * scale / 2) / blocksPerTile);
        int endTileX = (int) Math.ceil((centerX + width * scale / 2) / blocksPerTile);
        int startTileZ = (int) Math.floor((centerZ - height * scale / 2) / blocksPerTile);
        int endTileZ = (int) Math.ceil((centerZ + height * scale / 2) / blocksPerTile);
        
        // Render each tile
        for (int tileX = startTileX; tileX <= endTileX; tileX++) {
            for (int tileZ = startTileZ; tileZ <= endTileZ; tileZ++) {
                MapCache.CachedMapTile cachedTile = mapCache.getCachedTile(tileX, tileZ, zoom, flags);
                
                if (cachedTile != null && cachedTile.isValid()) {
                    // Calculate screen position for this tile
                    double tileWorldX = tileX * blocksPerTile;
                    double tileWorldZ = tileZ * blocksPerTile;
                    
                    int screenX = (int) ((tileWorldX - centerX) / scale + width / 2);
                    int screenZ = (int) ((tileWorldZ - centerZ) / scale + height / 2);
                    
                    int tileScreenWidth = (int) Math.ceil(blocksPerTile / scale);
                    int tileScreenHeight = (int) Math.ceil(blocksPerTile / scale);
                    
                    // Draw the tile
                    g2d.drawImage(cachedTile.image, screenX, screenZ, 
                                tileScreenWidth, tileScreenHeight, null);
                }
            }
        }
    }
    
    /**
     * Get a unique key for a tile request
     */
    private String getTileKey(MapCache.TileRequest request) {
        return String.format("%d,%d,%d,%d", request.tileX, request.tileZ, request.zoomLevel, request.renderFlags);
    }
    
    /**
     * Clean up completed pending tiles
     */
    private void cleanupPendingTiles() {
        pendingTiles.entrySet().removeIf(entry -> entry.getValue().isDone());
    }
    
    /**
     * Create an error image when rendering fails
     */
    private BufferedImage createErrorImage() {
        BufferedImage errorImage = new BufferedImage(viewportWidth, viewportHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = errorImage.createGraphics();
        
        // Fill with error pattern
        g2d.setColor(Color.MAGENTA);
        g2d.fillRect(0, 0, viewportWidth, viewportHeight);
        
        g2d.setColor(Color.BLACK);
        g2d.drawString("Rendering Error", viewportWidth / 2 - 50, viewportHeight / 2);
        
        g2d.dispose();
        return errorImage;
    }
    
    // ===============================
    // Coordinate Conversion
    // ===============================
    
    /**
     * Convert screen coordinates to world coordinates
     * @param screenX Screen X coordinate
     * @param screenZ Screen Z coordinate
     * @return World coordinates as double[2] {worldX, worldZ}
     */
    public double[] screenToWorld(int screenX, int screenZ) {
        double worldX = viewportCenterX + (screenX - viewportWidth / 2.0) * scale;
        double worldZ = viewportCenterZ + (screenZ - viewportHeight / 2.0) * scale;
        return new double[]{worldX, worldZ};
    }
    
    /**
     * Convert world coordinates to screen coordinates
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return Screen coordinates as int[2] {screenX, screenZ}
     */
    public int[] worldToScreen(double worldX, double worldZ) {
        int screenX = (int) ((worldX - viewportCenterX) / scale + viewportWidth / 2.0);
        int screenZ = (int) ((worldZ - viewportCenterZ) / scale + viewportHeight / 2.0);
        return new int[]{screenX, screenZ};
    }
    
    // ===============================
    // Utility Methods
    // ===============================
    
    /**
     * Pre-render tiles around the current viewport for smooth scrolling
     * @param bufferTiles Number of tiles to buffer around viewport
     */
    public void preRenderTiles(int bufferTiles) {
        // Calculate extended viewport
        int extendedWidth = (int) (viewportWidth * (1 + bufferTiles * 0.25));
        int extendedHeight = (int) (viewportHeight * (1 + bufferTiles * 0.25));
        
        // Get tiles for extended area
        List<MapCache.TileRequest> bufferTileRequests = mapCache.getTilesForViewport(
            viewportCenterX, viewportCenterZ, extendedWidth, extendedHeight, 
            zoomLevel, renderFlags);
        
        // Start rendering buffer tiles (lower priority)
        renderExecutor.submit(() -> {
            for (MapCache.TileRequest tileRequest : bufferTileRequests) {
                String tileKey = getTileKey(tileRequest);
                if (!pendingTiles.containsKey(tileKey)) {
                    tileRenderer.renderTileAsync(tileRequest);
                }
            }
        });
    }
    
    /**
     * Get current rendering performance metrics
     * @return RenderingStats with performance data
     */
    public RenderingStats getStats() {
        MapCache.MapCacheStats cacheStats = mapCache.getStats();
        double avgRenderTime = totalRenderRequests > 0 ? (double) totalRenderTime / totalRenderRequests : 0;
        
        return new RenderingStats(
            totalRenderRequests, avgRenderTime, lastFrameTime,
            pendingTiles.size(), cacheStats,
            viewportCenterX, viewportCenterZ, zoomLevel, scale,
            currentSeed, currentDimension
        );
    }
    
    /**
     * Force cache maintenance
     */
    public void performMaintenance() {
        mapCache.performMaintenance();
        cleanupPendingTiles();
    }
    
    /**
     * Clean up resources
     */
    public void shutdown() {
        tileRenderer.shutdown();
        renderExecutor.shutdown();
        mapCache.clearAll();
        pendingTiles.clear();
    }
    
    // ===============================
    // Getters
    // ===============================
    
    public double getViewportCenterX() { return viewportCenterX; }
    public double getViewportCenterZ() { return viewportCenterZ; }
    public int getViewportWidth() { return viewportWidth; }
    public int getViewportHeight() { return viewportHeight; }
    public int getZoomLevel() { return zoomLevel; }
    public double getScale() { return scale; }
    public int getRenderFlags() { return renderFlags; }
    public long getCurrentSeed() { return currentSeed; }
    public int getCurrentDimension() { return currentDimension; }
    
    public int getMinZoomLevel() { return MIN_ZOOM_LEVEL; }
    public int getMaxZoomLevel() { return MAX_ZOOM_LEVEL; }
    
    // ===============================
    // Statistics Class
    // ===============================
    
    /**
     * Rendering performance statistics
     */
    public static class RenderingStats {
        public final long totalRenderRequests;
        public final double avgRenderTimeMs;
        public final long lastFrameTimeMs;
        public final int pendingTiles;
        public final MapCache.MapCacheStats cacheStats;
        public final double viewportCenterX, viewportCenterZ;
        public final int zoomLevel;
        public final double scale;
        public final long currentSeed;
        public final int currentDimension;
        
        RenderingStats(long totalRenderRequests, double avgRenderTimeMs, long lastFrameTimeMs,
                      int pendingTiles, MapCache.MapCacheStats cacheStats,
                      double viewportCenterX, double viewportCenterZ, int zoomLevel, double scale,
                      long currentSeed, int currentDimension) {
            this.totalRenderRequests = totalRenderRequests;
            this.avgRenderTimeMs = avgRenderTimeMs;
            this.lastFrameTimeMs = lastFrameTimeMs;
            this.pendingTiles = pendingTiles;
            this.cacheStats = cacheStats;
            this.viewportCenterX = viewportCenterX;
            this.viewportCenterZ = viewportCenterZ;
            this.zoomLevel = zoomLevel;
            this.scale = scale;
            this.currentSeed = currentSeed;
            this.currentDimension = currentDimension;
        }
        
        @Override
        public String toString() {
            return String.format("Render: %d frames (%.1fms avg, %dms last), %d pending | %s | View: (%.0f,%.0f) zoom %d",
                totalRenderRequests, avgRenderTimeMs, lastFrameTimeMs, pendingTiles, 
                cacheStats.toString(), viewportCenterX, viewportCenterZ, zoomLevel);
        }
    }
}
