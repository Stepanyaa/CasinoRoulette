package ru.stepanyaa.casinoRoulette;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;

public class CasinoRoulette extends JavaPlugin implements Listener, CommandExecutor {
    private Object economy;
    private Object permission;
    private final Random random = new Random();

    private ConfigManager configManager;
    private SlotMachine slotMachine;
    private CrashGame crashGame;
    private RouletteGUI gui;

    private final Map<UUID, Integer> playerChips = new HashMap<>();
    private final Map<UUID, Integer> totalWon = new HashMap<>();
    private final Map<UUID, Integer> totalLost = new HashMap<>();
    private final Map<UUID, Integer> wins = new HashMap<>();
    private final Map<UUID, Integer> losses = new HashMap<>();
    private final Map<UUID, Integer> totalRounds = new HashMap<>();

    private final Set<UUID> playersInGame = new HashSet<>();
    private final Map<UUID, Inventory> activeGameInventories = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> activeBets = new HashMap<>();
    private final Map<UUID, Integer> currentBetAmount = new HashMap<>();
    private final LinkedList<Integer> history = new LinkedList<>();
    private DatabaseManager db;

    public enum GameState { WAITING, SPINNING }
    public GameState gameState = GameState.WAITING;
    public int timer = 30;
    private int timerDuration = 30;
    private BukkitRunnable gameTask;

    private int payoutNumber = 36;
    private int payoutColor = 2;
    private int payoutSector = 3;
    private int payoutZero = 36;

    private final Map<String, Integer> selectionLimits = new HashMap<>();
    private double commissionPercent = 10.0;
    private int minExchange = 100;

    private int minBet = 1000;
    private int maxBet = 10000;
    private boolean checkForUpdates = true;
    private boolean rouletteEnabled = true;
    private boolean slotsEnabled = true;
    private boolean crashEnabled = true;

    private boolean isConfigured = false;
    private String economyMode = "VAULT";
    private Material itemResource = Material.DIAMOND;
    private int setupStep = 0;

    private final Map<UUID, String> awaitingInput = new HashMap<>();

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        loadConfigValues();
        db = new DatabaseManager(this);

        setupEconomy();
        setupPermissions();

        gui = new RouletteGUI(this);
        slotMachine = new SlotMachine(this);
        crashGame = new CrashGame(this);

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("casino").setExecutor(this);

        if (checkForUpdates) {
            new UpdateChecker(this).checkForUpdates();
        }

        if (!isConfigured) {
            if (!checkAndAutoConfigure(null)) {
                Bukkit.getScheduler().runTaskLater(this, this::sendSetupWizardToAdmins, 20L);
            }
        }

        getLogger().info("CasinoRoulette v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        for (UUID uuid : activeBets.keySet()) {
            Map<String, Integer> bets = activeBets.get(uuid);
            int sum = bets.values().stream().mapToInt(Integer::intValue).sum();
            if (sum > 0) {
                playerChips.put(uuid, playerChips.getOrDefault(uuid, 0) + sum);
            }
        }
        Set<UUID> all = new HashSet<>();
        all.addAll(playerChips.keySet());
        all.addAll(wins.keySet());
        all.addAll(losses.keySet());
        all.addAll(totalWon.keySet());
        all.addAll(totalLost.keySet());
        all.addAll(totalRounds.keySet());
        for (UUID uuid : all) {
            saveSpecificPlayer(uuid);
        }
        if (gameTask != null) gameTask.cancel();
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        // Handle active bets - refund them to prevent loss
        handlePlayerDisconnect(uuid);

        saveSpecificPlayer(uuid);
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        db.loadPlayer(p.getUniqueId(), this);

        if (!isConfigured && p.hasPermission("casino.admin")) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!checkAndAutoConfigure(p)) {
                    sendSetupMessage(p);
                }
            }, 20L);
        }
    }

    private boolean checkAndAutoConfigure(Player adminToNotify) {
        if (isConfigured) return true;

        boolean hasSpigot = false;
        try {
            Player.class.getMethod("spigot");
            hasSpigot = true;
        } catch (NoSuchMethodException ignored) {}

        boolean ecoReady = setupEconomy();

        if (!hasSpigot || !ecoReady) {
            configManager.getConfig().set("settings.economy-mode", "ITEM");
            configManager.getConfig().set("settings.is-configured", true);
            configManager.saveConfig();
            loadConfigValues();
            isConfigured = true;

            String msg = configManager.getMessage("messages.auto_setup", "&e[CasinoRoulette] &aAutomatic setup complete! Selected ITEM mode (because Vault was not found or clean Bukkit is used).");

            if (adminToNotify != null && adminToNotify.isOnline()) {
                adminToNotify.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
            } else {
                getLogger().info(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', msg)));
            }
            return true;
        }

        return false;
    }

    public void saveSpecificPlayer(UUID uuid) {
        int chips = playerChips.getOrDefault(uuid, 0);
        int w = wins.getOrDefault(uuid, 0);
        int l = losses.getOrDefault(uuid, 0);
        int tw = totalWon.getOrDefault(uuid, 0);
        int tl = totalLost.getOrDefault(uuid, 0);
        int tr = totalRounds.getOrDefault(uuid, 0);

        if (chips > 0 || w > 0 || l > 0 || tw > 0 || tl > 0 || tr > 0) {
            db.savePlayer(uuid, chips, w, l, tw, tl, tr);
        }
        playerChips.remove(uuid);
        wins.remove(uuid);
        losses.remove(uuid);
        totalWon.remove(uuid);
        totalLost.remove(uuid);
        totalRounds.remove(uuid);
    }

    public void loadConfigValues() {
        configManager.reload();
        FileConfiguration config = configManager.getConfig();

        isConfigured = config.getBoolean("settings.is-configured", false);
        economyMode = config.getString("settings.economy-mode", "VAULT");
        try {
            itemResource = Material.valueOf(config.getString("settings.item-resource", "DIAMOND").toUpperCase());
        } catch (Exception e) {
            itemResource = Material.DIAMOND;
        }

        rouletteEnabled = config.getBoolean("roulette.enabled", true);
        slotsEnabled = config.getBoolean("slots.enabled", true);
        crashEnabled = config.getBoolean("crash.enabled", true);

        timerDuration = config.getInt("roulette.timer_duration", 30);
        timer = timerDuration;
        payoutNumber = config.getInt("roulette.payouts.number", 36);
        payoutColor = config.getInt("roulette.payouts.color", 2);
        payoutSector = config.getInt("roulette.payouts.sector", 3);
        payoutZero = config.getInt("roulette.payouts.zero", 36);
        commissionPercent = config.getDouble("settings.commission_percent", 10.0);

        String modeKey = economyMode.equalsIgnoreCase("VAULT") ? "vault" : "item";
        minExchange = config.getInt("economy." + modeKey + ".min_exchange", economyMode.equalsIgnoreCase("VAULT") ? 100 : 10);
        minBet = config.getInt("economy." + modeKey + ".min_bet", economyMode.equalsIgnoreCase("VAULT") ? 1000 : 10);
        maxBet = config.getInt("economy." + modeKey + ".max_bet", economyMode.equalsIgnoreCase("VAULT") ? 10000 : 1000);
        checkForUpdates = config.getBoolean("check-for-updates", true);

        selectionLimits.clear();
        if (config.isConfigurationSection("selection_limits")) {
            for (String key : config.getConfigurationSection("selection_limits").getKeys(false)) {
                selectionLimits.put(key.toLowerCase(), config.getInt("selection_limits." + key));
            }
        }
        selectionLimits.putIfAbsent("default", 5);
        if (slotMachine != null) slotMachine.loadConfigValues();
    }

    private boolean setupPermissions() {
        try {
            if (getServer().getPluginManager().getPlugin("Vault") == null) {
                permission = null;
                return true;
            }
            RegisteredServiceProvider<?> rsp = getServer().getServicesManager().getRegistration(Class.forName("net.milkbowl.vault.permission.Permission"));
            permission = rsp != null ? rsp.getProvider() : null;
        } catch (Exception e) {
            permission = null;
        }
        return true;
    }

    /**
     * Handles player disconnection by refunding any active bets
     * @param uuid The UUID of the player who disconnected
     */
    public void handlePlayerDisconnect(UUID uuid) {
        // Refund Crash game bets
        if (crashGame != null) {
            crashGame.handlePlayerDisconnect(uuid);
        }

        // Refund Slot machine bets
        if (slotMachine != null) {
            slotMachine.handlePlayerDisconnect(uuid);
        }

        // Refund Roulette bets
        if (activeBets.containsKey(uuid)) {
            Map<String, Integer> bets = activeBets.remove(uuid);
            int totalRefund = bets.values().stream().mapToInt(Integer::intValue).sum();
            if (totalRefund > 0) {
                playerChips.put(uuid, playerChips.getOrDefault(uuid, 0) + totalRefund);
                getLogger().info("Refunded " + totalRefund + " chips to player " + uuid + " due to disconnection");
            }
        }

        // Clean up
        playersInGame.remove(uuid);
        activeGameInventories.remove(uuid);
        currentBetAmount.remove(uuid);
        awaitingInput.remove(uuid);
    }

    public boolean hasEnoughBalance(Player p, double amount) {
        if (economyMode.equalsIgnoreCase("VAULT")) {
            try {
                if (economy != null) {
                    // Try different method signatures for has()
                    boolean success = false;
                    boolean result = false;

                    // Try Player, double parameters first
                    try {
                        result = (boolean) economy.getClass().getMethod("has", Player.class, double.class).invoke(economy, p, amount);
                        success = true;
                    } catch (NoSuchMethodException e1) {
                        // Try String, double parameters (player name)
                        try {
                            result = (boolean) economy.getClass().getMethod("has", String.class, double.class).invoke(economy, p.getName(), amount);
                            success = true;
                        } catch (NoSuchMethodException e2) {
                            // Try UUID, double parameters
                            try {
                                result = (boolean) economy.getClass().getMethod("has", UUID.class, double.class).invoke(economy, p.getUniqueId(), amount);
                                success = true;
                            } catch (NoSuchMethodException e3) {
                                // Try OfflinePlayer, double parameters
                                try {
                                    result = (boolean) economy.getClass().getMethod("has", OfflinePlayer.class, double.class).invoke(economy, p, amount);
                                    success = true;
                                } catch (NoSuchMethodException e4) {
                                    getLogger().warning("No compatible has() method found in Vault economy provider");
                                }
                            }
                        }
                    }

                    if (success) {
                        return result;
                    }
                }
            } catch (Exception e) {
                getLogger().warning("Failed to check balance via Vault, falling back to item mode: " + e.getMessage());
                return p.getInventory().containsAtLeast(new ItemStack(itemResource), (int) amount);
            }
        }
        return p.getInventory().containsAtLeast(new ItemStack(itemResource), (int) amount);
    }

    public void withdrawBalance(Player p, double amount) {
        if (economyMode.equalsIgnoreCase("VAULT")) {
            try {
                if (economy != null) {
                    // Try different method signatures for withdrawPlayer()
                    boolean success = false;

                    // Try Player, double parameters first
                    try {
                        economy.getClass().getMethod("withdrawPlayer", Player.class, double.class).invoke(economy, p, amount);
                        success = true;
                    } catch (NoSuchMethodException e1) {
                        // Try String, double parameters (player name)
                        try {
                            economy.getClass().getMethod("withdrawPlayer", String.class, double.class).invoke(economy, p.getName(), amount);
                            success = true;
                        } catch (NoSuchMethodException e2) {
                            // Try UUID, double parameters
                            try {
                                economy.getClass().getMethod("withdrawPlayer", UUID.class, double.class).invoke(economy, p.getUniqueId(), amount);
                                success = true;
                            } catch (NoSuchMethodException e3) {
                                // Try OfflinePlayer, double parameters
                                try {
                                    economy.getClass().getMethod("withdrawPlayer", OfflinePlayer.class, double.class).invoke(economy, p, amount);
                                    success = true;
                                } catch (NoSuchMethodException e4) {
                                    getLogger().warning("No compatible withdrawPlayer() method found in Vault economy provider");
                                }
                            }
                        }
                    }

                    if (!success) {
                        getLogger().warning("Failed to withdraw balance via Vault, falling back to item mode");
                        p.getInventory().removeItem(new ItemStack(itemResource, (int) amount));
                    }
                }
            } catch (Exception e) {
                getLogger().warning("Failed to withdraw balance via Vault, falling back to item mode: " + e.getMessage());
                p.getInventory().removeItem(new ItemStack(itemResource, (int) amount));
            }
        } else {
            p.getInventory().removeItem(new ItemStack(itemResource, (int) amount));
        }
    }

    public void depositBalance(Player p, double amount) {
        if (economyMode.equalsIgnoreCase("VAULT")) {
            try {
                if (economy != null) {
                    // Try different method signatures for depositPlayer()
                    boolean success = false;

                    // Try Player, double parameters first
                    try {
                        economy.getClass().getMethod("depositPlayer", Player.class, double.class).invoke(economy, p, amount);
                        success = true;
                    } catch (NoSuchMethodException e1) {
                        // Try String, double parameters (player name)
                        try {
                            economy.getClass().getMethod("depositPlayer", String.class, double.class).invoke(economy, p.getName(), amount);
                            success = true;
                        } catch (NoSuchMethodException e2) {
                            // Try UUID, double parameters
                            try {
                                economy.getClass().getMethod("depositPlayer", UUID.class, double.class).invoke(economy, p.getUniqueId(), amount);
                                success = true;
                            } catch (NoSuchMethodException e3) {
                                // Try OfflinePlayer, double parameters
                                try {
                                    economy.getClass().getMethod("depositPlayer", OfflinePlayer.class, double.class).invoke(economy, p, amount);
                                    success = true;
                                } catch (NoSuchMethodException e4) {
                                    getLogger().warning("No compatible depositPlayer() method found in Vault economy provider");
                                }
                            }
                        }
                    }

                    if (!success) {
                        getLogger().warning("Failed to deposit balance via Vault, falling back to item mode");
                        ItemStack items = new ItemStack(itemResource, (int) amount);
                        Map<Integer, ItemStack> overflow = p.getInventory().addItem(items);
                        if (!overflow.isEmpty()) {
                            for (ItemStack stack : overflow.values()) {
                                p.getWorld().dropItemNaturally(p.getLocation(), stack);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                getLogger().warning("Failed to deposit balance via Vault, falling back to item mode: " + e.getMessage());
                ItemStack items = new ItemStack(itemResource, (int) amount);
                Map<Integer, ItemStack> overflow = p.getInventory().addItem(items);
                if (!overflow.isEmpty()) {
                    for (ItemStack stack : overflow.values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), stack);
                    }
                }
            }
        } else {
            ItemStack items = new ItemStack(itemResource, (int) amount);
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(items);
            if (!overflow.isEmpty()) {
                for (ItemStack stack : overflow.values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), stack);
                }
            }
        }
    }

    public String formatBalance(Player p) {
        if (economyMode.equalsIgnoreCase("VAULT")) {
            try {
                if (economy != null) {
                    // Try different method signatures for getBalance
                    double balance = 0;
                    boolean success = false;

                    // Try Player parameter first
                    try {
                        balance = (double) economy.getClass().getMethod("getBalance", Player.class).invoke(economy, p);
                        success = true;
                    } catch (NoSuchMethodException e1) {
                        // Try String parameter (player name)
                        try {
                            balance = (double) economy.getClass().getMethod("getBalance", String.class).invoke(economy, p.getName());
                            success = true;
                        } catch (NoSuchMethodException e2) {
                            // Try UUID parameter
                            try {
                                balance = (double) economy.getClass().getMethod("getBalance", UUID.class).invoke(economy, p.getUniqueId());
                                success = true;
                            } catch (NoSuchMethodException e3) {
                                // Try OfflinePlayer parameter
                                try {
                                    balance = (double) economy.getClass().getMethod("getBalance", OfflinePlayer.class).invoke(economy, p);
                                    success = true;
                                } catch (NoSuchMethodException e4) {
                                    getLogger().warning("No compatible getBalance method found in Vault economy provider");
                                }
                            }
                        }
                    }

                    if (success) {
                        return economy.getClass().getMethod("format", double.class).invoke(economy, balance).toString();
                    }
                }
            } catch (Exception e) {
                getLogger().warning("Failed to format balance via Vault, falling back to item mode: " + e.getMessage());
            }
        }

        // Fallback to item mode
        int count = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == itemResource) {
                count += item.getAmount();
            }
        }
        return count + " " + itemResource.name();
    }

    public void sendSetupWizardToAdmins() {
        if (isConfigured) return;
        if (checkAndAutoConfigure(null)) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("casino.admin")) sendSetupMessage(p);
        }
    }

    public void sendSetupMessage(Player p) {
        // Check if BungeeChat API is available for clickable messages
        if (BungeeSetupMessage.isAvailable()) {
            try {
                BungeeSetupMessage.sendSetupMessage(this, p);
                return;
            } catch (Exception e) {
                getLogger().warning("Failed to send Bungee setup message, falling back to text: " + e.getMessage());
            }
        }

        // Fallback to simple text messages if BungeeChat API is not available
        simpleSetupMessage(p);
    }

    private void simpleSetupMessage(Player p) {
        ConfigManager cm = this.configManager;
        p.sendMessage(cm.getMessage("messages.setup.separator", "&6=============================================", new Object[0]));
        p.sendMessage(cm.getMessage("messages.setup.wizard_title", "&e [CasinoRoulette Setup Wizard]", new Object[0]));
        p.sendMessage(cm.getMessage("messages.setup.wizard_subtitle", "&f Let's configure the plugin to your liking!", new Object[0]));
        p.sendMessage("");

        if (this.setupStep == 0) {
            p.sendMessage(cm.getMessage("messages.setup.step1", "&b Step 1: Select your preferred language:", new Object[0]));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a&l[ English ] &7- Type: /casino setup lang en"));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c&l[ Russian ] &7- Type: /casino setup lang ru"));
        } else if (this.setupStep == 1) {
            p.sendMessage(cm.getMessage("messages.setup.step2", "&b Step 2: Select server currency system:", new Object[0]));
            boolean vaultInstalled = this.getServer().getPluginManager().getPlugin("Vault") != null;
            boolean econInstalled = this.setupEconomy();
            boolean ecoReady = vaultInstalled && econInstalled;
            String vaultStatus = ecoReady ? cm.getMessage("messages.setup.status_eco_found", "&a (Economy system detected)", new Object[0]) : cm.getMessage("messages.setup.status_eco_missing", "&c (No economy plugins found!)", new Object[0]);

            if (ecoReady) {
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&a&l[ Vault Economy ] &7- Type: /casino setup eco vault"));
            } else {
                p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&7&l[ Vault Economy ] &7- Type: /casino setup eco vault"));
            }
            p.sendMessage(cm.getMessage("messages.setup.status", "&8   Status: %status%", new Object[]{"status", vaultStatus}));
            p.sendMessage("");
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&6&l[ Item Resource ] &7- Type: /casino setup eco item"));
            p.sendMessage(cm.getMessage("messages.setup.status", "&8   Status: %status%", new Object[]{"status", cm.getMessage("messages.setup.status_item", "&a(Always available - customizable in config.yml)", new Object[0])}));
        }

        p.sendMessage(cm.getMessage("messages.setup.separator", "&6=============================================", new Object[0]));
    }

    private void sendClickableMessage(Player player, String text, String hoverText, String command) {
        // Send formatted text message with instructions
        // Note: For full clickable functionality, consider adding BungeeChat as a dependency
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', text + " &7- " + hoverText));
    }

    public void logGameResult(UUID uuid, int bet, int payout, boolean isWin) {
        totalRounds.put(uuid, totalRounds.getOrDefault(uuid, 0) + 1);
        if (isWin) {
            wins.put(uuid, wins.getOrDefault(uuid, 0) + 1);
            totalWon.put(uuid, totalWon.getOrDefault(uuid, 0) + payout);
        } else {
            losses.put(uuid, losses.getOrDefault(uuid, 0) + 1);
            totalLost.put(uuid, totalLost.getOrDefault(uuid, 0) + bet);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("setup") && sender.hasPermission("casino.admin")) {
            if (isConfigured) {
                sender.sendMessage(configManager.getMessage("messages.setup.already_configured", "&cPlugin is already configured!"));
                return true;
            }
            if (args.length > 2 && args[1].equalsIgnoreCase("lang")) {
                String selectedLang = args[2].toLowerCase();
                if (selectedLang.equals("en") || selectedLang.equals("ru")) {
                    configManager.getConfig().set("lang", selectedLang);
                    configManager.saveConfig();
                    configManager.reload();
                    loadConfigValues();
                    setupStep = 1;
                    sendSetupWizardToAdmins();
                }
                return true;
            }
            if (args.length > 2 && args[1].equalsIgnoreCase("eco")) {
                String selectedEco = args[2].toUpperCase();
                if (selectedEco.equals("VAULT") || selectedEco.equals("ITEM")) {
                    configManager.getConfig().set("settings.economy-mode", selectedEco);
                    configManager.getConfig().set("settings.is-configured", true);
                    configManager.saveConfig();
                    configManager.reload();
                    loadConfigValues();
                    isConfigured = true;

                    ConfigManager cm = configManager;
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.hasPermission("casino.admin")) {
                            p.sendMessage(cm.getMessage("messages.setup.separator", "&6============================================="));
                            p.sendMessage(cm.getMessage("messages.setup.completed_title", "&a [CasinoRoulette Setup Completed!]"));
                            p.sendMessage(cm.getMessage("messages.setup.completed_by", "&f Setup successfully completed by %player%!", "player", sender.getName()));
                            p.sendMessage(cm.getMessage("messages.setup.lang_applied", "&e Language applied: %lang%", "lang", configManager.getConfig().getString("lang").toUpperCase()));
                            p.sendMessage(cm.getMessage("messages.setup.eco_applied", "&e Currency mode: %eco%", "eco", selectedEco));
                            p.sendMessage(cm.getMessage("messages.setup.change_hint", "&7 You can change any settings anytime inside config.yml."));
                            p.sendMessage(cm.getMessage("messages.setup.separator", "&6============================================="));
                        }
                    }
                }
                return true;
            }
        }

        if (!isConfigured) {
            if (sender.hasPermission("casino.admin")) {
                if (checkAndAutoConfigure((Player) sender)) {
                    gui.openMainMenu((Player) sender);
                } else {
                    sendSetupMessage((Player) sender);
                }
            } else {
                sender.sendMessage(configManager.getMessage("messages.setup.not_configured", "&cThe plugin is not configured yet. Please contact an administrator."));
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("casino.reload")) {
                sender.sendMessage(configManager.getMessage("messages.no_permission", "&cYou do not have permission to execute this command!"));
                return true;
            }
            loadConfigValues();
            sender.sendMessage(configManager.getMessage("messages.prefix", "&7[&6Casino&7] ") + configManager.getMessage("messages.reload_success", "&aReloaded!"));
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(configManager.getMessage("messages.players_only", "&cPlayers only!"));
            return true;
        }

        gui.openMainMenu((Player) sender);
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        if (!isConfigured) {
            e.setCancelled(true);
            return;
        }

        String title = ChatColor.stripColor(e.getView().getTitle());
        String tMain = ChatColor.stripColor(configManager.getMessage("gui.titles.main_menu", "Casino: Main Menu"));
        String tExchange = ChatColor.stripColor(configManager.getMessage("gui.titles.exchange", "Casino: Exchange"));
        String tBuy = ChatColor.stripColor(configManager.getMessage("gui.titles.buy_chips", "Casino: Buy Chips"));
        String tSell = ChatColor.stripColor(configManager.getMessage("gui.titles.sell_chips", "Casino: Sell Chips"));
        String tRoulette = ChatColor.stripColor(configManager.getMessage("gui.titles.roulette", "Roulette Room"));
        String tStats = ChatColor.stripColor(configManager.getMessage("gui.titles.stats", "Detailed Statistics"));
        String tSlots = ChatColor.stripColor(configManager.getMessage("gui.titles.slots", "Slot Machine"));
        String tCrash = ChatColor.stripColor(configManager.getMessage("gui.titles.crash", "Crash Game"));

        if (title.equals(tMain) || title.equals(tRoulette) || title.equals(tExchange) ||
                title.equals(tBuy) || title.equals(tSell) || title.equals(tStats) ||
                title.equals(tSlots) || title.equals(tCrash)) {

            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;

            if (title.equals(tMain)) {
                if (e.getSlot() == 22) {
                    if (!rouletteEnabled) {
                        p.sendMessage(configManager.getMessage("messages.prefix", "&7[&6Casino&7] ") + configManager.getMessage("messages.game_disabled", "&cThis game is currently disabled!"));
                        return;
                    }
                    gui.openGameMenu(p);
                }
                else if (e.getSlot() == 49) gui.openExchangeMenu(p);
                else if (e.getSlot() == 53) gui.openDetailedStats(p);
                else if (e.getSlot() == 29) {
                    if (!slotsEnabled) {
                        p.sendMessage(configManager.getMessage("messages.prefix", "&7[&6Casino&7] ") + configManager.getMessage("messages.game_disabled", "&cThis game is currently disabled!"));
                        return;
                    }
                    slotMachine.openSlotMachine(p);
                }
                else if (e.getSlot() == 33) {
                    if (!crashEnabled) {
                        p.sendMessage(configManager.getMessage("messages.prefix", "&7[&6Casino&7] ") + configManager.getMessage("messages.game_disabled", "&cThis game is currently disabled!"));
                        return;
                    }
                    crashGame.openCrashMenu(p);
                }
            }
            else if (title.equals(tExchange)) gui.handleExchangeMenuClick(p, e);
            else if (title.equals(tBuy)) gui.handleBuyMenuClick(p, e);
            else if (title.equals(tSell)) gui.handleSellMenuClick(p, e);
            else if (title.equals(tRoulette)) gui.handleGameClick(p, e);
            else if (title.equals(tStats) && e.getSlot() == 26) gui.openMainMenu(p);
            else if (title.equals(tSlots)) slotMachine.handleClick(e);
            else if (title.equals(tCrash)) crashGame.handleClick(e);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        String title = ChatColor.stripColor(e.getView().getTitle());
        String tRoulette = ChatColor.stripColor(configManager.getMessage("gui.titles.roulette", "Roulette Room"));
        if (title.equals(tRoulette)) {
            UUID uuid = e.getPlayer().getUniqueId();
            playersInGame.remove(uuid);
            activeGameInventories.remove(uuid);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (!awaitingInput.containsKey(uuid)) return;

        String input = e.getMessage().trim();
        String mode = awaitingInput.get(uuid);

        // Check for cancel command
        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("отмена")) {
            e.setCancelled(true);
            awaitingInput.remove(uuid);
            p.sendMessage(configManager.getMessage("messages.prefix", "&7[&6Casino&7] ") +
                         configManager.getMessage("messages.exchange.cancelled", "&cOperation cancelled."));
            return;
        }

        e.setCancelled(true);

        Bukkit.getScheduler().runTask(this, () -> {
            try {
                int amount = Integer.parseInt(input);
                if (amount < minExchange) {
                    p.sendMessage(configManager.getMessage("messages.min_exchange", "&cMin: %min%", "min", minExchange));
                    return;
                }
                double commission = commissionPercent / 100.0;
                String prefix = configManager.getMessage("messages.prefix", "&7[&6Casino&7] ");
                int currentChips = playerChips.getOrDefault(uuid, 0);
                String currency = getCurrencyName();

                if (mode.equals("buy")) {
                    int chipsGain = (int) (amount * (1 - commission));
                    if (hasEnoughBalance(p, amount)) {
                        withdrawBalance(p, amount);
                        playerChips.put(uuid, currentChips + chipsGain);
                        p.sendMessage(prefix + configManager.getMessage("messages.exchange.buy_success", "&aPurchased %chips% chips for %coins% %currency%.", "chips", chipsGain, "coins", amount, "currency", currency));
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    } else {
                        p.sendMessage(prefix + configManager.getMessage("messages.insufficient_funds", "&cNo funds!") +
                                     " " + configManager.getMessage("messages.current_balance", "&7(You have: %balance%)", "balance", formatBalance(p)));
                    }
                } else {
                    int monetGain = (int) (amount * (1 - commission));
                    if (currentChips >= amount) {
                        playerChips.put(uuid, currentChips - amount);
                        depositBalance(p, monetGain);
                        p.sendMessage(prefix + configManager.getMessage("messages.exchange.sell_success", "&aSold %chips% chips for %coins% %currency%.", "chips", amount, "coins", monetGain, "currency", currency));
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    } else {
                        p.sendMessage(prefix + configManager.getMessage("messages.no_chips", "&cNo chips!") + " " +
                                     configManager.getMessage("messages.current_chips", "&7(You have: %chips% chips)", "chips", formatNumber(currentChips)));
                    }
                }
                awaitingInput.remove(uuid);
            } catch (NumberFormatException ex) {
                p.sendMessage(configManager.getMessage("messages.invalid_number", "&cInvalid number!") +
                             " " + configManager.getMessage("messages.cancel_hint", "&7(Type 'cancel' to abort)"));
            }
        });
    }

    public int getPlayerSelectionLimit(Player p) {
        if (p.hasPermission("casino.limit.admin")) {
            return selectionLimits.getOrDefault("admin", 25);
        } else if (p.hasPermission("casino.limit.vip")) {
            return selectionLimits.getOrDefault("vip", 10);
        }
        return selectionLimits.getOrDefault("default", 5);
    }

    public void checkGameLoop() {
        if (gameTask == null || gameTask.isCancelled()) {
            if (!playersInGame.isEmpty()) startGameTask();
        }
    }

    private void startGameTask() {
        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (playersInGame.isEmpty()) {
                    cancel();
                    gameState = GameState.WAITING;
                    timer = timerDuration;
                    return;
                }
                if (gameState == GameState.WAITING) {
                    timer--;
                    String clockName = configManager.getMessage("gui.roulette.accepting", "&6Accepting Bets...");
                    String clockLore = configManager.getMessage("gui.roulette.spinning_in", "&eSpinning in: %time%s", "time", timer);
                    ItemStack clock = createItem(Material.CLOCK, clockName, clockLore);

                    for (Inventory inv : activeGameInventories.values()) inv.setItem(4, clock);
                    if (timer <= 0) {
                        gameState = GameState.SPINNING;
                        startSpinAnimation();
                        cancel();
                    }
                }
            }
        };
        gameTask.runTaskTimer(this, 0L, 20L);
    }

    private void startSpinAnimation() {
        new BukkitRunnable() {
            int tick = 0;
            final int[] wheel = {0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26};
            int offset = random.nextInt(wheel.length);
            @Override
            public void run() {
                tick++;
                offset = (offset + 1) % wheel.length;
                for (Inventory inv : activeGameInventories.values()) {
                    for (int i = 0; i < 9; i++) {
                        int num = wheel[(offset + i) % wheel.length];
                        Material mat = num == 0 ? Material.LIME_STAINED_GLASS_PANE :
                                isNumRed(num) ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
                        if (i == 4) mat = num == 0 ? Material.LIME_CONCRETE :
                                isNumRed(num) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
                        inv.setItem(i, createItem(mat, (i==4 ? ChatColor.BOLD : "") + String.valueOf(num)));
                    }
                }
                for (UUID uuid : playersInGame) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f + tick * 0.02f);
                }
                if (tick > 40) {
                    cancel();
                    int winningNumber = wheel[(offset + 4) % wheel.length];
                    processResults(winningNumber);
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private void processResults(int winningNumber) {
        history.addFirst(winningNumber);
        if (history.size() > 10) history.removeLast();

        boolean isRed = isNumRed(winningNumber);
        ChatColor color = winningNumber == 0 ? ChatColor.GREEN : (isRed ? ChatColor.RED : ChatColor.BLACK);
        String colorName = winningNumber == 0 ? configManager.getMessage("words.color.zero", "Zero") : (isRed ? configManager.getMessage("words.color.red", "Red") : configManager.getMessage("words.color.black", "Black"));
        String prefix = configManager.getMessage("messages.prefix", "&7[&6Casino&7] ");

        Set<UUID> betters = new HashSet<>(activeBets.keySet());

        for (UUID uuid : betters) {
            Player p = Bukkit.getPlayer(uuid);
            Map<String, Integer> bets = activeBets.remove(uuid);
            if (bets == null || bets.isEmpty()) continue;

            int totalBet = bets.values().stream().mapToInt(Integer::intValue).sum();
            int win = 0;

            for (Map.Entry<String, Integer> e : bets.entrySet()) {
                String type = e.getKey();
                int amt = e.getValue();

                if (type.equals(String.valueOf(winningNumber))) {
                    win += amt * (winningNumber == 0 ? payoutZero : payoutNumber);
                }
                else if (type.equals("red") && isRed) win += amt * payoutColor;
                else if (type.equals("black") && !isRed && winningNumber != 0) win += amt * payoutColor;
                else if (type.equals("1st12") && winningNumber >= 1 && winningNumber <= 12) win += amt * payoutSector;
                else if (type.equals("2nd12") && winningNumber >= 13 && winningNumber <= 24) win += amt * payoutSector;
                else if (type.equals("3rd12") && winningNumber >= 25 && winningNumber <= 36) win += amt * payoutSector;
            }

            if (win > 0) {
                playerChips.put(uuid, playerChips.getOrDefault(uuid, 0) + win);
                logGameResult(uuid, totalBet, win, true);
                if (p != null) {
                    p.sendMessage(prefix + configManager.getMessage("messages.roulette.win", "&aResult: %color%%number% -> Won: %payout% chips!", "color", color.toString(), "number", winningNumber, "payout", formatNumber(win)));
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                }
            } else {
                logGameResult(uuid, totalBet, 0, false);
                if (p != null) {
                    p.sendMessage(prefix + configManager.getMessage("messages.roulette.loss", "&cResult: %color%%number% -> Lost: %bet% chips.", "color", color.toString(), "number", winningNumber, "bet", formatNumber(totalBet)));
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            }

            // Always update the inventory to show current chips, even for spectators
            if (p != null && activeGameInventories.containsKey(uuid)) {
                gui.updateGameInventory(p, activeGameInventories.get(uuid));
            }
        }

        for (UUID uuid : playersInGame) {
            if (!betters.contains(uuid)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendMessage(prefix + configManager.getMessage("messages.roulette.spectate", "&7Result: %color%%number% (%colorname%)", "color", color.toString(), "number", winningNumber, "colorname", colorName));
                    gui.updateGameInventory(p, activeGameInventories.get(uuid));
                }
            }
        }

        gameState = GameState.WAITING;
        timer = timerDuration;
        checkGameLoop();
    }

    public ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (name != null) meta.setDisplayName(name);
        if (lore.length > 0) meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (name != null) meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public void addGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.LUCK, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    public boolean isNumRed(int n) {
        int[] red = {1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36};
        for (int r : red) if (r == n) return true;
        return false;
    }

    public String formatNumber(int number) {
        return String.format("%,d", number).replace(',', ' ');
    }

    public String getCurrencyName() {
        String customCurrencyName = configManager.getConfig().getString("settings.custom-currency-name", "").trim();
        if (!customCurrencyName.isEmpty()) {
            return customCurrencyName;
        }

        if (economyMode.equalsIgnoreCase("VAULT")) {
            if (economy != null) {
                try {
                    String currencyName = (String) economy.getClass().getMethod("currencyNamePlural").invoke(economy);
                    if (currencyName != null && !currencyName.isEmpty()) {
                        return currencyName;
                    }
                    currencyName = (String) economy.getClass().getMethod("currencyNameSingular").invoke(economy);
                    if (currencyName != null && !currencyName.isEmpty()) {
                        return currencyName;
                    }
                } catch (Exception e) {
                    getLogger().warning("Failed to get currency name from Vault, using fallback: " + e.getMessage());
                }
            }
            return configManager.getMessage("common.currency_name", "coins");
        } else {
            return itemResource.name().toLowerCase();
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public Map<UUID, Integer> getPlayerChips() { return playerChips; }
    public Map<UUID, Integer> getTotalWon() { return totalWon; }
    public Map<UUID, Integer> getTotalLost() { return totalLost; }
    public Map<UUID, Integer> getWins() { return wins; }
    public Map<UUID, Integer> getLosses() { return losses; }
    public Map<UUID, Integer> getTotalRounds() { return totalRounds; }
    public Object getEconomy() { return economy; }
    public Set<UUID> getPlayersInGame() { return playersInGame; }
    public Map<UUID, Inventory> getActiveGameInventories() { return activeGameInventories; }
    public Map<UUID, Map<String, Integer>> getActiveBets() { return activeBets; }
    public Map<UUID, Integer> getCurrentBetAmount() { return currentBetAmount; }
    public LinkedList<Integer> getHistory() { return history; }
    public GameState getGameState() { return gameState; }
    public int getTimer() { return timer; }
    public Map<UUID, String> getAwaitingInput() { return awaitingInput; }
    public Map<String, Integer> getSelectionLimits() { return selectionLimits; }
    public Object getPermission() { return permission; }
    public RouletteGUI getGui() { return gui; }

    public int getMinBet() { return minBet; }
    public int getMaxBet() { return maxBet; }
    public int getMinExchange() { return minExchange; }
    public boolean isCheckForUpdates() { return checkForUpdates; }
    public boolean isRouletteEnabled() { return rouletteEnabled; }
    public boolean isSlotsEnabled() { return slotsEnabled; }
    public boolean isCrashEnabled() { return crashEnabled; }
    public int getSetupStep() { return setupStep; }
    public boolean setupEconomy() {
        try {
            if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
            RegisteredServiceProvider<?> rsp = getServer().getServicesManager().getRegistration(Class.forName("net.milkbowl.vault.economy.Economy"));
            if (rsp == null) return false;
            economy = rsp.getProvider();
            return economy != null;
        } catch (Exception e) {
            economy = null;
            return false;
        }
    }
}