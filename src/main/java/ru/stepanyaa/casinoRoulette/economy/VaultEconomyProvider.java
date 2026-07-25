package ru.stepanyaa.casinoRoulette.economy;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

public final class VaultEconomyProvider implements EconomyProvider {

    private final Object economy;
    private final Method getBalance;
    private final Method has;
    private final Method withdrawPlayer;
    private final Method depositPlayer;
    private final Method format;
    private final Method transactionSuccess;
    private final Method getOfflinePlayer;

    private VaultEconomyProvider(Object economy,
                                 Method getBalance,
                                 Method has,
                                 Method withdrawPlayer,
                                 Method depositPlayer,
                                 Method format,
                                 Method transactionSuccess,
                                 Method getOfflinePlayer) {
        this.economy = economy;
        this.getBalance = getBalance;
        this.has = has;
        this.withdrawPlayer = withdrawPlayer;
        this.depositPlayer = depositPlayer;
        this.format = format;
        this.transactionSuccess = transactionSuccess;
        this.getOfflinePlayer = getOfflinePlayer;
    }

    public static VaultEconomyProvider tryCreate(Logger logger) {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Class<?> responseClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            Class<?> bukkitClass = Class.forName("org.bukkit.Bukkit");
            Class<?> offlinePlayerClass = Class.forName("org.bukkit.OfflinePlayer");

            Object servicesManager = bukkitClass.getMethod("getServicesManager").invoke(null);
            Object registration = servicesManager.getClass()
                    .getMethod("getRegistration", Class.class)
                    .invoke(servicesManager, economyClass);
            if (registration == null) {

                return null;
            }

            Object economy = registration.getClass()
                    .getMethod("getProvider").invoke(registration);
            if (economy == null) {
                return null;
            }

            return new VaultEconomyProvider(
                    economy,
                    economyClass.getMethod("getBalance", offlinePlayerClass),
                    economyClass.getMethod("has", offlinePlayerClass, double.class),
                    economyClass.getMethod("withdrawPlayer", offlinePlayerClass, double.class),
                    economyClass.getMethod("depositPlayer", offlinePlayerClass, double.class),
                    economyClass.getMethod("format", double.class),
                    responseClass.getMethod("transactionSuccess"),
                    bukkitClass.getMethod("getOfflinePlayer", UUID.class));
        } catch (ClassNotFoundException | NoSuchMethodException notPresent) {
            return null;
        } catch (Throwable failure) {
            logger.warning("Vault economy hook could not be created: " + failure);
            return null;
        }
    }

    @Override
    public String name() {
        return "Vault";
    }

    @Override
    public String currencyName() {
        for (String getter : new String[] {"currencyNamePlural", "currencyNameSingular"}) {
            try {
                Object value = economy.getClass().getMethod(getter).invoke(economy);
                if (value instanceof String && !((String) value).trim().isEmpty()) {
                    return ((String) value).trim();
                }
            } catch (Throwable ignored) {

            }
        }
        return null;
    }

    @Override
    public boolean isAvailable() {
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            Object servicesManager = Class.forName("org.bukkit.Bukkit")
                    .getMethod("getServicesManager").invoke(null);
            return servicesManager.getClass()
                    .getMethod("getRegistration", Class.class)
                    .invoke(servicesManager, economyClass) != null;
        } catch (Throwable gone) {
            return false;
        }
    }

    private Object offline(UUID player) throws Exception {
        return getOfflinePlayer.invoke(null, player);
    }

    @Override
    public double balance(UUID player) {
        try {
            Object value = getBalance.invoke(economy, offline(player));
            return value instanceof Number ? ((Number) value).doubleValue() : 0.0D;
        } catch (Throwable failure) {
            return 0.0D;
        }
    }

    @Override
    public boolean has(UUID player, double amount) {
        if (amount <= 0) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(has.invoke(economy, offline(player), amount));
        } catch (Throwable failure) {
            return false;
        }
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        if (amount <= 0) {
            return true;
        }
        try {
            return succeeded(withdrawPlayer.invoke(economy, offline(player), amount));
        } catch (Throwable failure) {
            return false;
        }
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        if (amount <= 0) {
            return true;
        }
        try {
            return succeeded(depositPlayer.invoke(economy, offline(player), amount));
        } catch (Throwable failure) {
            return false;
        }
    }

    private boolean succeeded(Object response) throws Exception {
        return response != null
                && Boolean.TRUE.equals(transactionSuccess.invoke(response));
    }

    @Override
    public String format(double amount) {
        try {
            Object formatted = format.invoke(economy, amount);
            return formatted == null ? String.valueOf(amount) : formatted.toString();
        } catch (Throwable failure) {
            return String.valueOf(amount);
        }
    }
}
