package ru.stepanyaa.casinoRoulette.platform.impl.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import ru.stepanyaa.casinoRoulette.compat.MaterialResolver;
import ru.stepanyaa.casinoRoulette.economy.EconomyManager;
import ru.stepanyaa.casinoRoulette.economy.EconomyProvider;
import ru.stepanyaa.casinoRoulette.events.EventBridge;
import ru.stepanyaa.casinoRoulette.placeholder.PlaceholderBridge;
import ru.stepanyaa.casinoRoulette.placeholder.PlaceholderService;
import ru.stepanyaa.casinoRoulette.placeholder.papi.PapiPlaceholderBridge;
import ru.stepanyaa.casinoRoulette.platform.CasinoCommand;
import ru.stepanyaa.casinoRoulette.platform.CasinoInventory;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;
import ru.stepanyaa.casinoRoulette.platform.CasinoSender;
import ru.stepanyaa.casinoRoulette.platform.PlatformAdapter;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;
import ru.stepanyaa.casinoRoulette.scheduler.SchedulerBackend;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public abstract class AbstractBukkitAdapter implements PlatformAdapter {

    protected final JavaPlugin plugin;
    protected final PlaceholderService placeholderService = new PlaceholderService();

    private MaterialResolver materials;
    private BukkitEventBridge eventBridge;
    private EconomyManager economyManager;
    private PlaceholderBridge placeholderBridge = PlaceholderBridge.NOOP;

    protected AbstractBukkitAdapter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String serverBrand() {
        return Bukkit.getName() + " " + Bukkit.getVersion();
    }

    @Override
    public String minecraftVersion() {
        return Bukkit.getBukkitVersion().split("-")[0];
    }

    @Override
    public Logger logger() {
        return plugin.getLogger();
    }

    @Override
    public File dataFolder() {
        return plugin.getDataFolder();
    }

    @Override
    public InputStream resource(String path) {
        return plugin.getResource(path);
    }

    @Override
    public boolean isPluginEnabled() {
        return plugin.isEnabled();
    }

    @Override
    public boolean isPluginPresent(String pluginId) {
        return Bukkit.getPluginManager().isPluginEnabled(pluginId);
    }

    protected SchedulerBackend createSchedulerBackend() {
        return new BukkitSchedulerBackend(plugin);
    }

    @Override
    public SchedulerBackend scheduler() {
        return CasinoScheduler.backend();
    }

    @Override
    public EventBridge events() {
        return eventBridge;
    }

    @Override
    public EconomyProvider economy() {
        return economyManager;
    }

    @Override
    public PlaceholderBridge placeholders() {
        return placeholderBridge;
    }

    public PlaceholderService placeholderService() {
        return placeholderService;
    }

    public MaterialResolver materials() {
        return materials;
    }

    @Override
    public Optional<CasinoPlayer> player(UUID uuid) {
        Player online = uuid == null ? null : Bukkit.getPlayer(uuid);
        return online == null
                ? Optional.empty()
                : Optional.of(new BukkitCasinoPlayer(online, this::toItemStack));
    }

    @Override
    public Optional<CasinoPlayer> player(String name) {
        Player online = name == null ? null : Bukkit.getPlayerExact(name);
        return online == null
                ? Optional.empty()
                : Optional.of(new BukkitCasinoPlayer(online, this::toItemStack));
    }

    @Override
    public Collection<CasinoPlayer> onlinePlayers() {
        List<CasinoPlayer> players = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            players.add(new BukkitCasinoPlayer(online, this::toItemStack));
        }
        return players;
    }

    @Override
    public CasinoSender console() {
        return new BukkitCasinoSender(Bukkit.getConsoleSender());
    }

    public String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String cached = offline == null ? null : offline.getName();
        return cached == null ? uuid.toString().substring(0, 8) : cached;
    }

    @Override
    public void dispatchConsoleCommand(String command) {

        CasinoScheduler.run(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    @Override
    public void registerCommand(final CasinoCommand command) {
        PluginCommand pluginCommand = plugin.getCommand(command.name());
        if (pluginCommand == null) {
            logger().warning("Command /" + command.name()
                    + " is missing from plugin.yml and was not registered.");
            return;
        }

        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command bukkitCommand,
                                     String label, String[] args) {
                CasinoSender wrapped = wrapSender(sender);
                String permission = command.permission();
                if (permission != null && !wrapped.hasPermission(permission)) {
                    wrapped.sendMessage("&cYou do not have permission to use that command.");
                    return true;
                }
                try {
                    return command.execute(wrapped, args);
                } catch (Throwable failure) {

                    wrapped.sendMessage("&cAn internal error occurred while running that command.");
                    logger().warning("/" + command.name() + " failed: " + failure);
                    return true;
                }
            }
        };

        TabCompleter completer = new TabCompleter() {
            @Override
            public List<String> onTabComplete(CommandSender sender, Command bukkitCommand,
                                              String label, String[] args) {
                try {
                    List<String> suggestions = command.complete(wrapSender(sender), args);
                    return suggestions == null ? Collections.emptyList() : suggestions;
                } catch (Throwable failure) {
                    return Collections.emptyList();
                }
            }
        };

        pluginCommand.setExecutor(executor);
        pluginCommand.setTabCompleter(completer);
    }

    private CasinoSender wrapSender(CommandSender sender) {
        return sender instanceof Player
                ? new BukkitCasinoPlayer((Player) sender, this::toItemStack)
                : new BukkitCasinoSender(sender);
    }

    @Override
    public boolean materialExists(String id) {
        return id != null && Material.matchMaterial(id) != null;
    }

    @Override
    public CasinoInventory createInventory(String title, int size, String guiId) {
        return new BukkitCasinoInventory(guiId, title, size, this::toItemStack);
    }

    @Override
    public Object createItem(CasinoItem item) {
        return toItemStack(item);
    }

    public ItemStack toItemStack(CasinoItem item) {
        String resolved = materials == null
                ? item.materialId()
                : materials.resolve(item.materialId());

        Material material = resolved == null ? null : Material.matchMaterial(resolved);
        if (material == null) {
            material = Material.STONE;
        }

        ItemStack stack = new ItemStack(material, Math.max(1, item.amount()));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        if (item.displayName() != null) {
            meta.setDisplayName(BukkitCasinoSender.colorise(item.displayName()));
        }
        if (!item.lore().isEmpty()) {
            List<String> lore = new ArrayList<>(item.lore().size());
            for (String line : item.lore()) {
                lore.add(BukkitCasinoSender.colorise(line));
            }
            meta.setLore(lore);
        }
        if (item.glowing()) {
            applyGlow(meta);
        }
        if (item.hideAttributes()) {
            try {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            } catch (Throwable ignored) {
            }
        }
        if (item.customModelData() != null) {
            try {
                meta.setCustomModelData(item.customModelData());
            } catch (Throwable belowOneFourteen) {

            }
        }

        stack.setItemMeta(meta);
        return stack;
    }

    private static void applyGlow(ItemMeta meta) {
        try {
            Enchantment enchantment = Enchantment.getByName("DURABILITY");
            if (enchantment == null) {
                enchantment = Enchantment.getByName("UNBREAKING");
            }
            if (enchantment != null) {
                meta.addEnchant(enchantment, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void enable() {

        CasinoScheduler.install(createSchedulerBackend());

        this.materials = new MaterialResolver(this);

        this.eventBridge = new BukkitEventBridge(plugin, this::toItemStack);
        this.eventBridge.register();

        bindEconomyFromConfig();
        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPluginEnable(org.bukkit.event.server.PluginEnableEvent event) {
                String name = event.getPlugin().getName();
                if (name == null) {
                    return;
                }
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                if (lower.equals("playerpoints") || lower.equals("vault")
                        || lower.equals("placeholderapi")) {
                    if (economyManager != null) {
                        economyManager.rebind(currentEconomyMode());
                    }
                    if (lower.equals("placeholderapi") && placeholderBridge != null) {
                        try {
                            placeholderBridge.unregister();
                        } catch (Throwable ignored) {
                        }
                        placeholderBridge = new PapiPlaceholderBridge(
                                plugin, placeholderService, "casinoroulette");
                        placeholderBridge.register();
                    }
                }
            }
        }, plugin);

        this.placeholderBridge =
                new PapiPlaceholderBridge(plugin, placeholderService, "casinoroulette");
        this.placeholderBridge.register();
    }

    public void rebindEconomy() {
        if (economyManager != null) {
            economyManager.rebind(currentEconomyMode());
        } else {
            bindEconomyFromConfig();
        }
    }

    private String currentEconomyMode() {
        return plugin.getConfig().getString("settings.economy-mode",
                plugin.getConfig().getString("economy-mode", "AUTO"));
    }

    private void bindEconomyFromConfig() {
        this.economyManager = new EconomyManager(
                logger(),
                type(),
                dataFolder(),
                currentEconomyMode(),
                plugin.getConfig().getDouble("economy.internal.starting-balance",
                        plugin.getConfig().getDouble("internal-economy.starting-balance", 0.0D)),
                plugin.getConfig().getString("economy.internal.currency-symbol",
                        plugin.getConfig().getString("internal-economy.currency-symbol", "$")));
    }

    @Override
    public void disable() {
        if (placeholderBridge != null) {
            placeholderBridge.unregister();
        }
        if (eventBridge != null) {
            eventBridge.unregister();
        }
        if (economyManager != null) {
            economyManager.shutdown();
        }

        CasinoScheduler.shutdown();
    }
}
