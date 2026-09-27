package cubeium.cubeium.ui;

import java.util.OptionalLong;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.world.gen.GeneratorOptions;

/**
 * Seed text field. Seeds are parsed exactly like the vanilla "Create World" screen:
 * a number is used as-is, any other text is hashed with {@link String#hashCode()}.
 */
public class SeedInputWidget extends TextFieldWidget {
    private static final int WIDGET_WIDTH = 200;
    private static final int WIDGET_HEIGHT = 20;

    private final SeedChangeListener listener;

    public interface SeedChangeListener {
        /** Called on every edit; {@code isValid} is false when the field is empty. */
        void onSeedChanged(long seed, boolean isValid);
    }

    public SeedInputWidget(TextRenderer textRenderer, int x, int y, SeedChangeListener listener) {
        super(textRenderer, x, y, WIDGET_WIDTH, WIDGET_HEIGHT, Text.translatable("cubeium.ui.seed"));
        this.listener = listener;
        setMaxLength(128);
        setPlaceholder(Text.translatable("cubeium.ui.seed_placeholder"));
        setChangedListener(this::onTextChanged);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);

        OptionalLong seed = GeneratorOptions.parseSeed(getText());
        if (seed.isPresent()) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.translatable("cubeium.ui.seed_value", Long.toString(seed.getAsLong())),
                getX(), getY() - 12, 0xFFAAAAAA);
        }
    }

    private void onTextChanged(String text) {
        OptionalLong seed = GeneratorOptions.parseSeed(text);
        if (listener != null) {
            listener.onSeedChanged(seed.orElse(0L), seed.isPresent());
        }
    }
}
