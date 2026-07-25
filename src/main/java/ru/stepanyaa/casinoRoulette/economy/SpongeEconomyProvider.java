package ru.stepanyaa.casinoRoulette.economy;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class SpongeEconomyProvider implements EconomyProvider {

    private final Logger logger;
    private final Object economyService;

    private final Method getOrCreateAccount;
    private final Method accountBalance;
    private final Method accountWithdraw;
    private final Method accountDeposit;
    private final Method defaultCurrency;
    private final Method resultType;

    private SpongeEconomyProvider(Logger logger, Object economyService, Method getOrCreateAccount,
                                  Method accountBalance, Method accountWithdraw,
                                  Method accountDeposit, Method defaultCurrency,
                                  Method resultType) {
        this.logger = logger;
        this.economyService = economyService;
        this.getOrCreateAccount = getOrCreateAccount;
        this.accountBalance = accountBalance;
        this.accountWithdraw = accountWithdraw;
        this.accountDeposit = accountDeposit;
        this.defaultCurrency = defaultCurrency;
        this.resultType = resultType;
    }

    public static SpongeEconomyProvider tryCreate(Logger logger) {
        try {
            Class<?> spongeClass = Class.forName("org.spongepowered.api.Sponge");
            Class<?> economyServiceClass =
                    Class.forName("org.spongepowered.api.service.economy.EconomyService");

            Object serviceProvider = spongeClass.getMethod("serviceProvider").invoke(null);
            Object optional = serviceProvider.getClass()
                    .getMethod("provide", Class.class)
                    .invoke(serviceProvider, economyServiceClass);

            if (!(optional instanceof Optional) || !((Optional<?>) optional).isPresent()) {
                return null;
            }
            Object economyService = ((Optional<?>) optional).get();

            Class<?> accountClass =
                    Class.forName("org.spongepowered.api.service.economy.account.Account");
            Class<?> currencyClass =
                    Class.forName("org.spongepowered.api.service.economy.Currency");
            Class<?> transactionResultClass = Class.forName(
                    "org.spongepowered.api.service.economy.transaction.TransactionResult");

            Method getOrCreate =
                    economyServiceClass.getMethod("findOrCreateAccount", UUID.class);
            Method balance = accountClass.getMethod("balance", currencyClass);
            Method withdraw =
                    accountClass.getMethod("withdraw", currencyClass, BigDecimal.class);
            Method deposit =
                    accountClass.getMethod("deposit", currencyClass, BigDecimal.class);
            Method currency = economyServiceClass.getMethod("defaultCurrency");
            Method result = transactionResultClass.getMethod("result");

            logger.info("Economy: Sponge EconomyService detected.");
            return new SpongeEconomyProvider(logger, economyService, getOrCreate, balance,
                    withdraw, deposit, currency, result);
        } catch (ClassNotFoundException notFound) {
            return null;
        } catch (Throwable throwable) {
            logger.warning("Sponge EconomyService present but could not be hooked: " + throwable);
            return null;
        }
    }

    @Override
    public String name() {
        return "Sponge EconomyService";
    }

    @Override
    public boolean isAvailable() {
        return economyService != null;
    }

    private Object account(UUID player) throws ReflectiveOperationException {
        Object optional = getOrCreateAccount.invoke(economyService, player);
        if (optional instanceof Optional && ((Optional<?>) optional).isPresent()) {
            return ((Optional<?>) optional).get();
        }
        return null;
    }

    private Object currency() throws ReflectiveOperationException {
        return defaultCurrency.invoke(economyService);
    }

    private boolean succeeded(Object transactionResult) throws ReflectiveOperationException {
        if (transactionResult == null) {
            return false;
        }
        Object type = resultType.invoke(transactionResult);
        return type != null && "SUCCESS".equalsIgnoreCase(String.valueOf(type));
    }

    @Override
    public double balance(UUID player) {
        try {
            Object account = account(player);
            if (account == null) {
                return 0D;
            }
            Object value = accountBalance.invoke(account, currency());
            return value instanceof BigDecimal ? ((BigDecimal) value).doubleValue() : 0D;
        } catch (Throwable throwable) {
            logger.warning("Sponge balance lookup failed: " + throwable);
            return 0D;
        }
    }

    @Override
    public boolean has(UUID player, double amount) {
        return amount <= 0 || balance(player) >= amount;
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        if (amount <= 0) {
            return true;
        }
        try {
            Object account = account(player);
            if (account == null) {
                return false;
            }
            Object result = accountWithdraw.invoke(account, currency(), BigDecimal.valueOf(amount));
            return succeeded(result);
        } catch (Throwable throwable) {
            logger.warning("Sponge withdraw failed: " + throwable);
            return false;
        }
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        if (amount <= 0) {
            return true;
        }
        try {
            Object account = account(player);
            if (account == null) {
                return false;
            }
            Object result = accountDeposit.invoke(account, currency(), BigDecimal.valueOf(amount));
            return succeeded(result);
        } catch (Throwable throwable) {
            logger.warning("Sponge deposit failed: " + throwable);
            return false;
        }
    }

    @Override
    public String format(double amount) {
        return String.format(java.util.Locale.ROOT, "%,.2f", amount);
    }
}
