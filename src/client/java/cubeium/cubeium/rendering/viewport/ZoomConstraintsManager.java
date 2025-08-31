package cubeium.cubeium.rendering.viewport;

import cubeium.cubeium.rendering.MapRenderer;

/**
 * Manages zoom level constraints and dynamic zoom limits for the map renderer.
 * Provides advanced constraint features like performance-based limits,
 * content-aware zooming, and adaptive constraints.
 */
public class ZoomConstraintsManager {
    
    // Base zoom constraints (can be overridden)
    private static final int ABSOLUTE_MIN_ZOOM = -6; // Maximum zoom in
    private static final int ABSOLUTE_MAX_ZOOM = 10; // Maximum zoom out
    private static final int DEFAULT_MIN_ZOOM = -4;
    private static final int DEFAULT_MAX_ZOOM = 8;
    
    // Performance-based constraint thresholds
    private static final long PERFORMANCE_MEMORY_THRESHOLD = 1024 * 1024 * 1024; // 1GB
    private static final double PERFORMANCE_FPS_THRESHOLD = 30.0; // Minimum FPS
    private static final int PERFORMANCE_RESTRICTED_MIN_ZOOM = -2; // Less detailed when performance is poor
    
    // Content-aware zoom constraints
    private static final int SPAWN_AREA_ZOOM_MIN = -2; // Don't zoom too far into spawn
    private static final int WORLD_BORDER_ZOOM_MAX = 6; // Don't zoom out past world border
    
    private final MapRenderer mapRenderer;
    
    // Current constraint settings
    private int currentMinZoom = DEFAULT_MIN_ZOOM;
    private int currentMaxZoom = DEFAULT_MAX_ZOOM;
    
    // Dynamic constraint features
    private boolean performanceConstraintsEnabled = true;
    private boolean contentAwareConstraintsEnabled = true;
    private boolean adaptiveConstraintsEnabled = true;
    private boolean smoothConstraintTransitions = true;
    
    // Performance monitoring
    private long lastPerformanceCheck = 0;
    private double averageFPS = 60.0;
    private long currentMemoryUsage = 0;
    private boolean performanceRestricted = false;
    
    // Content awareness
    private double worldBorderRadius = 29999984.0; // Default world border
    private boolean nearSpawn = false;
    private boolean nearWorldBorder = false;
    
    // Constraint change listeners
    private ZoomConstraintsListener listener;
    
    /**
     * Interface for listening to constraint changes
     */
    public interface ZoomConstraintsListener {
        void onConstraintsChanged(int oldMinZoom, int oldMaxZoom, int newMinZoom, int newMaxZoom, String reason);
    }
    
    /**
     * Create a zoom constraints manager
     * @param mapRenderer Map renderer to manage constraints for
     */
    public ZoomConstraintsManager(MapRenderer mapRenderer) {
        this.mapRenderer = mapRenderer;
        updateConstraints("initialization");
    }
    
    /**
     * Update constraints based on current conditions
     */
    public void tick() {
        if (System.currentTimeMillis() - lastPerformanceCheck > 1000) { // Check every second
            updatePerformanceMetrics();
            updateContentAwareness();
            
            String reason = updateConstraints("periodic_update");
            if (reason != null && listener != null) {
                listener.onConstraintsChanged(currentMinZoom, currentMaxZoom, 
                    getCurrentMinZoom(), getCurrentMaxZoom(), reason);
            }
            
            lastPerformanceCheck = System.currentTimeMillis();
        }
    }
    
    /**
     * Update performance metrics
     */
    private void updatePerformanceMetrics() {
        // Get current memory usage
        Runtime runtime = Runtime.getRuntime();
        currentMemoryUsage = runtime.totalMemory() - runtime.freeMemory();
        
        // Update FPS (would need integration with Minecraft's FPS counter)
        // For now, assume good performance
        averageFPS = 60.0;
        
        // Determine if performance restrictions should apply
        boolean wasRestricted = performanceRestricted;
        performanceRestricted = (currentMemoryUsage > PERFORMANCE_MEMORY_THRESHOLD) ||
                               (averageFPS < PERFORMANCE_FPS_THRESHOLD);
        
        if (performanceRestricted != wasRestricted && performanceConstraintsEnabled) {
            updateConstraints(performanceRestricted ? "performance_restriction" : "performance_recovery");
        }
    }
    
    /**
     * Update content awareness based on current viewport
     */
    private void updateContentAwareness() {
        if (!contentAwareConstraintsEnabled) return;
        
        double centerX = mapRenderer.getViewportCenterX();
        double centerZ = mapRenderer.getViewportCenterZ();
        
        // Check if near spawn (within 1000 blocks)
        boolean wasNearSpawn = nearSpawn;
        nearSpawn = Math.sqrt(centerX * centerX + centerZ * centerZ) < 1000.0;
        
        // Check if near world border
        boolean wasNearWorldBorder = nearWorldBorder;
        double distanceFromCenter = Math.sqrt(centerX * centerX + centerZ * centerZ);
        nearWorldBorder = distanceFromCenter > (worldBorderRadius * 0.8);
        
        if ((nearSpawn != wasNearSpawn) || (nearWorldBorder != wasNearWorldBorder)) {
            updateConstraints("content_awareness");
        }
    }
    
    /**
     * Update zoom constraints based on current conditions
     * @param reason Reason for the update
     * @return Update reason if constraints changed, null otherwise
     */
    private String updateConstraints(String reason) {
        int oldMinZoom = currentMinZoom;
        int oldMaxZoom = currentMaxZoom;
        
        // Start with base constraints
        int newMinZoom = DEFAULT_MIN_ZOOM;
        int newMaxZoom = DEFAULT_MAX_ZOOM;
        
        // Apply performance constraints
        if (performanceConstraintsEnabled && performanceRestricted) {
            newMinZoom = Math.max(newMinZoom, PERFORMANCE_RESTRICTED_MIN_ZOOM);
        }
        
        // Apply content-aware constraints
        if (contentAwareConstraintsEnabled) {
            if (nearSpawn) {
                newMinZoom = Math.max(newMinZoom, SPAWN_AREA_ZOOM_MIN);
            }
            
            if (nearWorldBorder) {
                newMaxZoom = Math.min(newMaxZoom, WORLD_BORDER_ZOOM_MAX);
            }
        }
        
        // Ensure constraints are within absolute limits
        newMinZoom = Math.max(ABSOLUTE_MIN_ZOOM, newMinZoom);
        newMaxZoom = Math.min(ABSOLUTE_MAX_ZOOM, newMaxZoom);
        
        // Ensure min <= max
        if (newMinZoom > newMaxZoom) {
            newMaxZoom = newMinZoom;
        }
        
        // Check if constraints changed
        if (newMinZoom != oldMinZoom || newMaxZoom != oldMaxZoom) {
            if (smoothConstraintTransitions) {
                applyConstraintsSmooth(newMinZoom, newMaxZoom);
            } else {
                applyConstraintsImmediate(newMinZoom, newMaxZoom);
            }
            
            currentMinZoom = newMinZoom;
            currentMaxZoom = newMaxZoom;
            
            return reason;
        }
        
        return null;
    }
    
    /**
     * Apply constraints with smooth transitions
     */
    private void applyConstraintsSmooth(int newMinZoom, int newMaxZoom) {
        // If current zoom is outside new constraints, smoothly move it
        int currentZoom = mapRenderer.getZoomLevel();
        
        if (currentZoom < newMinZoom) {
            // Smoothly zoom out to minimum
            animateToZoomLevel(newMinZoom);
        } else if (currentZoom > newMaxZoom) {
            // Smoothly zoom in to maximum
            animateToZoomLevel(newMaxZoom);
        }
    }
    
    /**
     * Apply constraints immediately
     */
    private void applyConstraintsImmediate(int newMinZoom, int newMaxZoom) {
        int currentZoom = mapRenderer.getZoomLevel();
        
        if (currentZoom < newMinZoom) {
            mapRenderer.setZoomLevel(newMinZoom);
        } else if (currentZoom > newMaxZoom) {
            mapRenderer.setZoomLevel(newMaxZoom);
        }
    }
    
    /**
     * Animate to specific zoom level (would integrate with viewport manager)
     */
    private void animateToZoomLevel(int targetZoom) {
        // This would integrate with MapViewportManager for smooth transitions
        // For now, just set immediately
        mapRenderer.setZoomLevel(targetZoom);
    }
    
    /**
     * Check if zoom level is allowed
     */
    public boolean isZoomLevelAllowed(int zoomLevel) {
        return zoomLevel >= getCurrentMinZoom() && zoomLevel <= getCurrentMaxZoom();
    }
    
    /**
     * Clamp zoom level to current constraints
     */
    public int clampZoomLevel(int zoomLevel) {
        return Math.max(getCurrentMinZoom(), Math.min(getCurrentMaxZoom(), zoomLevel));
    }
    
    /**
     * Get zoom level constraint violation info
     */
    public ZoomViolationInfo checkZoomViolation(int proposedZoomLevel) {
        int minZoom = getCurrentMinZoom();
        int maxZoom = getCurrentMaxZoom();
        
        if (proposedZoomLevel < minZoom) {
            return new ZoomViolationInfo(ZoomViolationType.TOO_ZOOMED_IN, 
                proposedZoomLevel, minZoom, getConstraintReason(true));
        } else if (proposedZoomLevel > maxZoom) {
            return new ZoomViolationInfo(ZoomViolationType.TOO_ZOOMED_OUT,
                proposedZoomLevel, maxZoom, getConstraintReason(false));
        } else {
            return new ZoomViolationInfo(ZoomViolationType.NONE, proposedZoomLevel, proposedZoomLevel, "");
        }
    }
    
    /**
     * Get reason for current constraint limits
     */
    private String getConstraintReason(boolean forMinZoom) {
        if (performanceRestricted && performanceConstraintsEnabled) {
            return "Performance limitations";
        }
        
        if (contentAwareConstraintsEnabled) {
            if (forMinZoom && nearSpawn) {
                return "Near spawn area";
            }
            if (!forMinZoom && nearWorldBorder) {
                return "Near world border";
            }
        }
        
        return "System limits";
    }
    
    // ===============================
    // Configuration Methods
    // ===============================
    
    /**
     * Set base zoom constraints
     */
    public void setBaseConstraints(int minZoom, int maxZoom) {
        minZoom = Math.max(ABSOLUTE_MIN_ZOOM, Math.min(ABSOLUTE_MAX_ZOOM, minZoom));
        maxZoom = Math.max(ABSOLUTE_MIN_ZOOM, Math.min(ABSOLUTE_MAX_ZOOM, maxZoom));
        
        if (minZoom <= maxZoom) {
            // Would update DEFAULT_MIN_ZOOM and DEFAULT_MAX_ZOOM if they weren't final
            updateConstraints("manual_base_change");
        }
    }
    
    /**
     * Enable/disable performance-based constraints
     */
    public void setPerformanceConstraintsEnabled(boolean enabled) {
        if (this.performanceConstraintsEnabled != enabled) {
            this.performanceConstraintsEnabled = enabled;
            updateConstraints(enabled ? "performance_enabled" : "performance_disabled");
        }
    }
    
    /**
     * Enable/disable content-aware constraints
     */
    public void setContentAwareConstraintsEnabled(boolean enabled) {
        if (this.contentAwareConstraintsEnabled != enabled) {
            this.contentAwareConstraintsEnabled = enabled;
            updateConstraints(enabled ? "content_awareness_enabled" : "content_awareness_disabled");
        }
    }
    
    /**
     * Enable/disable adaptive constraints
     */
    public void setAdaptiveConstraintsEnabled(boolean enabled) {
        this.adaptiveConstraintsEnabled = enabled;
    }
    
    /**
     * Enable/disable smooth constraint transitions
     */
    public void setSmoothConstraintTransitions(boolean enabled) {
        this.smoothConstraintTransitions = enabled;
    }
    
    /**
     * Set world border radius for content-aware constraints
     */
    public void setWorldBorderRadius(double radius) {
        this.worldBorderRadius = Math.max(1000.0, radius);
        updateContentAwareness();
    }
    
    /**
     * Set constraint change listener
     */
    public void setConstraintsListener(ZoomConstraintsListener listener) {
        this.listener = listener;
    }
    
    // ===============================
    // Getter Methods
    // ===============================
    
    public int getCurrentMinZoom() { return currentMinZoom; }
    public int getCurrentMaxZoom() { return currentMaxZoom; }
    public boolean isPerformanceRestricted() { return performanceRestricted; }
    public boolean isNearSpawn() { return nearSpawn; }
    public boolean isNearWorldBorder() { return nearWorldBorder; }
    public double getAverageFPS() { return averageFPS; }
    public long getCurrentMemoryUsage() { return currentMemoryUsage; }
    
    /**
     * Get detailed constraint status
     */
    public ZoomConstraintsStatus getStatus() {
        return new ZoomConstraintsStatus(
            currentMinZoom, currentMaxZoom,
            performanceConstraintsEnabled, contentAwareConstraintsEnabled,
            adaptiveConstraintsEnabled, smoothConstraintTransitions,
            performanceRestricted, nearSpawn, nearWorldBorder,
            averageFPS, currentMemoryUsage, worldBorderRadius
        );
    }
    
    // ===============================
    // Helper Classes
    // ===============================
    
    /**
     * Zoom violation types
     */
    public enum ZoomViolationType {
        NONE, TOO_ZOOMED_IN, TOO_ZOOMED_OUT
    }
    
    /**
     * Zoom violation information
     */
    public static class ZoomViolationInfo {
        public final ZoomViolationType type;
        public final int proposedZoom;
        public final int constrainedZoom;
        public final String reason;
        
        ZoomViolationInfo(ZoomViolationType type, int proposedZoom, int constrainedZoom, String reason) {
            this.type = type;
            this.proposedZoom = proposedZoom;
            this.constrainedZoom = constrainedZoom;
            this.reason = reason;
        }
        
        public boolean isViolation() {
            return type != ZoomViolationType.NONE;
        }
        
        @Override
        public String toString() {
            return String.format("ZoomViolation: %s (%d → %d) - %s", type, proposedZoom, constrainedZoom, reason);
        }
    }
    
    /**
     * Current constraints status
     */
    public static class ZoomConstraintsStatus {
        public final int currentMinZoom;
        public final int currentMaxZoom;
        public final boolean performanceConstraintsEnabled;
        public final boolean contentAwareConstraintsEnabled;
        public final boolean adaptiveConstraintsEnabled;
        public final boolean smoothConstraintTransitions;
        public final boolean performanceRestricted;
        public final boolean nearSpawn;
        public final boolean nearWorldBorder;
        public final double averageFPS;
        public final long currentMemoryUsage;
        public final double worldBorderRadius;
        
        ZoomConstraintsStatus(int currentMinZoom, int currentMaxZoom,
                             boolean performanceConstraintsEnabled, boolean contentAwareConstraintsEnabled,
                             boolean adaptiveConstraintsEnabled, boolean smoothConstraintTransitions,
                             boolean performanceRestricted, boolean nearSpawn, boolean nearWorldBorder,
                             double averageFPS, long currentMemoryUsage, double worldBorderRadius) {
            this.currentMinZoom = currentMinZoom;
            this.currentMaxZoom = currentMaxZoom;
            this.performanceConstraintsEnabled = performanceConstraintsEnabled;
            this.contentAwareConstraintsEnabled = contentAwareConstraintsEnabled;
            this.adaptiveConstraintsEnabled = adaptiveConstraintsEnabled;
            this.smoothConstraintTransitions = smoothConstraintTransitions;
            this.performanceRestricted = performanceRestricted;
            this.nearSpawn = nearSpawn;
            this.nearWorldBorder = nearWorldBorder;
            this.averageFPS = averageFPS;
            this.currentMemoryUsage = currentMemoryUsage;
            this.worldBorderRadius = worldBorderRadius;
        }
        
        @Override
        public String toString() {
            return String.format("ZoomConstraints: [%d-%d] perf=%s content=%s adaptive=%s smooth=%s restricted=%s spawn=%s border=%s fps=%.1f mem=%dMB",
                currentMinZoom, currentMaxZoom,
                performanceConstraintsEnabled, contentAwareConstraintsEnabled,
                adaptiveConstraintsEnabled, smoothConstraintTransitions,
                performanceRestricted, nearSpawn, nearWorldBorder,
                averageFPS, currentMemoryUsage / (1024 * 1024));
        }
    }
}
