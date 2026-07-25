package ru.stepanyaa.casinoRoulette;

import org.bukkit.entity.Player;

public class CasinoPlaceholders {
    public static void register(CasinoRoulette plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) return;
        try {
            Class<?> expansion = Class.forName("me.clip.placeholderapi.expansion.PlaceholderExpansion");
            Object obj = java.lang.reflect.Proxy.newProxyInstance(CasinoPlaceholders.class.getClassLoader(), new Class[]{}, (proxy, method, args) -> null);
        } catch (Throwable ignored) {
            plugin.getLogger().info("PlaceholderAPI detected. Use CasinoRoulette API getLeaderboardManager().placeholder(category, position) for external placeholders if PlaceholderAPI jar is not compiled in.");
        }
    }
    public static String resolve(CasinoRoulette plugin, Player player, String params) {
        if (params == null) return "";
        if (params.startsWith("top_")) {
            String[] p = params.split("_");
            if (p.length >= 3) {
                try { return plugin.getLeaderboardManager().placeholder(p[1], Integer.parseInt(p[2])); } catch (Exception ignored) {}
            }
        }
        if (player != null && params.equalsIgnoreCase("chips")) return String.valueOf(plugin.getPlayerChips().getOrDefault(player.getUniqueId(), 0));
        if (player != null && params.equalsIgnoreCase("wins")) return String.valueOf(plugin.getWins().getOrDefault(player.getUniqueId(), 0));
        return "";
    }
}
