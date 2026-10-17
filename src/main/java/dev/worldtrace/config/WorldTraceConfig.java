package dev.worldtrace.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.worldtrace.util.InputValidation;

import java.util.ArrayList;
import java.util.List;

/**
 * Mod-wide preferences.
 *
 * <p>The config is plain JSON in {@code worldtrace/config.json}. Every field is validated on load:
 * an unknown enum name, a negative number or a malformed colour falls back to the default and is
 * reported through {@link #loadWarnings()} instead of aborting the launch.
 */
public final class WorldTraceConfig {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ENTRIES_MIN = 100;
    public static final int MAX_ENTRIES_MAX = 200_000;

    private final boolean biomeDiscoveries;
    private final boolean structurePinDiscoveries;
    private final boolean milestoneDiscoveries;
    private final NotificationMode notificationMode;
    private final RevisitMode revisitMode;
    private final boolean navigationHud;
    private final boolean showTechnicalIds;
    private final int accentRgb;
    private final boolean autoScreenshotOnDiscovery;
    private final int maxEntries;
    private final String worldIdOverride;
    private final List<String> loadWarnings;

    private WorldTraceConfig(Builder builder) {
        this.biomeDiscoveries = builder.biomeDiscoveries;
        this.structurePinDiscoveries = builder.structurePinDiscoveries;
        this.milestoneDiscoveries = builder.milestoneDiscoveries;
        this.notificationMode = builder.notificationMode;
        this.revisitMode = builder.revisitMode;
        this.navigationHud = builder.navigationHud;
        this.showTechnicalIds = builder.showTechnicalIds;
        this.accentRgb = builder.accentRgb;
        this.autoScreenshotOnDiscovery = builder.autoScreenshotOnDiscovery;
        this.maxEntries = builder.maxEntries;
        this.worldIdOverride = builder.worldIdOverride;
        this.loadWarnings = List.copyOf(builder.loadWarnings);
    }

    public static WorldTraceConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    // ------------------------------------------------------------- accessors

    public boolean biomeDiscoveries() {
        return biomeDiscoveries;
    }

    public boolean structurePinDiscoveries() {
        return structurePinDiscoveries;
    }

    public boolean milestoneDiscoveries() {
        return milestoneDiscoveries;
    }

    public NotificationMode notificationMode() {
        return notificationMode;
    }

    public RevisitMode revisitMode() {
        return revisitMode;
    }

    public boolean navigationHud() {
        return navigationHud;
    }

    /** Whether registry identifiers are shown alongside the pretty names in the journal. */
    public boolean showTechnicalIds() {
        return showTechnicalIds;
    }

    /** Packed 0xRRGGBB accent colour used for headers and category highlights. */
    public int accentRgb() {
        return accentRgb;
    }

    public boolean autoScreenshotOnDiscovery() {
        return autoScreenshotOnDiscovery;
    }

    public int maxEntries() {
        return maxEntries;
    }

    public String worldIdOverride() {
        return worldIdOverride;
    }

    public List<String> loadWarnings() {
        return loadWarnings;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.biomeDiscoveries = biomeDiscoveries;
        builder.structurePinDiscoveries = structurePinDiscoveries;
        builder.milestoneDiscoveries = milestoneDiscoveries;
        builder.notificationMode = notificationMode;
        builder.revisitMode = revisitMode;
        builder.navigationHud = navigationHud;
        builder.showTechnicalIds = showTechnicalIds;
        builder.accentRgb = accentRgb;
        builder.autoScreenshotOnDiscovery = autoScreenshotOnDiscovery;
        builder.maxEntries = maxEntries;
        builder.worldIdOverride = worldIdOverride;
        return builder;
    }

    // -------------------------------------------------------- (de)serialising

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("schemaVersion", SCHEMA_VERSION);
        json.addProperty("biomeDiscoveries", biomeDiscoveries);
        json.addProperty("structurePinDiscoveries", structurePinDiscoveries);
        json.addProperty("milestoneDiscoveries", milestoneDiscoveries);
        json.addProperty("notificationMode", notificationMode.name());
        json.addProperty("revisitMode", revisitMode.name());
        json.addProperty("navigationHud", navigationHud);
        json.addProperty("showTechnicalIds", showTechnicalIds);
        json.addProperty("accentColor", formatColor(accentRgb));
        json.addProperty("autoScreenshotOnDiscovery", autoScreenshotOnDiscovery);
        json.addProperty("maxEntries", maxEntries);
        json.addProperty("worldIdOverride", worldIdOverride);
        return json;
    }

    public static WorldTraceConfig fromJson(JsonObject json) {
        Builder builder = builder();
        if (json == null) {
            return builder.build();
        }

        builder.biomeDiscoveries = readBoolean(json, "biomeDiscoveries", builder.biomeDiscoveries, builder);
        builder.structurePinDiscoveries =
                readBoolean(json, "structurePinDiscoveries", builder.structurePinDiscoveries, builder);
        builder.milestoneDiscoveries =
                readBoolean(json, "milestoneDiscoveries", builder.milestoneDiscoveries, builder);
        builder.navigationHud = readBoolean(json, "navigationHud", builder.navigationHud, builder);
        builder.showTechnicalIds = readBoolean(json, "showTechnicalIds", builder.showTechnicalIds, builder);
        builder.autoScreenshotOnDiscovery = readBoolean(json, "autoScreenshotOnDiscovery",
                builder.autoScreenshotOnDiscovery, builder);

        builder.notificationMode = readEnum(json, "notificationMode", builder.notificationMode, builder);
        builder.revisitMode = readEnum(json, "revisitMode", builder.revisitMode, builder);

        String color = readString(json, "accentColor", builder);
        if (color != null) {
            Integer parsed = parseColor(color);
            if (parsed == null) {
                builder.loadWarnings.add("Invalid accentColor '" + color + "'; using the default.");
            } else {
                builder.accentRgb = parsed;
            }
        }

        JsonElement maxEntries = json.get("maxEntries");
        if (maxEntries != null && maxEntries.isJsonPrimitive() && maxEntries.getAsJsonPrimitive().isNumber()) {
            int raw = maxEntries.getAsInt();
            int clamped = Math.max(MAX_ENTRIES_MIN, Math.min(MAX_ENTRIES_MAX, raw));
            if (clamped != raw) {
                builder.loadWarnings.add("maxEntries " + raw + " is out of range ["
                        + MAX_ENTRIES_MIN + ", " + MAX_ENTRIES_MAX + "]; clamped to " + clamped + ".");
            }
            builder.maxEntries = clamped;
        }

        builder.worldIdOverride = InputValidation.truncate(
                readString(json, "worldIdOverride", builder), InputValidation.MAX_LABEL_LENGTH);
        return builder.build();
    }

    // ---------------------------------------------------------------- helpers

    private static boolean readBoolean(JsonObject json, String key, boolean fallback, Builder builder) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        builder.loadWarnings.add("Config field '" + key + "' must be true or false; using " + fallback + ".");
        return fallback;
    }

    private static <E extends Enum<E>> E readEnum(JsonObject json, String key, E fallback, Builder builder) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        String raw = element.getAsString();
        try {
            return Enum.valueOf(fallback.getDeclaringClass(), raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            builder.loadWarnings.add("Config field '" + key + "' has the unknown value '"
                    + InputValidation.sanitizeForLog(raw) + "'; using " + fallback + ".");
            return fallback;
        }
    }

    private static String readString(JsonObject json, String key, Builder builder) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        return element.getAsString();
    }

    /** Accepts {@code RRGGBB} and {@code #RRGGBB}. */
    public static Integer parseColor(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (value.length() != 6) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value, 16);
            if (parsed < 0 || parsed > 0xFFFFFF) {
                return null;
            }
            return parsed;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String formatColor(int rgb) {
        return String.format(java.util.Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
    }

    /** Mutable staging area; each loaded field is validated before it lands here. */
    public static final class Builder {
        private boolean biomeDiscoveries = true;
        private boolean structurePinDiscoveries = true;
        private boolean milestoneDiscoveries = true;
        private NotificationMode notificationMode = NotificationMode.ACTION_BAR;
        private RevisitMode revisitMode = RevisitMode.COUNT_ONLY;
        private boolean navigationHud = true;
        private boolean showTechnicalIds = false;
        private int accentRgb = 0x7FC4E0;
        private boolean autoScreenshotOnDiscovery = false;
        private int maxEntries = 20_000;
        private String worldIdOverride = "";
        private final List<String> loadWarnings = new ArrayList<>();

        public Builder biomeDiscoveries(boolean value) {
            this.biomeDiscoveries = value;
            return this;
        }

        public Builder structurePinDiscoveries(boolean value) {
            this.structurePinDiscoveries = value;
            return this;
        }

        public Builder milestoneDiscoveries(boolean value) {
            this.milestoneDiscoveries = value;
            return this;
        }

        public Builder notificationMode(NotificationMode value) {
            this.notificationMode = value == null ? NotificationMode.ACTION_BAR : value;
            return this;
        }

        public Builder revisitMode(RevisitMode value) {
            this.revisitMode = value == null ? RevisitMode.COUNT_ONLY : value;
            return this;
        }

        public Builder navigationHud(boolean value) {
            this.navigationHud = value;
            return this;
        }

        public Builder showTechnicalIds(boolean value) {
            this.showTechnicalIds = value;
            return this;
        }

        public Builder accentRgb(int value) {
            this.accentRgb = value & 0xFFFFFF;
            return this;
        }

        public Builder autoScreenshotOnDiscovery(boolean value) {
            this.autoScreenshotOnDiscovery = value;
            return this;
        }

        public Builder maxEntries(int value) {
            this.maxEntries = Math.max(MAX_ENTRIES_MIN, Math.min(MAX_ENTRIES_MAX, value));
            return this;
        }

        public Builder worldIdOverride(String value) {
            this.worldIdOverride = InputValidation.truncate(
                    value == null ? "" : value.trim(), InputValidation.MAX_LABEL_LENGTH);
            return this;
        }

        public WorldTraceConfig build() {
            return new WorldTraceConfig(this);
        }
    }
}