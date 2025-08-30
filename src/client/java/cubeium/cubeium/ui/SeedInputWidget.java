package cubeium.cubeium.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import cubeium.cubeium.Cubeium;

/**
 * Custom seed input widget with validation, formatting, and random seed generation
 */
public class SeedInputWidget extends TextFieldWidget {
    private static final int WIDGET_WIDTH = 200;
    private static final int WIDGET_HEIGHT = 20;
    
    private boolean isValidSeed = true;
    private String errorMessage = "";
    private final SeedChangeListener listener;
    
    public interface SeedChangeListener {
        void onSeedChanged(long seed, boolean isValid);
    }
    
    public SeedInputWidget(TextRenderer textRenderer, int x, int y, SeedChangeListener listener) {
        super(textRenderer, x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Text.literal("Seed"));
        this.listener = listener;
        
        // Set placeholder text
        this.setPlaceholder(Text.literal("Enter seed or leave empty for random"));
        
        // Set up change listener
        this.setChangedListener(this::onTextChanged);
        
        // Initialize with empty seed - will be set later
        this.setText("");
    }
    
    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);
        
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        
        // Draw error message if seed is invalid
        if (!isValidSeed && !errorMessage.isEmpty()) {
            context.drawTextWithShadow(
                tr, 
                errorMessage, 
                getX(), 
                getY() + getHeight() + 2, 
                0xFFFF6666
            );
        }
        
        // Draw seed info
        if (isValidSeed && !getText().isEmpty()) {
            long seed = parseSeed(getText());
            String seedInfo = String.format("Seed: %d (0x%X)", seed, seed);
            context.drawTextWithShadow(
                tr, 
                seedInfo, 
                getX(), 
                getY() - 12, 
                0xFFAAAAAA
            );
        }
    }
    
    private void onTextChanged(String text) {
        validateSeed(text);
    }
    
    private void validateSeed(String input) {
        if (input == null || input.trim().isEmpty()) {
            // Empty input is valid (will use random seed)
            isValidSeed = true;
            errorMessage = "";
            if (listener != null) {
                listener.onSeedChanged(0L, true);  // 0 indicates random seed
            }
            return;
        }
        
        try {
            String cleanInput = input.trim();
            long seed;
            
            // Handle hexadecimal input
            if (cleanInput.toLowerCase().startsWith("0x")) {
                seed = Long.parseUnsignedLong(cleanInput.substring(2), 16);
            } 
            // Handle regular numeric input
            else {
                seed = Long.parseLong(cleanInput);
            }
            
            // Seed is valid
            isValidSeed = true;
            errorMessage = "";
            if (listener != null) {
                listener.onSeedChanged(seed, true);
            }
            
            Cubeium.LOGGER.info("Seed input validated: {} -> {}", input, seed);
            
        } catch (NumberFormatException e) {
            // Try to interpret as string seed (hash the string)
            if (input.trim().length() > 0) {
                long seed = input.trim().hashCode();
                isValidSeed = true;
                errorMessage = "";
                if (listener != null) {
                    listener.onSeedChanged(seed, true);
                }
                
                Cubeium.LOGGER.info("String seed hashed: {} -> {}", input, seed);
            } else {
                isValidSeed = false;
                errorMessage = "Invalid seed format";
                if (listener != null) {
                    listener.onSeedChanged(0L, false);
                }
            }
        } catch (Exception e) {
            isValidSeed = false;
            errorMessage = "Invalid seed: " + e.getMessage();
            if (listener != null) {
                listener.onSeedChanged(0L, false);
            }
            
            Cubeium.LOGGER.warn("Seed validation error: {}", e.getMessage());
        }
    }
    
    /**
     * Parse a seed string to long value
     */
    public long parseSeed(String input) {
        if (input == null || input.trim().isEmpty()) {
            return System.currentTimeMillis(); // Random seed based on current time
        }
        
        try {
            String cleanInput = input.trim();
            
            // Handle hexadecimal input
            if (cleanInput.toLowerCase().startsWith("0x")) {
                return Long.parseUnsignedLong(cleanInput.substring(2), 16);
            }
            // Handle regular numeric input
            else {
                return Long.parseLong(cleanInput);
            }
        } catch (NumberFormatException e) {
            // Hash string input
            return input.trim().hashCode();
        }
    }
    
    /**
     * Generate and set a random seed
     */
    public void generateRandomSeed() {
        long randomSeed = System.currentTimeMillis() ^ (System.nanoTime() << 16);
        setText(String.valueOf(randomSeed));
        validateSeed(getText());
    }
    
    /**
     * Set seed value programmatically
     */
    public void setSeed(long seed) {
        setText(String.valueOf(seed));
        validateSeed(getText());
    }
    
    /**
     * Get current seed value
     */
    public long getCurrentSeed() {
        if (!isValidSeed || getText().trim().isEmpty()) {
            return System.currentTimeMillis(); // Fallback to random
        }
        return parseSeed(getText());
    }
    
    /**
     * Check if current seed input is valid
     */
    public boolean isValidSeed() {
        return isValidSeed;
    }
    
    /**
     * Get current error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle special key combinations
        if (hasControlDown()) {
            switch (keyCode) {
                case 82 -> { // Ctrl+R - Random seed
                    generateRandomSeed();
                    return true;
                }
                case 67 -> { // Ctrl+C - Copy seed
                    if (!getText().isEmpty()) {
                        MinecraftClient.getInstance().keyboard.setClipboard(getText());
                    }
                    return true;
                }
                case 86 -> { // Ctrl+V - Paste seed
                    String clipboard = MinecraftClient.getInstance().keyboard.getClipboard();
                    if (clipboard != null && !clipboard.trim().isEmpty()) {
                        setText(clipboard.trim());
                    }
                    return true;
                }
            }
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    
    private boolean hasControlDown() {
        return net.minecraft.client.gui.screen.Screen.hasControlDown();
    }
}
