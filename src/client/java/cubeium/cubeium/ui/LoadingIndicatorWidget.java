package cubeium.cubeium.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Loading indicator widget with animated spinner and progress bar
 */
public class LoadingIndicatorWidget {
    private final TextRenderer textRenderer;
    private int x, y, width, height;
    private boolean visible = false;
    private String loadingText = "Loading...";
    private float progress = 0.0f; // 0.0 to 1.0
    private boolean indeterminate = true;
    private long startTime;
    
    // Visual constants
    private static final int BACKGROUND_COLOR = 0xC0000000; // Semi-transparent black
    private static final int BORDER_COLOR = 0xFF666666; // Gray border
    private static final int TEXT_COLOR = 0xFFFFFF; // White text
    private static final int PROGRESS_BG_COLOR = 0xFF333333; // Dark gray
    private static final int PROGRESS_FILL_COLOR = 0xFF00AA00; // Green
    private static final int SPINNER_COLOR = 0xFFAAFFAA; // Light green
    
    private static final int PADDING = 16;
    private static final int SPINNER_SIZE = 16;
    private static final int PROGRESS_BAR_HEIGHT = 4;
    private static final int ELEMENT_SPACING = 8;
    
    public LoadingIndicatorWidget(int x, int y, int width, TextRenderer textRenderer) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.textRenderer = textRenderer;
        this.startTime = System.currentTimeMillis();
        calculateHeight();
    }
    
    private void calculateHeight() {
        this.height = PADDING * 2 + // Top and bottom padding
                     SPINNER_SIZE + // Spinner
                     ELEMENT_SPACING + // Space between spinner and text
                     textRenderer.fontHeight + // Text height
                     ELEMENT_SPACING + // Space between text and progress bar
                     PROGRESS_BAR_HEIGHT; // Progress bar
    }
    
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        
        // Draw background
        context.fill(x, y, x + width, y + height, BACKGROUND_COLOR);
        context.drawBorder(x, y, width, height, BORDER_COLOR);
        
        int currentY = y + PADDING;
        int centerX = x + width / 2;
        
        // Draw animated spinner
        drawSpinner(context, centerX - SPINNER_SIZE / 2, currentY);
        currentY += SPINNER_SIZE + ELEMENT_SPACING;
        
        // Draw loading text (centered)
        int textWidth = textRenderer.getWidth(loadingText);
        context.drawText(textRenderer, Text.literal(loadingText), 
                        centerX - textWidth / 2, currentY, TEXT_COLOR, false);
        currentY += textRenderer.fontHeight + ELEMENT_SPACING;
        
        // Draw progress bar
        drawProgressBar(context, x + PADDING, currentY, width - PADDING * 2);
    }
    
    private void drawSpinner(DrawContext context, int centerX, int centerY) {
        long currentTime = System.currentTimeMillis();
        float rotation = ((currentTime - startTime) / 100.0f) % 360.0f; // Full rotation every 3.6 seconds
        
        int spinnerCenterX = centerX + SPINNER_SIZE / 2;
        int spinnerCenterY = centerY + SPINNER_SIZE / 2;
        
        // Draw spinning dots in a circle
        for (int i = 0; i < 8; i++) {
            float angle = (rotation + i * 45.0f) * (float) Math.PI / 180.0f;
            float alpha = 1.0f - (i / 8.0f); // Fade trail effect
            
            int dotX = spinnerCenterX + (int) (Math.cos(angle) * 6);
            int dotY = spinnerCenterY + (int) (Math.sin(angle) * 6);
            
            int color = (int) (alpha * 255) << 24 | (SPINNER_COLOR & 0xFFFFFF);
            context.fill(dotX - 1, dotY - 1, dotX + 2, dotY + 2, color);
        }
    }
    
    private void drawProgressBar(DrawContext context, int barX, int barY, int barWidth) {
        // Background
        context.fill(barX, barY, barX + barWidth, barY + PROGRESS_BAR_HEIGHT, PROGRESS_BG_COLOR);
        
        if (indeterminate) {
            // Animated indeterminate progress
            long currentTime = System.currentTimeMillis();
            float animationProgress = ((currentTime - startTime) / 2000.0f) % 1.0f; // 2-second cycle
            
            int indicatorWidth = barWidth / 4;
            int indicatorX = barX + (int) (animationProgress * (barWidth - indicatorWidth));
            
            context.fill(indicatorX, barY, 
                        indicatorX + indicatorWidth, barY + PROGRESS_BAR_HEIGHT, 
                        PROGRESS_FILL_COLOR);
        } else {
            // Determinate progress
            int fillWidth = (int) (barWidth * MathHelper.clamp(progress, 0.0f, 1.0f));
            context.fill(barX, barY, 
                        barX + fillWidth, barY + PROGRESS_BAR_HEIGHT, 
                        PROGRESS_FILL_COLOR);
        }
    }
    
    public void show(String text) {
        this.loadingText = text != null ? text : "Loading...";
        this.visible = true;
        this.startTime = System.currentTimeMillis();
    }
    
    public void hide() {
        this.visible = false;
    }
    
    public void setProgress(float progress) {
        this.progress = MathHelper.clamp(progress, 0.0f, 1.0f);
        this.indeterminate = false;
    }
    
    public void setIndeterminate(boolean indeterminate) {
        this.indeterminate = indeterminate;
    }
    
    public void setText(String text) {
        this.loadingText = text != null ? text : "Loading...";
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
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
