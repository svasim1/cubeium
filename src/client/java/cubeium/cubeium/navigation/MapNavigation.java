package cubeium.cubeium.navigation;

import cubeium.cubeium.rendering.MapRenderer;

/**
 * MapNavigation class for coordinate transformations and navigation utilities.
 * Provides coordinate conversion between different scales (block/chunk/region)
 * and navigation helper functions for the map system.
 */
public class MapNavigation {
    
    // Minecraft coordinate constants
    public static final int BLOCKS_PER_CHUNK = 16;
    public static final int CHUNKS_PER_REGION = 32;
    public static final int BLOCKS_PER_REGION = BLOCKS_PER_CHUNK * CHUNKS_PER_REGION; // 512
    
    // Map scale constants
    public static final double MIN_ZOOM_SCALE = 0.0625; // 1/16 - very zoomed in (1 pixel = 1/16 block)
    public static final double MAX_ZOOM_SCALE = 64.0;   // very zoomed out (1 pixel = 64 blocks)
    
    private final MapRenderer mapRenderer;
    
    /**
     * Create a MapNavigation instance
     * @param mapRenderer Map renderer for coordinate transformations
     */
    public MapNavigation(MapRenderer mapRenderer) {
        this.mapRenderer = mapRenderer;
    }
    
    // ===============================
    // Coordinate System Conversions
    // ===============================
    
    /**
     * Convert block coordinates to chunk coordinates
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return Chunk coordinates as int[2] {chunkX, chunkZ}
     */
    public static int[] blockToChunk(int blockX, int blockZ) {
        return new int[] {
            Math.floorDiv(blockX, BLOCKS_PER_CHUNK),
            Math.floorDiv(blockZ, BLOCKS_PER_CHUNK)
        };
    }
    
    /**
     * Convert chunk coordinates to block coordinates (center of chunk)
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return Block coordinates as int[2] {blockX, blockZ}
     */
    public static int[] chunkToBlock(int chunkX, int chunkZ) {
        return new int[] {
            chunkX * BLOCKS_PER_CHUNK + BLOCKS_PER_CHUNK / 2,
            chunkZ * BLOCKS_PER_CHUNK + BLOCKS_PER_CHUNK / 2
        };
    }
    
    /**
     * Convert block coordinates to region coordinates
     * @param blockX Block X coordinate
     * @param blockZ Block Z coordinate
     * @return Region coordinates as int[2] {regionX, regionZ}
     */
    public static int[] blockToRegion(int blockX, int blockZ) {
        return new int[] {
            Math.floorDiv(blockX, BLOCKS_PER_REGION),
            Math.floorDiv(blockZ, BLOCKS_PER_REGION)
        };
    }
    
    /**
     * Convert region coordinates to block coordinates (center of region)
     * @param regionX Region X coordinate
     * @param regionZ Region Z coordinate
     * @return Block coordinates as int[2] {blockX, blockZ}
     */
    public static int[] regionToBlock(int regionX, int regionZ) {
        return new int[] {
            regionX * BLOCKS_PER_REGION + BLOCKS_PER_REGION / 2,
            regionZ * BLOCKS_PER_REGION + BLOCKS_PER_REGION / 2
        };
    }
    
    /**
     * Convert chunk coordinates to region coordinates
     * @param chunkX Chunk X coordinate
     * @param chunkZ Chunk Z coordinate
     * @return Region coordinates as int[2] {regionX, regionZ}
     */
    public static int[] chunkToRegion(int chunkX, int chunkZ) {
        return new int[] {
            Math.floorDiv(chunkX, CHUNKS_PER_REGION),
            Math.floorDiv(chunkZ, CHUNKS_PER_REGION)
        };
    }
    
    /**
     * Convert region coordinates to chunk coordinates (center of region)
     * @param regionX Region X coordinate
     * @param regionZ Region Z coordinate
     * @return Chunk coordinates as int[2] {chunkX, chunkZ}
     */
    public static int[] regionToChunk(int regionX, int regionZ) {
        return new int[] {
            regionX * CHUNKS_PER_REGION + CHUNKS_PER_REGION / 2,
            regionZ * CHUNKS_PER_REGION + CHUNKS_PER_REGION / 2
        };
    }
    
    // ===============================
    // Screen to World Coordinate Transformations
    // ===============================
    
    /**
     * Convert screen coordinates to world coordinates using current viewport
     * @param screenX Screen X coordinate (pixels)
     * @param screenY Screen Y coordinate (pixels)
     * @return World coordinates as double[2] {worldX, worldZ}
     */
    public double[] screenToWorld(int screenX, int screenY) {
        return mapRenderer.screenToWorld(screenX, screenY);
    }
    
    /**
     * Convert world coordinates to screen coordinates using current viewport
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return Screen coordinates as int[2] {screenX, screenY}
     */
    public int[] worldToScreen(double worldX, double worldZ) {
        return mapRenderer.worldToScreen(worldX, worldZ);
    }
    
    // ===============================
    // Navigation Utilities
    // ===============================
    
    /**
     * Calculate distance between two world coordinates
     * @param x1 First X coordinate
     * @param z1 First Z coordinate
     * @param x2 Second X coordinate
     * @param z2 Second Z coordinate
     * @return Distance in blocks
     */
    public static double calculateDistance(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    /**
     * Calculate the angle between two world coordinates
     * @param x1 First X coordinate
     * @param z1 First Z coordinate
     * @param x2 Second X coordinate
     * @param z2 Second Z coordinate
     * @return Angle in radians (0 = east, π/2 = south, π = west, 3π/2 = north)
     */
    public static double calculateAngle(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.atan2(dz, dx);
    }
    
    /**
     * Get the current viewport center
     * @return Center coordinates as double[2] {centerX, centerZ}
     */
    public double[] getViewportCenter() {
        return new double[] {
            mapRenderer.getViewportCenterX(),
            mapRenderer.getViewportCenterZ()
        };
    }
    
    /**
     * Get the current zoom level
     * @return Current zoom level
     */
    public int getZoomLevel() {
        return mapRenderer.getZoomLevel();
    }
    
    /**
     * Get the current scale factor
     * @return Scale factor (blocks per pixel)
     */
    public double getScale() {
        return Math.pow(2.0, mapRenderer.getZoomLevel());
    }
    
    /**
     * Calculate world area visible in current viewport
     * @return World bounds as double[4] {minX, minZ, maxX, maxZ}
     */
    public double[] getVisibleWorldBounds() {
        double centerX = mapRenderer.getViewportCenterX();
        double centerZ = mapRenderer.getViewportCenterZ();
        double scale = getScale();
        
        int viewportWidth = mapRenderer.getViewportWidth();
        int viewportHeight = mapRenderer.getViewportHeight();
        
        double halfWorldWidth = (viewportWidth * scale) / 2.0;
        double halfWorldHeight = (viewportHeight * scale) / 2.0;
        
        return new double[] {
            centerX - halfWorldWidth,  // minX
            centerZ - halfWorldHeight, // minZ
            centerX + halfWorldWidth,  // maxX
            centerZ + halfWorldHeight  // maxZ
        };
    }
    
    /**
     * Check if a world coordinate is visible in current viewport
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return true if coordinate is visible
     */
    public boolean isCoordinateVisible(double worldX, double worldZ) {
        double[] bounds = getVisibleWorldBounds();
        return worldX >= bounds[0] && worldX <= bounds[2] && 
               worldZ >= bounds[1] && worldZ <= bounds[3];
    }
    
    /**
     * Get the appropriate scale level description for current zoom
     * @return Scale description string
     */
    public String getScaleDescription() {
        double scale = getScale();
        
        if (scale < 1.0) {
            return String.format("1 pixel = %.2f blocks", scale);
        } else if (scale < BLOCKS_PER_CHUNK) {
            return String.format("1 pixel = %.0f blocks", scale);
        } else if (scale < BLOCKS_PER_REGION) {
            double chunksPerPixel = scale / BLOCKS_PER_CHUNK;
            return String.format("1 pixel = %.1f chunks", chunksPerPixel);
        } else {
            double regionsPerPixel = scale / BLOCKS_PER_REGION;
            return String.format("1 pixel = %.2f regions", regionsPerPixel);
        }
    }
    
    /**
     * Get appropriate grid scale for current zoom level
     * @return Grid spacing in blocks
     */
    public int getGridScale() {
        double scale = getScale();
        
        if (scale <= 1.0) {
            return 1; // Block grid
        } else if (scale <= 16.0) {
            return BLOCKS_PER_CHUNK; // Chunk grid
        } else if (scale <= 512.0) {
            return BLOCKS_PER_REGION; // Region grid
        } else {
            return BLOCKS_PER_REGION * 4; // Large region grid
        }
    }
    
    // ===============================
    // Coordinate Formatting
    // ===============================
    
    /**
     * Format world coordinates as a readable string
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return Formatted coordinate string
     */
    public static String formatCoordinates(double worldX, double worldZ) {
        return String.format("(%.0f, %.0f)", worldX, worldZ);
    }
    
    /**
     * Format coordinates with scale information
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return Formatted coordinate string with chunk/region info
     */
    public static String formatCoordinatesDetailed(double worldX, double worldZ) {
        int blockX = (int) Math.round(worldX);
        int blockZ = (int) Math.round(worldZ);
        
        int[] chunk = blockToChunk(blockX, blockZ);
        int[] region = blockToRegion(blockX, blockZ);
        
        return String.format("Block: (%d, %d) | Chunk: (%d, %d) | Region: (%d, %d)",
                           blockX, blockZ, chunk[0], chunk[1], region[0], region[1]);
    }
    
    // ===============================
    // Navigation Constraints
    // ===============================
    
    /**
     * Clamp coordinates to reasonable world bounds
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return Clamped coordinates as double[2] {clampedX, clampedZ}
     */
    public static double[] clampToWorldBounds(double worldX, double worldZ) {
        // Minecraft world border is at ±30,000,000 blocks
        final double MAX_COORD = 30_000_000;
        final double MIN_COORD = -30_000_000;
        
        return new double[] {
            Math.max(MIN_COORD, Math.min(MAX_COORD, worldX)),
            Math.max(MIN_COORD, Math.min(MAX_COORD, worldZ))
        };
    }
    
    /**
     * Calculate optimal zoom level for viewing a specific area
     * @param minX Minimum X coordinate
     * @param minZ Minimum Z coordinate
     * @param maxX Maximum X coordinate
     * @param maxZ Maximum Z coordinate
     * @param viewportWidth Viewport width in pixels
     * @param viewportHeight Viewport height in pixels
     * @return Optimal zoom level
     */
    public int calculateOptimalZoom(double minX, double minZ, double maxX, double maxZ,
                                   int viewportWidth, int viewportHeight) {
        double worldWidth = Math.abs(maxX - minX);
        double worldHeight = Math.abs(maxZ - minZ);
        
        // Calculate scale needed to fit both dimensions
        double scaleX = worldWidth / viewportWidth;
        double scaleY = worldHeight / viewportHeight;
        double requiredScale = Math.max(scaleX, scaleY);
        
        // Add 10% margin
        requiredScale *= 1.1;
        
        // Convert to zoom level (scale = 2^zoom)
        int zoomLevel = (int) Math.ceil(Math.log(requiredScale) / Math.log(2.0));
        
        // Clamp to valid range
        return Math.max(mapRenderer.getMinZoomLevel(), 
                       Math.min(mapRenderer.getMaxZoomLevel(), zoomLevel));
    }
}
