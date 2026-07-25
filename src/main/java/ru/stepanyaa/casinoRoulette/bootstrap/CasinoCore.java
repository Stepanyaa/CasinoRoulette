package ru.stepanyaa.casinoRoulette.bootstrap;

import ru.stepanyaa.casinoRoulette.config.ConfigService;
import ru.stepanyaa.casinoRoulette.config.YamlDocument;
import ru.stepanyaa.casinoRoulette.game.ChipsBank;
import ru.stepanyaa.casinoRoulette.game.GameContext;
import ru.stepanyaa.casinoRoulette.game.GameHub;
import ru.stepanyaa.casinoRoulette.game.Messages;
import ru.stepanyaa.casinoRoulette.game.RouletteHost;
import ru.stepanyaa.casinoRoulette.game.StatsStore;
import ru.stepanyaa.casinoRoulette.placeholder.CasinoPlaceholderRegistrar;
import ru.stepanyaa.casinoRoulette.placeholder.PlaceholderService;
import ru.stepanyaa.casinoRoulette.platform.PlatformAdapter;
import ru.stepanyaa.casinoRoulette.platform.Platforms;

import java.io.File;
import java.util.logging.Logger;

public final class CasinoCore {

    private final PlatformAdapter adapter;
    private final Logger logger;
    private volatile boolean started;

    private ConfigService configs;
    private Messages messages;
    private GameContext context;
    private GameHub games;

    public CasinoCore(PlatformAdapter adapter) {
        this.adapter = adapter;
        this.logger = adapter.logger();
    }

    public PlatformAdapter adapter() {
        return adapter;
    }

    public boolean isStarted() {
        return started;
    }

    public void start(PlaceholderService placeholderService,
                      CasinoPlaceholderRegistrar.StatsSource stats) {
        if (started) {
            return;
        }

        Platforms.install(adapter);
        adapter.enable();

        if (placeholderService != null && stats != null) {
            CasinoPlaceholderRegistrar.registerAll(placeholderService, stats);
            logger.info("Registered " + placeholderService.identifiers().size()
                    + " placeholder ids.");
        }

        started = true;
    }

    public void stop() {
        if (!started) {
            return;
        }
        started = false;
        try {
            adapter.disable();
        } catch (Throwable failure) {
            logger.warning("Shutdown did not complete cleanly: " + failure);
        } finally {
            Platforms.uninstall();
        }
    }

    public void reload() {
        if (context != null) {
            context.reload();
            messages.update(loadLanguage(context.config()));
        }
        if (games != null) {
            games.reload();
        }
        logger.info("Configuration reloaded.");
    }

    public GameHub startGames(ChipsBank chips, StatsStore stats, RouletteHost host) {
        if (games != null) {
            return games;
        }

        this.configs = new ConfigService(adapter.dataFolder(), adapter::resource, logger);
        for (String file : new String[]{GameContext.CONFIG, GameContext.WHEEL,
                GameContext.DAILY_WHEEL, GameContext.LEADERBOARD, GameContext.MENUS}) {
            configs.install(file);
        }

        YamlDocument config = YamlDocument.load(configs.file(GameContext.CONFIG));
        this.messages = new Messages(loadLanguage(config));
        this.context = new GameContext(adapter, configs, messages, chips, stats);
        this.games = new GameHub(context, host);
        this.games.start();
        return games;
    }

    private YamlDocument loadLanguage(YamlDocument config) {
        String language = config.getString("lang", "en").trim().toLowerCase();
        if (language.isEmpty()) {
            language = "en";
        }
        File file = installLanguage(language);
        if (file == null || !file.isFile()) {
            if (!"en".equals(language)) {
                logger.warning("Unknown language '" + language + "' in config.yml, using English.");
            }
            file = installLanguage("en");
        }
        return file == null ? YamlDocument.empty() : YamlDocument.load(file);
    }

    private File installLanguage(String language) {
        try {
            return configs.install("lang/messages_" + language + ".yml");
        } catch (Throwable failure) {
            logger.warning("Could not install the language file for '" + language + "': " + failure);
            return null;
        }
    }

    public GameHub games() {
        return games;
    }

    public GameContext context() {
        return context;
    }
}
