package cubeium.cubeium.ui;

import cubeium.cubeium.rendering.MapRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Widget that displays current zoom level with visual indicators and constraints.
 * Shows zoom level, scale ratio, coverage area, and provides visual feedback.
 */
public class ZoomIndicatorWidget {
    
    // Widget positioning and sizing
    private static final int WIDGET_WIDTH = 180;
    private static final int WIDGET_HEIGHT = 85;
    private static final int MARGIN = 10;
    private static final int PADDING = 8;
    private static final int LINE_SPACING = 2;
    
    // Colors (ARGB format)
    private static final int BACKGROUND_COLOR = 0x80000000; // Semi-transparent black
    private static final int BORDER_COLOR = 0xFF404040; // Dark gray
    private static final int TEXT_COLOR = 0xFFFFFFFF; // White
    private static final int ACCENT_COLOR = 0xFF00AAFF; // Blue
    private static final int WARNING_COLOR = 0xFFFFAA00; // Orange
    private static final int DANGER_COLOR = 0xFFFF4444; // Red
    
    // Zoom indicator bar
    private static final int BAR_WIDTH = 140;
    private static final int BAR_HEIGHT = 6;
    private static final int BAR_BACKGROUND_COLOR = 0xFF333333;
    private static final int BAR_FILL_COLOR = 0xFF00AAFF;
    private static final int BAR_LIMIT_COLOR = 0xFFFFFFFF;
    
    // Animation timing
    private static final int FADE_DURATION_MS = 3000; // How long to show after zoom change
    private static final int FADE_OUT_MS = 500; // Fade out duration
    
    private final MinecraftClient client;
    private final MapRenderer mapRenderer;
    
    // Widget state
    private long lastZoomChangeTime = 0;
    private int lastZoomLevel = 0;
    private boolean isVisible = true;
    private boolean alwaysVisible = false;
    
    // Positioning
    private WidgetPosition position = WidgetPosition.TOP_RIGHT;
    private int offsetX = 0;
    private int offsetY = 0;
    
    /**
     * Widget positioning options
     */
    public enum WidgetPosition {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CUSTOM
    }
    
    /**
     * Create a zoom indicator widget
     * @param client Minecraft client instance
     * @param mapRenderer Map renderer to monitor
     */
    public ZoomIndicatorWidget(MinecraftClient client, MapRenderer mapRenderer) {
        this.client = client;
        this.mapRenderer = mapRenderer;
        this.lastZoomLevel = mapRenderer.getZoomLevel();
    }
    
    /**
     * Update widget state
     */
    public void tick() {
        int currentZoom = mapRenderer.getZoomLevel();
        
        // Check if zoom level changed
        if (currentZoom != lastZoomLevel) {
            lastZoomLevel = currentZoom;
            lastZoomChangeTime = System.currentTimeMillis();
            
            // Make visible when zoom changes (unless always visible)
            if (!alwaysVisible) {
                isVisible = true;
            }
        }
        
        // Handle auto-hide after zoom change
        if (!alwaysVisible && isVisible) {
            long timeSinceChange = System.currentTimeMillis() - lastZoomChangeTime;
            if (timeSinceChange > FADE_DURATION_MS) {
                isVisible = false;
            }
        }
    }
    
    /**
     * Render the widget
     * @param context Drawing context
     * @param screenWidth Screen width
     * @param screenHeight Screen height
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     */
    public void render(DrawContext context, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!isVisible && !alwaysVisible) {
            return;
        }
        
        // Calculate widget position
        int[] pos = calculatePosition(screenWidth, screenHeight);
        int widgetX = pos[0];
        int widgetY = pos[1];
        
        // Calculate opacity based on fade state
        float opacity = 1.0f;
        if (!alwaysVisible && isVisible) {
            long timeSinceChange = System.currentTimeMillis() - lastZoomChangeTime;
            if (timeSinceChange > FADE_DURATION_MS - FADE_OUT_MS) {
                float fadeProgress = (float)(timeSinceChange - (FADE_DURATION_MS - FADE_OUT_MS)) / FADE_OUT_MS;
                opacity = Math.max(0.0f, 1.0f - fadeProgress);
            }
        }
        
        // Apply opacity to colors
        int bgColor = applyOpacity(BACKGROUND_COLOR, opacity);
        int borderColor = applyOpacity(BORDER_COLOR, opacity);
        int textColor = applyOpacity(TEXT_COLOR, opacity);
        
        // Draw background
        context.fill(widgetX, widgetY, widgetX + WIDGET_WIDTH, widgetY + WIDGET_HEIGHT, bgColor);
        
        // Draw border
        drawBorder(context, widgetX, widgetY, WIDGET_WIDTH, WIDGET_HEIGHT, borderColor);
        
        // Render content
        renderContent(context, widgetX + PADDING, widgetY + PADDING, textColor, opacity);
    }
    
    /**
     * Render widget content
     */
    private void renderContent(DrawContext context, int x, int y, int textColor, float opacity) {
        TextRenderer textRenderer = client.textRenderer;
        int currentZoom = mapRenderer.getZoomLevel();
        int minZoom = mapRenderer.getMinZoomLevel();
        int maxZoom = mapRenderer.getMaxZoomLevel();
        
        int lineHeight = textRenderer.fontHeight + LINE_SPACING;
        int currentY = y;
        
        // Zoom level text
        String zoomText = formatZoomLevel(currentZoom);
        Text zoomLevelText = Text.literal("Zoom: ").formatted(Formatting.GRAY)
            .append(Text.literal(zoomText).formatted(getZoomLevelFormatting(currentZoom, minZoom, maxZoom)));
        
        context.drawText(textRenderer, zoomLevelText, x, currentY, textColor, false);
        currentY += lineHeight;
        
        // Scale ratio
        double scale = Math.pow(2.0, -currentZoom);
        String scaleText = formatScale(scale);
        Text scaleRatioText = Text.literal("Scale: ").formatted(Formatting.GRAY)
            .append(Text.literal(scaleText).formatted(Formatting.WHITE));
        
        context.drawText(textRenderer, scaleRatioText, x, currentY, textColor, false);
        currentY += lineHeight;
        
        // Coverage area
        String coverageText = formatCoverage(currentZoom);
        Text coverageAreaText = Text.literal("View: ").formatted(Formatting.GRAY)
            .append(Text.literal(coverageText).formatted(Formatting.AQUA));
        
        context.drawText(textRenderer, coverageAreaText, x, currentY, textColor, false);
        currentY += lineHeight + 2;
        
        // Zoom indicator bar
        drawZoomBar(context, x, currentY, currentZoom, minZoom, maxZoom, opacity);
    }
    
    /**
     * Draw zoom level indicator bar
     */
    private void drawZoomBar(DrawContext context, int x, int y, int currentZoom, int minZoom, int maxZoom, float opacity) {
        // Background bar
        int bgColor = applyOpacity(BAR_BACKGROUND_COLOR, opacity);
        context.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, bgColor);
        
        // Calculate position along bar
        double progress = (double)(currentZoom - minZoom) / (maxZoom - minZoom);
        int fillWidth = (int)(BAR_WIDTH * progress);
        
        // Fill bar
        int fillColor = applyOpacity(BAR_FILL_COLOR, opacity);
        if (fillWidth > 0) {
            context.fill(x, y, x + fillWidth, y + BAR_HEIGHT, fillColor);
        }
        
        // Limit markers
        int limitColor = applyOpacity(BAR_LIMIT_COLOR, opacity);
        
        // Min zoom marker (left)
        context.fill(x, y - 1, x + 1, y + BAR_HEIGHT + 1, limitColor);
        
        // Max zoom marker (right)
        context.fill(x + BAR_WIDTH - 1, y - 1, x + BAR_WIDTH, y + BAR_HEIGHT + 1, limitColor);
        
        // Default zoom marker (0 level)
        if (minZoom <= 0 && maxZoom >= 0) {
            double defaultProgress = (double)(0 - minZoom) / (maxZoom - minZoom);
            int defaultX = x + (int)(BAR_WIDTH * defaultProgress);
            context.fill(defaultX - 1, y - 2, defaultX + 1, y + BAR_HEIGHT + 2, limitColor);
        }
    }
    
    /**
     * Format zoom level for display
     */
    private String formatZoomLevel(int zoomLevel) {
        if (zoomLevel == 0) {
            return "1:1";
        } else if (zoomLevel > 0) {
            return "1:" + (1 << zoomLevel);
        } else {
            return (1 << -zoomLevel) + ":1";
        }
    }
    
    /**
     * Format scale for display
     */
    private String formatScale(double scale) {
        if (scale >= 1.0) {
            return String.format("%.0fx", scale);
        } else if (scale >= 0.1) {
            return String.format("%.1fx", scale);
        } else {
            return String.format("%.2fx", scale);
        }
    }
    
    /**
     * Format coverage area for display
     */
    private String formatCoverage(int zoomLevel) {
        // Approximate view coverage based on typical screen size
        int baseBlocks = 1000; // Base coverage at zoom 0
        double coverage = baseBlocks * Math.pow(2.0, zoomLevel);
        
        if (coverage < 1000) {
            return String.format("%.0f blocks", coverage);
        } else if (coverage < 1000000) {
            return String.format("%.1f km", coverage / 1000.0);
        } else {
            return String.format("%.0f km", coverage / 1000.0);
        }
    }
    
    /**
     * Get formatting for zoom level based on constraints
     */
    private Formatting getZoomLevelFormatting(int currentZoom, int minZoom, int maxZoom) {
        if (currentZoom <= minZoom || currentZoom >= maxZoom) {
            return Formatting.RED; // At limits
        } else if (currentZoom <= minZoom + 1 || currentZoom >= maxZoom - 1) {
            return Formatting.YELLOW; // Near limits
        } else {
            return Formatting.WHITE; // Normal range
        }
    }
    
    /**
     * Calculate widget position based on settings
     */
    private int[] calculatePosition(int screenWidth, int screenHeight) {
        int x, y;
        
        switch (position) {
            case TOP_LEFT:
                x = MARGIN;
                y = MARGIN;
                break;
            case TOP_RIGHT:
                x = screenWidth - WIDGET_WIDTH - MARGIN;
                y = MARGIN;
                break;
            case BOTTOM_LEFT:
                x = MARGIN;
                y = screenHeight - WIDGET_HEIGHT - MARGIN;
                break;
            case BOTTOM_RIGHT:
                x = screenWidth - WIDGET_WIDTH - MARGIN;
                y = screenHeight - WIDGET_HEIGHT - MARGIN;
                break;
            case CUSTOM:
                x = offsetX;
                y = offsetY;
                break;
            default:
                x = screenWidth - WIDGET_WIDTH - MARGIN;
                y = MARGIN;
                break;
        }
        
        return new int[]{x, y};
    }
    
    /**
     * Draw border around rectangle
     */
    private void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        // Top
        context.fill(x, y, x + width, y + 1, color);
        // Bottom
        context.fill(x, y + height - 1, x + width, y + height, color);
        // Left
        context.fill(x, y, x + 1, y + height, color);
        // Right
        context.fill(x + width - 1, y, x + width, y + height, color);
    }
    
    /**
     * Apply opacity to color
     */
    private int applyOpacity(int color, float opacity) {
        int alpha = (int)((color >>> 24) * opacity);
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
    
    // ===============================
    // Configuration Methods
    // ===============================
    
    /**
     * Set widget position
     */
    public void setPosition(WidgetPosition position) {
        this.position = position;
    }
    
    /**
     * Set custom position offset
     */
    public void setCustomPosition(int x, int y) {
        this.position = WidgetPosition.CUSTOM;
        this.offsetX = x;
        this.offsetY = y;
    }
    
    /**
     * Set always visible (no auto-hide)
     */
    public void setAlwaysVisible(boolean alwaysVisible) {
        this.alwaysVisible = alwaysVisible;
        if (alwaysVisible) {
            this.isVisible = true;
        }
    }
    
    /**
     * Force show widget
     */
    public void show() {
        this.isVisible = true;
        this.lastZoomChangeTime = System.currentTimeMillis();
    }
    
    /**
     * Force hide widget
     */
    public void hide() {
        if (!alwaysVisible) {
            this.isVisible = false;
        }
    }
    
    /**
     * Toggle widget visibility
     */
    public void toggle() {
        if (isVisible || alwaysVisible) {
            hide();
        } else {
            show();
        }
    }
    
    /**
     * Check if widget is currently visible
     */
    public boolean isVisible() {
        return isVisible || alwaysVisible;
    }
    
    /**
     * Get current zoom level constraints info
     */
    public ZoomConstraintsInfo getConstraintsInfo() {
        int currentZoom = mapRenderer.getZoomLevel();
        int minZoom = mapRenderer.getMinZoomLevel();
        int maxZoom = mapRenderer.getMaxZoomLevel();
        
        return new ZoomConstraintsInfo(
            currentZoom, minZoom, maxZoom,
            currentZoom <= minZoom, currentZoom >= maxZoom,
            currentZoom <= minZoom + 1, currentZoom >= maxZoom - 1
        );
    }
    
    /**
     * Zoom constraints information
     */
    public static class ZoomConstraintsInfo {
        public final int currentZoom;
        public final int minZoom;
        public final int maxZoom;
        public final boolean atMinLimit;
        public final boolean atMaxLimit;
        public final boolean nearMinLimit;
        public final boolean nearMaxLimit;
        
        ZoomConstraintsInfo(int currentZoom, int minZoom, int maxZoom,
                           boolean atMinLimit, boolean atMaxLimit,
                           boolean nearMinLimit, boolean nearMaxLimit) {
            this.currentZoom = currentZoom;
            this.minZoom = minZoom;
            this.maxZoom = maxZoom;
            this.atMinLimit = atMinLimit;
            this.atMaxLimit = atMaxLimit;
            this.nearMinLimit = nearMinLimit;
            this.nearMaxLimit = nearMaxLimit;
        }
        
        @Override
        public String toString() {
            return String.format("ZoomConstraints: %d [%d-%d] limits=%s,%s near=%s,%s",
                currentZoom, minZoom, maxZoom,
                atMinLimit, atMaxLimit, nearMinLimit, nearMaxLimit);
        }
    }
}
