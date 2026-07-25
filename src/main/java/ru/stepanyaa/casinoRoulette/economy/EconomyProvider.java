package ru.stepanyaa.casinoRoulette.economy;

import java.util.UUID;

public interface EconomyProvider {

    String name();

    boolean isAvailable();

    double balance(UUID player);

    boolean has(UUID player, double amount);

    boolean withdraw(UUID player, double amount);

    boolean deposit(UUID player, double amount);

    String format(double amount);

    default String currencyName() {
        return null;
    }

    default void shutdown() {
    }
}
