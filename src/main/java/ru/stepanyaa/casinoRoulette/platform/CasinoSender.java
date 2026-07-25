package ru.stepanyaa.casinoRoulette.platform;

public interface CasinoSender {

    String name();

    void sendMessage(String message);

    boolean hasPermission(String permission);

    boolean isPlayer();

    boolean isConsole();

    Object handle();
}
