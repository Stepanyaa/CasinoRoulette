package ru.stepanyaa.casinoRoulette.stats;

import ru.stepanyaa.casinoRoulette.CasinoRoulette;
import ru.stepanyaa.casinoRoulette.PlayerStats;
import ru.stepanyaa.casinoRoulette.placeholder.CasinoPlaceholderRegistrar;
import ru.stepanyaa.casinoRoulette.platform.Platforms;
import ru.stepanyaa.casinoRoulette.scheduler.CasinoScheduler;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CoreStatsSource implements CasinoPlaceholderRegistrar.StatsSource {

    private static final long DAILY_CACHE_MILLIS = 2_000L;

    private final CasinoRoulette plugin;

    private final Map<UUID, long[]> dailyCache = new ConcurrentHashMap<>();

    public CoreStatsSource(CasinoRoulette plugin) {
        this.plugin = plugin;
    }

    @Override
    public double balance(UUID player) {
        if (!Platforms.isReady()) {
            return 0.0D;
        }
        return Platforms.get().economy().balance(player);
    }

    @Override
    public long chips(UUID player) {
        return plugin.getPlayerChips().getOrDefault(player, 0);
    }

    @Override
    public int level(UUID player) {
        long rounds = totalGames(player);
        return 1 + (int) Math.floor(Math.sqrt((double) Math.max(0L, rounds)));
    }

    @Override
    public long lastWin(UUID player) {
        return plugin.getLastWin().getOrDefault(player, 0);
    }

    @Override
    public long totalGames(UUID player) {
        return plugin.getTotalRounds().getOrDefault(player, 0);
    }

    @Override
    public long totalWins(UUID player) {
        return plugin.getWins().getOrDefault(player, 0);
    }

    @Override
    public long totalLosses(UUID player) {
        return plugin.getLosses().getOrDefault(player, 0);
    }

    @Override
    public long biggestWin(UUID player) {
        return plugin.getBiggestWin().getOrDefault(player, 0L);
    }

    @Override
    public long totalWon(UUID player) {
        return plugin.getTotalWon().getOrDefault(player, 0);
    }

    @Override
    public long totalLost(UUID player) {
        return plugin.getTotalLost().getOrDefault(player, 0);
    }

    @Override
    public long dailyCooldownMillis(UUID player) {
        long cooldown = cooldownWindowMillis();
        long lastUse = cachedLastDaily(player);
        if (lastUse <= 0L) {
            return 0L;
        }
        long remaining = lastUse + cooldown - System.currentTimeMillis();
        return remaining > 0L ? remaining : 0L;
    }

    private long cooldownWindowMillis() {
        try {
            return plugin.getExtra("dailywheel.yml").get()
                    .getLong("settings.cooldown-hours", 24L) * 3_600_000L;
        } catch (Throwable missingConfig) {
            return 24L * 3_600_000L;
        }
    }

    private long cachedLastDaily(UUID player) {
        long now = System.currentTimeMillis();
        long[] entry = dailyCache.get(player);

        if (entry == null || now - entry[1] > DAILY_CACHE_MILLIS) {
            if (entry == null) {
                dailyCache.put(player, new long[]{0L, now});
            } else {
                entry[1] = now;
            }
            CasinoScheduler.async(() -> {
                long value = plugin.getDb().getLastDaily(player);
                dailyCache.put(player, new long[]{value, System.currentTimeMillis()});
            });
        }

        long[] current = dailyCache.get(player);
        return current == null ? 0L : current[0];
    }

    public void invalidate(UUID player) {
        dailyCache.remove(player);
    }

    @Override
    public String topName(String board, int place) {
        PlayerStats stats = entry(board, place);
        if (stats == null) {
            return null;
        }
        return stats.name == null || stats.name.isEmpty()
                ? stats.uuid.toString().substring(0, 8)
                : stats.name;
    }

    @Override
    public double topValue(String board, int place) {
        PlayerStats stats = entry(board, place);
        return stats == null ? 0.0D : value(category(board), stats);
    }

    private PlayerStats entry(String board, int place) {
        if (place < 1 || plugin.getGames() == null) {
            return null;
        }
        List<PlayerStats> top = plugin.getGames().leaderboards().top(category(board), place);
        return top.size() < place ? null : top.get(place - 1);
    }

    private static String category(String board) {
        if (board == null) {
            return "money";
        }
        switch (board.toLowerCase(java.util.Locale.ROOT)) {
            case "chips":
            case "balance":
            case "money":
                return "money";
            case "wins":
            case "win":
                return "wins";
            case "games":
            case "rounds":
                return "games";
            case "bets":
                return "bets";
            case "biggest_win":
            case "bigwin":
                return "biggest_win";
            case "biggest_loss":
            case "losses":
                return "biggest_loss";
            case "daily":
                return "daily";
            case "wheel":
                return "wheel";
            case "chips_bought":
                return "chips_bought";
            default:
                return board;
        }
    }

    private static double value(String category, PlayerStats s) {
        switch (category) {
            case "money":
                return s.chips;
            case "wins":
                return s.wins;
            case "bets":
                return s.bets;
            case "games":
                return s.rounds;
            case "biggest_win":
                return s.biggestWin;
            case "biggest_loss":
                return s.biggestLoss;
            case "daily":
                return s.dailyUses;
            case "wheel":
                return s.wheelSpins;
            case "chips_bought":
                return s.chipsBought;
            default:
                return 0.0D;
        }
    }

    @Override
    public String formatMoney(double amount) {
        if (Platforms.isReady()) {
            try {
                return Platforms.get().economy().format(amount);
            } catch (Throwable brokenEconomy) {

            }
        }
        return plugin.formatNumber((int) Math.round(amount));
    }
}
