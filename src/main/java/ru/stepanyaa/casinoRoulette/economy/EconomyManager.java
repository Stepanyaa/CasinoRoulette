package ru.stepanyaa.casinoRoulette.economy;

import ru.stepanyaa.casinoRoulette.platform.PlatformType;

import java.io.File;
import java.util.UUID;
import java.util.Map;
import java.util.logging.Logger;

public final class EconomyManager implements EconomyProvider {

    private final Logger logger;
    private final PlatformType platform;
    private final File dataFolder;
    private final double startingBalance;
    private final String currencySymbol;
    private final InternalEconomyProvider internal;

    private volatile String preferred;
    private volatile EconomyProvider delegate;

    public EconomyManager(Logger logger, PlatformType platform, File dataFolder,
                          String preferred, double startingBalance, String currencySymbol) {
        this.logger = logger;
        this.platform = platform;
        this.dataFolder = dataFolder;
        this.startingBalance = startingBalance;
        this.currencySymbol = currencySymbol;
        this.internal = new InternalEconomyProvider(logger, dataFolder, startingBalance, currencySymbol);
        this.preferred = preferred;
        this.delegate = select(logger, platform, preferred);
        logger.info("Economy provider: " + this.delegate.name());
    }

    public synchronized void rebind(String preferredMode) {
        if (preferredMode != null && !preferredMode.trim().isEmpty()) {
            this.preferred = preferredMode;
        }
        EconomyProvider previous = this.delegate;
        EconomyProvider next = select(logger, platform, this.preferred);
        if (previous != null && previous != internal && previous != next) {
            try {
                previous.shutdown();
            } catch (Throwable ignored) {
            }
        }
        this.delegate = next;
        if (previous == null || !previous.name().equals(next.name())) {
            logger.info("Economy provider rebound to: " + next.name());
        }
    }

    private EconomyProvider select(Logger logger, PlatformType platform, String preferred) {
        String mode = preferred == null ? "AUTO" : preferred.trim().toUpperCase(java.util.Locale.ROOT);
        if (mode.isEmpty()) {
            mode = "AUTO";
        }

        if ("INTERNAL".equals(mode)) {
            return internal;
        }

        boolean folia = platform == PlatformType.FOLIA;
        boolean wantPoints = mode.equals("AUTO")
                || mode.equals("PLAYERPOINTS")
                || mode.equals("PLAYER_POINTS")
                || mode.equals("POINTS")
                || (folia && (mode.equals("VAULT") || mode.equals("AUTO")));
        boolean wantVault = mode.equals("AUTO") || mode.equals("VAULT");
        boolean vaultFirst = wantVault && !folia;

        if (platform.isBukkitFamily() && wantPoints && !vaultFirst) {
            EconomyProvider points = PlayerPointsEconomyProvider.tryCreate(logger);
            if (points != null) {
                if (mode.equals("VAULT")) {
                    logger.info("Folia detected with economy-mode VAULT: using PlayerPoints instead "
                            + "(Vault is unreliable on Folia). Set economy-mode to PLAYERPOINTS "
                            + "to silence this message.");
                }
                return points;
            }
            if (mode.equals("PLAYERPOINTS") || mode.equals("PLAYER_POINTS") || mode.equals("POINTS")) {
                logger.warning("economy-mode is PLAYERPOINTS but PlayerPoints is unavailable. "
                        + "Falling back to the internal economy.");
                return internal;
            }
        }

        if (platform.isBukkitFamily() && wantVault) {
            EconomyProvider vault = VaultEconomyProvider.tryCreate(logger);
            if (vault != null) {
                return vault;
            }
            if (mode.equals("VAULT") && !folia) {
                logger.warning("economy-mode is VAULT but Vault is unavailable; "
                        + "trying PlayerPoints, then the internal economy.");
            }
        }

        if (platform.isBukkitFamily() && wantPoints && vaultFirst) {
            EconomyProvider points = PlayerPointsEconomyProvider.tryCreate(logger);
            if (points != null) {
                return points;
            }
            if (mode.equals("PLAYERPOINTS") || mode.equals("PLAYER_POINTS") || mode.equals("POINTS")) {
                logger.warning("economy-mode is PLAYERPOINTS but PlayerPoints is unavailable. "
                        + "Falling back to the internal economy.");
            }
        }

        if (platform.isBukkitFamily() && mode.equals("VAULT")) {
            EconomyProvider points = PlayerPointsEconomyProvider.tryCreate(logger);
            if (points != null) {
                logger.info("economy-mode is VAULT but Vault is unavailable; using PlayerPoints.");
                return points;
            }
            logger.warning("economy-mode is VAULT but neither Vault nor PlayerPoints is available; "
                    + "casino exchange falls back to the internal economy.");
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
        EconomyProvider current = delegate;
        if (current == null || !current.isAvailable()) {
            if (platform.isBukkitFamily() && current != internal) {
                EconomyProvider points = PlayerPointsEconomyProvider.tryCreate(logger);
                if (points != null) {
                    this.delegate = points;
                    logger.info("Economy provider late-bound to: " + points.name());
                    return points;
                }
            }
            return internal;
        }
        return current;
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

    @Override public boolean setBalance(UUID player, double amount) {
        return amount >= 0 && live().setBalance(player, amount);
    }

    @Override public Map<UUID, Double> balances() { return live().balances(); }

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
            EconomyProvider current = delegate;
            if (current != null) {
                current.shutdown();
            }
        } finally {
            if (delegate != internal) {
                internal.shutdown();
            }
        }
    }
}
