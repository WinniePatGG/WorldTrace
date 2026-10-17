package dev.worldtrace.discovery;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The structures WorldTrace knows how to corroborate, and the blocks that prove they are there.
 *
 * <p>A Fabric client never receives structure start data from the server, so WorldTrace cannot make
 * a claim about a structure on its own. What it can do is verify a claim the player has supplied:
 * when a watch pin is reached, the blocks inside the already-loaded chunks are checked for the
 * characteristic building materials of that structure type. Finding them upgrades the entry to
 * <em>confirmed</em>; not finding them still creates an entry, clearly marked <em>unconfirmed</em>.
 *
 * <p>Marker blocks are heuristics, and the journal says so. A player who builds a sandstone
 * staircase next to a pin may see a temple listed as unconfirmed rather than confirmed — which is the
 * honest outcome.
 */
public final class StructureCatalog {

    /**
     * @param structureIds  vanilla structure registry ids this type covers
     * @param markers       blocks that strongly indicate the structure; scanning stops at the first hit
     * @param defaultRadius suggested pin radius, large enough to contain the whole structure
     */
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

    /** Returns the type covering {@code structureId}, or {@code null} when it is not known. */
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

    /** Every catalogued structure id, offered in the watch-pin dialog. */
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