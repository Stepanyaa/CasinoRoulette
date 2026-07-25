package ru.stepanyaa.casinoRoulette.platform;

public interface CasinoInventory {

    String guiId();

    String title();

    int size();

    void setItem(int slot, CasinoItem item);

    void clear(int slot);

    void fillEmpty(CasinoItem item);

    void refresh();

    Object handle();
}
