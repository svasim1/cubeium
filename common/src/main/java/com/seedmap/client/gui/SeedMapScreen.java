package com.seedmap.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.seedmap.SeedMapMod;
import com.seedmap.world.BiomeGenerator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;

public class SeedMapScreen extends Screen {
    private static final ResourceLocation TEXTURE = new ResourceLocation(SeedMapMod.MOD_ID, "textures/gui/seed_map.png");
    private static final ResourceLocation ZOOM_IN = new ResourceLocation(SeedMapMod.MOD_ID, "textures/gui/zoom_in.png");
    private static final ResourceLocation ZOOM_OUT = new ResourceLocation(SeedMapMod.MOD_ID, "textures/gui/zoom_out.png");
    private static final ResourceLocation PLAYER = new ResourceLocation(SeedMapMod.MOD_ID, "textures/gui/player.png");
    
    private int mapWidth = 256;
    private int mapHeight = 256;
    private int guiLeft;
    private int guiTop;
    private BiomeGenerator biomeGenerator;
    private int zoomLevel = 1;
    private ImageButton zoomInButton;
    private ImageButton zoomOutButton;
    private Button centerPlayerButton;
    private Button toggleMinimapButton;
    private int centerX = 0;
    private int centerZ = 0;
    private boolean isMinimapMode = false;
    private String hoveredBiome = null;

    public SeedMapScreen() {
        super(Component.translatable("screen.seedmap.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.guiLeft = (this.width - this.mapWidth) / 2;
        this.guiTop = (this.height - this.mapHeight) / 2;
        
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            this.biomeGenerator = new BiomeGenerator(level);
        }
        
        // Add zoom buttons
        int buttonSize = 20;
        this.zoomInButton = new ImageButton(
            this.guiLeft + this.mapWidth + 10,
            this.guiTop + this.mapHeight / 2 - buttonSize,
            buttonSize,
            buttonSize,
            0, 0, buttonSize,
            ZOOM_IN,
            buttonSize, buttonSize,
            button -> zoomLevel = Math.min(zoomLevel + 1, 4)
        );
        
        this.zoomOutButton = new ImageButton(
            this.guiLeft + this.mapWidth + 10,
            this.guiTop + this.mapHeight / 2 + 10,
            buttonSize,
            buttonSize,
            0, 0, buttonSize,
            ZOOM_OUT,
            buttonSize, buttonSize,
            button -> zoomLevel = Math.max(zoomLevel - 1, 1)
        );
        
        // Add center player button
        this.centerPlayerButton = Button.builder(Component.translatable("button.seedmap.center_player"), button -> centerOnPlayer())
            .pos(this.guiLeft + this.mapWidth + 10, this.guiTop + this.mapHeight / 2 + 40)
            .size(100, 20)
            .build();
            
        // Add minimap toggle button
        this.toggleMinimapButton = Button.builder(Component.translatable("button.seedmap.toggle_minimap"), button -> toggleMinimap())
            .pos(this.guiLeft + this.mapWidth + 10, this.guiTop + this.mapHeight / 2 + 70)
            .size(100, 20)
            .build();
        
        addRenderableWidget(zoomInButton);
        addRenderableWidget(zoomOutButton);
        addRenderableWidget(centerPlayerButton);
        addRenderableWidget(toggleMinimapButton);
    }

    private void centerOnPlayer() {
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            BlockPos pos = player.blockPosition();
            centerX = pos.getX();
            centerZ = pos.getZ();
        }
    }

    private void toggleMinimap() {
        isMinimapMode = !isMinimapMode;
        if (isMinimapMode) {
            mapWidth = 128;
            mapHeight = 128;
        } else {
            mapWidth = 256;
            mapHeight = 256;
        }
        init();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        
        // Draw the map background
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        guiGraphics.blit(TEXTURE, this.guiLeft, this.guiTop, 0, 0, this.mapWidth, this.mapHeight);
        
        // Draw biome colors
        if (biomeGenerator != null) {
            int scale = 1 << (zoomLevel - 1);
            int halfWidth = mapWidth / (2 * scale);
            int halfHeight = mapHeight / (2 * scale);
            
            for (int x = 0; x < mapWidth; x++) {
                for (int z = 0; z < mapHeight; z++) {
                    int worldX = centerX + (x - halfWidth) * scale;
                    int worldZ = centerZ + (z - halfHeight) * scale;
                    
                    var biome = biomeGenerator.getBiomeAt(worldX, worldZ);
                    int color = biomeGenerator.getBiomeColor(biome);
                    
                    // Draw a pixel with the biome color
                    guiGraphics.fill(
                        guiLeft + x,
                        guiTop + z,
                        guiLeft + x + 1,
                        guiTop + z + 1,
                        color
                    );
                    
                    // Check if mouse is over this pixel
                    if (mouseX >= guiLeft + x && mouseX < guiLeft + x + 1 &&
                        mouseY >= guiTop + z && mouseY < guiTop + z + 1) {
                        hoveredBiome = biome.location().getPath();
                    }
                }
            }
        }
        
        // Draw player position
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            BlockPos pos = player.blockPosition();
            int playerX = guiLeft + (pos.getX() - centerX) / (1 << (zoomLevel - 1)) + mapWidth / 2;
            int playerZ = guiTop + (pos.getZ() - centerZ) / (1 << (zoomLevel - 1)) + mapHeight / 2;
            
            if (playerX >= guiLeft && playerX < guiLeft + mapWidth &&
                playerZ >= guiTop && playerZ < guiTop + mapHeight) {
                RenderSystem.setShaderTexture(0, PLAYER);
                guiGraphics.blit(PLAYER, playerX - 4, playerZ - 4, 0, 0, 8, 8, 8, 8);
            }
        }
        
        // Draw coordinates
        String coords = String.format("X: %d, Z: %d", centerX, centerZ);
        guiGraphics.drawString(this.font, coords, this.guiLeft, this.guiTop - 20, 0xFFFFFF);
        
        // Draw the title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, this.guiTop - 20, 0xFFFFFF);
        
        // Draw biome tooltip
        if (hoveredBiome != null) {
            String biomeName = hoveredBiome.replace('_', ' ').substring(0, 1).toUpperCase() + 
                             hoveredBiome.replace('_', ' ').substring(1);
            guiGraphics.drawString(this.font, biomeName, mouseX + 10, mouseY + 10, 0xFFFFFF);
        }
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) { // Left click
            centerX -= dragX * zoomLevel;
            centerZ -= dragY * zoomLevel;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
} 