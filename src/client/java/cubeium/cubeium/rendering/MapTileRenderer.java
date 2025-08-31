package cubeium.cubeium.rendering;

import cubeium.cubeium.world.MapCache;
import net.minecraft.client.gui.DrawContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * High-performance tile-based map renderer similar to OpenLayers/Google Maps.
 * Uses cached biome data tiles for smooth panning and zooming.
 */
public class MapTileRenderer {
    
    // Tile configuration
    private static final int TILE_SIZE = 128; // 128x128 blocks per tile (reasonable for Minecraft)
    private static final int MAX_CACHED_TILES = 200; // Memory limit
    
    // Tile cache: stores pre-computed biome data
    private final Map<TileKey, CachedTile> tileCache = new ConcurrentHashMap<>();
    private final MapCache mapCache;
    
    public MapTileRenderer(MapCache mapCache) {
        this.mapCache = mapCache;
    }
    
    /**
     * Render the visible map area using tiles.
     * Only renders tiles that are actually visible in viewport.
     */
    public void renderMap(DrawContext context, long seed, 
                         int screenX, int screenY, int screenWidth, int screenHeight,
                         int mapCenterX, int mapCenterZ, int zoomLevel) {
        
        // Calculate which tiles are visible
        int blocksPerPixel = zoomLevel;
        int viewWidthBlocks = screenWidth * blocksPerPixel;
        int viewHeightBlocks = screenHeight * blocksPerPixel;
        
        // World coordinates of viewport corners
        int worldLeft = mapCenterX - viewWidthBlocks / 2;
        int worldTop = mapCenterZ - viewHeightBlocks / 2;
        int worldRight = worldLeft + viewWidthBlocks;
        int worldBottom = worldTop + viewHeightBlocks;
        
        // Convert to tile coordinates
        int tileLeft = Math.floorDiv(worldLeft, TILE_SIZE);
        int tileTop = Math.floorDiv(worldTop, TILE_SIZE);
        int tileRight = Math.floorDiv(worldRight, TILE_SIZE);
        int tileBottom = Math.floorDiv(worldBottom, TILE_SIZE);
        
        // SAFETY: Limit tile range to prevent infinite loops
        int maxTileRange = 100; // Maximum 100 tiles in any direction
        tileLeft = Math.max(tileLeft, -maxTileRange);
        tileTop = Math.max(tileTop, -maxTileRange);
        tileRight = Math.min(tileRight, maxTileRange);
        tileBottom = Math.min(tileBottom, maxTileRange);
        
        // Additional safety check: if range is too large, something is wrong
        int tileRangeX = tileRight - tileLeft;
        int tileRangeZ = tileBottom - tileTop;
        if (tileRangeX > maxTileRange * 2 || tileRangeZ > maxTileRange * 2) {
            System.err.println("[MapTileRenderer] ERROR: Tile range too large: " + tileRangeX + "x" + tileRangeZ + 
                             " (zoom: " + zoomLevel + ", viewport: " + screenWidth + "x" + screenHeight + ")");
            return; // Abort rendering to prevent infinite loop
        }
        
        // Render visible tiles only
        for (int tileZ = tileTop; tileZ <= tileBottom; tileZ++) {
            for (int tileX = tileLeft; tileX <= tileRight; tileX++) {
                renderTile(context, seed, tileX, tileZ, zoomLevel, 
                          screenX, screenY, screenWidth, screenHeight, 
                          mapCenterX, mapCenterZ, blocksPerPixel);
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
    private void renderTile(DrawContext context, long seed, int tileX, int tileZ, int zoomLevel,
                           int screenX, int screenY, int screenWidth, int screenHeight,
                           int mapCenterX, int mapCenterZ, int blocksPerPixel) {
        
        TileKey key = new TileKey(seed, tileX, tileZ, zoomLevel);
        CachedTile tile = tileCache.get(key);
        
        // Generate tile if not cached
        if (tile == null || tile.isExpired()) {
            tile = generateTile(seed, tileX, tileZ, zoomLevel);
            if (tile != null) {
                tileCache.put(key, tile);
            }
        }
        
        // Render tile if ready
        if (tile != null && tile.biomeData != null) {
            renderTileData(context, tile, tileX, tileZ, screenX, screenY, 
                          screenWidth, screenHeight, mapCenterX, mapCenterZ, blocksPerPixel);
        }
    }
    
    /**
     * Render the biome data from a cached tile
     */
    private void renderTileData(DrawContext context, CachedTile tile, int tileX, int tileZ,
                               int screenX, int screenY, int screenWidth, int screenHeight,
                               int mapCenterX, int mapCenterZ, int blocksPerPixel) {
        
        // World coordinates of this tile's top-left corner
        int tileWorldX = tileX * TILE_SIZE;
        int tileWorldZ = tileZ * TILE_SIZE;
        
        // Calculate screen position: 
        // Convert world offset to screen pixels, then center on screen
        int worldOffsetX = tileWorldX - mapCenterX;
        int worldOffsetZ = tileWorldZ - mapCenterZ;
        
        int tileScreenX = screenX + screenWidth / 2 + worldOffsetX / blocksPerPixel;
        int tileScreenZ = screenY + screenHeight / 2 + worldOffsetZ / blocksPerPixel;
        
        int pixelsPerTile = TILE_SIZE / blocksPerPixel;
        if (pixelsPerTile < 1) pixelsPerTile = 1;
        
        // Clip to screen bounds
        int startX = Math.max(0, screenX - tileScreenX);
        int startZ = Math.max(0, screenY - tileScreenZ);
        int endX = Math.min(pixelsPerTile, screenX + screenWidth - tileScreenX);
        int endZ = Math.min(pixelsPerTile, screenY + screenHeight - tileScreenZ);
        
        if (startX >= endX || startZ >= endZ) return; // Tile not visible
        
        // Render visible portion of tile
        int pixelSize = Math.max(1, blocksPerPixel);
        
        for (int z = startZ; z < endZ; z++) {
            for (int x = startX; x < endX; x++) {
                int biomeIndex = z * pixelsPerTile + x;
                if (biomeIndex >= 0 && biomeIndex < tile.biomeData.length) {
                    int biomeId = tile.biomeData[biomeIndex];
                    int color = getBiomeColor(biomeId);
                    
                    int pixelX = tileScreenX + x;
                    int pixelZ = tileScreenZ + z;
                    
                    context.fill(pixelX, pixelZ, pixelX + pixelSize, pixelZ + pixelSize, color);
                }
            }
        }
    }
    
    /**
     * Generate a tile by loading biome data from cache.
     */
    private CachedTile generateTile(long seed, int tileX, int tileZ, int zoomLevel) {
        // SAFETY CHECK: Prevent excessive zoom levels that cause infinite loops
        if (zoomLevel > 4096) {
            System.out.println("[MapTileRenderer] WARNING: Zoom level too high (" + zoomLevel + "), skipping tile generation");
            return null; // Skip tile generation for extremely zoomed out levels
        }
        
        if (zoomLevel < 1) {
            System.out.println("[MapTileRenderer] WARNING: Zoom level too detailed (" + zoomLevel + "), skipping tile generation");
            return null; // Skip tile generation for invalid zoom levels
        }
        
        int tileWorldX = tileX * TILE_SIZE + TILE_SIZE / 2; // Center of tile
        int tileWorldZ = tileZ * TILE_SIZE + TILE_SIZE / 2;
        
        int pixelsPerTile = Math.max(1, TILE_SIZE / zoomLevel);
        
        // ADDITIONAL SAFETY: Prevent tiny pixel sizes that cause massive getBiomeArea calls
        if (pixelsPerTile > 64) {
            System.out.println("[MapTileRenderer] WARNING: Pixels per tile too high (" + pixelsPerTile + "), capping at 64");
            pixelsPerTile = 64; // Cap at reasonable size
        }
        
        // Get biome data for this tile area
        int[] biomeData = mapCache.getBiomeArea(seed, tileWorldX, tileWorldZ,
                                               pixelsPerTile, pixelsPerTile, zoomLevel);
        
        if (biomeData == null) {
            return null; // Data not ready yet
        }
        
        return new CachedTile(biomeData, System.currentTimeMillis());
    }
    
    /**
     * Get biome color (optimized lookup)
     */
    private int getBiomeColor(int biomeId) {
        return switch (biomeId) {
            case 0 -> 0xFF4169E1;  // Ocean - blue
            case 1 -> 0xFF90EE90;  // Plains - light green
            case 2 -> 0xFFFFA500;  // Desert - orange
            case 3 -> 0xFF006400;  // Forest - dark green
            case 4 -> 0xFF32CD32;  // Jungle - lime
            case 5 -> 0xFFFFFFFF;  // Ice/Snow - white
            case 6 -> 0xFF8B4513;  // Mountain - brown
            case 7 -> 0xFF2F4F4F;  // Swamp - dark gray
            case 8 -> 0xFF800080;  // Nether - purple
            case 9 -> 0xFFFFFF00;  // End - yellow
            default -> 0xFF808080; // Unknown - gray
        };
    }
    
    /**
     * Remove old tiles when cache gets too full
     */
    private void cleanupOldTiles() {
        long cutoffTime = System.currentTimeMillis() - 60000; // 60 seconds
        tileCache.entrySet().removeIf(entry -> entry.getValue().createdAt < cutoffTime);
    }
    
    /**
     * Clear all cached tiles (call when changing seeds)
     */
    public void clearCache() {
        tileCache.clear();
    }
    
    /**
     * Get cache statistics
     */
    public int getCachedTileCount() {
        return tileCache.size();
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
    
    /**
     * Cached tile data
     */
    private static class CachedTile {
        final int[] biomeData;
        final long createdAt;
        
        CachedTile(int[] biomeData, long createdAt) {
            this.biomeData = biomeData;
            this.createdAt = createdAt;
        }
        
        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > 120000; // 2 minutes
        }
    }
}
