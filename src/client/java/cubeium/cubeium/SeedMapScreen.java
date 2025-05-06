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
        context.fill(mapX, mapY, mapX + mapWidth, mapY + mapHeight, 0x80000000); // Semi-transparent black

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
                int playerY = playerPos.getY();

                BlockPos pos = new BlockPos(worldX, playerY, worldZ);
                int color;

                // Check if the chunk is loaded
                if (!client.world.isChunkLoaded(worldX >> 4, worldZ >> 4)) {
                    color = 0x80000000; // Semi-transparent black for unloaded chunks
                } else {
                    RegistryEntry<Biome> biome = client.world.getBiome(pos);
                    color = getBiomeColor(biome);
                }

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
        if (biome.matchesKey(BiomeKeys.SUNFLOWER_PLAINS))
            return 0xFFFFFF00; // Yellow
        if (biome.matchesKey(BiomeKeys.DESERT))
            return 0xFFFFD700; // Gold
        if (biome.matchesKey(BiomeKeys.BADLANDS))
            return 0xFFCD853F; // Peru
        if (biome.matchesKey(BiomeKeys.WOODED_BADLANDS))
            return 0xFF8B4513; // Saddle brown
        if (biome.matchesKey(BiomeKeys.ERODED_BADLANDS))
            return 0xFFD2691E; // Chocolate
        if (biome.matchesKey(BiomeKeys.SAVANNA))
            return 0xFFDAA520; // Goldenrod
        if (biome.matchesKey(BiomeKeys.SAVANNA_PLATEAU))
            return 0xFFB8860B; // Dark goldenrod
        if (biome.matchesKey(BiomeKeys.WINDSWEPT_SAVANNA))
            return 0xFFB8860B; // Dark goldenrod
        if (biome.matchesKey(BiomeKeys.RIVER))
            return 0xFF0000FF; // Blue
        if (biome.matchesKey(BiomeKeys.FROZEN_RIVER))
            return 0xFFADD8E6; // Light blue
        if (biome.matchesKey(BiomeKeys.OCEAN))
            return 0xFF00008B; // Dark blue
        if (biome.matchesKey(BiomeKeys.DEEP_OCEAN))
            return 0xFF000080; // Navy
        if (biome.matchesKey(BiomeKeys.FROZEN_OCEAN))
            return 0xFF4682B4; // Steel blue
        if (biome.matchesKey(BiomeKeys.WARM_OCEAN))
            return 0xFF00CED1; // Dark turquoise
        if (biome.matchesKey(BiomeKeys.LUKEWARM_OCEAN))
            return 0xFF20B2AA; // Light sea green
        if (biome.matchesKey(BiomeKeys.COLD_OCEAN))
            return 0xFF1E90FF; // Dodger blue
        if (biome.matchesKey(BiomeKeys.FOREST))
            return 0xFF228B22; // Forest green
        if (biome.matchesKey(BiomeKeys.FLOWER_FOREST))
            return 0xFFFF69B4; // Hot pink
        if (biome.matchesKey(BiomeKeys.SNOWY_PLAINS))
            return 0xFFFFFFFF; // White
        if (biome.matchesKey(BiomeKeys.ICE_SPIKES))
            return 0xFFAFEEEE; // Pale turquoise
        if (biome.matchesKey(BiomeKeys.SWAMP))
            return 0xFF556B2F; // Dark olive green
        if (biome.matchesKey(BiomeKeys.MANGROVE_SWAMP))
            return 0xFF2E8B57; // Sea green
        if (biome.matchesKey(BiomeKeys.DARK_FOREST))
            return 0xFF006400; // Dark green
        if (biome.matchesKey(BiomeKeys.JUNGLE))
            return 0xFF32CD32; // Lime green
        if (biome.matchesKey(BiomeKeys.SPARSE_JUNGLE))
            return 0xFF7CFC00; // Lawn green
        if (biome.matchesKey(BiomeKeys.BAMBOO_JUNGLE))
            return 0xFF7CFC00; // Lawn green
        if (biome.matchesKey(BiomeKeys.TAIGA))
            return 0xFF8B4513; // Saddle brown
        if (biome.matchesKey(BiomeKeys.SNOWY_TAIGA))
            return 0xFFD3D3D3; // Light gray
        if (biome.matchesKey(BiomeKeys.MEADOW))
            return 0xFF98FB98; // Pale green
        if (biome.matchesKey(BiomeKeys.GROVE))
            return 0xFF2E8B57; // Sea green
        if (biome.matchesKey(BiomeKeys.SNOWY_SLOPES))
            return 0xFFF0F8FF; // Alice blue
        if (biome.matchesKey(BiomeKeys.JAGGED_PEAKS))
            return 0xFFB0C4DE; // Light steel blue
        if (biome.matchesKey(BiomeKeys.FROZEN_PEAKS))
            return 0xFF87CEEB; // Sky blue
        if (biome.matchesKey(BiomeKeys.STONY_PEAKS))
            return 0xFF808080; // Gray
        if (biome.matchesKey(BiomeKeys.STONY_SHORE))
            return 0xFF808080; // Gray
        if (biome.matchesKey(BiomeKeys.BEACH))
            return 0xFFFFFACD; // Lemon chiffon
        if (biome.matchesKey(BiomeKeys.SNOWY_BEACH))
            return 0xFFFFFFFF; // White
        if (biome.matchesKey(BiomeKeys.LUSH_CAVES))
            return 0xFF32CD32; // Lime green
        if (biome.matchesKey(BiomeKeys.DRIPSTONE_CAVES))
            return 0xFF8B4513; // Saddle brown
        if (biome.matchesKey(BiomeKeys.DEEP_DARK))
            return 0xFF000000; // Black
        return 0xFF808080; // Default gray for unknown biomes
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}