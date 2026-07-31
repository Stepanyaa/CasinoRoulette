package ru.stepanyaa.casinoRoulette.platform.impl.sponge;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class SpongeMenuRegistry {

    private static final Map<Object, MenuInfo> BY_HANDLE =
            Collections.synchronizedMap(new WeakHashMap<>());

    static final class MenuInfo {
        final String guiId;
        final int topSize;
        final boolean callbacks;

        MenuInfo(String guiId, int topSize, boolean callbacks) {
            this.guiId = guiId;
            this.topSize = topSize;
            this.callbacks = callbacks;
        }
    }

    private SpongeMenuRegistry() {
    }

    static void remember(Object menu, Object inventory, String guiId, int topSize,
                         boolean callbacks) {
        if (guiId == null) {
            return;
        }
        MenuInfo info = new MenuInfo(guiId, topSize, callbacks);
        if (menu != null) {
            BY_HANDLE.put(menu, info);
        }
        if (inventory != null) {
            BY_HANDLE.put(inventory, info);
        }
    }

    static void rememberContainer(Object container, String guiId, int topSize,
                                  boolean callbacks) {
        if (container != null && guiId != null) {
            BY_HANDLE.put(container, new MenuInfo(guiId, topSize, callbacks));
        }
    }

    static MenuInfo infoOf(Object handle) {
        if (handle == null) {
            return null;
        }
        MenuInfo direct = BY_HANDLE.get(handle);
        if (direct != null) {
            return direct;
        }

        try {
            Object menu = SpongeReflection.call(handle, "currentMenu");
            java.util.Optional<Object> resolved = SpongeReflection.unwrap(menu);
            if (resolved.isPresent()) {
                return BY_HANDLE.get(resolved.get());
            }
        } catch (Throwable notAContainer) {
        }
        return null;
    }

    static String guiIdOf(Object handle) {
        if (handle == null) {
            return null;
        }
        MenuInfo info = infoOf(handle);
        return info == null ? null : info.guiId;
    }

    static void forget(Object handle) {
        if (handle != null) {
            BY_HANDLE.remove(handle);
        }
    }

    static void clear() {
        BY_HANDLE.clear();
    }
}
