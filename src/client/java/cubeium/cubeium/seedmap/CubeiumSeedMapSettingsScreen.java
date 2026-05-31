package cubeium.cubeium.seedmap;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class CubeiumSeedMapSettingsScreen extends Screen {

    private static final Identifier SETTINGS_ICON_TEXTURE =
            Identifier.of("cubeium", "textures/gui/settings_icon.png");

    private final Screen parent;
    private final CubeiumSeedMapScreen.SeedMapSession session;

    private ButtonWidget floatingTooltipButton;
    private ButtonWidget renderMetricsButton;
    private ButtonWidget teleportMenuButton;
    private ButtonWidget markerLabelsButton;
    private ButtonWidget biomeFilterButton;
    private ButtonWidget preservePanButton;

    public CubeiumSeedMapSettingsScreen(Screen parent, CubeiumSeedMapScreen.SeedMapSession session) {
        super(Text.translatable("cubeium.settings.title"));
        this.parent = parent;
        this.session = session;
    }

    @Override
    protected void init() {
        super.init();

        int panelWidth = 220;
        int panelX = (width - panelWidth) / 2;

        int mapOtherY = 72;
        floatingTooltipButton = ButtonWidget.builder(floatingTooltipLabel(), btn -> {
            session.setShowFloatingTooltip(!session.isShowFloatingTooltip());
            btn.setMessage(floatingTooltipLabel());
            CubeiumSeedMapSettingsStore.savePersistentSettings(session);
        }).dimensions(panelX, mapOtherY, panelWidth, 20).build();
        addDrawableChild(floatingTooltipButton);

        int advancedY = mapOtherY + 62;
        renderMetricsButton = ButtonWidget.builder(renderMetricsLabel(), btn -> {
            session.setShowPerformanceInfo(!session.isShowPerformanceInfo());
            btn.setMessage(renderMetricsLabel());
            CubeiumSeedMapSettingsStore.savePersistentSettings(session);
        }).dimensions(panelX, advancedY, panelWidth, 20).build();
        addDrawableChild(renderMetricsButton);

        int teleportY = advancedY + 26;
        teleportMenuButton = ButtonWidget.builder(teleportLabel(), btn -> {
            session.setEnableTeleportInContextMenu(!session.isEnableTeleportInContextMenu());
            btn.setMessage(teleportLabel());
            CubeiumSeedMapSettingsStore.savePersistentSettings(session);
        }).dimensions(panelX, teleportY, panelWidth, 20).build();
        addDrawableChild(teleportMenuButton);

        int labelsY = teleportY + 26;
        markerLabelsButton = ButtonWidget.builder(markerLabelsLabel(), btn -> {
            session.setShowMarkerLabels(!session.isShowMarkerLabels());
            btn.setMessage(markerLabelsLabel());
            CubeiumSeedMapSettingsStore.savePersistentSettings(session);
        }).dimensions(panelX, labelsY, panelWidth, 20).build();
        addDrawableChild(markerLabelsButton);

        int biomeFilterY = labelsY + 26;
        biomeFilterButton = ButtonWidget.builder(Text.translatable("cubeium.settings.biome_filter"), btn -> openBiomeFilter())
            .dimensions(panelX, biomeFilterY, panelWidth, 20)
            .build();
        addDrawableChild(biomeFilterButton);

        int preservePanY = biomeFilterY + 26;
        preservePanButton = ButtonWidget.builder(preservePanLabel(), btn -> {
            session.setPreservePanOnOpen(!session.isPreservePanOnOpen());
            btn.setMessage(preservePanLabel());
            CubeiumSeedMapSettingsStore.savePersistentSettings(session);
        }).dimensions(panelX, preservePanY, panelWidth, 20).build();
        addDrawableChild(preservePanButton);

        addDrawableChild(ButtonWidget.builder(Text.translatable("cubeium.button.done"), btn -> close())
            .dimensions((width - 100) / 2, height - 36, 100, 20)
            .build());
    }

    private Text floatingTooltipLabel() {
        return Text.translatable("cubeium.settings.floating_tooltip", session.isShowFloatingTooltip() ? Text.translatable("cubeium.state.on") : Text.translatable("cubeium.state.off"));
    }

    private Text teleportLabel() {
        return Text.translatable("cubeium.settings.teleport_menu_item", session.isEnableTeleportInContextMenu() ? Text.translatable("cubeium.state.on") : Text.translatable("cubeium.state.off"));
    }

    private Text renderMetricsLabel() {
        return Text.translatable("cubeium.settings.render_metrics", session.isShowPerformanceInfo() ? Text.translatable("cubeium.state.on") : Text.translatable("cubeium.state.off"));
    }

    private Text markerLabelsLabel() {
        return Text.translatable("cubeium.settings.marker_labels", session.isShowMarkerLabels() ? Text.translatable("cubeium.state.on") : Text.translatable("cubeium.state.off"));
    }

    private void openBiomeFilter() {
        if (client != null) {
            client.setScreen(new CubeiumBiomeFilterScreen(this, session));
        }
    }

    private Text preservePanLabel() {
        return Text.translatable("cubeium.settings.preserve_pan", session.isPreservePanOnOpen() ? Text.translatable("cubeium.state.on") : Text.translatable("cubeium.state.off"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Screen.renderBackgroundTexture(context, MENU_BACKGROUND_TEXTURE, 0, 0, 0.0F, 0.0F, width, height);
        super.render(context, mouseX, mouseY, delta);

        int titleX = width / 2;
        int titleY = 16;
        context.drawCenteredTextWithShadow(textRenderer, title, titleX, titleY, 0xFFFFFFFF);
        int iconSize = 12;
        int iconX = titleX - textRenderer.getWidth(title) / 2 - iconSize - 5;
        int iconY = titleY + (textRenderer.fontHeight - iconSize) / 2;
        context.drawTexture(RenderLayer::getGuiTextured, SETTINGS_ICON_TEXTURE,
            iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);

        if (floatingTooltipButton != null) {
            context.drawText(textRenderer, Text.translatable("cubeium.settings.section_map_other").getString(),
                floatingTooltipButton.getX(), floatingTooltipButton.getY() - 14, 0xFFAAAAAA, false);
        }
        if (renderMetricsButton != null) {
            context.drawText(textRenderer, Text.translatable("cubeium.settings.section_advanced").getString(),
                renderMetricsButton.getX(), renderMetricsButton.getY() - 14, 0xFFAAAAAA, false);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Screen.renderBackgroundTexture(context, MENU_BACKGROUND_TEXTURE, 0, 0, 0.0F, 0.0F, width, height);
    }

    @Override
    public void close() {
        CubeiumSeedMapSettingsStore.savePersistentSettings(session);
        client.setScreen(parent);
    }
}
