package ru.stepanyaa.casinoRoulette.game;

import java.util.UUID;

public interface ChipsBank {

    int chips(UUID player);

    void add(UUID player, int amount);

    boolean take(UUID player, int amount);
}
