package cubeium.cubeium.screen;

import cubeium.cubeium.config.CubeiumConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The classic menu look: a block texture, darkened. This is how the options background was drawn
 * before 1.20.5 (dirt tinted 0x404040 for screens, 0x202020 for lists), using the game's own
 * textures rather than bundled copies. Dark mode swaps dirt for blackstone.
 */
final class Backgrounds {
    private static final Identifier DIRT = Identifier.withDefaultNamespace("textures/block/dirt.png");
    private static final Identifier BLACKSTONE = Identifier.withDefaultNamespace("textures/block/blackstone.png");
    private static final int SCREEN_TINT = 0xFF404040;
    private static final int PANEL_TINT = 0xFF202020;
    /** Blackstone is much darker than dirt, so it takes a lighter tint for a similar brightness. */
    private static final int DARK_SCREEN_TINT = 0xFFA0A0A0;
    private static final int DARK_PANEL_TINT = 0xFF505050;
    /** A 16 px texture drawn over 32 GUI pixels, as vanilla did. */
    private static final int TILE = 32;

    private Backgrounds() {
    }

    static void screen(GuiGraphicsExtractor graphics, int width, int height) {
        boolean dark = CubeiumConfig.get().darkMode;
        draw(graphics, 0, 0, width, height, dark ? DARK_SCREEN_TINT : SCREEN_TINT);
    }

    static void panelFill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
        boolean dark = CubeiumConfig.get().darkMode;
        draw(graphics, x0, y0, x1 - x0, y1 - y0, dark ? DARK_PANEL_TINT : PANEL_TINT);
    }

    private static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int tint) {
        Identifier texture = CubeiumConfig.get().darkMode ? BLACKSTONE : DIRT;
        // u/v follow the screen position so neighbouring areas line up seamlessly.
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, x, y, width, height, TILE, TILE, tint);
    }
}
