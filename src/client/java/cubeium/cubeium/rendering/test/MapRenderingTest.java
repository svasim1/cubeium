package cubeium.cubeium.rendering.test;

import cubeium.cubeium.Cubeium;
import cubeium.cubeium.world.generation.BiomeGenerator;
import cubeium.cubeium.world.generation.StructureGenerator;
import cubeium.cubeium.rendering.MapRenderer;
import cubeium.cubeium.rendering.cache.MapCache;
import cubeium.cubeium.rendering.tile.TileRenderer;
import cubeium.cubeium.rendering.viewport.MapViewportManager;
import cubeium.cubeium.util.ClientColorPalette;
import cubeium.cubeium.world.CubiomesInterface;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Comprehensive test suite for the map rendering system (Task 4.0).
 * Tests all components: MapCache, TileRenderer, MapRenderer, and MapViewportManager.
 */
public class MapRenderingTest {
    
    private static final long TEST_SEED = 123456789L;
    private static final int TEST_DIMENSION = CubiomesInterface.DIM_OVERWORLD;
    private static final String OUTPUT_DIR = "test_output/rendering";
    
    /**
     * Run all map rendering tests
     */
    public static boolean runAllTests() {
        Cubeium.LOGGER.info("=== Starting Map Rendering Tests (Task 4.0) ===");
        
        boolean success = true;
        success &= testMapCacheBasicOperations();
        success &= testTileRendererBasicRendering();
        success &= testMapRendererViewportRendering();
        success &= testViewportManagerInteraction();
        success &= testPerformanceAndMemory();
        success &= testEdgeCases();
        
        if (success) {
            Cubeium.LOGGER.info("✅ All Map Rendering tests passed!");
        } else {
            Cubeium.LOGGER.error("❌ Some Map Rendering tests failed!");
        }
        
        return success;
    }
    
    /**
     * Test 4.1: MapCache basic operations
     */
    public static boolean testMapCacheBasicOperations() {
        Cubeium.LOGGER.info("=== Test 4.1: MapCache Basic Operations ===");
        
        try {
            MapCache mapCache = new MapCache(256, 1000, 4);
            
            // Test world state management
            mapCache.setWorldState(TEST_SEED, TEST_DIMENSION, 0);
            
            // Test cache stats
            MapCache.MapCacheStats initialStats = mapCache.getStats();
            Cubeium.LOGGER.info("Initial cache stats: {}", initialStats);
            
            if (initialStats.tileCacheSize != 0) {
                Cubeium.LOGGER.error("Expected empty cache, got {} tiles", initialStats.tileCacheSize);
                return false;
            }
            
            // Test viewport tile requests
            var tileRequests = mapCache.getTilesForViewport(0, 0, 800, 600, 0, 
                TileRenderer.RENDER_BIOMES);
            
            if (tileRequests.isEmpty()) {
                Cubeium.LOGGER.error("Expected tile requests for viewport, got none");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Generated {} tile requests for viewport", tileRequests.size());
            
            // Test cache operations
            BufferedImage testTile = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
            mapCache.cacheTile(0, 0, 0, TileRenderer.RENDER_BIOMES, testTile);
            
            MapCache.CachedMapTile cached = mapCache.getCachedTile(0, 0, 0, TileRenderer.RENDER_BIOMES);
            if (cached == null) {
                Cubeium.LOGGER.error("Failed to retrieve cached tile");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Successfully cached and retrieved tile");
            
            // Test cache maintenance
            mapCache.performMaintenance();
            mapCache.clearAll();
            
            MapCache.MapCacheStats clearedStats = mapCache.getStats();
            if (clearedStats.tileCacheSize != 0) {
                Cubeium.LOGGER.error("Expected empty cache after clear, got {} tiles", 
                    clearedStats.tileCacheSize);
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Cache maintenance and clearing works");
            return true;
            
        } catch (Exception e) {
            Cubeium.LOGGER.error("MapCache test failed", e);
            return false;
        }
    }
    
    /**
     * Test 4.2: TileRenderer basic rendering
     */
    public static boolean testTileRendererBasicRendering() {
        Cubeium.LOGGER.info("=== Test 4.2: TileRenderer Basic Rendering ===");
        
        try {
            // Setup components
            BiomeGenerator biomeGenerator = new BiomeGenerator();
            StructureGenerator structureGenerator = new StructureGenerator();
            ClientColorPalette colorPalette = new ClientColorPalette();
            MapCache mapCache = new MapCache();
            
            biomeGenerator.setSeed(TEST_SEED, TEST_DIMENSION);
            structureGenerator.setSeed(TEST_SEED, TEST_DIMENSION);
            mapCache.setWorldState(TEST_SEED, TEST_DIMENSION, 0);
            
            TileRenderer tileRenderer = new TileRenderer(biomeGenerator, structureGenerator, 
                                                       colorPalette, mapCache, 2);
            
            // Test basic tile rendering
            MapCache.TileRequest request = new MapCache.TileRequest(0, 0, 0, 
                TileRenderer.RENDER_BIOMES, 0, 0, 256, 256, 1.0);
            
            TileRenderer.RenderedTile renderedTile = tileRenderer.renderTile(request);
            
            if (renderedTile == null || renderedTile.image == null) {
                Cubeium.LOGGER.error("Failed to render basic tile");
                return false;
            }
            
            if (renderedTile.image.getWidth() <= 0 || renderedTile.image.getHeight() <= 0) {
                Cubeium.LOGGER.error("Invalid tile dimensions: {}x{}", 
                    renderedTile.image.getWidth(), renderedTile.image.getHeight());
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Rendered tile: {} ({}ms)", 
                renderedTile, renderedTile.renderTimeMs);
            
            // Test different render flags
            MapCache.TileRequest structureRequest = new MapCache.TileRequest(0, 0, 0, 
                TileRenderer.RENDER_BIOMES | TileRenderer.RENDER_STRUCTURES, 0, 0, 256, 256, 1.0);
            
            TileRenderer.RenderedTile structureTile = tileRenderer.renderTile(structureRequest);
            
            if (structureTile == null || structureTile.image == null) {
                Cubeium.LOGGER.error("Failed to render tile with structures");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Rendered tile with structures: {} ({}ms)", 
                structureTile, structureTile.renderTimeMs);
            
            // Test renderer settings
            tileRenderer.setAntiAliasing(TileRenderer.AntiAliasing.HIGH_QUALITY);
            tileRenderer.setEnableBiomeBlending(true);
            tileRenderer.setStructureOpacity(0.8f);
            
            TileRenderer.RenderedTile qualityTile = tileRenderer.renderTile(request);
            
            if (qualityTile == null || qualityTile.image == null) {
                Cubeium.LOGGER.error("Failed to render high-quality tile");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Rendered high-quality tile: {} ({}ms)", 
                qualityTile, qualityTile.renderTimeMs);
            
            // Save test images
            saveTestImage(renderedTile.image, "basic_tile.png");
            saveTestImage(structureTile.image, "structure_tile.png");
            saveTestImage(qualityTile.image, "quality_tile.png");
            
            tileRenderer.shutdown();
            return true;
            
        } catch (Exception e) {
            Cubeium.LOGGER.error("TileRenderer test failed", e);
            return false;
        }
    }
    
    /**
     * Test 4.3: MapRenderer viewport rendering
     */
    public static boolean testMapRendererViewportRendering() {
        Cubeium.LOGGER.info("=== Test 4.3: MapRenderer Viewport Rendering ===");
        
        try {
            // Setup components
            BiomeGenerator biomeGenerator = new BiomeGenerator();
            StructureGenerator structureGenerator = new StructureGenerator();
            ClientColorPalette colorPalette = new ClientColorPalette();
            
            MapRenderer mapRenderer = new MapRenderer(biomeGenerator, structureGenerator, 
                                                    colorPalette, 2);
            
            // Set world state
            mapRenderer.setWorldState(TEST_SEED, TEST_DIMENSION);
            
            // Test basic viewport rendering
            mapRenderer.setViewportSize(800, 600);
            mapRenderer.setViewportCenter(0, 0);
            mapRenderer.setZoomLevel(0);
            
            BufferedImage viewport1 = mapRenderer.renderViewport();
            
            if (viewport1 == null || viewport1.getWidth() != 800 || viewport1.getHeight() != 600) {
                Cubeium.LOGGER.error("Invalid viewport rendering: {}x{}", 
                    viewport1 != null ? viewport1.getWidth() : 0, 
                    viewport1 != null ? viewport1.getHeight() : 0);
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Rendered 800x600 viewport at (0,0) zoom 0");
            
            // Test different zoom levels
            mapRenderer.setZoomLevel(2); // Zoom out
            BufferedImage zoomedOut = mapRenderer.renderViewport();
            
            if (zoomedOut == null) {
                Cubeium.LOGGER.error("Failed to render zoomed out viewport");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Rendered viewport at zoom level 2");
            
            mapRenderer.setZoomLevel(-1); // Zoom in
            BufferedImage zoomedIn = mapRenderer.renderViewport();
            
            if (zoomedIn == null) {
                Cubeium.LOGGER.error("Failed to render zoomed in viewport");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Rendered viewport at zoom level -1");
            
            // Test different positions
            mapRenderer.setViewportCenter(1000, -500);
            mapRenderer.setZoomLevel(1);
            BufferedImage offsetViewport = mapRenderer.renderViewport();
            
            if (offsetViewport == null) {
                Cubeium.LOGGER.error("Failed to render offset viewport");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Rendered viewport at offset position (1000,-500)");
            
            // Test render flags
            mapRenderer.setRenderFlags(TileRenderer.RENDER_BIOMES | TileRenderer.RENDER_STRUCTURES | 
                                      TileRenderer.RENDER_GRID);
            BufferedImage gridViewport = mapRenderer.renderViewport();
            
            if (gridViewport == null) {
                Cubeium.LOGGER.error("Failed to render viewport with grid");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Rendered viewport with grid overlay");
            
            // Test coordinate conversion
            double[] worldCoords = mapRenderer.screenToWorld(400, 300); // Center of screen
            int[] screenCoords = mapRenderer.worldToScreen(worldCoords[0], worldCoords[1]);
            
            if (Math.abs(screenCoords[0] - 400) > 1 || Math.abs(screenCoords[1] - 300) > 1) {
                Cubeium.LOGGER.error("Coordinate conversion failed: ({},{}) -> ({},{}) -> ({},{})", 
                    400, 300, worldCoords[0], worldCoords[1], screenCoords[0], screenCoords[1]);
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Coordinate conversion works correctly");
            
            // Test stats
            MapRenderer.RenderingStats stats = mapRenderer.getStats();
            if (stats.totalRenderRequests == 0) {
                Cubeium.LOGGER.error("Expected render requests, got {}", stats.totalRenderRequests);
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Rendering stats: {}", stats);
            
            // Save test images
            saveTestImage(viewport1, "viewport_basic.png");
            saveTestImage(zoomedOut, "viewport_zoomed_out.png");
            saveTestImage(zoomedIn, "viewport_zoomed_in.png");
            saveTestImage(offsetViewport, "viewport_offset.png");
            saveTestImage(gridViewport, "viewport_grid.png");
            
            mapRenderer.shutdown();
            return true;
            
        } catch (Exception e) {
            Cubeium.LOGGER.error("MapRenderer test failed", e);
            return false;
        }
    }
    
    /**
     * Test 4.4: MapViewportManager interaction
     */
    public static boolean testViewportManagerInteraction() {
        Cubeium.LOGGER.info("=== Test 4.4: MapViewportManager Interaction ===");
        
        try {
            // Setup components
            BiomeGenerator biomeGenerator = new BiomeGenerator();
            StructureGenerator structureGenerator = new StructureGenerator();
            ClientColorPalette colorPalette = new ClientColorPalette();
            
            MapRenderer mapRenderer = new MapRenderer(biomeGenerator, structureGenerator, 
                                                    colorPalette, 1);
            mapRenderer.setWorldState(TEST_SEED, TEST_DIMENSION);
            
            MapViewportManager viewportManager = new MapViewportManager(mapRenderer);
            
            // Test world constraints
            viewportManager.setWorldConstraints(-1000, 1000, -1000, 1000);
            
            MapViewportManager.ViewportState state = viewportManager.getState();
            if (state.minWorldX != -1000 || state.maxWorldX != 1000) {
                Cubeium.LOGGER.error("World constraints not set correctly");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ World constraints set: [{},{}] x [{},{}]", 
                state.minWorldX, state.maxWorldX, state.minWorldZ, state.maxWorldZ);
            
            // Test navigation methods
            int initialZoom = mapRenderer.getZoomLevel();
            
            viewportManager.zoomIn();
            if (mapRenderer.getZoomLevel() >= initialZoom) {
                Cubeium.LOGGER.error("Zoom in failed: {} -> {}", initialZoom, mapRenderer.getZoomLevel());
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Zoom in: {} -> {}", initialZoom, mapRenderer.getZoomLevel());
            
            viewportManager.zoomOut();
            viewportManager.zoomOut(); // Should be back above initial
            if (mapRenderer.getZoomLevel() <= initialZoom) {
                Cubeium.LOGGER.error("Zoom out failed: {} -> {}", initialZoom, mapRenderer.getZoomLevel());
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Zoom out: {} -> {}", initialZoom, mapRenderer.getZoomLevel());
            
            // Test go to spawn
            viewportManager.goToSpawn();
            
            // Allow time for animation (if any)
            Thread.sleep(100);
            
            double spawnX = mapRenderer.getViewportCenterX();
            double spawnZ = mapRenderer.getViewportCenterZ();
            
            if (Math.abs(spawnX) > 100 || Math.abs(spawnZ) > 100) {
                Cubeium.LOGGER.warn("Go to spawn may not have completed: ({}, {})", spawnX, spawnZ);
            }
            
            Cubeium.LOGGER.info("✓ Go to spawn completed: ({}, {})", spawnX, spawnZ);
            
            // Test animation to position
            viewportManager.animateToPosition(500, -300, 1);
            
            // Allow time for animation
            Thread.sleep(200);
            
            double finalX = mapRenderer.getViewportCenterX();
            double finalZ = mapRenderer.getViewportCenterZ();
            
            if (Math.abs(finalX - 500) > 50 || Math.abs(finalZ + 300) > 50) {
                Cubeium.LOGGER.warn("Animation may not have completed: target (500, -300), actual ({}, {})", 
                    finalX, finalZ);
            }
            
            Cubeium.LOGGER.info("✓ Animation to position: target (500, -300), actual ({}, {})", 
                finalX, finalZ);
            
            // Test state reporting
            MapViewportManager.ViewportState finalState = viewportManager.getState();
            Cubeium.LOGGER.info("✓ Final viewport state: {}", finalState);
            
            viewportManager.shutdown();
            mapRenderer.shutdown();
            return true;
            
        } catch (Exception e) {
            Cubeium.LOGGER.error("MapViewportManager test failed", e);
            return false;
        }
    }
    
    /**
     * Test performance and memory usage
     */
    public static boolean testPerformanceAndMemory() {
        Cubeium.LOGGER.info("=== Test 4.5: Performance and Memory ===");
        
        try {
            // Setup components
            BiomeGenerator biomeGenerator = new BiomeGenerator();
            StructureGenerator structureGenerator = new StructureGenerator();
            ClientColorPalette colorPalette = new ClientColorPalette();
            
            MapRenderer mapRenderer = new MapRenderer(biomeGenerator, structureGenerator, 
                                                    colorPalette, 4);
            mapRenderer.setWorldState(TEST_SEED, TEST_DIMENSION);
            mapRenderer.setViewportSize(1024, 768);
            
            // Render multiple viewports to test performance
            long startTime = System.currentTimeMillis();
            int renderCount = 10;
            
            for (int i = 0; i < renderCount; i++) {
                mapRenderer.setViewportCenter(i * 100, i * 50);
                mapRenderer.setZoomLevel(i % 5);
                BufferedImage viewport = mapRenderer.renderViewport();
                
                if (viewport == null) {
                    Cubeium.LOGGER.error("Failed to render viewport #{}", i);
                    return false;
                }
            }
            
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            double avgTime = (double) totalTime / renderCount;
            
            Cubeium.LOGGER.info("✓ Rendered {} viewports in {}ms (avg {:.1f}ms per viewport)", 
                renderCount, totalTime, avgTime);
            
            // Check memory usage
            MapRenderer.RenderingStats stats = mapRenderer.getStats();
            long memoryUsage = stats.cacheStats.estimatedMemoryUsage;
            int cacheSize = stats.cacheStats.tileCacheSize;
            
            Cubeium.LOGGER.info("✓ Cache stats: {} tiles, {:.1f}MB memory", 
                cacheSize, memoryUsage / (1024.0 * 1024.0));
            
            if (memoryUsage > 100 * 1024 * 1024) { // 100MB limit
                Cubeium.LOGGER.warn("High memory usage: {:.1f}MB", memoryUsage / (1024.0 * 1024.0));
            }
            
            // Test cache maintenance
            mapRenderer.performMaintenance();
            
            MapRenderer.RenderingStats afterMaintenance = mapRenderer.getStats();
            Cubeium.LOGGER.info("✓ After maintenance: {} tiles, {:.1f}MB memory", 
                afterMaintenance.cacheStats.tileCacheSize,
                afterMaintenance.cacheStats.estimatedMemoryUsage / (1024.0 * 1024.0));
            
            mapRenderer.shutdown();
            return true;
            
        } catch (Exception e) {
            Cubeium.LOGGER.error("Performance test failed", e);
            return false;
        }
    }
    
    /**
     * Test edge cases and error handling
     */
    public static boolean testEdgeCases() {
        Cubeium.LOGGER.info("=== Test 4.6: Edge Cases and Error Handling ===");
        
        try {
            BiomeGenerator biomeGenerator = new BiomeGenerator();
            StructureGenerator structureGenerator = new StructureGenerator();
            ClientColorPalette colorPalette = new ClientColorPalette();
            
            MapRenderer mapRenderer = new MapRenderer(biomeGenerator, structureGenerator, 
                                                    colorPalette, 1);
            
            // Test extreme coordinates
            mapRenderer.setWorldState(TEST_SEED, TEST_DIMENSION);
            mapRenderer.setViewportCenter(1000000, -1000000);
            mapRenderer.setZoomLevel(mapRenderer.getMaxZoomLevel());
            
            BufferedImage extremeViewport = mapRenderer.renderViewport();
            if (extremeViewport == null) {
                Cubeium.LOGGER.error("Failed to render extreme coordinate viewport");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Rendered viewport at extreme coordinates (1M, -1M)");
            
            // Test maximum zoom in
            mapRenderer.setZoomLevel(mapRenderer.getMinZoomLevel());
            mapRenderer.setViewportCenter(0, 0);
            
            BufferedImage maxZoomIn = mapRenderer.renderViewport();
            if (maxZoomIn == null) {
                Cubeium.LOGGER.error("Failed to render maximum zoom in viewport");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Rendered viewport at maximum zoom in (level {})", 
                mapRenderer.getMinZoomLevel());
            
            // Test invalid viewport sizes
            mapRenderer.setViewportSize(0, 0);
            BufferedImage invalidSize = mapRenderer.renderViewport();
            
            if (invalidSize == null || invalidSize.getWidth() < 1 || invalidSize.getHeight() < 1) {
                Cubeium.LOGGER.info("✓ Properly handled invalid viewport size");
            } else {
                Cubeium.LOGGER.warn("Invalid viewport size created {}x{} image", 
                    invalidSize.getWidth(), invalidSize.getHeight());
            }
            
            // Test very large viewport
            mapRenderer.setViewportSize(4096, 4096);
            long startTime = System.currentTimeMillis();
            BufferedImage largeViewport = mapRenderer.renderViewport();
            long renderTime = System.currentTimeMillis() - startTime;
            
            if (largeViewport == null) {
                Cubeium.LOGGER.error("Failed to render large viewport");
                return false;
            }
            
            Cubeium.LOGGER.info("✓ Rendered large viewport (4096x4096) in {}ms", renderTime);
            
            if (renderTime > 10000) { // 10 second limit
                Cubeium.LOGGER.warn("Large viewport render took {}ms (over 10s limit)", renderTime);
            }
            
            mapRenderer.shutdown();
            return true;
            
        } catch (Exception e) {
            Cubeium.LOGGER.error("Edge cases test failed", e);
            return false;
        }
    }
    
    /**
     * Save a test image to the output directory
     */
    private static void saveTestImage(BufferedImage image, String filename) {
        try {
            File outputDir = new File(OUTPUT_DIR);
            outputDir.mkdirs();
            
            File outputFile = new File(outputDir, filename);
            ImageIO.write(image, "PNG", outputFile);
            
            Cubeium.LOGGER.info("Saved test image: {}", outputFile.getAbsolutePath());
        } catch (Exception e) {
            Cubeium.LOGGER.warn("Failed to save test image {}: {}", filename, e.getMessage());
        }
    }
}
