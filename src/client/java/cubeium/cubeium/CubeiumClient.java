package cubeium.cubeium;

import org.lwjgl.glfw.GLFW;

import cubeium.cubeium.world.test.JNITestRunner;
import cubeium.cubeium.blazemap.BlazeMapSeedScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

public class CubeiumClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Cubeium.LOGGER.info("Initializing Cubeium client...");

        // Register the key binding
        Cubeium.seedMapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cubeium.seedmap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.cubeium.keybinds"));

        // Register key binding tick event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            KeyBinding seedMapKey = (KeyBinding) Cubeium.seedMapKey;
            if (seedMapKey == null) {
                Cubeium.LOGGER.error("Seed map key is null!");
                return;
            }

            if (seedMapKey.wasPressed()) {
                Cubeium.LOGGER.info("Seed map key pressed!");
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Opening BlazeMap seed explorer..."), false);
                }

                try {
                    client.setScreen(new BlazeMapSeedScreen());
                    Cubeium.LOGGER.info("BlazeMap Screen set successfully");
                } catch (Exception e) {
                    Cubeium.LOGGER.error("Failed to set BlazeMap screen: " + e.getMessage());
                }
            }
        });

        // Run comprehensive JNI tests for Task 1.6
        JNITestRunner.runComprehensiveTests();

        Cubeium.LOGGER.info("Client initialization complete!");
    }
}