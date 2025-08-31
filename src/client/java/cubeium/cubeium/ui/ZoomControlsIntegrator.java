package cubeium.cubeium.ui;

import cubeium.cubeium.rendering.MapRenderer;
import cubeium.cubeium.rendering.viewport.MapViewportManager;
import net.minecraft.client.MinecraftClient;

/**
 * Helper class to integrate zoom controls and indicators with the seed map screen.
 * Provides a unified interface for managing zoom-related UI components.
 */
public class ZoomControlsIntegrator {
    
    private final MinecraftClient client;
    private final MapRenderer mapRenderer;
    private final MapViewportManager viewportManager;
    
    // UI Components
    private ZoomIndicatorWidget zoomIndicator;
    
    // Configuration
    private boolean zoomIndicatorEnabled = true;
    private ZoomIndicatorWidget.WidgetPosition indicatorPosition = ZoomIndicatorWidget.WidgetPosition.TOP_RIGHT;
    private boolean autoHideIndicator = true;
    
    /**
     * Create zoom controls integrator
     * @param client Minecraft client
     * @param mapRenderer Map renderer instance
     * @param viewportManager Viewport manager instance
     */
    public ZoomControlsIntegrator(MinecraftClient client, MapRenderer mapRenderer, MapViewportManager viewportManager) {
        this.client = client;
        this.mapRenderer = mapRenderer;
        this.viewportManager = viewportManager;
        
        initializeComponents();
    }
    
    /**
     * Initialize UI components
     */
    private void initializeComponents() {
        if (zoomIndicatorEnabled) {
            zoomIndicator = new ZoomIndicatorWidget(client, mapRenderer);
            zoomIndicator.setPosition(indicatorPosition);
            zoomIndicator.setAlwaysVisible(!autoHideIndicator);
        }
    }
    
    /**
     * Update all zoom controls (call from screen tick)
     */
    public void tick() {
        if (zoomIndicator != null) {
            zoomIndicator.tick();
        }
        
        // Update viewport manager constraints
        viewportManager.tick();
    }
    
    /**
     * Render all zoom UI components
     * @param context Draw context
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param delta Frame delta time
     */
    public void render(net.minecraft.client.gui.DrawContext context, int screenWidth, int screenHeight, 
                      int mouseX, int mouseY, float delta) {
        if (zoomIndicator != null && zoomIndicatorEnabled) {
            zoomIndicator.render(context, screenWidth, screenHeight, mouseX, mouseY);
        }
    }
    
    /**
     * Handle key press events
     * @param keyCode Key code
     * @param scanCode Scan code
     * @param modifiers Modifier keys
     * @return true if handled
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle zoom indicator toggle (could be configurable)
        if (keyCode == 292) { // F11 key - could make this configurable
            toggleZoomIndicator();
            return true;
        }
        
        return false;
    }
    
    /**
     * Handle mouse scroll events for zoom indication
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param scrollX Horizontal scroll
     * @param scrollY Vertical scroll
     * @return true if handled
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Show zoom indicator when zooming
        if (zoomIndicator != null && scrollY != 0) {
            zoomIndicator.show();
        }
        return false; // Don't consume the event
    }
    
    /**
     * Toggle zoom indicator visibility
     */
    public void toggleZoomIndicator() {
        if (zoomIndicator != null) {
            zoomIndicator.toggle();
        }
    }
    
    /**
     * Show zoom indicator
     */
    public void showZoomIndicator() {
        if (zoomIndicator != null) {
            zoomIndicator.show();
        }
    }
    
    /**
     * Hide zoom indicator
     */
    public void hideZoomIndicator() {
        if (zoomIndicator != null) {
            zoomIndicator.hide();
        }
    }
    
    /**
     * Get current zoom level information
     */
    public String getCurrentZoomInfo() {
        int zoom = mapRenderer.getZoomLevel();
        int minZoom = viewportManager.getZoomConstraintsManager().getCurrentMinZoom();
        int maxZoom = viewportManager.getZoomConstraintsManager().getCurrentMaxZoom();
        
        return String.format("Zoom: %d [%d to %d]", zoom, minZoom, maxZoom);
    }
    
    /**
     * Get zoom constraints status
     */
    public String getConstraintsStatus() {
        var status = viewportManager.getZoomConstraintsStatus();
        return status.toString();
    }
    
    // ===============================
    // Configuration Methods
    // ===============================
    
    /**
     * Enable/disable zoom indicator
     */
    public void setZoomIndicatorEnabled(boolean enabled) {
        if (this.zoomIndicatorEnabled != enabled) {
            this.zoomIndicatorEnabled = enabled;
            
            if (enabled && zoomIndicator == null) {
                zoomIndicator = new ZoomIndicatorWidget(client, mapRenderer);
                zoomIndicator.setPosition(indicatorPosition);
                zoomIndicator.setAlwaysVisible(!autoHideIndicator);
            } else if (!enabled) {
                zoomIndicator = null;
            }
        }
    }
    
    /**
     * Set zoom indicator position
     */
    public void setIndicatorPosition(ZoomIndicatorWidget.WidgetPosition position) {
        this.indicatorPosition = position;
        if (zoomIndicator != null) {
            zoomIndicator.setPosition(position);
        }
    }
    
    /**
     * Set zoom indicator auto-hide behavior
     */
    public void setAutoHideIndicator(boolean autoHide) {
        this.autoHideIndicator = autoHide;
        if (zoomIndicator != null) {
            zoomIndicator.setAlwaysVisible(!autoHide);
        }
    }
    
    /**
     * Set custom indicator position
     */
    public void setCustomIndicatorPosition(int x, int y) {
        if (zoomIndicator != null) {
            zoomIndicator.setCustomPosition(x, y);
        }
    }
    
    /**
     * Enable/disable performance constraints
     */
    public void setPerformanceConstraintsEnabled(boolean enabled) {
        viewportManager.getZoomConstraintsManager().setPerformanceConstraintsEnabled(enabled);
    }
    
    /**
     * Enable/disable content-aware constraints
     */
    public void setContentAwareConstraintsEnabled(boolean enabled) {
        viewportManager.getZoomConstraintsManager().setContentAwareConstraintsEnabled(enabled);
    }
    
    /**
     * Enable/disable smooth constraint transitions
     */
    public void setSmoothConstraintTransitions(boolean enabled) {
        viewportManager.getZoomConstraintsManager().setSmoothConstraintTransitions(enabled);
    }
    
    // ===============================
    // Status Methods
    // ===============================
    
    public boolean isZoomIndicatorEnabled() { return zoomIndicatorEnabled; }
    public boolean isZoomIndicatorVisible() { return zoomIndicator != null && zoomIndicator.isVisible(); }
    public ZoomIndicatorWidget.WidgetPosition getIndicatorPosition() { return indicatorPosition; }
    public boolean isAutoHideIndicator() { return autoHideIndicator; }
    
    /**
     * Get zoom indicator widget instance (for advanced configuration)
     */
    public ZoomIndicatorWidget getZoomIndicator() {
        return zoomIndicator;
    }
    
    /**
     * Get detailed zoom state information
     */
    public ZoomStateInfo getZoomStateInfo() {
        var constraintsInfo = zoomIndicator != null ? zoomIndicator.getConstraintsInfo() : null;
        var constraintsStatus = viewportManager.getZoomConstraintsStatus();
        
        return new ZoomStateInfo(
            mapRenderer.getZoomLevel(),
            constraintsStatus.currentMinZoom,
            constraintsStatus.currentMaxZoom,
            constraintsInfo != null && (constraintsInfo.atMinLimit || constraintsInfo.atMaxLimit),
            constraintsStatus.performanceRestricted,
            constraintsStatus.nearSpawn,
            constraintsStatus.nearWorldBorder,
            zoomIndicatorEnabled && (zoomIndicator != null && zoomIndicator.isVisible())
        );
    }
    
    /**
     * Zoom state information
     */
    public static class ZoomStateInfo {
        public final int currentZoom;
        public final int minZoom;
        public final int maxZoom;
        public final boolean atLimit;
        public final boolean performanceRestricted;
        public final boolean nearSpawn;
        public final boolean nearWorldBorder;
        public final boolean indicatorVisible;
        
        ZoomStateInfo(int currentZoom, int minZoom, int maxZoom, boolean atLimit,
                     boolean performanceRestricted, boolean nearSpawn, boolean nearWorldBorder,
                     boolean indicatorVisible) {
            this.currentZoom = currentZoom;
            this.minZoom = minZoom;
            this.maxZoom = maxZoom;
            this.atLimit = atLimit;
            this.performanceRestricted = performanceRestricted;
            this.nearSpawn = nearSpawn;
            this.nearWorldBorder = nearWorldBorder;
            this.indicatorVisible = indicatorVisible;
        }
        
        @Override
        public String toString() {
            return String.format("ZoomState: %d [%d-%d] limit=%s perf=%s spawn=%s border=%s indicator=%s",
                currentZoom, minZoom, maxZoom, atLimit, performanceRestricted, 
                nearSpawn, nearWorldBorder, indicatorVisible);
        }
    }
}
