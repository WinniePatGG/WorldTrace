package dev.worldtrace.journal;

import dev.worldtrace.config.WorldTraceConfig;
import dev.worldtrace.model.EntryCategory;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.navigation.NavigationCore;
import dev.worldtrace.navigation.NavigationOverlay;
import dev.worldtrace.util.Distance;
import dev.worldtrace.util.NameFormatting;
import dev.worldtrace.util.Timestamps;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Optional;

final class DetailPanel {

    static final int FAVORITE = 1;
    static final int DESTINATION = 2;
    static final int COPY = 3;
    static final int EDIT = 4;
    static final int SCREENSHOT = 5;
    static final int DELETE = 6;

    private static final int FIRST_ACTION = FAVORITE;
    private static final int LAST_ACTION = DELETE;

    record Area(int left, int right, int top, int bottom, int actionTop) {
    }

    private DetailPanel() {
    }

    static String actionLabel(int action, TraceEntry entry, boolean isDestination) {
        return switch (action) {
            case FAVORITE -> entry.favorite() ? "Unfavorite" : "Favorite";
            case DESTINATION -> isDestination ? "Clear destination" : "Set destination";
            case COPY -> "Copy coords";
            case EDIT -> "Edit";
            case DELETE -> "Delete";
            case SCREENSHOT -> "Screenshot";
            default -> "";
        };
    }

    static boolean isAlwaysEnabled(int action) {
        return action == COPY || action == FAVORITE;
    }

    static boolean isAction(int action) {
        return action >= FIRST_ACTION && action <= LAST_ACTION;
    }

    static void render(DrawContext context, TextRenderer textRenderer, WorldTraceConfig config, TraceEntry entry,
                       Area area, List<HitRegion> regions, boolean isDestination, boolean readOnly,
                       int mouseX, int mouseY, int accent) {
        int padding = layoutPadding();
        int x = area.left() + padding;
        int width = area.right() - area.left() - padding * 2;
        int y = area.top() + padding;
        int lineHeight = textRenderer.fontHeight + 2;
        int yLimit = area.actionTop() - 6;

        y = drawWrapped(context, textRenderer, entry.name(), x, y, width, JournalPalette.TEXT, 2, lineHeight, yLimit);
        y = drawBadge(context, textRenderer, entry, x, y, width, lineHeight);

        context.fill(x, y, area.right() - padding, y + 1, JournalPalette.BORDER);
        y += 6;

        y = drawFacts(context, textRenderer, config, entry, x, y, width, yLimit);
        y = drawNotes(context, textRenderer, entry, x, y, width, lineHeight, yLimit);
        y = drawTags(context, textRenderer, entry, x, y, width, lineHeight, yLimit, accent);
        drawScreenshot(context, textRenderer, entry, x, y, width, lineHeight, yLimit);

        for (HitRegion region : regions) {
            if (!isAction(region.action())) {
                continue;
            }
            boolean active = switch (region.action()) {
                case FAVORITE -> entry.favorite();
                case DESTINATION -> isDestination;
                default -> false;
            };
            JournalPainter.button(context, textRenderer,
                    actionLabel(region.action(), entry, isDestination),
                    region.x1(), region.y1(), region.width(), region.height(),
                    !readOnly || isAlwaysEnabled(region.action()),
                    region.contains(mouseX, mouseY), active, accent);
        }
    }

    private static int layoutPadding() {

        return 8;
    }

    private static int drawBadge(DrawContext context, TextRenderer textRenderer, TraceEntry entry,
                                 int x, int y, int width, int lineHeight) {
        String badge = JournalPainter.glyph(entry.category()) + "  " + entry.category().displayName();
        if (entry.category() == EntryCategory.STRUCTURE && !entry.confirmed()) {
            badge += "  ·  unconfirmed";
        }
        JournalPainter.text(context, textRenderer, JournalPainter.fit(textRenderer, badge, width), x, y,
                entry.category().argb());
        return y + lineHeight;
    }

    private static int drawFacts(DrawContext context, TextRenderer textRenderer, WorldTraceConfig config,
                                 TraceEntry entry, int x, int y, int width, int yLimit) {
        y = drawField(context, textRenderer, "Dimension",
                NameFormatting.prettifyIdentifier(entry.dimensionId()), x, y, width, yLimit);
        y = drawField(context, textRenderer, "Coordinates",
                entry.x() + " / " + entry.y() + " / " + entry.z(), x, y, width, yLimit);
        y = drawField(context, textRenderer, "Discovered",
                Timestamps.formatCompact(entry.firstSeenAtMillis()) + "  ·  "
                        + Timestamps.formatRelative(entry.firstSeenAtMillis(), System.currentTimeMillis()),
                x, y, width, yLimit);
        y = drawField(context, textRenderer, "Last seen",
                Timestamps.formatCompact(entry.lastSeenAtMillis()) + "  ·  " + entry.visitCount()
                        + (entry.visitCount() == 1 ? " visit" : " visits"),
                x, y, width, yLimit);

        if (entry.biomeId() != null) {
            y = drawField(context, textRenderer, "Biome",
                    config.showTechnicalIds() ? entry.biomeId()
                            : NameFormatting.prettifyIdentifier(entry.biomeId()),
                    x, y, width, yLimit);
        }
        if (entry.structureId() != null) {
            y = drawField(context, textRenderer, "Structure",
                    config.showTechnicalIds() ? entry.structureId()
                            : NameFormatting.prettifyIdentifier(entry.structureId()),
                    x, y, width, yLimit);
        }
        if (config.showTechnicalIds()) {
            y = drawField(context, textRenderer, "Id", entry.id(), x, y, width, yLimit);
        }

        Optional<NavigationCore.Result> navigation = NavigationOverlay.currentFor(entry);
        if (navigation.isPresent()) {
            y = drawField(context, textRenderer, "Distance", describeNavigation(navigation.get()),
                    x, y, width, yLimit);
        }
        return y;
    }

    private static int drawNotes(DrawContext context, TextRenderer textRenderer, TraceEntry entry,
                                 int x, int y, int width, int lineHeight, int yLimit) {

        if (y + lineHeight * 3 >= yLimit) {
            return y;
        }
        y = drawWrapped(context, textRenderer, "Notes", x, y + 4, width, JournalPalette.TEXT_FAINT,
                1, lineHeight, yLimit);
        if (entry.notes().isEmpty()) {
            JournalPainter.text(context, textRenderer, "No notes yet — press Edit to add some.", x, y,
                    JournalPalette.TEXT_FAINT);
            return y + lineHeight;
        }
        return drawWrapped(context, textRenderer, entry.notes(), x, y, width, JournalPalette.TEXT,
                4, lineHeight, yLimit);
    }

    private static int drawTags(DrawContext context, TextRenderer textRenderer, TraceEntry entry,
                                int x, int y, int width, int lineHeight, int yLimit, int accent) {
        if (entry.tags().isEmpty()) {
            return y;
        }
        int tagX = x;
        int tagY = y + lineHeight * 2 + 2;
        if (tagY + JournalPainter.chipHeight() >= yLimit) {
            return y;
        }
        JournalPainter.text(context, textRenderer, "Tags", x, tagY - lineHeight, JournalPalette.TEXT_FAINT);
        for (String tag : entry.tags()) {
            int tagWidth = JournalPainter.chipWidth(textRenderer, tag);
            if (tagX + tagWidth > x + width) {
                tagX = x;
                tagY += JournalPainter.chipHeight() + 3;
            }
            JournalPainter.chip(context, textRenderer, tag, tagX, tagY, false, false, accent);
            tagX += tagWidth + JournalPainter.CHIP_SPACING;
        }
        return tagY + JournalPainter.chipHeight() + 4;
    }

    private static void drawScreenshot(DrawContext context, TextRenderer textRenderer, TraceEntry entry,
                                       int x, int y, int width, int lineHeight, int yLimit) {
        if (entry.screenshotRef() == null) {
            return;
        }
        JournalPainter.text(context, textRenderer,
                JournalPainter.fit(textRenderer, "Screenshot attached: " + entry.screenshotRef(), width),
                x, Math.min(y + lineHeight, yLimit - 2), JournalPalette.POSITIVE);
    }

    private static String describeNavigation(NavigationCore.Result result) {
        return switch (result.kind()) {
            case OTHER_DIMENSION -> "In another dimension — find a portal first";
            case UNKNOWN -> "Position unavailable";
            case SAME_DIMENSION -> Distance.formatDistance(result.distanceBlocks())
                    + "  ·  " + NavigationCore.relativeLabel(result.horizontalDelta())
                    + " (" + NavigationCore.compassLabel(result.targetYaw()) + ")";
        };
    }

    private static int drawField(DrawContext context, TextRenderer textRenderer, String label, String value,
                                 int x, int y, int width, int yLimit) {
        if (y + textRenderer.fontHeight > yLimit) {
            return y;
        }
        int labelWidth = textRenderer.getWidth(label) + 6;
        JournalPainter.text(context, textRenderer, label, x, y, JournalPalette.TEXT_FAINT);
        JournalPainter.text(context, textRenderer,
                JournalPainter.fit(textRenderer, value, Math.max(0, width - labelWidth)),
                x + labelWidth, y, JournalPalette.TEXT);
        return y + textRenderer.fontHeight + 2;
    }

    private static int drawWrapped(DrawContext context, TextRenderer textRenderer, String value,
                                   int x, int y, int maxWidth, int color, int maxLines,
                                   int lineHeight, int yLimit) {
        if (value == null || value.isEmpty()) {
            return y;
        }
        List<String> lines = JournalPainter.wrap(textRenderer, value, maxWidth);
        if (lines.isEmpty()) {
            return y;
        }
        int drawn = Math.min(maxLines, lines.size());
        for (int i = 0; i < drawn; i++) {
            int lineY = y + i * lineHeight;
            if (lineY + textRenderer.fontHeight > yLimit) {
                return y;
            }
            JournalPainter.text(context, textRenderer, lines.get(i), x, lineY, color);
        }
        return y + drawn * lineHeight;
    }
}
