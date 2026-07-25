package ru.stepanyaa.casinoRoulette.placeholder;

public interface PlaceholderBridge {

    boolean register();

    void unregister();

    boolean isRegistered();

    PlaceholderBridge NOOP = new PlaceholderBridge() {
        @Override
        public boolean register() {
            return false;
        }

        @Override
        public void unregister() {
        }

        @Override
        public boolean isRegistered() {
            return false;
        }
    };
}
