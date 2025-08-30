package cubeium.cubeium;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ModInitializer;

public class Cubeium implements ModInitializer {
	public static final String MOD_ID = "cubeium";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static Object seedMapKey; // Placeholder for client keybinding

	@Override
	public void onInitialize() {
		LOGGER.info("Initializing Cubeium mod...");

		// Key binding registration is handled in CubeiumClient.java
		LOGGER.info("Cubeium mod initialized!");
	}
}