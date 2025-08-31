package cubeium.cubeium.rendering.tile;

import cubeium.cubeium.world.generation.BiomeGenerator;
import cubeium.cubeium.world.generation.StructureGenerator;
import cubeium.cubeium.rendering.cache.MapCache;
import cubeium.cubeium.util.ClientColorPalette;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * High-performance tile renderer for generating map tiles asynchronously.
 * Handles biome coloring, structure overlays, and custom rendering effects.
 */
public class TileRenderer {
    
    // Rendering flags
    public static final int RENDER_BIOMES = 1;
    public static final int RENDER_STRUCTURES = 2;
    public static final int RENDER_ELEVATION = 4;
    public static final int RENDER_GRID = 8;
    public static final int RENDER_PLAYER_POS = 16;
    public static final int RENDER_WAYPOINTS = 32;
    public static final int RENDER_CHUNK_BORDERS = 64;
    
    // Anti-aliasing modes
    public enum AntiAliasing {
        NONE,
        FAST,
        HIGH_QUALITY
    }
    
    private final BiomeGenerator biomeGenerator;
    private final StructureGenerator structureGenerator;
    private final ClientColorPalette colorPalette;
    private final ExecutorService renderExecutor;
    private final MapCache mapCache;
    
    // Rendering settings
    private AntiAliasing antiAliasing = AntiAliasing.FAST;
    private boolean enableStructureLabels = true;
    private boolean enableBiomeBlending = true;
    private float structureOpacity = 1.0f;
    private int gridColor = 0x40FFFFFF;
    private int chunkBorderColor = 0x80808080;
    
    /**
     * Create a TileRenderer
     * @param biomeGenerator Biome generation system
     * @param structureGenerator Structure generation system
     * @param colorPalette Color palette for rendering
     * @param mapCache Map cache for data storage
     * @param threadCount Number of rendering threads
     */
    public TileRenderer(BiomeGenerator biomeGenerator, StructureGenerator structureGenerator,
                       ClientColorPalette colorPalette, MapCache mapCache, int threadCount) {
        this.biomeGenerator = biomeGenerator;
        this.structureGenerator = structureGenerator;
        this.colorPalette = colorPalette;
        this.mapCache = mapCache;
        
        // Create thread pool with custom thread factory
        this.renderExecutor = Executors.newFixedThreadPool(threadCount, new ThreadFactory() {
            private int threadIndex = 0;
            
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "TileRenderer-" + threadIndex++);
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            }
        });
    }
    
    /**
     * Render a tile asynchronously
     * @param request Tile request containing coordinates and settings
     * @return CompletableFuture that will complete with the rendered tile
     */
    public CompletableFuture<RenderedTile> renderTileAsync(MapCache.TileRequest request) {
        return CompletableFuture.supplyAsync(() -> renderTile(request), renderExecutor);
    }
    
    /**
     * Render a tile synchronously
     * @param request Tile request containing coordinates and settings
     * @return RenderedTile with image and metadata
     */
    public RenderedTile renderTile(MapCache.TileRequest request) {
        long startTime = System.nanoTime();
        
        try {
            // Create image
            BufferedImage tileImage = new BufferedImage(
                (int) (request.worldWidth / request.scale),
                (int) (request.worldHeight / request.scale),
                BufferedImage.TYPE_INT_ARGB
            );
            
            Graphics2D g2d = tileImage.createGraphics();
            setupGraphicsSettings(g2d);
            
            // Render layers
            if ((request.renderFlags & RENDER_BIOMES) != 0) {
                renderBiomeLayer(g2d, request);
            } else {
                // Fill with default background
                g2d.setColor(colorPalette.getOceanColor());
                g2d.fillRect(0, 0, tileImage.getWidth(), tileImage.getHeight());
            }
            
            if ((request.renderFlags & RENDER_STRUCTURES) != 0) {
                renderStructureLayer(g2d, request);
            }
            
            if ((request.renderFlags & RENDER_GRID) != 0) {
                renderGridLayer(g2d, request);
            }
            
            if ((request.renderFlags & RENDER_CHUNK_BORDERS) != 0) {
                renderChunkBorders(g2d, request);
            }
            
            g2d.dispose();
            
            // Cache the tile
            mapCache.cacheTile(request.tileX, request.tileZ, request.zoomLevel, 
                              request.renderFlags, tileImage);
            
            long renderTime = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
            
            return new RenderedTile(tileImage, request, renderTime);
            
        } catch (Exception e) {
            System.err.println("Error rendering tile " + request + ": " + e.getMessage());
            e.printStackTrace();
            return new RenderedTile(createErrorTile(request), request, 0);
        }
    }
    
    /**
     * Setup graphics settings based on anti-aliasing mode
     */
    private void setupGraphicsSettings(Graphics2D g2d) {
        switch (antiAliasing) {
            case HIGH_QUALITY:
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
                break;
            case FAST:
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                break;
            case NONE:
            default:
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                break;
        }
    }
    
    /**
     * Render the biome layer
     */
    private void renderBiomeLayer(Graphics2D g2d, MapCache.TileRequest request) {
        // Get or generate biome data
        MapCache.CachedBiomeData cachedBiomes = mapCache.getCachedBiomeData(
            (int) request.worldX, (int) request.worldZ, 
            request.worldWidth, request.worldHeight);
        
        BiomeGenerator.BiomeRegion biomeRegion;
        if (cachedBiomes != null) {
            biomeRegion = cachedBiomes.biomeRegion;
        } else {
            // Generate new biome data
            biomeRegion = biomeGenerator.generateBiomes(
                (int) request.worldX, (int) request.worldZ,
                request.worldWidth, request.worldHeight, 4);
            mapCache.cacheBiomeData((int) request.worldX, (int) request.worldZ,
                                  request.worldWidth, request.worldHeight, biomeRegion);
        }
        
        // Render biome data to image
        renderBiomeRegion(g2d, biomeRegion, request);
    }
    
    /**
     * Render biome data to graphics context
     */
    private void renderBiomeRegion(Graphics2D g2d, BiomeGenerator.BiomeRegion biomeRegion, 
                                  MapCache.TileRequest request) {
        int imageWidth = (int) (request.worldWidth / request.scale);
        int imageHeight = (int) (request.worldHeight / request.scale);
        
        // Create biome image data
        BufferedImage biomeImage = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                // Calculate world coordinates
                double worldX = request.worldX + x * request.scale;
                double worldZ = request.worldZ + y * request.scale;
                
                // Get biome at this position
                int biomeId = biomeRegion.getBiome((int) ((worldX - biomeRegion.x * biomeRegion.scale) / biomeRegion.scale), 
                                                  (int) ((worldZ - biomeRegion.z * biomeRegion.scale) / biomeRegion.scale));
                Color biomeColor = colorPalette.getBiomeColor(biomeId);
                
                // Apply blending if enabled
                if (enableBiomeBlending && request.scale > 0.5) {
                    biomeColor = getBlendedBiomeColor(biomeRegion, worldX, worldZ, biomeColor);
                }
                
                biomeImage.setRGB(x, y, biomeColor.getRGB());
            }
        }
        
        // Draw the biome image
        g2d.drawImage(biomeImage, 0, 0, null);
    }
    
    /**
     * Get blended biome color for smoother transitions
     */
    private Color getBlendedBiomeColor(BiomeGenerator.BiomeRegion biomeRegion, 
                                      double worldX, double worldZ, Color baseColor) {
        // Sample neighboring biomes for blending
        int localX = (int) ((worldX - biomeRegion.x * biomeRegion.scale) / biomeRegion.scale);
        int localZ = (int) ((worldZ - biomeRegion.z * biomeRegion.scale) / biomeRegion.scale);
        
        int centerBiome = biomeRegion.getBiome(localX, localZ);
        
        float totalR = baseColor.getRed();
        float totalG = baseColor.getGreen();
        float totalB = baseColor.getBlue();
        float weight = 1.0f;
        
        // Sample 4 neighbors
        int[] neighbors = {
            getBiomeSafe(biomeRegion, localX - 1, localZ),
            getBiomeSafe(biomeRegion, localX + 1, localZ),
            getBiomeSafe(biomeRegion, localX, localZ - 1),
            getBiomeSafe(biomeRegion, localX, localZ + 1)
        };
        
        for (int neighborBiome : neighbors) {
            if (neighborBiome != centerBiome) {
                Color neighborColor = colorPalette.getBiomeColor(neighborBiome);
                float blendWeight = 0.1f;
                totalR += neighborColor.getRed() * blendWeight;
                totalG += neighborColor.getGreen() * blendWeight;
                totalB += neighborColor.getBlue() * blendWeight;
                weight += blendWeight;
            }
        }
        
        return new Color(
            Math.min(255, (int) (totalR / weight)),
            Math.min(255, (int) (totalG / weight)),
            Math.min(255, (int) (totalB / weight))
        );
    }
    
    /**
     * Get biome safely from region, returns center biome if out of bounds
     */
    private int getBiomeSafe(BiomeGenerator.BiomeRegion biomeRegion, int localX, int localZ) {
        if (localX < 0 || localX >= biomeRegion.width || localZ < 0 || localZ >= biomeRegion.height) {
            return biomeRegion.getBiome(biomeRegion.width / 2, biomeRegion.height / 2);
        }
        return biomeRegion.getBiome(localX, localZ);
    }
    
    /**
     * Render the structure layer
     */
    private void renderStructureLayer(Graphics2D g2d, MapCache.TileRequest request) {
        // Get or generate structure data
        MapCache.CachedStructureData cachedStructures = mapCache.getCachedStructureData(
            (int) request.worldX, (int) request.worldZ,
            request.worldWidth, request.worldHeight);
        
        List<StructureGenerator.StructurePos> structures;
        if (cachedStructures != null) {
            structures = cachedStructures.structures;
        } else {
            // Generate new structure data
            structures = structureGenerator.findStructuresInRegion(
                (int) request.worldX, (int) request.worldZ,
                request.worldWidth, request.worldHeight);
            mapCache.cacheStructureData((int) request.worldX, (int) request.worldZ,
                                       request.worldWidth, request.worldHeight, structures);
        }
        
        // Render structures
        renderStructures(g2d, structures, request);
    }
    
    /**
     * Render structures to graphics context
     */
    private void renderStructures(Graphics2D g2d, List<StructureGenerator.StructurePos> structures,
                                 MapCache.TileRequest request) {
        Composite originalComposite = g2d.getComposite();
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, structureOpacity));
        
        for (StructureGenerator.StructurePos structure : structures) {
            // Calculate screen position
            int screenX = (int) ((structure.x - request.worldX) / request.scale);
            int screenZ = (int) ((structure.z - request.worldZ) / request.scale);
            
            // Skip if outside tile bounds
            if (screenX < -10 || screenX > request.worldWidth / request.scale + 10 ||
                screenZ < -10 || screenZ > request.worldHeight / request.scale + 10) {
                continue;
            }
            
            // Get structure color and icon
            Color structureColor = colorPalette.getStructureColor(structure.type.getName());
            int iconSize = Math.max(2, (int) (8 / request.scale));
            
            // Draw structure icon
            g2d.setColor(structureColor);
            g2d.fillOval(screenX - iconSize/2, screenZ - iconSize/2, iconSize, iconSize);
            
            // Draw border
            g2d.setColor(Color.BLACK);
            g2d.drawOval(screenX - iconSize/2, screenZ - iconSize/2, iconSize, iconSize);
            
            // Draw label if enabled and zoom level is appropriate
            if (enableStructureLabels && request.scale < 2.0) {
                drawStructureLabel(g2d, structure, screenX, screenZ + iconSize/2 + 2);
            }
        }
        
        g2d.setComposite(originalComposite);
    }
    
    /**
     * Draw structure label
     */
    private void drawStructureLabel(Graphics2D g2d, StructureGenerator.StructurePos structure, 
                                   int x, int y) {
        String label = structure.type.getDisplayName();
        FontMetrics fm = g2d.getFontMetrics();
        int labelWidth = fm.stringWidth(label);
        int labelHeight = fm.getHeight();
        
        // Draw label background
        g2d.setColor(new Color(0, 0, 0, 160));
        g2d.fillRoundRect(x - labelWidth/2 - 2, y - labelHeight + 2, 
                         labelWidth + 4, labelHeight + 2, 4, 4);
        
        // Draw label text
        g2d.setColor(Color.WHITE);
        g2d.drawString(label, x - labelWidth/2, y);
    }
    
    /**
     * Render grid layer
     */
    private void renderGridLayer(Graphics2D g2d, MapCache.TileRequest request) {
        g2d.setColor(new Color(gridColor, true));
        g2d.setStroke(new BasicStroke(1.0f));
        
        int imageWidth = (int) (request.worldWidth / request.scale);
        int imageHeight = (int) (request.worldHeight / request.scale);
        
        // Calculate grid spacing
        int gridSpacing = calculateGridSpacing(request.scale);
        
        // Draw vertical lines
        int startX = (int) (Math.ceil(request.worldX / gridSpacing) * gridSpacing);
        for (int worldX = startX; worldX <= request.worldX + request.worldWidth; worldX += gridSpacing) {
            int screenX = (int) ((worldX - request.worldX) / request.scale);
            if (screenX >= 0 && screenX < imageWidth) {
                g2d.drawLine(screenX, 0, screenX, imageHeight);
            }
        }
        
        // Draw horizontal lines
        int startZ = (int) (Math.ceil(request.worldZ / gridSpacing) * gridSpacing);
        for (int worldZ = startZ; worldZ <= request.worldZ + request.worldHeight; worldZ += gridSpacing) {
            int screenZ = (int) ((worldZ - request.worldZ) / request.scale);
            if (screenZ >= 0 && screenZ < imageHeight) {
                g2d.drawLine(0, screenZ, imageWidth, screenZ);
            }
        }
    }
    
    /**
     * Calculate appropriate grid spacing based on zoom level
     */
    private int calculateGridSpacing(double scale) {
        if (scale <= 0.125) return 16;      // Very zoomed in - 16 block grid
        else if (scale <= 0.25) return 32;  // 32 block grid
        else if (scale <= 0.5) return 64;   // 64 block grid
        else if (scale <= 1.0) return 128;  // 128 block grid
        else if (scale <= 2.0) return 256;  // 256 block grid
        else if (scale <= 4.0) return 512;  // 512 block grid
        else return 1024;                   // Very zoomed out - 1024 block grid
    }
    
    /**
     * Render chunk borders
     */
    private void renderChunkBorders(Graphics2D g2d, MapCache.TileRequest request) {
        g2d.setColor(new Color(chunkBorderColor, true));
        g2d.setStroke(new BasicStroke(1.0f));
        
        int imageWidth = (int) (request.worldWidth / request.scale);
        int imageHeight = (int) (request.worldHeight / request.scale);
        
        // Draw chunk borders (16 block grid)
        int chunkSize = 16;
        
        // Draw vertical chunk borders
        int startX = (int) (Math.floor(request.worldX / chunkSize) * chunkSize);
        for (int worldX = startX; worldX <= request.worldX + request.worldWidth; worldX += chunkSize) {
            int screenX = (int) ((worldX - request.worldX) / request.scale);
            if (screenX >= 0 && screenX < imageWidth) {
                g2d.drawLine(screenX, 0, screenX, imageHeight);
            }
        }
        
        // Draw horizontal chunk borders
        int startZ = (int) (Math.floor(request.worldZ / chunkSize) * chunkSize);
        for (int worldZ = startZ; worldZ <= request.worldZ + request.worldHeight; worldZ += chunkSize) {
            int screenZ = (int) ((worldZ - request.worldZ) / request.scale);
            if (screenZ >= 0 && screenZ < imageHeight) {
                g2d.drawLine(0, screenZ, imageWidth, screenZ);
            }
        }
    }
    
    /**
     * Create an error tile when rendering fails
     */
    private BufferedImage createErrorTile(MapCache.TileRequest request) {
        int width = Math.max(1, (int) (request.worldWidth / request.scale));
        int height = Math.max(1, (int) (request.worldHeight / request.scale));
        
        BufferedImage errorImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = errorImage.createGraphics();
        
        // Fill with error pattern (checkerboard)
        g2d.setColor(Color.MAGENTA);
        g2d.fillRect(0, 0, width, height);
        g2d.setColor(Color.BLACK);
        
        int checkerSize = 8;
        for (int y = 0; y < height; y += checkerSize) {
            for (int x = 0; x < width; x += checkerSize) {
                if ((x / checkerSize + y / checkerSize) % 2 == 1) {
                    g2d.fillRect(x, y, Math.min(checkerSize, width - x), Math.min(checkerSize, height - y));
                }
            }
        }
        
        g2d.dispose();
        return errorImage;
    }
    
    // ===============================
    // Settings and Configuration
    // ===============================
    
    public void setAntiAliasing(AntiAliasing antiAliasing) {
        this.antiAliasing = antiAliasing;
    }
    
    public void setEnableStructureLabels(boolean enableStructureLabels) {
        this.enableStructureLabels = enableStructureLabels;
    }
    
    public void setEnableBiomeBlending(boolean enableBiomeBlending) {
        this.enableBiomeBlending = enableBiomeBlending;
    }
    
    public void setStructureOpacity(float structureOpacity) {
        this.structureOpacity = Math.max(0.0f, Math.min(1.0f, structureOpacity));
    }
    
    public void setGridColor(int gridColor) {
        this.gridColor = gridColor;
    }
    
    public void setChunkBorderColor(int chunkBorderColor) {
        this.chunkBorderColor = chunkBorderColor;
    }
    
    /**
     * Shutdown the tile renderer and clean up resources
     */
    public void shutdown() {
        renderExecutor.shutdown();
    }
    
    /**
     * Rendered tile result with metadata
     */
    public static class RenderedTile {
        public final BufferedImage image;
        public final MapCache.TileRequest request;
        public final long renderTimeMs;
        
        public RenderedTile(BufferedImage image, MapCache.TileRequest request, long renderTimeMs) {
            this.image = image;
            this.request = request;
            this.renderTimeMs = renderTimeMs;
        }
        
        @Override
        public String toString() {
            return String.format("RenderedTile[%dx%d, %dms]", 
                image.getWidth(), image.getHeight(), renderTimeMs);
        }
    }
}
