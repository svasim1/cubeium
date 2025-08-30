package cubeium.cubeium.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

/**
 * Status bar widget showing current operation status, memory usage, and performance info
 */
public class StatusBarWidget {
    public enum StatusLevel {
        INFO(0xFF666666),    // Gray
        SUCCESS(0xFF00AA00), // Green  
        WARNING(0xFFFFAA00), // Orange
        ERROR(0xFFAA0000);   // Red
        
        public final int color;
        
        StatusLevel(int color) {
            this.color = color;
        }
    }
    
    private final TextRenderer textRenderer;
    private int x, y, width, height;
    private String statusText = "Ready";
    private StatusLevel currentLevel = StatusLevel.INFO;
    private long lastUpdateTime = 0;
    private boolean showPerformanceInfo = false;
    
    // Performance tracking
    private long lastFrameTime = System.nanoTime();
    private int frameCount = 0;
    private float averageFPS = 0.0f;
    private long memoryUsed = 0;
    private long memoryTotal = 0;
    
    // Visual constants
    private static final int BACKGROUND_COLOR = 0xC0222222; // Dark semi-transparent
    private static final int BORDER_COLOR = 0xFF444444; // Gray border
    private static final int TEXT_COLOR = 0xFFFFFFFF; // White
    private static final int PADDING = 6;
    private static final int SEPARATOR_COLOR = 0xFF666666;
    
    public StatusBarWidget(int x, int y, int width, TextRenderer textRenderer) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.textRenderer = textRenderer;
        this.height = textRenderer.fontHeight + (PADDING * 2);
        updateMemoryInfo();
    }
    
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updatePerformanceInfo();
        
        // Draw background
        context.fill(x, y, x + width, y + height, BACKGROUND_COLOR);
        
        // Draw top border line
        context.fill(x, y, x + width, y + 1, BORDER_COLOR);
        
        int textY = y + PADDING;
        int currentX = x + PADDING;
        
        // Status text with colored indicator
        drawStatusIndicator(context, currentX, textY);
        currentX += 12; // Space for indicator dot
        
        context.drawText(textRenderer, Text.literal(statusText), 
                        currentX, textY, TEXT_COLOR, false);
        
        if (showPerformanceInfo) {
            currentX += textRenderer.getWidth(statusText) + 16;
            drawPerformanceInfo(context, currentX, textY);
        }
        
        // Memory info on the right side
        drawMemoryInfo(context, textY);
    }
    
    private void drawStatusIndicator(DrawContext context, int x, int y) {
        // Draw status dot
        int dotSize = 6;
        int dotY = y + (textRenderer.fontHeight / 2) - (dotSize / 2);
        context.fill(x, dotY, x + dotSize, dotY + dotSize, currentLevel.color);
    }
    
    private void drawPerformanceInfo(DrawContext context, int x, int y) {
        String fpsText = String.format("FPS: %.1f", averageFPS);
        context.drawText(textRenderer, Text.literal(fpsText), x, y, TEXT_COLOR, false);
        
        // Draw separator
        int separatorX = x + textRenderer.getWidth(fpsText) + 8;
        context.fill(separatorX, y + 2, separatorX + 1, y + textRenderer.fontHeight - 2, SEPARATOR_COLOR);
    }
    
    private void drawMemoryInfo(DrawContext context, int y) {
        String memoryText = formatMemory(memoryUsed) + "/" + formatMemory(memoryTotal);
        int memoryWidth = textRenderer.getWidth(memoryText);
        int memoryX = x + width - PADDING - memoryWidth;
        
        context.drawText(textRenderer, Text.literal(memoryText), memoryX, y, TEXT_COLOR, false);
        
        // Memory usage bar
        int barWidth = 60;
        int barHeight = 3;
        int barX = memoryX - barWidth - 8;
        int barY = y + textRenderer.fontHeight / 2 - barHeight / 2;
        
        // Background
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
        
        // Usage
        float usage = (float) memoryUsed / memoryTotal;
        int usageWidth = (int) (barWidth * usage);
        int usageColor = usage > 0.8f ? 0xFFAA0000 : (usage > 0.6f ? 0xFFFFAA00 : 0xFF00AA00);
        context.fill(barX, barY, barX + usageWidth, barY + barHeight, usageColor);
    }
    
    private void updatePerformanceInfo() {
        long currentTime = System.nanoTime();
        frameCount++;
        
        // Update FPS every second
        if (currentTime - lastFrameTime >= 1_000_000_000L) {
            averageFPS = frameCount / ((currentTime - lastFrameTime) / 1_000_000_000.0f);
            frameCount = 0;
            lastFrameTime = currentTime;
            
            // Update memory info less frequently
            updateMemoryInfo();
        }
    }
    
    private void updateMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        memoryTotal = runtime.totalMemory();
        memoryUsed = memoryTotal - runtime.freeMemory();
    }
    
    private String formatMemory(long bytes) {
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format("%.1fKB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024.0));
        return String.format("%.1fGB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
    
    public void setStatus(String text, StatusLevel level) {
        this.statusText = text != null ? text : "Ready";
        this.currentLevel = level != null ? level : StatusLevel.INFO;
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    public void setStatus(String text) {
        setStatus(text, StatusLevel.INFO);
    }
    
    public void showSuccess(String text) {
        setStatus(text, StatusLevel.SUCCESS);
    }
    
    public void showWarning(String text) {
        setStatus(text, StatusLevel.WARNING);
    }
    
    public void showError(String text) {
        setStatus(text, StatusLevel.ERROR);
    }
    
    public void clearStatus() {
        setStatus("Ready", StatusLevel.INFO);
    }
    
    public void setShowPerformanceInfo(boolean show) {
        this.showPerformanceInfo = show;
    }
    
    public boolean isShowingPerformanceInfo() {
        return showPerformanceInfo;
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
    
    public String getCurrentStatus() {
        return statusText;
    }
    
    public StatusLevel getCurrentLevel() {
        return currentLevel;
    }
    
    public long getTimeSinceLastUpdate() {
        return System.currentTimeMillis() - lastUpdateTime;
    }
}
