package cubeium.cubeium;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;

public class CubeiumClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Cubeium.LOGGER.info("Initializing Cubeium client...");

        // Register key binding tick event
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (Cubeium.seedMapKey == null) {
                Cubeium.LOGGER.error("Seed map key is null!");
                return;
            }

            if (Cubeium.seedMapKey.wasPressed()) {
                Cubeium.LOGGER.info("Seed map key pressed!");
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("Opening seed map..."), false);
                } else {
                    Cubeium.LOGGER.warn("Player is null when key was pressed!");
                }

                try {
                    client.setScreen(new SeedMapScreen());
                    Cubeium.LOGGER.info("Screen set successfully");
                } catch (Exception e) {
                    Cubeium.LOGGER.error("Failed to set screen: " + e.getMessage());
                }
            }
        });

        Cubeium.LOGGER.info("Client initialization complete!");
    }
}