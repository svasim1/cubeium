package cubeium.cubeium.screen;

import java.util.function.Consumer;
import java.util.function.Supplier;

import cubeium.cubeium.config.CubeiumConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/** Cubeium's settings, laid out like the vanilla Options screen. */
final class SeedMapSettingsScreen extends Screen {
    private final Screen parent;
    private final CubeiumConfig config = CubeiumConfig.get();
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);

    SeedMapSettingsScreen(Screen parent) {
        super(Component.translatable("cubeium.settings.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        layout.addTitleHeader(title, font);
        GridLayout grid = new GridLayout().columnSpacing(10).rowSpacing(4);
        GridLayout.RowHelper rows = grid.createRowHelper(2);
        rows.addChild(toggle("dark_mode", () -> config.darkMode, v -> config.darkMode = v));
        rows.addChild(toggle("floating_tooltip", () -> config.floatingTooltip, v -> config.floatingTooltip = v));
        rows.addChild(toggle("marker_labels", () -> config.markerLabels, v -> config.markerLabels = v));
        rows.addChild(toggle("teleport", () -> config.teleportInMenu, v -> config.teleportInMenu = v));
        rows.addChild(toggle("keep_position", () -> config.keepMapPosition, v -> config.keepMapPosition = v));
        rows.addChild(toggle("render_metrics", () -> config.renderMetrics, v -> config.renderMetrics = v));
        layout.addToContents(grid);
        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build());
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    private CycleButton<Boolean> toggle(String key, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        Component description = Component.translatable("cubeium.settings." + key + ".tooltip");
        return CycleButton.onOffBuilder(getter.get())
                .withTooltip(value -> Tooltip.create(description))
                .create(0, 0, 150, 20, Component.translatable("cubeium.settings." + key), (button, value) -> {
                    setter.accept(value);
                    config.save();
                });
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Backgrounds.screen(graphics, width, height);
        int headerBottom = layout.getHeaderHeight();
        int footerTop = height - layout.getFooterHeight();
        graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, 0, headerBottom - 2, 0, 0, width, 2, 32, 2);
        graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, 0, footerTop, 0, 0, width, 2, 32, 2);
    }

    @Override
    public void onClose() {
        config.save();
        minecraft.gui.setScreen(parent);
    }
}
