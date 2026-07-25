package ru.stepanyaa.casinoRoulette.game;

import java.util.UUID;

public interface StatsStore {

    java.util.List<ru.stepanyaa.casinoRoulette.PlayerStats> loadAll();

    ru.stepanyaa.casinoRoulette.PlayerStats snapshot(UUID player);

    long stat(UUID player, String column);

    void increment(UUID player, String column, long value, boolean highestOnly);

    void logGameResult(UUID player, int bet, int payout, boolean win);

    long lastDaily(UUID player);

    void setDaily(UUID player, long usedAt, String reward, long value);
}
