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
        try {
            Object player = SpongeReflection.call(event, "player");
            return SpongeReflection.unwrap(player).orElse(null);
        } catch (Throwable notPlayerEvent) {
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
            }
        } catch (Throwable failure) {
            logger.warning("Quit event translation failed: " + failure);
        }
    }

    private void handleClick(Object event) {
        try {
            Object container = SpongeReflection.call(event, "container");
            String guiId = SpongeMenuRegistry.guiIdOf(container);
            if (guiId == null) {
                return;
            }

            Object player = playerOf(event);
            if (player == null) {
                return;
            }

            int slot = slotIndexOf(event);
            CasinoEvents.ClickType clickType = translateClick(event);

            CasinoEvents.InventoryClick shared = new CasinoEvents.InventoryClick(
                    wrap(player), guiId, slot, clickType, true);
            shared.setCancelled(true);
            post(shared);

            setCancelled(event, shared.isCancelled());
        } catch (Throwable failure) {
            logger.warning("Click event translation failed: " + failure);
        }
    }

    private int slotIndexOf(Object event) {
        try {
            java.util.Optional<Object> resolved = SpongeReflection.unwrap(SpongeReflection.call(event, "slot"));
            if (!resolved.isPresent()) return -1;
            Object slot = resolved.get();
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
        } catch (Throwable ignored) { }
        return -1;
    }

    private CasinoEvents.ClickType translateClick(Object event) {
        String name = event.getClass().getName();
        if (name.contains("Shift")) {
            return name.contains("Secondary")
                    ? CasinoEvents.ClickType.SHIFT_RIGHT
                    : CasinoEvents.ClickType.SHIFT_LEFT;
        }
        if (name.contains("Secondary")) {
            return CasinoEvents.ClickType.RIGHT;
        }
        if (name.contains("Middle")) {
            return CasinoEvents.ClickType.MIDDLE;
        }
        if (name.contains("NumberPress")) {
            return CasinoEvents.ClickType.NUMBER_KEY;
        }
        if (name.contains("Double")) {
            return CasinoEvents.ClickType.DOUBLE_CLICK;
        }
        if (name.contains("Drop")) {
            return CasinoEvents.ClickType.DROP;
        }
        if (name.contains("Primary")) {
            return CasinoEvents.ClickType.LEFT;
        }
        return CasinoEvents.ClickType.OTHER;
    }

    private void handleClose(Object event) {
        try {
            Object container = SpongeReflection.call(event, "container");
            String guiId = SpongeMenuRegistry.guiIdOf(container);
            if (guiId == null) {
                return;
            }
            Object player = playerOf(event);
            if (player != null) {
                post(new CasinoEvents.InventoryClose(wrap(player), guiId));
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

    private boolean isServerPlayer(Object entity) {
        try {
            return SpongeReflection
                    .type("org.spongepowered.api.entity.living.player.server.ServerPlayer")
                    .isInstance(entity);
        } catch (Throwable unavailable) {
            return false;
        }
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
