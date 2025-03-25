package cubeium.cubeium;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.BiomeKeys;

public class SeedMapScreen extends Screen {
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 4.0f;
    private float zoomLevel = 1.0f;
    private int mapX, mapY, mapWidth, mapHeight;

    public SeedMapScreen() {
        super(Text.literal("Seed Map"));
    }

    @Override
    protected void init() {
        super.init();
        // Calculate map dimensions (80% of screen size)
        mapWidth = (int) (width * 0.8);
        mapHeight = (int) (height * 0.8);
        mapX = (width - mapWidth) / 2;
        mapY = (height - mapHeight) / 2;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            context.drawCenteredTextWithShadow(textRenderer, "No world loaded!", width / 2, height / 2, 0xFFFFFFFF);
            return;
        }

        // Draw the map background
        context.fill(mapX, mapY, mapX + mapWidth, mapY + mapHeight, 0xFF000000);

        // Draw biome colors
        BlockPos playerPos = client.player.getBlockPos();
        int centerX = playerPos.getX();
        int centerZ = playerPos.getZ();

        // Calculate the visible area based on map size and zoom
        int visibleWidth = (int) (mapWidth / zoomLevel);
        int visibleHeight = (int) (mapHeight / zoomLevel);
        int startX = centerX - visibleWidth / 2;
        int startZ = centerZ - visibleHeight / 2;

        // Draw the visible area
        for (int x = 0; x < mapWidth; x++) {
            for (int z = 0; z < mapHeight; z++) {
                int worldX = startX + (int) (x / zoomLevel);
                int worldZ = startZ + (int) (z / zoomLevel);

                RegistryEntry<Biome> biome = client.world.getBiome(new BlockPos(worldX, 0, worldZ));
                int color = getBiomeColor(biome);

                context.fill(mapX + x, mapY + z, mapX + x + 1, mapY + z + 1, color);
            }
        }

        // Draw player position (red dot)
        int playerScreenX = mapX + mapWidth / 2;
        int playerScreenY = mapY + mapHeight / 2;
        context.fill(playerScreenX - 2, playerScreenY - 2,
                playerScreenX + 2, playerScreenY + 2,
                0xFFFF0000);

        // Draw zoom level indicator
        String zoomText = String.format("Zoom: %.1fx", zoomLevel);
        context.drawTextWithShadow(textRenderer, zoomText, mapX + 5, mapY + 5, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Only zoom if the mouse is over the map area
        if (mouseX >= mapX && mouseX < mapX + mapWidth &&
                mouseY >= mapY && mouseY < mapY + mapHeight) {
            zoomLevel = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoomLevel + (float) verticalAmount * 0.1f));

            return true;
        }
        return false;
    }

    private int getBiomeColor(RegistryEntry<Biome> biome) {
        if (biome.matchesKey(BiomeKeys.PLAINS))
            return 0xFF90EE90; // Light green
        if (biome.matchesKey(BiomeKeys.DESERT))
            return 0xFFFFD700; // Gold
        if (biome.matchesKey(BiomeKeys.OCEAN))
            return 0xFF0000FF; // Blue
        if (biome.matchesKey(BiomeKeys.FOREST))
            return 0xFF228B22; // Forest green
        if (biome.matchesKey(BiomeKeys.JUNGLE))
            return 0xFF32CD32; // Lime green
        if (biome.matchesKey(BiomeKeys.SAVANNA))
            return 0xFFDAA520; // Goldenrod
        if (biome.matchesKey(BiomeKeys.SNOWY_PLAINS))
            return 0xFFFFFFFF; // White
        if (biome.matchesKey(BiomeKeys.TAIGA))
            return 0xFF8B4513; // Saddle brown
        return 0xFF808080; // Gray for unknown biomes
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}