package ru.stepanyaa.casinoRoulette.economy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class InternalEconomyProvider implements EconomyProvider {

    private static final String FILE_NAME = "economy.dat";

    private final Logger logger;
    private final File storage;
    private final Map<UUID, BigDecimal> balances = new ConcurrentHashMap<>();
    private final BigDecimal startingBalance;
    private final String currencySymbol;
    private volatile boolean dirty;

    public InternalEconomyProvider(Logger logger, File dataFolder,
                                   double startingBalance, String currencySymbol) {
        this.logger = logger;
        this.storage = new File(dataFolder, FILE_NAME);
        this.startingBalance = scale(BigDecimal.valueOf(Math.max(0.0D, startingBalance)));
        this.currencySymbol = currencySymbol == null ? "" : currencySymbol;
        load();
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String name() {
        return "Internal";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public double balance(UUID player) {
        return balances.getOrDefault(player, startingBalance).doubleValue();
    }

    @Override
    public boolean has(UUID player, double amount) {
        if (amount <= 0) {
            return true;
        }
        return balances.getOrDefault(player, startingBalance)
                .compareTo(scale(BigDecimal.valueOf(amount))) >= 0;
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        if (amount <= 0) {
            return true;
        }
        final BigDecimal cost = scale(BigDecimal.valueOf(amount));
        final boolean[] ok = {false};
        balances.compute(player, (uuid, current) -> {
            BigDecimal value = current == null ? startingBalance : current;
            if (value.compareTo(cost) < 0) {
                return value;
            }
            ok[0] = true;
            return scale(value.subtract(cost));
        });
        if (ok[0]) {
            dirty = true;
        }
        return ok[0];
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        if (amount <= 0) {
            return true;
        }
        final BigDecimal gain = scale(BigDecimal.valueOf(amount));
        balances.compute(player, (uuid, current) ->
                scale((current == null ? startingBalance : current).add(gain)));
        dirty = true;
        return true;
    }

    @Override
    public String format(double amount) {
        return currencySymbol + scale(BigDecimal.valueOf(amount)).toPlainString();
    }

    private void load() {
        if (!storage.isFile()) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(
                storage.toPath(), StandardCharsets.UTF_8)) {
            String line;
            int broken = 0;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    broken++;
                    continue;
                }
                try {
                    UUID uuid = UUID.fromString(line.substring(0, separator).trim());
                    balances.put(uuid,
                            scale(new BigDecimal(line.substring(separator + 1).trim())));
                } catch (RuntimeException malformed) {

                    broken++;
                }
            }
            if (broken > 0) {
                logger.warning("Skipped " + broken + " malformed line(s) in " + FILE_NAME + ".");
            }
        } catch (IOException failure) {
            logger.log(Level.SEVERE,
                    "Could not read " + FILE_NAME + "; starting with empty balances.", failure);
        }
    }

    public synchronized void save() {
        if (!dirty) {
            return;
        }
        File parent = storage.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            logger.severe("Could not create " + parent + "; balances were NOT saved.");
            return;
        }

        File temp = new File(storage.getAbsolutePath() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(
                temp.toPath(), StandardCharsets.UTF_8)) {
            writer.write("# CasinoRoulette internal economy. uuid=balance");
            writer.newLine();
            for (Map.Entry<UUID, BigDecimal> entry : balances.entrySet()) {
                writer.write(entry.getKey().toString());
                writer.write('=');
                writer.write(entry.getValue().toPlainString());
                writer.newLine();
            }
        } catch (IOException failure) {
            logger.log(Level.SEVERE, "Could not write balances to " + temp, failure);
            return;
        }

        try {
            Files.move(temp.toPath(), storage.toPath(), StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException failure) {
            logger.log(Level.SEVERE,
                    "Could not replace " + FILE_NAME + "; the previous file is intact.", failure);
        }
    }

    @Override
    public void shutdown() {
        save();
    }
}
