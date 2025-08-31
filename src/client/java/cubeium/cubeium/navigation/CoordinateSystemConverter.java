package cubeium.cubeium.navigation;

import cubeium.cubeium.rendering.MapRenderer;

/**
 * Advanced coordinate system conversion supporting multiple scales and display modes.
 * Provides dynamic switching between block, chunk, and region coordinate systems
 * with automatic scale selection based on zoom level.
 */
public class CoordinateSystemConverter {
    
    /**
     * Coordinate system scale types
     */
    public enum CoordinateScale {
        BLOCK("Block", 1, "blocks"),
        CHUNK("Chunk", MapNavigation.BLOCKS_PER_CHUNK, "chunks"), 
        REGION("Region", MapNavigation.BLOCKS_PER_REGION, "regions"),
        AUTO("Auto", 0, "auto"); // Automatically selects based on zoom
        
        public final String displayName;
        public final int blocksPerUnit;
        public final String unitName;
        
        CoordinateScale(String displayName, int blocksPerUnit, String unitName) {
            this.displayName = displayName;
            this.blocksPerUnit = blocksPerUnit;
            this.unitName = unitName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    /**
     * Coordinate display formats
     */
    public enum CoordinateFormat {
        SIMPLE("X, Z"),                    // Simple coordinates: "123, 456"
        DETAILED("X: %d, Z: %d"),         // Detailed format: "X: 123, Z: 456"
        PARENTHESES("(%d, %d)"),          // Parentheses format: "(123, 456)"
        MINECRAFT("[X: %d, Z: %d]"),      // Minecraft style: "[X: 123, Z: 456]"
        TECHNICAL("X=%d Z=%d"),           // Technical format: "X=123 Z=456"
        GRID("%d,%d");                    // Grid format: "123,456"
        
        public final String template;
        
        CoordinateFormat(String template) {
            this.template = template;
        }
        
        @Override
        public String toString() {
            return name().toLowerCase().replace('_', ' ');
        }
    }
    
    private final MapRenderer mapRenderer;
    private final MapNavigation navigation;
    
    // Current settings
    private CoordinateScale currentScale = CoordinateScale.AUTO;
    private CoordinateFormat currentFormat = CoordinateFormat.SIMPLE;
    private boolean showAllScales = false;
    private boolean showRelativeCoordinates = false;
    private boolean showDistanceFromSpawn = false;
    
    // Scale transition thresholds
    private static final int BLOCK_TO_CHUNK_THRESHOLD = 4;  // Zoom level where chunks become more relevant
    private static final int CHUNK_TO_REGION_THRESHOLD = 7; // Zoom level where regions become more relevant
    
    // Reference point for relative coordinates
    private double referenceX = 0.0;
    private double referenceZ = 0.0;
    
    /**
     * Create coordinate system converter
     * @param mapRenderer Map renderer instance
     */
    public CoordinateSystemConverter(MapRenderer mapRenderer) {
        this.mapRenderer = mapRenderer;
        this.navigation = new MapNavigation(mapRenderer);
    }
    
    /**
     * Convert world coordinates to current coordinate system
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return Converted coordinates as CoordinateInfo
     */
    public CoordinateInfo convertCoordinates(double worldX, double worldZ) {
        CoordinateScale scale = getEffectiveScale();
        
        // Convert to appropriate scale
        double convertedX, convertedZ;
        String scaleName;
        
        switch (scale) {
            case BLOCK:
                convertedX = worldX;
                convertedZ = worldZ;
                scaleName = "blocks";
                break;
            case CHUNK:
                int[] chunkCoords = MapNavigation.blockToChunk((int)worldX, (int)worldZ);
                convertedX = chunkCoords[0];
                convertedZ = chunkCoords[1];
                scaleName = "chunks";
                break;
            case REGION:
                int[] regionCoords = MapNavigation.blockToRegion((int)worldX, (int)worldZ);
                convertedX = regionCoords[0];
                convertedZ = regionCoords[1];
                scaleName = "regions";
                break;
            default:
                convertedX = worldX;
                convertedZ = worldZ;
                scaleName = "blocks";
                break;
        }
        
        return new CoordinateInfo(
            worldX, worldZ,
            convertedX, convertedZ,
            scale, scaleName,
            calculateRelativeCoordinates(worldX, worldZ),
            calculateDistanceFromSpawn(worldX, worldZ)
        );
    }
    
    /**
     * Format coordinates according to current format settings
     * @param info Coordinate information
     * @return Formatted coordinate string
     */
    public String formatCoordinates(CoordinateInfo info) {
        StringBuilder result = new StringBuilder();
        
        // Main coordinates
        String mainCoords = formatSingleCoordinate(info.convertedX, info.convertedZ, currentFormat);
        result.append(mainCoords);
        
        // Add scale indicator if not obvious
        if (currentScale == CoordinateScale.AUTO || showAllScales) {
            result.append(" ").append(info.scaleName);
        }
        
        // Add all scales if requested
        if (showAllScales) {
            result.append("\n");
            result.append(formatAllScales(info.worldX, info.worldZ));
        }
        
        // Add relative coordinates if enabled
        if (showRelativeCoordinates) {
            result.append(" (rel: ").append(formatRelativeCoordinates(info.relativeX, info.relativeZ)).append(")");
        }
        
        // Add distance from spawn if enabled
        if (showDistanceFromSpawn) {
            result.append(" [").append(String.format("%.0f blocks from spawn", info.distanceFromSpawn)).append("]");
        }
        
        return result.toString();
    }
    
    /**
     * Format coordinates for all scale systems
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return Multi-line formatted string with all scales
     */
    public String formatAllScales(double worldX, double worldZ) {
        StringBuilder result = new StringBuilder();
        
        // Block coordinates
        result.append("Block: ").append(formatSingleCoordinate(worldX, worldZ, CoordinateFormat.PARENTHESES)).append("\n");
        
        // Chunk coordinates
        int[] chunkCoords = MapNavigation.blockToChunk((int)worldX, (int)worldZ);
        result.append("Chunk: ").append(formatSingleCoordinate(chunkCoords[0], chunkCoords[1], CoordinateFormat.PARENTHESES));
        
        // Add chunk-relative block position
        int blockInChunkX = ((int)worldX) % MapNavigation.BLOCKS_PER_CHUNK;
        int blockInChunkZ = ((int)worldZ) % MapNavigation.BLOCKS_PER_CHUNK;
        if (blockInChunkX < 0) blockInChunkX += MapNavigation.BLOCKS_PER_CHUNK;
        if (blockInChunkZ < 0) blockInChunkZ += MapNavigation.BLOCKS_PER_CHUNK;
        result.append(" [").append(blockInChunkX).append(",").append(blockInChunkZ).append("]\n");
        
        // Region coordinates
        int[] regionCoords = MapNavigation.blockToRegion((int)worldX, (int)worldZ);
        result.append("Region: ").append(formatSingleCoordinate(regionCoords[0], regionCoords[1], CoordinateFormat.PARENTHESES));
        
        // Add region file name
        result.append(" (r.").append(regionCoords[0]).append(".").append(regionCoords[1]).append(".mca)");
        
        return result.toString();
    }
    
    /**
     * Get coordinate display appropriate for current zoom level
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @return Zoom-appropriate coordinate string
     */
    public String getZoomAppropriateDisplay(double worldX, double worldZ) {
        CoordinateInfo info = convertCoordinates(worldX, worldZ);
        int zoom = mapRenderer.getZoomLevel();
        
        if (zoom <= BLOCK_TO_CHUNK_THRESHOLD) {
            // Detailed view - show block coordinates with chunk info
            return String.format("Block: (%.0f, %.0f) in chunk (%d, %d)", 
                worldX, worldZ, 
                MapNavigation.blockToChunk((int)worldX, (int)worldZ)[0],
                MapNavigation.blockToChunk((int)worldX, (int)worldZ)[1]);
        } else if (zoom <= CHUNK_TO_REGION_THRESHOLD) {
            // Medium view - show chunk coordinates
            int[] chunkCoords = MapNavigation.blockToChunk((int)worldX, (int)worldZ);
            return String.format("Chunk: (%d, %d)", chunkCoords[0], chunkCoords[1]);
        } else {
            // Zoomed out - show region coordinates
            int[] regionCoords = MapNavigation.blockToRegion((int)worldX, (int)worldZ);
            return String.format("Region: (%d, %d)", regionCoords[0], regionCoords[1]);
        }
    }
    
    /**
     * Get grid lines appropriate for current zoom and coordinate system
     * @return Grid spacing information
     */
    public GridInfo getAppropriateGrid() {
        int zoom = mapRenderer.getZoomLevel();
        CoordinateScale scale = getEffectiveScale();
        
        int majorSpacing, minorSpacing;
        String gridType;
        
        switch (scale) {
            case BLOCK:
                majorSpacing = MapNavigation.BLOCKS_PER_CHUNK;
                minorSpacing = zoom <= 0 ? 1 : (zoom <= 2 ? 4 : 8);
                gridType = "Block grid";
                break;
            case CHUNK:
                majorSpacing = MapNavigation.CHUNKS_PER_REGION * MapNavigation.BLOCKS_PER_CHUNK;
                minorSpacing = MapNavigation.BLOCKS_PER_CHUNK;
                gridType = "Chunk grid";
                break;
            case REGION:
                majorSpacing = MapNavigation.BLOCKS_PER_REGION * 4; // 4 regions
                minorSpacing = MapNavigation.BLOCKS_PER_REGION;
                gridType = "Region grid";
                break;
            default:
                majorSpacing = navigation.getGridScale();
                minorSpacing = majorSpacing / 4;
                gridType = "Auto grid";
                break;
        }
        
        return new GridInfo(majorSpacing, minorSpacing, gridType, scale);
    }
    
    /**
     * Convert coordinates from one scale to another
     * @param coordinates Coordinates to convert
     * @param fromScale Source scale
     * @param toScale Target scale  
     * @return Converted coordinates
     */
    public double[] convertBetweenScales(double[] coordinates, CoordinateScale fromScale, CoordinateScale toScale) {
        if (coordinates.length != 2) {
            throw new IllegalArgumentException("Coordinates must be [x, z] array");
        }
        
        // Convert to world coordinates first
        double worldX, worldZ;
        
        switch (fromScale) {
            case BLOCK:
                worldX = coordinates[0];
                worldZ = coordinates[1];
                break;
            case CHUNK:
                int[] blockFromChunk = MapNavigation.chunkToBlock((int)coordinates[0], (int)coordinates[1]);
                worldX = blockFromChunk[0];
                worldZ = blockFromChunk[1];
                break;
            case REGION:
                int[] blockFromRegion = MapNavigation.regionToBlock((int)coordinates[0], (int)coordinates[1]);
                worldX = blockFromRegion[0];
                worldZ = blockFromRegion[1];
                break;
            default:
                worldX = coordinates[0];
                worldZ = coordinates[1];
                break;
        }
        
        // Convert from world coordinates to target scale
        switch (toScale) {
            case BLOCK:
                return new double[]{worldX, worldZ};
            case CHUNK:
                int[] chunkCoords = MapNavigation.blockToChunk((int)worldX, (int)worldZ);
                return new double[]{chunkCoords[0], chunkCoords[1]};
            case REGION:
                int[] regionCoords = MapNavigation.blockToRegion((int)worldX, (int)worldZ);
                return new double[]{regionCoords[0], regionCoords[1]};
            default:
                return new double[]{worldX, worldZ};
        }
    }
    
    // ===============================
    // Private Helper Methods
    // ===============================
    
    /**
     * Get the effective coordinate scale based on current settings
     */
    private CoordinateScale getEffectiveScale() {
        if (currentScale != CoordinateScale.AUTO) {
            return currentScale;
        }
        
        // Auto-select based on zoom level
        int zoom = mapRenderer.getZoomLevel();
        
        if (zoom <= BLOCK_TO_CHUNK_THRESHOLD) {
            return CoordinateScale.BLOCK;
        } else if (zoom <= CHUNK_TO_REGION_THRESHOLD) {
            return CoordinateScale.CHUNK;
        } else {
            return CoordinateScale.REGION;
        }
    }
    
    /**
     * Format a single coordinate pair
     */
    private String formatSingleCoordinate(double x, double z, CoordinateFormat format) {
        switch (format) {
            case SIMPLE:
                return String.format("%.0f, %.0f", x, z);
            case DETAILED:
            case MINECRAFT:
            case TECHNICAL:
                return String.format(format.template, (int)x, (int)z);
            case PARENTHESES:
            case GRID:
                return String.format(format.template, (int)x, (int)z);
            default:
                return String.format("%.0f, %.0f", x, z);
        }
    }
    
    /**
     * Calculate relative coordinates from reference point
     */
    private double[] calculateRelativeCoordinates(double worldX, double worldZ) {
        return new double[]{
            worldX - referenceX,
            worldZ - referenceZ
        };
    }
    
    /**
     * Format relative coordinates
     */
    private String formatRelativeCoordinates(double relX, double relZ) {
        String signX = relX >= 0 ? "+" : "";
        String signZ = relZ >= 0 ? "+" : "";
        return String.format("%s%.0f, %s%.0f", signX, relX, signZ, relZ);
    }
    
    /**
     * Calculate distance from spawn (0,0)
     */
    private double calculateDistanceFromSpawn(double worldX, double worldZ) {
        return Math.sqrt(worldX * worldX + worldZ * worldZ);
    }
    
    // ===============================
    // Configuration Methods
    // ===============================
    
    /**
     * Set coordinate scale
     */
    public void setCoordinateScale(CoordinateScale scale) {
        this.currentScale = scale;
    }
    
    /**
     * Set coordinate format
     */
    public void setCoordinateFormat(CoordinateFormat format) {
        this.currentFormat = format;
    }
    
    /**
     * Enable/disable showing all coordinate scales
     */
    public void setShowAllScales(boolean show) {
        this.showAllScales = show;
    }
    
    /**
     * Enable/disable relative coordinates
     */
    public void setShowRelativeCoordinates(boolean show) {
        this.showRelativeCoordinates = show;
    }
    
    /**
     * Enable/disable distance from spawn
     */
    public void setShowDistanceFromSpawn(boolean show) {
        this.showDistanceFromSpawn = show;
    }
    
    /**
     * Set reference point for relative coordinates
     */
    public void setReferencePoint(double x, double z) {
        this.referenceX = x;
        this.referenceZ = z;
    }
    
    /**
     * Set reference point to current viewport center
     */
    public void setReferenceToViewportCenter() {
        this.referenceX = mapRenderer.getViewportCenterX();
        this.referenceZ = mapRenderer.getViewportCenterZ();
    }
    
    /**
     * Reset reference point to spawn (0,0)
     */
    public void resetReferenceToSpawn() {
        this.referenceX = 0.0;
        this.referenceZ = 0.0;
    }
    
    // ===============================
    // Getter Methods
    // ===============================
    
    public CoordinateScale getCurrentScale() { return currentScale; }
    public CoordinateFormat getCurrentFormat() { return currentFormat; }
    public boolean isShowAllScales() { return showAllScales; }
    public boolean isShowRelativeCoordinates() { return showRelativeCoordinates; }
    public boolean isShowDistanceFromSpawn() { return showDistanceFromSpawn; }
    public double[] getReferencePoint() { return new double[]{referenceX, referenceZ}; }
    
    // ===============================
    // Data Classes
    // ===============================
    
    /**
     * Complete coordinate information
     */
    public static class CoordinateInfo {
        public final double worldX, worldZ;
        public final double convertedX, convertedZ;
        public final CoordinateScale scale;
        public final String scaleName;
        public final double[] relativeCoords;
        public final double relativeX, relativeZ;
        public final double distanceFromSpawn;
        
        CoordinateInfo(double worldX, double worldZ, double convertedX, double convertedZ,
                      CoordinateScale scale, String scaleName, double[] relativeCoords,
                      double distanceFromSpawn) {
            this.worldX = worldX;
            this.worldZ = worldZ;
            this.convertedX = convertedX;
            this.convertedZ = convertedZ;
            this.scale = scale;
            this.scaleName = scaleName;
            this.relativeCoords = relativeCoords;
            this.relativeX = relativeCoords[0];
            this.relativeZ = relativeCoords[1];
            this.distanceFromSpawn = distanceFromSpawn;
        }
        
        @Override
        public String toString() {
            return String.format("CoordinateInfo: world=(%.1f,%.1f) converted=(%.1f,%.1f) scale=%s distance=%.1f",
                worldX, worldZ, convertedX, convertedZ, scale, distanceFromSpawn);
        }
    }
    
    /**
     * Grid display information
     */
    public static class GridInfo {
        public final int majorSpacing;
        public final int minorSpacing;
        public final String gridType;
        public final CoordinateScale scale;
        
        GridInfo(int majorSpacing, int minorSpacing, String gridType, CoordinateScale scale) {
            this.majorSpacing = majorSpacing;
            this.minorSpacing = minorSpacing;
            this.gridType = gridType;
            this.scale = scale;
        }
        
        @Override
        public String toString() {
            return String.format("GridInfo: %s - major:%d minor:%d scale:%s",
                gridType, majorSpacing, minorSpacing, scale);
        }
    }
}
