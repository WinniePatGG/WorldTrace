package dev.worldtrace.journal;

import dev.worldtrace.discovery.DiscoveryManager;
import dev.worldtrace.util.InputValidation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public abstract class JournalFormScreen extends Screen {

    public record FormField(String label, String initial, boolean editable, int maxLength) {

        public static FormField text(String label, String initial, int maxLength) {
            return new FormField(label, initial, true, maxLength);
        }

        public static FormField choice(String label, String initial) {
            return new FormField(label, initial, false, 0);
        }
    }

    private record ButtonSpec(String label, Runnable action, boolean enabled) {

        ButtonSpec withEnabled(boolean value) {
            return new ButtonSpec(label, action, value);
        }
    }

    private record HitRegion(int kind, int payload, int x1, int y1, int x2, int y2) {

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
        }
    }

    private static final int KIND_BUTTON = 0;
    private static final int KIND_PREV = 1;
    private static final int KIND_NEXT = 2;

    private static final int ROW_LABEL_HEIGHT = 10;
    private static final int ROW_FIELD_HEIGHT = 16;
    private static final int ROW_GAP = 6;
    private static final int STEP_BUTTON_WIDTH = 12;

    protected final DiscoveryManager manager;
    private final Screen parent;
    private final String dialogTitle;
    private final List<FormField> fields;
    private final List<ButtonSpec> buttons = new ArrayList<>();
    private final List<TextFieldWidget> inputs = new ArrayList<>();
    private final List<String> choiceValues = new ArrayList<>();
    private final List<HitRegion> regions = new ArrayList<>();

    private int windowX;
    private int windowY;
    private int windowWidth;
    private int windowHeight;
    private int contentTop;

    protected JournalFormScreen(DiscoveryManager manager, Screen parent, String dialogTitle,
                                List<FormField> fields) {
        super(Text.literal(dialogTitle));
        this.manager = manager;
        this.parent = parent;
        this.dialogTitle = dialogTitle;
        this.fields = List.copyOf(fields);
    }

    protected void addButton(String label, Runnable action) {
        buttons.add(new ButtonSpec(label, action, true));
    }

    protected void setButtonEnabled(int index, boolean enabled) {
        if (index >= 0 && index < buttons.size()) {
            buttons.set(index, buttons.get(index).withEnabled(enabled));
        }
    }

    protected String value(int index) {
        TextFieldWidget input = inputs.get(index);
        return input == null ? fields.get(index).initial() : input.getText();
    }

    protected void setValue(int index, String value) {
        TextFieldWidget input = inputs.get(index);
        if (input != null) {
            input.setText(value);
        }
    }

    protected String choice(int index) {
        return choiceValues.get(index);
    }

    protected void setChoice(int index, String value) {
        choiceValues.set(index, value);
    }

    protected int fieldCount() {
        return fields.size();
    }

    protected void onConfirm() {
    }

    @Override
    protected void init() {
        inputs.clear();
        choiceValues.clear();
        regions.clear();

        buttons.clear();

        windowWidth = Math.max(170, Math.min(320, width - 24));
        int contentHeight = 18 + fields.size() * (ROW_LABEL_HEIGHT + ROW_FIELD_HEIGHT + ROW_GAP) + 10;
        windowHeight = contentHeight + 26;

        windowX = (width - windowWidth) / 2;
        windowY = Math.max(4, (height - windowHeight) / 2);
        contentTop = windowY + 16;

        addDrawable(new FormSurface());

        for (int i = 0; i < fields.size(); i++) {
            FormField field = fields.get(i);
            if (field.editable()) {
                int y = contentTop + i * (ROW_LABEL_HEIGHT + ROW_FIELD_HEIGHT + ROW_GAP);
                TextFieldWidget input = new TextFieldWidget(textRenderer, windowX + 8, y + ROW_LABEL_HEIGHT,
                        windowWidth - 16, ROW_FIELD_HEIGHT, Text.literal(field.label()));
                input.setMaxLength(field.maxLength());
                input.setText(field.initial() == null ? "" : field.initial());
                addDrawableChild(input);
                inputs.add(input);
                choiceValues.add(field.initial() == null ? "" : field.initial());
            } else {
                choiceValues.add(field.initial() == null ? "" : field.initial());
            }
        }

        buildButtons();
        buildChoiceRegions();
    }

    protected void defineButtons() {
    }

    private void buildButtons() {
        defineButtons();
        int y = contentTop + fields.size() * (ROW_LABEL_HEIGHT + ROW_FIELD_HEIGHT + ROW_GAP) + 4;
        int x = windowX + 8;
        int available = windowWidth - 16;
        int rowHeight = ROW_FIELD_HEIGHT;

        for (int i = 0; i < buttons.size(); i++) {
            ButtonSpec button = buttons.get(i);
            int buttonWidth = JournalPainter.chipWidth(textRenderer, button.label()) + 8;
            if (x + buttonWidth > windowX + windowWidth - 8 && x > windowX + 8) {
                x = windowX + 8;
                y += rowHeight + 4;
            }
            regions.add(new HitRegion(KIND_BUTTON, i, x, y, x + buttonWidth, y + rowHeight));
            x += buttonWidth + 4;
        }
    }

    private void buildChoiceRegions() {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).editable()) {
                continue;
            }
            int y = contentTop + i * (ROW_LABEL_HEIGHT + ROW_FIELD_HEIGHT + ROW_GAP) + ROW_LABEL_HEIGHT;
            int right = windowX + windowWidth - 8;
            int left = windowX + 8;
            regions.add(new HitRegion(KIND_PREV, i, left, y, left + STEP_BUTTON_WIDTH, y + ROW_FIELD_HEIGHT));
            regions.add(new HitRegion(KIND_NEXT, i, right - STEP_BUTTON_WIDTH, y, right, y + ROW_FIELD_HEIGHT));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        super.render(context, mouseX, mouseY, delta);
    }

    private final class FormSurface implements net.minecraft.client.gui.Drawable {

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            int accent = JournalPalette.opaque(manager.config().accentRgb());
            JournalPainter.window(context, width, height, accent);

            JournalPainter.panel(context, windowX, windowY, windowX + windowWidth, windowY + windowHeight,
                    JournalPalette.BACKGROUND, JournalPalette.BORDER);
            context.fill(windowX, windowY, windowX + windowWidth, windowY + 1, accent);
            JournalPainter.text(context, textRenderer, dialogTitle, windowX + 8, windowY + 6, JournalPalette.TEXT);

            for (int i = 0; i < fields.size(); i++) {
                FormField field = fields.get(i);
                int y = contentTop + i * (ROW_LABEL_HEIGHT + ROW_FIELD_HEIGHT + ROW_GAP);
                JournalPainter.text(context, textRenderer, field.label(), windowX + 8, y, JournalPalette.TEXT_FAINT);
                if (!field.editable()) {
                    drawChoice(context, i, y + ROW_LABEL_HEIGHT, mouseX, mouseY, accent);
                }
            }

            for (HitRegion region : regions) {
                if (region.kind() != KIND_BUTTON) {
                    continue;
                }
                ButtonSpec button = buttons.get(region.payload());
                JournalPainter.button(context, textRenderer, button.label(), region.x1(), region.y1(),
                        region.x2() - region.x1(), region.y2() - region.y1(), button.enabled(),
                        region.contains(mouseX, mouseY), false, accent);
            }
        }
    }

    private void drawChoice(DrawContext context, int index, int y, int mouseX, int mouseY, int accent) {
        int left = windowX + 8;
        int right = windowX + windowWidth - 8;
        JournalPainter.panel(context, left, y, right, y + ROW_FIELD_HEIGHT, JournalPalette.PANEL_SUNKEN,
                JournalPalette.BORDER);

        int valueLeft = left + STEP_BUTTON_WIDTH + 4;
        int valueWidth = right - STEP_BUTTON_WIDTH - 4 - valueLeft - STEP_BUTTON_WIDTH - 4;
        JournalPainter.centered(context, textRenderer,
                JournalPainter.fit(textRenderer, choiceValues.get(index), Math.max(0, valueWidth)),
                (valueLeft + valueWidth / 2), y + 4, JournalPalette.TEXT);

        for (HitRegion region : regions) {
            if (region.kind() == KIND_PREV && region.payload() == index) {
                JournalPainter.button(context, textRenderer, "«", region.x1(), region.y1(),
                        region.x2() - region.x1(), region.y2() - region.y1(), true,
                        region.contains(mouseX, mouseY), false, accent);
            } else if (region.kind() == KIND_NEXT && region.payload() == index) {
                JournalPainter.button(context, textRenderer, "»", region.x1(), region.y1(),
                        region.x2() - region.x1(), region.y2() - region.y1(), true,
                        region.contains(mouseX, mouseY), false, accent);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (HitRegion region : regions) {
                if (!region.contains(mouseX, mouseY)) {
                    continue;
                }
                switch (region.kind()) {
                    case KIND_BUTTON -> {
                        ButtonSpec button2 = buttons.get(region.payload());
                        if (button2.enabled()) {
                            button2.action().run();
                            return true;
                        }
                        return true;
                    }
                    case KIND_PREV, KIND_NEXT -> {
                        cycleChoice(region.payload(), region.kind() == KIND_NEXT ? 1 : -1);
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected void cycleChoice(int index, int direction) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            onConfirm();
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    protected static String normalize(String raw, int maxLength) {
        return InputValidation.truncate(raw == null ? "" : raw.trim(), maxLength);
    }
}
