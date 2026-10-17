package dev.worldtrace.util;

/**
 * Turns registry identifiers such as {@code minecraft:cherry_grove} into human readable labels.
 *
 * <p>WorldTrace deliberately derives names from the identifier instead of a translation key:
 * biome and structure display names are not guaranteed to be translatable for modded content, and
 * a deterministic transformation keeps the journal readable in every language.
 */
public final class NameFormatting {

    private NameFormatting() {
    }

    public static String prettifyIdentifier(String identifier) {
        if (identifier == null) {
            return "Unknown";
        }
        String value = identifier.trim();
        int colon = value.indexOf(':');
        if (colon >= 0) {
            // "minecraft:cherry_grove" -> "cherry_grove"; "minecraft:" -> nothing usable left.
            String tail = value.substring(colon + 1);
            value = tail.isBlank() ? "" : tail;
        }
        value = value.replace('_', ' ').replace('-', ' ').replace('.', ' ').trim();
        if (value.isEmpty()) {
            return "Unknown";
        }

        StringBuilder result = new StringBuilder(value.length());
        boolean startOfWord = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ' ') {
                result.append(c);
                startOfWord = true;
                continue;
            }
            if (startOfWord && Character.isLowerCase(c)) {
                result.append(Character.toUpperCase(c));
                startOfWord = false;
            } else {
                result.append(c);
                startOfWord = false;
            }
        }
        return result.toString();
    }

    /**
     * Shortens a label for compact UI slots, appending an ellipsis when it had to be cut.
     */
    public static String ellipsize(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        if (maxLength <= 1) {
            return value.substring(0, Math.max(0, maxLength));
        }
        return value.substring(0, maxLength - 1) + "…";
    }
}