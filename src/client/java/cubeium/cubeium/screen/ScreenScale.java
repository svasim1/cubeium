package cubeium.cubeium.screen;

import com.mojang.blaze3d.platform.Window;

import cubeium.cubeium.config.CubeiumConfig;
import net.minecraft.client.Minecraft;

/**
 * Gives the Cubeium screens their own GUI scale while they are open, so the layout looks the same
 * on every monitor. The player's normal GUI scale is restored when they close.
 */
final class ScreenScale {
    static final int AUTO = 0;
    /** Auto picks about one GUI unit per this many window pixels of height (1080p: 2, 1440p: 3, 4K: 4). */
    private static final float WINDOW_PIXELS_PER_SCALE = 500f;

    private ScreenScale() {
    }

    /** Switches the window to the map screens' scale. Returns true when it changed. */
    static boolean apply(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        int scale = scale(minecraft);
        if (window.getGuiScale() == scale) {
            return false;
        }
        window.setGuiScale(scale);
        return true;
    }

    static void restore(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        window.setGuiScale(window.calculateScale(minecraft.options.guiScale().get(), minecraft.isEnforceUnicode()));
    }

    /** The largest scale the window supports. */
    static int maxScale(Minecraft minecraft) {
        return minecraft.getWindow().calculateScale(0, minecraft.isEnforceUnicode());
    }

    private static int scale(Minecraft minecraft) {
        int setting = CubeiumConfig.get().mapUiScale;
        int scale = setting == AUTO ? Math.round(minecraft.getWindow().getHeight() / WINDOW_PIXELS_PER_SCALE) : setting;
        return Math.clamp(scale, 1, maxScale(minecraft));
    }
}
