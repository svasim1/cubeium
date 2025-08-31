package cubeium.cubeium.blazemap;

import cubeium.cubeium.world.generation.BiomeGenerator;
import cubeium.cubeium.world.MapCache;
import cubeium.cubeium.rendering.MapTileRenderer;
import cubeium.cubeium.gui.MouseSubpixelSmoother;
import cubeium.cubeium.ui.SeedInputWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.client.render.Tessellator;

/**
 * Complete BlazeMap-style seed map screen.
 * Features professional map rendering, smooth navigation, and precise coordinate system.
 */
public class BlazeMapSeedScreen extends Screen {
    
    // Core systems
    private final BiomeGenerator biomeGenerator;
    private final MapCache mapCache;
    private final MapTileRenderer tileRenderer;
    private final MouseSubpixelSmoother mouseSmoother;
    
    // Map state (Fixed to match working SeedMapScreen approach)
    private long currentSeed = 0L;
    private boolean hasValidSeed = false;
    private boolean mapGenerated = false; // NEW: track if map generation has been started
    private int mapCenterX = 0; // FIXED: Use int like SeedMapScreen, not double
    private int mapCenterZ = 0; // FIXED: Use int like SeedMapScreen, not double
    private int zoomLevel = 4; // FIXED: Use SeedMapScreen default - 4 blocks per pixel
    
    // UI components
    private SeedInputWidget seedInput;
    private ButtonWidget generateButton;
    private ButtonWidget resetViewButton;
    
    // Mouse state
    private boolean isDragging = false;
    private boolean showCoordinates = true;
    private boolean showPerformanceInfo = true;
    
    // Viewport
    private int mapX, mapY, mapWidth, mapHeight;
    
    public BlazeMapSeedScreen() {
        super(Text.empty());
        
        try {
            // initialization log removed
            biomeGenerator = new BiomeGenerator();
            // init log removed
            
            mapCache = new MapCache(biomeGenerator);
            // init log removed
            
            tileRenderer = new MapTileRenderer(mapCache);
            // init log removed
            
            mouseSmoother = new MouseSubpixelSmoother();
            // init log removed
            
            // DO NOT initialize with any seed - wait for user input only
            currentSeed = 0L; // No default seed
            hasValidSeed = false;
            
            updateScale();
            // init log removed
        } catch (Exception e) {
            // initialization error log removed
            e.printStackTrace();
            throw new RuntimeException("BlazeMapSeedScreen initialization failed", e);
        }
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Calculate viewport (full screen minus UI areas)
        mapX = 10;
        mapY = 60;
        mapWidth = width - 20;
        mapHeight = height - 100;
        
        // Seed input field (BlazeMap-style positioned)
        seedInput = new SeedInputWidget(
            textRenderer,
            width / 2 - 100, 
            20,
            this::onSeedChanged
        );
        addDrawableChild(seedInput);
        
        // Generate button
        generateButton = ButtonWidget.builder(Text.literal("Generate"), button -> generateMap())
                .dimensions(width / 2 - 150, 45, 70, 20)
                .build();
        addDrawableChild(generateButton);
        
    // Random seed button removed per request
        
        // Reset view button
        resetViewButton = ButtonWidget.builder(Text.literal("Reset View"), button -> resetView())
                .dimensions(width / 2 + 5, 45, 80, 20)
                .build();
        addDrawableChild(resetViewButton);
        
        // Try to get current world seed like the working SeedMapScreen
        tryGetWorldSeed();
        
        // Update button state after initialization
        if (generateButton != null) {
            generateButton.active = hasValidSeed;
        }
        
    // UI init log removed
    }
    
    private void onSeedChanged(long seed, boolean isValid) {
    // seed change log removed
        this.currentSeed = seed;
        this.hasValidSeed = isValid;
        // IMPORTANT: Do NOT set mapGenerated = true here - only when user clicks Generate
        // This prevents automatic map generation when world seed is loaded
        
        // Only set button active if button exists (avoid null pointer during initialization)
        if (this.generateButton != null) {
            this.generateButton.active = isValid;
        }
    }
    
    private void tryGetWorldSeed() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.world != null) {
                // Try to get the world seed through integrated server
                if (client.getServer() != null && client.getServer().getWorld(client.world.getRegistryKey()) != null) {
                    long worldSeed = client.getServer().getWorld(client.world.getRegistryKey()).getSeed();
                    if (seedInput != null) {
                        seedInput.setText(String.valueOf(worldSeed));
                    }
                    // world seed log removed
                    return;
                }
            }
        } catch (Exception e) {
            // world seed error log removed
        }
        
        // Fallback: don't set any seed - wait for user input
        if (seedInput != null) {
            seedInput.setText("");
        }
    // no world seed log removed
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // BlazeMap-style dark background
        renderBackground(context, mouseX, mouseY, delta);
        
        // Render the main map
        renderMap(context, mouseX, mouseY);
        
        // Render UI overlays
        renderUI(context, mouseX, mouseY);
        
        // Render widgets
        super.render(context, mouseX, mouseY, delta);
        
        // RENDER TEST SQUARES LAST - AFTER EVERYTHING ELSE

    }
    
    // ... debug test squares removed
    
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    // render debug logs removed
        
        // TEMPORARILY DISABLE ALL BACKGROUND LAYERS TO FIND TEST SQUARES
        // Dark gray background - DISABLED
        // context.fill(0, 0, width, height, 0xFF1A1A1A);
        
        // Map area border - DISABLED  
        // context.fill(mapX - 2, mapY - 2, mapX + mapWidth + 2, mapY + mapHeight + 2, 0xFF2A2A2A);
        
    // render debug logs removed
    }
    
    /**
     * Render the map using tile renderer like the working SeedMapScreen
     */
    private void renderMap(DrawContext context, int mouseX, int mouseY) {
        // Map border
        context.drawBorder(mapX, mapY, mapWidth, mapHeight, 0xFF404040);

        // CRITICAL FIX: Only render map tiles after user explicitly clicks Generate
        // This prevents automatic rendering when world seed is loaded
        if (!mapGenerated || mapCache == null || tileRenderer == null) {
            // TEMPORARILY DISABLE placeholder fill that might be covering test squares
            // context.fill(mapX + 1, mapY + 1, mapX + mapWidth - 1, mapY + mapHeight - 1, 0xFF1a1a2e);
            
            String message = hasValidSeed ? "Click 'Generate' to render biomes" : "Enter a seed to start";
            int messageWidth = textRenderer.getWidth(message);
            context.drawText(textRenderer, message, 
                mapX + (mapWidth - messageWidth) / 2,
                mapY + mapHeight / 2, 0xFFAAAAAA, false);
            // render debug log removed
            return;
        }
        
    // ADDITIONAL SAFETY: Only render if map cache has sufficient data
    // Compute progress focused only on the visible viewport to avoid global world progress numbers
    int viewWidthBlocks = (mapWidth - 2) * zoomLevel;
    int viewHeightBlocks = (mapHeight - 2) * zoomLevel;
    float progress = mapCache.getGenerationProgressForViewport(currentSeed, mapCenterX, mapCenterZ, viewWidthBlocks, viewHeightBlocks);
        if (progress < 0.05f) { // Wait for at least 5% of data to be cached
            // TEMPORARILY DISABLE loading screen fill that might be covering test squares
            // context.fill(mapX + 1, mapY + 1, mapX + mapWidth - 1, mapY + mapHeight - 1, 0xFF1a1a2e);
            
            String message = String.format("Generating map data... %.0f%%", progress * 100);
            int messageWidth = textRenderer.getWidth(message);
            context.drawText(textRenderer, message, 
                mapX + (mapWidth - messageWidth) / 2,
                mapY + mapHeight / 2, 0xFFFFFFFF, false);
            // render debug log removed
            return;
        }
        
        // TEMPORARY DEBUG: Bypass tile renderer and render directly from biome data
            // debug bypass log removed
        renderDirectBiomeMap(context, currentSeed, 
                            mapX + 1, mapY + 1, mapWidth - 2, mapHeight - 2,
                            mapCenterX, mapCenterZ, zoomLevel);
        
    // Loading progress overlay removed per UI cleanup
        
        // Show cache statistics (viewport-focused progress)
        if (mapCache.hasCachedData(currentSeed)) {
            String stats = String.format("Chunks: %d | Tiles: %d | Progress: %.0f%%", 
                mapCache.getChunkCount(currentSeed),
                tileRenderer.getCachedTileCount(),
                progress * 100);
            context.drawText(textRenderer, stats, mapX + 5, mapY + 5, 0xFF888888, true);
        }
    }
    
    /**
     * Render UI overlays (BlazeMap-style)
     */
    private void renderUI(DrawContext context, int mouseX, int mouseY) {
    // Title removed per UI cleanup
        
    // Current seed and zoom displays removed per UI cleanup
        
        // Mouse coordinates (BlazeMap precision)
        if (showCoordinates && isMouseOverMap(mouseX, mouseY)) {
            renderMouseCoordinates(context, mouseX, mouseY);
        }
        
        // Controls help (BlazeMap-style)
        renderControls(context);
        
        // Performance info
        if (showPerformanceInfo) {
            renderPerformanceOverlay(context);
        }
    }
    
    /**
     * Render mouse coordinates with fixed coordinate calculation
     */
    private void renderMouseCoordinates(DrawContext context, int mouseX, int mouseY) {
        // FIXED: Use direct coordinate calculation like SeedMapScreen instead of broken scale calculation
        int relX = mouseX - mapX - 1;
        int relY = mouseY - mapY - 1;
        
        // Calculate world coordinates using SeedMapScreen's working approach
        // Adjust for map center being in screen center
        int mouseOffsetX = relX - mapWidth / 2;
        int mouseOffsetY = relY - mapHeight / 2;
        
        // Convert screen pixels to world blocks using direct zoom level like SeedMapScreen
        int worldX = mapCenterX + (mouseOffsetX * zoomLevel);
        int worldZ = mapCenterZ + (mouseOffsetY * zoomLevel);
        
        String coordText = String.format("X: %d, Z: %d", worldX, worldZ);
        int textWidth = textRenderer.getWidth(coordText);
        
        // Position near mouse with BlazeMap-style background
        int tooltipX = mouseX + 10;
        int tooltipY = mouseY - 10;
        
        // Keep tooltip on screen
        if (tooltipX + textWidth > width) tooltipX = mouseX - textWidth - 10;
        if (tooltipY < 0) tooltipY = mouseY + 20;
        
        // BlazeMap-style tooltip background
        context.fill(tooltipX - 2, tooltipY - 2, tooltipX + textWidth + 2, tooltipY + 10, 0xE0000000);
        context.drawText(textRenderer, coordText, tooltipX, tooltipY, 0xFFFFFF00, true);
    }
    
    /**
     * Render controls help
     */
    private void renderControls(DrawContext context) {
        String[] controls = {
            "Mouse Drag: Pan map",
            "Scroll: Zoom in/out", 
            "Arrow Keys: Navigate",
            "R: Reset view",
            "C: Toggle coordinates",
            "P: Toggle performance"
        };
        
        int startY = mapY + 10;
        for (int i = 0; i < controls.length; i++) {
            context.drawText(textRenderer, controls[i], width - 150, startY + i * 12, 0xFF888888, false);
        }
    }
    
    /**
     * Render performance overlay
     */
    private void renderPerformanceOverlay(DrawContext context) {
        String perf = String.format("Center: %d, %d | View: %dx%d blocks", 
                                   mapCenterX, mapCenterZ, 
                                   mapWidth * zoomLevel, mapHeight * zoomLevel);
        context.drawText(textRenderer, perf, mapX + 5, mapY + mapHeight - 15, 0xFF00FF00, true);
    }
    
    /**
     * Check if mouse is over map area
     */
    private boolean isMouseOverMap(int mouseX, int mouseY) {
        return mouseX >= mapX && mouseX < mapX + mapWidth &&
               mouseY >= mapY && mouseY < mapY + mapHeight;
    }
    
    /**
     * Generate map for current seed using working approach
     */
    private void generateMap() {
        if (mapCache == null) {
            // map cache null log removed
            return;
        }
        
        // Get the current seed from the input widget
        long tempSeed;
        if (seedInput != null && !seedInput.getText().isEmpty()) {
            try {
                String seedText = seedInput.getText().trim();
                if (seedText.toLowerCase().startsWith("0x")) {
                    tempSeed = Long.parseUnsignedLong(seedText.substring(2), 16);
                } else {
                    tempSeed = Long.parseLong(seedText);
                }
            } catch (NumberFormatException e) {
                // Treat as string seed - hash it
                tempSeed = (long) seedInput.getText().trim().hashCode();
            }
        } else {
            tempSeed = currentSeed;
        }
        
        final long seedToUse = tempSeed;
        
        // Update current seed to match what we're actually using
        this.currentSeed = seedToUse;
        this.hasValidSeed = true;
        this.mapGenerated = true; // FIXED: Set flag to allow rendering

        // Reset map center to origin (0, 0) when generating new map like SeedMapScreen
        this.mapCenterX = 0;
        this.mapCenterZ = 0;
        if (tileRenderer != null) {
            tileRenderer.clearCache();
        }
        
    // map generation started log removed
        
        // Start progressive generation - this returns immediately and loads in background
        mapCache.generateMapAsync(seedToUse).thenRun(() -> {
            // map generation completed log removed
        }).exceptionally(throwable -> {
            // map generation failed log removed
            return null;
        });
        // Start viewport-prioritized generation: pass view size in blocks
        int viewWidthBlocks = Math.max(1, (mapWidth - 2) * zoomLevel);
        int viewHeightBlocks = Math.max(1, (mapHeight - 2) * zoomLevel);
        mapCache.generateMapAsync(seedToUse, mapCenterX, mapCenterZ, viewWidthBlocks, viewHeightBlocks).thenRun(() -> {
            // map generation completed for prioritized queue
        }).exceptionally(throwable -> {
            return null;
        });
        
        // Map generation started - rendering will load from cache progressively
    }
    
    /**
     * Generate random seed using working approach
     */
    // random seed functionality removed from BlazeMap UI
    
    /**
     * Reset view to origin
     */
    private void resetView() {
        mapCenterX = 0;
        mapCenterZ = 0;
        zoomLevel = 4; // Reset to default 4 blocks per pixel
        // Clear map generated flag to prevent rendering until Generate is clicked
        mapGenerated = false;
    }
    
    /**
     * Update scale from zoom level (removed - not needed for SeedMapScreen compatibility)
     */
    private void updateScale() {
        // No longer needed - SeedMapScreen doesn't use scale
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle seed input first
        if (seedInput != null && seedInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        // Fixed controls to use SeedMapScreen approach
        int moveDistance = 32 * zoomLevel; // Pan by 32 blocks * zoom level
        
        switch (keyCode) {
            case 262: // Right arrow
                mapCenterX += moveDistance;
                return true;
            case 263: // Left arrow  
                mapCenterX -= moveDistance;
                return true;
            case 264: // Down arrow
                mapCenterZ += moveDistance;
                return true;
            case 265: // Up arrow
                mapCenterZ -= moveDistance;
                return true;
            case 82: // R key - reset view
                resetView();
                return true;
            case 67: // C key - toggle coordinates
                showCoordinates = !showCoordinates;
                return true;
            case 80: // P key - toggle performance
                showPerformanceInfo = !showPerformanceInfo;
                return true;
            case 256: // Escape
                close();
                return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Handle UI clicks first
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        // Handle map clicks
        if (isMouseOverMap((int) mouseX, (int) mouseY) && button == 0) {
            isDragging = true;
            mouseSmoother.reset();
            return true;
        }
        
        return false;
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isMouseOverMap((int) mouseX, (int) mouseY)) {
            // Fixed zoom to use SeedMapScreen approach - direct blocks per pixel
            if (verticalAmount > 0) {
                // Zoom in (fewer blocks per pixel)
                zoomLevel = Math.max(1, zoomLevel - 1);
            } else {
                // Zoom out (more blocks per pixel) 
                zoomLevel = Math.min(64, zoomLevel + 1);
            }
            return true;
        }
        
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && isDragging) {
            // Fixed dragging to use SeedMapScreen approach with integer coordinates
            mouseSmoother.addMovement(-deltaX * zoomLevel, -deltaY * zoomLevel);
            mapCenterX += (int) mouseSmoother.movementX();
            mapCenterZ += (int) mouseSmoother.movementY();
            return true;
        }
        
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public void close() {
    // closing log removed
        // Clean up resources when closing the screen
        if (mapCache != null) {
            // shutdown log removed
            mapCache.shutdown();
        }
        super.close();
    }
    
    /**
     * ENHANCED DEBUG METHOD: Render biomes using BufferBuilder system
     */
    private void renderDirectBiomeMap(DrawContext context, long seed, 
                                     int startX, int startY, int width, int height,
                                     int centerX, int centerZ, int blocksPerPixel) {
    // biome render debug logs removed
        
        // Get biome data for the visible map area using the same coordinate system
        int mapPixelWidth = mapWidth - 2; // Subtract border
        int mapPixelHeight = mapHeight - 2; // Subtract border
        
        try {
            // Use MapCache to get biome data for the current view
            int[] biomeData = mapCache.getBiomeArea(currentSeed, mapCenterX, mapCenterZ, 
                                                   mapPixelWidth, mapPixelHeight, zoomLevel);
            
            if (biomeData != null && biomeData.length > 0) {
                // biome data debug logs removed
                
                // Render each pixel using the biome data
                for (int pixelY = 0; pixelY < mapPixelHeight && pixelY < biomeData.length / mapPixelWidth; pixelY++) {
                    for (int pixelX = 0; pixelX < mapPixelWidth && (pixelY * mapPixelWidth + pixelX) < biomeData.length; pixelX++) {
                        int biomeId = biomeData[pixelY * mapPixelWidth + pixelX];
                        int biomeColor = getBiomeColor(biomeId);
                        
                        // Draw pixel at screen coordinates (inside map border)
                        int screenX = mapX + 1 + pixelX;
                        int screenY = mapY + 1 + pixelY;
                        
                        // Draw a small pixel (1x1)
                        context.fill(screenX, screenY, screenX + 1, screenY + 1, biomeColor);
                    }
                }
                
                // biome render finished log removed
                
                // Sample a few biomes for debugging
                if (biomeData.length > 0) {
                    // center biome sample removed
                }
                
            } else {
                // no biome data placeholder log removed
                // Neutral loading placeholder while biome data is generating
                context.fill(mapX + 10, mapY + 10, mapX + Math.min(mapX + 200, mapX + mapWidth - 10), mapY + 40, 0xFF2A2A2A);
                context.drawText(textRenderer, "Loading biome data...", mapX + 15, mapY + 16, 0xFFCCCCCC, false);
            }
            
        } catch (Exception e) {
            // biome data error logging removed
            
            // Error fallback - red error square
            context.fill(mapX + 10, mapY + 10, mapX + 50, mapY + 50, 0xFFFF0000);
        }
    }
    
    // drawQuad removed (not used)
    
    /**
     * ENHANCED DEBUG METHOD: Biome to color mapping with brighter colors
     */
    private int getBiomeColor(int biomeId) {
        // Using Amidst color scheme for accurate biome visualization
        switch (biomeId) {
            // Basic biomes
            case 0: return 0xFF000070; // Ocean - dark blue (0,0,112)
            case 1: return 0xFF8DB360; // Plains - olive green (141,179,96)
            case 2: return 0xFFFA9418; // Desert - orange (250,148,24)
            case 3: return 0xFF606060; // Mountains - gray (96,96,96)
            case 4: return 0xFF056621; // Forest - dark green (5,102,33)
            case 5: return 0xFF0B6A5F; // Taiga - custom teal (11,106,95)
            case 6: return 0xFF07F9B2; // Swamp - cyan (7,249,178)
            case 7: return 0xFF0000FF; // River - bright blue (0,0,255)
            case 8: return 0xFFFF0000; // Nether Wastes - red (255,0,0)
            case 9: return 0xFF8080FF; // End - light blue (128,128,255)
            case 10: return 0xFF7070D6; // Frozen Ocean - purple-blue (112,112,214)
            case 11: return 0xFFA0A0FF; // Frozen River - light purple (160,160,255)
            case 12: return 0xFFE0E0E0; // Snowy Plains (Snowy Tundra) - light gray (224,224,224)
            case 13: return 0xFFA0A0A0; // Snowy Mountains - light gray (160,160,160)
            case 14: return 0xFFFF00FF; // Mushroom Fields - magenta (255,0,255)
            case 15: return 0xFFA000FF; // Mushroom Field Shore - purple (160,0,255)
            case 16: return 0xFFFFDE55; // Beach - yellow (255,222,85)
            case 17: return 0xFFD25F12; // Desert Hills - brown (210,95,18)
            case 18: return 0xFF22551C; // Wooded Hills - dark green (34,85,28)
            case 19: return 0xFF163933; // Taiga Hills - dark teal (22,57,51)
            case 20: return 0xFF72789A; // Mountain Edge - blue-gray (114,120,154)
            case 21: return 0xFF537B09; // Jungle - jungle green (83,123,9)
            case 22: return 0xFF2C4205; // Jungle Hills - darker jungle (44,66,5)
            case 23: return 0xFF628B17; // Jungle Edge - lighter jungle (98,139,23)
            case 24: return 0xFF000030; // Deep Ocean - very dark blue (0,0,48)
            case 25: return 0xFFA2A284; // Stone Shore - beige-gray (162,162,132)
            case 26: return 0xFFFAF0C0; // Snowy Beach - light beige (250,240,192)
            case 27: return 0xFF307444; // Birch Forest - green (48,116,68)
            case 28: return 0xFF1F0532; // Birch Forest Hills - dark purple (31,5,50)
            case 29: return 0xFF40511A; // Dark Forest - dark olive (64,81,26)
            case 30: return 0xFF31554A; // Snowy Taiga - dark green-blue (49,85,74)
            case 31: return 0xFF243F36; // Snowy Taiga Hills - darker teal (36,63,54)
            case 32: return 0xFF596651; // Giant Tree Taiga - brown-green (89,102,81)
            case 33: return 0xFF45073E; // Giant Tree Taiga Hills - dark purple (69,7,62)
            case 34: return 0xFF507050; // Wooded Mountains - green-gray (80,112,80)
            case 35: return 0xFFBDB25F; // Savanna - custom olive-tan (189,178,95)
            case 36: return 0xFFA79D64; // Savanna Plateau - brown (167,157,100)
            case 37: return 0xFFD94515; // Badlands - orange-red (217,69,21)
            case 38: return 0xFFB09765; // Wooded Badlands Plateau - tan (176,151,101)
            case 39: return 0xFFCA8C65; // Badlands Plateau - tan (202,140,101)

            // Rare biomes  
            case 40: case 41: case 42: case 43: return 0xFF8080FF; // Sky biomes - light blue (128,128,255)

            // Ocean variants
            case 44: return 0xFF0000AC; // Warm Ocean - blue (0,0,172)
            case 45: return 0xFF000090; // Lukewarm Ocean - darker blue (0,0,144)
            case 46: return 0xFF202070; // Cold Ocean - purple-blue (32,32,112)
            case 47: return 0xFF000050; // Deep Warm Ocean - dark blue (0,0,80)
            case 48: return 0xFF000040; // Deep Lukewarm Ocean - darker (0,0,64)
            case 49: return 0xFF202038; // Deep Cold Ocean - dark purple (32,32,56)
            case 50: return 0xFF404090; // Deep Frozen Ocean - blue-gray (64,64,144)

            // Special/Unknown
            case 127: return 0xFF000000; // Void - black (0,0,0)

            // Hills variants (128+)
            case 129: return 0xFFB5DB88; // Sunflower Plains - light green (181,219,136)
            case 130: return 0xFFFFBC40; // Desert Lakes - light orange (255,188,64)
            case 131: return 0xFF888888; // Gravelly Mountains - gray (136,136,136)
            case 132: return 0xFF2D8E49; // Flower Forest - bright green (45,142,73)
            case 133: return 0xFF338E13; // Taiga Mountains - green (51,142,19)
            case 134: return 0xFF2FFF12; // Swamp Hills - bright green (47,255,18)

            // Other variants
            case 140: return 0xFFB4DCDC; // Ice Spikes - light cyan (180,220,220)
            case 149: return 0xFF7B0D31; // Modified Jungle - dark red (123,13,49)
            case 151: return 0xFF8AB33F; // Modified Jungle Edge - olive (138,179,63)
            case 155: return 0xFF589C6C; // Modified Birch Forest - teal (88,156,108)
            case 156: return 0xFF470F5A; // Modified Birch Forest Hills - purple (71,15,90)
            case 157: return 0xFF687942; // Tall Birch Forest - olive (104,121,66)
            case 158: return 0xFF597D72; // Tall Birch Hills - gray-green (89,125,114)
            case 160: return 0xFF818E79; // Modified Gravelly Mountains - gray-green (129,142,121)
            case 161: return 0xFF6D7766; // Modified Wooded Badlands - gray (109,119,102)
            case 162: return 0xFF783478; // Modified Badlands Plateau - purple (120,52,120)
            case 163: return 0xFFE5DA87; // Windswept Savanna (Shattered Savanna) - light yellow (229,218,135)
            case 165: return 0xFFFF6D3D; // Eroded Badlands - orange (255,109,61)
            // 1.14 biomes
            case 168: return 0xFF849500; // Bamboo Jungle - olive green (132,149,0)
            case 169: return 0xFFCFC58C; // Bamboo Jungle Hills - tan (207,197,140)
            
            // 1.16 Nether biomes
            case 170: return 0xFFFF6D3D; // Soul Sand Valley - orange (255,109,61)
            case 171: return 0xFFD8BF8D; // Crimson Forest - tan (216,191,141)
            case 172: return 0xFFF2B48D; // Warped Forest - light tan (242,180,141)
            case 173: return 0xFF768E14; // Basalt Deltas - olive (118,142,20)
            
            // 1.17 biomes
            case 174: return 0xFF3B470A; // Dripstone Caves - dark olive (59,71,10)
            case 175: return 0xFF522921; // Lush Caves - brown (82,41,33)
            
            // 1.18 biomes
            case 177: return 0xFF60A445; // Meadow - custom green (96,164,69)
            case 178: return 0xFF47726C; // Grove - custom gray-green (71,114,108)
            case 179: return 0xFFC4C4C4; // Snowy Slopes - custom light gray (196,196,196)
            case 180: return 0xFFDCDCC8; // Jagged Peaks - custom light gray (220,220,200)
            case 181: return 0xFFB0B3CE; // Frozen Peaks - custom light blue-gray (176,179,206)
            case 182: return 0xFF7B8F74; // Stony Peaks - custom green-gray (123,143,116)
            
            // 1.19 biomes
            case 183: return 0xFFDD0808; // Deep Dark - dark red (221,8,8)
            case 184: return 0xFF2CCC8E; // Mangrove Swamp - custom teal (44,204,142)
            
            // 1.20 biomes
            case 185: return 0xFFFF91C8; // Cherry Grove - custom pink (255,145,200)
            
            // 1.21 biomes
            case 186: return 0xFF696D95; // Pale Garden - custom blue-gray (105,109,149)
            
            // Fallback
            default: return 0xFFFF00FF; // Unknown - bright magenta
        }
    }
}
