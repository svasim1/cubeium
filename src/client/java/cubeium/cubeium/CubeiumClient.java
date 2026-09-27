package cubeium.cubeium;

import org.lwjgl.glfw.GLFW;

import cubeium.cubeium.seedmap.CubeiumSeedMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class CubeiumClient implements ClientModInitializer {
    private static KeyBinding seedMapKey;

    @Override
    public void onInitializeClient() {
        seedMapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cubeium.seedmap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_M,
                "category.cubeium.keybinds"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (seedMapKey.wasPressed()) {
                client.setScreen(new CubeiumSeedMapScreen());
            }
        });
    }
}
