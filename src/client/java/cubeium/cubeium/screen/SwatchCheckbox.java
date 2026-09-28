package cubeium.cubeium.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/** A vanilla-looking checkbox with a color swatch between the box and its label (biome list). */
final class SwatchCheckbox extends AbstractButton {
    private static final Identifier BOX = Identifier.withDefaultNamespace("widget/checkbox");
    private static final Identifier BOX_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/checkbox_highlighted");
    private static final Identifier BOX_SELECTED = Identifier.withDefaultNamespace("widget/checkbox_selected");
    private static final Identifier BOX_SELECTED_HIGHLIGHTED = Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted");
    private static final int BOX_SIZE = 17; // vanilla Checkbox.getBoxSize
    private static final int SWATCH = 9;
    private static final int GAP = 4;

    interface OnChange {
        void changed(boolean selected);
    }

    private final int color;
    private final OnChange onChange;
    private boolean selected;

    SwatchCheckbox(int width, Component label, int color, boolean selected, OnChange onChange) {
        super(0, 0, width, BOX_SIZE, label);
        this.color = color;
        this.selected = selected;
        this.onChange = onChange;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        selected = !selected;
        onChange.changed(selected);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Identifier sprite = selected
                ? (isHoveredOrFocused() ? BOX_SELECTED_HIGHLIGHTED : BOX_SELECTED)
                : (isHoveredOrFocused() ? BOX_HIGHLIGHTED : BOX);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), BOX_SIZE, BOX_SIZE, ARGB.white(alpha));

        int swatchX = getX() + BOX_SIZE + GAP, swatchY = getY() + (BOX_SIZE - SWATCH) / 2;
        graphics.fill(swatchX, swatchY, swatchX + SWATCH, swatchY + SWATCH, 0xFF000000);
        graphics.fill(swatchX + 1, swatchY + 1, swatchX + SWATCH - 1, swatchY + SWATCH - 1, color);

        Font font = Minecraft.getInstance().font;
        int textX = swatchX + SWATCH + GAP;
        // Centered on the label's own midpoint = left-aligned when it fits; scrolls when too long.
        int centerX = Math.min(textX + font.width(getMessage()) / 2, (textX + getRight()) / 2);
        graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.notClickable(isHovered()))
                .acceptScrolling(getMessage(), centerX, textX, getRight(), getY(), getBottom());
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, createNarrationMessage());
        if (active) {
            output.add(NarratedElementType.USAGE, Component.translatable(isFocused()
                    ? (selected ? "narration.checkbox.usage.focused.uncheck" : "narration.checkbox.usage.focused.check")
                    : (selected ? "narration.checkbox.usage.hovered.uncheck" : "narration.checkbox.usage.hovered.check")));
        }
    }
}
