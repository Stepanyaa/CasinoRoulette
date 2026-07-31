package ru.stepanyaa.casinoRoulette.platform.impl.sponge;

import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.EventListener;
import org.spongepowered.api.event.EventListenerRegistration;
import org.spongepowered.api.event.EventManager;
import org.spongepowered.api.event.Order;
import org.spongepowered.plugin.PluginContainer;
import ru.stepanyaa.casinoRoulette.events.CasinoEvents;
import ru.stepanyaa.casinoRoulette.events.EventBridge;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;
import ru.stepanyaa.casinoRoulette.platform.CasinoPlayer;

import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

final class SpongeEventBridge extends EventBridge {

    private final Object pluginContainer;
    private final Logger logger;
    private final Function<CasinoItem, Object> itemFactory;

    private final java.util.List<Object[]> registered = new java.util.ArrayList<>();

    SpongeEventBridge(Object pluginContainer, Logger logger,
                      Function<CasinoItem, Object> itemFactory) {
        this.pluginContainer = pluginContainer;
        this.logger = logger;
        this.itemFactory = itemFactory;
    }

    @Override
    protected void logHandlerFailure(Object event, Throwable throwable) {
        logger.warning("Handler for " + event.getClass().getSimpleName()
                + " failed: " + throwable);
    }

    @Override
    public void register() {
        listen("org.spongepowered.api.event.network.ServerSideConnectionEvent$Join",
                this::handleJoin);
        listen("org.spongepowered.api.event.network.ServerSideConnectionEvent$Disconnect",
                this::handleQuit);
        listen("org.spongepowered.api.event.item.inventory.container.ClickContainerEvent",
                this::handleClick);
        listen("org.spongepowered.api.event.item.inventory.container.InteractContainerEvent$Close",
                this::handleClose);
        listen("org.spongepowered.api.event.entity.MoveEntityEvent",
                this::handleMove);
        listen("org.spongepowered.api.event.entity.DamageEntityEvent",
                this::handleDamage);
        listen("org.spongepowered.api.event.block.InteractBlockEvent$Secondary",
                this::handleInteractBlock);
        listen("org.spongepowered.api.event.entity.InteractEntityEvent$Secondary",
                this::handleInteractEntity);
    }

    @SuppressWarnings("unchecked")
    private void listen(String eventClassName, final java.util.function.Consumer<Object> handler) {
        try {
            Class<? extends Event> eventClass =
                    (Class<? extends Event>) SpongeReflection.type(eventClassName);
            EventListener<Event> listener = event -> {
                try {
                    handler.accept(event);
                } catch (Throwable failure) {
                    logger.warning("Sponge listener failed: " + failure);
                }
            };

            EventListenerRegistration<Event> registration = EventListenerRegistration
                    .builder((Class<Event>) eventClass)
                    .plugin((PluginContainer) pluginContainer)
                    .order(Order.DEFAULT)
                    .listener(listener)
                    .build();
            EventManager eventManager = (EventManager) SpongeReflection.call(SpongeReflection.game(), "eventManager");
            eventManager.registerListener(registration);
            registered.add(new Object[]{listener});
        } catch (Throwable failure) {

            logger.warning("Could not register a listener for " + eventClassName
                    + ": " + failure);
        }
    }

    @Override
    public void unregister() {
        try {
            Object eventManager = SpongeReflection.call(SpongeReflection.game(), "eventManager");
            for (Object[] entry : registered) {
                try {
                    SpongeReflection.method(eventManager.getClass(), "unregisterListeners",
                                    Object.class)
                            .invoke(eventManager, entry[0]);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        registered.clear();
        SpongeMenuRegistry.clear();
        super.unregister();
    }

    private CasinoPlayer wrap(Object serverPlayer) throws ReflectiveOperationException {
        UUID uuid = (UUID) SpongeReflection.call(serverPlayer, "uniqueId");
        Object name = SpongeReflection.call(serverPlayer, "name");
        return new SpongeCasinoPlayer(serverPlayer, uuid, String.valueOf(name), itemFactory);
    }

    private Object playerOf(Object event) {
        for (String getter : new String[] {"player", "serverPlayer", "entity"}) {
            try {
                Object player = SpongeReflection.call(event, getter);
                Object unwrapped = SpongeReflection.unwrap(player).orElse(player);
                if (isServerPlayer(unwrapped)) {
                    return unwrapped;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Object fromCause = playerFromCause(SpongeReflection.call(event, "cause"));
            if (fromCause != null) {
                return fromCause;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object profile = SpongeReflection.call(event, "profile");
            Object unwrapped = SpongeReflection.unwrap(profile).orElse(profile);
            if (unwrapped != null) {
                Object uuid = SpongeReflection.call(unwrapped, "uniqueId");
                if (uuid instanceof UUID) {
                    return SpongeReflection.onlinePlayer((UUID) uuid).orElse(null);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean isServerPlayer(Object value) {
        if (value == null) {
            return false;
        }
        try {
            Class<?> playerType = SpongeReflection.type(
                    "org.spongepowered.api.entity.living.player.server.ServerPlayer");
            return playerType.isInstance(value);
        } catch (Throwable failure) {
            String name = value.getClass().getName();
            return name.contains("ServerPlayer") || name.endsWith("EntityPlayerMP");
        }
    }

    private Object playerFromCause(Object cause) {
        if (cause == null) {
            return null;
        }
        try {
            Class<?> playerType = SpongeReflection.type(
                    "org.spongepowered.api.entity.living.player.server.ServerPlayer");
            Object found = SpongeReflection.method(cause.getClass(), "first", Class.class)
                    .invoke(cause, playerType);
            java.util.Optional<Object> player = SpongeReflection.unwrap(found);
            if (player.isPresent()) {
                return player.get();
            }
            Object root = SpongeReflection.call(cause, "root");
            return playerType.isInstance(root) ? root : null;
        } catch (Throwable failure) {
            return null;
        }
    }

    private void handleJoin(Object event) {
        try {
            Object player = playerOf(event);
            if (player != null) {
                post(new CasinoEvents.PlayerJoin(wrap(player)));
            }
        } catch (Throwable failure) {
            logger.warning("Join event translation failed: " + failure);
        }
    }

    private void handleQuit(Object event) {
        try {
            Object player = playerOf(event);
            if (player != null) {
                post(new CasinoEvents.PlayerQuit(wrap(player)));
                return;
            }
            UUID uuid = uuidOf(event);
            if (uuid != null) {
                post(new CasinoEvents.PlayerQuit(
                        new SpongeCasinoPlayer(null, uuid, uuid.toString().substring(0, 8), itemFactory)));
            }
        } catch (Throwable failure) {
            logger.warning("Quit event translation failed: " + failure);
        }
    }

    private UUID uuidOf(Object event) {
        for (String getter : new String[] {"profile", "player", "serverPlayer"}) {
            try {
                Object value = SpongeReflection.call(event, getter);
                Object unwrapped = SpongeReflection.unwrap(value).orElse(value);
                if (unwrapped == null) {
                    continue;
                }
                Object uuid = SpongeReflection.call(unwrapped, "uniqueId");
                if (uuid instanceof UUID) {
                    return (UUID) uuid;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            Object cause = SpongeReflection.call(event, "cause");
            Object player = playerFromCause(cause);
            if (player != null) {
                Object uuid = SpongeReflection.call(player, "uniqueId");
                if (uuid instanceof UUID) {
                    return (UUID) uuid;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void handleClick(Object event) {
        try {
            Object container = SpongeReflection.call(event, "container");
            SpongeMenuRegistry.MenuInfo info = SpongeMenuRegistry.infoOf(container);
            if (info == null || info.callbacks) {
                return;
            }

            Object player = playerOf(event);
            if (player == null) {
                return;
            }

            int slot = slotIndexOf(event, container);
            CasinoEvents.ClickType clickType = translateClick(event);

            CasinoEvents.InventoryClick shared = new CasinoEvents.InventoryClick(
                    wrap(player), info.guiId, slot, clickType,
                    slot >= 0 && slot < info.topSize);
            shared.setCancelled(true);
            post(shared);

            setCancelled(event, shared.isCancelled());
        } catch (Throwable failure) {
            logger.warning("Click event translation failed: " + failure);
        }
    }

    private int slotIndexOf(Object event, Object container) {
        try {
            java.util.Optional<Object> resolved = SpongeReflection.unwrap(SpongeReflection.call(event, "slot"));
            if (!resolved.isPresent()) return -1;
            Object slot = resolved.get();

            Integer keyed = slotIndexFromKey(slot, container);
            if (keyed != null) return keyed;

            for (String name : new String[]{"ordinal", "index", "getOrdinal", "getIndex"}) {
                try {
                    Object value = SpongeReflection.call(slot, name);
                    if (value instanceof Number) return ((Number) value).intValue();
                } catch (Throwable ignored) { }
            }
            for (String name : new String[]{"ordinal", "index"}) {
                try {
                    java.lang.reflect.Field field = slot.getClass().getDeclaredField(name);
                    field.setAccessible(true);
                    Object value = field.get(slot);
                    if (value instanceof Number) return ((Number) value).intValue();
                } catch (Throwable ignored) { }
            }
            try {
                Object slots = SpongeReflection.call(container, "slots");
                if (slots instanceof java.util.List) {
                    java.util.List<?> list = (java.util.List<?>) slots;
                    int direct = list.indexOf(slot);
                    if (direct >= 0) return direct;
                    try {
                        Object viewed = SpongeReflection.call(slot, "viewedSlot");
                        direct = list.indexOf(viewed);
                        if (direct >= 0) return direct;
                    } catch (Throwable ignored) { }
                }
            } catch (Throwable ignored) { }
        } catch (Throwable ignored) { }
        return -1;
    }

    private Integer slotIndexFromKey(Object slot, Object container) {
        Integer inContainer = slotIndexKey(slot);
        if (inContainer != null) {
            return inContainer;
        }
        try {
            Object viewed = SpongeReflection.unwrap(SpongeReflection.call(slot, "viewedSlot"))
                    .orElse(null);
            if (viewed != null && viewed != slot) {
                return slotIndexKey(viewed);
            }
        } catch (Throwable notAViewedSlot) {
        }
        return null;
    }

    private Integer slotIndexKey(Object slot) {
        try {
            Class<?> keys = SpongeReflection.type("org.spongepowered.api.data.Keys");
            Object slotIndexKey = keys.getField("SLOT_INDEX").get(null);
            Object value = SpongeReflection.method(slot.getClass(), "get",
                            SpongeReflection.type("org.spongepowered.api.data.Key"))
                    .invoke(slot, slotIndexKey);
            Object index = SpongeReflection.unwrap(value).orElse(null);
            if (index instanceof Number) {
                return ((Number) index).intValue();
            }
        } catch (Throwable unavailable) {
        }
        return null;
    }

    void handleMenuClick(String guiId, int topSize, Object cause, Object container,
                         int slot, Object spongeClickType) {
        try {
            Object player = playerFromCause(cause);
            if (player == null) {
                return;
            }
            SpongeMenuRegistry.rememberContainer(container, guiId, topSize, true);
            CasinoEvents.InventoryClick shared = new CasinoEvents.InventoryClick(
                    wrap(player), guiId, slot, translateMenuClick(spongeClickType),
                    slot >= 0 && slot < topSize);
            shared.setCancelled(true);
            post(shared);
        } catch (Throwable failure) {
            logger.warning("Menu click translation failed: " + failure);
        }
    }

    void handleMenuClose(String guiId, Object cause, Object container) {
        try {
            Object player = playerFromCause(cause);
            if (player != null) {
                post(new CasinoEvents.InventoryClose(wrap(player), guiId));
            }
        } catch (Throwable failure) {
            logger.warning("Menu close translation failed: " + failure);
        } finally {
            SpongeMenuRegistry.forget(container);
        }
    }

    private CasinoEvents.ClickType translateMenuClick(Object clickType) {
        return classifyClick(registryName(clickType) + ' ' + SpongeReflection.typeNames(clickType));
    }

    private String registryName(Object value) {
        if (value == null) return "";
        String key = SpongeReflection.registryValueKey("CLICK_TYPE", value);
        if (!key.isEmpty()) {
            return key;
        }
        for (String method : new String[]{"key", "location"}) {
            try {
                return String.valueOf(SpongeReflection.call(value, method));
            } catch (Throwable ignored) { }
        }
        return String.valueOf(value);
    }

    private CasinoEvents.ClickType translateClick(Object event) {
        return classifyClick(SpongeReflection.typeNames(event));
    }

    private CasinoEvents.ClickType classifyClick(String typeNames) {
        String name = typeNames == null
                ? ""
                : typeNames.toUpperCase(java.util.Locale.ROOT);

        boolean right = name.contains("RIGHT") || name.contains("SECONDARY");
        boolean left = name.contains("LEFT") || name.contains("PRIMARY");

        if (name.contains("SHIFT")) {
            return right ? CasinoEvents.ClickType.SHIFT_RIGHT : CasinoEvents.ClickType.SHIFT_LEFT;
        }
        if (name.contains("KEY_SWAP") || name.contains("KEYSWAP") || name.contains("NUMBER")) {
            return CasinoEvents.ClickType.NUMBER_KEY;
        }
        if (name.contains("DOUBLE")) {
            return CasinoEvents.ClickType.DOUBLE_CLICK;
        }
        if (name.contains("THROW") || name.contains("DROP")) {
            return CasinoEvents.ClickType.DROP;
        }
        if (name.contains("MIDDLE")) {
            return CasinoEvents.ClickType.MIDDLE;
        }
        if (right) {
            return CasinoEvents.ClickType.RIGHT;
        }
        if (left) {
            return CasinoEvents.ClickType.LEFT;
        }
        return CasinoEvents.ClickType.OTHER;
    }

    private void handleClose(Object event) {
        try {
            Object container = SpongeReflection.call(event, "container");
            SpongeMenuRegistry.MenuInfo info = SpongeMenuRegistry.infoOf(container);
            if (info == null || info.callbacks) {
                return;
            }
            Object player = playerOf(event);
            if (player != null) {
                post(new CasinoEvents.InventoryClose(wrap(player), info.guiId));
            }
        } catch (Throwable failure) {
            logger.warning("Close event translation failed: " + failure);
        }
    }

    private void handleMove(Object event) {
        if (!hasSubscribers(CasinoEvents.PlayerMove.class)) {
            return;
        }
        try {
            Object entity = SpongeReflection.call(event, "entity");
            if (!isServerPlayer(entity)) {
                return;
            }
            Object from = SpongeReflection.call(event, "originalPosition");
            Object to = SpongeReflection.call(event, "destinationPosition");

            double fromX = component(from, "x");
            double fromY = component(from, "y");
            double fromZ = component(from, "z");
            double toX = component(to, "x");
            double toY = component(to, "y");
            double toZ = component(to, "z");

            if ((int) Math.floor(fromX) == (int) Math.floor(toX)
                    && (int) Math.floor(fromY) == (int) Math.floor(toY)
                    && (int) Math.floor(fromZ) == (int) Math.floor(toZ)) {
                return;
            }

            CasinoEvents.PlayerMove shared = new CasinoEvents.PlayerMove(
                    wrap(entity), fromX, fromY, fromZ, toX, toY, toZ);
            post(shared);
            if (shared.isCancelled()) {
                setCancelled(event, true);
            }
        } catch (Throwable failure) {
            logger.warning("Move event translation failed: " + failure);
        }
    }

    private static double component(Object vector, String axis) throws ReflectiveOperationException {
        Object value = SpongeReflection.call(vector, axis);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
    }

    private void handleInteractBlock(Object event) {
        if (!hasSubscribers(CasinoEvents.PlayerInteract.class)) {
            return;
        }
        try {
            Object player = playerOf(event);
            if (player == null) {
                return;
            }
            Object block = SpongeReflection.call(event, "block");
            CasinoEvents.PlayerInteract shared =
                    new CasinoEvents.PlayerInteract(wrap(player), block, true);
            post(shared);
            if (shared.isCancelled()) {
                setCancelled(event, true);
            }
        } catch (Throwable failure) {
            logger.warning("Interact event translation failed: " + failure);
        }
    }

    private void handleInteractEntity(Object event) {
        if (!hasSubscribers(CasinoEvents.PlayerInteract.class)) {
            return;
        }
        try {
            Object player = playerOf(event);
            if (player == null) {
                return;
            }
            Object target = SpongeReflection.call(event, "entity");
            CasinoEvents.PlayerInteract shared =
                    new CasinoEvents.PlayerInteract(wrap(player), target, true);
            post(shared);
            if (shared.isCancelled()) {
                setCancelled(event, true);
            }
        } catch (Throwable failure) {
            logger.warning("Interact entity translation failed: " + failure);
        }
    }

    private void handleDamage(Object event) {
        if (!hasSubscribers(CasinoEvents.EntityDamage.class)) {
            return;
        }
        try {
            Object entity = SpongeReflection.call(event, "entity");
            Object amount = SpongeReflection.call(event, "originalDamage");
            double damage = amount instanceof Number ? ((Number) amount).doubleValue() : 0.0D;

            CasinoEvents.EntityDamage shared = new CasinoEvents.EntityDamage(entity, damage);
            post(shared);
            if (shared.isCancelled()) {
                setCancelled(event, true);
            }
        } catch (Throwable failure) {
            logger.warning("Damage event translation failed: " + failure);
        }
    }

    private void setCancelled(Object event, boolean cancelled) {
        try {
            Class<?> cancellable = SpongeReflection.type("org.spongepowered.api.event.Cancellable");
            if (cancellable.isInstance(event)) {
                SpongeReflection.method(cancellable, "setCancelled", boolean.class)
                        .invoke(event, cancelled);
            }
        } catch (Throwable ignored) {
        }
    }
}
