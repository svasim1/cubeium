package cubeium.cubeium.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import cubeium.cubeium.config.CubeiumConfig;
import cubeium.cubeium.config.Waypoint;
import cubeium.cubeium.map.MapStructures;
import cubeium.cubeium.map.MapTiles;
import cubeium.cubeium.map.MapView;
import cubeium.cubeium.world.Biomes;
import cubeium.cubeium.world.Dimension;
import cubeium.cubeium.world.SlimeChunks;
import cubeium.cubeium.world.StructureFinder;
import cubeium.cubeium.world.StructureKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/** The interactive biome map: pan by dragging, zoom with the wheel, right-click for a menu. */
final class MapWidget extends AbstractWidget {
    private static final int STRIP_HEIGHT = 12;
    private static final int STRIP_COLOR = 0xB0000000;
    private static final int GRID_COLOR = 0x40000000;
    private static final int REGION = 512;
    private static final int ICON = 16;
    private static final Map<StructureKind, ItemStack> ICONS = icons();
    private static final ItemStack ORIGIN_ICON = new ItemStack(Items.COMPASS);
    static final DyeColor[] WAYPOINT_COLORS = {DyeColor.RED, DyeColor.BLUE, DyeColor.LIME, DyeColor.YELLOW,
            DyeColor.MAGENTA, DyeColor.ORANGE, DyeColor.CYAN, DyeColor.WHITE};
    private static final int SLIME_COLOR = 0x6040E040;
    /** Slime chunks are drawn once a chunk is at least this many GUI pixels wide. */
    private static final double SLIME_MIN_CHUNK_PIXELS = 6;
    private static final int FOUND_COLOR = 0xFFFF5555;

    private final MapSession session;
    private final Font font;
    private final Runnable onContextTeleport;
    private final Runnable onWaypointsChanged;
    private @Nullable ContextMenu menu;
    private boolean dragging;
    private boolean hovering;
    /** The strip keeps showing the last hovered position after the cursor leaves the map. */
    private boolean hasHovered;
    private int hoverX;
    private int hoverZ;

    MapWidget(MapSession session, Font font, Runnable onContextTeleport, Runnable onWaypointsChanged) {
        super(0, 0, 0, 0, Component.translatable("cubeium.map"));
        this.session = session;
        this.font = font;
        this.onContextTeleport = onContextTeleport;
        this.onWaypointsChanged = onWaypointsChanged;
    }

    void setBounds(int x, int y, int width, int height) {
        setRectangle(width, height, x, y);
        menu = null;
    }

    /** Block coordinate under a GUI position. */
    private double worldX(double guiX) {
        return session.view.centerX() + (guiX - (getX() + width / 2.0)) * session.view.blocksPerPixel();
    }

    private double worldZ(double guiY) {
        return session.view.centerZ() + (guiY - (getY() + height / 2.0)) * session.view.blocksPerPixel();
    }

    private int guiX(double worldX) {
        return (int) Math.floor(getX() + width / 2.0 + (worldX - session.view.centerX()) / session.view.blocksPerPixel());
    }

    private int guiY(double worldZ) {
        return (int) Math.floor(getY() + height / 2.0 + (worldZ - session.view.centerZ()) / session.view.blocksPerPixel());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        MapTiles tiles = session.tiles();
        graphics.fill(getX() - 1, getY() - 1, getRight() + 1, getBottom() + 1, 0xFF000000);
        if (tiles == null) {
            graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFF101010);
            graphics.centeredText(font, Component.translatable("cubeium.map.enter_seed"), getX() + width / 2, getY() + height / 2 - 4, 0xFFA0A0A0);
            return;
        }

        tiles.draw(graphics, getX(), getY(), width, height, session.view, Minecraft.getInstance().getWindow().getGuiScale());
        graphics.enableScissor(getX(), getY(), getRight(), getBottom());
        CubeiumConfig config = CubeiumConfig.get();
        if (config.slimeChunks && session.dimension == Dimension.OVERWORLD) {
            drawSlimeChunks(graphics, tiles.world().seed());
        }
        if (config.regionGrid) {
            drawGrid(graphics);
        }
        List<StructureFinder.Found> markers = drawMarkers(graphics, config);
        List<Waypoint> waypoints = drawWaypoints(graphics);
        drawFound(graphics);
        drawPlayer(graphics);

        hovering = isMouseOver(mouseX, mouseY) && (menu == null || !menu.contains(mouseX, mouseY));
        if (hovering) {
            hasHovered = true;
            hoverX = (int) Math.floor(worldX(mouseX));
            hoverZ = (int) Math.floor(worldZ(mouseY));
        }
        drawStrip(graphics, tiles);
        if (config.coordinateAxes) {
            drawAxes(graphics);
        }
        if (config.renderMetrics) {
            drawMetrics(graphics, tiles);
        }
        graphics.disableScissor();

        if (menu != null) {
            menu.draw(graphics, font, mouseX, mouseY);
        } else if (hovering && !dragging) {
            StructureFinder.Found near = markerNear(markers, mouseX, mouseY);
            Waypoint waypoint = waypointNear(waypoints, mouseX, mouseY);
            if (waypoint != null) {
                graphics.setTooltipForNextFrame(font, Component.literal(waypoint.name)
                        .append(Component.literal("  " + waypoint.x + ", " + waypoint.z).withStyle(s -> s.withColor(0xFFFF55))), mouseX, mouseY);
            } else if (near != null) {
                graphics.setTooltipForNextFrame(font, Component.translatable("cubeium.structure." + near.kind().key())
                        .append(Component.literal("  " + near.x() + ", " + near.z()).withStyle(s -> s.withColor(0xFFFF55))), mouseX, mouseY);
            } else if (config.floatingTooltip) {
                graphics.setTooltipForNextFrame(font, biomeName(tiles).copy()
                        .append(Component.literal("  " + hoverX + ", " + hoverZ).withStyle(s -> s.withColor(0xFFFF55))), mouseX, mouseY);
            }
        }
    }

    private void drawGrid(GuiGraphicsExtractor graphics) {
        double bpp = session.view.blocksPerPixel();
        if (REGION / bpp < 8) {
            return;
        }
        long x0 = Math.floorDiv((long) worldX(getX()), REGION) * REGION;
        for (long wx = x0; guiX(wx) < getRight(); wx += REGION) {
            graphics.fill(guiX(wx), getY(), guiX(wx) + 1, getBottom(), GRID_COLOR);
        }
        long z0 = Math.floorDiv((long) worldZ(getY()), REGION) * REGION;
        for (long wz = z0; guiY(wz) < getBottom(); wz += REGION) {
            graphics.fill(getX(), guiY(wz), getRight(), guiY(wz) + 1, GRID_COLOR);
        }
    }

    private List<StructureFinder.Found> drawMarkers(GuiGraphicsExtractor graphics, CubeiumConfig config) {
        MapStructures structures = session.structures();
        List<StructureFinder.Found> shown = new ArrayList<>();
        if (structures == null) {
            return shown;
        }
        double bpp = session.view.blocksPerPixel();
        structures.update(config.enabledStructures(), worldX(getX()), worldZ(getY()), worldX(getRight()), worldZ(getBottom()), bpp);

        drawIcon(graphics, ORIGIN_ICON, 0, 0, config.markerLabels ? Component.translatable("cubeium.map.origin") : null);
        for (StructureFinder.Found found : structures.found()) {
            if (!config.structures.contains(found.kind().key()) || !structures.visibleAt(found.kind(), bpp)) {
                continue;
            }
            int x = guiX(found.x()), y = guiY(found.z());
            if (x < getX() - ICON || x > getRight() + ICON || y < getY() - ICON || y > getBottom() + ICON) {
                continue;
            }
            shown.add(found);
            drawIcon(graphics, ICONS.get(found.kind()), found.x(), found.z(),
                    config.markerLabels ? Component.translatable("cubeium.structure." + found.kind().key()) : null);
        }
        return shown;
    }

    private void drawIcon(GuiGraphicsExtractor graphics, ItemStack icon, int worldX, int worldZ, @Nullable Component label) {
        int x = guiX(worldX), y = guiY(worldZ);
        graphics.fakeItem(icon, x - ICON / 2, y - ICON / 2);
        if (label != null) {
            int w = font.width(label);
            graphics.fill(x - w / 2 - 2, y + ICON / 2, x + w / 2 + 2, y + ICON / 2 + 10, 0xA0000000);
            graphics.centeredText(font, label, x, y + ICON / 2 + 1, 0xFFFFFFFF);
        }
    }

    private void drawSlimeChunks(GuiGraphicsExtractor graphics, long seed) {
        double bpp = session.view.blocksPerPixel();
        if (16 / bpp < SLIME_MIN_CHUNK_PIXELS) {
            return;
        }
        int cx0 = Math.floorDiv((int) Math.floor(worldX(getX())), 16), cx1 = Math.floorDiv((int) Math.ceil(worldX(getRight())), 16);
        int cz0 = Math.floorDiv((int) Math.floor(worldZ(getY())), 16), cz1 = Math.floorDiv((int) Math.ceil(worldZ(getBottom())), 16);
        for (int cz = cz0; cz <= cz1; cz++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                if (SlimeChunks.isSlimeChunk(seed, cx, cz)) {
                    graphics.fill(guiX(cx * 16), guiY(cz * 16), guiX(cx * 16 + 16), guiY(cz * 16 + 16), SLIME_COLOR);
                }
            }
        }
    }

    private List<Waypoint> drawWaypoints(GuiGraphicsExtractor graphics) {
        List<Waypoint> shown = new ArrayList<>();
        if (session.worldKey == null) {
            return shown;
        }
        for (Waypoint waypoint : CubeiumConfig.get().waypoints(session.worldKey)) {
            if (!session.dimension.key().equals(waypoint.dimension)) {
                continue;
            }
            shown.add(waypoint);
            drawIcon(graphics, waypointIcon(waypoint), waypoint.x, waypoint.z, Component.literal(waypoint.name));
        }
        return shown;
    }

    static ItemStack waypointIcon(Waypoint waypoint) {
        return new ItemStack(Items.BANNER.pick(WAYPOINT_COLORS[Math.floorMod(waypoint.color, WAYPOINT_COLORS.length)]));
    }

    private void drawFound(GuiGraphicsExtractor graphics) {
        MapSession.FoundTarget found = session.found;
        if (found == null) {
            return;
        }
        int x = guiX(found.x()), y = guiY(found.z());
        graphics.outline(x - 10, y - 10, 20, 20, FOUND_COLOR);
        graphics.outline(x - 11, y - 11, 22, 22, 0xFF000000);
    }

    private @Nullable Waypoint waypointNear(List<Waypoint> waypoints, double mouseX, double mouseY) {
        for (Waypoint waypoint : waypoints) {
            if (Math.abs(guiX(waypoint.x) - mouseX) <= ICON / 2 && Math.abs(guiY(waypoint.z) - mouseY) <= ICON / 2) {
                return waypoint;
            }
        }
        return null;
    }

    private StructureFinder.@Nullable Found markerNear(List<StructureFinder.Found> markers, int mouseX, int mouseY) {
        for (StructureFinder.Found found : markers) {
            if (Math.abs(guiX(found.x()) - mouseX) <= ICON / 2 && Math.abs(guiY(found.z()) - mouseY) <= ICON / 2) {
                return found;
            }
        }
        return null;
    }

    private void drawPlayer(GuiGraphicsExtractor graphics) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || SeedMapScreen.dimensionOf(player) != session.dimension) {
            return;
        }
        int x = guiX(player.getX()), y = guiY(player.getZ());
        graphics.fill(x - 5, y - 5, x + 5, y + 5, 0xFF000000);
        PlayerFaceExtractor.extractRenderState(graphics, player.getSkin(), x - 4, y - 4, 8);
    }

    private Component biomeName(MapTiles tiles) {
        int id = tiles.cachedBiomeAt(hoverX, hoverZ);
        if (id < 0) {
            id = tiles.world().biomeAt(hoverX, hoverZ);
        }
        String name = Biomes.name(MapSession.MC_VERSION, id);
        return name == null ? Component.literal("?") : Component.translatable("biome.minecraft." + name);
    }

    private void drawStrip(GuiGraphicsExtractor graphics, MapTiles tiles) {
        graphics.fill(getX(), getY(), getRight(), getY() + STRIP_HEIGHT, STRIP_COLOR);
        if (session.searching) {
            graphics.text(font, Component.translatable("cubeium.find.searching"), getX() + 4, getY() + 2, 0xFFA0A0A0);
        } else if (session.found != null) {
            MapSession.FoundTarget found = session.found;
            graphics.text(font, Component.translatable("cubeium.find.result", found.label(), found.x(), found.z(), found.distance()),
                    getX() + 4, getY() + 2, 0xFFFF7777);
        }
        if (!hasHovered) {
            return;
        }
        Component coords = Component.literal("X: " + hoverX + "  Z: " + hoverZ);
        int coordsX = getRight() - 4 - font.width(coords);
        graphics.text(font, coords, coordsX, getY() + 2, 0xFFFFFF55);

        int id = tiles.cachedBiomeAt(hoverX, hoverZ);
        if (id < 0) {
            id = tiles.world().biomeAt(hoverX, hoverZ);
        }
        Component name = biomeName(tiles);
        int nameX = coordsX - 12 - font.width(name);
        graphics.text(font, name, nameX, getY() + 2, 0xFFFFFFFF);
        graphics.fill(nameX - 11, getY() + 2, nameX - 3, getY() + 10, 0xFF000000);
        graphics.fill(nameX - 10, getY() + 3, nameX - 4, getY() + 9, Biomes.color(id));
    }

    private void drawAxes(GuiGraphicsExtractor graphics) {
        double bpp = session.view.blocksPerPixel();
        int step = axisStep(bpp);
        long x0 = Math.floorDiv((long) worldX(getX()), step) * step + step;
        for (long wx = x0; guiX(wx) < getRight() - 30; wx += step) {
            label(graphics, String.valueOf(wx), guiX(wx), getBottom() - 10, true);
        }
        long z0 = Math.floorDiv((long) worldZ(getY() + STRIP_HEIGHT), step) * step + step;
        for (long wz = z0; guiY(wz) < getBottom() - 14; wz += step) {
            label(graphics, String.valueOf(wz), getRight() - 2, guiY(wz) - 4, false);
        }
    }

    /** A round axis step giving roughly 100 GUI pixels between labels. */
    private static int axisStep(double bpp) {
        double target = bpp * 100;
        for (int step : new int[] {16, 32, 64, 128, 256, 500, 1000, 2500, 5000, 10000, 25000, 50000, 100000}) {
            if (step >= target) {
                return step;
            }
        }
        return 250000;
    }

    private void label(GuiGraphicsExtractor graphics, String text, int anchorX, int y, boolean centered) {
        int w = font.width(text);
        int x = centered ? anchorX - w / 2 : anchorX - w - 2;
        graphics.fill(x - 2, y - 1, x + w + 1, y + 9, 0x90000000);
        graphics.text(font, text, x, y, 0xFFE0E0E0);
    }

    private void drawMetrics(GuiGraphicsExtractor graphics, MapTiles tiles) {
        Minecraft minecraft = Minecraft.getInstance();
        String[] lines = {
            minecraft.getFps() + " fps",
            "tiles: " + tiles.textureCount() + " ready, " + tiles.pendingCount() + " pending",
            String.format("tile generation: %.1f ms", tiles.averageGenerationMillis()),
            "zoom: " + session.view.blocksPerPixel() + " blocks/px"
        };
        int y = getBottom() - 14 - lines.length * 10;
        for (String line : lines) {
            graphics.fill(getX() + 2, y - 1, getX() + 6 + font.width(line), y + 9, 0x90000000);
            graphics.text(font, line, getX() + 4, y, 0xFF55FF55);
            y += 10;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu != null) {
            ContextMenu open = menu;
            menu = null;
            if (open.click(event.x(), event.y())) {
                return true;
            }
        }
        if (session.tiles() == null || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = true;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            openMenu((int) event.x(), (int) event.y());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            session.view.panPixels(dx, dy);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (session.tiles() == null || !isMouseOver(x, y) || scrollY == 0) {
            return false;
        }
        menu = null;
        session.view.zoomAround(scrollY > 0 ? -1 : 1, x - (getX() + width / 2.0), y - (getY() + height / 2.0));
        return true;
    }

    private void openMenu(int x, int y) {
        int wx = (int) Math.floor(worldX(x)), wz = (int) Math.floor(worldZ(y));
        List<ContextMenu.Item> items = new ArrayList<>();
        items.add(new ContextMenu.Item(Component.translatable("cubeium.menu.copy"),
                () -> Minecraft.getInstance().keyboardHandler.setClipboard(wx + " " + wz)));
        items.add(new ContextMenu.Item(Component.translatable("cubeium.menu.center"), () -> session.view.center(wx + 0.5, wz + 0.5)));
        List<Waypoint> waypoints = CubeiumConfig.get().waypoints(session.worldKey == null ? "none" : session.worldKey);
        Waypoint near = waypointNear(waypoints.stream().filter(w -> session.dimension.key().equals(w.dimension)).toList(), x, y);
        if (near != null) {
            items.add(new ContextMenu.Item(Component.translatable("cubeium.menu.remove_waypoint", near.name), () -> {
                waypoints.remove(near);
                CubeiumConfig.get().save();
                onWaypointsChanged.run();
            }));
        } else {
            items.add(new ContextMenu.Item(Component.translatable("cubeium.menu.add_waypoint"), () -> {
                String name = Component.translatable("cubeium.waypoints.default_name", waypoints.size() + 1).getString();
                waypoints.add(new Waypoint(name, wx, wz, session.dimension.key(), waypoints.size()));
                CubeiumConfig.get().save();
                onWaypointsChanged.run();
            }));
        }
        if (SeedMapScreen.canTeleport()) {
            items.add(new ContextMenu.Item(Component.translatable("cubeium.menu.teleport"), () -> {
                SeedMapScreen.teleport(session.dimension, wx, wz);
                onContextTeleport.run();
            }));
        }
        Component header = Component.literal("X: " + wx + "  Z: " + wz);
        menu = ContextMenu.open(header, items, x, y, getRight(), getBottom(), font);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private static Map<StructureKind, ItemStack> icons() {
        Map<StructureKind, ItemStack> icons = new EnumMap<>(StructureKind.class);
        icons.put(StructureKind.VILLAGE, new ItemStack(Items.BELL));
        icons.put(StructureKind.STRONGHOLD, new ItemStack(Items.ENDER_EYE));
        icons.put(StructureKind.PILLAGER_OUTPOST, new ItemStack(Items.CROSSBOW));
        icons.put(StructureKind.OCEAN_MONUMENT, new ItemStack(Items.PRISMARINE));
        icons.put(StructureKind.WOODLAND_MANSION, new ItemStack(Items.TOTEM_OF_UNDYING));
        icons.put(StructureKind.RUINED_PORTAL, new ItemStack(Items.CRYING_OBSIDIAN));
        icons.put(StructureKind.DESERT_PYRAMID, new ItemStack(Items.CHISELED_SANDSTONE));
        icons.put(StructureKind.JUNGLE_TEMPLE, new ItemStack(Items.MOSSY_COBBLESTONE));
        icons.put(StructureKind.SWAMP_HUT, new ItemStack(Items.CAULDRON));
        icons.put(StructureKind.IGLOO, new ItemStack(Items.SNOW_BLOCK));
        icons.put(StructureKind.SHIPWRECK, new ItemStack(Items.OAK_BOAT));
        icons.put(StructureKind.OCEAN_RUIN, new ItemStack(Items.CRACKED_STONE_BRICKS));
        icons.put(StructureKind.BURIED_TREASURE, new ItemStack(Items.HEART_OF_THE_SEA));
        icons.put(StructureKind.ANCIENT_CITY, new ItemStack(Items.ECHO_SHARD));
        icons.put(StructureKind.TRAIL_RUINS, new ItemStack(Items.BRUSH));
        icons.put(StructureKind.TRIAL_CHAMBERS, new ItemStack(Items.TRIAL_KEY));
        icons.put(StructureKind.ABANDONED_CAMP, new ItemStack(Items.CAMPFIRE));
        icons.put(StructureKind.MINESHAFT, new ItemStack(Items.RAIL));
        icons.put(StructureKind.NETHER_FORTRESS, new ItemStack(Items.NETHER_BRICKS));
        icons.put(StructureKind.BASTION_REMNANT, new ItemStack(Items.GILDED_BLACKSTONE));
        icons.put(StructureKind.RUINED_PORTAL_NETHER, new ItemStack(Items.CRYING_OBSIDIAN));
        icons.put(StructureKind.END_CITY, new ItemStack(Items.PURPUR_BLOCK));
        icons.put(StructureKind.END_CITY_SHIP, new ItemStack(Items.ELYTRA));
        return icons;
    }

    static ItemStack icon(StructureKind kind) {
        return ICONS.get(kind);
    }

    /** Dimension shown as the default when the map opens; mirrors the player's. */
    static Dimension defaultDimension() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? Dimension.OVERWORLD : SeedMapScreen.dimensionOf(player);
    }
}
