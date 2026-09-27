package cubeium.cubeium.seedmap;

import java.util.List;
import java.util.Map;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/**
 * Renders markers on the map.
 * Converts world coordinates to screen coordinates and draws icon textures.
 */
public class CubeiumMarkerRenderer {
    // A missing texture renders as the purple/black placeholder rather than throwing,
    // so every marker type must point at a texture that actually ships with the mod.
    private static final Map<CubeiumMapMarker.MarkerType, Identifier> ICON_TEXTURES = Map.of(
        CubeiumMapMarker.MarkerType.ORIGIN, Identifier.of("cubeium", "textures/gui/origin_icon.png"),
        CubeiumMapMarker.MarkerType.VILLAGE, Identifier.of("cubeium", "textures/gui/village_icon.png"),
        CubeiumMapMarker.MarkerType.STRONGHOLD, Identifier.of("cubeium", "textures/gui/stronghold_icon.png")
    );

    /**
     * Render all visible markers on the map.
     */
    public void renderMarkers(DrawContext context, List<CubeiumMapMarker> markers,
                              int mapX, int mapY, int mapWidth, int mapHeight,
                              int mapCenterX, int mapCenterZ, int zoomLevel,
                              boolean showLabels) {
        if (markers == null || markers.isEmpty()) {
            return;
        }

        for (CubeiumMapMarker marker : markers) {
            if (!marker.visible) continue;
            renderMarker(context, marker, mapX, mapY, mapWidth, mapHeight, mapCenterX, mapCenterZ, zoomLevel, showLabels);
        }
    }

    private void renderMarker(DrawContext context, CubeiumMapMarker marker, int mapX, int mapY, int mapWidth, int mapHeight,
                             int mapCenterX, int mapCenterZ, int zoomLevel, boolean showLabels) {
        // Convert world coordinates to screen coordinates
        ScreenPos screenPos = worldToScreen(marker.worldX, marker.worldZ,
                                           mapX, mapY, mapWidth, mapHeight,
                                           mapCenterX, mapCenterZ, zoomLevel);
        if (screenPos == null) return;

        int iconSize = marker.getIconSize();
        int iconX = screenPos.x - iconSize / 2;
        int iconY = screenPos.y - iconSize / 2;

        // Draw icon based on marker type
        if (marker.type == CubeiumMapMarker.MarkerType.PLAYER) {
            renderPlayerHeadMarker(context, iconX, iconY, iconSize);
        } else {
            renderTextureIcon(context, marker, iconX, iconY, iconSize);
        }

        // Optional: Draw label below marker
        if (showLabels && marker.label != null && !marker.label.isEmpty() && !marker.label.equals(marker.type.toString())) {
            drawMarkerLabel(context, marker.label, screenPos.x, screenPos.y + iconSize / 2 + 4);
        }
    }

    private void renderPlayerHeadMarker(DrawContext context, int iconX, int iconY, int iconSize) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // The face is the 8x8 region at (8, 8) of the 64x64 skin; draw it at 2x.
        Identifier skin = client.player.getSkinTextures().texture();
        int drawSize = 16;
        int drawX = iconX + (iconSize - drawSize) / 2;
        int drawY = iconY + (iconSize - drawSize) / 2;
        context.drawTexture(RenderLayer::getGuiTextured, skin, drawX, drawY, 8.0F, 8.0F, drawSize, drawSize, 8, 8, 64, 64);
    }

    private void renderTextureIcon(DrawContext context, CubeiumMapMarker marker, int iconX, int iconY, int iconSize) {
        Identifier texture = ICON_TEXTURES.get(marker.type);
        if (texture == null) return;
        context.drawTexture(RenderLayer::getGuiTextured, texture, iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
    }

    private void drawMarkerLabel(DrawContext context, String label, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;

        int labelWidth = client.textRenderer.getWidth(label);
        int labelLeft = x - labelWidth / 2;

        // Semi-transparent background for readability
        context.fill(labelLeft - 2, y - 1, labelLeft + labelWidth + 2, y + 8, 0xAA000000);
        context.drawText(client.textRenderer, label, labelLeft, y, 0xFFFFFFFF, false);
    }

    /**
     * Convert world coordinates to screen coordinates.
     * Reuses same math as hover sampling for consistency.
     */
    private ScreenPos worldToScreen(int worldX, int worldZ,
                                    int mapX, int mapY, int mapWidth, int mapHeight,
                                    int mapCenterX, int mapCenterZ, int zoomLevel) {
        int innerWidth = mapWidth - 2;
        int innerHeight = mapHeight - 2;
        int blocksPerPixel = zoomLevel;

        int viewWorldLeft = mapCenterX - (innerWidth * blocksPerPixel) / 2;
        int viewWorldTop = mapCenterZ - (innerHeight * blocksPerPixel) / 2;

        int pixelOffsetX = Math.floorDiv(worldX - viewWorldLeft, blocksPerPixel);
        int pixelOffsetY = Math.floorDiv(worldZ - viewWorldTop, blocksPerPixel);

        int screenX = mapX + 1 + pixelOffsetX;
        int screenY = mapY + 1 + pixelOffsetY;

        // Check if marker is within visible map area
        if (screenX < mapX + 1 || screenX >= mapX + mapWidth - 1 ||
            screenY < mapY + 1 || screenY >= mapY + mapHeight - 1) {
            return null;
        }

        return new ScreenPos(screenX, screenY);
    }

    private static class ScreenPos {
        int x, y;
        ScreenPos(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }}
