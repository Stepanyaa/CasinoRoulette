package ru.stepanyaa.casinoRoulette;

import dev.faststats.ErrorTracker;
import dev.faststats.bukkit.BukkitContext;
import dev.faststats.data.Metric;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
import ru.stepanyaa.casinoRoulette.bootstrap.CasinoCore;
import ru.stepanyaa.casinoRoulette.platform.PlatformAdapter;
import ru.stepanyaa.casinoRoulette.platform.PlatformFactory;
import ru.stepanyaa.casinoRoulette.platform.impl.bukkit.AbstractBukkitAdapter;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;
import ru.stepanyaa.casinoRoulette.stats.CoreStatsSource;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoTask;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class CasinoRoulette extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    public static final ErrorTracker ERROR_TRACKER = ErrorTracker.contextAware();
    private final AtomicInteger gameCount = new AtomicInteger();

    private final BukkitContext context = new BukkitContext.Factory(this, "7eeb807d660afc63f69815ea95be83e2")
            .errorTrackerService(ERROR_TRACKER)
            .metrics(factory -> factory
                    .addMetric(Metric.number("game_count", gameCount::get))
                    .addMetric(Metric.string("server_version", () -> "1.0.0"))

                    .onFlush(() -> gameCount.set(0))

                    .create())
            .create();

    private Object economy;
    private Object permission;
    private final Random random = new Random();

    private ConfigManager configManager;

    private BukkitGameBridge bridge;

    private ru.stepanyaa.casinoRoulette.game.GameHub games;

    private RouletteGUI legacyGui;
    private LeaderboardManager legacyLeaderboardManager;

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
    private final java.util.Map<String, ExtraConfig> extraConfigs = new java.util.HashMap<>();

    public enum GameState { WAITING, SPINNING }
    public GameState gameState = GameState.WAITING;
    public int timer = 30;
    private int timerDuration = 30;
    private CasinoTask gameTask;

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
    private boolean wheelEnabled = true;
    private boolean dailyWheelEnabled = true;
    private boolean leaderboardsEnabled = true;

    private boolean isConfigured = false;
    private String economyMode = "AUTO";
    private Material itemResource = Material.DIAMOND;
    private int setupStep = 0;

    private final Map<UUID, String> awaitingInput = new HashMap<>();

    private final Map<UUID, Integer> lastWin = new HashMap<>();

    private final Map<UUID, Long> biggestWin = new HashMap<>();

    private PlatformAdapter adapter;
    private CasinoCore core;
    private CoreStatsSource statsSource;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.adapter = PlatformFactory.createBukkit(this, getLogger());
        this.core = new CasinoCore(adapter);
        this.statsSource = new CoreStatsSource(this);
        this.core.start(((AbstractBukkitAdapter) adapter).placeholderService(), statsSource);

        configManager = new ConfigManager(this);
        loadConfigValues();
        db = new DatabaseManager(this);
        for (String f : new String[]{"leaderboard.yml","wheel.yml","dailywheel.yml","menus.yml"}) extraConfigs.put(f, new ExtraConfig(this, f));

        legacyGui = new RouletteGUI(this);
        legacyLeaderboardManager = new LeaderboardManager(this);

        setupEconomy();
        setupPermissions();

        bridge = new BukkitGameBridge(this);
        games = core.startGames(bridge, bridge, bridge);

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("casino").setExecutor(this);
        getCommand("casino").setTabCompleter(this);
        if (getCommand("dailywheel") != null) getCommand("dailywheel").setExecutor(this);

        if (checkForUpdates) {
            new UpdateChecker(this).checkForUpdates();
        }

        if (!isConfigured) {
            if (!checkAndAutoConfigure(null)) {
                CasinoScheduler.delay(20L, this::sendSetupWizardToAdmins);
            }
        }
        context.ready();
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
        context.shutdown();
        if (gameTask != null) gameTask.cancel();
        if (db != null) db.close();

        if (core != null) core.stop();
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();

        handlePlayerDisconnect(uuid);

        saveSpecificPlayer(uuid);
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        db.loadPlayer(p.getUniqueId(), this);
        db.setName(p.getUniqueId(), p.getName());

        final UUID joined = p.getUniqueId();
        CasinoScheduler.async(() -> biggestWin.put(joined, db.getStat(joined, "biggest_win")));

        if (!isConfigured && p.hasPermission("casino.admin")) {
            CasinoScheduler.delayAtEntity(p, 20L, () -> {
                if (!checkAndAutoConfigure(p)) {
                    sendSetupMessage(p);
                }
            });
        }
    }

    private boolean checkAndAutoConfigure(Player adminToNotify) {
        if (isConfigured) return true;

        boolean hasSpigot = false;
        try {
            Player.class.getMethod("spigot");
            hasSpigot = true;
        } catch (NoSuchMethodException ignored) {}

        boolean pointsReady = getServer().getPluginManager().getPlugin("PlayerPoints") != null
                && getServer().getPluginManager().isPluginEnabled("PlayerPoints");
        boolean vaultReady = setupEconomy();
        boolean ecoReady = pointsReady || vaultReady;
        if (!ecoReady) {

            return false;
        }

        if (pointsReady && (!vaultReady || ru.stepanyaa.casinoRoulette.platform.Platforms.isFolia())) {
            economyMode = "PLAYERPOINTS";
        } else if (vaultReady) {
            economyMode = "VAULT";
        }

        isConfigured = true;
        configManager.getConfig().set("settings.is-configured", true);
        configManager.getConfig().set("settings.economy-mode", economyMode);
        configManager.saveConfig();
        if (adapter instanceof AbstractBukkitAdapter) {
            ((AbstractBukkitAdapter) adapter).rebindEconomy();
        }

        String done = "CasinoRoulette configured automatically: economy mode " + economyMode
                + (hasSpigot ? "" : " (plain-text setup messages: no Spigot chat API)");
        getLogger().info(done);
        if (adminToNotify != null && adminToNotify.isOnline()) {
            adminToNotify.sendMessage(configManager.getMessage("messages.setup.auto_done",
                    "&a[CasinoRoulette] &7Setup completed automatically. Economy: &f%mode%",
                    "mode", economyMode));
        }
        return true;
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
        economyMode = config.getString("settings.economy-mode", "AUTO");
        try {
            itemResource = Material.valueOf(config.getString("settings.item-resource", "DIAMOND").toUpperCase());
        } catch (Exception e) {
            itemResource = Material.DIAMOND;
        }

        rouletteEnabled = config.getBoolean("roulette.enabled", true);
        slotsEnabled = config.getBoolean("slots.enabled", true);
        crashEnabled = config.getBoolean("crash.enabled", true);
        wheelEnabled = config.getBoolean("features.fortune-wheel", true);
        dailyWheelEnabled = config.getBoolean("features.free-wheel", true);
        leaderboardsEnabled = config.getBoolean("features.leaderboards", true);

        timerDuration = config.getInt("roulette.timer_duration", 30);
        timer = timerDuration;
        payoutNumber = config.getInt("roulette.payouts.number", 36);
        payoutColor = config.getInt("roulette.payouts.color", 2);
        payoutSector = config.getInt("roulette.payouts.sector", 3);
        payoutZero = config.getInt("roulette.payouts.zero", 36);
        commissionPercent = config.getDouble("settings.commission_percent", 10.0);

        boolean itemMode = isItemMode();
        String modeKey = itemMode ? "item" : "vault";
        minExchange = config.getInt("economy." + modeKey + ".min_exchange", itemMode ? 10 : 100);
        minBet = config.getInt("economy." + modeKey + ".min_bet", itemMode ? 10 : 1000);
        maxBet = config.getInt("economy." + modeKey + ".max_bet", itemMode ? 1000 : 10000);
        checkForUpdates = config.getBoolean("check-for-updates", true);

        selectionLimits.clear();
        if (config.isConfigurationSection("selection_limits")) {
            for (String key : config.getConfigurationSection("selection_limits").getKeys(false)) {
                selectionLimits.put(key.toLowerCase(), config.getInt("selection_limits." + key));
            }
        }
        selectionLimits.putIfAbsent("default", 5);
        if (adapter instanceof AbstractBukkitAdapter) {
            ((AbstractBukkitAdapter) adapter).rebindEconomy();
        }
        if (games != null) games.reload();
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

    public void handlePlayerDisconnect(UUID uuid) {
        if (games != null) {

            games.forget(uuid);
        }
        if (activeBets.containsKey(uuid)) {
            Map<String, Integer> bets = activeBets.remove(uuid);
            int totalRefund = bets.values().stream().mapToInt(Integer::intValue).sum();
            if (totalRefund > 0) {
                playerChips.put(uuid, playerChips.getOrDefault(uuid, 0) + totalRefund);
                getLogger().info("Refunded " + totalRefund + " chips to player " + uuid + " due to disconnection");
            }
        }

        playersInGame.remove(uuid);
        activeGameInventories.remove(uuid);
        currentBetAmount.remove(uuid);
        awaitingInput.remove(uuid);
    }

    private ru.stepanyaa.casinoRoulette.economy.EconomyProvider economyProvider() {
        return adapter == null ? null : adapter.economy();
    }

    public boolean hasEnoughBalance(Player p, double amount) {
        if (isItemMode()) {
            return p.getInventory().containsAtLeast(new ItemStack(itemResource), units(amount));
        }
        ru.stepanyaa.casinoRoulette.economy.EconomyProvider provider = economyProvider();
        if (provider == null) {

            return false;
        }
        return provider.has(p.getUniqueId(), amount);
    }

    public boolean withdrawBalance(Player p, double amount) {
        int count = units(amount);
        if (isItemMode()) {
            if (count <= 0) {
                return true;
            }
            if (!p.getInventory().containsAtLeast(new ItemStack(itemResource), count)) {
                return false;
            }

            return p.getInventory().removeItem(new ItemStack(itemResource, count)).isEmpty();
        }
        ru.stepanyaa.casinoRoulette.economy.EconomyProvider provider = economyProvider();
        if (provider == null) {
            return false;
        }
        return provider.withdraw(p.getUniqueId(), amount);
    }

    public boolean depositBalance(Player p, double amount) {
        if (isItemMode()) {
            int count = units(amount);
            if (count <= 0) {
                return true;
            }
            Map<Integer, ItemStack> overflow = p.getInventory().addItem(new ItemStack(itemResource, count));
            for (ItemStack stack : overflow.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), stack);
            }
            return true;
        }
        ru.stepanyaa.casinoRoulette.economy.EconomyProvider provider = economyProvider();
        if (provider == null) {
            return false;
        }
        return provider.deposit(p.getUniqueId(), amount);
    }

    private int units(double amount) {
        double rounded = Math.ceil(amount - 1.0E-9D);
        return (int) Math.max(0.0D, Math.min(Integer.MAX_VALUE, rounded));
    }

    public String formatBalance(Player p) {
        if (isItemMode()) {
            int count = 0;
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && item.getType() == itemResource) {
                    count += item.getAmount();
                }
            }
            return count + " " + getCurrencyName();
        }
        ru.stepanyaa.casinoRoulette.economy.EconomyProvider provider = economyProvider();
        if (provider == null) {
            return "0 " + getCurrencyName();
        }
        return provider.format(provider.balance(p.getUniqueId()));
    }

    public void sendSetupWizardToAdmins() {
        if (isConfigured) return;
        if (checkAndAutoConfigure(null)) return;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("casino.admin")) sendSetupMessage(p);
        }
    }

    private static Boolean bungeeChatApi;

    private static boolean hasBungeeChatApi() {
        if (bungeeChatApi == null) {
            try {
                Class.forName("net.md_5.bungee.api.chat.BaseComponent");
                bungeeChatApi = Boolean.TRUE;
            } catch (Throwable absent) {
                bungeeChatApi = Boolean.FALSE;
            }
        }
        return bungeeChatApi;
    }

    public void sendSetupMessage(Player p) {
        if (hasBungeeChatApi()) {
            try {
                BungeeSetupMessage.sendSetupMessage(this, p);
                return;
            } catch (Throwable e) {

                getLogger().warning("Rich setup message unavailable, falling back to plain text: " + e);
            }
        }

        simpleSetupMessage(p);
    }

    private void simpleSetupMessage(Player p) {
        ConfigManager cm = this.configManager;
        p.sendMessage(cm.getMessage("messages.setup.separator", "&6============================================="));
        p.sendMessage(cm.getMessage("messages.setup.wizard_title", "&e[ CasinoRoulette Setup ]"));
        p.sendMessage(cm.getMessage("messages.setup.wizard_subtitle", "&fLet's configure the plugin!"));
        p.sendMessage("");
        if (this.setupStep == 0) {
            p.sendMessage(cm.getMessage("messages.setup.step1", "&bStep 1: choose the plugin language:"));
            String[][] languages = {
                    {"en", "setup.language.en"}, {"ru", "setup.language.ru"}, {"de", "setup.language.de"},
                    {"fr", "setup.language.fr"}, {"tr", "setup.language.tr"}, {"pl", "setup.language.pl"}, {"pt_br", "setup.language.pt_br"}
            };
            for (String[] lang : languages) {
                p.sendMessage(cm.getMessage("messages." + lang[1], "&e[ " + lang[0] + " ]") + " " +
                        cm.getMessage("messages.setup.command_hint", "&7/casino setup %type% %value%", "type", "lang", "value", lang[0]));
            }
        } else if (this.setupStep == 1) {
            p.sendMessage(cm.getMessage("messages.setup.step2", "&bStep 2: choose the server currency system:"));
            boolean vaultInstalled = this.getServer().getPluginManager().getPlugin("Vault") != null;
            boolean pointsInstalled = this.getServer().getPluginManager().getPlugin("PlayerPoints") != null;
            boolean ecoReady = vaultInstalled && this.setupEconomy();
            p.sendMessage(cm.getMessage(ecoReady ? "messages.setup.eco_vault" : "messages.setup.eco_vault_disabled", ecoReady ? "&a&l[ Vault Economy ]" : "&7&l[ Vault Economy ]") + " " +
                    cm.getMessage("messages.setup.command_hint", "&7/casino setup %type% %value%", "type", "eco", "value", "vault"));
            p.sendMessage((pointsInstalled ? "&a&l[ PlayerPoints ]" : "&7&l[ PlayerPoints ]") + " " +
                    cm.getMessage("messages.setup.command_hint", "&7/casino setup %type% %value%", "type", "eco", "value", "playerpoints"));
            p.sendMessage(cm.getMessage("messages.setup.eco_item", "&6&l[ Item Resource ]") + " " +
                    cm.getMessage("messages.setup.command_hint", "&7/casino setup %type% %value%", "type", "eco", "value", "item"));
            p.sendMessage("&b&l[ AUTO ]" + " " +
                    cm.getMessage("messages.setup.command_hint", "&7/casino setup %type% %value%", "type", "eco", "value", "auto"));
        }
        p.sendMessage(cm.getMessage("messages.setup.separator", "&6============================================="));
    }

    private void sendClickableMessage(Player player, String text, String hoverText, String command) {
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', text + " &7- " + hoverText));
    }

    public void logGameResult(UUID uuid, int bet, int payout, boolean isWin) {
        if (payout > 0) db.incrementStat(uuid, "biggest_win", payout, true);
        if (!isWin) db.incrementStat(uuid, "biggest_loss", bet, true);
        db.incrementStat(uuid, "bets", 1, false);
        totalRounds.put(uuid, totalRounds.getOrDefault(uuid, 0) + 1);
        if (isWin) {
            wins.put(uuid, wins.getOrDefault(uuid, 0) + 1);
            totalWon.put(uuid, totalWon.getOrDefault(uuid, 0) + payout);
            lastWin.put(uuid, payout);
            if (payout > biggestWin.getOrDefault(uuid, 0L)) biggestWin.put(uuid, (long) payout);
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
                if (java.util.Arrays.asList("en","ru","de","fr","tr","pl","pt_br").contains(selectedLang)) {
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
                if (selectedEco.equals("PLAYER_POINTS") || selectedEco.equals("POINTS")) {
                    selectedEco = "PLAYERPOINTS";
                }
                if (selectedEco.equals("VAULT") || selectedEco.equals("ITEM")
                        || selectedEco.equals("PLAYERPOINTS") || selectedEco.equals("AUTO")
                        || selectedEco.equals("INTERNAL")) {
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
                if (!(sender instanceof Player)) {
                    sender.sendMessage(configManager.getMessage("messages.setup.not_configured", "&cPlugin is not configured yet."));
                    return true;
                }
                if (checkAndAutoConfigure((Player) sender)) {
                    openMainMenu((Player) sender);
                } else {
                    sendSetupMessage((Player) sender);
                }
            } else {
                sender.sendMessage(configManager.getMessage("messages.setup.not_configured", "&cThe plugin is not configured yet. Please contact an administrator."));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("dailywheel")) { if (!(sender instanceof Player)) { sender.sendMessage(configManager.getMessage("messages.players_only", "&cPlayers only!")); return true; } if (!dailyWheelEnabled) { sender.sendMessage(configManager.getMessage("messages.prefix", "&7[&6Casino&7] ") + configManager.getMessage("messages.game_disabled", "&cThis game is currently disabled!")); return true; } withPlayer((Player) sender, p -> games.daily().open(p)); return true; }

        if (args.length > 0 && (args[0].equalsIgnoreCase("top") || args[0].equalsIgnoreCase("leaderboard"))) { if (!(sender instanceof Player)) { sender.sendMessage(configManager.getMessage("messages.players_only", "&cPlayers only!")); return true; } if (!leaderboardsEnabled) { sender.sendMessage(configManager.getMessage("messages.prefix", "&7[&6Casino&7] ") + configManager.getMessage("messages.game_disabled", "&cThis game is currently disabled!")); return true; } final String category = args.length>1?args[1].toLowerCase():"money"; withPlayer((Player) sender, p -> games.leaderboards().open(p, category)); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("wheel")) { if (!(sender instanceof Player)) { sender.sendMessage(configManager.getMessage("messages.players_only", "&cPlayers only!")); return true; } if (!wheelEnabled) { sender.sendMessage(configManager.getMessage("messages.prefix", "&7[&6Casino&7] ") + configManager.getMessage("messages.game_disabled", "&cThis game is currently disabled!")); return true; } withPlayer((Player) sender, p -> games.wheel().open(p)); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("daily")) { if (!(sender instanceof Player)) { sender.sendMessage(configManager.getMessage("messages.players_only", "&cPlayers only!")); return true; } if (!dailyWheelEnabled) { sender.sendMessage(configManager.getMessage("messages.prefix", "&7[&6Casino&7] ") + configManager.getMessage("messages.game_disabled", "&cThis game is currently disabled!")); return true; } withPlayer((Player) sender, p -> games.daily().open(p)); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("stats") && args.length > 1) { sender.sendMessage(configManager.getMessage("messages.prefix", "&7[&6Casino&7] ")+ db.formatStats(args[1])); return true; }

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

        openMainMenu((Player) sender);
        return true;
    }

    public ru.stepanyaa.casinoRoulette.platform.CasinoPlayer wrap(Player player) {
        if (player == null || adapter == null) return null;
        return adapter.player(player.getUniqueId()).orElse(null);
    }

    private void withPlayer(Player player,
                            java.util.function.Consumer<ru.stepanyaa.casinoRoulette.platform.CasinoPlayer> action) {
        ru.stepanyaa.casinoRoulette.platform.CasinoPlayer wrapped = wrap(player);
        if (wrapped != null && games != null) {
            action.accept(wrapped);
        }
    }

    public void openMainMenu(Player player) {
        withPlayer(player, p -> games.gui().openMainMenu(p));
    }

    public ru.stepanyaa.casinoRoulette.game.GameHub getGames() {
        return games;
    }

    public RouletteGUI getGui() {
        return legacyGui;
    }

    public LeaderboardManager getLeaderboardManager() {
        return legacyLeaderboardManager;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("casino")) return java.util.Collections.emptyList();
        java.util.List<String> base = java.util.Arrays.asList("reload","top","leaderboard","stats","wheel","daily","setup");
        if (args.length == 1) {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (String b : base) if (b.startsWith(args[0].toLowerCase())) out.add(b);
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("top") || args[0].equalsIgnoreCase("leaderboard"))) {
            return java.util.Arrays.asList("money","wins","bets","games","biggest_win","biggest_loss","daily","wheel","chips_bought");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("lang")) {
            return java.util.Arrays.asList("en","ru","de","fr","tr","pl","pt_br");
        }
        return java.util.Collections.emptyList();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (isConfigured) {
            return;
        }
        String guiId = ru.stepanyaa.casinoRoulette.platform.impl.bukkit.BukkitCasinoInventory
                .guiIdOf(e.getView().getTopInventory());
        if (guiId != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        String guiId = ru.stepanyaa.casinoRoulette.platform.impl.bukkit.BukkitCasinoInventory
                .guiIdOf(e.getInventory());
        if (!ru.stepanyaa.casinoRoulette.game.RouletteGUI.GUI_TABLE.equals(guiId)) {
            return;
        }
        UUID uuid = e.getPlayer().getUniqueId();
        playersInGame.remove(uuid);
        activeGameInventories.remove(uuid);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        UUID uuid = p.getUniqueId();
        if (!awaitingInput.containsKey(uuid)) return;

        String input = e.getMessage().trim();
        String mode = awaitingInput.get(uuid);

        if (input.equalsIgnoreCase("cancel") || input.equalsIgnoreCase("отмена")) {
            e.setCancelled(true);
            awaitingInput.remove(uuid);
            p.sendMessage(configManager.getMessage("messages.prefix", "&7[&6Casino&7] ") +
                         configManager.getMessage("messages.exchange.cancelled", "&cOperation cancelled."));
            return;
        }

        e.setCancelled(true);

        CasinoScheduler.atEntity(p, () -> {
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

                    if (withdrawBalance(p, amount)) {
                        playerChips.put(uuid, currentChips + chipsGain);
                        db.incrementStat(uuid, "chips_bought", chipsGain, false);
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
                        if (!depositBalance(p, monetGain)) {

                            playerChips.put(uuid, currentChips);
                            p.sendMessage(prefix + configManager.getMessage("messages.economy_unavailable",
                                    "&cEconomy is unavailable, your chips were returned."));
                            awaitingInput.remove(uuid);
                            return;
                        }
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

    private static final int[] WHEEL = {0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26};

    private void startGameTask() {
        if (gameTask != null && !gameTask.isCancelled()) {
            return;
        }
        if (timer <= 0) {
            timer = timerDuration;
        }
        gameState = GameState.WAITING;

        gameTask = CasinoScheduler.timer(0L, 20L, task -> {
            if (playersInGame.isEmpty()) {
                task.cancel();
                gameTask = null;
                gameState = GameState.WAITING;
                timer = timerDuration;
                return;
            }
            if (gameState == GameState.WAITING) {
                timer--;
                if (timer == 25 || timer == 20 || timer == 15 || timer == 10
                        || timer == 5 || timer == 4 || timer == 3 || timer == 2 || timer == 1) {
                    for (UUID uuid : new java.util.ArrayList<>(playersInGame)) {
                        Player viewer = Bukkit.getPlayer(uuid);
                        if (viewer != null) {
                            playSpinSound(viewer, Math.min(2.0f, 0.7f + (Math.max(0, timerDuration - timer)) * 0.03f));
                        }
                    }
                }
                String clockName = configManager.getMessage("gui.roulette.accepting", "&6Accepting Bets...");
                String clockLore = configManager.getMessage("gui.roulette.spinning_in", "&eSpinning in: %time%s", "time", timer);
                final ItemStack clock = createItem(Material.CLOCK, clockName, clockLore);

                forEachViewer((viewer, inventory) -> inventory.setItem(4, clock));

                if (games != null) {
                    games.gui().updateAllTables();
                }

                if (timer <= 0) {
                    gameState = GameState.SPINNING;
                    if (games != null) {
                        games.gui().updateAllTables();
                    }
                    startSpinAnimation();
                    task.cancel();
                    gameTask = null;
                }
            }
        });
    }

    private void forEachViewer(java.util.function.BiConsumer<Player, Inventory> action) {
        for (UUID uuid : new java.util.ArrayList<>(playersInGame)) {
            final Player viewer = Bukkit.getPlayer(uuid);
            final Inventory inventory = activeGameInventories.get(uuid);
            if (viewer == null || inventory == null) continue;
            CasinoScheduler.atEntity(viewer, () -> action.accept(viewer, inventory));
        }
    }

    private void startSpinAnimation() {

        final int[] state = {0, random.nextInt(WHEEL.length)};

        CasinoScheduler.timer(0L, 2L, task -> {
            state[0]++;
            state[1] = (state[1] + 1) % WHEEL.length;
            final int tick = state[0];
            final int offset = state[1];

            forEachViewer((viewer, inventory) -> {
                renderWheelStrip(inventory, offset);
                viewer.playSound(viewer.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING,
                        0.8f, Math.min(2.0f, 1.2f + tick * 0.02f));
            });

            if (tick > 40) {
                task.cancel();
                int winningNumber = WHEEL[(offset + 4) % WHEEL.length];
                processResults(winningNumber);
            }
        });
    }

    private void renderWheelStrip(Inventory inventory, int offset) {
        for (int i = 0; i < 9; i++) {
            int num = WHEEL[(offset + i) % WHEEL.length];
            Material mat = num == 0 ? Material.LIME_STAINED_GLASS_PANE :
                    isNumRed(num) ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
            if (i == 4) mat = num == 0 ? Material.LIME_CONCRETE :
                    isNumRed(num) ? Material.RED_CONCRETE : Material.BLACK_CONCRETE;
            inventory.setItem(i, createItem(mat, (i == 4 ? ChatColor.BOLD : "") + String.valueOf(num)));
        }
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

            if (p != null && games != null) {
                games.gui().updateTable(uuid);
            }
        }

        for (UUID uuid : playersInGame) {
            if (!betters.contains(uuid)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendMessage(prefix + configManager.getMessage("messages.roulette.spectate", "&7Result: %color%%number% (%colorname%)", "color", color.toString(), "number", winningNumber, "colorname", colorName));
                    if (games != null) {
                        games.gui().updateTable(uuid);
                    }
                }
            }
        }

        gameState = GameState.WAITING;
        timer = timerDuration;
        gameTask = null;
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

        if (isItemMode()) {
            return itemResource.name().toLowerCase();
        }

        ru.stepanyaa.casinoRoulette.economy.EconomyProvider provider = economyProvider();
        if (provider != null) {
            String name = provider.currencyName();
            if (name != null && !name.trim().isEmpty()) {
                return name.trim();
            }
        }
        return configManager.getMessage("common.currency_name", "coins");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public Map<UUID, Integer> getPlayerChips() { return playerChips; }
    public Map<UUID, Integer> getTotalWon() { return totalWon; }
    public Map<UUID, Integer> getTotalLost() { return totalLost; }
    public Map<UUID, Integer> getWins() { return wins; }
    public Map<UUID, Integer> getLosses() { return losses; }
    public Map<UUID, Integer> getTotalRounds() { return totalRounds; }
    public Map<UUID, Integer> getLastWin() { return lastWin; }
    public Map<UUID, Long> getBiggestWin() { return biggestWin; }
    public PlatformAdapter getAdapter() { return adapter; }
    public CasinoCore getCore() { return core; }
    public Object getEconomy() { return economy; }
    public DatabaseManager getDb() { return db; }
    public ExtraConfig getExtra(String name) { return extraConfigs.get(name); }
    public void addChips(UUID uuid, int amount) { playerChips.put(uuid, playerChips.getOrDefault(uuid,0)+amount); }
    public boolean takeChips(UUID uuid, int amount) { int c=playerChips.getOrDefault(uuid,0); if(c<amount)return false; playerChips.put(uuid,c-amount); return true; }

    private boolean requireEnabled(Player p, boolean enabled) {
        if (enabled) return true;
        p.sendMessage(configManager.getMessage("messages.prefix", "&7[&6Casino&7] ")
                + configManager.getMessage("messages.game_disabled", "&cThis game is currently disabled!"));
        return false;
    }

    private static final String[][] MAIN_MENU_BUTTONS = {
            {"roulette", "roulette-slot", "20"},
            {"wheel", "fortune-wheel-slot", "22"},
            {"daily", "daily-casino-slot", "24"},
            {"slots", "slots-slot", "29"},
            {"leaderboards", "leaderboards-slot", "31"},
            {"crash", "crash-slot", "33"},
            {"profile", "profile-slot", "45"},
            {"exchange", "exchange-slot", "49"},
            {"stats", "stats-slot", "53"},
    };

    private final java.util.Set<String> warnedMenuSlots = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public java.util.Map<Integer, String> mainMenuActions() {
        java.util.Map<Integer, String> actions = new java.util.LinkedHashMap<>();
        org.bukkit.configuration.file.FileConfiguration cfg = configManager.getConfig();
        for (String[] button : MAIN_MENU_BUTTONS) {
            String action = button[0];
            if (action.equals("wheel") && !wheelEnabled) continue;
            if (action.equals("daily") && !dailyWheelEnabled) continue;
            if (action.equals("leaderboards") && !leaderboardsEnabled) continue;
            int fallback = Integer.parseInt(button[2]);
            int slot = cfg.getInt("main-menu.buttons." + button[1], fallback);
            if (slot < 0 || slot > 53) {
                if (warnedMenuSlots.add(action)) {
                    getLogger().warning("main-menu.buttons." + button[1] + " = " + slot
                            + " is outside 0-53, using the default slot " + fallback + ".");
                }
                slot = fallback;
            }
            String taken = actions.get(slot);
            if (taken != null) {
                if (warnedMenuSlots.add(action)) {
                    getLogger().warning("Main menu slot " + slot + " is used by both '" + taken
                            + "' and '" + action + "'; '" + action + "' was skipped.");
                }
                continue;
            }
            actions.put(slot, action);
        }
        return actions;
    }

    public boolean isWheelEnabled() { return wheelEnabled; }
    public boolean isDailyWheelEnabled() { return dailyWheelEnabled; }
    public boolean isLeaderboardsEnabled() { return leaderboardsEnabled; }

    public boolean isItemMode() {
        String mode = economyMode == null ? "" : economyMode.trim().toUpperCase(java.util.Locale.ROOT);
        return mode.equals("ITEM") || mode.equals("ITEMS") || mode.equals("RESOURCE");
    }
    public Material getItemResource() { return itemResource; }
    public int getChips(UUID uuid) { return playerChips.getOrDefault(uuid, 0); }
    public String getBetCurrencyName() { return isItemMode() ? getCurrencyName() : configManager.getMessage("common.chips", "chips"); }
    public String formatGameBalance(Player p) {
        if (isItemMode()) {
            int c = 0;
            for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() == itemResource) c += it.getAmount();
            return c + " " + getCurrencyName();
        }
        return formatNumber(playerChips.getOrDefault(p.getUniqueId(), 0)) + " " + configManager.getMessage("common.chips", "chips");
    }
    public boolean hasGameBalance(Player p, int amount) {
        if (isItemMode()) return p.getInventory().containsAtLeast(new ItemStack(itemResource), amount);
        return playerChips.getOrDefault(p.getUniqueId(), 0) >= amount;
    }
    public void takeGameBalance(Player p, int amount) {
        if (isItemMode()) p.getInventory().removeItem(new ItemStack(itemResource, amount));
        else playerChips.put(p.getUniqueId(), Math.max(0, playerChips.getOrDefault(p.getUniqueId(), 0) - amount));
    }
    public void giveGameBalance(Player p, int amount) {
        if (isItemMode()) {
            java.util.Map<Integer, ItemStack> overflow = p.getInventory().addItem(new ItemStack(itemResource, amount));
            for (ItemStack o : overflow.values()) p.getWorld().dropItemNaturally(p.getLocation(), o);
        } else playerChips.put(p.getUniqueId(), playerChips.getOrDefault(p.getUniqueId(), 0) + amount);
    }
    public void playConfiguredSound(Player p, String key, String defName, float pitch) {
        if (p == null) return;
        org.bukkit.configuration.file.FileConfiguration cfg = configManager.getConfig();
        if (!cfg.getBoolean("sounds.enabled", true)) return;
        String name = cfg.getString("sounds." + key, defName);
        try { p.playSound(p.getLocation(), Sound.valueOf(name.toUpperCase()), 1f, pitch); } catch (Exception ignored) {}
    }
    public void playClickSound(Player p) { playConfiguredSound(p, "click", "UI_BUTTON_CLICK", 1f); }
    public void playMenuSound(Player p) { playConfiguredSound(p, "open_menu", "BLOCK_CHEST_OPEN", 1f); }
    public void playSpinSound(Player p, float pitch) { playConfiguredSound(p, "spin", "BLOCK_NOTE_BLOCK_PLING", pitch); }
    public void playWinSound(Player p) { playConfiguredSound(p, "win", "ENTITY_PLAYER_LEVELUP", 1f); }
    public void playLoseSound(Player p) { playConfiguredSound(p, "lose", "ENTITY_VILLAGER_NO", 1f); }
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
            if (getServer().getPluginManager().getPlugin("PlayerPoints") != null
                    && getServer().getPluginManager().isPluginEnabled("PlayerPoints")) {
                return true;
            }
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
