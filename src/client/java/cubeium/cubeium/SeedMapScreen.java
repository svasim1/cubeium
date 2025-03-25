package cubeium.cubeium;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.BiomeKeys;

public class SeedMapScreen extends Screen {
    private static final int MAP_SIZE = 200;
    private static final int ZOOM_LEVEL = 4;
    private final int centerX;
    private final int centerZ;

    public SeedMapScreen() {
        super(Text.literal("Seed Map"));

        // Get player position for center of map
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            BlockPos pos = client.player.getBlockPos();
            centerX = pos.getX();
            centerZ = pos.getZ();
        } else {
            centerX = 0;
            centerZ = 0;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw a semi-transparent black background
        context.fill(0, 0, this.width, this.height, 0xE0000000);

        // Draw the map background
        int mapX = (width - MAP_SIZE) / 2;
        int mapY = (height - MAP_SIZE) / 2;
        context.fill(mapX, mapY, mapX + MAP_SIZE, mapY + MAP_SIZE, 0xFF000000);

        if (client.world != null) {
            BiomeAccess biomeAccess = client.world.getBiomeAccess();

            // Draw biome colors
            for (int x = 0; x < MAP_SIZE; x++) {
                for (int z = 0; z < MAP_SIZE; z++) {
                    int worldX = centerX + ((x - MAP_SIZE / 2) * ZOOM_LEVEL);
                    int worldZ = centerZ + ((z - MAP_SIZE / 2) * ZOOM_LEVEL);

                    RegistryEntry<Biome> biomeEntry = biomeAccess.getBiome(new BlockPos(worldX, 0, worldZ));
                    int color = getBiomeColor(biomeEntry);

                    context.fill(
                            mapX + x, mapY + z,
                            mapX + x + 1, mapY + z + 1,
                            color);
                }
            }

            // Draw player position
            if (client.player != null) {
                BlockPos pos = client.player.getBlockPos();
                int playerX = mapX + MAP_SIZE / 2 + (pos.getX() - centerX) / ZOOM_LEVEL;
                int playerZ = mapY + MAP_SIZE / 2 + (pos.getZ() - centerZ) / ZOOM_LEVEL;
                context.fill(playerX - 2, playerZ - 2, playerX + 2, playerZ + 2, 0xFFFF0000);
            }
        } else {
            // Draw text if no world is loaded
            context.drawText(
                    this.textRenderer,
                    "No world loaded",
                    width / 2 - 50,
                    height / 2,
                    0xFFFFFFFF,
                    true);
        }
    }

    private int getBiomeColor(RegistryEntry<Biome> biomeEntry) {
        if (biomeEntry.matchesKey(BiomeKeys.OCEAN))
            return 0xFF0000FF; // Blue
        if (biomeEntry.matchesKey(BiomeKeys.PLAINS))
            return 0xFF90EE90; // Light green
        if (biomeEntry.matchesKey(BiomeKeys.DESERT))
            return 0xFFFFD700; // Gold
        if (biomeEntry.matchesKey(BiomeKeys.FOREST))
            return 0xFF228B22; // Forest green
        if (biomeEntry.matchesKey(BiomeKeys.TAIGA))
            return 0xFF2F4F4F; // Dark slate gray
        if (biomeEntry.matchesKey(BiomeKeys.SWAMP))
            return 0xFF556B2F; // Dark olive green
        if (biomeEntry.matchesKey(BiomeKeys.RIVER))
            return 0xFF4169E1; // Royal blue
        if (biomeEntry.matchesKey(BiomeKeys.NETHER_WASTES))
            return 0xFF8B0000; // Dark red
        if (biomeEntry.matchesKey(BiomeKeys.THE_END))
            return 0xFF4B0082; // Indigo
        return 0xFF808080; // Default gray
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}