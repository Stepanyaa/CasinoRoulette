package ru.stepanyaa.casinoRoulette.scheduler;

public interface CasinoTask {

    void cancel();

    boolean isCancelled();

    CasinoTask NOOP = new CasinoTask() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };
}
