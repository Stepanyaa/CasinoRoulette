package ru.stepanyaa.casinoRoulette;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class UpdateChecker {

    private final CasinoRoulette plugin;
    private static final String CURRENT_VERSION = "1.1.0";
    private static final String MODRINTH_API = "https://api.modrinth.com/v2/project/casinoroulette/version";

    public UpdateChecker(CasinoRoulette plugin) {
        this.plugin = plugin;
    }

    public void checkForUpdates() {
        if (!plugin.getConfigManager().getConfig().getBoolean("check-for-updates", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(MODRINTH_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "CasinoRoulette/" + CURRENT_VERSION);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                    );
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JsonParser parser = new JsonParser();
                    JsonArray versions = parser.parse(sb.toString()).getAsJsonArray();

                    String latestVersion = null;

                    for (JsonElement el : versions) {
                        String vNum = el.getAsJsonObject().get("version_number").getAsString();
                        if (latestVersion == null || isNewerVersion(vNum, latestVersion)) {
                            latestVersion = vNum;
                        }
                    }

                    ConfigManager cm = plugin.getConfigManager();

                    if (latestVersion != null && isNewerVersion(latestVersion, CURRENT_VERSION)) {
                        plugin.getLogger().warning(cm.getMessage("messages.update.console.title", "╔════════════════════════════════════╗"));
                        plugin.getLogger().warning(cm.getMessage("messages.update.console.available", "║     CasinoRoulette UPDATE AVAILABLE    "));
                        plugin.getLogger().warning(cm.getMessage("messages.update.console.version", "║  New version: %version%               ", "version", latestVersion));
                        plugin.getLogger().warning(cm.getMessage("messages.update.console.link", "║  Download: https://modrinth.com/plugin/casinoroulette "));
                        plugin.getLogger().warning(cm.getMessage("messages.update.console.bottom", "╚════════════════════════════════════╝"));

                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.hasPermission("casino.update.notify")) {
                                p.sendMessage(cm.getMessage("messages.update.available", "&6[CasinoRoulette] §eДоступно обновление: &f%version%", "version", latestVersion));
                                p.sendMessage(cm.getMessage("messages.update.download", "&7Скачать: &bhttps://modrinth.com/plugin/casinoroulette"));
                            }
                        }
                    } else {
                        plugin.getLogger().info(cm.getMessage("messages.update.up_to_date", "CasinoRoulette is up to date (%version%)", "version", CURRENT_VERSION));
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
        });
    }

    private boolean isNewerVersion(String v1, String v2) {
        String[] p1 = v1.split("\\.");
        String[] p2 = v2.split("\\.");
        for (int i = 0; i < Math.min(p1.length, p2.length); i++) {
            try {
                int n1 = Integer.parseInt(p1[i]);
                int n2 = Integer.parseInt(p2[i]);
                if (n1 > n2) return true;
                if (n1 < n2) return false;
            } catch (NumberFormatException ignored) {}
        }
        return p1.length > p2.length;
    }
}