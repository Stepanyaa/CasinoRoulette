package ru.stepanyaa.casinoRoulette.platform.impl.sponge;

import ru.stepanyaa.casinoRoulette.platform.CasinoInventory;
import ru.stepanyaa.casinoRoulette.platform.CasinoItem;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

final class SpongeCasinoInventory implements CasinoInventory {

    private final String guiId;
    private final String title;
    private final int size;
    private final Logger logger;
    private final Function<CasinoItem, Object> itemFactory;

    private final Object menu;

    private final Object inventory;

    private final CasinoItem[] contents;
    private final SpongeEventBridge eventBridge;
    private boolean callbacksRegistered;

    SpongeCasinoInventory(String guiId, String title, int size, Logger logger,
                          Function<CasinoItem, Object> itemFactory,
                          SpongeEventBridge eventBridge) {
        this.guiId = guiId;
        this.title = title == null ? "" : title;

        int rows = Math.max(1, Math.min(6, (Math.max(9, size) + 8) / 9));
        this.size = rows * 9;
        this.logger = logger;
        this.itemFactory = itemFactory;
        this.eventBridge = eventBridge;
        this.contents = new CasinoItem[this.size];

        Object builtMenu = null;
        Object builtInventory = null;
        try {
            builtInventory = buildViewable(rows);
            if (builtInventory != null) {
                builtMenu = buildMenu(builtInventory);
            }
        } catch (Throwable failure) {
            logger.log(Level.WARNING, "Could not build the Sponge screen " + guiId
                    + ": " + failure, failure);
        }
        this.menu = builtMenu;
        this.inventory = builtInventory;

        if (builtMenu != null) {
            this.callbacksRegistered = registerCallbacks(builtMenu);
        }
        SpongeMenuRegistry.remember(builtMenu, builtInventory, guiId, this.size,
                callbacksRegistered);
    }

    private Object buildViewable(int rows) throws ReflectiveOperationException {
        Class<?> viewable = SpongeReflection
                .type("org.spongepowered.api.item.inventory.type.ViewableInventory");
        Object builder = viewable.getMethod("builder").invoke(null);

        Class<?> containerTypes = SpongeReflection
                .type("org.spongepowered.api.item.inventory.ContainerTypes");
        Object containerType = containerTypes.getField("GENERIC_9X" + rows).get(null);

        Object step = callByName(builder, "type", containerType);
        if (step == null) {
            return null;
        }
        Object completed = callByName(step, "completeStructure");
        if (completed == null) {
            return null;
        }

        Object owner = SpongeAdapterHolder.pluginContainer();
        if (owner != null) {
            Object withPlugin = callByName(completed, "plugin", owner);
            if (withPlugin != null) {
                completed = withPlugin;
            }
        }

        return callByName(completed, "build");
    }

    private Object buildMenu(Object viewableInventory) throws ReflectiveOperationException {
        Object built = callByName(viewableInventory, "asMenu");
        if (built == null) {
            return null;
        }

        Object titleComponent = SpongeReflection.component(title);
        if (titleComponent != null) {
            callByName(built, "setTitle", titleComponent);
        }

        callByName(built, "setReadOnly", Boolean.TRUE);
        return built;
    }

    private boolean registerCallbacks(Object inventoryMenu) {
        if (eventBridge == null) {
            return false;
        }
        boolean slotClicks = registerHandler(inventoryMenu, "registerSlotClick",
                "org.spongepowered.api.item.inventory.menu.handler.SlotClickHandler", 5,
                args -> {
                    eventBridge.handleMenuClick(guiId, size, args[0], args[1],
                            ((Number) args[3]).intValue(), args[4]);
                    return Boolean.FALSE;
                });

        if (!slotClicks) {
            try {
                callByName(inventoryMenu, "unregisterAll");
            } catch (Throwable ignored) {
            }
            logger.log(Level.WARNING, "Could not register Sponge menu click callbacks for "
                    + guiId + "; the global click listener will be used instead.");
            return false;
        }

        registerHandler(inventoryMenu, "registerKeySwap",
                "org.spongepowered.api.item.inventory.menu.handler.KeySwapHandler", 5,
                args -> {
                    eventBridge.handleMenuClick(guiId, size, args[0], args[1],
                            ((Number) args[3]).intValue(), args[4]);
                    return Boolean.FALSE;
                });

        registerHandler(inventoryMenu, "registerClose",
                "org.spongepowered.api.item.inventory.menu.handler.CloseHandler", 2,
                args -> {
                    eventBridge.handleMenuClose(guiId, args[0], args[1]);
                    return null;
                });

        return true;
    }

    private boolean registerHandler(Object inventoryMenu, String registerMethod,
                                    String handlerType, int expectedArguments,
                                    java.util.function.Function<Object[], Object> body) {
        try {
            Class<?> handlerClass = SpongeReflection.type(handlerType);
            ClassLoader loader = handlerClass.getClassLoader();
            if (loader == null) {
                loader = getClass().getClassLoader();
            }

            Object proxy = Proxy.newProxyInstance(loader, new Class<?>[]{handlerClass},
                    (instance, method, args) -> {
                        if (method.getName().equals("handle")
                                && args != null && args.length >= expectedArguments) {
                            Object result = body.apply(args);
                            if (result != null) {
                                return result;
                            }
                            return method.getReturnType() == void.class
                                    ? null
                                    : proxyDefault(instance, method, args);
                        }
                        return proxyDefault(instance, method, args);
                    });

            callRequired(inventoryMenu, registerMethod, handlerClass, proxy);
            return true;
        } catch (Throwable failure) {
            logger.log(Level.WARNING, "Sponge " + registerMethod + " is unavailable for "
                    + guiId + ": " + failure);
            return false;
        }
    }

    private static Object proxyDefault(Object proxy, Method method, Object[] args) {
        if (method.getName().equals("toString")) return "CasinoRouletteMenuHandler";
        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
        if (method.getName().equals("equals")) return args != null && args.length == 1 && proxy == args[0];
        if (method.getReturnType() == boolean.class) return Boolean.FALSE;
        return null;
    }

    private void callRequired(Object target, String name, Class<?> parameter, Object value)
            throws ReflectiveOperationException {
        SpongeReflection.method(target.getClass(), name, parameter).invoke(target, value);
    }

    private Object callByName(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            Object result = tryMethods(type.getMethods(), target, name, args);
            if (result != NO_MATCH) {
                return result;
            }
        }
        return null;
    }

    private static final Object NO_MATCH = new Object();

    private Object tryMethods(Method[] methods, Object target, String name, Object[] args) {
        for (Method method : methods) {
            if (!method.getName().equals(name)
                    || method.getParameterCount() != args.length) {
                continue;
            }
            try {
                try {
                    method.setAccessible(true);
                } catch (Throwable inaccessible) {
                }
                return method.invoke(target, args);
            } catch (IllegalArgumentException wrongOverload) {

            } catch (Throwable failure) {
                logger.log(Level.FINE, "Sponge call " + name + " failed on screen "
                        + guiId + ": " + failure);
                return null;
            }
        }
        return NO_MATCH;
    }

    @Override
    public String guiId() {
        return guiId;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public void setItem(int slot, CasinoItem item) {
        if (slot < 0 || slot >= size) {
            return;
        }
        contents[slot] = item;

        if (inventory == null) {
            return;
        }
        Object stack = item == null ? emptyStack() : itemFactory.apply(item);
        if (stack == null) {
            return;
        }
        writeSlot(slot, stack);
    }

    @Override
    public void clear(int slot) {
        setItem(slot, null);
    }

    @Override
    public void fillEmpty(CasinoItem item) {
        if (item == null) {
            return;
        }
        for (int slot = 0; slot < size; slot++) {
            if (contents[slot] == null) {
                setItem(slot, item);
            }
        }
    }

    @Override
    public void refresh() {
    }

    @Override
    public Object handle() {
        return menu;
    }

    private void writeSlot(int slot, Object stack) {
        for (Method method : inventory.getClass().getMethods()) {
            if (!method.getName().equals("set") || method.getParameterCount() != 2) {
                continue;
            }
            Class<?> first = method.getParameterTypes()[0];
            if (first != int.class && first != Integer.class) {
                continue;
            }
            try {
                try {
                    method.setAccessible(true);
                } catch (Throwable inaccessible) {
                }
                method.invoke(inventory, slot, stack);
                return;
            } catch (Throwable failure) {
                logger.log(Level.FINE, "Could not write slot " + slot + " of screen "
                        + guiId + ": " + failure);
                return;
            }
        }
    }

    private Object emptyStack() {
        try {
            return SpongeReflection
                    .type("org.spongepowered.api.item.inventory.ItemStack")
                    .getMethod("empty")
                    .invoke(null);
        } catch (Throwable failure) {
            return null;
        }
    }
}
