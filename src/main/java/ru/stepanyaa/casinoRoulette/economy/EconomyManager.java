package ru.stepanyaa.casinoRoulette.economy;

import ru.stepanyaa.casinoRoulette.platform.PlatformType;

import java.io.File;
import java.util.UUID;
import java.util.logging.Logger;

public final class EconomyManager implements EconomyProvider {

    private final Logger logger;
    private final EconomyProvider delegate;
    private final InternalEconomyProvider internal;

    public EconomyManager(Logger logger, PlatformType platform, File dataFolder,
                          String preferred, double startingBalance, String currencySymbol) {
        this.logger = logger;
        this.internal = new InternalEconomyProvider(logger, dataFolder, startingBalance, currencySymbol);
        this.delegate = select(logger, platform, preferred);
        logger.info("Economy provider: " + this.delegate.name());
    }

    private EconomyProvider select(Logger logger, PlatformType platform, String preferred) {
        String mode = preferred == null ? "AUTO" : preferred.trim().toUpperCase(java.util.Locale.ROOT);

        if ("INTERNAL".equals(mode)) {
            return internal;
        }

        if (platform.isBukkitFamily() && (mode.equals("AUTO") || mode.equals("VAULT"))) {
            EconomyProvider vault = VaultEconomyProvider.tryCreate(logger);
            if (vault != null) {
                return vault;
            }
            if (mode.equals("VAULT")) {
                logger.warning("economy-mode is VAULT but Vault is unavailable. "
                        + "Falling back to the internal economy.");
            }
        }

        if (platform.isBukkitFamily() && (mode.equals("AUTO") || mode.equals("PLAYERPOINTS")
                || mode.equals("PLAYER_POINTS") || mode.equals("POINTS"))) {
            EconomyProvider points = PlayerPointsEconomyProvider.tryCreate(logger);
            if (points != null) {
                return points;
            }
            if (!mode.equals("AUTO")) {
                logger.warning("economy-mode is PLAYERPOINTS but PlayerPoints is unavailable. "
                        + "Falling back to the internal economy.");
            }
        }

        if (platform.isSpongeFamily() && (mode.equals("AUTO") || mode.equals("SPONGE"))) {
            EconomyProvider sponge = SpongeEconomyProvider.tryCreate(logger);
            if (sponge != null) {
                return sponge;
            }
            if (mode.equals("SPONGE")) {
                logger.warning("economy-mode is SPONGE but no EconomyService is registered. "
                        + "Falling back to the internal economy.");
            }
        }

        return internal;
    }

    public EconomyProvider active() {
        return delegate.isAvailable() ? delegate : internal;
    }

    private EconomyProvider live() {
        if (!delegate.isAvailable()) {

            return internal;
        }
        return delegate;
    }

    @Override
    public String name() {
        return live().name();
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public double balance(UUID player) {
        return live().balance(player);
    }

    @Override
    public boolean has(UUID player, double amount) {
        return live().has(player, amount);
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        return live().withdraw(player, amount);
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        boolean ok = live().deposit(player, amount);
        if (!ok) {

            logger.severe("PAYOUT FAILED for " + player + " amount=" + amount
                    + " provider=" + live().name() + ". Compensate this player manually.");
        }
        return ok;
    }

    @Override
    public String format(double amount) {
        return live().format(amount);
    }

    @Override
    public String currencyName() {
        return live().currencyName();
    }

    @Override
    public void shutdown() {
        try {
            delegate.shutdown();
        } finally {
            if (delegate != internal) {
                internal.shutdown();
            }
        }
    }
}
