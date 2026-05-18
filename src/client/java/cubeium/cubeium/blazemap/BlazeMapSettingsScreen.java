package cubeium.cubeium.blazemap;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

class BlazeMapSettingsScreen extends Screen {

    private static final Identifier SETTINGS_ICON_TEXTURE =
            Identifier.of("cubeium", "textures/gui/settings_icon.png");

    private final Screen parent;
    private final BlazeMapSeedScreen.SeedMapSession session;

    private ButtonWidget floatingTooltipButton;
    private ButtonWidget renderMetricsButton;

    BlazeMapSettingsScreen(Screen parent, BlazeMapSeedScreen.SeedMapSession session) {
        super(Text.literal("Settings"));
        this.parent = parent;
        this.session = session;
    }

    @Override
    protected void init() {
        super.init();

        int panelWidth = 220;
        int panelX = (width - panelWidth) / 2;

        // --- Map Settings > Other ---
        int mapOtherY = 72;
        floatingTooltipButton = ButtonWidget.builder(floatingTooltipLabel(), btn -> {
            session.showFloatingTooltip = !session.showFloatingTooltip;
            btn.setMessage(floatingTooltipLabel());
        }).dimensions(panelX, mapOtherY, panelWidth, 20).build();
        addDrawableChild(floatingTooltipButton);

        // --- Advanced / Debug ---
        int advancedY = mapOtherY + 62;
        renderMetricsButton = ButtonWidget.builder(renderMetricsLabel(), btn -> {
            session.showPerformanceInfo = !session.showPerformanceInfo;
            btn.setMessage(renderMetricsLabel());
        }).dimensions(panelX, advancedY, panelWidth, 20).build();
        addDrawableChild(renderMetricsButton);

        // Done
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), btn -> close())
            .dimensions((width - 100) / 2, height - 36, 100, 20)
            .build());
    }

    private Text floatingTooltipLabel() {
        return Text.literal("Floating Tooltip: " + (session.showFloatingTooltip ? "On" : "Off"));
    }

    private Text renderMetricsLabel() {
        return Text.literal("Render Metrics: " + (session.showPerformanceInfo ? "On" : "Off"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Screen.renderBackgroundTexture(context, MENU_BACKGROUND_TEXTURE, 0, 0, 0.0F, 0.0F, width, height);
        super.render(context, mouseX, mouseY, delta);

        // Title with settings icon
        int titleX = width / 2;
        int titleY = 16;
        context.drawCenteredTextWithShadow(textRenderer, title, titleX, titleY, 0xFFFFFFFF);
        int iconSize = 12;
        int iconX = titleX - textRenderer.getWidth(title) / 2 - iconSize - 5;
        int iconY = titleY + (textRenderer.fontHeight - iconSize) / 2;
        context.drawTexture(RenderLayer::getGuiTextured, SETTINGS_ICON_TEXTURE,
            iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, iconSize, iconSize);

        // Section headers
        if (floatingTooltipButton != null) {
            context.drawText(textRenderer, "Map Settings  \u203a  Other",
                floatingTooltipButton.getX(), floatingTooltipButton.getY() - 14, 0xFFAAAAAA, false);
        }
        if (renderMetricsButton != null) {
            context.drawText(textRenderer, "Advanced / Debug",
                renderMetricsButton.getX(), renderMetricsButton.getY() - 14, 0xFFAAAAAA, false);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Screen.renderBackgroundTexture(context, MENU_BACKGROUND_TEXTURE, 0, 0, 0.0F, 0.0F, width, height);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
