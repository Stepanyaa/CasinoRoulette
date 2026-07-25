
package ru.stepanyaa.casinoRoulette;

import ru.stepanyaa.casinoRoulette.game.ChipsBank;
import ru.stepanyaa.casinoRoulette.game.RouletteHost;
import ru.stepanyaa.casinoRoulette.game.StatsStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class BukkitGameBridge implements ChipsBank, StatsStore, RouletteHost {

    private final CasinoRoulette plugin;

    public BukkitGameBridge(CasinoRoulette plugin) {
        this.plugin = plugin;
    }

    @Override
    public int chips(UUID player) {
        return plugin.getPlayerChips().getOrDefault(player, 0);
    }

    @Override
    public void add(UUID player, int amount) {
        if (amount > 0) {
            plugin.addChips(player, amount);
        }
    }

    @Override
    public boolean take(UUID player, int amount) {
        if (amount <= 0) {
            return true;
        }

        return plugin.takeChips(player, amount);
    }

    @Override
    public List<PlayerStats> loadAll() {
        List<PlayerStats> stored = plugin.getDb().loadAllStats();
        if (stored == null) {
            return new ArrayList<>();
        }
        for (PlayerStats stats : stored) {
            overlay(stats);
        }
        return stored;
    }

    @Override
    public PlayerStats snapshot(UUID player) {
        for (PlayerStats stats : loadAll()) {
            if (stats.uuid.equals(player)) {
                return stats;
            }
        }

        PlayerStats fresh = new PlayerStats(player);
        overlay(fresh);
        return fresh;
    }

    private void overlay(PlayerStats stats) {
        UUID uuid = stats.uuid;
        Integer chips = plugin.getPlayerChips().get(uuid);
        if (chips != null) {
            stats.chips = chips;
        }
        Integer wins = plugin.getWins().get(uuid);
        if (wins != null) {
            stats.wins = wins;
        }
        Integer losses = plugin.getLosses().get(uuid);
        if (losses != null) {
            stats.losses = losses;
        }
        Integer won = plugin.getTotalWon().get(uuid);
        if (won != null) {
            stats.totalWon = won;
        }
        Integer lost = plugin.getTotalLost().get(uuid);
        if (lost != null) {
            stats.totalLost = lost;
        }
        Integer rounds = plugin.getTotalRounds().get(uuid);
        if (rounds != null) {
            stats.rounds = rounds;
        }
        Long biggest = plugin.getBiggestWin().get(uuid);
        if (biggest != null && biggest > stats.biggestWin) {
            stats.biggestWin = biggest;
        }
    }

    @Override
    public long stat(UUID player, String column) {
        return plugin.getDb().getStat(player, column);
    }

    @Override
    public void increment(UUID player, String column, long value, boolean highestOnly) {
        plugin.getDb().incrementStat(player, column, value, highestOnly);
    }

    @Override
    public void logGameResult(UUID player, int bet, int payout, boolean win) {
        plugin.logGameResult(player, bet, payout, win);
    }

    @Override
    public long lastDaily(UUID player) {
        return plugin.getDb().getLastDaily(player);
    }

    @Override
    public void setDaily(UUID player, long usedAt, String reward, long value) {
        plugin.getDb().setDaily(player, usedAt, reward, value);
    }

    @Override
    public State state() {
        return plugin.getGameState() == CasinoRoulette.GameState.SPINNING
                ? State.SPINNING
                : State.WAITING;
    }

    @Override
    public int timer() {
        return plugin.getTimer();
    }

    @Override
    public Map<UUID, Map<String, Integer>> activeBets() {
        return plugin.getActiveBets();
    }

    @Override
    public Map<UUID, Integer> currentBetAmount() {
        return plugin.getCurrentBetAmount();
    }

    @Override
    public Set<UUID> playersInGame() {
        return plugin.getPlayersInGame();
    }

    @Override
    public List<Integer> history() {
        return plugin.getHistory();
    }

    @Override
    public void checkGameLoop() {
        plugin.checkGameLoop();
    }

    @Override
    public void awaitInput(UUID player, String kind) {
        plugin.getAwaitingInput().put(player, kind);
    }
}
