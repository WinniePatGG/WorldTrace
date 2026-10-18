package dev.worldtrace.util;

import dev.worldtrace.config.NotificationMode;
import dev.worldtrace.config.RevisitMode;
import dev.worldtrace.config.WorldTraceConfig;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputValidationTest {

    @Test
    @DisplayName("Names are trimmed and whitespace-collapsed")
    void namesAreCollapsed() {
        assertEquals("My Base", InputValidation.normalizeName("  My    Base  "));
        assertEquals("Fallback", InputValidation.normalizeName("   ", "Fallback"));
        assertEquals("Fallback", InputValidation.normalizeName(null, "Fallback"));
    }

    @Test
    @DisplayName("Overlong names are truncated to the documented limit")
    void namesAreTruncated() {
        String long_ = "n".repeat(500);
        assertEquals(InputValidation.MAX_NAME_LENGTH, InputValidation.normalizeName(long_).length());
    }

    @Test
    @DisplayName("Tags become lowercase slugs and unsafe characters are dropped")
    void tagsAreNormalised() {
        assertEquals("my-base", InputValidation.normalizeTag("  My Base!  "));
        assertEquals("fancy-tag", InputValidation.normalizeTag("--Fancy--Tag--"));
        assertEquals("a-b", InputValidation.normalizeTag("a   b"));
        assertEquals("", InputValidation.normalizeTag("!!!"));
        assertEquals("", InputValidation.normalizeTag(null));
    }

    @Test
    @DisplayName("Tag collections are de-duplicated, capped and order-preserving")
    void tagCollectionsAreCleaned() {
        List<String> tags = InputValidation.normalizeTags(
                List.of("Base", "base", "storage", "!!!", "home", "a", "b", "c", "d", "e", "f", "g", "h", "i"));
        assertEquals(InputValidation.MAX_TAGS, tags.size());
        assertEquals("base", tags.get(0));
        assertEquals("storage", tags.get(1));
        assertEquals("home", tags.get(2));
    }

    @Test
    @DisplayName("Tag strings split on commas and whitespace")
    void tagParsing() {
        assertEquals(List.of("base", "storage"), InputValidation.parseTags("base, storage"));
        assertEquals(List.of("base", "storage"), InputValidation.parseTags("base storage"));
        assertEquals(List.of("base", "storage"), InputValidation.parseTags("base,,storage,"));
        assertTrue(InputValidation.parseTags("   ").isEmpty());
        assertTrue(InputValidation.parseTags(null).isEmpty());
    }

    @Test
    @DisplayName("Notes stay empty when blank instead of becoming a placeholder")
    void notesMayBeEmpty() {
        assertEquals("", InputValidation.normalizeNotes(null));
        assertEquals("", InputValidation.normalizeNotes("    "));
        assertEquals("real note", InputValidation.normalizeNotes("  real note  "));
        assertEquals(InputValidation.MAX_NOTES_LENGTH,
                InputValidation.normalizeNotes("x".repeat(5_000)).length());
    }

    @Test
    @DisplayName("Registry identifiers become readable labels")
    void identifiersArePrettified() {
        assertEquals("Cherry Grove", NameFormatting.prettifyIdentifier("minecraft:cherry_grove"));
        assertEquals("The Nether", NameFormatting.prettifyIdentifier("minecraft:the_nether"));
        assertEquals("Ancient City", NameFormatting.prettifyIdentifier("ancient_city"));
        assertEquals("Deep Dark", NameFormatting.prettifyIdentifier("deep_dark"));
        assertEquals("Unknown", NameFormatting.prettifyIdentifier(null));
        assertEquals("Unknown", NameFormatting.prettifyIdentifier("minecraft:"));
    }

    @Test
    @DisplayName("Overlong labels are ellipsized")
    void labelsAreEllipsized() {
        assertEquals("abc", NameFormatting.ellipsize("abc", 5));
        assertEquals("ab…", NameFormatting.ellipsize("abcdef", 3));
    }

    @Test
    @DisplayName("Log sanitising removes newlines so records cannot forge log lines")
    void logSanitising() {
        assertEquals("a b", InputValidation.sanitizeForLog("a\nb"));
        assertEquals("null", InputValidation.sanitizeForLog(null));
    }

    @Test
    @DisplayName("Config round trips through JSON")
    void configRoundTrip() {
        WorldTraceConfig original = WorldTraceConfig.builder()
                .biomeDiscoveries(false)
                .notificationMode(NotificationMode.OFF)
                .revisitMode(RevisitMode.RECORD_SEPARATE)
                .showTechnicalIds(true)
                .accentRgb(0x123456)
                .maxEntries(5_000)
                .worldIdOverride("my-world")
                .build();

        WorldTraceConfig restored = WorldTraceConfig.fromJson(original.toJson());

        assertFalse(restored.biomeDiscoveries());
        assertEquals(NotificationMode.OFF, restored.notificationMode());
        assertEquals(RevisitMode.RECORD_SEPARATE, restored.revisitMode());
        assertTrue(restored.showTechnicalIds());
        assertEquals(0x123456, restored.accentRgb());
        assertEquals(5_000, restored.maxEntries());
        assertEquals("my-world", restored.worldIdOverride());
        assertTrue(restored.loadWarnings().isEmpty());
    }

    @Test
    @DisplayName("An invalid enum value falls back and is reported")
    void invalidEnumFallsBack() {
        JsonObject json = WorldTraceConfig.defaults().toJson();
        json.addProperty("notificationMode", "TELEPATHY");

        WorldTraceConfig config = WorldTraceConfig.fromJson(json);

        assertEquals(NotificationMode.ACTION_BAR, config.notificationMode());
        assertEquals(1, config.loadWarnings().size());
        assertTrue(config.loadWarnings().get(0).contains("notificationMode"));
    }

    @Test
    @DisplayName("An out-of-range entry limit is clamped and reported")
    void entryLimitIsClamped() {
        JsonObject json = WorldTraceConfig.defaults().toJson();
        json.addProperty("maxEntries", -5);

        WorldTraceConfig config = WorldTraceConfig.fromJson(json);
        assertEquals(WorldTraceConfig.MAX_ENTRIES_MIN, config.maxEntries());
        assertTrue(config.loadWarnings().get(0).contains("maxEntries"));

        json.addProperty("maxEntries", 10_000_000);
        assertEquals(WorldTraceConfig.MAX_ENTRIES_MAX, WorldTraceConfig.fromJson(json).maxEntries());
    }

    @Test
    @DisplayName("A malformed colour falls back to the default accent")
    void malformedColourFallsBack() {
        JsonObject json = WorldTraceConfig.defaults().toJson();
        json.addProperty("accentColor", "not-a-colour");

        WorldTraceConfig config = WorldTraceConfig.fromJson(json);
        assertEquals(WorldTraceConfig.defaults().accentRgb(), config.accentRgb());
        assertTrue(config.loadWarnings().get(0).contains("accentColor"));
    }

    @Test
    @DisplayName("Colours accept RRGGBB with or without a hash")
    void colourParsing() {
        assertEquals(0xABCDEF, WorldTraceConfig.parseColor("ABCDEF"));
        assertEquals(0xABCDEF, WorldTraceConfig.parseColor("#ABCDEF"));
        assertEquals(0xABCDEF, WorldTraceConfig.parseColor("  #abcdef "));
        assertEquals(null, WorldTraceConfig.parseColor("#ABC"));
        assertEquals(null, WorldTraceConfig.parseColor("GGGGGG"));
        assertEquals(null, WorldTraceConfig.parseColor(null));
        assertEquals("#ABCDEF", WorldTraceConfig.formatColor(0xABCDEF));
    }

    @Test
    @DisplayName("A wrong boolean type is rejected rather than silently coerced")
    void wrongBooleanTypeIsRejected() {
        JsonObject json = WorldTraceConfig.defaults().toJson();
        json.addProperty("biomeDiscoveries", "yes please");

        WorldTraceConfig config = WorldTraceConfig.fromJson(json);
        assertTrue(config.biomeDiscoveries(), "The default is kept");
        assertTrue(config.loadWarnings().get(0).contains("biomeDiscoveries"));
    }

    @Test
    @DisplayName("An empty or null config document yields the defaults")
    void emptyDocumentYieldsDefaults() {
        assertEquals(WorldTraceConfig.defaults().toJson(), WorldTraceConfig.fromJson(new JsonObject()).toJson());
        assertEquals(WorldTraceConfig.defaults().toJson(), WorldTraceConfig.fromJson(null).toJson());
    }

    @Test
    @DisplayName("Notification and revisit modes parse leniently but never to null")
    void enumsNeverReturnNull() {
        assertEquals(NotificationMode.OFF, NotificationMode.fromString("off", NotificationMode.ACTION_BAR));
        assertEquals(NotificationMode.ACTION_BAR, NotificationMode.fromString("nope", NotificationMode.ACTION_BAR));
        assertEquals(NotificationMode.ACTION_BAR, NotificationMode.fromString(null, NotificationMode.ACTION_BAR));
        assertEquals(RevisitMode.ANNOUNCE, RevisitMode.fromString("announce", RevisitMode.COUNT_ONLY));
        assertNotEquals(RevisitMode.COUNT_ONLY, RevisitMode.fromString("x", RevisitMode.ANNOUNCE));
    }
}
