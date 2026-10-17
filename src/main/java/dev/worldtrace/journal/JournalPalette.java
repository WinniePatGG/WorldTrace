package dev.worldtrace.journal;

/**
 * The journal's colour scheme: a dark, low-chroma field journal that stays out of the way of the
 * game behind it.
 *
 * <p>All colours are ARGB. Panels are nearly opaque so text stays legible over an arbitrary world,
 * while the outer frame keeps a sliver of transparency so the screen still feels part of Minecraft.
 */
public final class JournalPalette {

    private JournalPalette() {
    }

    /**
     * Main window surface. Fully opaque on purpose: WorldTrace's panels cover the whole screen, so a
     * translucent surface would only let vanilla's blurred backdrop bleed through and give the
     * journal a washed-out, frosted look.
     */
    public static final int BACKGROUND = 0xFF10141A;
    /** Raised surfaces: detail pane, cards, inputs. */
    public static final int PANEL = 0xFF161C24;
    public static final int PANEL_RAISED = 0xFF1D242E;
    public static final int PANEL_SUNKEN = 0xFF0C1015;

    public static final int BORDER = 0xFF2B3440;
    public static final int BORDER_STRONG = 0xFF3E4A59;
    public static final int BORDER_FOCUS = 0xFF6F8299;

    public static final int TEXT = 0xFFE8EDF4;
    public static final int TEXT_MUTED = 0xFF8D99AA;
    public static final int TEXT_FAINT = 0xFF5F6B7C;
    public static final int TEXT_ON_ACCENT = 0xFF0B1016;

    /** Selected list row. */
    public static final int ROW_SELECTED = 0xFF273444;
    public static final int ROW_HOVER = 0xFF1C242E;
    public static final int ROW_STRIPE = 0xFF141A21;

    /** Unconfirmed discoveries get a visible, honest marker. */
    public static final int WARNING = 0xFFE0A33C;
    public static final int POSITIVE = 0xFF7FBF87;

    public static final int FAVORITE = 0xFFE6C15A;

    /** Default accent, used when the config does not override it. */
    public static final int DEFAULT_ACCENT_RGB = 0x7FC4E0;

    /** Combines a configured 0xRRGGBB value with full alpha. */
    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    /** Applies an alpha multiplier to an existing ARGB colour. */
    public static int withAlpha(int argb, float factor) {
        int alpha = Math.max(0, Math.min(255, Math.round((argb >>> 24) * factor)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** Blends {@code argb} towards {@code backgroundArgb}; used for subtle state changes. */
    public static int mix(int argb, int backgroundArgb, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int a = blend(argb >>> 24, backgroundArgb >>> 24, t);
        int r = blend((argb >> 16) & 0xFF, (backgroundArgb >> 16) & 0xFF, t);
        int g = blend((argb >> 8) & 0xFF, (backgroundArgb >> 8) & 0xFF, t);
        int b = blend(argb & 0xFF, backgroundArgb & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blend(int from, int to, float t) {
        return (int) Math.round(from + (to - from) * t);
    }
}