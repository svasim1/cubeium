package cubeium.cubeium.blazemap;

import cubeium.cubeium.world.MapCache;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.MinecraftClient;

/**
 * BlazeMap-style renderer for seed maps using optimized rendering techniques.
 * Implements BlazeMap's smooth rendering approach adapted for Fabric.
 */
public class SeedMapRenderer {
    
    private final MapCache mapCache;
    private final MinecraftClient client;
    
    // Performance tracking
    private long lastRenderTime = 0;
    private static int renderCallCount = 0;
    
    public SeedMapRenderer(MapCache mapCache) {
        this.mapCache = mapCache;
        this.client = MinecraftClient.getInstance();
    }
    
    /**
     * Render the seed map using BlazeMap's optimized approach
     */
    public void render(DrawContext context, long seed, double centerX, double centerZ, 
                      double scale, int screenX, int screenY, int screenWidth, int screenHeight) {
        
        renderCallCount++;
        if (renderCallCount <= 3 || renderCallCount % 60 == 0) {
                        // render call debug log removed
        }
        
        // Generate biome data
        int blocksWidth = (int) Math.ceil(screenWidth * scale);
        int blocksHeight = (int) Math.ceil(screenHeight * scale);
        int startX = (int) (centerX - blocksWidth / 2.0);
        int startZ = (int) (centerZ - blocksHeight / 2.0);
        
        // Get biome data from cache
        int[] biomeData = mapCache.getBiomeArea(seed, startX, startZ, blocksWidth, blocksHeight, Math.max(1, (int) scale));
        
        // Debug output
        if (biomeData == null) {
                        // no biome data debug log removed
        } else {
            // Show first few biomes and their colors to debug what we're getting
            StringBuilder biomePreview = new StringBuilder();
            java.util.Set<Integer> uniqueBiomes = new java.util.HashSet<>();
            for (int i = 0; i < Math.min(20, biomeData.length); i++) {
                int biomeId = biomeData[i];
                uniqueBiomes.add(biomeId);
                if (i < 10) {
                    int color = getBiomeColor(biomeId);
                    biomePreview.append(biomeId).append("(#").append(String.format("%06X", color & 0xFFFFFF)).append(") ");
                }
            }
                        // biome data debug logs removed
        }
        
        if (biomeData != null) {
            // BlazeMap-style optimized rendering: render in efficient blocks
            renderBiomeData(context, biomeData, blocksWidth, blocksHeight, 
                           screenX, screenY, screenWidth, screenHeight, scale);
        } else {
            // Render loading indicator
            renderLoadingIndicator(context, screenX, screenY, screenWidth, screenHeight);
        }
        
        // Performance overlay (BlazeMap-style)
        renderPerformanceInfo(context, screenX, screenY);
    }
    
    /**
     * Render biome data using BlazeMap's efficient block rendering
     */
    private void renderBiomeData(DrawContext context, int[] biomeData, 
                                int blocksWidth, int blocksHeight,
                                int screenX, int screenY, int screenWidth, int screenHeight, 
                                double scale) {
        
        // BlazeMap approach: render larger blocks for efficiency instead of individual pixels
        // Remove unused blockSize calculation
        
        // Track what we're actually rendering
        java.util.Map<Integer, Integer> colorCounts = new java.util.HashMap<>();
        
        for (int z = 0; z < blocksHeight; z++) {
            for (int x = 0; x < blocksWidth; x++) {
                int biomeIndex = z * blocksWidth + x;
                if (biomeIndex >= biomeData.length) continue;
                
                int biomeId = biomeData[biomeIndex];
                int color = getBiomeColor(biomeId);
                
                // Track color usage
                colorCounts.put(color, colorCounts.getOrDefault(color, 0) + 1);
                
                // Calculate screen position
                int pixelX = screenX + (int) (x / scale);
                int pixelY = screenY + (int) (z / scale);
                int pixelEndX = screenX + (int) ((x + 1) / scale);
                int pixelEndY = screenY + (int) ((z + 1) / scale);
                
                // Ensure we stay within bounds
                pixelEndX = Math.min(pixelEndX, screenX + screenWidth);
                pixelEndY = Math.min(pixelEndY, screenY + screenHeight);
                
                if (pixelX < screenX + screenWidth && pixelY < screenY + screenHeight) {
                    // BlazeMap-style efficient fill operation
                    context.fill(pixelX, pixelY, pixelEndX, pixelEndY, color | 0xFF000000);
                }
            }
        }
        
        // Debug: show what colors we're actually rendering
                            // colorCounts debug log removed
    }
    
    /**
     * Get biome color (BlazeMap-style vibrant colors)
     */
    private int getBiomeColor(int biomeId) {
        return switch (biomeId) {
            // Water biomes - Blues
            case 0 -> 0xFF1E3A8A;  // Ocean - deep blue
            case 7 -> 0xFF3B82F6;  // River - bright blue  
            case 10 -> 0xFFBFDBFE; // Frozen Ocean - ice blue
            case 46, 47, 48, 49 -> 0xFF2563EB; // Other oceans - blue variants
            
            // Plains and grasslands - Greens
            case 1 -> 0xFF22C55E;  // Plains - green
            case 129 -> 0xFF84CC16; // Sunflower Plains - lime green
            
            // Desert biomes - Yellows/Oranges
            case 2 -> 0xFFF59E0B;  // Desert - orange
            case 17, 130 -> 0xFFEAB308; // Desert variants - yellow
            
            // Mountain biomes - Grays
            case 3 -> 0xFF6B7280;  // Mountains - gray
            case 34, 131, 162 -> 0xFF9CA3AF; // Mountain variants - light gray
            
            // Forest biomes - Dark greens
            case 4 -> 0xFF166534;  // Forest - dark green
            case 18, 132 -> 0xFF15803D; // Forest variants - green
            case 27, 28 -> 0xFF65A30D; // Birch - lime
            case 29, 157 -> 0xFF14532D; // Dark Forest - very dark green
            
            // Taiga biomes - Dark greens with blue tint
            case 5, 19, 30, 31, 32, 33, 133, 158 -> 0xFF047857; // Taiga variants - teal green
            
            // Swamp biomes - Muddy colors
            case 6, 134 -> 0xFF92400E; // Swamp - brown
            
            // Jungle biomes - Bright greens
            case 21, 22, 23, 149, 151, 168, 169 -> 0xFF16A34A; // Jungle variants - bright green
            
            // Snow biomes - Whites
            case 12, 13, 26, 140 -> 0xFFF8FAFC; // Snow variants - white
            
            // Mesa/Badlands biomes - Reds/Oranges
            case 37, 38, 39, 165, 166, 167 -> 0xFFDC2626; // Mesa variants - red
            
            // Savanna biomes - Yellow-greens
            case 35, 36, 163, 164 -> 0xFFA3A922; // Savanna variants - olive
            
            // Nether biomes - Reds/Purples
            case 8 -> 0xFF991B1B; // Nether - dark red
            
            // End biomes - Purples/Yellows
            case 9 -> 0xFF7C3AED; // End - purple
            
            // Cave biomes - Underground colors  
            case 178 -> 0xFF8B5A2B; // Lush Caves - brown-green
            case 175 -> 0xFF8B5A2B; // Lush Caves variant - brown-green
            case 174 -> 0xFF2D1B69; // Dripstone Caves - dark blue
            case 177 -> 0xFF374151; // Deep Dark - very dark gray
            
            default -> 0xFF6B7280; // Unknown - gray
        };
    }
    
    /**
     * Render loading indicator (BlazeMap-style)
     */
    private void renderLoadingIndicator(DrawContext context, int x, int y, int width, int height) {
        context.fill(x, y, x + width, y + height, 0xFF202020);
        
        String loading = "Loading seed map...";
        int textWidth = client.textRenderer.getWidth(loading);
        context.drawText(client.textRenderer, loading, 
                        x + (width - textWidth) / 2, 
                        y + height / 2, 
                        0xFFFFFFFF, true);
    }
    
    /**
     * Render performance info (BlazeMap-style)
     */
    private void renderPerformanceInfo(DrawContext context, int x, int y) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRenderTime > 1000) {
            lastRenderTime = currentTime;
        }
        
        // Show scale info like BlazeMap
        String info = "Seed Map | BlazeMap Style";
        context.drawText(client.textRenderer, info, x + 5, y + 5, 0xFFFFFFFF, true);
    }
    
    /**
     * Invalidate renderer for next frame
     */
    public void invalidate() {
        // Mark for update on next render
    }
    
    /**
     * Clean up resources
     */
    public void close() {
        // No resources to clean in this implementation
    }
}
