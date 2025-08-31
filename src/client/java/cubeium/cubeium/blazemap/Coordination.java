package cubeium.cubeium.blazemap;

/**
 * BlazeMap-style coordinate calculation system for seed maps.
 * Handles precise coordinate transformations and viewport management.
 */
public class Coordination {
    
    /**
     * Calculate world coordinates from screen position using BlazeMap's method
     */
    public static CoordinateResult calculate(double mouseX, double mouseY, 
                                           int screenX, int screenY, int screenWidth, int screenHeight,
                                           double centerX, double centerZ, double scale, 
                                           double guiScale) {
        
        // Convert screen coordinates to viewport-relative coordinates
        double relativeX = mouseX - screenX;
        double relativeY = mouseY - screenY;
        
        // Apply GUI scale compensation (BlazeMap approach)
        relativeX /= guiScale;
        relativeY /= guiScale;
        
        // Convert to world coordinates
        double worldX = centerX + (relativeX - screenWidth / 2.0) * scale;
        double worldZ = centerZ + (relativeY - screenHeight / 2.0) * scale;
        
        return new CoordinateResult((int) Math.floor(worldX), (int) Math.floor(worldZ));
    }
    
    /**
     * Calculate screen position from world coordinates
     */
    public static ScreenResult worldToScreen(double worldX, double worldZ,
                                           int screenX, int screenY, int screenWidth, int screenHeight,
                                           double centerX, double centerZ, double scale,
                                           double guiScale) {
        
        // Convert world to viewport relative
        double relativeX = (worldX - centerX) / scale + screenWidth / 2.0;
        double relativeY = (worldZ - centerZ) / scale + screenHeight / 2.0;
        
        // Apply GUI scale and convert to screen coordinates
        double screenPosX = screenX + relativeX * guiScale;
        double screenPosY = screenY + relativeY * guiScale;
        
        return new ScreenResult(screenPosX, screenPosY);
    }
    
    /**
     * Get zoom scale from zoom level (BlazeMap approach)
     */
    public static double getScale(int zoomLevel) {
        // BlazeMap-style zoom: lower values = more zoomed in
        return Math.pow(2.0, zoomLevel - 8); // Center around level 8
    }
    
    /**
     * Get zoom level from scale
     */
    public static int getZoomLevel(double scale) {
        return (int) Math.round(Math.log(scale) / Math.log(2.0) + 8);
    }
    
    /**
     * Clamp zoom level to valid range
     */
    public static int clampZoomLevel(int zoomLevel) {
        return Math.max(-4, Math.min(6, zoomLevel)); // BlazeMap-style range
    }
    
    /**
     * Result of coordinate calculation
     */
    public static class CoordinateResult {
        public final int worldX;
        public final int worldZ;
        
        public CoordinateResult(int worldX, int worldZ) {
            this.worldX = worldX;
            this.worldZ = worldZ;
        }
    }
    
    /**
     * Result of screen position calculation
     */
    public static class ScreenResult {
        public final double screenX;
        public final double screenY;
        
        public ScreenResult(double screenX, double screenY) {
            this.screenX = screenX;
            this.screenY = screenY;
        }
    }
}
