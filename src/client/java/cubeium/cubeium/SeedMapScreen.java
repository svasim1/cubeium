package cubeium.cubeium;

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

/**
 * Functional Seed Map Screen with seed input and biome visualization.
 */
public class SeedMapScreen extends Screen {
    
    private BiomeGenerator biomeGenerator;
    private MapCache mapCache;
    private MapTileRenderer tileRenderer;
    private MouseSubpixelSmoother mouseSmoother;
    private SeedInputWidget seedInput;
    private ButtonWidget generateButton;
    private ButtonWidget randomSeedButton;
    
    private long currentSeed = 0L;
    private boolean hasValidSeed = false;
    private int mapCenterX = 0;
    private int mapCenterZ = 0;
    private int zoomLevel = 4; // Blocks per pixel (1=max zoom in, 32=max zoom out)
    
    // Map rendering area
    private int mapStartX, mapStartY, mapWidth, mapHeight;
    
    // Mouse dragging state
    private boolean isDragging = false;
    
    public SeedMapScreen() {
        super(Text.literal("Seed Map"));
        
        try {
            biomeGenerator = new BiomeGenerator();
            mapCache = new MapCache(biomeGenerator);
            tileRenderer = new MapTileRenderer(mapCache);
            mouseSmoother = new MouseSubpixelSmoother();
            System.out.println("[Cubeium] Seed Map Screen initialized with optimized rendering system");
        } catch (Exception e) {
            System.err.println("[Cubeium] Failed to initialize Seed Map Screen: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Calculate UI layout
        int centerX = width / 2;
        int topPanelHeight = 70; // Increased to give more space for seed input
        
        mapStartX = 10;
        mapStartY = topPanelHeight + 10;
        mapWidth = width - 20;
        mapHeight = height - topPanelHeight - 30;
        
        // Create seed input widget
        seedInput = new SeedInputWidget(
            textRenderer,
            centerX - 100,
            30, // Moved down to give more space
            this::onSeedChanged
        );
        addDrawableChild(seedInput);
        
        // Create generate button
        generateButton = ButtonWidget.builder(
            Text.literal("Generate Map"),
            button -> generateMap()
        )
        .dimensions(centerX + 110, 30, 80, 20)
        .build();
        addDrawableChild(generateButton);
        
        // Create random seed button
        randomSeedButton = ButtonWidget.builder(
            Text.literal("Random"),
            button -> generateRandomSeed()
        )
        .dimensions(centerX + 200, 30, 60, 20)
        .build();
        addDrawableChild(randomSeedButton);
        
        // Try to get current world seed
        tryGetWorldSeed();
        
        // Update button state after initialization
        if (generateButton != null) {
            generateButton.active = hasValidSeed;
        }
    }
    
    private void onSeedChanged(long seed, boolean isValid) {
        System.out.println("[Cubeium] Seed changed: " + seed + ", valid: " + isValid);
        this.currentSeed = seed;
        this.hasValidSeed = isValid;
        // Only set button active if button exists (avoid null pointer during initialization)
        if (this.generateButton != null) {
            this.generateButton.active = isValid;
        }
    }
    
    private void generateRandomSeed() {
        long randomSeed = (long)(Math.random() * Long.MAX_VALUE);
        System.out.println("[Cubeium] Generating random seed: " + randomSeed);
        seedInput.setText(String.valueOf(randomSeed));
        // Force update the current seed
        this.currentSeed = randomSeed;
        // Generate map with the new seed
        generateMap();
    }
    
    private void tryGetWorldSeed() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.world != null) {
            try {
                // Try to get the world seed through integrated server
                if (client.getServer() != null && client.getServer().getWorld(client.world.getRegistryKey()) != null) {
                    long worldSeed = client.getServer().getWorld(client.world.getRegistryKey()).getSeed();
                    seedInput.setText(String.valueOf(worldSeed));
                    System.out.println("[Cubeium] Using current world seed: " + worldSeed);
                    return;
                }
            } catch (Exception e) {
                System.out.println("[Cubeium] Failed to get world seed: " + e.getMessage());
            }
        }
        
        // Fallback to a good default seed
        seedInput.setText("12345");
        System.out.println("[Cubeium] Using default seed: 12345");
    }
    
    private void generateMap() {
        if (mapCache == null) {
            System.err.println("[Cubeium] MapCache is null, cannot generate map");
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
        
        // Reset map center to origin (0, 0) when generating new map
        this.mapCenterX = 0;
        this.mapCenterZ = 0;
        
        // Clear tile cache for new seed
        if (tileRenderer != null) {
            tileRenderer.clearCache();
        }
        
        System.out.println("[Cubeium] Starting progressive map generation for seed: " + seedToUse);
        
        // Start progressive generation - this returns immediately and loads in background
        mapCache.generateMapAsync(seedToUse).thenRun(() -> {
            System.out.println("[Cubeium] Map generation completed for seed: " + seedToUse);
        }).exceptionally(throwable -> {
            System.err.println("[Cubeium] Map generation failed: " + throwable.getMessage());
            return null;
        });
        
        // Map generation started - rendering will load from cache progressively
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Clear background without blur
        context.fill(0, 0, width, height, 0x80000000);
        
        // Render title
        String title = "Seed Map" + (hasValidSeed ? " - Seed: " + currentSeed : "");
        int titleWidth = textRenderer.getWidth(title);
        context.drawText(textRenderer, title, (width - titleWidth) / 2, 5, 0xFFFFFFFF, true);
        
        // Render seed input label
        String seedLabel = "Seed:";
        context.drawText(textRenderer, seedLabel, width/2 - 100 - textRenderer.getWidth(seedLabel) - 5, 35, 0xFFFFFFFF, false);
        
        // Render map area
        renderMap(context, mouseX, mouseY);
        
        // Render UI info
        renderUI(context, mouseX, mouseY);
        
        // Manually render widgets without blur effects
        if (seedInput != null) {
            seedInput.render(context, mouseX, mouseY, delta);
        }
        if (generateButton != null) {
            generateButton.render(context, mouseX, mouseY, delta);
        }
        if (randomSeedButton != null) {
            randomSeedButton.render(context, mouseX, mouseY, delta);
        }
    }
    
    private void renderMap(DrawContext context, int mouseX, int mouseY) {
        // Map border
        context.drawBorder(mapStartX, mapStartY, mapWidth, mapHeight, 0xFF404040);
        
        if (!hasValidSeed || mapCache == null || tileRenderer == null) {
            // Show placeholder when no map is generated
            context.fill(mapStartX + 1, mapStartY + 1, mapStartX + mapWidth - 1, mapStartY + mapHeight - 1, 0xFF1a1a2e);
            
            String message = hasValidSeed ? "Click 'Generate Map' to render biomes" : "Enter a seed to start";
            int messageWidth = textRenderer.getWidth(message);
            context.drawText(textRenderer, message, 
                mapStartX + (mapWidth - messageWidth) / 2, 
                mapStartY + mapHeight / 2, 0xFFAAAAAA, false);
            return;
        }
        
        // Use tile-based rendering for high performance
        tileRenderer.renderMap(context, currentSeed, 
                              mapStartX + 1, mapStartY + 1, mapWidth - 2, mapHeight - 2,
                              mapCenterX, mapCenterZ, zoomLevel);
        
        // Show progress if still generating
        float progress = mapCache.getGenerationProgress(currentSeed);
        if (progress < 1.0f) {
            // Show loading progress overlay
            String message = String.format("Loading map... %.0f%%", progress * 100);
            int messageWidth = textRenderer.getWidth(message);
            
            // Semi-transparent background
            context.fill(mapStartX + 10, mapStartY + 10, 
                        mapStartX + messageWidth + 20, mapStartY + 35, 
                        0x80000000);
            
            context.drawText(textRenderer, message, 
                mapStartX + 15, mapStartY + 15, 0xFFFFFFFF, true);
        }
        
        // Show cache statistics
        if (mapCache.hasCachedData(currentSeed)) {
            String stats = String.format("Chunks: %d | Tiles: %d | Progress: %.0f%%", 
                mapCache.getChunkCount(currentSeed),
                tileRenderer.getCachedTileCount(),
                mapCache.getGenerationProgress(currentSeed) * 100);
            context.drawText(textRenderer, stats, mapStartX + 5, mapStartY + 5, 0xFF888888, true);
        }
    }

    private void renderUI(DrawContext context, int mouseX, int mouseY) {
        // Show coordinates where mouse is pointing
        if (mouseX >= mapStartX && mouseX < mapStartX + mapWidth &&
            mouseY >= mapStartY && mouseY < mapStartY + mapHeight && 
            hasValidSeed && mapCache != null) {
            
            int relX = mouseX - mapStartX - 1;
            int relY = mouseY - mapStartY - 1;
            
            // Calculate world coordinates using BlazeMap-style approach
            // Adjust for map center being in screen center
            int mouseOffsetX = relX - mapWidth / 2;
            int mouseOffsetY = relY - mapHeight / 2;
            
            // Convert screen pixels to world blocks
            int worldX, worldZ;
            if (zoomLevel >= 1) {
                worldX = mapCenterX + (mouseOffsetX / zoomLevel);
                worldZ = mapCenterZ + (mouseOffsetY / zoomLevel);
            } else {
                worldX = (int) (mapCenterX + (mouseOffsetX / (double)zoomLevel));
                worldZ = (int) (mapCenterZ + (mouseOffsetY / (double)zoomLevel));
            }
            
            String coordText = String.format("Coordinates: %d, %d", worldX, worldZ);
            context.drawText(textRenderer, coordText, 10, height - 30, 0xFFFFFFFF, true);
            
            // Show biome info by querying a single point from MapCache
            int[] singleBiome = mapCache.getBiomeArea(currentSeed, worldX, worldZ, 1, 1, 1);
            if (singleBiome != null && singleBiome.length > 0) {
                int biomeId = singleBiome[0];
                String biomeName = getBiomeName(biomeId);
                String biomeText = String.format("Biome: %s (ID: %d)", biomeName, biomeId);
                context.drawText(textRenderer, biomeText, 10, height - 15, 0xFFFFFFFF, true);
            }
        }
        
        // Show controls
        String[] controls = {
            "Arrow Keys: Move map",
            "Mouse Drag: Pan map",
            "Mouse Scroll: Zoom in/out",
            "+/-: Zoom in/out", 
            "ESC: Close"
        };
        
        for (int i = 0; i < controls.length; i++) {
            context.drawText(textRenderer, controls[i], width - 150, height - 45 + i * 12, 0xFFAAAAAA, false);
        }
    }
    
    private String getBiomeName(int biomeId) {
        // Map biome IDs to readable names
        switch (biomeId) {
            // Water biomes
            case 0: return "Ocean";
            case 7: return "River";
            case 10: return "Frozen Ocean";
            case 46: return "Cold Ocean";
            case 47: return "Deep Ocean";
            case 48: return "Lukewarm Ocean";
            case 49: return "Warm Ocean";
            
            // Plains and grasslands
            case 1: return "Plains";
            case 129: return "Sunflower Plains";
            
            // Desert biomes
            case 2: return "Desert";
            case 17: return "Desert Hills";
            case 130: return "Desert Lakes";
            
            // Mountain biomes
            case 3: return "Mountains";
            case 34: return "Mountain Edge";
            case 131: return "Gravelly Mountains";
            case 162: return "Modified Gravelly Mountains";
            
            // Forest biomes
            case 4: return "Forest";
            case 18: return "Wooded Hills";
            case 27: return "Birch Forest";
            case 28: return "Birch Forest Hills";
            case 29: return "Dark Forest";
            case 157: return "Dark Forest Hills";
            case 132: return "Flower Forest";
            
            // Taiga biomes
            case 5: return "Taiga";
            case 19: return "Taiga Hills";
            case 30: return "Cold Taiga";
            case 31: return "Cold Taiga Hills";
            case 32: return "Mega Taiga";
            case 33: return "Mega Taiga Hills";
            case 133: return "Taiga Mountains";
            case 158: return "Snowy Taiga Mountains";
            
            // Swamp biomes
            case 6: return "Swampland";
            case 134: return "Swampland Mountains";
            
            // Jungle biomes
            case 21: return "Jungle";
            case 22: return "Jungle Hills";
            case 23: return "Jungle Edge";
            case 149: return "Modified Jungle";
            case 151: return "Modified Jungle Edge";
            case 168: return "Bamboo Jungle";
            case 169: return "Bamboo Jungle Hills";
            
            // Snow biomes
            case 12: return "Snowy Tundra";
            case 13: return "Snowy Mountains";
            case 26: return "Snowy Beach";
            case 140: return "Ice Spikes";
            
            // Mesa/Badlands biomes
            case 37: return "Mesa";
            case 38: return "Mesa Plateau F";
            case 39: return "Mesa Plateau";
            case 165: return "Modified Mesa";
            case 166: return "Modified Mesa Plateau F";
            case 167: return "Modified Mesa Plateau";
            
            // Savanna biomes
            case 35: return "Savanna";
            case 36: return "Savanna Plateau";
            case 163: return "Shattered Savanna";
            case 164: return "Shattered Savanna Plateau";
            
            // Beach biomes
            case 16: return "Beach";
            case 25: return "Stone Shore";
            
            // Mushroom biomes
            case 14: return "Mushroom Fields";
            case 15: return "Mushroom Field Shore";
            
            // End biomes
            case 9: return "The End";
            case 40: return "Small End Islands";
            case 41: return "End Midlands";
            case 42: return "End Highlands";
            case 43: return "End Barrens";
            
            // Nether biomes
            case 8: return "Nether Wastes";
            case 170: return "Soul Sand Valley";
            case 171: return "Crimson Forest";
            case 172: return "Warped Forest";
            case 173: return "Basalt Deltas";
            
            // Cave biomes (1.18+)
            case 174: return "Deep Dark";
            case 175: return "Lush Caves";
            case 176: return "Dripstone Caves";
            
            // Void
            case 127: return "The Void";
            
            // Default for unknown biomes
            default: return "Unknown Biome";
        }
    }
    
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Let widgets handle input first
        if (seedInput != null && seedInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        
        // Handle navigation controls
        int panAmount = 32 * zoomLevel; // Pan by 32 blocks * zoom level
        
        switch (keyCode) {
            case 265: // Up arrow
                mapCenterZ -= panAmount;
                generateMap();
                return true;
            case 264: // Down arrow
                mapCenterZ += panAmount;
                generateMap();
                return true;
            case 263: // Left arrow
                mapCenterX -= panAmount;
                generateMap();
                return true;
            case 262: // Right arrow
                mapCenterX += panAmount;
                generateMap();
                return true;
            case 61: // Plus key
            case 334: // Numpad plus
                if (zoomLevel > 1) {
                    zoomLevel = Math.max(1, zoomLevel / 2);
                    generateMap();
                }
                return true;
            case 45: // Minus key  
            case 333: // Numpad minus
                if (zoomLevel < 16) {
                    zoomLevel = Math.min(16, zoomLevel * 2);
                    generateMap();
                }
                return true;
            case 82: // R key - reset to origin
                mapCenterX = 0;
                mapCenterZ = 0;
                generateMap();
                return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    @Override
    public boolean charTyped(char chr, int modifiers) {
        // Let seed input handle character typing first
        if (seedInput != null && seedInput.charTyped(chr, modifiers)) {
            return true;
        }
        
        return super.charTyped(chr, modifiers);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Let widgets handle mouse clicks first
        if (seedInput != null && seedInput.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (generateButton != null && generateButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (randomSeedButton != null && randomSeedButton.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        
        // Check if click is in map area
        if (mouseX >= mapStartX && mouseX < mapStartX + mapWidth &&
            mouseY >= mapStartY && mouseY < mapStartY + mapHeight) {
            
            if (button == 0) { // Left click - start dragging
                isDragging = true;
                mouseSmoother.reset(); // Reset movement accumulator
                return true;
            }
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Only handle scroll when mouse is over the map area
        if (mouseX >= mapStartX && mouseX < mapStartX + mapWidth &&
            mouseY >= mapStartY && mouseY < mapStartY + mapHeight) {
            
            if (verticalAmount > 0 && zoomLevel > 1) {
                // Zoom in - decrease blocks per pixel for more detail
                zoomLevel = Math.max(1, zoomLevel - 1);
                return true;
            } else if (verticalAmount < 0 && zoomLevel < 32) {
                // Zoom out - increase blocks per pixel for wider view
                zoomLevel = Math.min(32, zoomLevel + 1);
                return true;
            }
        }
        
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // Handle map dragging with left mouse button
        if (button == 0 && isDragging) {
            // BlazeMap-style movement calculation with GUI scale compensation
            double scale = client.getWindow().getScaleFactor();
            double zoomFactor = (double) zoomLevel; // Our zoom is blocks per pixel
            
            // Add smooth subpixel movement accumulation
            mouseSmoother.addMovement(deltaX * scale / zoomFactor, deltaY * scale / zoomFactor);
            
            // Move the map center by accumulated integer movement
            mapCenterX -= mouseSmoother.movementX();
            mapCenterZ -= mouseSmoother.movementY();
            
            // No need to regenerate map - cache handles different view areas automatically
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
        System.out.println("[Cubeium] Closing Seed Map Screen");
        // Clean up resources when closing the screen
        if (mapCache != null) {
            System.out.println("[Cubeium] Shutting down map cache on screen close");
            mapCache.shutdown();
        }
        super.close();
    }
    
    @Override
    public boolean shouldPause() {
        return false; // Don't pause game when map is open
    }
}
