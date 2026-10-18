package dev.worldtrace.journal;

import dev.worldtrace.model.EntryCategory;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public final class JournalPainter {

    public static final int CHIP_PADDING = 5;
    public static final int CHIP_SPACING = 4;

    private JournalPainter() {
    }

    public static void panel(DrawContext context, int x1, int y1, int x2, int y2, int fill, int border) {
        if (fill != 0) {
            context.fill(x1, y1, x2, y2, fill);
        }
        if (border != 0) {
            context.drawBorder(x1, y1, x2 - x1, y2 - y1, border);
        }
    }

    public static void window(DrawContext context, int width, int height, int accentArgb) {
        context.fill(0, 0, width, height, JournalPalette.BACKGROUND);

        context.fill(0, 0, width, 1, accentArgb);
        context.drawBorder(0, 0, width, height, JournalPalette.BORDER);
    }

    public static void text(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        draw(context, renderer, value, x, y, color, false);
    }

    public static void textShadow(DrawContext context, TextRenderer renderer, String value, int x, int y, int color) {
        draw(context, renderer, value, x, y, color, true);
    }

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

    public static String fit(TextRenderer renderer, String value, int maxWidth) {
        if (value == null) {
            return "";
        }
        if (renderer.getWidth(value) <= maxWidth) {
            return value;
        }
        return renderer.trimToWidth(value, Math.max(0, maxWidth - renderer.getWidth("…")));
    }

    public static int chipWidth(TextRenderer renderer, String label) {
        return renderer.getWidth(label) + CHIP_PADDING * 2;
    }

    public static int chipHeight() {
        return 14;
    }

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

    public static int categoryColor(EntryCategory category) {
        return category.argb();
    }

    public static String glyph(EntryCategory category) {
        return switch (category) {
            case BIOME -> "▲";
            case STRUCTURE -> "◆";
            case LANDMARK -> "✦";
            case BOOKMARK -> "★";
            case MILESTONE -> "✧";
        };
    }

    public static void field(DrawContext context, TextRenderer renderer, String label, String value,
                             int x, int y, int maxWidth, int valueColor) {
        int labelWidth = renderer.getWidth(label) + 6;
        text(context, renderer, label, x, y, JournalPalette.TEXT_FAINT);
        text(context, renderer, fit(renderer, value, Math.max(0, maxWidth - labelWidth)), x + labelWidth, y, valueColor);
    }
}
