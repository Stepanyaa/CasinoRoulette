package ru.stepanyaa.casinoRoulette.economy;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

public final class PlayerPointsEconomyProvider implements EconomyProvider {

    private final Object api;
    private final Method look;
    private final Method give;
    private final Method take;

    private PlayerPointsEconomyProvider(Object api, Method look, Method give, Method take) {
        this.api = api;
        this.look = look;
        this.give = give;
        this.take = take;
    }

    public static PlayerPointsEconomyProvider tryCreate(Logger logger) {
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            Object manager = bukkit.getMethod("getPluginManager").invoke(null);
            Object plugin = manager.getClass()
                    .getMethod("getPlugin", String.class)
                    .invoke(manager, "PlayerPoints");
            if (plugin == null) {
                return null;
            }
            Object enabled = plugin.getClass().getMethod("isEnabled").invoke(plugin);
            if (!Boolean.TRUE.equals(enabled)) {
                return null;
            }

            Object api = plugin.getClass().getMethod("getAPI").invoke(plugin);
            if (api == null) {
                return null;
            }

            Method look = find(api.getClass(), "look");
            Method give = find(api.getClass(), "give");
            Method take = find(api.getClass(), "take");
            if (look == null || give == null || take == null) {
                logger.warning("PlayerPoints was found but its API does not expose "
                        + "look/give/take(UUID, int). Skipping this provider.");
                return null;
            }

            return new PlayerPointsEconomyProvider(api, look, give, take);
        } catch (ClassNotFoundException | NoSuchMethodException notPresent) {
            return null;
        } catch (Throwable failure) {
            logger.warning("PlayerPoints economy hook could not be created: " + failure);
            return null;
        }
    }

    private static Method find(Class<?> type, String name) {
        Method fallback = null;
        for (Method candidate : type.getMethods()) {
            if (!candidate.getName().equals(name)) {
                continue;
            }
            Class<?>[] parameters = candidate.getParameterTypes();
            if (parameters.length == 0 || !parameters[0].equals(UUID.class)) {
                continue;
            }
            if (name.equals("look") && parameters.length == 1) {
                return candidate;
            }
            if (!name.equals("look") && parameters.length == 2
                    && (parameters[1].equals(int.class) || parameters[1].equals(Integer.class))) {
                return candidate;
            }
            fallback = fallback == null ? candidate : fallback;
        }
        return fallback;
    }

    @Override
    public String name() {
        return "PlayerPoints";
    }

    @Override
    public String currencyName() {
        return "points";
    }

    @Override
    public boolean isAvailable() {
        try {
            Object manager = Class.forName("org.bukkit.Bukkit")
                    .getMethod("getPluginManager").invoke(null);
            Object plugin = manager.getClass()
                    .getMethod("getPlugin", String.class)
                    .invoke(manager, "PlayerPoints");
            return plugin != null
                    && Boolean.TRUE.equals(plugin.getClass().getMethod("isEnabled").invoke(plugin));
        } catch (Throwable gone) {
            return false;
        }
    }

    @Override
    public double balance(UUID player) {
        try {
            Object value = look.invoke(api, player);
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
        return balance(player) >= charge(amount);
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        if (amount <= 0) {
            return true;
        }
        int points = charge(amount);

        try {
            return Boolean.TRUE.equals(take.invoke(api, player, points));
        } catch (Throwable failure) {
            return false;
        }
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        if (amount <= 0) {
            return true;
        }
        int points = payout(amount);
        if (points <= 0) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(give.invoke(api, player, points));
        } catch (Throwable failure) {
            return false;
        }
    }

    private int charge(double amount) {
        double rounded = Math.ceil(amount - 1.0E-9D);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0.0D, rounded));
    }

    private int payout(double amount) {
        double rounded = Math.floor(amount + 1.0E-9D);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0.0D, rounded));
    }

    @Override
    public String format(double amount) {
        return charge(amount) + " points";
    }
}
