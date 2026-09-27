package cubeium.cubeium.screen;

import java.util.List;

import com.google.common.collect.ImmutableList;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Vanilla tabs (as on the Create World screen) sitting on top of a panel instead of the screen
 * header: the selected tab is backed by the panel's dirt so it merges into the panel below.
 */
final class PanelTabBar extends TabNavigationBar {
    static final int HEIGHT = 24;
    private static final int TAB_WIDTH = 100;

    private PanelTabBar(int x, int y, int width, TabManager tabManager, ImmutableList<TabButton> buttons, ImmutableList<Tab> tabs) {
        super(x, y, width, HEIGHT, tabManager, buttons, tabs);
    }

    static PanelTabBar create(TabManager tabManager, int x, int y, int width, List<Tab> tabs) {
        ImmutableList.Builder<TabButton> buttons = ImmutableList.builder();
        for (Tab tab : tabs) {
            buttons.add(new PanelTabButton(tabManager, tab));
        }
        PanelTabBar bar = new PanelTabBar(x, y, width, tabManager, buttons.build(), ImmutableList.copyOf(tabs));
        bar.arrangeElements(width);
        return bar;
    }

    @Override
    public void arrangeElements(int width) {
        this.width = width;
        int tabsWidth = tabButtons.size() * TAB_WIDTH;
        layout.setPosition(getX() + (width - tabsWidth) / 2, getY());
        layout.arrangeElements();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int lineY = getY() + HEIGHT - 2;
        int tabsLeft = tabButtons.getFirst().getX(), tabsRight = tabButtons.getLast().getRight();
        graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, getX(), lineY, 0, 0, tabsLeft - getX(), 2, 32, 2);
        graphics.blit(RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, tabsRight, lineY, 0, 0, getRight() - tabsRight, 2, 32, 2);
        super.extractWidgetRenderState(graphics, mouseX, mouseY, a);
    }

    private static final class PanelTabButton extends MenuTabBar.MenuTabButton {
        PanelTabButton(TabManager tabManager, Tab tab) {
            super(tabManager, tab, TAB_WIDTH, HEIGHT);
        }

        @Override
        protected void renderMenuBackground(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
            Backgrounds.panelFill(graphics, x0, y0, x1, y1);
        }
    }
}
