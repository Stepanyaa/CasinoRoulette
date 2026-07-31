package ru.stepanyaa.casinoRoulette.platform.impl.sponge;

import ru.stepanyaa.casinoRoulette.compat.MaterialResolver;
import ru.stepanyaa.casinoRoulette.economy.EconomyManager;
import ru.stepanyaa.casinoRoulette.economy.EconomyProvider;
import ru.stepanyaa.casinoRoulette.events.EventBridge;
import ru.stepanyaa.casinoRoulette.placeholder.PlaceholderBridge;
import ru.stepanyaa.casinoRoulette.placeholder.PlaceholderService;
import ru.stepanyaa.casinoRoulette.platform.CasinoCommand;
import ru.stepanyaa.casinoRoulette.platform.CasinoInventory;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;
import ru.stepanyaa.casinoRoulette.platform.CasinoSender;
import ru.stepanyaa.casinoRoulette.platform.PlatformAdapter;
import ru.stepanyaa.casinoRoulette.platform.PlatformType;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;
import ru.stepanyaa.casinoRoulette.scheduler.SchedulerBackend;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class SpongeAdapter implements PlatformAdapter {

    private final Object pluginContainer;
    private final Logger logger;
    private final File dataFolder;
    private final PlaceholderService placeholderService = new PlaceholderService();

    private MaterialResolver materials;
    private SpongeEventBridge eventBridge;
    private EconomyManager economyManager;

    private final List<CasinoCommand> pendingCommands = new ArrayList<>();

    public SpongeAdapter(Object pluginContainer, Logger logger, File dataFolder) {
        this.pluginContainer = pluginContainer;
        this.logger = logger;
        this.dataFolder = dataFolder;

        SpongeAdapterHolder.install(pluginContainer);
    }

    @Override
    public PlatformType type() {
        return PlatformType.SPONGE;
    }

    @Override
    public String serverBrand() {
        try {
            Object platform = SpongeReflection.call(SpongeReflection.game(), "platform");
            return "Sponge " + SpongeReflection.call(platform, "minecraftVersion");
        } catch (Throwable unavailable) {
            return "Sponge";
        }
    }

    @Override
    public String minecraftVersion() {
        try {
            Object platform = SpongeReflection.call(SpongeReflection.game(), "platform");
            Object version = SpongeReflection.call(platform, "minecraftVersion");
            return String.valueOf(SpongeReflection.call(version, "name"));
        } catch (Throwable unavailable) {
            return "unknown";
        }
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public File dataFolder() {
        return dataFolder;
    }

    @Override
    public InputStream resource(String path) {

        return getClass().getClassLoader().getResourceAsStream(path);
    }

    @Override
    public boolean isPluginEnabled() {
        return true;
    }

    @Override
    public boolean isPluginPresent(String pluginId) {
        try {
            Object manager = SpongeReflection.call(SpongeReflection.game(), "pluginManager");
            Object found = SpongeReflection
                    .method(manager.getClass(), "plugin", String.class)
                    .invoke(manager, pluginId);
            return SpongeReflection.unwrap(found).isPresent();
        } catch (Throwable unavailable) {
            return false;
        }
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

        return PlaceholderBridge.NOOP;
    }

    public PlaceholderService placeholderService() {
        return placeholderService;
    }

    public MaterialResolver materials() {
        return materials;
    }

    public synchronized void configureEconomy(String preferred, double startingBalance,
                                              String currencySymbol) {
        EconomyManager previous = this.economyManager;
        String mode = preferred == null ? "INTERNAL" : preferred.trim().toUpperCase(java.util.Locale.ROOT);
        if (mode.equals("VAULT") || mode.equals("ITEM") || mode.equals("ITEMS")
                || mode.equals("RESOURCE")) mode = "INTERNAL";
        this.economyManager = new EconomyManager(logger, type(), dataFolder,
                mode, startingBalance, currencySymbol);
        if (previous != null) previous.shutdown();
    }

    private CasinoPlayer wrap(Object serverPlayer) {
        try {
            UUID uuid = (UUID) SpongeReflection.call(serverPlayer, "uniqueId");
            Object name = SpongeReflection.call(serverPlayer, "name");
            return new SpongeCasinoPlayer(serverPlayer, uuid, String.valueOf(name),
                    this::toItemStack);
        } catch (Throwable failure) {
            return null;
        }
    }

    @Override
    public Optional<CasinoPlayer> player(UUID uuid) {
        return SpongeReflection.onlinePlayer(uuid).map(this::wrap);
    }

    @Override
    public Optional<CasinoPlayer> player(String name) {
        return SpongeReflection.onlinePlayer(name).map(this::wrap);
    }

    @Override
    public Collection<CasinoPlayer> onlinePlayers() {
        List<CasinoPlayer> players = new ArrayList<>();
        try {
            Object server = SpongeReflection.server();
            Object online = SpongeReflection.call(server, "onlinePlayers");
            if (online instanceof Collection) {
                for (Object player : (Collection<?>) online) {
                    CasinoPlayer wrapped = wrap(player);
                    if (wrapped != null) {
                        players.add(wrapped);
                    }
                }
            }
        } catch (Throwable failure) {
            logger.warning("Could not list online players: " + failure);
        }
        return players;
    }

    @Override
    public CasinoSender console() {
        try {
            Object server = SpongeReflection.server();
            Object systemSubject = SpongeReflection.call(server, "systemSubject");
            return new SpongeCasinoSender(systemSubject, "Console", true);
        } catch (Throwable failure) {

            return new SpongeCasinoSender(null, "Console", true) {
                @Override
                public void sendMessage(String message) {
                    logger.info(message);
                }
            };
        }
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        CasinoScheduler.run(() -> {
            try {
                Object commandManager =
                        SpongeReflection.call(SpongeReflection.server(), "commandManager");
                Object cause = SpongeReflection.call(SpongeReflection.server(), "systemSubject");
                SpongeReflection.method(commandManager.getClass(), "process",
                                SpongeReflection.type("org.spongepowered.api.service.permission.Subject"),
                                SpongeReflection.type("net.kyori.adventure.audience.Audience"),
                                String.class)
                        .invoke(commandManager, cause, cause, command);
            } catch (Throwable failure) {
                logger.warning("Console command failed: " + command + " (" + failure + ")");
            }
        });
    }

    @Override
    public void registerCommand(CasinoCommand command) {
        pendingCommands.add(command);
    }

    public void flushCommands(Object registerCommandEvent) {
        for (CasinoCommand command : pendingCommands) {
            try {
                SpongeCommandRegistrar.register(registerCommandEvent, pluginContainer,
                        command, this, logger);
            } catch (Throwable failure) {
                logger.warning("Could not register /" + command.name() + ": " + failure);
            }
        }
    }

    CasinoSender senderFor(Object cause) {
        try {
            Class<?> serverPlayer = SpongeReflection
                    .type("org.spongepowered.api.entity.living.player.server.ServerPlayer");
            Object root = SpongeReflection.call(cause, "root");
            if (serverPlayer.isInstance(root)) {
                CasinoPlayer wrapped = wrap(root);
                if (wrapped != null) {
                    return wrapped;
                }
            }
            return new SpongeCasinoSender(root, String.valueOf(root), false);
        } catch (Throwable failure) {
            return console();
        }
    }

    @Override
    public boolean materialExists(String id) {
        return SpongeReflection.registryValue("ITEM_TYPE", id).isPresent();
    }

    @Override
    public CasinoInventory createInventory(String title, int size, String guiId) {
        return new SpongeCasinoInventory(guiId, title, size, logger, this::toItemStack,
                eventBridge);
    }

    @Override
    public Object createItem(CasinoItem item) {
        return toItemStack(item);
    }

    Object toItemStack(CasinoItem item) {
        try {
            String requested = materials == null
                    ? item.materialId()
                    : materials.resolve(item.materialId());

            Object itemType = SpongeReflection.registryValue("ITEM_TYPE", requested)
                    .orElseGet(() -> SpongeReflection
                            .registryValue("ITEM_TYPE", MaterialResolver.LAST_RESORT)
                            .orElse(null));
            if (itemType == null) {
                return null;
            }

            Class<?> stackClass = SpongeReflection
                    .type("org.spongepowered.api.item.inventory.ItemStack");
            Object stack = SpongeReflection.method(stackClass, "of",
                            SpongeReflection.type("org.spongepowered.api.item.ItemType"),
                            int.class)
                    .invoke(null, itemType, Math.max(1, item.amount()));

            Class<?> keys = SpongeReflection.type("org.spongepowered.api.data.Keys");
            Class<?> keyClass = SpongeReflection.type("org.spongepowered.api.data.Key");

            if (item.displayName() != null) {
                SpongeReflection.method(stack.getClass(), "offer", keyClass, Object.class)
                        .invoke(stack, keys.getField("CUSTOM_NAME").get(null),
                                SpongeReflection.component(item.displayName()));
            }

            if (!item.lore().isEmpty()) {
                List<Object> lore = new ArrayList<>(item.lore().size());
                for (String line : item.lore()) {
                    lore.add(SpongeReflection.component(line));
                }
                SpongeReflection.method(stack.getClass(), "offer", keyClass, Object.class)
                        .invoke(stack, keys.getField("LORE").get(null), lore);
            }

            if (item.glowing()) {

                SpongeReflection.method(stack.getClass(), "offer", keyClass, Object.class)
                        .invoke(stack, keys.getField("APPLIED_ENCHANTMENTS").get(null),
                                new ArrayList<>());
            }

            if (item.hideAttributes()) {
                SpongeReflection.method(stack.getClass(), "offer", keyClass, Object.class)
                        .invoke(stack, keys.getField("HIDE_ATTRIBUTES").get(null), Boolean.TRUE);
            }

            if (item.customModelData() != null) {
                SpongeReflection.method(stack.getClass(), "offer", keyClass, Object.class)
                        .invoke(stack, keys.getField("CUSTOM_MODEL_DATA").get(null),
                                item.customModelData());
            }

            return stack;
        } catch (Throwable failure) {
            logger.warning("Could not build the item \"" + item.materialId() + "\": " + failure);
            return null;
        }
    }

    @Override
    public void enable() {
        CasinoScheduler.install(new SpongeSchedulerBackend(pluginContainer, logger));

        this.materials = new MaterialResolver(this);

        this.eventBridge = new SpongeEventBridge(pluginContainer, logger, this::toItemStack);
        this.eventBridge.register();

        this.economyManager = new EconomyManager(logger, type(), dataFolder,
                "INTERNAL", 0.0D, "$");
    }

    @Override
    public void disable() {
        if (eventBridge != null) {
            eventBridge.unregister();
        }
        if (economyManager != null) {
            economyManager.shutdown();
        }
        CasinoScheduler.shutdown();
    }
}
