package ru.stepanyaa.casinoRoulette;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

public class ExtraConfig {
    private final CasinoRoulette plugin;
    private final String name;
    private File file;
    private FileConfiguration config;
    public ExtraConfig(CasinoRoulette plugin, String name) { this.plugin = plugin; this.name = name; reload(); }
    public void reload() {
        file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
        config = YamlConfiguration.loadConfiguration(file);
    }
    public FileConfiguration get() { return config; }
    public void save() { try { config.save(file); } catch (Exception e) { plugin.getLogger().warning("Cannot save " + name + ": " + e.getMessage()); } }
}
