package ru.stepanyaa.casinoRoulette;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;

import ru.stepanyaa.casinoRoulette.config.YamlMerge;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private final CasinoRoulette plugin;
    private File configFile;
    private FileConfiguration config;
    private File langFile;
    private FileConfiguration langConfig;

    private static final String CURRENT_CONFIG_VERSION = "1.2.0";
    private static final String CURRENT_LANG_VERSION = "1.2.0";
    private static final String LANG_FOLDER = "lang";

    public ConfigManager(CasinoRoulette plugin) {
        this.plugin = plugin;
        setupConfig();
        setupLanguage();
    }

    public void setupConfig() {
        configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            plugin.saveDefaultConfig();
            plugin.getLogger().info("Created default config.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);
        config = updateFileIfNeeded(config, configFile, "config.yml", "config-version", CURRENT_CONFIG_VERSION);
    }

    public void setupLanguage() {
        String lang = config.getString("lang", "en").toLowerCase();
        String fileName = "messages_" + lang + ".yml";
        File folder = new File(plugin.getDataFolder(), LANG_FOLDER);
        if (!folder.exists()) folder.mkdirs();
        langFile = new File(folder, fileName);

        String resourcePath = LANG_FOLDER + "/" + fileName;
        if (!langFile.exists()) {
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            } else if (plugin.getResource(LANG_FOLDER + "/messages_en.yml") != null) {
                plugin.saveResource(LANG_FOLDER + "/messages_en.yml", false);
                File fallback = new File(folder, "messages_en.yml");
                fallback.renameTo(langFile);
            } else if (plugin.getResource(fileName) != null) {
                plugin.saveResource(fileName, false);
                new File(plugin.getDataFolder(), fileName).renameTo(langFile);
            }
            plugin.getLogger().info("Created localization file: lang/" + fileName);
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);
        langConfig = updateFileIfNeeded(langConfig, langFile, resourcePath, "config-version", CURRENT_LANG_VERSION);
    }

    private FileConfiguration updateFileIfNeeded(FileConfiguration target, File targetFile,
                                                 String resourcePath, String versionKey,
                                                 String currentVersion) {
        String fileVersion = target.getString(versionKey, "0");
        boolean versionChanged = !fileVersion.equals(currentVersion);

        try (InputStream resourceStream = plugin.getResource(resourcePath)) {
            if (resourceStream == null) {
                plugin.getLogger().warning("Resource not found: " + resourcePath);
                return target;
            }

            String defaults = readAll(resourceStream);
            String current;
            try (InputStream currentStream = new FileInputStream(targetFile)) {
                current = readAll(currentStream);
            }

            YamlMerge.Result merged = YamlMerge.merge(current, defaults);
            if (!merged.changed() && !versionChanged) {
                return target;
            }

            String text = merged.text();
            if (versionChanged) {

                text = withVersion(text, versionKey, currentVersion);
            }

            java.nio.file.Files.copy(targetFile.toPath(),
                    new File(targetFile.getParentFile(), targetFile.getName() + ".backup").toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(targetFile), StandardCharsets.UTF_8)) {
                writer.write(text);
            }

            plugin.getLogger().info("Updated " + targetFile.getName() + " from " + fileVersion
                    + " to " + currentVersion + ": " + merged.addedKeys().size()
                    + " new option(s) added, comments and existing values preserved.");

            return YamlConfiguration.loadConfiguration(targetFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update " + targetFile.getName() + ": " + e.getMessage());
            return target;
        }
    }

    private static String readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String withVersion(String text, String versionKey, String version) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith(versionKey + ":")) {
                lines[i] = versionKey + ": " + version;
                return String.join("\n", lines);
            }
        }
        return text + (text.endsWith("\n") ? "" : "\n") + versionKey + ": " + version + "\n";
    }

    public void saveConfig() {
        try { config.save(configFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public void reload() {
        setupConfig();
        setupLanguage();
        plugin.getLogger().info("Plugin and localization successfully reloaded.");
    }

    public FileConfiguration getConfig() { return config; }

    public String getMessage(String path, String def, Object... placeholders) {
        String msg = langConfig.getString(path, def);
        for (int i = 0; i < placeholders.length; i += 2) if (i + 1 < placeholders.length) msg = msg.replace("%" + placeholders[i] + "%", String.valueOf(placeholders[i + 1]));
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public List<String> getMessageList(String path, List<String> def, Object... placeholders) {
        List<String> list = langConfig.getStringList(path);
        if (list == null || list.isEmpty()) list = def;
        List<String> result = new ArrayList<>();
        for (String s : list) {
            String line = s;
            for (int i = 0; i < placeholders.length; i += 2) if (i + 1 < placeholders.length) line = line.replace("%" + placeholders[i] + "%", String.valueOf(placeholders[i + 1]));
            result.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return result;
    }
}
