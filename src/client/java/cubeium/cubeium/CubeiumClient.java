package cubeium.cubeium;

import org.lwjgl.glfw.GLFW;

import cubeium.cubeium.seedmap.CubeiumSeedMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class CubeiumClient implements ClientModInitializer {
    private long tickCount = 0;
    private boolean firstTickLogged = false;

    @Override
    public void onInitializeClient() {
        Cubeium.LOGGER.info("[Cubeium] Client initializer starting");

        // Register the key binding
        Cubeium.seedMapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cubeium.seedmap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.cubeium.keybinds"));
        Cubeium.LOGGER.info("[Cubeium] Registered keybind key.cubeium.seedmap on key M");

        // Register key binding tick event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCount++;

            if (!firstTickLogged) {
                firstTickLogged = true;
                Cubeium.LOGGER.info("[Cubeium] END_CLIENT_TICK callback is active");
            }

            KeyBinding seedMapKey = (KeyBinding) Cubeium.seedMapKey;
            if (seedMapKey == null) {
                Cubeium.LOGGER.error("[Cubeium] Seed map key is null in tick callback");
                return;
            }

            if (seedMapKey.wasPressed()) {
                Cubeium.LOGGER.info("[Cubeium] Seed map key pressed at tick {}", tickCount);
                // Quiet mode: do not send a chat message when opening the seed map screen

                try {
                    client.setScreen(new CubeiumSeedMapScreen());
                    Cubeium.LOGGER.info("[Cubeium] Seed map screen opened successfully");
                } catch (Exception e) {
                    Cubeium.LOGGER.error("[Cubeium] Failed to open seed map screen", e);
                }
            }
        });

    // JNI test runner removed from client initialization to avoid runtime/compile-time dependency

        Cubeium.LOGGER.info("[Cubeium] Client initializer complete");
    }
}