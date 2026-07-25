package ru.stepanyaa.casinoRoulette.placeholder.papi;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import ru.stepanyaa.casinoRoulette.placeholder.PlaceholderBridge;
import ru.stepanyaa.casinoRoulette.placeholder.PlaceholderService;

public final class PapiPlaceholderBridge implements PlaceholderBridge, Listener {

    private static final String PAPI = "PlaceholderAPI";

    private final Plugin plugin;
    private final PlaceholderService service;
    private final String identifier;

    private volatile Object expansion;
    private volatile boolean registered;
    private boolean listenerRegistered;

    public PapiPlaceholderBridge(Plugin plugin, PlaceholderService service, String identifier) {
        this.plugin = plugin;
        this.service = service;
        this.identifier = identifier;
    }

    @Override
    public boolean register() {

        if (!listenerRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            listenerRegistered = true;
        }
        if (registered) {
            return true;
        }
        if (!isPapiPresent()) {

            return false;
        }
        return doRegister();
    }

    private boolean isPapiPresent() {
        try {
            return Bukkit.getPluginManager().isPluginEnabled(PAPI);
        } catch (Throwable throwable) {
            return false;
        }
    }

    private boolean doRegister() {
        try {
            PapiExpansion created = new PapiExpansion(plugin, service, identifier);

            try {
                me.clip.placeholderapi.expansion.PlaceholderExpansion existing =
                        me.clip.placeholderapi.PlaceholderAPIPlugin.getInstance()
                                .getLocalExpansionManager()
                                .getExpansion(identifier);
                if (existing != null) {
                    existing.unregister();
                }
            } catch (Throwable ignored) {

            }

            if (created.register()) {
                this.expansion = created;
                this.registered = true;
                plugin.getLogger().info("PlaceholderAPI expansion '" + identifier + "' registered.");
                return true;
            }
            plugin.getLogger().warning(
                    "PlaceholderAPI refused the '" + identifier + "' expansion registration.");
            return false;
        } catch (Throwable throwable) {
            plugin.getLogger().warning(
                    "Could not register the PlaceholderAPI expansion: " + throwable.getMessage());
            return false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPluginEnable(PluginEnableEvent event) {
        if (registered) {
            return;
        }
        if (PAPI.equalsIgnoreCase(event.getPlugin().getName())) {
            doRegister();
        }
    }

    @Override
    public void unregister() {
        Object current = expansion;
        if (current == null) {
            registered = false;
            return;
        }
        try {
            ((me.clip.placeholderapi.expansion.PlaceholderExpansion) current).unregister();
        } catch (Throwable ignored) {

        }
        expansion = null;
        registered = false;
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }
}
