package cubeium.cubeium.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

/**
 * Widget for displaying current coordinates on the map
 */
public class CoordinateDisplayWidget {
    private final TextRenderer textRenderer;
    private int x, y;
    private int width, height;
    private int currentX = 0;
    private int currentY = 0;
    private int currentZ = 0;
    private String biome = "Unknown";
    private boolean visible = true;
    
    private static final int PADDING = 8;
    private static final int LINE_HEIGHT = 12;
    private static final int BACKGROUND_COLOR = 0x80000000; // Semi-transparent black
    private static final int TEXT_COLOR = 0xFFFFFF; // White
    private static final int BORDER_COLOR = 0xFF666666; // Gray border
    
    public CoordinateDisplayWidget(int x, int y, TextRenderer textRenderer) {
        this.x = x;
        this.y = y;
        this.textRenderer = textRenderer;
        calculateDimensions();
    }
    
    private void calculateDimensions() {
        // Calculate width based on longest possible coordinate string
        String longestText = "Biome: Very Long Biome Name Here";
        int maxTextWidth = Math.max(
            textRenderer.getWidth("X: -2147483648"),
            Math.max(
                textRenderer.getWidth("Y: -2147483648"), 
                Math.max(
                    textRenderer.getWidth("Z: -2147483648"),
                    textRenderer.getWidth(longestText)
                )
            )
        );
        
        this.width = maxTextWidth + (PADDING * 2);
        this.height = (LINE_HEIGHT * 4) + (PADDING * 2); // 4 lines of text
    }
    
    public void updateCoordinates(int x, int y, int z) {
        this.currentX = x;
        this.currentY = y;
        this.currentZ = z;
    }
    
    public void updateBiome(String biome) {
        this.biome = biome != null ? biome : "Unknown";
    }
    
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        
        // Draw background
        context.fill(x, y, x + width, y + height, BACKGROUND_COLOR);
        
        // Draw border
        context.drawBorder(x, y, width, height, BORDER_COLOR);
        
        // Draw coordinate text
        int textY = y + PADDING;
        context.drawText(textRenderer, 
            Text.literal("X: " + currentX), 
            x + PADDING, textY, TEXT_COLOR, false);
        
        textY += LINE_HEIGHT;
        context.drawText(textRenderer, 
            Text.literal("Y: " + currentY), 
            x + PADDING, textY, TEXT_COLOR, false);
        
        textY += LINE_HEIGHT;
        context.drawText(textRenderer, 
            Text.literal("Z: " + currentZ), 
            x + PADDING, textY, TEXT_COLOR, false);
        
        textY += LINE_HEIGHT;
        String biomeText = biome.length() > 20 ? biome.substring(0, 17) + "..." : biome;
        context.drawText(textRenderer, 
            Text.literal("Biome: " + biomeText), 
            x + PADDING, textY, TEXT_COLOR, false);
    }
    
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && 
               mouseY >= y && mouseY < y + height;
    }
    
    public int getX() {
        return x;
    }
    
    public int getY() {
        return y;
    }
    
    public int getWidth() {
        return width;
    }
    
    public int getHeight() {
        return height;
    }
}
