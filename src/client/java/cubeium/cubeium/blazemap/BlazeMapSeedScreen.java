package cubeium.cubeium.blazemap;

import cubeium.cubeium.Cubeium;
import cubeium.cubeium.gui.MouseSubpixelSmoother;
import cubeium.cubeium.rendering.MapTileRenderer;
import cubeium.cubeium.ui.SeedInputWidget;
import cubeium.cubeium.util.RenderMetrics;
import cubeium.cubeium.world.MapCache;
import cubeium.cubeium.world.generation.BiomeGenerator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Complete BlazeMap-style seed map screen.
 * Features professional map rendering, smooth navigation, and precise coordinate system.
 */
public class BlazeMapSeedScreen extends Screen {
    private static SeedMapSession sharedSession;
    private static final Identifier NAVIGATION_ICON_TEXTURE = Identifier.of("cubeium", "textures/gui/navigation_icon.png");
    private static final Identifier SETTINGS_ICON_TEXTURE = Identifier.of("cubeium", "textures/gui/settings_icon.png");
    
    // Core systems
    private static final long SEED_GENERATE_DEBOUNCE_MS = 450L;
    private final MapCache mapCache;
    private final MapTileRenderer tileRenderer;
    private final MouseSubpixelSmoother mouseSmoother;
    private final SeedMapSession session;
    private MarkerRenderer markerRenderer;
    
    // Map state (Fixed to match working SeedMapScreen approach)
    private long currentSeed = 0L;
    private boolean hasValidSeed = false;
    private boolean mapGenerated = false; // NEW: track if map generation has been started
    private int mapCenterX = 0; // FIXED: Use int like SeedMapScreen, not double
    private int mapCenterZ = 0; // FIXED: Use int like SeedMapScreen, not double
    private int zoomLevel = 4; // FIXED: Use SeedMapScreen default - 4 blocks per pixel
    private long pendingSeed = Long.MIN_VALUE;
    private long pendingGenerateAtMillis = -1L;
    private long lastGeneratedSeed = Long.MIN_VALUE;
    
    // UI components
    private SeedInputWidget seedInput;
    private ButtonWidget navigationMenuButton;
    private TextFieldWidget travelXInput;
    private TextFieldWidget travelZInput;
    private ButtonWidget travelGoButton;
    private ButtonWidget travelOriginButton;
    private ButtonWidget settingsButton;
    private boolean isNavigationMenuOpen = false;
    
    // Mouse state
    private boolean isDragging = false;

    // Last-hovered info box state (persists when mouse leaves map)
    private int lastInfoWorldX = 0;
    private int lastInfoWorldZ = 0;
    private String lastInfoBiomeName = "";
    private int lastInfoBiomeColor = 0xFF888888;
    private boolean hasInfoBoxData = false;

    // Per-frame hover sample cache so all overlays read identical biome/coords.
    private final HoverSample hoverSample = new HoverSample();
    
    // Viewport
    private int mapX, mapY, mapWidth, mapHeight;
    
    public BlazeMapSeedScreen() {
        super(Text.empty());

        try {
            session = getOrCreateSession();
            mapCache = session.mapCache;
            tileRenderer = session.tileRenderer;
            mouseSmoother = new MouseSubpixelSmoother();

            currentSeed = session.currentSeed;
            hasValidSeed = session.hasValidSeed;
            mapGenerated = session.mapGenerated;
            mapCenterX = session.mapCenterX;
            mapCenterZ = session.mapCenterZ;
            zoomLevel = session.zoomLevel;
            lastGeneratedSeed = session.lastGeneratedSeed;
        } catch (Exception e) {
            // initialization error log removed
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
        int topBarX = 10;
        int topBarY = 20;
        seedInput = new SeedInputWidget(
            textRenderer,
            topBarX,
            topBarY,
            this::onSeedChanged
        );
        addDrawableChild(seedInput);
        
        // Navigation menu button (icon can be swapped in later)
        navigationMenuButton = ButtonWidget.builder(Text.empty(), button -> toggleNavigationMenu())
            .dimensions(topBarX + 206, topBarY, 20, 20)
                .build();
        addDrawableChild(navigationMenuButton);

        travelOriginButton = ButtonWidget.builder(Text.literal("Origin (0, 0)"), button -> {
                resetView();
                isNavigationMenuOpen = false;
                syncNavigationMenuState();
            })
            .dimensions(0, 0, 190, 20)
            .build();
        addDrawableChild(travelOriginButton);

        travelXInput = new TextFieldWidget(textRenderer, 0, 0, 82, 20, Text.literal("X"));
        travelXInput.setPlaceholder(Text.literal("X"));
        travelXInput.setMaxLength(12);
        addDrawableChild(travelXInput);

        travelZInput = new TextFieldWidget(textRenderer, 0, 0, 82, 20, Text.literal("Z"));
        travelZInput.setPlaceholder(Text.literal("Z"));
        travelZInput.setMaxLength(12);
        addDrawableChild(travelZInput);

        travelGoButton = ButtonWidget.builder(Text.literal("Go"), button -> travelToCoordinates())
            .dimensions(0, 0, 44, 20)
            .build();
        addDrawableChild(travelGoButton);

        layoutNavigationMenuWidgets();

        settingsButton = ButtonWidget.builder(Text.empty(), button -> openSettings())
            .dimensions(width - 30, 20, 20, 20)
            .build();
        addDrawableChild(settingsButton);

        // Initialize marker renderer and origin marker
        markerRenderer = new MarkerRenderer();
        if (session.markers.isEmpty()) {
            session.markers.add(new MapMarker(MapMarker.MarkerType.ORIGIN, 0, 0, "Origin (0, 0)"));
        }

        syncNavigationMenuState();

        if (session.seedInputText != null && !session.seedInputText.isEmpty()) {
            seedInput.setText(session.seedInputText);
        } else if (mapGenerated || hasValidSeed) {
            seedInput.setText(Long.toString(currentSeed));
        } else {
            // Try to get current world seed like the working SeedMapScreen
            tryGetWorldSeed();
        }
        
    // UI init log removed
    }
    
    private void onSeedChanged(long seed, boolean isValid) {
        String seedText = seedInput != null ? seedInput.getText().trim() : "";
        this.hasValidSeed = isValid;

        if (!isValid || seedText.isEmpty()) {
            pendingSeed = Long.MIN_VALUE;
            pendingGenerateAtMillis = -1L;
            return;
        }

        if (seed == lastGeneratedSeed) {
            pendingSeed = Long.MIN_VALUE;
            pendingGenerateAtMillis = -1L;
            return;
        }

        pendingSeed = seed;
        pendingGenerateAtMillis = System.currentTimeMillis() + SEED_GENERATE_DEBOUNCE_MS;
    }

    @Override
    public void tick() {
        super.tick();

        if (pendingGenerateAtMillis <= 0L || pendingSeed == Long.MIN_VALUE) {
            updatePlayerMarker();
            return;
        }

        if (System.currentTimeMillis() >= pendingGenerateAtMillis) {
            long seedForGeneration = pendingSeed;
            pendingSeed = Long.MIN_VALUE;
            pendingGenerateAtMillis = -1L;
            generateMapForSeed(seedForGeneration);
        }
        updatePlayerMarker();
    }

    private void updatePlayerMarker() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;

            // Find or create player marker
            MapMarker playerMarker = null;
            for (MapMarker marker : session.markers) {
                if (marker.type == MapMarker.MarkerType.PLAYER) {
                    playerMarker = marker;
                    break;
                }
            }

            int playerWorldX = (int) client.player.getX();
            int playerWorldZ = (int) client.player.getZ();

            if (playerMarker == null) {
                playerMarker = new MapMarker(MapMarker.MarkerType.PLAYER, playerWorldX, playerWorldZ, "Player");
                session.markers.add(playerMarker);
            } else {
                playerMarker.setPosition(playerWorldX, playerWorldZ);
            }
        } catch (Exception e) {
            // Silently ignore player tracking errors
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
        RenderMetrics metrics = RenderMetrics.get();
        metrics.beginFrame();
        long renderStart = System.nanoTime();

        try {
            // Force the standard tiled menu dirt background even when in-world.
            Screen.renderBackgroundTexture(context, MENU_BACKGROUND_TEXTURE, 0, 0, 0.0F, 0.0F, width, height);
            renderMap(context);
            renderUI(context, mouseX, mouseY);
            super.render(context, mouseX, mouseY, delta);
            renderNavigationButtonIcon(context);
            renderSettingsButtonIcon(context);

            if (isNavigationMenuOpen) {
                renderNavigationMenuLabels(context);
            }
        } finally {
            metrics.addRenderNanos(System.nanoTime() - renderStart);
            int chunkQueueDepth = mapCache != null ? mapCache.getPendingTaskCount() : 0;
            int tileQueueDepth = tileRenderer != null ? tileRenderer.getPendingTileCount() : 0;
            metrics.setQueueDepth(chunkQueueDepth + tileQueueDepth);
            metrics.setTileCacheSize(tileRenderer != null ? tileRenderer.getCachedTileCount() : 0);
            metrics.setChunkCacheSize(mapCache != null ? mapCache.getChunkCount(currentSeed) : 0);
            metrics.endFrame();

            if (metrics.shouldLogNow(5000)) {
                String line = metrics.snapshot().toLogLine();
                Cubeium.LOGGER.info("[CubeiumMetrics] {}", line);
                // Print to stdout as well so metrics are visible in debug console even if logger filters INFO.
                System.out.println("[CubeiumMetrics] " + line);
            }
        }

    }
    
    // ... debug test squares removed
    
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Screen.renderBackgroundTexture(context, MENU_BACKGROUND_TEXTURE, 0, 0, 0.0F, 0.0F, width, height);
    }

    private void renderNavigationMenuLabels(DrawContext context) {
        int panelLeft = travelOriginButton.getX() - 8;
        int panelTop = travelOriginButton.getY() - 6;
        int panelRight = travelGoButton.getX() + travelGoButton.getWidth() + 8;
        int panelBottom = travelGoButton.getY() + travelGoButton.getHeight() + 6;

        // Subtle panel background behind the menu controls.
        context.fill(panelLeft, panelTop, panelRight, panelBottom, 0xD0222730);
        context.drawBorder(panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, 0xFF5B6470);

        String xLabel = "X:";
        String zLabel = "Z:";
        int xLabelY = travelXInput.getY() + 6;
        int zLabelY = travelZInput.getY() + 6;
        int xLabelX = travelXInput.getX() - textRenderer.getWidth(xLabel) - 6;
        int zLabelX = travelZInput.getX() - textRenderer.getWidth(zLabel) - 6;

        context.drawText(textRenderer, xLabel, xLabelX, xLabelY, 0xFFE0E0E0, false);
        context.drawText(textRenderer, zLabel, zLabelX, zLabelY, 0xFFE0E0E0, false);
    }

    private void renderNavigationButtonIcon(DrawContext context) {
        if (navigationMenuButton == null) {
            return;
        }

        int iconSize = 12;
        int iconX = navigationMenuButton.getX() + (navigationMenuButton.getWidth() - iconSize) / 2;
        int iconY = navigationMenuButton.getY() + (navigationMenuButton.getHeight() - iconSize) / 2;

        context.drawTexture(RenderLayer::getGuiTextured, NAVIGATION_ICON_TEXTURE,
            iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
    }

    private void layoutNavigationMenuWidgets() {
        if (navigationMenuButton == null || travelOriginButton == null || travelXInput == null
            || travelZInput == null || travelGoButton == null) {
            return;
        }

        int menuX = navigationMenuButton.getX();
        int menuY = navigationMenuButton.getY() + navigationMenuButton.getHeight() + 4;

        int originWidth = 190;
        int fieldWidth = 82;
        int fieldGap = 6;
        int goWidth = 44;
        int row2Y = menuY + 26;

        travelOriginButton.setDimensionsAndPosition(originWidth, 20, menuX, menuY);
        travelXInput.setX(menuX + 16);
        travelXInput.setY(row2Y);
        travelXInput.setWidth(fieldWidth);
        travelXInput.setHeight(20);

        travelZInput.setX(travelXInput.getX() + fieldWidth + fieldGap + 16);
        travelZInput.setY(row2Y);
        travelZInput.setWidth(fieldWidth);
        travelZInput.setHeight(20);

        travelGoButton.setDimensionsAndPosition(goWidth, 20, travelZInput.getX() + fieldWidth + fieldGap, row2Y);
    }

    private void toggleNavigationMenu() {
        isNavigationMenuOpen = !isNavigationMenuOpen;
        if (isNavigationMenuOpen) {
            travelXInput.setText(Integer.toString(mapCenterX));
            travelZInput.setText(Integer.toString(mapCenterZ));
            setFocused(travelXInput);
        } else {
            setFocused(null);
        }
        syncNavigationMenuState();
    }

    private void syncNavigationMenuState() {
        if (navigationMenuButton != null) {
            navigationMenuButton.setMessage(Text.empty());
        }
        if (travelOriginButton != null) {
            travelOriginButton.visible = isNavigationMenuOpen;
            travelOriginButton.active = isNavigationMenuOpen;
        }
        if (travelXInput != null) {
            travelXInput.visible = isNavigationMenuOpen;
            travelXInput.active = isNavigationMenuOpen;
        }
        if (travelZInput != null) {
            travelZInput.visible = isNavigationMenuOpen;
            travelZInput.active = isNavigationMenuOpen;
        }
        if (travelGoButton != null) {
            travelGoButton.visible = isNavigationMenuOpen;
            travelGoButton.active = isNavigationMenuOpen;
        }
    }

    private void travelToCoordinates() {
        if (travelXInput == null || travelZInput == null) {
            return;
        }

        try {
            int x = Integer.parseInt(travelXInput.getText().trim());
            int z = Integer.parseInt(travelZInput.getText().trim());
            mapCenterX = x;
            mapCenterZ = z;

            if (mapGenerated && hasValidSeed && tileRenderer != null) {
                tileRenderer.prewarmTiles(currentSeed, mapCenterX, mapCenterZ, mapWidth - 2, mapHeight - 2, zoomLevel);
            }

            isNavigationMenuOpen = false;
            syncNavigationMenuState();
            setFocused(null);
        } catch (NumberFormatException ignored) {
            // Keep menu open and ignore invalid values until user fixes input.
        }
    }
    
    /**
     * Render the map using tile renderer like the working SeedMapScreen
     */
    private void renderMap(DrawContext context) {
        // Map border
        context.drawBorder(mapX, mapY, mapWidth, mapHeight, 0xFF404040);

        if (!mapGenerated || mapCache == null || tileRenderer == null) {
            String message = hasValidSeed ? "Generating map..." : "Enter a seed to start";
            int messageWidth = textRenderer.getWidth(message);
            context.drawText(textRenderer, message, 
                mapX + (mapWidth - messageWidth) / 2,
                mapY + mapHeight / 2, 0xFFAAAAAA, false);
            return;
        }
        
        tileRenderer.renderMap(context, currentSeed,
            mapX + 1, mapY + 1, mapWidth - 2, mapHeight - 2,
            mapCenterX, mapCenterZ, zoomLevel);
        
    // Loading progress overlay removed per UI cleanup
        
    }
    
    /**
     * Render UI overlays (BlazeMap-style)
     */
    private void renderUI(DrawContext context, int mouseX, int mouseY) {
    // Title removed per UI cleanup
        
    // Current seed and zoom displays removed per UI cleanup

        updateHoverSample(mouseX, mouseY);
        
        // Render map markers (before tooltips so markers appear under text)
        if (markerRenderer != null && mapGenerated && hasValidSeed) {
            markerRenderer.renderMarkers(context, session.markers, mapX, mapY, mapWidth, mapHeight,
                                        mapCenterX, mapCenterZ, zoomLevel);
        }
        
        // Coordinate + biome info box — always shown in top-right corner of map
        renderMapInfoBox(context);

        // Floating tooltip near cursor when enabled in settings
        if (session.showFloatingTooltip && isMouseOverMap(mouseX, mouseY)) {
            renderFloatingTooltip(context, mouseX, mouseY);
        }

        // Performance info
        if (session.showPerformanceInfo) {
            renderPerformanceOverlay(context);
        }
    }
    
    /**
     * Fixed top-right corner info box: biome swatch | biome name | X/Z coords.
     * Always rendered; updates last-hovered values when the mouse is over the map.
     */
    private void renderMapInfoBox(DrawContext context) {
        if (hoverSample.valid) {
            lastInfoWorldX = hoverSample.worldX;
            lastInfoWorldZ = hoverSample.worldZ;
            lastInfoBiomeName = hoverSample.biomeName;
            lastInfoBiomeColor = hoverSample.biomeColor;
            hasInfoBoxData = true;
        }

        int swatchSize = 8;
        int pad = 5;
        int innerGap = 5;
        int sectionGap = 12;
        String biomeName = hasInfoBoxData ? lastInfoBiomeName : "-";
        String coordText = hasInfoBoxData ? String.format("X: %d  Z: %d", lastInfoWorldX, lastInfoWorldZ) : "X: -  Z: -";
        int biomeColor = lastInfoBiomeColor;

        int contentWidth = swatchSize + innerGap + textRenderer.getWidth(biomeName) + sectionGap + textRenderer.getWidth(coordText);
        int boxWidth = contentWidth + pad * 2;
        int boxHeight = 9 + pad * 2;

        int boxRight = mapX + mapWidth - 4;
        int boxTop = mapY + 4;
        int boxLeft = boxRight - boxWidth;

        context.fill(boxLeft, boxTop, boxRight, boxTop + boxHeight, 0xD0111111);
        context.drawBorder(boxLeft, boxTop, boxWidth, boxHeight, 0xFF404040);

        int cx = boxLeft + pad;
        int ty = boxTop + pad;

        context.fill(cx, ty, cx + swatchSize, ty + swatchSize, biomeColor);
        cx += swatchSize + innerGap;

        context.drawText(textRenderer, biomeName, cx, ty, 0xFFE0E0E0, false);
        cx += textRenderer.getWidth(biomeName) + sectionGap;

        context.drawText(textRenderer, coordText, cx, ty, 0xFFFFFF66, false);
    }
    
    private void renderFloatingTooltip(DrawContext context, int mouseX, int mouseY) {
        if (!hoverSample.valid) return;
        String biomeName = hoverSample.biomeName;
        int biomeColor = hoverSample.biomeColor;
        String coordText = String.format("X: %d  Z: %d", hoverSample.worldX, hoverSample.worldZ);

        int swatchSize = 8;
        int pad = 4;
        int innerGap = 4;
        int sectionGap = 10;
        int contentWidth = swatchSize + innerGap + textRenderer.getWidth(biomeName) + sectionGap + textRenderer.getWidth(coordText);
        int boxWidth = contentWidth + pad * 2;
        int boxHeight = 9 + pad * 2;

        int tx = mouseX + 12;
        int ty = mouseY - boxHeight - 4;
        if (tx + boxWidth > width) tx = mouseX - boxWidth - 12;
        if (ty < 0) ty = mouseY + 12;

        context.fill(tx, ty, tx + boxWidth, ty + boxHeight, 0xEE000000);
        context.drawBorder(tx, ty, boxWidth, boxHeight, 0xFF606060);

        int cx = tx + pad;
        int textY = ty + pad;
        context.fill(cx, textY, cx + swatchSize, textY + swatchSize, biomeColor);
        cx += swatchSize + innerGap;
        context.drawText(textRenderer, biomeName, cx, textY, 0xFFE0E0E0, false);
        cx += textRenderer.getWidth(biomeName) + sectionGap;
        context.drawText(textRenderer, coordText, cx, textY, 0xFFFFFF66, false);
    }

    private void renderSettingsButtonIcon(DrawContext context) {
        if (settingsButton == null) return;
        int iconSize = 12;
        int iconX = settingsButton.getX() + (settingsButton.getWidth() - iconSize) / 2;
        int iconY = settingsButton.getY() + (settingsButton.getHeight() - iconSize) / 2;
        context.drawTexture(RenderLayer::getGuiTextured, SETTINGS_ICON_TEXTURE,
            iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
    }

    private void openSettings() {
        if (client != null) {
            client.setScreen(new BlazeMapSettingsScreen(this, session));
        }
    }

    /**
     * Render performance overlay
     */
    private void renderPerformanceOverlay(DrawContext context) {
        RenderMetrics.Snapshot snapshot = RenderMetrics.get().snapshot();

        context.drawText(textRenderer, "CubeiumMetrics", mapX + 5, mapY + mapHeight - 63, 0xFFFFFF00, true);

        String perf = String.format("Center: %d, %d | View: %dx%d blocks", 
                                   mapCenterX, mapCenterZ, 
                                   mapWidth * zoomLevel, mapHeight * zoomLevel);
        context.drawText(textRenderer, perf, mapX + 5, mapY + mapHeight - 15, 0xFF00FF00, true);

        String perf2 = String.format("Frame: %.2fms | Render: %.2fms | FPS~%.1f", 
                                    snapshot.avgFrameMs, snapshot.avgRenderMs, snapshot.fpsEstimate);
        context.drawText(textRenderer, perf2, mapX + 5, mapY + mapHeight - 27, 0xFF66FF66, true);

        String perf3 = String.format("Tiles f/t: %d/%d | Hit: %.1f%% | Queue: %d",
                                    snapshot.tilesLastFrame, snapshot.tilesTotal,
                                    snapshot.tileCacheHitRate, snapshot.queueDepth);
        context.drawText(textRenderer, perf3, mapX + 5, mapY + mapHeight - 39, 0xFF66FFFF, true);

        String perf4 = String.format("TileGen: %.2fms avg (%d) | JNI: %.3fms avg (%d)",
                                    snapshot.avgTileGenerationMs, snapshot.tileGenerations,
                                    snapshot.avgJniMs, snapshot.jniCalls);
        context.drawText(textRenderer, perf4, mapX + 5, mapY + mapHeight - 51, 0xFFFFFF99, true);
    }
    
    /**
     * Check if mouse is over map area
     */
    private boolean isMouseOverMap(int mouseX, int mouseY) {
        return mouseX >= mapX && mouseX < mapX + mapWidth &&
               mouseY >= mapY && mouseY < mapY + mapHeight;
    }

    private boolean isMouseOverMapInner(int mouseX, int mouseY) {
        return mouseX >= mapX + 1 && mouseX < mapX + mapWidth - 1 &&
               mouseY >= mapY + 1 && mouseY < mapY + mapHeight - 1;
    }

    private void updateHoverSample(int mouseX, int mouseY) {
        hoverSample.valid = false;
        if (!mapGenerated || !hasValidSeed || session.biomeGenerator == null || !isMouseOverMapInner(mouseX, mouseY)) {
            return;
        }

        int innerWidth = mapWidth - 2;
        int innerHeight = mapHeight - 2;
        int relX = mouseX - (mapX + 1);
        int relY = mouseY - (mapY + 1);

        int viewWorldLeft = mapCenterX - (innerWidth * zoomLevel) / 2;
        int viewWorldTop = mapCenterZ - (innerHeight * zoomLevel) / 2;
        int worldX = viewWorldLeft + relX * zoomLevel;
        int worldZ = viewWorldTop + relY * zoomLevel;

        Integer renderedBiomeId = tileRenderer != null
            ? tileRenderer.sampleBiomeAtScreen(
                currentSeed,
                mapX + 1, mapY + 1, innerWidth, innerHeight,
                mapCenterX, mapCenterZ, zoomLevel,
                mouseX, mouseY)
            : null;

        int biomeId;
        if (renderedBiomeId != null) {
            biomeId = renderedBiomeId;
        } else {
            // Fallback when this pixel is still on placeholder/no tile data.
            session.biomeGenerator.setSeed(currentSeed, 0);
            biomeId = session.biomeGenerator.getBiomeAt(worldX, worldZ);
        }

        hoverSample.worldX = worldX;
        hoverSample.worldZ = worldZ;
        hoverSample.biomeName = session.biomeGenerator.getBiomeName(biomeId);
        hoverSample.biomeColor = MapTileRenderer.getBiomeColor(biomeId);
        hoverSample.valid = true;
    }
    
    private void generateMapForSeed(long seedToUse) {
        if (mapCache == null) {
            return;
        }

        // Update current seed to match what we're actually using
        this.currentSeed = seedToUse;
        this.hasValidSeed = true;
        this.mapGenerated = true;
        this.lastGeneratedSeed = seedToUse;
        if (tileRenderer != null) {
            tileRenderer.clearCache();
        }

        // Start viewport-prioritized generation: pass view size in blocks
        int viewWidthBlocks = Math.max(1, (mapWidth - 2) * zoomLevel);
        int viewHeightBlocks = Math.max(1, (mapHeight - 2) * zoomLevel);
        mapCache.generateMapAsync(seedToUse, mapCenterX, mapCenterZ, viewWidthBlocks, viewHeightBlocks).thenRun(() -> {
            // map generation completed for prioritized queue
        }).exceptionally(throwable -> {
            return null;
        });
        
        // Map generation started - rendering will load from cache progressively
        // Warm visible tiles in background so the tile cache is populated and the UI "Tiles:" counter updates
        if (tileRenderer != null) {
            tileRenderer.prewarmTiles(seedToUse, mapCenterX, mapCenterZ, mapWidth - 2, mapHeight - 2, zoomLevel);
        }
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

        if (mapGenerated && hasValidSeed && tileRenderer != null) {
            tileRenderer.prewarmTiles(currentSeed, mapCenterX, mapCenterZ, mapWidth - 2, mapHeight - 2, zoomLevel);
        }
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
        if (markerRenderer != null) {
            markerRenderer.shutdown();
        }
        saveSessionState();
        super.close();
    }

    private static SeedMapSession getOrCreateSession() {
        if (sharedSession == null) {
            sharedSession = new SeedMapSession();
        }
        return sharedSession;
    }

    private void saveSessionState() {
        session.currentSeed = currentSeed;
        session.hasValidSeed = hasValidSeed;
        session.mapGenerated = mapGenerated;
        session.mapCenterX = mapCenterX;
        session.mapCenterZ = mapCenterZ;
        session.zoomLevel = zoomLevel;
        session.lastGeneratedSeed = lastGeneratedSeed;
        if (seedInput != null) {
            session.seedInputText = seedInput.getText();
        }
    }

    public static final class SeedMapSession {
        final BiomeGenerator biomeGenerator;
        final MapCache mapCache;
        final MapTileRenderer tileRenderer;
        final java.util.List<MapMarker> markers = new java.util.concurrent.CopyOnWriteArrayList<>();

        long currentSeed = 0L;
        boolean hasValidSeed = false;
        boolean mapGenerated = false;
        int mapCenterX = 0;
        int mapCenterZ = 0;
        int zoomLevel = 4;
        long lastGeneratedSeed = Long.MIN_VALUE;
        boolean showPerformanceInfo = false;
        boolean showFloatingTooltip = false;
        String seedInputText = "";

        SeedMapSession() {
            biomeGenerator = new BiomeGenerator();
            mapCache = new MapCache(biomeGenerator);
            tileRenderer = new MapTileRenderer(mapCache);
        }
    }

    private static final class HoverSample {
        int worldX;
        int worldZ;
        String biomeName = "";
        int biomeColor = 0xFF888888;
        boolean valid;
    }
}
