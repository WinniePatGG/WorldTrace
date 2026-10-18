package dev.worldtrace.discovery;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class StructureCatalog {

    public record StructureType(String displayName, List<String> structureIds, Set<Block> markers,
                                int defaultRadius) {

        public boolean matches(String structureId) {
            return structureId != null && structureIds.contains(structureId.toLowerCase(Locale.ROOT));
        }
    }

    private static final Map<String, StructureType> BY_STRUCTURE_ID = new LinkedHashMap<>();

    private StructureCatalog() {
    }

    private static void register(String displayName, int defaultRadius, Set<Block> markers, String... ids) {
        List<String> normalized = new ArrayList<>(ids.length);
        for (String id : ids) {
            normalized.add(id.toLowerCase(Locale.ROOT));
        }
        StructureType type = new StructureType(displayName, List.copyOf(normalized), Set.copyOf(markers), defaultRadius);
        for (String id : normalized) {
            BY_STRUCTURE_ID.put(id, type);
        }
    }

    static {
        register("Village", 64,
                Set.of(Blocks.BELL, Blocks.LECTERN),
                "minecraft:village_plains", "minecraft:village_desert", "minecraft:village_savanna",
                "minecraft:village_snowy", "minecraft:village_taiga");

        register("Desert Temple", 48,
                Set.of(Blocks.CHISELED_SANDSTONE, Blocks.TERRACOTTA),
                "minecraft:desert_pyramid");

        register("Ocean Monument", 48,
                Set.of(Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS),
                "minecraft:ocean_monument");

        register("Woodland Mansion", 80,
                Set.of(Blocks.COPPER_BULB),
                "minecraft:mansion");

        register("Ancient City", 80,
                Set.of(Blocks.SCULK, Blocks.SCULK_CATALYST, Blocks.SCULK_SHRIEKER, Blocks.REINFORCED_DEEPSLATE),
                "minecraft:ancient_city");

        register("Nether Fortress", 96,
                Set.of(Blocks.NETHER_BRICKS),
                "minecraft:fortress");

        register("Bastion Remnant", 96,
                Set.of(Blocks.CHISELED_POLISHED_BLACKSTONE, Blocks.GILDED_BLACKSTONE),
                "minecraft:bastion_remnant");

        register("End City", 96,
                Set.of(Blocks.END_STONE_BRICKS, Blocks.PURPUR_BLOCK, Blocks.END_ROD),
                "minecraft:end_city");

        register("Stronghold", 96,
                Set.of(Blocks.END_PORTAL_FRAME, Blocks.END_PORTAL),
                "minecraft:stronghold");

        register("Trial Chambers", 64,
                Set.of(Blocks.HEAVY_CORE, Blocks.TUFF_BRICKS),
                "minecraft:trial_chambers");
    }

    public static StructureType lookup(String structureId) {
        if (structureId == null) {
            return null;
        }
        return BY_STRUCTURE_ID.get(structureId.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean isKnown(String structureId) {
        return lookup(structureId) != null;
    }

    public static int suggestedRadius(String structureId) {
        StructureType type = lookup(structureId);
        return type == null ? 48 : type.defaultRadius();
    }

    public static List<String> allStructureIds() {
        return List.copyOf(BY_STRUCTURE_ID.keySet());
    }

    public static List<StructureType> allTypes() {
        List<StructureType> types = new ArrayList<>();
        for (StructureType type : BY_STRUCTURE_ID.values()) {
            if (!types.contains(type)) {
                types.add(type);
            }
        }
        return List.copyOf(types);
    }
}
