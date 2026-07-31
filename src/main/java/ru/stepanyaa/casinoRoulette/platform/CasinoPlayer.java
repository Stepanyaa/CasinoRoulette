package ru.stepanyaa.casinoRoulette.platform;

import java.util.UUID;

public interface CasinoPlayer extends CasinoSender {

    UUID uuid();

    boolean isOnline();

    void openInventory(CasinoInventory inventory);

    void closeInventory();

    void giveItem(CasinoItem item);

    int countItem(String materialId);

    boolean takeItem(String materialId, int amount);

    void playSound(String soundId, float volume, float pitch);

    void spawnParticle(String particleId, int count, double offsetX, double offsetY, double offsetZ);

    void sendActionBar(String message);

    void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut);

    void runOwning(Runnable task);
}
