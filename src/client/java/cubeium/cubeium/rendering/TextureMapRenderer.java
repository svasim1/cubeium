package cubeium.cubeium.rendering;

import cubeium.cubeium.world.MapCache;
import net.minecraft.client.gui.DrawContext;

/**
 * BlazeMap-inspired map renderer with optimized rendering approach.
 * Uses optimized fill operations while texture approach is being developed.
 */
public class TextureMapRenderer {
    
    private final MapCache mapCache;
    
    // Viewport state
    private int viewportWidth, viewportHeight;
    private int lastCenterX, lastCenterZ, lastZoomLevel;
    private long lastSeed = -1;
    private boolean needsUpdate = true;
    
    public TextureMapRenderer(MapCache mapCache) {
        this.mapCache = mapCache;
    }
    
    /**
     * Render the map using a single texture quad (BlazeMap approach)
     */
    public void renderMap(DrawContext context, long seed, 
                         int screenX, int screenY, int screenWidth, int screenHeight,
                         int mapCenterX, int mapCenterZ, int zoomLevel) {
        
        // Check if we need to update texture
        if (needsUpdate || seed != lastSeed || mapCenterX != lastCenterX || 
            mapCenterZ != lastCenterZ || zoomLevel != lastZoomLevel ||
            screenWidth != viewportWidth || screenHeight != viewportHeight) {
            
            updateTexture(seed, mapCenterX, mapCenterZ, screenWidth, screenHeight, zoomLevel);
            lastSeed = seed;
            lastCenterX = mapCenterX;
            lastCenterZ = mapCenterZ;
            lastZoomLevel = zoomLevel;
            viewportWidth = screenWidth;
            viewportHeight = screenHeight;
            needsUpdate = false;
        }
        
        // Render as single texture quad using simple fill approach for now
        // TODO: Implement proper texture rendering once we get the API right
        renderWithFallback(context, seed, screenX, screenY, screenWidth, screenHeight, 
                          mapCenterX, mapCenterZ, zoomLevel);
    }
    
    /**
     * Fallback to optimized fill rendering while we work on texture approach
     */
    private void renderWithFallback(DrawContext context, long seed, int screenX, int screenY, 
                                   int screenWidth, int screenHeight, int mapCenterX, int mapCenterZ, int zoomLevel) {
        
        // Calculate blocks to display
        int blocksWidth = Math.max(1, screenWidth / zoomLevel);
        int blocksHeight = Math.max(1, screenHeight / zoomLevel);
        
        // Get biome data
        int[] biomeData = mapCache.getBiomeArea(seed, mapCenterX, mapCenterZ, 
                                               blocksWidth, blocksHeight, zoomLevel);
        
        if (biomeData == null) {
            return; // Not ready yet
        }
        
        // Render in larger blocks for better performance
        int blockSize = Math.max(1, zoomLevel);
        
        for (int z = 0; z < blocksHeight; z++) {
            for (int x = 0; x < blocksWidth; x++) {
                int biomeIndex = z * blocksWidth + x;
                if (biomeIndex >= biomeData.length) continue;
                
                int biomeId = biomeData[biomeIndex];
                int color = getBiomeColor(biomeId);
                
                int pixelX = screenX + x * blockSize;
                int pixelY = screenY + z * blockSize;
                
                // Use larger fill operations instead of 1x1 pixels
                context.fill(pixelX, pixelY, pixelX + blockSize, pixelY + blockSize, color);
            }
        }
    }
    
    /**
     * Update the dynamic texture with current map data (future implementation)
     */
    private void updateTexture(long seed, int centerX, int centerZ, 
                              int width, int height, int zoomLevel) {
        
        // Placeholder for future texture-based implementation
        // For now we use the fallback approach
        needsUpdate = true;
    }
    
    /**
     * Get biome color
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
     * Mark texture as needing update
     */
    public void invalidate() {
        needsUpdate = true;
    }
    
    /**
     * Clean up resources
     */
    public void close() {
        // No resources to clean up in current implementation
    }
}
