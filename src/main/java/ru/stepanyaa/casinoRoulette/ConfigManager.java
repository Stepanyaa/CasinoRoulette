/*
MIT License

Copyright (c) 2026 Stepanyaa

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package ru.stepanyaa.casinoRoulette;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

        String fileVersion = config.getString("config-version", "1.2.0");
        if (!fileVersion.equals(CURRENT_CONFIG_VERSION)) {
            plugin.getLogger().info("Updating config.yml from " + fileVersion + " to " + CURRENT_CONFIG_VERSION);
            updateConfigFile();
        }
    }

    private void updateConfigFile() {
        if (plugin.getResource("config.yml") == null) {
            plugin.getLogger().warning("Resource config.yml not found in plugin!");
            return;
        }

        try (InputStream resourceStream = plugin.getResource("config.yml")) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resourceStream, StandardCharsets.UTF_8)
            );

            boolean updated = false;

            for (String key : defaults.getKeys(true)) {
                if (!config.isSet(key)) {
                    config.set(key, defaults.get(key));
                    updated = true;
                }
            }

            config.set("config-version", CURRENT_CONFIG_VERSION);
            saveConfig();

            if (updated) {
                plugin.getLogger().info("Config.yml successfully updated to version " + CURRENT_CONFIG_VERSION);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update config.yml: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setupLanguage() {
        String lang = config.getString("lang", "en");
        String fileName = "messages_" + lang + ".yml";
        langFile = new File(plugin.getDataFolder(), fileName);

        if (!langFile.exists()) {
            plugin.saveResource(fileName, false);
            plugin.getLogger().info("Created localization file: " + fileName);
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        if (!langConfig.getString("config-version", "0").equals(CURRENT_LANG_VERSION)) {
            plugin.getLogger().info("Updating " + fileName + " from " + langConfig.getString("config-version") + " to " + CURRENT_LANG_VERSION);
            updateLanguageFile(fileName);
        }
    }

    private void updateLanguageFile(String fileName) {
        try (InputStream resourceStream = plugin.getResource(fileName)) {
            if (resourceStream == null) {
                plugin.getLogger().warning("Resource not found: " + fileName);
                return;
            }

            YamlConfiguration defaultLang = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resourceStream, StandardCharsets.UTF_8)
            );

            boolean updated = false;

            for (String key : defaultLang.getKeys(true)) {
                if (!langConfig.isSet(key)) {
                    langConfig.set(key, defaultLang.get(key));
                    updated = true;
                }
            }

            if (updated) {
                langConfig.set("config-version", CURRENT_LANG_VERSION);
                langConfig.save(langFile);
                plugin.getLogger().info("Added new strings to " + fileName);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update " + fileName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void reload() {
        setupConfig();
        setupLanguage();
        plugin.getLogger().info("Plugin and localization successfully reloaded.");
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public String getMessage(String path, String def, Object... placeholders) {
        String msg = langConfig.getString(path, def);
        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                msg = msg.replace("%" + placeholders[i] + "%", String.valueOf(placeholders[i + 1]));
            }
        }
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public List<String> getMessageList(String path, List<String> def, Object... placeholders) {
        List<String> list = langConfig.getStringList(path);
        if (list == null || list.isEmpty()) list = def;

        List<String> result = new ArrayList<>();
        for (String s : list) {
            String line = s;
            for (int i = 0; i < placeholders.length; i += 2) {
                if (i + 1 < placeholders.length) {
                    line = line.replace("%" + placeholders[i] + "%", String.valueOf(placeholders[i + 1]));
                }
            }
            result.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        return result;
    }
}