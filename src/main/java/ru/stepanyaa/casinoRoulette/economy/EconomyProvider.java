package ru.stepanyaa.casinoRoulette.economy;

import java.util.UUID;
import java.util.Collections;
import java.util.Map;

public interface EconomyProvider {

    String name();

    boolean isAvailable();

    double balance(UUID player);

    boolean has(UUID player, double amount);

    boolean withdraw(UUID player, double amount);

    boolean deposit(UUID player, double amount);

    default boolean setBalance(UUID player, double amount) {
        double current = balance(player);
        return amount >= current ? deposit(player, amount - current) : withdraw(player, current - amount);
    }

    default Map<UUID, Double> balances() { return Collections.emptyMap(); }

    String format(double amount);

    default String currencyName() {
        return null;
    }

    default void shutdown() {
    }
}
