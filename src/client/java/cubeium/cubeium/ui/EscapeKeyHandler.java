package cubeium.cubeium.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Handles ESC key functionality for closing screens and returning to game
 */
public class EscapeKeyHandler {
    private final MinecraftClient client;
    private boolean escapePressed = false;
    private long lastEscapeTime = 0;
    private static final long DOUBLE_PRESS_THRESHOLD = 300; // 300ms for double press
    
    public EscapeKeyHandler() {
        this.client = MinecraftClient.getInstance();
    }
    
    /**
     * Handle escape key press event
     * @param keyCode The key that was pressed
     * @param scanCode The scan code
     * @param modifiers Any modifier keys
     * @return true if the event was handled, false otherwise
     */
    public boolean handleKeyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            long currentTime = System.currentTimeMillis();
            
            // Check for double press (quick close)
            if (escapePressed && (currentTime - lastEscapeTime) < DOUBLE_PRESS_THRESHOLD) {
                handleDoubleEscape();
                escapePressed = false;
                return true;
            }
            
            // Single escape press
            boolean handled = handleSingleEscape();
            escapePressed = true;
            lastEscapeTime = currentTime;
            
            return handled;
        }
        
        return false;
    }
    
    /**
     * Handle single escape key press
     * @return true if handled, false to pass to default handler
     */
    private boolean handleSingleEscape() {
        Screen currentScreen = client.currentScreen;
        
        if (currentScreen != null) {
            // If we're in a custom screen, handle the escape
            if (currentScreen.getClass().getPackage().getName().startsWith("cubeium")) {
                // Custom screen - close it and return to game or previous screen
                client.setScreen(null);
                return true;
            }
            
            // Let other screens handle escape normally
            return false;
        }
        
        // If in-game, let default handler open pause menu
        return false;
    }
    
    /**
     * Handle double escape key press (quick exit)
     */
    private void handleDoubleEscape() {
        // Double escape always returns to game, bypassing any menus
        client.setScreen(null);
        
        // If in a world, return focus to game
        if (client.world != null) {
            client.mouse.lockCursor();
        }
    }
    
    /**
     * Reset escape state (call when screen changes)
     */
    public void reset() {
        escapePressed = false;
        lastEscapeTime = 0;
    }
    
    /**
     * Check if currently in a cubeium screen
     */
    public boolean isInCubeiumScreen() {
        Screen currentScreen = client.currentScreen;
        return currentScreen != null && 
               currentScreen.getClass().getPackage().getName().startsWith("cubeium");
    }
    
    /**
     * Get time since last escape press
     */
    public long getTimeSinceLastEscape() {
        return System.currentTimeMillis() - lastEscapeTime;
    }
}
