package cubeium.cubeium;

import cubeium.cubeium.generator.BiomeGenerator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class SeedMapScreen extends Screen {
    private static final int MAP_SIZE = 64;
    private static final int CHUNK_SIZE = 16;
    private static final int BIOME_SIZE = 4;
    private static final int MAP_SCALE = 4;
    private static final int MAP_WIDTH = MAP_SIZE / MAP_SCALE;
    private static final int MAP_HEIGHT = MAP_SIZE / MAP_SCALE;
    private static final int MAP_X = 0;
    private static final int MAP_Y = 0;

    private final MinecraftClient client;
    private BiomeGenerator biomeGenerator;
    private final long seed;
    private double zoomLevel = 1.0;
    private double offsetX = 0;
    private double offsetZ = 0;
    private boolean isDragging = false;
    private int lastMouseX = 0;
    private int lastMouseY = 0;
    private int[] biomeMap;
    private boolean needsUpdate = true;

    public SeedMapScreen(MinecraftClient client, long seed) {
        super(Text.literal("Biome Map"));
        this.client = client;
        this.seed = seed;
        this.biomeGenerator = new BiomeGenerator(seed, false);
    }

    @Override
    public void init() {
        super.init();
        // Center the map on the player's position
        if (client.player != null) {
            BlockPos pos = client.player.getBlockPos();
            offsetX = -pos.getX() / MAP_SCALE + MAP_WIDTH / 2;
            offsetZ = -pos.getZ() / MAP_SCALE + MAP_HEIGHT / 2;
        } else {
            // Default center if no player
            offsetX = -MAP_WIDTH / 2;
            offsetZ = -MAP_HEIGHT / 2;
        }

        // Add regenerate button
        ButtonWidget regenerateButton = ButtonWidget.builder(Text.literal("Regenerate"), (button) -> {
            // Generate a new random seed
            long newSeed = client.world.getRandom().nextLong();
            // Create new biome generator
            this.biomeGenerator = new BiomeGenerator(newSeed, false);
            // Force map update
            this.needsUpdate = true;
        }).dimensions(width - 120, 10, 100, 20).build();

        this.addDrawableChild(regenerateButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Draw map background
        context.fill(MAP_X, MAP_Y, MAP_X + MAP_WIDTH, MAP_Y + MAP_HEIGHT, 0xFF000000);

        // Update biome map if needed
        if (needsUpdate) {
            updateBiomeMap();
            needsUpdate = false;
        }

        // Draw biomes
        if (biomeMap != null) {
            int visibleWidth = (int) (MAP_WIDTH / zoomLevel);
            int visibleHeight = (int) (MAP_HEIGHT / zoomLevel);
            int startX = (int) (-offsetX / zoomLevel);
            int startZ = (int) (-offsetZ / zoomLevel);

            for (int z = 0; z < visibleHeight; z++) {
                for (int x = 0; x < visibleWidth; x++) {
                    int worldX = startX + x;
                    int worldZ = startZ + z;

                    if (worldX >= 0 && worldX < MAP_WIDTH && worldZ >= 0 && worldZ < MAP_HEIGHT) {
                        int biomeId = biomeMap[worldZ * MAP_WIDTH + worldX];
                        int color = getBiomeColor(biomeId);

                        int screenX = MAP_X + (int) (x * zoomLevel);
                        int screenY = MAP_Y + (int) (z * zoomLevel);
                        int size = (int) Math.ceil(zoomLevel);

                        context.fill(screenX, screenY, screenX + size, screenY + size, color);
                    }
                }
            }
        }

        // Draw player position
        if (client.player != null) {
            BlockPos pos = client.player.getBlockPos();
            int playerScreenX = MAP_X + (int) ((pos.getX() / MAP_SCALE - offsetX) * zoomLevel);
            int playerScreenY = MAP_Y + (int) ((pos.getZ() / MAP_SCALE - offsetZ) * zoomLevel);
            context.fill(playerScreenX - 2, playerScreenY - 2, playerScreenX + 2, playerScreenY + 2, 0xFFFF0000);
        }

        // Draw zoom level
        String zoomText = String.format("Zoom: %.1fx", zoomLevel);
        context.drawTextWithShadow(textRenderer, zoomText, MAP_X + 5, MAP_Y + 5, 0xFFFFFFFF);
    }

    private void updateBiomeMap() {
        // Calculate the visible area in world coordinates
        int startX = (int) ((-offsetX / zoomLevel) * MAP_SCALE);
        int startZ = (int) ((-offsetZ / zoomLevel) * MAP_SCALE);
        int visibleWidth = (int) ((MAP_WIDTH / zoomLevel) * MAP_SCALE);
        int visibleHeight = (int) ((MAP_HEIGHT / zoomLevel) * MAP_SCALE);

        // Generate the biome map for the visible area
        biomeMap = biomeGenerator.generateBiomeMap(startX, startZ, visibleWidth, visibleHeight);
    }

    private int getBiomeColor(int biomeId) {
        // Map biome IDs to colors
        switch (biomeId) {
            case 0:
                return 0xFFF0F8FF; // Snowy biomes
            case 1:
                return 0xFF87CEEB; // Cold biomes
            case 2:
                return 0xFF90EE90; // Temperate biomes
            case 3:
                return 0xFFFFD700; // Warm biomes
            case 4:
                return 0xFFFF4500; // Hot biomes
            default:
                return 0xFF808080; // Unknown biomes
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInMap(mouseX, mouseY)) {
            isDragging = true;
            lastMouseX = (int) mouseX;
            lastMouseY = (int) mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDragging && button == 0) {
            offsetX -= deltaX / zoomLevel;
            offsetZ -= deltaY / zoomLevel;
            needsUpdate = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInMap(mouseX, mouseY)) {
            double oldZoom = zoomLevel;
            zoomLevel = Math.max(0.5, Math.min(4.0, zoomLevel * (1.0 - verticalAmount * 0.1)));

            // Adjust offset to zoom towards mouse position
            if (zoomLevel != oldZoom) {
                double factor = zoomLevel / oldZoom;
                offsetX = mouseX - (mouseX - offsetX) * factor;
                offsetZ = mouseY - (mouseY - offsetZ) * factor;
                needsUpdate = true;
            }
            return true;
        }
        return false;
    }

    private boolean isInMap(double mouseX, double mouseY) {
        return mouseX >= MAP_X && mouseX < MAP_X + MAP_WIDTH &&
                mouseY >= MAP_Y && mouseY < MAP_Y + MAP_HEIGHT;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Handle WASD/arrow key movement
        double moveSpeed = 10.0 / zoomLevel;
        switch (keyCode) {
            case 87: // W
            case 265: // Up arrow
                offsetZ -= moveSpeed;
                break;
            case 83: // S
            case 264: // Down arrow
                offsetZ += moveSpeed;
                break;
            case 65: // A
            case 263: // Left arrow
                offsetX -= moveSpeed;
                break;
            case 68: // D
            case 262: // Right arrow
                offsetX += moveSpeed;
                break;
            default:
                return false;
        }
        needsUpdate = true;
        return true;
    }
}