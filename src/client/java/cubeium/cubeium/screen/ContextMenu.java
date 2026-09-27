package cubeium.cubeium.screen;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;

/** The map's right-click menu, drawn in the vanilla tooltip style. */
final class ContextMenu {
    record Item(Component label, Runnable action) {
    }

    private static final int ROW = 12;
    private static final int HEADER_GAP = 4;

    private final Component header;
    private final List<Item> items;
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private ContextMenu(Component header, List<Item> items, int x, int y, int width, int height) {
        this.header = header;
        this.items = items;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** Opens at the cursor, shifted to stay inside (maxX, maxY). */
    static ContextMenu open(Component header, List<Item> items, int mouseX, int mouseY, int maxX, int maxY, Font font) {
        int width = font.width(header);
        for (Item item : items) {
            width = Math.max(width, font.width(item.label()) + 8);
        }
        int height = ROW + HEADER_GAP + items.size() * ROW;
        int x = Math.min(mouseX + 6, maxX - width - 8);
        int y = Math.min(mouseY + 6, maxY - height - 8);
        return new ContextMenu(header, items, x, y, width, height);
    }

    boolean contains(double mouseX, double mouseY) {
        return mouseX >= x - 4 && mouseX < x + width + 4 && mouseY >= y - 4 && mouseY < y + height + 4;
    }

    void draw(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        graphics.nextStratum();
        TooltipRenderUtil.extractTooltipBackground(graphics, x, y, width, height, null);
        graphics.text(font, header, x, y + 1, 0xFFFFFF55);
        for (int i = 0; i < items.size(); i++) {
            int rowY = rowY(i);
            boolean hovered = mouseX >= x - 2 && mouseX < x + width + 2 && mouseY >= rowY - 2 && mouseY < rowY + ROW - 2;
            if (hovered) {
                graphics.fill(x - 2, rowY - 2, x + width + 2, rowY + ROW - 2, 0x40FFFFFF);
            }
            graphics.text(font, items.get(i).label(), x + 4, rowY, hovered ? 0xFFFFFFFF : 0xFFE0E0E0);
        }
    }

    /** Runs the clicked item; returns whether the click was inside the menu. */
    boolean click(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            int rowY = rowY(i);
            if (mouseY >= rowY - 2 && mouseY < rowY + ROW - 2) {
                AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
                items.get(i).action().run();
                break;
            }
        }
        return true;
    }

    private int rowY(int index) {
        return y + ROW + HEADER_GAP + index * ROW;
    }
}
