package ru.stepanyaa.casinoRoulette.bootstrap;

import com.google.inject.Inject;
import org.spongepowered.api.Server;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.event.EventManager;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.event.lifecycle.StartedEngineEvent;
import org.spongepowered.api.event.lifecycle.StoppingEngineEvent;
import org.spongepowered.plugin.PluginContainer;
import ru.stepanyaa.casinoRoulette.platform.CasinoCommand;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;
import ru.stepanyaa.casinoRoulette.platform.CasinoSender;
import ru.stepanyaa.casinoRoulette.platform.impl.sponge.SpongeAdapter;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public final class SpongeBootstrap {

    private static final Logger LOGGER = Logger.getLogger("CasinoRoulette");

    private final Object pluginContainer;
    private final File dataFolder;

    private SpongeAdapter adapter;
    private CasinoCore core;
    private StandaloneGameState state;

    @Inject
    public SpongeBootstrap(PluginContainer pluginContainer) {
        this.pluginContainer = pluginContainer;
        this.dataFolder = resolveDataFolder();
        if (!this.dataFolder.exists() && !this.dataFolder.mkdirs()) {
            LOGGER.warning("Could not create CasinoRoulette data directory: "
                    + this.dataFolder.getAbsolutePath());
        }
        LOGGER.info("CasinoRoulette data directory: " + this.dataFolder.getAbsolutePath());

    }

    private File resolveDataFolder() {
        try {
            Class<?> sponge = Class.forName("org.spongepowered.api.Sponge");
            Object game = sponge.getMethod("game").invoke(null);
            Object configManager = game.getClass().getMethod("configManager").invoke(game);
            Object pluginConfig = configManager.getClass()
                    .getMethod("pluginConfig",
                            Class.forName("org.spongepowered.plugin.PluginContainer"))
                    .invoke(configManager, pluginContainer);
            Object directory = pluginConfig.getClass()
                    .getMethod("directory").invoke(pluginConfig);
            return ((java.nio.file.Path) directory).toFile();
        } catch (Throwable unavailable) {
            return new File("config" + File.separator + "casinoroulette");
        }
    }

    private void subscribeLifecycle() {
        try {
            Class<?> sponge = Class.forName("org.spongepowered.api.Sponge");
            Object game = sponge.getMethod("game").invoke(null);
            EventManager eventManager = (EventManager) game.getClass().getMethod("eventManager").invoke(game);
            eventManager.registerListeners((PluginContainer) pluginContainer, this);
        } catch (Throwable failure) {
            LOGGER.severe("Could not register Sponge lifecycle listeners: " + failure);
        }
    }

    @Listener
    public void onStarted(StartedEngineEvent<Server> event) {
        start();
    }

    @Listener
    public void onStopping(StoppingEngineEvent<Server> event) {
        stop();
    }

    @Listener
    public void onRegisterCommands(RegisterCommandEvent<Command.Raw> event) {
        start();
        registerCommands(event);
    }

    private synchronized void start() {
        if (core != null && core.isStarted() && state != null) {
            return;
        }
        try {
            this.adapter = new SpongeAdapter(pluginContainer, LOGGER, dataFolder);
            this.core = new CasinoCore(adapter);
            core.start(adapter.placeholderService(), null);
            this.state = new StandaloneGameState(dataFolder, LOGGER);
            ru.stepanyaa.casinoRoulette.game.GameHub games =
                    core.startGames(state, state, state);
            state.attach(core.context(), games);
            configureEconomy();
            registerSpongeCommands();
            if (!isConfigured()) {
                ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler.delay(40L, () -> {
                    for (CasinoPlayer player : adapter.onlinePlayers()) {
                        if (player.hasPermission("casino.admin")) showSetup(player, 0);
                    }
                });
            }
        } catch (Throwable failure) {
            LOGGER.severe("Startup failed: " + failure);
        }
    }

    private void registerCommands(Object registerCommandEvent) {
        if (adapter != null) {
            adapter.flushCommands(registerCommandEvent);
        }
    }

    private void registerSpongeCommands() {
        adapter.registerCommand(new CasinoCommand() {
            @Override public String name() { return "casino"; }
            @Override public List<String> aliases() { return Arrays.asList("roulette", "cr"); }
            @Override public String description() { return "Open the CasinoRoulette menu"; }
            @Override public boolean execute(CasinoSender sender, String[] args) {
                if (args.length > 0 && args[0].equalsIgnoreCase("setup")) {
                    return handleSetup(sender, args);
                }
                if (!isConfigured()) {
                    showSetup(sender, 0);
                    return true;
                }
                if (!(sender instanceof CasinoPlayer)) {
                    sender.sendMessage("&cThis command can only be used by a player.");
                    return true;
                }
                CasinoPlayer player = (CasinoPlayer) sender;
                if (args.length == 0) { core.games().gui().openMainMenu(player); return true; }
                String action = args[0].toLowerCase(java.util.Locale.ROOT);
                if (action.equals("bal") || action.equals("balance")) {
                    if (core.context().itemMode()) {
                        long count = (long) core.context().currencyBalance(player);
                        sender.sendMessage("&6Balance: &e" + count + " " + core.context().currencyName());
                    } else {
                        sender.sendMessage("&6Balance: &e" + core.context().economy().format(
                                core.context().economy().balance(player.uuid())));
                    }
                }
                else if (action.equals("wheel")) core.games().wheel().open(player);
                else if (action.equals("daily")) core.games().daily().open(player);
                else if (action.equals("stats")) core.games().gui().openDetailedStats(player);
                else if (action.equals("reload")) {
                    if (!sender.hasPermission("casino.reload")) {
                        sender.sendMessage("&cYou do not have permission to execute this command.");
                        return true;
                    }
                    core.reload();
                    configureEconomy();
                    sender.sendMessage("&aCasinoRoulette configuration reloaded.");
                }
                else if (action.equals("top") || action.equals("leaderboard")) {
                    core.games().leaderboards().open(player, args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "money");
                } else if (action.equals("menu") || action.equals("open")) core.games().gui().openMainMenu(player);
                else sender.sendMessage("&eUsage: /casino [menu|balance|wheel|daily|top|stats|reload]");
                return true;
            }
            @Override public List<String> complete(CasinoSender sender, String[] args) {
                if (args.length <= 1) return Arrays.asList("menu", "balance", "wheel", "daily", "top", "stats", "reload", "setup");
                if (args.length == 2 && args[0].equalsIgnoreCase("setup")) return Arrays.asList("lang", "eco", "reset");
                if (args.length == 3 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("lang"))
                    return Arrays.asList("en", "ru", "de", "fr", "tr", "pl", "pt_br");
                if (args.length == 3 && args[0].equalsIgnoreCase("setup") && args[1].equalsIgnoreCase("eco"))
                    return Arrays.asList("vault", "item");
                return Collections.emptyList();
            }
        });
        adapter.registerCommand(new CasinoCommand() {
            @Override public String name() { return "dailywheel"; }
            @Override public String description() { return "Open the daily casino wheel"; }
            @Override public boolean execute(CasinoSender sender, String[] args) {
                if (!(sender instanceof CasinoPlayer)) { sender.sendMessage("&cThis command can only be used by a player."); return true; }
                core.games().daily().open((CasinoPlayer) sender); return true;
            }
        });
    }

    private void configureEconomy() {
        if (adapter == null || core == null || core.context() == null) return;
        ru.stepanyaa.casinoRoulette.config.YamlDocument config = core.context().config();
        adapter.configureEconomy(
                config.getString("settings.economy-mode", "VAULT"),
                config.getDouble("economy.internal.starting-balance", 0.0D),
                config.getString("economy.internal.currency-symbol", "$"));
    }

    private boolean isConfigured() {
        return core != null && core.context() != null
                && core.context().config().getBoolean("settings.is-configured", false);
    }

    private boolean handleSetup(CasinoSender sender, String[] args) {
        if (!sender.hasPermission("casino.admin")) {
            sender.sendMessage("&cYou do not have permission to run SetupWizard.");
            return true;
        }
        if (args.length == 1) { showSetup(sender, isConfigured() ? 2 : 0); return true; }
        String part = args[1].toLowerCase(java.util.Locale.ROOT);
        if (part.equals("reset")) {
            writeConfigValue("is-configured", "false");
            core.reload();
            showSetup(sender, 0);
            return true;
        }
        if (args.length < 3) { showSetup(sender, part.equals("eco") ? 1 : 0); return true; }
        if (part.equals("lang")) {
            String language = args[2].toLowerCase(java.util.Locale.ROOT);
            if (!Arrays.asList("en", "ru", "de", "fr", "tr", "pl", "pt_br").contains(language)) {
                sender.sendMessage("&cUnknown language. Use: en, ru, de, fr, tr, pl, pt_br");
                return true;
            }
            writeRootConfigValue("lang", language);
            core.reload();
            sender.sendMessage("&aLanguage selected: " + language.toUpperCase(java.util.Locale.ROOT));
            showSetup(sender, 1);
            return true;
        }
        if (part.equals("eco")) {
            String mode = args[2].toUpperCase(java.util.Locale.ROOT);
            if (!mode.equals("VAULT") && !mode.equals("ITEM")) {
                sender.sendMessage("&cUnknown economy. Use VAULT or ITEM.");
                return true;
            }
            writeConfigValue("economy-mode", mode);
            writeConfigValue("is-configured", "true");
            core.reload();
            configureEconomy();
            sender.sendMessage("&aSetupWizard completed. Economy: " + mode);
            return true;
        }
        showSetup(sender, 0);
        return true;
    }

    private void showSetup(CasinoSender sender, int step) {
        sender.sendMessage("&6=============================================");
        sender.sendMessage("&e&lCasinoRoulette SetupWizard");
        if (step <= 0) {
            sender.sendMessage("&bStep 1: choose language");
            sender.sendMessage("&7/casino setup lang <en|ru|de|fr|tr|pl|pt_br>");
        } else if (step == 1) {
            sender.sendMessage("&bStep 2: choose Sponge currency");
            sender.sendMessage("&7/casino setup eco <vault|item>");
            sender.sendMessage("&8VAULT = built-in Sponge economy, ITEM = inventory item");
        } else {
            sender.sendMessage("&aPlugin is configured. Use /casino setup reset to restart wizard.");
        }
        sender.sendMessage("&6=============================================");
    }

    private void writeConfigValue(String key, String value) {
        replaceConfig("(?m)^(\\s{6}" + java.util.regex.Pattern.quote(key) + ":\\s*).*$", "$1" + value);
    }

    private void writeRootConfigValue(String key, String value) {
        replaceConfig("(?m)^(" + java.util.regex.Pattern.quote(key) + ":\\s*).*$", "$1" + value);
    }

    private void replaceConfig(String regex, String replacement) {
        try {
            java.nio.file.Path path = new File(dataFolder, "config.yml").toPath();
            String text = new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            String updated = text.replaceFirst(regex, replacement);
            java.nio.file.Files.write(path, updated.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Throwable failure) {
            LOGGER.warning("SetupWizard could not update config.yml: " + failure);
        }
    }

    private void stop() {

        if (state != null) {
            state.shutdown();
        }
        if (core != null) {
            core.stop();
        }
        state = null;
        core = null;
        adapter = null;
    }

    public CasinoCore core() {
        return core;
    }
}
