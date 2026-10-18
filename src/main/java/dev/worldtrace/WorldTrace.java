package dev.worldtrace;

import dev.worldtrace.config.ConfigManager;
import dev.worldtrace.config.NotificationMode;
import dev.worldtrace.discovery.BiomeTracker;
import dev.worldtrace.discovery.DiscoveryManager;
import dev.worldtrace.discovery.MilestoneTracker;
import dev.worldtrace.discovery.PinTracker;
import dev.worldtrace.hud.NavigationHud;
import dev.worldtrace.journal.EntryEditorScreen;
import dev.worldtrace.journal.JournalScreen;
import dev.worldtrace.model.TraceEntry;
import dev.worldtrace.navigation.NavigationManager;
import dev.worldtrace.navigation.NavigationOverlay;
import dev.worldtrace.notify.DiscoveryNotifier;
import dev.worldtrace.storage.ScreenshotLibrary;
import dev.worldtrace.storage.StoragePaths;
import dev.worldtrace.storage.WorldDataRepository;
import dev.worldtrace.storage.WorldIdentity;
import dev.worldtrace.util.NameFormatting;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.level.LevelProperties;

import java.nio.file.Path;

public final class WorldTrace implements ClientModInitializer {

    private static final int SAMPLE_INTERVAL_TICKS = 20;

    private static StoragePaths storagePaths;
    private static ConfigManager configManager;
    private static WorldDataRepository repository;
    private static ScreenshotLibrary screenshotLibrary;
    private static DiscoveryManager manager;
    private static NavigationManager navigation;
    private static DiscoveryNotifier notifier;
    private static NavigationHud hud;

    private static BiomeTracker biomeTracker;
    private static MilestoneTracker milestoneTracker;
    private static PinTracker pinTracker;

    private static int sampleCounter;

    @Override
    public void onInitializeClient() {
        Path gameDirectory = FabricLoader.getInstance().getGameDir();
        storagePaths = new StoragePaths(gameDirectory);
        configManager = new ConfigManager(storagePaths);
        configManager.load();

        repository = new WorldDataRepository(storagePaths);
        screenshotLibrary = new ScreenshotLibrary(storagePaths);
        manager = new DiscoveryManager(storagePaths, repository, screenshotLibrary, gameDirectory);
        manager.setConfig(configManager.get());

        navigation = new NavigationManager(manager);
        notifier = new DiscoveryNotifier(manager);
        notifier.register();

        milestoneTracker = new MilestoneTracker(manager);
        biomeTracker = new BiomeTracker(manager, milestoneTracker);
        pinTracker = new PinTracker(manager);
        hud = new NavigationHud(manager, navigation, notifier);

        WorldTraceKeys.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onWorldJoined(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onWorldLeft());
        ClientTickEvents.END_CLIENT_TICK.register(WorldTrace::onEndTick);
        HudRenderCallback.EVENT.register((context, tickCounter) -> hud.render(context, 0.0f));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> onClientStopping());

        Runtime.getRuntime().addShutdownHook(new Thread(WorldTrace::onClientStopping, "WorldTrace-Shutdown"));
    }

    private static void onWorldJoined(MinecraftClient client) {
        WorldIdentity identity = resolveIdentity(client);
        manager.loadWorld(identity, configManager.get());
        biomeTracker.reset();
        pinTracker.reset();
        milestoneTracker.reset();
        NavigationOverlay.clear();
        sampleCounter = 0;
        manager.save();
    }

    private static void onWorldLeft() {
        manager.unloadWorld();
        biomeTracker.reset();
        pinTracker.reset();
        NavigationOverlay.clear();
    }

    private static void onClientStopping() {
        try {
            onWorldLeft();
        } finally {
            manager.close();
            repository.close();
        }
    }

    private static WorldIdentity resolveIdentity(MinecraftClient client) {
        if (client.isIntegratedServerRunning()) {
            return WorldIdentity.singleplayer(readSingleplayerName(client));
        }
        ServerInfo serverInfo = client.getCurrentServerEntry();
        return WorldIdentity.multiplayer(serverInfo != null ? serverInfo.address : null);
    }

    private static String readSingleplayerName(MinecraftClient client) {
        IntegratedServer server = client.getServer();
        if (server == null) {
            return null;
        }
        SaveProperties properties = server.getSaveProperties();
        if (properties instanceof LevelProperties levelProperties) {
            return levelProperties.getLevelName();
        }
        return null;
    }

    private static void onEndTick(MinecraftClient client) {
        if (client.player == null || client.world == null || !manager.worldLoaded()) {
            return;
        }

        BlockPos position = client.player.getBlockPos();
        NavigationOverlay.update(
                client.world.getRegistryKey().getValue().toString(),
                position.getX(), position.getY(), position.getZ(),
                client.player.getYaw());

        biomeTracker.tick(client);
        pinTracker.tick(client);

        if (++sampleCounter >= SAMPLE_INTERVAL_TICKS) {
            sampleCounter = 0;
            milestoneTracker.onPositionSample(client.world, position, System.currentTimeMillis());
            milestoneTracker.onCoverageSample(position, System.currentTimeMillis());
        }

        if (WorldTraceKeys.journalPressed()) {

            openJournal(client, notifier.consumeFocusEntryId().orElse(null));
        }
        if (WorldTraceKeys.bookmarkPressed()) {
            quickBookmark(client);
        }
        if (WorldTraceKeys.destinationPressed()) {

            quickToggleDestination(client);
        }
    }

    private static void openJournal(MinecraftClient client, String focusEntryId) {
        if (!manager.worldLoaded()) {
            sendFeedback(client, Text.translatable("worldtrace.message.not_in_world"));
            return;
        }
        if (client.currentScreen instanceof JournalScreen) {
            client.setScreen(null);
            return;
        }
        client.setScreen(JournalScreen.open(manager, focusEntryId));
    }

    private static void quickBookmark(MinecraftClient client) {
        if (!manager.worldLoaded() || client.player == null || client.world == null) {
            sendFeedback(client, Text.translatable("worldtrace.message.not_in_world"));
            return;
        }
        if (manager.store().readOnly()) {
            sendFeedback(client, Text.translatable("worldtrace.message.read_only"));
            return;
        }
        BlockPos position = client.player.getBlockPos();
        String dimension = client.world.getRegistryKey().getValue().toString();
        TraceEntry entry = manager.createBookmark(null, dimension, position.getX(), position.getY(),
                position.getZ(), System.currentTimeMillis());
        sendFeedback(client, Text.translatable("worldtrace.message.bookmarked",
                Text.literal(entry.name())));

        client.setScreen(new EntryEditorScreen(manager, entry, null));
    }

    private static void quickToggleDestination(MinecraftClient client) {
        if (!manager.worldLoaded()) {
            sendFeedback(client, Text.translatable("worldtrace.message.not_in_world"));
            return;
        }
        String current = manager.destinationEntryId();
        if (current != null) {
            manager.clearDestination();
            sendFeedback(client, Text.translatable("worldtrace.message.destination_cleared"));
            return;
        }
        var latest = notifier.lastAnnounced();
        if (latest.isEmpty()) {
            sendFeedback(client, Text.translatable("worldtrace.message.no_recent_discovery"));
            return;
        }
        manager.setDestination(latest.get().id());
        sendFeedback(client, Text.translatable("worldtrace.message.destination_set",
                Text.literal(latest.get().name())));
    }

    private static void sendFeedback(MinecraftClient client, Text text) {
        if (client.player != null) {
            client.player.sendMessage(text, false);
        }
    }

    public static DiscoveryManager manager() {
        return manager;
    }

    public static StoragePaths storagePaths() {
        return storagePaths;
    }

    public static NavigationManager navigation() {
        return navigation;
    }

    public static String describe(TraceEntry entry) {
        return NameFormatting.prettifyIdentifier(entry.name());
    }

    public static NotificationMode notificationMode() {
        return configManager.get().notificationMode();
    }
}
