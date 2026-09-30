package cubeium.cubeium;

import com.mojang.blaze3d.platform.InputConstants;

import cubeium.cubeium.screen.SeedMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class CubeiumClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Cubeium.MOD_ID, "main"));
    private static KeyMapping openMap;

    public static KeyMapping openMapKey() {
        return openMap;
    }

    @Override
    public void onInitializeClient() {
        openMap = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cubeium.seedmap", InputConstants.Type.KEYSYM, InputConstants.KEY_M, CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMap.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new SeedMapScreen());
                }
            }
        });
    }
}
