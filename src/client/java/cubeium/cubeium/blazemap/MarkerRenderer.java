package cubeium.cubeium.blazemap;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cubeium.cubeium.Cubeium;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/**
 * Renders markers on the map.
 * Converts world coordinates to screen coordinates and draws icon textures.
 */
public class MarkerRenderer {
    private final Map<MapMarker.MarkerType, Identifier> iconTextures = new ConcurrentHashMap<>();

    // Icon texture paths - with fallbacks to parent gui folder if markers subfolder not populated
    private static final Identifier ORIGIN_ICON = Identifier.of("cubeium", "textures/gui/origin_icon.png");
    private static final Identifier ORIGIN_ICON_FALLBACK = Identifier.of("cubeium", "textures/gui/origin_icon.png");
    
    private static final Identifier PLAYER_ICON = Identifier.of("cubeium", "textures/gui/markers/player_head_icon.png");
    private static final Identifier PLAYER_ICON_FALLBACK = Identifier.of("cubeium", "textures/gui/settings_icon.png"); // Generic fallback
    
    private static final Identifier VILLAGE_ICON = Identifier.of("cubeium", "textures/gui/markers/village_icon.png");
    private static final Identifier VILLAGE_ICON_FALLBACK = Identifier.of("cubeium", "textures/gui/village_icon.png");
    
    private static final Identifier STRONGHOLD_ICON = Identifier.of("cubeium", "textures/gui/markers/stronghold_icon.png");
    private static final Identifier STRONGHOLD_ICON_FALLBACK = Identifier.of("cubeium", "textures/gui/stronghold_icon.png");

    public MarkerRenderer() {
        initializeIconTextures();
    }

    private void initializeIconTextures() {
        iconTextures.put(MapMarker.MarkerType.ORIGIN, ORIGIN_ICON);
        iconTextures.put(MapMarker.MarkerType.PLAYER, PLAYER_ICON);
        iconTextures.put(MapMarker.MarkerType.VILLAGE, VILLAGE_ICON);
        iconTextures.put(MapMarker.MarkerType.STRONGHOLD, STRONGHOLD_ICON);
    }

    /**
     * Render all visible markers on the map.
     */
    public void renderMarkers(DrawContext context, List<MapMarker> markers,
                              int mapX, int mapY, int mapWidth, int mapHeight,
                              int mapCenterX, int mapCenterZ, int zoomLevel) {
        if (markers == null || markers.isEmpty()) {
            return;
        }

        for (MapMarker marker : markers) {
            if (!marker.visible) continue;
            renderMarker(context, marker, mapX, mapY, mapWidth, mapHeight, mapCenterX, mapCenterZ, zoomLevel);
        }
    }

    private void renderMarker(DrawContext context, MapMarker marker, int mapX, int mapY, int mapWidth, int mapHeight,
                             int mapCenterX, int mapCenterZ, int zoomLevel) {
        // Convert world coordinates to screen coordinates
        ScreenPos screenPos = worldToScreen(marker.worldX, marker.worldZ,
                                           mapX, mapY, mapWidth, mapHeight,
                                           mapCenterX, mapCenterZ, zoomLevel);
        if (screenPos == null) return;

        int iconSize = marker.getIconSize();
        int iconX = screenPos.x - iconSize / 2;
        int iconY = screenPos.y - iconSize / 2;

        // Draw icon based on marker type
        if (marker.type == MapMarker.MarkerType.PLAYER) {
            renderPlayerHeadMarker(context, marker, iconX, iconY, iconSize);
        } else {
            renderTextureIcon(context, marker, iconX, iconY, iconSize);
        }

        // Optional: Draw label below marker
        if (marker.label != null && !marker.label.isEmpty() && !marker.label.equals(marker.type.toString())) {
            drawMarkerLabel(context, marker.label, screenPos.x, screenPos.y + iconSize / 2 + 4);
        }
    }

    private void renderPlayerHeadMarker(DrawContext context, MapMarker marker, int iconX, int iconY, int iconSize) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;

            Identifier playerSkin = getPlayerSkinTexture();
            if (playerSkin != null) {
                // Use the 8x8 face region (8,8 to 16,16) and scale it to 16x16
                int sourceSize = 8;
                int drawSize = sourceSize * 2;
                int drawX = iconX + (iconSize - drawSize) / 2;
                int drawY = iconY + (iconSize - drawSize) / 2;
                context.drawTexture(RenderLayer::getGuiTextured, playerSkin,
                    drawX, drawY, 8.0F, 8.0F, drawSize, drawSize,
                    sourceSize, sourceSize, 64, 64);
            } else {
                // Fallback to generic player icon if skin loading fails
                Identifier fallbackIcon = getFallbackIcon(MapMarker.MarkerType.PLAYER);
                if (fallbackIcon != null) {
                    context.drawTexture(RenderLayer::getGuiTextured, fallbackIcon,
                        iconX, iconY, 8.0F, 8.0F, iconSize, iconSize, iconSize, iconSize);
                }
            }
        } catch (Exception e) {
            Cubeium.LOGGER.warn("[MarkerRenderer] Error rendering player head: {}", e.getMessage());
        }
    }

    private Identifier getPlayerSkinTexture() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) {
                return null;
            }

            return client.player.getSkinTextures().texture();
        } catch (Exception e) {
            Cubeium.LOGGER.debug("[MarkerRenderer] Could not read player skin texture: {}", e.getMessage());
            return null;
        }
    }

    private void renderTextureIcon(DrawContext context, MapMarker marker, int iconX, int iconY, int iconSize) {
        Identifier texture = iconTextures.get(marker.type);
        if (texture == null) {
            texture = iconTextures.get(MapMarker.MarkerType.CUSTOM);
        }
        if (texture == null) return;

        try {
            context.drawTexture(RenderLayer::getGuiTextured, texture,
                iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
        } catch (Exception e) {
            // Try fallback icon for this marker type
            Identifier fallbackTexture = getFallbackIcon(marker.type);
            if (fallbackTexture != null && !fallbackTexture.equals(texture)) {
                try {
                    context.drawTexture(RenderLayer::getGuiTextured, fallbackTexture,
                        iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);
                    return;
                } catch (Exception fallbackError) {
                    Cubeium.LOGGER.debug("[MarkerRenderer] Both primary and fallback icons failed for {}", marker.type);
                }
            }
            Cubeium.LOGGER.debug("[MarkerRenderer] Could not render icon for {}: {}", marker.type, e.getMessage());
        }
    }

    private Identifier getFallbackIcon(MapMarker.MarkerType type) {
        return switch (type) {
            case ORIGIN -> ORIGIN_ICON_FALLBACK;
            case PLAYER -> PLAYER_ICON_FALLBACK;
            case VILLAGE -> VILLAGE_ICON_FALLBACK;
            case STRONGHOLD -> STRONGHOLD_ICON_FALLBACK;
            default -> null;
        };
    }

    private void drawMarkerLabel(DrawContext context, String label, int x, int y) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) return;

        int labelWidth = client.textRenderer.getWidth(label);
        int labelLeft = x - labelWidth / 2;

        // Semi-transparent background for readability
        context.fill(labelLeft - 2, y - 1, labelLeft + labelWidth + 2, y + 8, 0xAA000000);
        context.drawText(client.textRenderer, label, labelLeft, y, 0xFFFFFF, false);
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

        int pixelOffsetX = (worldX - viewWorldLeft) / blocksPerPixel;
        int pixelOffsetY = (worldZ - viewWorldTop) / blocksPerPixel;

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
    }

    /**
     * Cleanup resources when screen closes.
     */
    public void shutdown() {
        iconTextures.clear();
    }
}
