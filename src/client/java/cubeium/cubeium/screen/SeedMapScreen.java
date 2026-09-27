package cubeium.cubeium.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;

import com.mojang.blaze3d.platform.InputConstants;

import cubeium.cubeium.config.CubeiumConfig;
import cubeium.cubeium.map.MapView;
import cubeium.cubeium.world.Biomes;
import cubeium.cubeium.world.Dimension;
import cubeium.cubeium.world.StructureKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/** The seed map: seed and dimension on top, the map, and Map/Structures/Biomes tabs below. */
public final class SeedMapScreen extends Screen {
    private static final int PAD = 8;
    private static final int BAR_Y = 20;
    private static final int MAP_Y = 46;
    private static final int BOTTOM_MARGIN = 6;
    private static final long SEED_DEBOUNCE_MS = 300;
    private static final Identifier CENTER_SPRITE = Identifier.fromNamespaceAndPath("cubeium", "icon/center_on_player");
    private static final Identifier SETTINGS_SPRITE = Identifier.fromNamespaceAndPath("cubeium", "icon/settings");
    private static int selectedTab;

    private final MapSession session = MapSession.get();
    private final CubeiumConfig config = CubeiumConfig.get();
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private final List<SwatchCheckbox> biomeBoxes = new ArrayList<>();
    private String worldKey = "";
    private @Nullable EditBox seedBox;
    private @Nullable MapWidget map;
    private @Nullable PanelTabBar tabBar;
    private @Nullable Button showAll;
    private @Nullable String pendingSeedText;
    private long pendingSeedAt;
    private int panelTop;
    /** init() also runs on resize and rebuildWidgets(); world setup happens only once per opening. */
    private boolean opened;

    public SeedMapScreen() {
        super(Component.translatable("cubeium.map.title"));
    }

    @Override
    protected void init() {
        if (!opened) {
            opened = true;
            openWorld();
        }

        int mapWidth = width - 2 * PAD;
        int panelHeight = Math.clamp((height - MAP_Y) * 3 / 10, 64, 120);
        int tabBarY = height - BOTTOM_MARGIN - panelHeight - PanelTabBar.HEIGHT;
        panelTop = tabBarY + PanelTabBar.HEIGHT;
        int mapHeight = Math.max(40, tabBarY - 6 - MAP_Y);

        // Top bar: seed, center on player, dimension ... settings.
        Component seedLabel = Component.translatable("cubeium.map.seed");
        StringWidget label = addRenderableWidget(new StringWidget(PAD, BAR_Y + 6, font.width(seedLabel), 9, seedLabel, font));
        int seedX = label.getRight() + 5;
        seedBox = addRenderableWidget(new EditBox(font, seedX, BAR_Y, Math.min(200, width / 3), 20, seedBox, seedLabel));
        seedBox.setMaxLength(128);
        seedBox.setHint(Component.translatable("cubeium.map.seed_hint"));
        if (seedBox.getValue().isEmpty()) {
            seedBox.setValue(config.seeds.getOrDefault(worldKey, ""));
        }
        seedBox.setResponder(text -> {
            pendingSeedText = text;
            pendingSeedAt = System.currentTimeMillis();
        });

        addRenderableWidget(SpriteIconButton.builder(Component.translatable("cubeium.map.center_on_player"), b -> centerOnPlayer(), true)
                .size(20, 20).sprite(CENTER_SPRITE, 16, 16).withTootip().build()).setPosition(seedBox.getRight() + 4, BAR_Y);

        addRenderableWidget(CycleButton.builder(SeedMapScreen::dimensionName, session.dimension)
                .withValues(Dimension.values()).displayOnlyValue()
                .create(seedBox.getRight() + 28, BAR_Y, 76, 20, Component.translatable("cubeium.map.dimension"),
                        (button, dimension) -> switchDimension(dimension)));

        addRenderableWidget(SpriteIconButton.builder(Component.translatable("cubeium.settings.title"),
                        b -> minecraft.gui.setScreen(new SeedMapSettingsScreen(this)), true)
                .size(20, 20).sprite(SETTINGS_SPRITE, 16, 16).withTootip().build()).setPosition(width - PAD - 20, BAR_Y);

        // Map with zoom buttons and the highlight banner's button on top of it. The overlay buttons
        // take input before the map (which would otherwise start a drag) but are drawn after it.
        Button zoomIn = addWidget(Button.builder(Component.literal("+"), b -> session.view.setZoom(session.view.zoom() - 1))
                .bounds(PAD + mapWidth - 24, MAP_Y + mapHeight - 60, 20, 20).tooltip(Tooltip.create(Component.translatable("cubeium.map.zoom_in"))).build());
        Button zoomOut = addWidget(Button.builder(Component.literal("-"), b -> session.view.setZoom(session.view.zoom() + 1))
                .bounds(PAD + mapWidth - 24, MAP_Y + mapHeight - 38, 20, 20).tooltip(Tooltip.create(Component.translatable("cubeium.map.zoom_out"))).build());
        Component showAllText = Component.translatable("cubeium.biomes.show_all");
        showAll = addWidget(Button.builder(showAllText, b -> setHighlight(false))
                .bounds(0, MAP_Y + mapHeight - 38, font.width(showAllText) + 12, 20).build());
        map = addRenderableWidget(new MapWidget(session, font, this::onClose));
        map.setBounds(PAD, MAP_Y, mapWidth, mapHeight);
        addRenderableOnly(zoomIn);
        addRenderableOnly(zoomOut);
        layoutBanner();

        // Tabs.
        List<Tab> tabs = List.of(mapTab(mapWidth), structuresTab(mapWidth, panelHeight), biomesTab(mapWidth, panelHeight));
        tabBar = addRenderableWidget(PanelTabBar.create(tabManager, PAD, tabBarY, mapWidth, tabs));
        tabManager.setTabArea(new ScreenRectangle(PAD, panelTop + 4, mapWidth, panelHeight - 8));
        tabBar.selectTab(Math.min(selectedTab, tabs.size() - 1), false);
        setInitialFocus(map);
    }

    /** Called on (re)open: resets the view for a new world, fills in the seed, picks the dimension. */
    private void openWorld() {
        String key = currentWorldKey();
        boolean newWorld = !key.equals(session.worldKey);
        worldKey = key;
        session.worldKey = key;
        if (!config.seeds.containsKey(key)) {
            OptionalLong worldSeed = singleplayerSeed();
            if (worldSeed.isPresent()) {
                config.seeds.put(key, Long.toString(worldSeed.getAsLong()));
            }
        }
        if (newWorld || !config.keepMapPosition) {
            session.dimension = MapWidget.defaultDimension();
            session.view.setZoom(MapView.DEFAULT_ZOOM);
            centerOnPlayer();
        }
        applySeed(config.seeds.getOrDefault(key, ""));
    }

    private void applySeed(String text) {
        OptionalLong seed = WorldOptions.parseSeed(text);
        config.seeds.put(worldKey, text);
        session.show(seed.isPresent() ? seed.getAsLong() : null, session.dimension);
    }

    private void switchDimension(Dimension dimension) {
        Dimension previous = session.dimension;
        // Nether coordinates are 1/8 of the Overworld's, like portals.
        if (previous == Dimension.OVERWORLD && dimension == Dimension.NETHER) {
            session.view.center(session.view.centerX() / 8, session.view.centerZ() / 8);
        } else if (previous == Dimension.NETHER && dimension == Dimension.OVERWORLD) {
            session.view.center(session.view.centerX() * 8, session.view.centerZ() * 8);
        }
        session.show(session.seed(), dimension);
        rebuildWidgets(); // structure and biome lists depend on the dimension
    }

    private void centerOnPlayer() {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            session.view.center(0, 0);
            return;
        }
        double scale = coordinateScale(dimensionOf(player), session.dimension);
        session.view.center(player.getX() * scale, player.getZ() * scale);
    }

    private static double coordinateScale(Dimension from, Dimension to) {
        if (from == Dimension.OVERWORLD && to == Dimension.NETHER) return 1 / 8.0;
        if (from == Dimension.NETHER && to == Dimension.OVERWORLD) return 8;
        return 1;
    }

    private Tab mapTab(int panelWidth) {
        GridLayoutTab tab = new GridLayoutTab(Component.translatable("cubeium.tab.map"));
        GridLayout grid = (GridLayout) tab.getLayout();
        grid.rowSpacing(6).columnSpacing(8);
        grid.addChild(CycleButton.onOffBuilder(config.regionGrid).create(0, 0, 150, 20, Component.translatable("cubeium.map.region_grid"),
                (b, v) -> {
                    config.regionGrid = v;
                    config.save();
                }), 0, 0);
        grid.addChild(CycleButton.onOffBuilder(config.coordinateAxes).create(0, 0, 150, 20, Component.translatable("cubeium.map.axes"),
                (b, v) -> {
                    config.coordinateAxes = v;
                    config.save();
                }), 0, 1);

        LinearLayout goTo = LinearLayout.horizontal().spacing(4);
        goTo.defaultCellSetting().alignVerticallyMiddle();
        goTo.addChild(new StringWidget(Component.literal("X"), font));
        EditBox x = goTo.addChild(new EditBox(font, 64, 20, Component.literal("X")));
        goTo.addChild(new StringWidget(Component.literal("Z"), font));
        EditBox z = goTo.addChild(new EditBox(font, 64, 20, Component.literal("Z")));
        x.setValue(Integer.toString((int) Math.floor(session.view.centerX())));
        z.setValue(Integer.toString((int) Math.floor(session.view.centerZ())));
        goTo.addChild(Button.builder(Component.translatable("cubeium.map.go"), b -> {
            try {
                session.view.center(Integer.parseInt(x.getValue().trim()) + 0.5, Integer.parseInt(z.getValue().trim()) + 0.5);
            } catch (NumberFormatException ignored) {
                // leave the view where it is until the fields hold numbers
            }
        }).width(40).build());
        goTo.addChild(Button.builder(Component.translatable("cubeium.map.origin"), b -> session.view.center(0.5, 0.5)).width(60).build());
        grid.addChild(goTo, 1, 0, 1, 2);
        return tab;
    }

    private Tab structuresTab(int panelWidth, int panelHeight) {
        GridLayoutTab tab = new GridLayoutTab(Component.translatable("cubeium.tab.structures"));
        int columns = Math.max(1, Math.min(4, (panelWidth - 20) / 140));
        int columnWidth = (panelWidth - 20) / columns;
        GridLayout list = new GridLayout().rowSpacing(3);
        GridLayout.RowHelper rows = list.createRowHelper(columns);
        for (StructureKind kind : StructureKind.in(session.dimension)) {
            rows.addChild(Checkbox.builder(Component.translatable("cubeium.structure." + kind.key()), font)
                    .maxWidth(columnWidth - 4)
                    .selected(config.structures.contains(kind.key()))
                    .onValueChange((box, selected) -> {
                        config.setStructureEnabled(kind, selected);
                        config.save();
                    })
                    .build());
        }
        ScrollableLayout scroll = new ScrollableLayout(minecraft, list, panelHeight - 12);
        scroll.setMinWidth(panelWidth - 20);
        ((GridLayout) tab.getLayout()).addChild(scroll, 0, 0);
        return tab;
    }

    private Tab biomesTab(int panelWidth, int panelHeight) {
        GridLayoutTab tab = new GridLayoutTab(Component.translatable("cubeium.tab.biomes"));
        GridLayout grid = (GridLayout) tab.getLayout();
        grid.rowSpacing(4);

        LinearLayout actions = LinearLayout.horizontal().spacing(6);
        actions.addChild(CycleButton.onOffBuilder(config.highlightBiomes).create(0, 0, 150, 20,
                Component.translatable("cubeium.biomes.highlight"), (b, v) -> setHighlight(v)));
        List<Integer> ids = Biomes.mapBiomes(MapSession.MC_VERSION, session.dimension);
        actions.addChild(Button.builder(Component.translatable("cubeium.biomes.all"), b -> selectBiomes(ids, true)).width(50).build());
        actions.addChild(Button.builder(Component.translatable("cubeium.biomes.none"), b -> selectBiomes(ids, false)).width(50).build());
        grid.addChild(actions, 0, 0);

        int columns = Math.max(1, Math.min(4, (panelWidth - 20) / 140));
        int columnWidth = (panelWidth - 20) / columns;
        GridLayout list = new GridLayout().rowSpacing(3);
        GridLayout.RowHelper rows = list.createRowHelper(columns);
        biomeBoxes.clear();
        ids.stream()
                .sorted((a, b) -> biomeLabel(a).getString().compareToIgnoreCase(biomeLabel(b).getString()))
                .forEach(id -> {
                    SwatchCheckbox box = new SwatchCheckbox(columnWidth - 4, biomeLabel(id), Biomes.color(id),
                            config.highlightedBiomes.contains(id), selected -> {
                                if (selected) {
                                    config.highlightedBiomes.add(id);
                                } else {
                                    config.highlightedBiomes.remove(id);
                                }
                                config.save();
                                session.refreshPalette();
                            });
                    biomeBoxes.add(box);
                    rows.addChild(box);
                });
        ScrollableLayout scroll = new ScrollableLayout(minecraft, list, panelHeight - 12 - 24);
        scroll.setMinWidth(panelWidth - 20);
        grid.addChild(scroll, 1, 0);
        return tab;
    }

    private static Component biomeLabel(int id) {
        String name = Biomes.name(MapSession.MC_VERSION, id);
        return Component.translatable("biome.minecraft." + name);
    }

    private void selectBiomes(List<Integer> ids, boolean selected) {
        if (selected) {
            config.highlightedBiomes.addAll(ids);
        } else {
            config.highlightedBiomes.removeAll(ids);
        }
        biomeBoxes.forEach(box -> box.setSelected(selected));
        config.save();
        session.refreshPalette();
    }

    private void setHighlight(boolean enabled) {
        config.highlightBiomes = enabled;
        config.save();
        session.refreshPalette();
        if (!enabled) {
            rebuildWidgets(); // the Biomes tab's toggle shows the new state
        }
        layoutBanner();
    }

    private Component bannerText() {
        return Component.translatable("cubeium.biomes.banner", config.highlightedBiomes.size());
    }

    private void layoutBanner() {
        if (showAll != null && map != null) {
            showAll.visible = config.highlightBiomes && session.tiles() != null;
            showAll.setX(map.getX() + 10 + font.width(bannerText()) + 8);
        }
    }

    @Override
    public void tick() {
        if (pendingSeedText != null && System.currentTimeMillis() - pendingSeedAt >= SEED_DEBOUNCE_MS) {
            String text = pendingSeedText;
            pendingSeedText = null;
            applySeed(text);
            config.save();
            layoutBanner();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Backgrounds.screen(graphics, width, height);
        if (map != null) {
            Backgrounds.panelFill(graphics, map.getX(), panelTop, map.getRight(), height - BOTTOM_MARGIN);
            graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, map.getX(), height - BOTTOM_MARGIN - 2, 0, 0, map.getWidth(), 2, 32, 2);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (showAll != null && showAll.visible && map != null) {
            int y = showAll.getY();
            int right = showAll.getRight() + 3;
            graphics.fill(map.getX() + 4, y - 3, right, y + 23, 0xD0000000);
            graphics.outline(map.getX() + 4, y - 3, right - map.getX() - 4, 26, 0xFFFFFF55);
            graphics.text(font, bannerText(), map.getX() + 10, y + 6, 0xFFFFFF55);
            showAll.extractRenderState(graphics, mouseX, mouseY, a);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Ctrl+1/2/3 and Ctrl+Tab switch tabs, as on vanilla's Create World screen.
        if (tabBar != null && tabBar.keyPressed(event)) {
            return true;
        }
        if (!(getFocused() instanceof EditBox) && map != null) {
            double step = 64 * session.view.blocksPerPixel();
            switch (event.key()) {
                case InputConstants.KEY_LEFT -> session.view.center(session.view.centerX() - step, session.view.centerZ());
                case InputConstants.KEY_RIGHT -> session.view.center(session.view.centerX() + step, session.view.centerZ());
                case InputConstants.KEY_UP -> session.view.center(session.view.centerX(), session.view.centerZ() - step);
                case InputConstants.KEY_DOWN -> session.view.center(session.view.centerX(), session.view.centerZ() + step);
                default -> {
                    return super.keyPressed(event);
                }
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void removed() {
        if (tabBar != null) {
            selectedTab = tabBar.getTabs().indexOf(tabManager.getCurrentTab());
        }
        if (pendingSeedText != null) {
            applySeed(pendingSeedText);
        }
        config.save();
    }

    // ---- helpers shared with the map widget ----

    static Dimension dimensionOf(Player player) {
        var dimension = player.level().dimension();
        if (dimension == Level.NETHER) return Dimension.NETHER;
        if (dimension == Level.END) return Dimension.END;
        return Dimension.OVERWORLD;
    }

    private static Component dimensionName(Dimension dimension) {
        return Component.translatable("cubeium.dimension." + dimension.key());
    }

    /** Teleporting is offered only when enabled and the server lets this player run /tp. */
    static boolean canTeleport() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        return CubeiumConfig.get().teleportInMenu && minecraft.player != null && connection != null
                && connection.getCommands().getRoot().getChild("tp") != null;
    }

    static void teleport(Dimension dimension, int x, int z) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || minecraft.player == null) {
            return;
        }
        if (dimensionOf(minecraft.player) == dimension) {
            connection.sendCommand(String.format(Locale.ROOT, "tp @s %d ~ %d", x, z));
        } else {
            connection.sendCommand(String.format(Locale.ROOT, "execute in minecraft:%s run tp @s %d ~ %d", dimension.key(), x, z));
        }
    }

    /** Identifies the current world so each keeps its own seed. */
    private String currentWorldKey() {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            return "sp:" + server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        }
        ServerData data = minecraft.getCurrentServer();
        return data != null ? "mp:" + data.ip : "none";
    }

    private OptionalLong singleplayerSeed() {
        IntegratedServer server = minecraft.getSingleplayerServer();
        return server != null ? OptionalLong.of(server.overworld().getSeed()) : OptionalLong.empty();
    }
}
