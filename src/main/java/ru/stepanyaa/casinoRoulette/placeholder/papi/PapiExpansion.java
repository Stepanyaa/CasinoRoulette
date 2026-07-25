package ru.stepanyaa.casinoRoulette.placeholder.papi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import ru.stepanyaa.casinoRoulette.placeholder.PlaceholderService;

import java.util.UUID;

public final class PapiExpansion extends PlaceholderExpansion {

    private final Plugin plugin;
    private final PlaceholderService service;
    private final String identifier;

    public PapiExpansion(Plugin plugin, PlaceholderService service, String identifier) {
        this.plugin = plugin;
        this.service = service;
        this.identifier = identifier;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        UUID uuid = player != null ? player.getUniqueId() : null;
        return service.resolve(uuid, params);
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        UUID uuid = player != null ? player.getUniqueId() : null;
        return service.resolve(uuid, params);
    }
}
