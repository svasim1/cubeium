package cubeium.cubeium;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cubeium implements ModInitializer {
	public static final String MOD_ID = "cubeium";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static KeyBinding seedMapKey;

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Cubeium mod...");

		// Register the key binding
		seedMapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.cubeium.seedmap",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_M,
				"category.cubeium.keybinds"));

		LOGGER.info("Key binding registered successfully!");
	}
}