package dev.worldtrace.hud;

import dev.worldtrace.config.WorldTraceConfig;
import dev.worldtrace.discovery.DiscoveryManager;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.navigation.NavigationCore;
import dev.worldtrace.navigation.NavigationManager;
import dev.worldtrace.notify.DiscoveryNotifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.Optional;

/**
 * A deliberately small heads-up display: at most two unobtrusive lines above the hotbar.
 *
 * <p>Nothing is drawn over the crosshair, the hotbar, the chat box or the boss bar, and the whole
 * overlay can be switched off in the config. The recent-discovery strip fades out on its own so it
 * never becomes permanent screen furniture.
 */
public final class NavigationHud {

    /** How long the "recent find" line stays on screen. */
    private static final long RECENT_LINE_MILLIS = 6_000L;
    private static final long FADE_MILLIS = 1_500L;

    private final DiscoveryManager manager;
    private final NavigationManager navigation;
    private final DiscoveryNotifier notifier;

    public NavigationHud(DiscoveryManager manager, NavigationManager navigation, DiscoveryNotifier notifier) {
        this.manager = manager;
        this.navigation = navigation;
        this.notifier = notifier;
    }

    public void render(DrawContext context, float delta) {
        WorldTraceConfig config = manager.config();
        if (!config.navigationHud()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer renderer = client.textRenderer;
        int centerX = context.getScaledWindowWidth() / 2;
        // Just above the hotbar, which sits at the very bottom of the screen.
        int bottom = context.getScaledWindowHeight() - 52;

        Optional<String> destination = navigation.hudText();
        if (destination.isPresent()) {
            drawShadowed(context, renderer, destination.get(), centerX, bottom, 0xFFE8EDF4);
            bottom -= renderer.fontHeight + 3;
        }

        Optional<String> recent = recentLine();
        if (recent.isPresent()) {
            drawShadowed(context, renderer, recent.get(), centerX, bottom, 0xFF9FE0A8);
        }
    }

    /**
     * The most recent discovery, shown briefly after it was found. Returns empty once the line has
     * faded, so the HUD returns to its normal, quieter state.
     */
    private Optional<String> recentLine() {
        Optional<TraceEntry> entry = notifier.lastAnnounced();
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        long elapsed = System.currentTimeMillis() - notifier.lastAnnouncedAtMillis();
        if (elapsed > RECENT_LINE_MILLIS) {
            return Optional.empty();
        }
        String text = "Found: " + DiscoveryNotifier.describeRecent(entry.get(), System.currentTimeMillis());
        if (elapsed > RECENT_LINE_MILLIS - FADE_MILLIS) {
            text = text + "  (J)";
        }
        return Optional.of(text);
    }

    private static void drawShadowed(DrawContext context, TextRenderer renderer, String text, int centerX,
                                     int y, int color) {
        int x = centerX - renderer.getWidth(text) / 2;
        context.drawTextWithShadow(renderer, text, x, y, color);
    }

    /** Exposed for tests and future features: a short relative heading for a navigation result. */
    public static String headingLabel(NavigationCore.Result result) {
        return result.isDirectional() ? NavigationCore.relativeLabel(result.horizontalDelta()) : "";
    }
}