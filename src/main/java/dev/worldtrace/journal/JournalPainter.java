package dev.worldtrace.journal;

import dev.worldtrace.model.EntryCategory;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin drawing helpers that give every part of the journal the same visual language: one pixel
 * borders, consistent padding and identical chip/button geometry.
 *
 * <p>Static by design — this is a rendering vocabulary, not a widget framework.
 */
public final class JournalPainter {

    public static final int CHIP_PADDING = 5;
    public static final int CHIP_SPACING = 4;

    private JournalPainter() {
    }

    /** Filled rectangle with an optional one pixel border. */
    public static void panel(DrawContext context, int x1, int y1, int x2, int y2, int fill, int border) {
        if (fill != 0) {
            context.fill(x1, y1, x2, y2, fill);
        }
        if (border != 0) {
            context.drawBorder(x1, y1, x2 - x1, y2 - y1, border);
        }
    }

    /**
     * Paints the full-screen window surface that every WorldTrace screen sits on.
     *
     * <p>Callers must invoke {@link net.minecraft.client.gui.screen.Screen#renderBackground} first,
     * and both halves are required. {@code renderBackground} is what <em>releases</em> Minecraft's
     * screen-blur post-process after drawing the blurred backdrop; skip it and the post-process stays
     * bound, so every fill and every glyph this mod draws is smeared — most visibly next to widgets
     * such as the search field that render through a different path and stay sharp. Calling it while
     * keeping a translucent window surface has the mirror-image problem: the blurred backdrop bleeds
     * through the whole interface. An opaque surface drawn on top of the released backdrop gives a
     * crisp, fully opaque screen.
     */
    public static void window(DrawContext context, int width, int height, int accentArgb) {
        context.fill(0, 0, width, height, JournalPalette.BACKGROUND);
        // A single crisp accent rule at the top edge, so the surface still has a sense of craft
        // without any gradient or glass effect.
        context.fill(0, 0, width, 1, accentArgb);
        context.drawBorder(0, 0, width, height, JournalPalette.BORDER);
    }

    public static void text(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        draw(context, renderer, value, x, y, color, false);
    }

    public static void textShadow(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        draw(context, renderer, value, x, y, color, true);
    }

    /**
     * Draws a glyph run through {@link TextRenderer} using {@link TextRenderer.TextLayerType#NORMAL}.
     *
     * <p>{@code NORMAL} is the layer intended for interface text. The other option,
     * {@code SEE_THROUGH}, exists for world-space labels that must stay visible through geometry and
     * is not the right choice for a screen.
     */
    private static void draw(DrawContext context, TextRenderer renderer, String value, int x, int y,
                             int color, boolean shadow) {
        renderer.draw(value, x, y, color, shadow,
                context.getMatrices().peek().getPositionMatrix(),
                context.getVertexConsumers(),
                TextRenderer.TextLayerType.NORMAL, 0, 0xFFFFFF);
    }

    public static void centered(DrawContext context, TextRenderer renderer, String value, int centerX, int y, int color) {
        draw(context, renderer, value, centerX - renderer.getWidth(value) / 2, y, color, false);
    }

    /**
     * Greedy word wrap. Kept here rather than using {@code TextRenderer.wrapLines} because that
     * returns {@link net.minecraft.text.OrderedText}, which cannot be turned back into a plain
     * String for {@link #text} — and going through {@code DrawContext} is exactly what makes GUI
     * text render blurred.
     *
     * <p>Words longer than the available width are hard-split so a long tag or a pasted coordinate
     * string can never overflow the pane.
     */
    public static List<String> wrap(TextRenderer renderer, String value, int maxWidth) {
        List<String> lines = new ArrayList<>();
        if (value == null || value.isEmpty() || maxWidth <= 0) {
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : value.trim().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (renderer.getWidth(candidate) <= maxWidth) {
                current.setLength(0);
                current.append(candidate);
                continue;
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (renderer.getWidth(word) <= maxWidth) {
                current.append(word);
                continue;
            }
            StringBuilder part = new StringBuilder();
            for (int i = 0; i < word.length(); i++) {
                if (renderer.getWidth(part.toString() + word.charAt(i)) > maxWidth && part.length() > 0) {
                    lines.add(part.toString());
                    part.setLength(0);
                }
                part.append(word.charAt(i));
            }
            current.append(part);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    /** Truncates with a trailing ellipsis so long biome names never bleed into the next column. */
    public static String fit(TextRenderer renderer, String value, int maxWidth) {
        if (value == null) {
            return "";
        }
        if (renderer.getWidth(value) <= maxWidth) {
            return value;
        }
        return renderer.trimToWidth(value, Math.max(0, maxWidth - renderer.getWidth("…")));
    }

    /** Width a chip would occupy; used to lay the filter row out before drawing it. */
    public static int chipWidth(TextRenderer renderer, String label) {
        return renderer.getWidth(label) + CHIP_PADDING * 2;
    }

    public static int chipHeight() {
        return 14;
    }

    /**
     * Draws a toggle chip and returns its width so callers can flow the next chip after it.
     *
     * @param active   chip is part of the current filter
     * @param hover    the mouse is over the chip
     */
    public static int chip(DrawContext context, TextRenderer renderer, String label, int x, int y,
                           boolean active, boolean hover, int accentArgb) {
        int width = chipWidth(renderer, label);
        int height = chipHeight();
        int background;
        int border;
        int textColor;

        if (active) {
            background = accentArgb;
            border = accentArgb;
            textColor = JournalPalette.TEXT_ON_ACCENT;
        } else if (hover) {
            background = JournalPalette.PANEL_RAISED;
            border = JournalPalette.BORDER_STRONG;
            textColor = JournalPalette.TEXT;
        } else {
            background = JournalPalette.PANEL;
            border = JournalPalette.BORDER;
            textColor = JournalPalette.TEXT_MUTED;
        }
        panel(context, x, y, x + width, y + height, background, border);
        text(context, renderer, label, x + CHIP_PADDING, y + 3, textColor);
        return width;
    }

    /**
     * Draws a compact inline button. Returns nothing; hit testing lives with the screen so the
     * geometry is defined in exactly one place.
     */
    public static void button(DrawContext context, TextRenderer renderer, String label,
                              int x, int y, int width, int height, boolean enabled, boolean hover, boolean active,
                              int accentArgb) {
        int background;
        int border;
        int textColor;

        if (!enabled) {
            background = JournalPalette.PANEL_SUNKEN;
            border = JournalPalette.BORDER;
            textColor = JournalPalette.TEXT_FAINT;
        } else if (active) {
            background = JournalPalette.withAlpha(accentArgb, 0.85f);
            border = accentArgb;
            textColor = JournalPalette.TEXT_ON_ACCENT;
        } else if (hover) {
            background = JournalPalette.PANEL_RAISED;
            border = JournalPalette.BORDER_FOCUS;
            textColor = JournalPalette.TEXT;
        } else {
            background = JournalPalette.PANEL_RAISED;
            border = JournalPalette.BORDER_STRONG;
            textColor = JournalPalette.TEXT;
        }
        panel(context, x, y, x + width, y + height, background, border);
        String fitted = fit(renderer, label, width - 8);
        centered(context, renderer, fitted, x + width / 2, y + (height - renderer.fontHeight) / 2 + 1, textColor);
    }

    /** Colour used for a category's accent bar, badge text and list marker. */
    public static int categoryColor(EntryCategory category) {
        return category.argb();
    }

    /** A short glyph used as the list row icon so rows are scannable without item sprites. */
    public static String glyph(EntryCategory category) {
        return switch (category) {
            case BIOME -> "▲";
            case STRUCTURE -> "◆";
            case LANDMARK -> "✦";
            case BOOKMARK -> "★";
            case MILESTONE -> "✧";
        };
    }

    /** Key/value line used throughout the detail pane. */
    public static void field(DrawContext context, TextRenderer renderer, String label, String value,
                             int x, int y, int maxWidth, int valueColor) {
        int labelWidth = renderer.getWidth(label) + 6;
        text(context, renderer, label, x, y, JournalPalette.TEXT_FAINT);
        text(context, renderer, fit(renderer, value, Math.max(0, maxWidth - labelWidth)), x + labelWidth, y, valueColor);
    }
}