package dev.worldtrace.journal;

import dev.worldtrace.model.EntryCategory;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

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
     * <p>This deliberately does <em>not</em> call {@link net.minecraft.client.gui.screen.Screen#renderBackground}.
     * That method applies vanilla's full-screen blur and draws the panorama cube map. Since the
     * journal covers the entire window, the result was a frosted, blurred wash bleeding through the
     * whole interface (most obvious against the one widget that paints its own opaque background, the
     * search field) — plus a blur pass every frame that nothing could ever see.
     */
    public static void window(DrawContext context, int width, int height, int accentArgb) {
        context.fill(0, 0, width, height, JournalPalette.BACKGROUND);
        // A single crisp accent rule at the top edge, so the surface still has a sense of craft
        // without any gradient or glass effect.
        context.fill(0, 0, width, 1, accentArgb);
        context.drawBorder(0, 0, width, height, JournalPalette.BORDER);
    }

    public static void text(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        context.drawText(renderer, Text.literal(value), x, y, color, false);
    }

    public static void textShadow(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        context.drawTextWithShadow(renderer, Text.literal(value), x, y, color);
    }

    public static void centered(DrawContext context, TextRenderer renderer, String value, int centerX, int y, int color) {
        context.drawText(renderer, Text.literal(value), centerX - renderer.getWidth(value) / 2, y, color, false);
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