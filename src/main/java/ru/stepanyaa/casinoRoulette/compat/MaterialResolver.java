package ru.stepanyaa.casinoRoulette.compat;

import ru.stepanyaa.casinoRoulette.platform.PlatformAdapter;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MaterialResolver {

    public static final String LAST_RESORT = "STONE";

    private final PlatformAdapter adapter;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> fallbacks = new LinkedHashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    public MaterialResolver(PlatformAdapter adapter) {
        this.adapter = adapter;
        registerDefaults();
    }

    private void registerDefaults() {

        alias("INK_SACK", "INK_SAC");
        alias("WATCH", "CLOCK");
        alias("SULPHUR", "GUNPOWDER");
        alias("SIGN", "OAK_SIGN");
        alias("WORKBENCH", "CRAFTING_TABLE");
        alias("ENDER_PORTAL_FRAME", "END_PORTAL_FRAME");
        alias("DIAMOND_SHOVEL", "DIAMOND_SPADE");
        alias("GOLD_INGOT", "GOLD_INGOT");

        fallback("BLACK_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", "THIN_GLASS", "GLASS");
        fallback("RED_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", "THIN_GLASS", "GLASS");
        fallback("GREEN_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", "THIN_GLASS", "GLASS");
        fallback("GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE", "THIN_GLASS", "GLASS");

        fallback("NETHERITE_INGOT", "DIAMOND", "GOLD_INGOT");
        fallback("NETHERITE_BLOCK", "DIAMOND_BLOCK", "GOLD_BLOCK");
        fallback("HONEYCOMB", "GOLD_NUGGET", "GOLD_INGOT");
        fallback("CRYING_OBSIDIAN", "OBSIDIAN");
        fallback("SPYGLASS", "COMPASS");
        fallback("AMETHYST_SHARD", "DIAMOND", "EMERALD");
        fallback("ECHO_SHARD", "AMETHYST_SHARD", "DIAMOND");
        fallback("PLAYER_HEAD", "SKULL_ITEM", "PAPER");
        fallback("BARRIER", "RED_STAINED_GLASS_PANE", "GLASS");
    }

    public void alias(String from, String to) {
        aliases.put(normalise(from), normalise(to));
    }

    public void fallback(String id, String... chain) {
        fallbacks.put(normalise(id), Collections.unmodifiableList(Arrays.asList(chain)));
    }

    public String resolve(String requested) {
        if (requested == null || requested.trim().isEmpty()) {
            return LAST_RESORT;
        }
        String key = normalise(requested);
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        String resolved = compute(key);
        cache.put(key, resolved);
        return resolved;
    }

    private String compute(String key) {
        if (adapter.materialExists(key)) {
            return key;
        }
        String alias = aliases.get(key);
        if (alias != null && adapter.materialExists(alias)) {
            return alias;
        }
        List<String> chain = fallbacks.get(key);
        if (chain != null) {
            for (String candidate : chain) {
                String normalised = normalise(candidate);
                if (adapter.materialExists(normalised)) {
                    adapter.logger().fine("Material " + key + " unavailable, using " + normalised);
                    return normalised;
                }
            }
        }
        adapter.logger().warning(
                "Unknown material '" + key + "' in configuration, falling back to " + LAST_RESORT
                        + ". Check your config for this server version.");
        return LAST_RESORT;
    }

    public static String normalise(String id) {
        String value = id.trim();
        int colon = value.indexOf(':');
        if (colon >= 0) {
            value = value.substring(colon + 1);
        }
        return value.toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    public void invalidate() {
        cache.clear();
    }
}
