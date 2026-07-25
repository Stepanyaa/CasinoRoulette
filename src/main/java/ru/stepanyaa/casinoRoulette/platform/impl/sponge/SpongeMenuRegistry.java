package ru.stepanyaa.casinoRoulette.platform.impl.sponge;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

final class SpongeMenuRegistry {

    private static final Map<Object, String> BY_HANDLE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SpongeMenuRegistry() {
    }

    static void remember(Object menu, Object inventory, String guiId) {
        if (guiId == null) {
            return;
        }
        if (menu != null) {
            BY_HANDLE.put(menu, guiId);
        }
        if (inventory != null) {
            BY_HANDLE.put(inventory, guiId);
        }
    }

    static String guiIdOf(Object handle) {
        if (handle == null) {
            return null;
        }
        String direct = BY_HANDLE.get(handle);
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

    static void forget(Object handle) {
        if (handle != null) {
            BY_HANDLE.remove(handle);
        }
    }

    static void clear() {
        BY_HANDLE.clear();
    }
}
