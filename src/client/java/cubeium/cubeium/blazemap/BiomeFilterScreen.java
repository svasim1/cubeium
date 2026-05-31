package cubeium.cubeium.blazemap;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

class BiomeFilterScreen extends Screen {
    private static final int TOP_BUTTON_WIDTH = 74;
    private static final int TOP_BUTTON_HEIGHT = 20;
    private static final int TOP_BUTTON_GAP = 6;
    private static final int GRID_COLUMNS = 4;
    private static final int CARD_WIDTH_GAP = 12;
    private static final int CARD_HEIGHT_GAP = 12;
    private static final int CARD_PADDING = 6;
    private static final int CARD_TITLE_HEIGHT = 14;
    private static final int ENTRY_HEIGHT = 18;
    private static final int LIST_PANEL_PADDING = 8;
    private static final int LIST_PANEL_TOP_GAP = 16;
    private static final int LIST_SCROLLBAR_WIDTH = 6;
    private static final int LIST_SCROLLBAR_MARGIN = 4;

    private final Screen parent;
    private final BlazeMapSeedScreen.SeedMapSession session;
    private final List<BiomeFilterCatalog.BiomeGroup> groups;
    private final List<EntryHitbox> hitboxes = new ArrayList<>();

    private ButtonWidget enabledButton;
    private ButtonWidget selectAllButton;
    private ButtonWidget clearButton;
    private ButtonWidget invertButton;
    private ButtonWidget doneButton;

    private int controlsLeft;
    private int controlsRight;
    private int controlsTop;
    private int controlsBottom;

    private int scrollOffset;
    private int contentHeight;
    private int listLeft;
    private int listTop;
    private int listWidth;
    private int listHeight;

    BiomeFilterScreen(Screen parent, BlazeMapSeedScreen.SeedMapSession session) {
        super(Text.translatable("cubeium.biome_filter.title"));
        this.parent = parent;
        this.session = session;
        this.groups = BiomeFilterCatalog.getCategorizedBiomes(session.biomeGenerator);
    }

    @Override
    protected void init() {
        super.init();

        int totalButtonsWidth = TOP_BUTTON_WIDTH * 5 + TOP_BUTTON_GAP * 4;
        int buttonX = (width - totalButtonsWidth) / 2;
        int buttonY = 40;

        enabledButton = ButtonWidget.builder(filterEnabledLabel(), button -> {
            session.biomeFilteringEnabled = !session.biomeFilteringEnabled;
            button.setMessage(filterEnabledLabel());
            BlazeMapSeedScreen.savePersistentSettings(session);
        }).dimensions(buttonX, buttonY, TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build();
        addDrawableChild(enabledButton);

        selectAllButton = ButtonWidget.builder(Text.translatable("cubeium.biome_filter.select_all"), button -> {
            session.selectAllBiomes(BiomeFilterCatalog.flatten(groups));
            BlazeMapSeedScreen.savePersistentSettings(session);
        }).dimensions(buttonX + TOP_BUTTON_WIDTH + TOP_BUTTON_GAP, buttonY, TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build();
        addDrawableChild(selectAllButton);

        clearButton = ButtonWidget.builder(Text.translatable("cubeium.biome_filter.clear"), button -> {
            session.clearBiomes();
            BlazeMapSeedScreen.savePersistentSettings(session);
        }).dimensions(buttonX + (TOP_BUTTON_WIDTH + TOP_BUTTON_GAP) * 2, buttonY, TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build();
        addDrawableChild(clearButton);

        invertButton = ButtonWidget.builder(Text.translatable("cubeium.biome_filter.invert"), button -> {
            session.invertBiomes(BiomeFilterCatalog.flatten(groups));
            BlazeMapSeedScreen.savePersistentSettings(session);
        }).dimensions(buttonX + (TOP_BUTTON_WIDTH + TOP_BUTTON_GAP) * 3, buttonY, TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT).build();
        addDrawableChild(invertButton);

        doneButton = ButtonWidget.builder(Text.translatable("cubeium.button.done"), button -> close())
            .dimensions(buttonX + (TOP_BUTTON_WIDTH + TOP_BUTTON_GAP) * 4, buttonY, TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT)
            .build();
        addDrawableChild(doneButton);

        controlsLeft = buttonX - 6;
        controlsTop = buttonY - 6;
        controlsRight = buttonX + totalButtonsWidth + 6;
        controlsBottom = buttonY + TOP_BUTTON_HEIGHT + 6;

        listLeft = 24;
        listTop = controlsBottom + LIST_PANEL_TOP_GAP;
        listWidth = width - 48;
        listHeight = height - listTop - 22;
    }

    private Text filterEnabledLabel() {
        return Text.translatable("cubeium.biome_filter.enabled", session.biomeFilteringEnabled ? Text.translatable("cubeium.state.on") : Text.translatable("cubeium.state.off"));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        Screen.renderBackgroundTexture(context, MENU_BACKGROUND_TEXTURE, 0, 0, 0.0F, 0.0F, width, height);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 16, 0xFFFFFFFF);
        context.drawText(textRenderer,
            Text.translatable("cubeium.biome_filter.selected_count", session.getSelectedBiomeCount(), BiomeFilterCatalog.flatten(groups).size()).getString(),
            listLeft, 28, 0xFFE0E0E0, false);

        context.fill(controlsLeft, controlsTop, controlsRight, controlsBottom, 0xEE111111);
        context.drawBorder(controlsLeft, controlsTop, controlsRight - controlsLeft, controlsBottom - controlsTop, 0xFF5A5A5A);

        int panelLeft = listLeft - LIST_PANEL_PADDING;
        int panelTop = listTop - LIST_PANEL_PADDING;
        int panelRight = listLeft + listWidth + LIST_PANEL_PADDING;
        int panelBottom = listTop + listHeight + LIST_PANEL_PADDING;
        context.fill(panelLeft, panelTop, panelRight, panelBottom, 0xEE101010);
        context.drawBorder(panelLeft, panelTop, panelRight - panelLeft, panelBottom - panelTop, 0xFF5A5A5A);

        context.enableScissor(listLeft, listTop, listLeft + listWidth, listTop + listHeight);
        renderBiomeCards(context, mouseX, mouseY);
        context.disableScissor();

        renderScrollbar(context);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderBiomeCards(DrawContext context, int mouseX, int mouseY) {
        hitboxes.clear();

        int cardWidth = (listWidth - (GRID_COLUMNS - 1) * CARD_WIDTH_GAP) / GRID_COLUMNS;
        int[] columnHeights = new int[GRID_COLUMNS];
        int maxContentBottom = 0;

        for (BiomeFilterCatalog.BiomeGroup group : groups) {
            int column = findShortestColumn(columnHeights);
            int cardX = listLeft + column * (cardWidth + CARD_WIDTH_GAP);
            int cardY = listTop + columnHeights[column] - scrollOffset;
            int cardHeight = CARD_PADDING * 2 + CARD_TITLE_HEIGHT + group.entries().size() * ENTRY_HEIGHT;

            if (cardY + cardHeight >= listTop && cardY <= listTop + listHeight) {
                drawCategoryCard(context, mouseX, mouseY, cardX, cardY, cardWidth, group);
            }

            columnHeights[column] += cardHeight + CARD_HEIGHT_GAP;
            maxContentBottom = Math.max(maxContentBottom, columnHeights[column]);
        }

        contentHeight = Math.max(0, maxContentBottom - CARD_HEIGHT_GAP);
    }

    private void renderScrollbar(DrawContext context) {
        int maxScroll = Math.max(0, contentHeight - listHeight);
        if (maxScroll <= 0) {
            return;
        }

        int trackLeft = listLeft + listWidth - LIST_SCROLLBAR_WIDTH - LIST_SCROLLBAR_MARGIN;
        int trackTop = listTop + LIST_SCROLLBAR_MARGIN;
        int trackHeight = listHeight - LIST_SCROLLBAR_MARGIN * 2;
        context.fill(trackLeft, trackTop, trackLeft + LIST_SCROLLBAR_WIDTH, trackTop + trackHeight, 0x66111111);

        int thumbHeight = Math.max(24, trackHeight * listHeight / contentHeight);
        int thumbTravel = trackHeight - thumbHeight;
        int thumbTop = trackTop + (thumbTravel * scrollOffset / maxScroll);
        context.fill(trackLeft, thumbTop, trackLeft + LIST_SCROLLBAR_WIDTH, thumbTop + thumbHeight, 0xFF6A6A6A);
    }

    private void drawCategoryCard(DrawContext context, int mouseX, int mouseY, int cardX, int cardY, int cardWidth, BiomeFilterCatalog.BiomeGroup group) {
        int cardHeight = CARD_PADDING * 2 + CARD_TITLE_HEIGHT + group.entries().size() * ENTRY_HEIGHT;
        context.fill(cardX, cardY, cardX + cardWidth, cardY + cardHeight, 0xEE111111);
        context.drawBorder(cardX, cardY, cardWidth, cardHeight, 0xFF5A5A5A);

        context.fill(cardX + 1, cardY + 1, cardX + cardWidth - 1, cardY + CARD_PADDING + CARD_TITLE_HEIGHT, 0xFF1A1A1A);
        context.drawText(textRenderer, group.title(), cardX + CARD_PADDING, cardY + 4, 0xFFE0E0E0, false);

        int entryTop = cardY + CARD_PADDING + CARD_TITLE_HEIGHT;
        for (BiomeFilterCatalog.BiomeEntry biome : group.entries()) {
            boolean selected = session.isBiomeSelected(biome.id());
            boolean hovered = mouseX >= cardX + 2 && mouseX < cardX + cardWidth - 2 && mouseY >= entryTop && mouseY < entryTop + ENTRY_HEIGHT;

            context.fill(cardX + 2, entryTop, cardX + cardWidth - 2, entryTop + ENTRY_HEIGHT, hovered ? 0x334C6A8A : 0x22111111);
            context.drawBorder(cardX + 2, entryTop, cardWidth - 4, ENTRY_HEIGHT, hovered ? 0xFF89A9CF : 0xFF444444);

            int checkboxSize = 10;
            int checkboxX = cardX + 6;
            int checkboxY = entryTop + (ENTRY_HEIGHT - checkboxSize) / 2;
            context.fill(checkboxX, checkboxY, checkboxX + checkboxSize, checkboxY + checkboxSize, 0xFF111111);
            context.drawBorder(checkboxX, checkboxY, checkboxSize, checkboxSize, selected ? 0xFF89A9CF : 0xFF777777);
            if (selected) {
                context.fill(checkboxX + 2, checkboxY + 2, checkboxX + checkboxSize - 2, checkboxY + checkboxSize - 2, 0xFFE0E0E0);
            }

            context.fill(cardX + 22, entryTop + 5, cardX + 32, entryTop + 14, biome.color());
            String label = textRenderer.trimToWidth(biome.name(), cardWidth - 40);
            context.drawText(textRenderer, label, cardX + 38, entryTop + 5, 0xFFE0E0E0, false);

            hitboxes.add(new EntryHitbox(cardX + 2, entryTop, cardWidth - 4, ENTRY_HEIGHT, biome.id()));
            entryTop += ENTRY_HEIGHT;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button != 0) {
            return false;
        }

        int clickX = (int) mouseX;
        int clickY = (int) mouseY;
        for (EntryHitbox hitbox : hitboxes) {
            if (hitbox.contains(clickX, clickY)) {
                session.setBiomeSelected(hitbox.biomeId(), !session.isBiomeSelected(hitbox.biomeId()));
                BlazeMapSeedScreen.savePersistentSettings(session);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, contentHeight - listHeight);
        scrollOffset = clamp(scrollOffset - (int) (verticalAmount * 18), 0, maxScroll);
        return true;
    }

    @Override
    public void close() {
        BlazeMapSeedScreen.savePersistentSettings(session);
        client.setScreen(parent);
    }

    private int findShortestColumn(int[] columnHeights) {
        int bestColumn = 0;
        for (int i = 1; i < columnHeights.length; i++) {
            if (columnHeights[i] < columnHeights[bestColumn]) {
                bestColumn = i;
            }
        }
        return bestColumn;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record EntryHitbox(int x, int y, int width, int height, int biomeId) {
        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}