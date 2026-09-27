package cubeium.cubeium.screen;

import cubeium.cubeium.config.CubeiumConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The classic menu look: the dirt block texture, darkened. This is how the options background
 * was drawn before 1.20.5 (dirt tinted 0x404040 for screens, 0x202020 for lists), using the
 * game's own texture rather than a bundled copy.
 */
final class Backgrounds {
    private static final Identifier DIRT = Identifier.withDefaultNamespace("textures/block/dirt.png");
    private static final int SCREEN_TINT = 0xFF404040;
    private static final int PANEL_TINT = 0xFF202020;
    private static final int DARK_SCREEN_TINT = 0xFF1C1C1C;
    private static final int DARK_PANEL_TINT = 0xFF101010;
    /** A 16 px texture drawn over 32 GUI pixels, as vanilla did. */
    private static final int TILE = 32;

    private Backgrounds() {
    }

    static void screen(GuiGraphicsExtractor graphics, int width, int height) {
        dirt(graphics, 0, 0, width, height, CubeiumConfig.get().darkMode ? DARK_SCREEN_TINT : SCREEN_TINT);
    }

    /** A recessed panel with the vanilla header/footer separators along its top and bottom edge. */
    static void panel(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        dirt(graphics, x0, y0, x1 - x0, y1 - y0, panelTint());
        graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, x0, y0, 0, 0, x1 - x0, 2, 32, 2);
        graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, x0, y1 - 2, 0, 0, x1 - x0, 2, 32, 2);
    }

    static void panelFill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        dirt(graphics, x0, y0, x1 - x0, y1 - y0, panelTint());
    }

    private static int panelTint() {
        return CubeiumConfig.get().darkMode ? DARK_PANEL_TINT : PANEL_TINT;
    }

    private static void dirt(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int tint) {
        // u/v follow the screen position so neighbouring areas line up seamlessly.
        graphics.blit(RenderPipelines.GUI_TEXTURED, DIRT, x, y, x, y, width, height, TILE, TILE, tint);
    }
}
