package dev.worldtrace.notify;

import dev.worldtrace.config.NotificationMode;
import dev.worldtrace.config.RevisitMode;
import dev.worldtrace.config.WorldTraceConfig;
import dev.worldtrace.discovery.DiscoveryEvent;
import dev.worldtrace.discovery.DiscoveryManager;
import dev.worldtrace.model.EntryCategory;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.util.NameFormatting;
import dev.worldtrace.util.Timestamps;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Optional;

/**
 * Announces discoveries without interrupting play.
 *
 * <p>The action bar is used rather than chat or a toast: it appears above the hotbar for a couple of
 * seconds, leaves the chat history clean, and cannot be missed. No sound is played — a quiet mod is
 * a mod people keep installed.
 *
 * <p>Opening the journal within {@link #FOCUS_WINDOW_MILLIS} of a notification jumps straight to that
 * entry. That is the "quick way to the relevant entry" affordance, achieved without needing
 * clickable action-bar text and a matching click handler.
 */
public final class DiscoveryNotifier {

    static final long FOCUS_WINDOW_MILLIS = 10_000L;

    private final DiscoveryManager manager;

    private TraceEntry lastAnnounced;
    private long lastAnnouncedAtMillis;
    private String pendingFocusEntryId;

    public DiscoveryNotifier(DiscoveryManager manager) {
        this.manager = manager;
    }

    /** Subscribes to journal events for the lifetime of the mod. */
    public void register() {
        manager.addListener(this::onDiscoveryEvent);
    }

    private void onDiscoveryEvent(DiscoveryEvent event) {
        switch (event.kind()) {
            case CREATED -> announce(event.entry());
            case REVISITED -> {
                if (manager.config().revisitMode() == RevisitMode.ANNOUNCE) {
                    announceRevisit(event.entry());
                }
            }
            default -> {
                // Edits and deletions are the player's own actions; they do not need announcing.
            }
        }
    }

    private void announce(TraceEntry entry) {
        lastAnnounced = entry;
        lastAnnouncedAtMillis = System.currentTimeMillis();
        pendingFocusEntryId = entry.id();

        WorldTraceConfig config = manager.config();
        if (config.autoScreenshotOnDiscovery()) {
            // Best effort; silently skipped when the player did not just take a screenshot.
            manager.attachMostRecentScreenshot(entry.id());
        }
        if (config.notificationMode() == NotificationMode.OFF) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        client.player.sendMessage(buildMessage(entry), true);
    }

    private void announceRevisit(TraceEntry entry) {
        if (manager.config().notificationMode() == NotificationMode.OFF) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        client.player.sendMessage(Text.translatable("worldtrace.notification.revisit", entry.name()), true);
    }

    private MutableText buildMessage(TraceEntry entry) {
        MutableText message = Text.translatable("worldtrace.notification.found", Text.literal(entry.name()));
        if (entry.category() == EntryCategory.STRUCTURE && !entry.confirmed()) {
            // Never present a guess as a fact.
            message.append(Text.translatable("worldtrace.notification.unconfirmed"));
        } else {
            message.append(Text.translatable("worldtrace.notification.category",
                    Text.literal(entry.category().displayName())));
        }
        message.append(Text.translatable("worldtrace.notification.hint"));
        return message;
    }

    /**
     * Entry the journal should open on, if the player just received a discovery notification.
     * Consumed once so a later manual journal opening behaves normally.
     */
    public Optional<String> consumeFocusEntryId() {
        String candidate = pendingFocusEntryId;
        pendingFocusEntryId = null;
        if (candidate == null) {
            return Optional.empty();
        }
        long elapsed = System.currentTimeMillis() - lastAnnouncedAtMillis;
        if (elapsed > FOCUS_WINDOW_MILLIS || manager.store().entry(candidate).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidate);
    }

    public Optional<TraceEntry> lastAnnounced() {
        return Optional.ofNullable(lastAnnounced);
    }

    public long lastAnnouncedAtMillis() {
        return lastAnnouncedAtMillis;
    }

    /** Summary line for the HUD's "recent find" strip. */
    public static String describeRecent(TraceEntry entry, long now) {
        return NameFormatting.prettifyIdentifier(entry.name()) + " · "
                + Timestamps.formatRelative(entry.firstSeenAtMillis(), now);
    }
}