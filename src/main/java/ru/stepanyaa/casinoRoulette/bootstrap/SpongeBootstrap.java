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
            state.attach(core.context(), core.startGames(state, state, state));
            registerSpongeCommands();
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
                if (!(sender instanceof CasinoPlayer)) {
                    sender.sendMessage("&cThis command can only be used by a player.");
                    return true;
                }
                CasinoPlayer player = (CasinoPlayer) sender;
                if (args.length == 0) { core.games().gui().openMainMenu(player); return true; }
                String action = args[0].toLowerCase(java.util.Locale.ROOT);
                if (action.equals("wheel")) core.games().wheel().open(player);
                else if (action.equals("daily")) core.games().daily().open(player);
                else if (action.equals("top") || action.equals("leaderboard")) {
                    core.games().leaderboards().open(player, args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "money");
                } else if (action.equals("menu") || action.equals("open")) core.games().gui().openMainMenu(player);
                else sender.sendMessage("&eUsage: /casino [menu|wheel|daily|top]");
                return true;
            }
            @Override public List<String> complete(CasinoSender sender, String[] args) {
                return args.length <= 1 ? Arrays.asList("menu", "wheel", "daily", "top") : Collections.emptyList();
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
