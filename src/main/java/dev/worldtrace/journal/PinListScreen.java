package dev.worldtrace.journal;

import dev.worldtrace.discovery.DiscoveryManager;
import dev.worldtrace.model.TracePin;
import dev.worldtrace.navigation.NavigationCore;
import dev.worldtrace.navigation.NavigationOverlay;
import dev.worldtrace.util.Distance;
import dev.worldtrace.util.NameFormatting;
import dev.worldtrace.util.Timestamps;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lists the watch pins for the current world so they can be reviewed and removed.
 *
 * <p>Pins are the only expectation-driven feature in WorldTrace, so this screen deliberately shows
 * how far each pin is, whether it has been reached, and — for structure pins — whether the finding
 * was confirmed against real blocks or only visited.
 */
public final class PinListScreen extends Screen {

    private static final int ROW_HEIGHT = 26;
    private static final int ACTION_DELETE = 1;

    private final DiscoveryManager manager;
    private final Screen parent;

    private List<TracePin> pins = List.of();
    private List<HitRegion> deleteRegions = new ArrayList<>();
    private int scrollOffset;
    private int visibleRows;
    private int windowX;
    private int windowY;
    private int windowWidth;
    private int windowHeight;
    private int contentTop;
    private String status;

    public PinListScreen(DiscoveryManager manager, Screen parent) {
        super(Text.literal("Watch pins"));
        this.manager = manager;
        this.parent = parent;
    }

    @Override
    protected void init() {
        pins = new ArrayList<>(manager.store().pinList());
        windowWidth = Math.max(180, Math.min(380, width - 24));
        windowHeight = Math.max(80, Math.min(height - 16, 26 + pins.size() * ROW_HEIGHT + 20));
        windowX = (width - windowWidth) / 2;
        windowY = Math.max(4, (height - windowHeight) / 2);
        contentTop = windowY + 20;
        visibleRows = Math.max(1, (windowHeight - 28) / ROW_HEIGHT);
        rebuildRegions();
    }

    private void rebuildRegions() {
        deleteRegions.clear();
        int x = windowX + windowWidth - 52;
        for (int i = scrollOffset; i < Math.min(pins.size(), scrollOffset + visibleRows); i++) {
            int y = contentTop + (i - scrollOffset) * ROW_HEIGHT;
            deleteRegions.add(new HitRegion(ACTION_DELETE, i, x, y + 4, x + 44, y + 18));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int accent = JournalPalette.opaque(manager.config().accentRgb());
        renderBackground(context, mouseX, mouseY, delta);
        JournalPainter.window(context, width, height, accent);

        JournalPainter.panel(context, windowX, windowY, windowX + windowWidth, windowY + windowHeight,
                JournalPalette.BACKGROUND, JournalPalette.BORDER);
        context.fill(windowX, windowY, windowX + windowWidth, windowY + 1, accent);
        JournalPainter.text(context, textRenderer, "Watch pins", windowX + 8, windowY + 6, JournalPalette.TEXT);

        if (pins.isEmpty()) {
            JournalPainter.centered(context, textRenderer, "No watch pins yet",
                    windowX + windowWidth / 2, contentTop + 14, JournalPalette.TEXT_MUTED);
            JournalPainter.centered(context, textRenderer,
                    JournalPainter.fit(textRenderer, "Place one where you expect a structure to be.",
                            windowWidth - 16), windowX + windowWidth / 2, contentTop + 28, JournalPalette.TEXT_FAINT);
        } else {
            context.enableScissor(windowX + 1, contentTop - 1, windowX + windowWidth - 1,
                    windowY + windowHeight - 1);
            for (int i = scrollOffset; i < Math.min(pins.size(), scrollOffset + visibleRows); i++) {
                renderPin(context, pins.get(i), i, mouseX, mouseY, accent);
            }
            context.disableScissor();
        }

        int closeY = windowY + windowHeight - 16;
        JournalPainter.button(context, textRenderer, "Done", windowX + 8, closeY, 48, 14, true,
                isMouseOver(windowX + 8, closeY, 48, 14, mouseX, mouseY), false, accent);
        JournalPainter.button(context, textRenderer, "New pin", windowX + windowWidth - 56, closeY, 48, 14, true,
                isMouseOver(windowX + windowWidth - 56, closeY, 48, 14, mouseX, mouseY), false, accent);

        if (status != null) {
            JournalPainter.text(context, textRenderer,
                    JournalPainter.fit(textRenderer, status, windowWidth - 120), windowX + 62, closeY + 3,
                    JournalPalette.POSITIVE);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private static boolean isMouseOver(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void renderPin(DrawContext context, TracePin pin, int index, int mouseX, int mouseY, int accent) {
        int y = contentTop + (index - scrollOffset) * ROW_HEIGHT;
        int left = windowX + 6;
        int right = windowX + windowWidth - 58;
        boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;

        JournalPainter.panel(context, left, y, right, y + ROW_HEIGHT - 3,
                hovered ? JournalPalette.ROW_HOVER : JournalPalette.PANEL, 0);
        context.fill(left, y, left + 2, y + ROW_HEIGHT - 3, pin.category().argb());

        int textX = left + 7;
        int textWidth = right - textX - 4;
        JournalPainter.text(context, textRenderer,
                JournalPainter.fit(textRenderer, pin.label(), textWidth), textX, y + 3, JournalPalette.TEXT);

        String status = describe(pin);
        JournalPainter.text(context, textRenderer,
                JournalPainter.fit(textRenderer, status, textWidth), textX, y + 3 + textRenderer.fontHeight,
                pin.resolved() ? JournalPalette.POSITIVE : JournalPalette.TEXT_FAINT);

        for (HitRegion region : deleteRegions) {
            if (region.payload() != index) {
                continue;
            }
            JournalPainter.button(context, textRenderer, "Remove", region.x1(), region.y1(),
                    region.x2() - region.x1(), region.y2() - region.y1(), !manager.store().readOnly(),
                    region.contains(mouseX, mouseY), false, accent);
        }
    }

    private String describe(TracePin pin) {
        StringBuilder builder = new StringBuilder();
        builder.append(NameFormatting.prettifyIdentifier(pin.dimensionId()));
        builder.append(" · ").append(pin.x()).append(", ").append(pin.y()).append(", ").append(pin.z());

        Optional<NavigationCore.Result> navigation =
                NavigationOverlay.currentFor(pin.dimensionId(), pin.x(), pin.y(), pin.z());
        if (navigation.isPresent() && navigation.get().kind() == NavigationCore.DistanceKind.SAME_DIMENSION) {
            builder.append(" · ").append(Distance.formatDistance(navigation.get().distanceBlocks()));
        }

        if (pin.resolved()) {
            builder.append(" · reached ").append(Timestamps.formatCompact(pin.createdAtMillis()));
        } else {
            builder.append(" · waiting");
        }
        return builder.toString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        for (HitRegion region : deleteRegions) {
            if (region.contains(mouseX, mouseY)) {
                if (manager.store().readOnly()) {
                    status = "Read-only journal";
                } else if (manager.removePin(pinAt(region.payload()).id())) {
                    status = "Pin removed";
                    init();
                }
                return true;
            }
        }
        if (isMouseOver(windowX + 8, windowY + windowHeight - 16, 48, 14, (int) mouseX, (int) mouseY)) {
            close();
            return true;
        }
        if (isMouseOver(windowX + windowWidth - 56, windowY + windowHeight - 16, 48, 14,
                (int) mouseX, (int) mouseY)) {
            if (client != null && client.player != null && client.world != null) {
                client.setScreen(new PinEditorScreen(manager, client, this));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private TracePin pinAt(int index) {
        return pins.get(index);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int maxScroll = Math.max(0, pins.size() - visibleRows);
        int previous = scrollOffset;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + (int) Math.signum(vertical) * 2));
        if (scrollOffset != previous) {
            rebuildRegions();
        }
        return true;
    }

    @Override
    public void close() {
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private record HitRegion(int action, int payload, int x1, int y1, int x2, int y2) {

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x1 && mouseX < x2 && mouseY >= y1 && mouseY < y2;
        }
    }
}