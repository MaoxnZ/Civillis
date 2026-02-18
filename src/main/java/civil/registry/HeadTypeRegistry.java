package civil.registry;

import net.minecraft.entity.EntityType;

import java.util.Map;
import java.util.Set;

/**
 * Data-driven head type registry.
 *
 * <p>Maps skull type strings (e.g. {@code "ZOMBIE"}, {@code "SKELETON"}) to
 * {@link HeadTypeEntry} records that define the corresponding entity type and
 * behavioral flags. Populated at server startup / {@code /reload} by
 * {@link HeadTypeLoader} from datapack JSON files
 * ({@code data/<namespace>/civil_heads/*.json}).
 *
 * <p>This class is a <b>pure data holder</b> with no side effects.
 * Thread safety: the internal map reference is {@code volatile} and replaced
 * atomically on reload; reads are lock-free.
 */
public final class HeadTypeRegistry {

    /**
     * A single head type definition.
     *
     * @param skullType      registry key matching {@code SkullBlock.SkullType.toString()}
     *                       (e.g. "ZOMBIE", "BLAZE")
     * @param entityType     the mob entity this skull represents; may be null
     *                       if the skull contributes to zone/range but has no
     *                       direct mob mapping
     * @param enabled        master switch — if false this head type is completely
     *                       invisible to all gameplay systems (suppress, nearby,
     *                       conversion). Defaults to true.
     * @param convertEnabled if true, this head participates in the conversion
     *                       pool (weighted random replacement when >= 3 heads).
     *                       A head with enabled=true but convert=false still
     *                       counts toward nearby head count and suppress range.
     * @param dimensions     optional dimension whitelist (e.g. {@code ["minecraft:the_nether"]}).
     *                       If null, the head type is active in all dimensions.
     *                       If non-null, only active in the listed dimensions;
     *                       in other dimensions the head is treated as purely decorative.
     */
    public record HeadTypeEntry(
            String skullType,
            EntityType<?> entityType,
            boolean enabled,
            boolean convertEnabled,
            Set<String> dimensions
    ) {
        /**
         * Check if this head type is active in the given dimension.
         *
         * @param dimId dimension identifier (e.g. "minecraft:the_nether")
         * @return true if no dimension restriction or dimId is whitelisted
         */
        public boolean isActiveIn(String dimId) {
            return dimensions == null || dimensions.contains(dimId);
        }
    }

    private static volatile Map<String, HeadTypeEntry> entries = Map.of();

    private HeadTypeRegistry() {}

    /**
     * Look up a head type entry by skull type string.
     *
     * @param skullType the skull type name (e.g. "ZOMBIE")
     * @return the entry, or {@code null} if not registered
     */
    public static HeadTypeEntry get(String skullType) {
        return entries.get(skullType);
    }

    /**
     * Check whether a skull type is registered <b>and</b> enabled.
     * Convenience method for callers that only need a boolean gate
     * (e.g. SonarScanManager head-zone loops).
     *
     * @param skullType the skull type name
     * @return true if the type exists in the registry and its {@code enabled} flag is true
     */
    public static boolean isEnabled(String skullType) {
        HeadTypeEntry entry = entries.get(skullType);
        return entry != null && entry.enabled();
    }

    /**
     * Dimension-aware variant: checks registered, enabled, <b>and</b> active
     * in the given dimension.
     *
     * @param skullType the skull type name
     * @param dimId     dimension identifier (e.g. "minecraft:the_nether")
     * @return true if the type exists, is enabled, and its dimension whitelist
     *         permits the given dimension
     */
    public static boolean isEnabled(String skullType, String dimId) {
        HeadTypeEntry entry = entries.get(skullType);
        return entry != null && entry.enabled() && entry.isActiveIn(dimId);
    }

    /**
     * Atomically replace the entire head type map.
     * Called by {@link HeadTypeLoader} during datapack reload.
     *
     * @param newEntries fully resolved skull type → entry map
     */
    public static void reload(Map<String, HeadTypeEntry> newEntries) {
        entries = Map.copyOf(newEntries);
    }
}
