package ru.stepanyaa.casinoRoulette.platform;

import ru.stepanyaa.casinoRoulette.economy.EconomyProvider;
import ru.stepanyaa.casinoRoulette.events.EventBridge;
import ru.stepanyaa.casinoRoulette.placeholder.PlaceholderBridge;
import ru.stepanyaa.casinoRoulette.scheduler.SchedulerBackend;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public interface PlatformAdapter {

    PlatformType type();

    String serverBrand();

    String minecraftVersion();

    Logger logger();

    File dataFolder();

    InputStream resource(String path);

    boolean isPluginEnabled();

    boolean isPluginPresent(String pluginId);

    SchedulerBackend scheduler();

    EventBridge events();

    EconomyProvider economy();

    PlaceholderBridge placeholders();

    Optional<CasinoPlayer> player(UUID uuid);

    Optional<CasinoPlayer> player(String name);

    Collection<CasinoPlayer> onlinePlayers();

    CasinoSender console();

    void dispatchConsoleCommand(String command);

    void registerCommand(CasinoCommand command);

    boolean materialExists(String id);

    CasinoInventory createInventory(String title, int size, String guiId);

    Object createItem(CasinoItem item);

    void enable();

    void disable();
}
