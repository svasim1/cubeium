package cubeium.cubeium.ui;

import cubeium.cubeium.rendering.MapRenderer;
import cubeium.cubeium.navigation.CoordinateSystemConverter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Widget that displays coordinate information with conversion capabilities.
 * Supports multiple positioning modes and coordinate system formats.
 */
public class CoordinateDisplayWidget {
    
    // Widget positioning options
    public enum WidgetPosition {
        TOP_LEFT("Top Left"),
        TOP_RIGHT("Top Right"),
        BOTTOM_LEFT("Bottom Left"),
        BOTTOM_RIGHT("Bottom Right"),
        FOLLOW_MOUSE("Follow Mouse"),
        CENTER_BOTTOM("Center Bottom"),
        HIDDEN("Hidden");
        
        public final String displayName;
        
        WidgetPosition(String displayName) {
            this.displayName = displayName;
        }
    }
    
    // Widget sizing and appearance
    private static final int WIDGET_MARGIN = 10;
    private static final int WIDGET_PADDING = 8;
    private static final int LINE_SPACING = 2;
    private static final int MOUSE_OFFSET_X = 15;
    private static final int MOUSE_OFFSET_Y = -25;
    
    // Colors (ARGB format)
    private static final int BACKGROUND_COLOR = 0xCC000000; // Semi-transparent black
    private static final int BORDER_COLOR = 0xFF404040; // Dark gray
    private static final int TEXT_COLOR = 0xFFFFFFFF; // White
    private static final int ACCENT_COLOR = 0xFF00AAFF; // Blue
    private static final int HIGHLIGHT_COLOR = 0xFFFFFF00; // Yellow
    
    private final MinecraftClient client;
    private final MapRenderer mapRenderer;
    private final TextRenderer textRenderer;
    private final CoordinateSystemConverter coordinateConverter;
    
    // Widget state
    private WidgetPosition position = WidgetPosition.BOTTOM_LEFT;
    private boolean isExpanded = false;
    private boolean showGrid = true;
    private boolean showScale = true;
    private boolean legacyMode = false;
    
    // Tracking coordinates
    private double lastWorldX = 0;
    private double lastWorldZ = 0;
    private int lastMouseX = 0;
    private int lastMouseY = 0;
    
    public CoordinateDisplayWidget(MinecraftClient client, MapRenderer mapRenderer) {
        this.client = client;
        this.mapRenderer = mapRenderer;
        this.textRenderer = client.textRenderer;
        this.coordinateConverter = new CoordinateSystemConverter(mapRenderer);
    }
    
    /**
     * Set the widget position
     */
    public void setPosition(WidgetPosition position) {
        this.position = position;
    }
    
    /**
     * Get current widget position
     */
    public WidgetPosition getPosition() {
        return position;
    }
    
    /**
     * Toggle expanded view
     */
    public void toggleExpanded() {
        this.isExpanded = !this.isExpanded;
    }
    
    /**
     * Set expanded state
     */
    public void setExpanded(boolean expanded) {
        this.isExpanded = expanded;
    }
    
    /**
     * Toggle grid display
     */
    public void toggleGridDisplay() {
        this.showGrid = !this.showGrid;
    }
    
    /**
     * Toggle scale display
     */
    public void toggleScaleDisplay() {
        this.showScale = !this.showScale;
    }
    
    /**
     * Enable legacy coordinate mode
     */
    public void setLegacyMode(boolean legacyMode) {
        this.legacyMode = legacyMode;
    }
    
    /**
     * Update coordinate tracking
     */
    public void update(double worldX, double worldZ, int mouseX, int mouseY) {
        this.lastWorldX = worldX;
        this.lastWorldZ = worldZ;
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;
    }
    
    /**
     * Handle keyboard input
     */
    public boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
        // Handle coordinate widget shortcuts here if needed
        return false;
    }
    
    /**
     * Render the coordinate widget
     */
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (position == WidgetPosition.HIDDEN) {
            return;
        }
        
        // Get coordinate information
        CoordinateSystemConverter.CoordinateInfo coordInfo = 
            coordinateConverter.convertCoordinates(lastWorldX, lastWorldZ);
        
        // Prepare text lines
        String[] lines = prepareTextLines(coordInfo);
        if (lines.length == 0) {
            return;
        }
        
        // Calculate widget dimensions
        int maxTextWidth = 0;
        for (String line : lines) {
            maxTextWidth = Math.max(maxTextWidth, textRenderer.getWidth(line));
        }
        
        int widgetWidth = maxTextWidth + WIDGET_PADDING * 2;
        int widgetHeight = (lines.length * (textRenderer.fontHeight + LINE_SPACING)) 
                          - LINE_SPACING + WIDGET_PADDING * 2;
        
        // Calculate widget position
        int[] widgetPos = calculateWidgetPosition(widgetWidth, widgetHeight, mouseX, mouseY);
        int widgetX = widgetPos[0];
        int widgetY = widgetPos[1];
        
        // Render widget background
        context.fill(widgetX, widgetY, widgetX + widgetWidth, widgetY + widgetHeight, BACKGROUND_COLOR);
        context.drawBorder(widgetX, widgetY, widgetWidth, widgetHeight, BORDER_COLOR);
        
        // Render text lines
        int textY = widgetY + WIDGET_PADDING;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int textColor = getLineColor(i, lines.length);
            context.drawText(textRenderer, line, widgetX + WIDGET_PADDING, textY, textColor, false);
            textY += textRenderer.fontHeight + LINE_SPACING;
        }
    }
    
    /**
     * Prepare text lines for display
     */
    private String[] prepareTextLines(CoordinateSystemConverter.CoordinateInfo coordInfo) {
        if (legacyMode) {
            return prepareLegacyLines(coordInfo);
        }
        
        if (isExpanded) {
            return prepareExpandedLines(coordInfo);
        } else {
            return prepareCompactLines(coordInfo);
        }
    }
    
    /**
     * Prepare legacy format lines
     */
    private String[] prepareLegacyLines(CoordinateSystemConverter.CoordinateInfo coordInfo) {
        return new String[] {
            String.format("X: %.1f, Z: %.1f", lastWorldX, lastWorldZ),
            String.format("Chunk: [%d, %d]", (int)Math.floor(lastWorldX / 16), (int)Math.floor(lastWorldZ / 16))
        };
    }
    
    /**
     * Prepare compact format lines
     */
    private String[] prepareCompactLines(CoordinateSystemConverter.CoordinateInfo coordInfo) {
        String primary = coordinateConverter.formatCoordinates(coordInfo);
        String secondary = String.format("World: %.1f, %.1f", coordInfo.worldX, coordInfo.worldZ);
        return new String[] { primary, secondary };
    }
    
    /**
     * Prepare expanded format lines
     */
    private String[] prepareExpandedLines(CoordinateSystemConverter.CoordinateInfo coordInfo) {
        String[] lines = new String[4 + (showGrid ? 1 : 0) + (showScale ? 1 : 0)];
        int lineIndex = 0;
        
        lines[lineIndex++] = "§l§9Coordinates";
        lines[lineIndex++] = "§f" + coordinateConverter.formatCoordinates(coordInfo);
        lines[lineIndex++] = "§7World: " + String.format("%.1f, %.1f", coordInfo.worldX, coordInfo.worldZ);
        lines[lineIndex++] = "§7" + coordInfo.scaleName + ": " + String.format("%.1f, %.1f", coordInfo.convertedX, coordInfo.convertedZ);
        
        if (showGrid) {
            CoordinateSystemConverter.GridInfo gridInfo = coordinateConverter.getAppropriateGrid();
            lines[lineIndex++] = "§6Grid: " + gridInfo.toString();
        }
        
        if (showScale) {
            lines[lineIndex++] = "§8Scale: " + coordinateConverter.getCurrentScale().displayName;
        }
        
        return lines;
    }
    
    /**
     * Calculate widget position based on positioning mode
     */
    private int[] calculateWidgetPosition(int widgetWidth, int widgetHeight, int mouseX, int mouseY) {
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        switch (position) {
            case TOP_LEFT:
                return new int[] { WIDGET_MARGIN, WIDGET_MARGIN };
                
            case TOP_RIGHT:
                return new int[] { screenWidth - widgetWidth - WIDGET_MARGIN, WIDGET_MARGIN };
                
            case BOTTOM_LEFT:
                return new int[] { WIDGET_MARGIN, screenHeight - widgetHeight - WIDGET_MARGIN };
                
            case BOTTOM_RIGHT:
                return new int[] { screenWidth - widgetWidth - WIDGET_MARGIN, 
                                  screenHeight - widgetHeight - WIDGET_MARGIN };
                
            case CENTER_BOTTOM:
                return new int[] { (screenWidth - widgetWidth) / 2, 
                                  screenHeight - widgetHeight - WIDGET_MARGIN };
                
            case FOLLOW_MOUSE:
                int mouseWidgetX = mouseX + MOUSE_OFFSET_X;
                int mouseWidgetY = mouseY + MOUSE_OFFSET_Y;
                
                // Keep widget on screen
                mouseWidgetX = Math.max(0, Math.min(mouseWidgetX, screenWidth - widgetWidth));
                mouseWidgetY = Math.max(0, Math.min(mouseWidgetY, screenHeight - widgetHeight));
                
                return new int[] { mouseWidgetX, mouseWidgetY };
                
            default:
                return new int[] { WIDGET_MARGIN, WIDGET_MARGIN };
        }
    }
    
    /**
     * Get color for text line based on index
     */
    private int getLineColor(int lineIndex, int totalLines) {
        if (lineIndex == 0) {
            return ACCENT_COLOR; // Header line
        } else if (lineIndex == totalLines - 1 && showScale) {
            return 0xFF888888; // Scale info line
        } else {
            return TEXT_COLOR; // Regular text
        }
    }
    
    /**
     * Check if mouse is over widget (for interaction)
     */
    public boolean isMouseOver(int mouseX, int mouseY) {
        if (position == WidgetPosition.HIDDEN || position == WidgetPosition.FOLLOW_MOUSE) {
            return false;
        }
        
        // This is a simplified check - would need actual widget bounds
        return false;
    }
    
    /**
     * Handle mouse click on widget
     */
    public boolean handleMouseClick(int mouseX, int mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        
        if (button == 0) { // Left click - toggle expanded
            toggleExpanded();
            return true;
        } else if (button == 1) { // Right click - cycle position
            WidgetPosition[] positions = WidgetPosition.values();
            int currentIndex = position.ordinal();
            int nextIndex = (currentIndex + 1) % positions.length;
            setPosition(positions[nextIndex]);
            return true;
        }
        
        return false;
    }
}
