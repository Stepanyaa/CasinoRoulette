package ru.stepanyaa.casinoRoulette.platform;

public final class Platforms {

    private static volatile PlatformAdapter adapter;

    private Platforms() {
    }

    public static void install(PlatformAdapter platformAdapter) {
        adapter = platformAdapter;
    }

    public static PlatformAdapter get() {
        PlatformAdapter current = adapter;
        if (current == null) {
            throw new IllegalStateException(
                    "Platform adapter accessed before bootstrap completed.");
        }
        return current;
    }

    public static boolean isReady() {
        return adapter != null;
    }

    public static PlatformType type() {
        return isReady() ? adapter.type() : PlatformType.UNKNOWN;
    }

    public static boolean isFolia() {
        return type() == PlatformType.FOLIA;
    }

    public static void uninstall() {
        adapter = null;
    }
}
