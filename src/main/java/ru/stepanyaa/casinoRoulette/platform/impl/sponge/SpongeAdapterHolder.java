package ru.stepanyaa.casinoRoulette.platform.impl.sponge;

final class SpongeAdapterHolder {

    private static volatile Object pluginContainer;

    private SpongeAdapterHolder() {
    }

    static void install(Object container) {
        pluginContainer = container;
    }

    static Object pluginContainer() {
        return pluginContainer;
    }

    static void clear() {
        pluginContainer = null;
    }
}
