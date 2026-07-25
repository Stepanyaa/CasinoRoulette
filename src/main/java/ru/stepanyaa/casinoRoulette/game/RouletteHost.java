package ru.stepanyaa.casinoRoulette.game;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface RouletteHost {

    enum State {

        WAITING,

        SPINNING
    }

    State state();

    int timer();

    Map<UUID, Map<String, Integer>> activeBets();

    Map<UUID, Integer> currentBetAmount();

    Set<UUID> playersInGame();

    List<Integer> history();

    void checkGameLoop();

    void awaitInput(UUID player, String kind);
}
